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
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_NONE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;

import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.uwb.params.CccEncoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class CccEncoderTest {
    private static final CccOpenRangingParams.Builder TEST_CCC_OPEN_RANGING_PARAMS =
            new CccOpenRangingParams.Builder()
                    .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(CccParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new CccPulseShapeCombo(
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(UWB_CHANNEL_9)
                    .setNumChapsPerSlot(CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(HOPPING_CONFIG_MODE_NONE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT)
                    .setInitiationTimeMs(1);

    private static final String RANGE_DATA_NTF_CONFIG_DISABLED_TLV = "0E0100";
    private static final String RANGE_DATA_NTF_CONFIG_ENABLED_TLV = "0E0101";
    private static final String RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG_TLV = "0E0102";
    private static final String RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT_TLV = "0F020000";
    private static final String RANGE_DATA_NTF_PROXIMITY_NEAR_TLV = "0F026400";
    private static final String RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT_TLV = "1002204E";
    private static final String RANGE_DATA_NTF_PROXIMITY_FAR_TLV = "1002C800";
    private static final String TEST_CCC_OPEN_RANGING_TLV =
            "00010102010104010905010109048001000011010103010"
                    + "11B01062C0100A3020001A4020000A50100A602D0020802B004140101"
                    + "2B080100000000000000";
    private static final String TEST_CCC_OPEN_RANGING_TLV_DEFAULT =
            TEST_CCC_OPEN_RANGING_TLV + RANGE_DATA_NTF_CONFIG_DISABLED_TLV;
    private static final byte[] TEST_CCC_OPEN_RANGING_TLV_DATA =
            UwbUtil.getByteArray(TEST_CCC_OPEN_RANGING_TLV_DEFAULT);

    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private DeviceConfigFacade mDeviceConfigFacade;
    private CccEncoder mCccEncoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mCccEncoder = new CccEncoder(mUwbInjector);

        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isCccSupportedRangeDataNtfConfig()).thenReturn(false);
    }

    @Test
    public void testCccOpenRangingParams() throws Exception {
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        assertThat(tlvs.getNoOfParams()).isEqualTo(17);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_CCC_OPEN_RANGING_TLV_DATA);
    }

    @Test
    public void testCccOpenRangingParamsViaTlvEncoder() throws Exception {
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        TlvBuffer tlvs = TlvEncoder.getEncoder(CccParams.PROTOCOL_NAME, mUwbInjector)
                .getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        assertThat(tlvs.getNoOfParams()).isEqualTo(17);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_CCC_OPEN_RANGING_TLV_DATA);
    }

    @Test
    public void testCccOpenRangingParamsWithAbsoluteInitiationTime() throws Exception {
        long absoluteInitiationTimeUs = 10_000L;
        CccOpenRangingParams.Builder builder = new CccOpenRangingParams
                .Builder(TEST_CCC_OPEN_RANGING_PARAMS);
        CccOpenRangingParams params = builder.setAbsoluteInitiationTimeUs(absoluteInitiationTimeUs)
                .build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        byte[] testCccOpenRangingAbsoluteInitiationTimeTlvData =
                UwbUtil.getByteArray("00010102010104010905010109048001000011010103010"
                        + "11B01062C0100A3020001A4020000A50100A602D0020802B004140101"
                        + "2B0810270000000000000E0100");

        assertThat(tlvs.getNoOfParams()).isEqualTo(17);
        assertThat(tlvs.getByteArray()).isEqualTo(
                testCccOpenRangingAbsoluteInitiationTimeTlvData);
    }

    @Test
    public void testCccOpenRangingParams_withRangeDataNtfConfigSupportedAndDisabled()
            throws Exception {
        // Setup the DeviceConfigFacade flag to indicate that RANGE_DATA_NTF_CONFIG and related
        // fields will be configured for a CCC ranging session.
        when(mDeviceConfigFacade.isCccSupportedRangeDataNtfConfig()).thenReturn(true);

        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        // Setup the expected values to be the default ones, since the parameters are not
        // configured.
        String expectedTlvStr = TEST_CCC_OPEN_RANGING_TLV
                + RANGE_DATA_NTF_CONFIG_DISABLED_TLV
                + RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT_TLV
                + RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT_TLV;

        assertThat(tlvs.getNoOfParams()).isEqualTo(19);
        assertThat(tlvs.getByteArray()).isEqualTo(UwbUtil.getByteArray(expectedTlvStr));
    }

    @Test
    public void testCccOpenRangingParams_withRangeDataNtfConfigSupportedAndEnabled()
            throws Exception {
        // Setup the DeviceConfigFacade flag to indicate that RANGE_DATA_NTF_CONFIG and related
        // fields will be configured for a CCC ranging session (to default value of Disabled).
        when(mDeviceConfigFacade.isCccSupportedRangeDataNtfConfig()).thenReturn(true);

        CccOpenRangingParams.Builder builder = new CccOpenRangingParams
                .Builder(TEST_CCC_OPEN_RANGING_PARAMS);
        CccOpenRangingParams params = builder.setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE)
                .build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        // Setup the expected values to be enabled for RANGE_DATA_NTF_CONFIG and default for the
        // other parameters, since they are not configured.
        String expectedTlvStr = TEST_CCC_OPEN_RANGING_TLV
                + RANGE_DATA_NTF_CONFIG_ENABLED_TLV
                + RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT_TLV
                + RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT_TLV;

        assertThat(tlvs.getNoOfParams()).isEqualTo(19);
        assertThat(tlvs.getByteArray()).isEqualTo(UwbUtil.getByteArray(expectedTlvStr));
    }

    @Test
    public void testCccOpenRangingParams_withRangeDataNtfConfigSupportedAndConfigured()
            throws Exception {
        // Setup the DeviceConfigFacade flag to indicate that RANGE_DATA_NTF_CONFIG and related
        // fields will be configured for a CCC ranging session (to default value of Disabled).
        when(mDeviceConfigFacade.isCccSupportedRangeDataNtfConfig()).thenReturn(true);

        CccOpenRangingParams.Builder builder = new CccOpenRangingParams
                .Builder(TEST_CCC_OPEN_RANGING_PARAMS);
        CccOpenRangingParams params = builder
                .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG)
                .setRangeDataNtfProximityNear(100)
                .setRangeDataNtfProximityFar(200)
                .build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params, CccParams.PROTOCOL_VERSION_1_0);

        // Setup the expected values to match the configured parameter values.
        String expectedTlvStr = TEST_CCC_OPEN_RANGING_TLV
                + RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG_TLV
                + RANGE_DATA_NTF_PROXIMITY_NEAR_TLV
                + RANGE_DATA_NTF_PROXIMITY_FAR_TLV;

        assertThat(tlvs.getNoOfParams()).isEqualTo(19);
        assertThat(tlvs.getByteArray()).isEqualTo(UwbUtil.getByteArray(expectedTlvStr));
    }
}
