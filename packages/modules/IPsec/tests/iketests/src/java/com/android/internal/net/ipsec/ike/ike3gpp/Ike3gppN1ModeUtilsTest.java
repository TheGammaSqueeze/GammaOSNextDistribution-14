/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.ike3gpp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;

import com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload;
import com.android.internal.util.HexDump;

import org.junit.Test;

public class Ike3gppN1ModeUtilsTest {
    private static final byte PDU_SESSION_ID = (byte) 0x01;
    private static final byte[] N1_MODE_CAPABILITY_PAYLOAD = HexDump.hexStringToByteArray("0101");

    private static final byte[] SNSSAI = HexDump.hexStringToByteArray("11223344");
    private static final byte[] N1_MODE_INFORMATION_PAYLOAD =
            HexDump.hexStringToByteArray("0411223344");
    private static final byte[] INVALID_N1_MODE_INFORMATION_PAYLOAD =
            HexDump.hexStringToByteArray("0511223344");

    @Test
    public void testGenerateN1ModeCapabilityPayload() throws Exception {
        IkeNotifyPayload n1ModeCapabilityPayload =
                Ike3gppN1ModeUtils.generateN1ModeCapabilityPayload(PDU_SESSION_ID);

        assertEquals(
                Ike3gppExtensionExchange.NOTIFY_TYPE_N1_MODE_CAPABILITY,
                n1ModeCapabilityPayload.notifyType);
        assertArrayEquals(N1_MODE_CAPABILITY_PAYLOAD, n1ModeCapabilityPayload.notifyData);
    }

    @Test
    public void testGetSnssaiFromNotifyData() throws Exception {
        byte[] snssai = Ike3gppN1ModeUtils.getSnssaiFromNotifyData(N1_MODE_INFORMATION_PAYLOAD);

        assertArrayEquals(SNSSAI, snssai);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testGetSnssaiFromNotifyDataIncorrectLength() throws Exception {
        Ike3gppN1ModeUtils.getSnssaiFromNotifyData(INVALID_N1_MODE_INFORMATION_PAYLOAD);
    }
}
