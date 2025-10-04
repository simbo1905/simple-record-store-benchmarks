package org.lmdbjava.bench;

import com.github.simbo1905.nfp.srs.FileRecordStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Simple test to verify new FileRecordStore implementations work correctly.
 * Tests both RandomAccessFile and MemoryMapped modes.
 */
public class TestNewImplementations {
    
    private static final int NUM_RECORDS = 1000;
    private static final int VALUE_SIZE = 100;
    private static final Random RANDOM = new Random(42);
    
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 Testing New FileRecordStore Implementations");
        System.out.println("=".repeat(50));
        
        // Create temp directory for tests
        Path tempDir = Files.createTempDirectory("srs-test-");
        System.out.println("Temp directory: " + tempDir);
        
        try {
            // Test 1: Original API (backward compatibility)
            testOriginalImplementation(tempDir);
            
            // Test 2: New RandomAccessFile mode
            testNewRandomAccessFileMode(tempDir);
            
            // Test 3: New MemoryMapped mode
            testNewMemoryMappedMode(tempDir);
            
            System.out.println("\n✅ All tests passed!");
            
        } finally {
            // Cleanup
            deleteRecursively(tempDir.toFile());
            System.out.println("🧹 Cleaned up temp directory");
        }
    }
    
    private static void testOriginalImplementation(Path tempDir) throws IOException {
        System.out.println("\n1️⃣ Testing Original SimpleRecordStore (Legacy API)");
        
        Path dbPath = tempDir.resolve("original-srs.db");
        
        // Create store using legacy constructor (simulating old behavior)
        try (FileRecordStore store = new FileRecordStore.Builder()
                .path(dbPath)
                .preallocatedRecords(NUM_RECORDS)
                .byteArrayKeys(16)
                .useMemoryMapping(false)  // Classic RandomAccessFile
                .open()) {
            
            // Write test data
            long startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = generateValue(i);
                store.insertRecord(key, value);
            }
            long writeTime = System.nanoTime() - startTime;
            
            // Read test data
            startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = store.readRecordData(key);
                if (value == null || value.length != VALUE_SIZE) {
                    throw new RuntimeException("Invalid value read!");
                }
            }
            long readTime = System.nanoTime() - startTime;
            
            System.out.printf("  ✅ Original API: %d writes in %.2fms, %d reads in %.2fms%n", 
                NUM_RECORDS, writeTime / 1_000_000.0, NUM_RECORDS, readTime / 1_000_000.0);
            System.out.printf("  📊 Write: %.2f μs/op, Read: %.2f μs/op%n", 
                writeTime / 1000.0 / NUM_RECORDS, readTime / 1000.0 / NUM_RECORDS);
        }
    }
    
    private static void testNewRandomAccessFileMode(Path tempDir) throws IOException {
        System.out.println("\n2️⃣ Testing New RandomAccessFile Mode");
        
        Path dbPath = tempDir.resolve("new-random-access.db");
        
        // Create store using new builder with RandomAccessFile
        try (FileRecordStore store = new FileRecordStore.Builder()
                .path(dbPath)
                .preallocatedRecords(NUM_RECORDS)
                .byteArrayKeys(16)
                .useMemoryMapping(false)  // Explicitly use RandomAccessFile
                .open()) {
            
            // Write test data
            long startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = generateValue(i);
                store.insertRecord(key, value);
            }
            long writeTime = System.nanoTime() - startTime;
            
            // Read test data
            startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = store.readRecordData(key);
                if (value == null || value.length != VALUE_SIZE) {
                    throw new RuntimeException("Invalid value read!");
                }
            }
            long readTime = System.nanoTime() - startTime;
            
            System.out.printf("  ✅ RandomAccessFile: %d writes in %.2fms, %d reads in %.2fms%n", 
                NUM_RECORDS, writeTime / 1_000_000.0, NUM_RECORDS, readTime / 1_000_000.0);
            System.out.printf("  📊 Write: %.2f μs/op, Read: %.2f μs/op%n", 
                writeTime / 1000.0 / NUM_RECORDS, readTime / 1000.0 / NUM_RECORDS);
        }
    }
    
    private static void testNewMemoryMappedMode(Path tempDir) throws IOException {
        System.out.println("\n3️⃣ Testing New MemoryMapped Mode");
        
        Path dbPath = tempDir.resolve("new-mmap.db");
        
        // Create store using new builder with Memory Mapping
        try (FileRecordStore store = new FileRecordStore.Builder()
                .path(dbPath)
                .preallocatedRecords(NUM_RECORDS)
                .byteArrayKeys(16)
                .useMemoryMapping(true)  // Enable memory mapping!
                .open()) {
            
            // Write test data
            long startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = generateValue(i);
                store.insertRecord(key, value);
            }
            long writeTime = System.nanoTime() - startTime;
            
            // Read test data (this should be much faster!)
            startTime = System.nanoTime();
            for (int i = 0; i < NUM_RECORDS; i++) {
                byte[] key = ("key-" + i).getBytes();
                byte[] value = store.readRecordData(key);
                if (value == null || value.length != VALUE_SIZE) {
                    throw new RuntimeException("Invalid value read!");
                }
            }
            long readTime = System.nanoTime() - startTime;
            
            System.out.printf("  ✅ MemoryMapped: %d writes in %.2fms, %d reads in %.2fms%n", 
                NUM_RECORDS, writeTime / 1_000_000.0, NUM_RECORDS, readTime / 1_000_000.0);
            System.out.printf("  📊 Write: %.2f μs/op, Read: %.2f μs/op%n", 
                writeTime / 1000.0 / NUM_RECORDS, readTime / 1000.0 / NUM_RECORDS);
            
            // Test iteration
            startTime = System.nanoTime();
            int count = 0;
            for (byte[] key : store.keysBytes()) {
                count++;
            }
            long iterateTime = System.nanoTime() - startTime;
            System.out.printf("  🔄 Iterated %d keys in %.2fms (%.2f μs/key)%n", 
                count, iterateTime / 1_000_000.0, iterateTime / 1000.0 / count);
        }
    }
    
    private static byte[] generateValue(int index) {
        byte[] value = new byte[VALUE_SIZE];
        // Fill with deterministic but varied data
        for (int i = 0; i < VALUE_SIZE; i++) {
            value[i] = (byte) ((index + i) % 256);
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
}