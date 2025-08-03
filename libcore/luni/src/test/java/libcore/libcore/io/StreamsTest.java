/*
 * Copyright (C) 2015 The Android Open Source Project
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

package libcore.libcore.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import libcore.io.Streams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StreamsTest {

    @Test
    public void testReadFully() throws Exception {
        final byte[] bytes = "0123456789".getBytes();
        ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
        byte[] dst = new byte[bytes.length - 1];

        Arrays.fill(dst, (byte)-1);
        Streams.readFully(inStream, dst);
        for (int i = 0; i < dst.length; ++i) {
            assertEquals(bytes[i], dst[i]);
        }
    }
}
