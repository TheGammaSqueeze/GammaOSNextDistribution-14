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
 * Unit test for {@link UwbIndicationData}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UwbIndicationDataTest {

    private static final byte[] TEST_BYTES = new byte[] {(byte) 0b00010100, (byte) 0x9C};
    private static final byte[] TEST_BYTES2 =
            new byte[] {(byte) 0b11101001, (byte) 0x9C, (byte) 0xF1, 0x11};
    private static final byte[] TEST_BYTES3 =
            new byte[] {(byte) 0b11101001, (byte) 0x9C, (byte) 0xF1, 0x11, (byte) 0x01, 0x11};
    private static final boolean FIRA_UWB_SUPPORT = true;
    private static final boolean ISO_14443_SUPPORT = true;
    private static final boolean UWB_REG_INFO_AVAILABLE_IN_AD = true;
    private static final boolean UWB_REG_INFO_AVAILABLE_IN_OOB = false;
    private static final boolean FIRA_PROFILE_INFO_AVAILABLE_IN_AD = true;
    private static final boolean FIRA_PROFILE_INFO_AVAILABLE_IN_OOB = false;
    private static final boolean DUAL_GAP_ROLE_SUPPORT = true;
    private static final int BT_RSSI_THRESHOLD_DBM = -100;

    private static final boolean STATIC_INDICATION = true;
    private static final int SECID = 113;
    private static final SecureComponentInfo.SecureComponentType SC_TYPE =
            SecureComponentInfo.SecureComponentType.ESE_NONREMOVABLE;
    private static final SecureComponentInfo.SecureComponentProtocolType SC_PROTOCOL_TYPE =
            SecureComponentInfo.SecureComponentProtocolType.FIRA_OOB_ADMINISTRATIVE_PROTOCOL;

    @Test
    public void fromBytes_emptyData() {
        assertThat(UwbIndicationData.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(UwbIndicationData.fromBytes(new byte[] {0x00})).isNull();
    }

    @Test
    public void fromBytes_invalidReservedField() {
        assertThat(UwbIndicationData.fromBytes(new byte[] {0x02, 0x55})).isNull();
    }

    @Test
    public void fromBytes_noSecureComponentInfo() {
        UwbIndicationData info = UwbIndicationData.fromBytes(TEST_BYTES);
        assertThat(info).isNotNull();

        assertThat(info.firaUwbSupport).isEqualTo(false);
        assertThat(info.iso14443Support).isEqualTo(false);
        assertThat(info.uwbRegulartoryInfoAvailableInAd).isEqualTo(false);
        assertThat(info.uwbRegulartoryInfoAvailableInOob).isEqualTo(true);
        assertThat(info.firaProfileInfoAvailableInAd).isEqualTo(false);
        assertThat(info.firaProfileInfoAvailableInOob).isEqualTo(true);
        assertThat(info.dualGapRoleSupport).isEqualTo(false);
        assertThat(info.bluetoothRssiThresholdDbm).isEqualTo(BT_RSSI_THRESHOLD_DBM);
        assertThat(info.secureComponentInfos.length).isEqualTo(0);
    }

    @Test
    public void fromBytes_oneValidAndOneInvalidSecureComponentInfo() {
        UwbIndicationData info = UwbIndicationData.fromBytes(TEST_BYTES3);
        assertThat(info).isNotNull();

        assertThat(info.firaUwbSupport).isEqualTo(FIRA_UWB_SUPPORT);
        assertThat(info.iso14443Support).isEqualTo(ISO_14443_SUPPORT);
        assertThat(info.uwbRegulartoryInfoAvailableInAd).isEqualTo(UWB_REG_INFO_AVAILABLE_IN_AD);
        assertThat(info.uwbRegulartoryInfoAvailableInOob).isEqualTo(UWB_REG_INFO_AVAILABLE_IN_OOB);
        assertThat(info.firaProfileInfoAvailableInAd).isEqualTo(FIRA_PROFILE_INFO_AVAILABLE_IN_AD);
        assertThat(info.firaProfileInfoAvailableInOob)
                .isEqualTo(FIRA_PROFILE_INFO_AVAILABLE_IN_OOB);
        assertThat(info.dualGapRoleSupport).isEqualTo(DUAL_GAP_ROLE_SUPPORT);
        assertThat(info.bluetoothRssiThresholdDbm).isEqualTo(BT_RSSI_THRESHOLD_DBM);
        assertThat(info.secureComponentInfos.length).isEqualTo(1);

        SecureComponentInfo i = info.secureComponentInfos[0];
        assertThat(i.staticIndication).isEqualTo(STATIC_INDICATION);
        assertThat(i.secid).isEqualTo(SECID);
        assertThat(i.secureComponentType).isEqualTo(SC_TYPE);
        assertThat(i.secureComponentProtocolType).isEqualTo(SC_PROTOCOL_TYPE);
    }

    @Test
    public void toBytes_succeed() {
        UwbIndicationData info =
                new UwbIndicationData(
                        FIRA_UWB_SUPPORT,
                        ISO_14443_SUPPORT,
                        UWB_REG_INFO_AVAILABLE_IN_AD,
                        UWB_REG_INFO_AVAILABLE_IN_OOB,
                        FIRA_PROFILE_INFO_AVAILABLE_IN_AD,
                        FIRA_PROFILE_INFO_AVAILABLE_IN_OOB,
                        DUAL_GAP_ROLE_SUPPORT,
                        BT_RSSI_THRESHOLD_DBM,
                        new SecureComponentInfo[] {
                            new SecureComponentInfo(
                                    STATIC_INDICATION, SECID, SC_TYPE, SC_PROTOCOL_TYPE)
                        });
        assertThat(info).isNotNull();

        byte[] result = UwbIndicationData.toBytes(info);
        assertThat(result.length).isEqualTo(TEST_BYTES2.length);
        assertThat(UwbIndicationData.toBytes(info)).isEqualTo(TEST_BYTES2);
    }
}
