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

package com.android.libraries.entitlement.eapaka;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.io.BaseEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MasterKeyTest {
    private static final String EXPECTED_IK = "06C4880743EF17AAA99EE7B17CEE9119";
    private static final String EXPECTED_CK = "F330A26F2C1CCC1ED9B45FD48D06C516";
    private static final String IMSI_EAP = "0234107813240779@nai.epc.mnc010.mcc234.3gppnetwork.org";
    private static final String EXPECTED_AUT = "4A2137E6E292679DD4C3FD8AB67F13DA";

    @Test
    public void generateTransientEapKeys_getAutPassed() throws Exception {
        MasterKey masterKey =
                MasterKey.create(
                        IMSI_EAP,
                        convertHexStringToBytes(EXPECTED_IK),
                        convertHexStringToBytes(EXPECTED_CK));

        assertThat(masterKey.getAut()).isEqualTo(convertHexStringToBytes(EXPECTED_AUT));
    }

    @Test
    public void generateTransientEapKeys_withoutImsiEap_getNull() throws Exception {
        MasterKey masterKey =
                MasterKey.create(
                        null,
                        convertHexStringToBytes(EXPECTED_IK),
                        convertHexStringToBytes(EXPECTED_CK));

        assertThat(masterKey).isNull();
    }

    @Test
    public void generateTransientEapKeys_withoutIk_getNull() throws Exception {
        MasterKey masterKey =
                MasterKey.create(
                        IMSI_EAP,
                        null,
                        convertHexStringToBytes(EXPECTED_CK));

        assertThat(masterKey).isNull();
    }

    @Test
    public void generateTransientEapKeys_withoutCk_getNull() throws Exception {
        MasterKey masterKey =
                MasterKey.create(
                        IMSI_EAP,
                        convertHexStringToBytes(EXPECTED_IK),
                        null);

        assertThat(masterKey).isNull();
    }

    private byte[] convertHexStringToBytes(String input) {
        return BaseEncoding.base16().decode(input);
    }
}