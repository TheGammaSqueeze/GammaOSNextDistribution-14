/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery.info;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit test for {@link SecureComponentInfo}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SecureComponentInfoTest {

    private static final byte[] TEST_BYTES = new byte[] {(byte) 0xF1, 0x32};
    private static final boolean STATIC_INDICATION = true;
    private static final int SECID = 113;
    private static final SecureComponentInfo.SecureComponentType SC_TYPE =
            SecureComponentInfo.SecureComponentType.DISCRETE_EUICC_REMOVABLE;
    private static final SecureComponentInfo.SecureComponentProtocolType SC_PROTOCOL_TYPE =
            SecureComponentInfo.SecureComponentProtocolType.ISO_IEC_7816_4;

    @Test
    public void fromBytes_emptyData() {
        assertThat(SecureComponentInfo.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(SecureComponentInfo.fromBytes(new byte[] {0x01})).isNull();
    }

    @Test
    public void fromBytes_invalidSecid() {
        assertThat(SecureComponentInfo.fromBytes(new byte[] {0x01, 0x00})).isNull();
    }

    @Test
    public void fromBytes_invalidSecureComponentType() {
        assertThat(SecureComponentInfo.fromBytes(new byte[] {0x02, (byte) 0x80})).isNull();
    }

    @Test
    public void fromBytes_invalidSecureComponentProtocolType() {
        assertThat(SecureComponentInfo.fromBytes(new byte[] {0x02, (byte) 0x14})).isNull();
    }

    @Test
    public void fromBytes_succeed() {
        SecureComponentInfo info = SecureComponentInfo.fromBytes(TEST_BYTES);
        assertThat(info).isNotNull();

        assertThat(info.staticIndication).isEqualTo(STATIC_INDICATION);
        assertThat(info.secid).isEqualTo(SECID);
        assertThat(info.secureComponentType).isEqualTo(SC_TYPE);
        assertThat(info.secureComponentProtocolType).isEqualTo(SC_PROTOCOL_TYPE);
    }

    @Test
    public void toBytes_succeed() {
        SecureComponentInfo info =
                new SecureComponentInfo(STATIC_INDICATION, SECID, SC_TYPE, SC_PROTOCOL_TYPE);
        assertThat(info).isNotNull();

        byte[] result = SecureComponentInfo.toBytes(info);
        assertThat(result.length).isEqualTo(TEST_BYTES.length);
        assertThat(SecureComponentInfo.toBytes(info)).isEqualTo(TEST_BYTES);
    }
}
