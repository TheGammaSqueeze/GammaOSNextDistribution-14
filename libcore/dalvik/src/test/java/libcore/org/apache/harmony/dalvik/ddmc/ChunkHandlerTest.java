/*
 * Copyright (C) 2021 The Android Open Source Project
 *
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
 */

package libcore.org.apache.harmony.dalvik.ddmc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.harmony.dalvik.ddmc.Chunk;
import org.apache.harmony.dalvik.ddmc.ChunkHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChunkHandlerTest {

    @Test
    public void name() {
        assertEquals("ABCD", ChunkHandler.name(0x41424344));
        assertEquals("0123", ChunkHandler.name(0x30313233));
        assertEquals("0000", ChunkHandler.name(0x30303030));
        assertEquals("FAIL", ChunkHandler.name(0x4641494c));
        assertEquals("abcd", ChunkHandler.name(0x61626364));
        assertEquals("\"#$%", ChunkHandler.name(0x22232425));

        // all printable ASCII chars
        for (int i = 32; i <= 126; i++) {
            int test = (i << 24) | (i << 16) | (i << 8) | i;
            assertEquals(String.format("%c%c%c%c", i, i, i, i), ChunkHandler.name(test));
        }
    }

    @Test
    public void wrapChunk_simple() {
        final byte[] buf = new byte[]{ 0x00, 0x01 };
        Chunk request = new Chunk(ChunkHandler.CHUNK_FAIL, buf, 0, 2);
        ByteBuffer result = ChunkHandler.wrapChunk(request);

        assertArrayEquals(buf, result.array());
        assertEquals(0, result.arrayOffset());
        assertEquals(ByteOrder.BIG_ENDIAN, result.order());
    }

    @Test
    public void wrapChunk_stress() {
        final byte[][] BUFS = new byte[][]{
                {0x00, 0x01, 0x02, 0x03, 0x04, 0x05},
                {0x41, 0x42, 0x43, 0x44, 0x45},
        };
        final int[] TYPES = new int[]{
                ChunkHandler.type("FAIL"),
                ChunkHandler.type("1234"),
                ChunkHandler.type("yeah"),
        };

        for (byte[] buf : BUFS) {
            for (int type : TYPES) {
                for (int offset = 0; offset < buf.length; offset++) {
                    Chunk request = new Chunk(type, buf, offset, buf.length - offset);
                    ByteBuffer result = ChunkHandler.wrapChunk(request);

                    assertArrayEquals(buf, result.array());
                    assertEquals(0, result.arrayOffset());
                    assertEquals(ByteOrder.BIG_ENDIAN, result.order());

                    for (int i = 0; i < buf.length - offset; i++) {
                        assertEquals(buf[i + offset], result.get());
                    }
                }
            }
        }
    }
}
