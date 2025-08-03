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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructXfrmLifetimeCurTest {
    private static final String EXPECTED_HEX_STRING =
            "00000000000000000000000000000000" + "8CFE4265000000000000000000000000";
    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);
    private static final BigInteger ADD_TIME;

    static {
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.set(2023, Calendar.NOVEMBER, 2, 1, 42, 36);
        final long timestampSeconds = TimeUnit.MILLISECONDS.toSeconds(cal.getTimeInMillis());
        ADD_TIME = BigInteger.valueOf(timestampSeconds);
    }

    @Test
    public void testEncode() throws Exception {
        final StructXfrmLifetimeCur struct =
                new StructXfrmLifetimeCur(
                        BigInteger.ZERO, BigInteger.ZERO, ADD_TIME, BigInteger.ZERO);

        final ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_HEX.length);
        buffer.order(ByteOrder.nativeOrder());
        struct.writeToByteBuffer(buffer);

        assertArrayEquals(EXPECTED_HEX, buffer.array());
    }

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());
        final StructXfrmLifetimeCur struct =
                StructXfrmLifetimeCur.parse(StructXfrmLifetimeCur.class, buffer);

        assertEquals(BigInteger.ZERO, struct.bytes);
        assertEquals(BigInteger.ZERO, struct.packets);
        assertEquals(ADD_TIME, struct.addTime);
        assertEquals(BigInteger.ZERO, struct.useTime);
    }
}
