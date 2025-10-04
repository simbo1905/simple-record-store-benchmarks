# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a comprehensive JMH (Java Microbenchmark Harness) benchmark suite that compares the performance of various embedded, disk-backed, key-value stores available from Java. Originally forked from LmdbJava benchmarks, it evaluates write/read performance, iteration speed, and disk usage across different storage implementations with workloads ranging from 99MB to 152GB.

## Build Commands

### Build the benchmark
```bash
mvn clean package
```

### Run the benchmark
```bash
# Basic run with default parameters
java -jar target/benchmarks.jar

# Run with CSV output, 3 forks, stop on error
java -jar target/benchmarks.jar -rf csv -f 3 -foe true

# List all available parameters
java -jar target/benchmarks.jar -lp
```

### Run comprehensive test suite
```bash
# Execute full benchmark suite (6 test configurations)
cd results && ./run.sh

# Process results and generate graphs
cd results/DATE && ../process.sh
```

## Architecture

### Benchmark Structure
- **Common.java**: Base class providing shared JMH state, key generation, disk space reporting via POSIX calls, and parameter configuration
- **Individual implementations**: Each key-value store has its own benchmark class with identical test patterns:
  - SimpleRecordStore.java - SimpleRecordStore 1.0.0-RC6
  - Chronicle.java - Chronicle Map 3.17.7 (unordered keys only)
  - MapDb.java - MapDB 3.0.7 
  - MvStore.java - H2 MVStore 1.4.200
  - Xodus.java - JetBrains Xodus 1.3.124

### Key Benchmark Operations
1. **write**: Bulk insert key-value pairs using implementation-optimal methods (transactions, batch modes)
2. **readKey**: Random access reads by specific keys
3. **readSeq**: Forward ordered iteration over all entries
4. **readRev**: Reverse ordered iteration (where supported)
5. **readCrc**: Forward iteration computing CRC32 of keys+values
6. **readXxh64**: Forward iteration computing XXH64 hash via Zero-Allocation-Hashing

### Configuration Parameters
- `intKey`: 4-byte integer keys vs 16-byte zero-padded string keys
- `sequential`: Sequential vs random key insertion order
- `valRandom`: Random byte values vs key-based values
- `valSize`: Value sizes from 100 bytes to 16,368 bytes
- `num`: Entry counts from 1M to 10M entries
- `batchSize`: Batch sizes for LSM tree implementations (1M optimal)

### Storage Implementations Benchmarked
- **SimpleRecordStore**: 1.0.0-RC6 - Custom implementation, no ordered iteration
- **Chronicle Map**: 3.17.7 - Unordered hash-based map, fastest overall
- **MapDB**: 3.0.7 - Pure Java BTree, struggles with large workloads
- **MVStore**: 1.4.200 - H2 database storage engine, OOM on large tests
- **Xodus**: 1.3.124 - JetBrains transactional store, high overhead

### Test Methodology
Six comprehensive test configurations in results/run.sh:
1. **LMDB tuning**: Force safe, sync, writeMap, metaSync flags
2. **Value sizing**: 2-16KB optimal sizes for LMDB page alignment
3. **Batch optimization**: 1M batch size for LSM trees (LevelDB/RocksDB)
4. **Comprehensive comparison**: 1M entries, all implementations
5. **Medium workload**: 10M x 2KB values (~19GB)
6. **Large workload**: 10M x 4-16KB values (38-152GB)

### Performance Results Summary
**Key Findings from Historical Results (2016-2017):**

**Storage Efficiency (1M x 100 byte values):**
- Best: LevelDB/RocksDB ~3% overhead
- Worst: LMDB ~65% overhead (B+ tree + copy-on-write)
- Xodus: 355% overhead

**Read Performance:**
- LMDB consistently fastest for all read operations
- 27-81x faster than LevelDB for random reads
- Sequential iteration always superior to random access

**Write Performance:**
- Small values (100B): LMDB fastest among sorted implementations
- Medium values (2KB): LevelDB/RocksDB faster than LMDB
- Large values (8KB+): LMDB becomes faster again (write amplification in LSM)

**Scalability Issues:**
- MVStore: OOM at 19GB workloads
- MapDB/Xodus: Poor performance at scale
- LSM trees: Require extensive file handle tuning (100K+ files)

### Results Processing
- **results/run.sh**: Executes 6 test configurations with optimal parameters
- **results/process.sh**: Generates graphs and tables using gnuplot
- **Historical results**: Complete 2016/2017 benchmark reports in results/ directories
- **Storage measurements**: Actual POSIX disk usage, not apparent size
- **Performance tables**: Detailed ms/op comparisons across all operations

## Important Technical Notes

**Platform Requirements:**
- Linux-only (POSIX calls for disk measurement)
- Requires `/etc/security/limits.conf` nofile=1,000,000 for LSM trees
- 512GB+ RAM recommended for full test suite
- tmpfs filesystem preferred for memory-bound testing

**JMH Configuration:**
- SampleTime mode for latency measurement
- 3 warmup + 3 measurement iterations
- Single fork to reduce execution time
- 10 minute timeouts for large workloads

**Value Size Optimization:**
- LMDB optimal: 2,030, 4,084, 8,180, 12,276 bytes (4KB increments)
- Storage efficiency improves with larger values for all implementations
- Compression recommended (LZ4-Java, JavaFastPFOR) for space-constrained deployments

**Operational Considerations:**
- LMDB: Single-threaded, ACID transactions, 2 file handles
- LSM trees: Background compaction threads, 100K+ file handles
- Pure Java: GC tuning required, memory limitations at scale
- Chronicle Map: No ordered iteration, replication support

## JMH Annotation Processing Issues

If you encounter `ERROR: Unable to find the resource: /META-INF/BenchmarkList`, this means JMH's annotation processor didn't generate the required benchmark metadata. This typically happens when:

1. **The JMH annotation processor didn't run during compilation**
2. **Generated resources weren't included in the final JAR**

### Diagnostic Steps:

```bash
# Check if annotation processor generated any files
ls -la target/generated-sources/annotations/

# Clean and rebuild with annotation processing explicitly enabled
mvn clean compile

# Check verbose output for JMH annotation processing
mvn clean compile -X 2>&1 | grep -i "jmh\|annotation"

# Check if BenchmarkList exists in JAR
jar tf target/benchmarks.jar | grep -i benchmark
```

### Solutions:

1. **Ensure annotation processors are in compile scope** (not provided)
2. **Add explicit annotation processor configuration**
3. **Configure maven-compiler-plugin properly**
4. **For Java 25+ compatibility, ensure proper module access**

### Common Fixes:

```xml
<!-- In pom.xml, ensure these are in compile scope -->
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-generator-annprocess</artifactId>
  <version>${jmh.version}</version>
  <scope>compile</scope>
</dependency>

<!-- Configure compiler plugin explicitly -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.13.0</version>
  <configuration>
    <release>21</release>
    <annotationProcessorPaths>
      <path>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>${jmh.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```