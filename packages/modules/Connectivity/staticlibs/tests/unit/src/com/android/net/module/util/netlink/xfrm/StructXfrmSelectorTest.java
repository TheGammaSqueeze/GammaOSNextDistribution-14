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

package com.android.net.module.util.netlink.xfrm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructXfrmSelectorTest {
    private static final String EXPECTED_HEX_STRING =
            "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000200000000000000"
                    + "0000000000000000";
    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);

    private static final byte[] XFRM_ADDRESS_T_ANY_BYTES = new byte[16];
    private static final int FAMILY = OsConstants.AF_INET;

    @Test
    public void testEncode() throws Exception {
        final StructXfrmSelector struct = new StructXfrmSelector(FAMILY);

        final ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_HEX.length);
        buffer.order(ByteOrder.nativeOrder());
        struct.writeToByteBuffer(buffer);

        assertArrayEquals(EXPECTED_HEX, buffer.array());
    }

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());
        final StructXfrmSelector struct =
                StructXfrmSelector.parse(StructXfrmSelector.class, buffer);

        assertArrayEquals(XFRM_ADDRESS_T_ANY_BYTES, struct.nestedStructDAddr);
        assertArrayEquals(XFRM_ADDRESS_T_ANY_BYTES, struct.nestedStructSAddr);
        assertEquals(0, struct.dPort);
        assertEquals(0, struct.dPortMask);
        assertEquals(0, struct.sPort);
        assertEquals(0, struct.sPortMask);
        assertEquals(FAMILY, struct.selectorFamily);
        assertEquals(0, struct.prefixlenD);
        assertEquals(0, struct.prefixlenS);
        assertEquals(0, struct.proto);
        assertEquals(0, struct.ifIndex);
        assertEquals(0, struct.user);
    }
}
