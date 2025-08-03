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

import com.android.server.uwb.discovery.info.RegulatoryInfo.SourceOfInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unit test for {@link RegulatoryInfo}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RegulatoryInfoTest {

    private static final byte[] TEST_BYTES =
            new byte[] {0x41, 0x55, 0x53, 0x62, 0x1E, 0x3B, 0x7F, (byte) 0xD8, (byte) 0x9F};
    private static final SourceOfInfo SOURCE_OF_INFO = SourceOfInfo.SATELLITE_NAVIGATION_SYSTEM;
    private static final boolean TRANSMITTION_PERMITTED = true;
    private static final String COUNTRY_CODE =
            new String(new byte[] {0x55, 0x53}, StandardCharsets.UTF_8);
    private static final int TIMESTAMP = 1646148479;
    private static final int FIRST_CHANNELS = 13;
    private static final int NUMBER_OF_CHANNELS = 4;
    private static final boolean IS_INDOOR = false;
    private static final int AVERAGE_POWER_DBM = -97;

    @Test
    public void fromBytes_emptyData() {
        assertThat(RegulatoryInfo.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(RegulatoryInfo.fromBytes(new byte[] {0x0, 0x1})).isNull();
    }

    @Test
    public void fromBytes_invalidSourceOfInfo() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = 0x71;
        assertThat(RegulatoryInfo.fromBytes(bytes)).isNull();
    }

    @Test
    public void fromBytes_invalidReservedField() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = 0x47;
        assertThat(RegulatoryInfo.fromBytes(bytes)).isNull();
    }

    @Test
    public void fromBytes_invalidCountryCode() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[1] = 0x2b;
        assertThat(RegulatoryInfo.fromBytes(bytes)).isNull();
    }

    @Test
    public void fromBytes_succeed() {
        RegulatoryInfo info = RegulatoryInfo.fromBytes(TEST_BYTES);
        assertThat(info).isNotNull();

        assertThat(info.sourceOfInfo).isEqualTo(SOURCE_OF_INFO);
        assertThat(info.outdoorsTransmittionPermitted).isEqualTo(TRANSMITTION_PERMITTED);
        assertThat(info.countryCode).isEqualTo(COUNTRY_CODE);
        assertThat(info.timestampSecondsSinceEpoch).isEqualTo(TIMESTAMP);
        for (ChannelPowerInfo i : info.channelPowerInfos) {
            assertThat(i.firstChannel).isEqualTo(FIRST_CHANNELS);
            assertThat(i.numOfChannels).isEqualTo(NUMBER_OF_CHANNELS);
            assertThat(i.isIndoor).isEqualTo(IS_INDOOR);
            assertThat(i.averagePowerLimitDbm).isEqualTo(AVERAGE_POWER_DBM);
        }
    }

    @Test
    public void toBytes_succeed() {
        RegulatoryInfo info =
                new RegulatoryInfo(
                        SOURCE_OF_INFO,
                        TRANSMITTION_PERMITTED,
                        COUNTRY_CODE,
                        TIMESTAMP,
                        new ChannelPowerInfo[] {
                            new ChannelPowerInfo(
                                    FIRST_CHANNELS,
                                    NUMBER_OF_CHANNELS,
                                    IS_INDOOR,
                                    AVERAGE_POWER_DBM)
                        });
        assertThat(info).isNotNull();

        byte[] result = RegulatoryInfo.toBytes(info);
        assertThat(result.length).isEqualTo(TEST_BYTES.length);
        assertThat(result).isEqualTo(TEST_BYTES);
    }

    @Test
    public void fromBytesAndToBytes_eachSourceOfInfo() {
        testSourceOfInfo(SourceOfInfo.USER_DEFINED, (byte) 0x80);
        testSourceOfInfo(SourceOfInfo.SATELLITE_NAVIGATION_SYSTEM, (byte) 0x40);
        testSourceOfInfo(SourceOfInfo.CELLULAR_SYSTEM, (byte) 0x20);
        testSourceOfInfo(SourceOfInfo.ANOTHER_FIRA_DEVICE, (byte) 0x10);
    }

    private void testSourceOfInfo(SourceOfInfo sourceOfInfo, byte sourceOfInfoByte) {
        RegulatoryInfo info =
                new RegulatoryInfo(
                        sourceOfInfo,
                        TRANSMITTION_PERMITTED,
                        COUNTRY_CODE,
                        TIMESTAMP,
                        new ChannelPowerInfo[] {
                            new ChannelPowerInfo(
                                    FIRST_CHANNELS,
                                    NUMBER_OF_CHANNELS,
                                    IS_INDOOR,
                                    AVERAGE_POWER_DBM)
                        });
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = (byte) (sourceOfInfoByte | 0x01);

        byte[] bytesResult = RegulatoryInfo.toBytes(info);
        RegulatoryInfo regulatoryInfoResult = RegulatoryInfo.fromBytes(bytes);
        assertThat(regulatoryInfoResult).isNotNull();

        assertThat(bytesResult).isEqualTo(bytes);
        assertThat(regulatoryInfoResult.sourceOfInfo).isEqualTo(sourceOfInfo);
        assertThat(regulatoryInfoResult.outdoorsTransmittionPermitted)
                .isEqualTo(TRANSMITTION_PERMITTED);
        assertThat(regulatoryInfoResult.countryCode).isEqualTo(COUNTRY_CODE);
        assertThat(regulatoryInfoResult.timestampSecondsSinceEpoch).isEqualTo(TIMESTAMP);
    }
}
