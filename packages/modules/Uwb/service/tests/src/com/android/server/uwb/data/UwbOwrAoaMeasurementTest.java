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

package com.android.server.uwb.data;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link com.android.server.uwb.data.UwbOwrAoaMeasurement}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbOwrAoaMeasurementTest {
    private static final byte[] TEST_MAC_ADDRESS = {0x11, 0x13};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 3;
    private static final int TEST_BLOCK_INDEX = 5;
    private static final int TEST_FRAME_SEQ_NUMBER = 1;
    private static final int TEST_AOA_AZIMUTH = 67;
    private static final float TEST_AOA_AZIMUTH_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_AZIMUTH, 16), 9, 7);
    private static final int TEST_AOA_AZIMUTH_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_AZIMUTH_FLOAT, 9, 7);
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final int TEST_AOA_ELEVATION = 37;
    private static final float TEST_AOA_ELEVATION_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_ELEVATION, 16), 9, 7);
    private static final int TEST_AOA_ELEVATION_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_ELEVATION_FLOAT, 9, 7);
    private static final int TEST_AOA_ELEVATION_FOM = 90;

    private UwbOwrAoaMeasurement mUwbOwrAoaMeasurement;

    @Test
    public void testInitializeUwbOwrAoaMeasurement() throws Exception {
        mUwbOwrAoaMeasurement = new UwbOwrAoaMeasurement(TEST_MAC_ADDRESS, TEST_STATUS, TEST_LOS,
                TEST_FRAME_SEQ_NUMBER, TEST_BLOCK_INDEX,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_AOA_AZIMUTH_FOM,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_AOA_ELEVATION_FOM);

        assertThat(mUwbOwrAoaMeasurement.getMacAddress()).isEqualTo(TEST_MAC_ADDRESS);
        assertThat(mUwbOwrAoaMeasurement.getRangingStatus()).isEqualTo(TEST_STATUS);
        assertThat(mUwbOwrAoaMeasurement.getNLoS()).isEqualTo(TEST_LOS);
        assertThat(mUwbOwrAoaMeasurement.getFrameSequenceNumber()).isEqualTo(TEST_FRAME_SEQ_NUMBER);
        assertThat(mUwbOwrAoaMeasurement.getBlockIndex()).isEqualTo(TEST_BLOCK_INDEX);
        assertThat(mUwbOwrAoaMeasurement.getAoaAzimuth()).isEqualTo(TEST_AOA_AZIMUTH_FLOAT);
        assertThat(mUwbOwrAoaMeasurement.getAoaAzimuthFom()).isEqualTo(TEST_AOA_AZIMUTH_FOM);
        assertThat(mUwbOwrAoaMeasurement.getAoaElevation()).isEqualTo(TEST_AOA_ELEVATION_FLOAT);
        assertThat(mUwbOwrAoaMeasurement.getAoaElevationFom()).isEqualTo(TEST_AOA_ELEVATION_FOM);

        final String testString = "UwbOwrAoaMeasurement { "
                + " MacAddress = " + UwbUtil.toHexString(TEST_MAC_ADDRESS)
                + ", Status = " + TEST_STATUS
                + ", NLoS = " + TEST_LOS
                + ", FrameSequenceNumber = " + TEST_FRAME_SEQ_NUMBER
                + ", BlockIndex = " + TEST_BLOCK_INDEX
                + ", AoaAzimuth = " + TEST_AOA_AZIMUTH_FLOAT
                + ", AoaAzimuthFom = " + TEST_AOA_AZIMUTH_FOM
                + ", AoaElevation = " + TEST_AOA_ELEVATION_FLOAT
                + ", AoaElevationFom = " + TEST_AOA_ELEVATION_FOM
                + '}';

        assertThat(mUwbOwrAoaMeasurement.toString()).isEqualTo(testString);
    }
}
