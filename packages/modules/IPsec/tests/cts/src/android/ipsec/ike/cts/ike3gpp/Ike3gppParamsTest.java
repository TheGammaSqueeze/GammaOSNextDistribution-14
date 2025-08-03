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

package android.net.ipsec.ike.ike3gpp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class Ike3gppParamsTest {
    public static final byte PDU_SESSION_ID = (byte) 5;
    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";

    @Test
    public void testBuildIke3gppParams() {
        final Ike3gppParams params =
                new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build();

        assertEquals(PDU_SESSION_ID, params.getPduSessionId());
    }

    @Test
    public void testBuildIke3gppParamsWithoutPduSessionId() {
        final Ike3gppParams params = new Ike3gppParams.Builder().build();

        assertEquals(Ike3gppParams.PDU_SESSION_ID_UNSET, params.getPduSessionId());
    }

    @Test
    public void testBuildIke3gppParamsWithoutDeviceIdentity() {
        final Ike3gppParams params = new Ike3gppParams.Builder().build();
        assertNull(params.getMobileDeviceIdentity());
    }

    @Test
    public void testBuildIke3gppParamsWithNullDeviceIdentity() {
        final Ike3gppParams params =
                new Ike3gppParams.Builder().setMobileDeviceIdentity(null).build();
        assertNull(params.getMobileDeviceIdentity());
    }

    @Test
    public void testBuildIke3gppParamsWithDeviceIdentity() {
        final Ike3gppParams params =
                new Ike3gppParams.Builder().setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI).build();
        assertEquals(DEVICE_IDENTITY_IMEI, params.getMobileDeviceIdentity());
    }
}
