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

package com.android.internal.net.ipsec.test.ike.ike3gpp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload;
import com.android.internal.util.HexDump;

import org.junit.Test;

public class Ike3gppDeviceIdentityUtilsTest {
    private static final String DEVICE_IDENTITY_IMEISV = "1234567891234567";
    private static final byte[] DEVICE_IDENTITY_PAYLOAD_IMEISV =
            HexDump.hexStringToByteArray("0009022143658719325476");
    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";
    private static final byte[] DEVICE_IDENTITY_PAYLOAD_IMEI =
            HexDump.hexStringToByteArray("00090121436587193254F6");

    private static final String DEVICE_IDENTITY_INVALID_TOO_SHORT = "12345678912345";
    private static final String DEVICE_IDENTITY_INVALID_TOO_LONG = "12345678912345679";
    private static final String DEVICE_IDENTITY_INVALID_NON_NUMERIC = "A234567891234567";

    @Test
    public void testGenerateDeviceIdentityWithImeisv() throws Exception {
        IkeNotifyPayload deviceIdentityPayload =
                Ike3gppDeviceIdentityUtils.generateDeviceIdentityPayload(DEVICE_IDENTITY_IMEISV);

        assertEquals(
                Ike3gppExtensionExchange.NOTIFY_TYPE_DEVICE_IDENTITY,
                deviceIdentityPayload.notifyType);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEISV, deviceIdentityPayload.notifyData);
    }

    @Test
    public void testGenerateDeviceIdentityWithImei() throws Exception {
        IkeNotifyPayload deviceIdentityPayload =
                Ike3gppDeviceIdentityUtils.generateDeviceIdentityPayload(DEVICE_IDENTITY_IMEI);

        assertEquals(
                Ike3gppExtensionExchange.NOTIFY_TYPE_DEVICE_IDENTITY,
                deviceIdentityPayload.notifyType);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentityPayload.notifyData);
    }

    @Test
    public void testisValidDeviceIdentity() throws Exception {
        assertTrue(Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(DEVICE_IDENTITY_IMEI));
        assertTrue(Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(DEVICE_IDENTITY_IMEISV));

        assertFalse(
                Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(
                        DEVICE_IDENTITY_INVALID_TOO_SHORT));
        assertFalse(
                Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(DEVICE_IDENTITY_INVALID_TOO_LONG));
        assertFalse(
                Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(
                        DEVICE_IDENTITY_INVALID_NON_NUMERIC));
        assertFalse(Ike3gppDeviceIdentityUtils.isValidDeviceIdentity(null));
    }
}
