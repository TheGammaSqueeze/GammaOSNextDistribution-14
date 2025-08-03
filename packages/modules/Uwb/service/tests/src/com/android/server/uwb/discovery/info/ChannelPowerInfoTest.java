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
 * Unit test for {@link ChannelPowerInfo}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ChannelPowerInfoTest {

    private static final byte[] BYTES = new byte[] {(byte) 0xE5, (byte) 0x9F};
    private static final int FIRST_CHANNELS = 14;
    private static final int NUMBER_OF_CHANNELS = 2;
    private static final boolean IS_INDOOR = true;
    private static final int AVERAGE_POWER_DBM = -97;

    @Test
    public void fromBytes_emptyData() {
        assertThat(ChannelPowerInfo.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(ChannelPowerInfo.fromBytes(new byte[] {0x0})).isNull();
    }

    @Test
    public void fromBytes_succeed() {
        ChannelPowerInfo info = ChannelPowerInfo.fromBytes(BYTES);
        assertThat(info).isNotNull();

        assertThat(info.firstChannel).isEqualTo(FIRST_CHANNELS);
        assertThat(info.numOfChannels).isEqualTo(NUMBER_OF_CHANNELS);
        assertThat(info.isIndoor).isEqualTo(IS_INDOOR);
        assertThat(info.averagePowerLimitDbm).isEqualTo(AVERAGE_POWER_DBM);
    }

    @Test
    public void toBytes_succeed() {
        ChannelPowerInfo info =
                new ChannelPowerInfo(
                        FIRST_CHANNELS, NUMBER_OF_CHANNELS, IS_INDOOR, AVERAGE_POWER_DBM);
        assertThat(info).isNotNull();

        byte[] result = ChannelPowerInfo.toBytes(info);
        assertThat(result.length).isEqualTo(BYTES.length);
        assertThat(result).isEqualTo(BYTES);
    }
}
