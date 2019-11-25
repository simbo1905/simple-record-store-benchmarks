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

import com.github.trex_paxos.srs.ByteSequence;
import com.github.trex_paxos.srs.FileRecordStore;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.IOException;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.SampleTime;
import static org.openjdk.jmh.annotations.Scope.Benchmark;

@OutputTimeUnit(MILLISECONDS)
//@Fork(jvmArgsAppend = {"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005", "-ea"})
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)
@SuppressWarnings({"checkstyle:javadoctype", "checkstyle:designforextension"})
public class SimpleRecordStore {

//  static {
//    Logger.getLogger("").setLevel(java.util.logging.Level.FINEST);
//    Logger.getLogger("").getHandlers()[0].setLevel(java.util.logging.Level.FINEST);
//  }

  // FileRecordStore does not provide ordered keys, so no CRC/XXH64/rev/prev test
  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws IOException {
    for (final int key : r.keys) {
      if (r.intKey) {
        r.wkb.putInt(0, key);
      } else {
        r.wkb.putStringWithoutLengthUtf8(0, r.padKey(key));
      }
      bh.consume(r.map.readRecordData(ByteSequence.of(r.wkb.byteArray())));
    }
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws IOException {
    w.write();
  }

  @State(value = Benchmark)
  @SuppressWarnings("checkstyle:visibilitymodifier")
  public static class SimpleRecordStoreMap extends Common {

    FileRecordStore map;

    /**
     * Writable key buffer. Backed by a plain byte[] for Chroncile API ease.
     */
    MutableDirectBuffer wkb;

    /**
     * Writable value buffer. Backed by a plain byte[] for Chroncile API ease.
     */
    MutableDirectBuffer wvb;

    @Override
    public void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
      wkb = new UnsafeBuffer(new byte[keySize]);
      wvb = new UnsafeBuffer(new byte[valSize]);

      try {
        map = new FileRecordStore(new File(tmp, "srs.map").getCanonicalPath(), num);
      } catch (final IOException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public void teardown() throws IOException {
      reportSpaceBeforeClose();
      map.close();
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
        ByteSequence k = ByteSequence.copyOf(wkb.byteArray());
        if( !map.recordExists(k)){
          map.insertRecord(k, wvb.byteArray());
        } else {
          map.updateRecord(k, wvb.byteArray());
        }
      }
    }
  }

  @State(Benchmark)
  public static class Reader extends SimpleRecordStoreMap {

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
  public static class Writer extends SimpleRecordStoreMap {

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
