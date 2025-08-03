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

package com.android.server.uwb.params;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit tests for {@link com.android.server.uwb.params.RadarEncoder}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RadarEncoderTest {
    private static final FiraProtocolVersion PROTOCOL_VERSION_DUMMY = new FiraProtocolVersion(0, 0);
    private static final RadarOpenSessionParams.Builder TEST_RADAR_OPEN_SESSION_PARAMS =
            new RadarOpenSessionParams.Builder()
                    .setSessionId(22)
                    .setBurstPeriod(100)
                    .setSweepPeriod(40)
                    .setSweepsPerBurst(16)
                    .setSamplesPerSweep(128)
                    .setChannelNumber(FiraParams.UWB_CHANNEL_5)
                    .setSweepOffset(-1)
                    .setRframeConfig(FiraParams.RFRAME_CONFIG_SP3)
                    .setPreambleDuration(RadarParams.PREAMBLE_DURATION_T16384_SYMBOLS)
                    .setPreambleCodeIndex(90)
                    .setSessionPriority(99)
                    .setBitsPerSample(RadarParams.BITS_PER_SAMPLES_32)
                    .setPrfMode(FiraParams.PRF_MODE_HPRF)
                    .setNumberOfBursts(1000)
                    .setRadarDataType(RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES);

    private static final int TEST_RADAR_OPEN_SESSION_TLV_NUM_PARAMS = 12;
    private static final byte[] TEST_RADAR_OPEN_SESSION_TLV_DATA =
            UwbUtil.getByteArray(
                    "000764000000280010"
                            + "010180"
                            + "020105"
                            + "0302ffff"
                            + "040103"
                            + "050109"
                            + "06015a"
                            + "070163"
                            + "080100"
                            + "090101"
                            + "0a02e803"
                            + "0b0100");

    @Mock
    private UwbInjector mUwbInjector;
    private final RadarEncoder mRadarEncoder = new RadarEncoder();

    public static void verifyRadarOpenSessionParamsTlvBuffer(TlvBuffer tlvs) {
        assertThat(tlvs.getNoOfParams()).isEqualTo(TEST_RADAR_OPEN_SESSION_TLV_NUM_PARAMS);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_RADAR_OPEN_SESSION_TLV_DATA);
    }

    @Test
    public void testRadarOpenSessionParams() throws Exception {
        RadarOpenSessionParams params = TEST_RADAR_OPEN_SESSION_PARAMS.build();

        verifyRadarOpenSessionParamsTlvBuffer(
                mRadarEncoder.getTlvBuffer(params, PROTOCOL_VERSION_DUMMY));
    }

    @Test
    public void testRadarOpenSessionParamsViaTlvEncoder() throws Exception {
        RadarOpenSessionParams params = TEST_RADAR_OPEN_SESSION_PARAMS.build();

        verifyRadarOpenSessionParamsTlvBuffer(
                TlvEncoder.getEncoder(RadarParams.PROTOCOL_NAME, mUwbInjector)
                        .getTlvBuffer(params, PROTOCOL_VERSION_DUMMY));
    }
}
