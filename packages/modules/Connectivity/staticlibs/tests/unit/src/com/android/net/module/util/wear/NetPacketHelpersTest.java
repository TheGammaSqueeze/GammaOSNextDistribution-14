/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.net.module.util.wear;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.net.module.util.async.CircularByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetPacketHelpersTest {
    @Test
    public void decodeNetworkUnsignedInt16() {
        final byte[] data = new byte[4];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 1;
        data[2] = (byte) 2;
        data[3] = (byte) 0xFF;

        assertEquals(0x0102, NetPacketHelpers.decodeNetworkUnsignedInt16(data, 1));

        CircularByteBuffer buffer = new CircularByteBuffer(100);
        buffer.writeBytes(data, 0, data.length);

        assertEquals(0x0102, NetPacketHelpers.decodeNetworkUnsignedInt16(buffer, 1));
    }

    @Test
    public void encodeNetworkUnsignedInt16() {
        final byte[] data = new byte[4];
        data[0] = (byte) 0xFF;
        data[3] = (byte) 0xFF;
        NetPacketHelpers.encodeNetworkUnsignedInt16(0x0102, data, 1);

        assertEquals((byte) 0xFF, data[0]);
        assertEquals((byte) 1, data[1]);
        assertEquals((byte) 2, data[2]);
        assertEquals((byte) 0xFF, data[3]);
    }
}
