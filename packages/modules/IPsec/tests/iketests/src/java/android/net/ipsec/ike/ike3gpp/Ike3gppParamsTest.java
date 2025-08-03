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

package android.net.ipsec.test.ike.ike3gpp;

import static android.net.ipsec.test.ike.ike3gpp.Ike3gppParams.PDU_SESSION_ID_UNSET;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Ike3gppParamsTest {
    private static final byte PDU_SESSION_ID = (byte) 0x01;
    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";

    @Test
    public void testBuildWithPduSessionId() {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build();

        assertEquals(PDU_SESSION_ID, ike3gppParams.getPduSessionId());
    }

    @Test
    public void testBuildIke3gppParamsDefault() {
        Ike3gppParams ike3gppParams = new Ike3gppParams.Builder().build();

        assertEquals(PDU_SESSION_ID_UNSET, ike3gppParams.getPduSessionId());
        assertEquals(null, ike3gppParams.getMobileDeviceIdentity());
    }

    @Test
    public void testBuildWithDeviceIdentity() {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder().setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI).build();

        assertEquals(DEVICE_IDENTITY_IMEI, ike3gppParams.getMobileDeviceIdentity());
    }

    @Test
    public void testBuildIke3gppParamsSetAndClearDeviceIdentity() {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder()
                        .setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI)
                        .setMobileDeviceIdentity(null)
                        .build();

        assertEquals(null, ike3gppParams.getMobileDeviceIdentity());
    }

    @Test
    public void testBuildIke3gppParamsClearPduSessionId() {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder()
                        .setPduSessionId(PDU_SESSION_ID)
                        .setPduSessionId(PDU_SESSION_ID_UNSET)
                        .build();

        assertEquals(PDU_SESSION_ID_UNSET, ike3gppParams.getPduSessionId());
    }
}
