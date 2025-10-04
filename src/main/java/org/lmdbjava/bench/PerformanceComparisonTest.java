package org.lmdbjava.bench;

import com.github.simbo1905.nfp.srs.FileRecordStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Performance comparison test that mimics JMH benchmarks without the overhead.
 * Tests readKey, write, and readCrc operations across all implementations.
 */
public class PerformanceComparisonTest {
    
    private static final int[] TEST_SIZES = {10000, 50000, 100000};
    private static final int VALUE_SIZE = 100;
    private static final int WARMUP_ITERATIONS = 2;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final Random RANDOM = new Random(42);
    
    public static void main(String[] args) throws Exception {
        System.out.println("🔥 Performance Comparison Test - New FileRecordStore Generation");
        System.out.println("=".repeat(70));
        System.out.println("Testing configurations similar to JMH benchmarks");
        System.out.println("Operations: write, readKey, readCrc | Value size: " + VALUE_SIZE + " bytes");
        System.out.println();
        
        Path tempDir = Files.createTempDirectory("perf-test-");
        System.out.println("Temp directory: " + tempDir);
        
        try {
            for (int numRecords : TEST_SIZES) {
                System.out.println("\n📊 Testing with " + numRecords + " records:");
                System.out.println("-".repeat(50));
                
                testImplementation(tempDir, "Original-Legacy", numRecords, false, true);
                testImplementation(tempDir, "New-RandomAccess", numRecords, false, false);
                testImplementation(tempDir, "New-MemoryMapped", numRecords, true, false);
            }
            
            System.out.println("\n✅ All performance tests completed!");
            
        } finally {
            deleteRecursively(tempDir.toFile());
            System.out.println("\n🧹 Cleaned up temp directory");
        }
    }
    
    private static void testImplementation(Path tempDir, String name, int numRecords, 
                                           boolean useMemoryMapping, boolean legacyMode) throws IOException {
        
        System.out.println("\n🧪 " + name + " (memoryMapping=" + useMemoryMapping + ")");
        
        Path dbPath = tempDir.resolve(name.toLowerCase().replace("-", "_") + ".db");
        
        // Create appropriate store
        FileRecordStore store;
        if (legacyMode) {
            // Simulate original SimpleRecordStore behavior
            store = new FileRecordStore.Builder()
                    .path(dbPath)
                    .preallocatedRecords(numRecords)
                    .byteArrayKeys(16)
                    .useMemoryMapping(false)
                    .open();
        } else {
            store = new FileRecordStore.Builder()
                    .path(dbPath)
                    .preallocatedRecords(numRecords)
                    .byteArrayKeys(16)
                    .useMemoryMapping(useMemoryMapping)
                    .open();
        }
        
        try (store) {
            // Generate test data
            int[] keys = generateKeys(numRecords);
            
            // Warmup
            System.out.println("  🔥 Warming up...");
            try {
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    performWriteTest(store, keys, false);
                    performReadKeyTest(store, keys, false);
                    performReadCrcTest(store, false);
                }
            } catch (IOException e) {
                throw new RuntimeException("Warmup failed", e);
            }
            
            // Actual measurements
            System.out.println("  📏 Measuring performance...");
            List<Long> writeTimes = new ArrayList<>();
            List<Long> readKeyTimes = new ArrayList<>();
            List<Long> readCrcTimes = new ArrayList<>();
            
            // Write test (only once since it's expensive to recreate)
            try {
                writeTimes.add(performWriteTest(store, keys, true));
            } catch (IOException e) {
                throw new RuntimeException("Write test failed", e);
            }
            
            // Multiple read tests
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                try {
                    readKeyTimes.add(performReadKeyTest(store, keys, true));
                    readCrcTimes.add(performReadCrcTest(store, true));
                } catch (IOException e) {
                    throw new RuntimeException("Read test failed", e);
                }
            }
            
            // Print results
            printResults(name, "write", writeTimes, numRecords);
            printResults(name, "readKey", readKeyTimes, numRecords);
            printResults(name, "readCrc", readCrcTimes, numRecords);
            
        }
    }
    
    private static long performWriteTest(FileRecordStore store, int[] keys, boolean measure) throws IOException {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < keys.length; i++) {
            byte[] key = ("key-" + keys[i]).getBytes();
            byte[] value = generateValue(keys[i]);
            
            if (store.recordExists(key)) {
                store.updateRecord(key, value);
            } else {
                store.insertRecord(key, value);
            }
        }
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static long performReadKeyTest(FileRecordStore store, int[] keys, boolean measure) throws IOException {
        long startTime = System.nanoTime();
        
        for (int key : keys) {
            byte[] keyBytes = ("key-" + key).getBytes();
            byte[] value = store.readRecordData(keyBytes);
            if (value == null || value.length != VALUE_SIZE) {
                throw new RuntimeException("Invalid read!");
            }
        }
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static long performReadCrcTest(FileRecordStore store, boolean measure) throws IOException {
        CRC32 crc = new CRC32();
        long startTime = System.nanoTime();
        
        for (byte[] key : store.keysBytes()) {
            byte[] value = store.readRecordData(key);
            crc.update(key);
            crc.update(value);
        }
        
        long result = crc.getValue();
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static void printResults(String implementation, String operation, 
                                   List<Long> times, int numOperations) {
        Collections.sort(times);
        
        long total = times.stream().mapToLong(Long::longValue).sum();
        long avg = total / times.size();
        long min = times.get(0);
        long max = times.get(times.size() - 1);
        long median = times.get(times.size() / 2);
        
        double avgMs = avg / 1_000_000.0;
        double avgPerOp = (double) avg / numOperations / 1000.0; // microseconds per operation
        
        System.out.printf("    %s: avg=%.2fms (%.2fμs/op), median=%.2fms, min=%.2fms, max=%.2fms%n",
            operation, avgMs, avgPerOp, median / 1_000_000.0, min / 1_000_000.0, max / 1_000_000.0);
    }
    
    private static int[] generateKeys(int count) {
        int[] keys = new int[count];
        for (int i = 0; i < count; i++) {
            keys[i] = i; // Sequential keys for consistent testing
        }
        return keys;
    }
    
    private static byte[] generateValue(int seed) {
        byte[] value = new byte[VALUE_SIZE];
        for (int i = 0; i < VALUE_SIZE; i++) {
            value[i] = (byte) ((seed + i) % 256);
        }
        return value;
    }
    
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    // Simple CRC32 implementation for testing
    static class CRC32 {
        private java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        
        void update(byte[] data) {
            crc.update(data);
        }
        
        void update(byte[] data, int offset, int length) {
            crc.update(data, offset, length);
        }
        
        long getValue() {
            return crc.getValue();
        }
        
        void reset() {
            crc.reset();
        }
    }
}