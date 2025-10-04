/*-
 * #%L
 * LmdbJava Benchmarks
 * %%
 * Copyright (C) 2016 - 2019 The LmdbJava Open Source Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.lmdbjava.bench;

import com.github.simbo1905.nfp.srs.FileRecordStore;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.SampleTime;
import static org.openjdk.jmh.annotations.Scope.Benchmark;

@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)
@SuppressWarnings({"checkstyle:javadoctype", "checkstyle:designforextension"})
public class NewSimpleRecordStore {

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws IOException {
    for (final int key : r.keys) {
      if (r.intKey) {
        r.wkb.putInt(0, key);
      } else {
        r.wkb.putStringWithoutLengthUtf8(0, r.padKey(key));
      }
      bh.consume(r.map.readRecordData(r.wkb.byteArray()));
    }
  }

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) {
    r.crc.reset();
    final Iterator<byte[]> iter = r.map.keysBytes().iterator();
    while (iter.hasNext()) {
      final byte[] k = iter.next();
      final byte[] v;
      try {
        v = r.map.readRecordData(k);
      } catch (IOException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
      r.crc.update(k);
      r.crc.update(v);
    }
    bh.consume(r.crc.getValue());
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws IOException {
    w.write();
  }

  @State(value = Benchmark)
  @SuppressWarnings("checkstyle:visibilitymodifier")
  public static class NewSimpleRecordStoreMap extends Common {

    FileRecordStore map;

    /**
     * Writable key buffer. 
     */
    MutableDirectBuffer wkb;

    /**
     * Writable value buffer.
     */
    MutableDirectBuffer wvb;

    @Override
    public void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
      wkb = new UnsafeBuffer(new byte[keySize]);
      wvb = new UnsafeBuffer(new byte[valSize]);

      try {
        // Create builder for new FileRecordStore API
        FileRecordStore.Builder builder = new FileRecordStore.Builder();
        
        // Configure for the benchmark
        builder.byteArrayKeys(intKey ? 4 : 16); // 4 bytes for integer keys, 16 for string keys
        builder.preallocatedRecords(num);
        builder.tempFile("new-srs-random-", ".map");
        
        // Use RandomAccessFile (classic) mode for first benchmark (no memory mapping)
        builder.useMemoryMapping(false);
        
        map = builder.open();
      } catch (final IOException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public void teardown() throws IOException {
      reportSpaceBeforeClose();
      map.close(); // Clean close without explicit flush
      super.teardown();
    }

    void write() throws IOException {
      final int rndByteMax = RND_MB.length - valSize;
      int rndByteOffset = 0;
      for (final int key : keys) {
        if (intKey) {
          wkb.putInt(0, key, LITTLE_ENDIAN);
        } else {
          wkb.putStringWithoutLengthUtf8(0, padKey(key));
        }
        if (valRandom) {
          wvb.putBytes(0, RND_MB, rndByteOffset, valSize);
          rndByteOffset += valSize;
          if (rndByteOffset >= rndByteMax) {
            rndByteOffset = 0;
          }
        } else {
          wvb.putInt(0, key);
        }
        
        // New API uses byte arrays directly
        if (!map.recordExists(wkb.byteArray())) {
          map.insertRecord(wkb.byteArray(), wvb.byteArray());
        } else {
          map.updateRecord(wkb.byteArray(), wvb.byteArray());
        }
      }
    }
  }

  @State(Benchmark)
  public static class Reader extends NewSimpleRecordStoreMap {

    @Setup(Trial)
    @Override
    public void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
      super.write();
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws IOException {
      super.teardown();
    }
  }

  @SuppressWarnings("checkstyle:javadoctype")
  @State(Benchmark)
  public static class Writer extends NewSimpleRecordStoreMap {

    @Setup(Invocation)
    @Override
    public final void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
    }

    @TearDown(Invocation)
    @Override
    public final void teardown() throws IOException {
      super.teardown();
    }
  }
}