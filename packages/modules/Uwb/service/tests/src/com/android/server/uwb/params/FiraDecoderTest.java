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
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_INTERLEAVING_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_DT_TAG_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.KEY_LENGTH_256_BITS_SUPPORTED;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_1_1;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_2_0;
import static com.google.uwb.support.fira.FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_ESS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_OWR_AOA_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_OWR_DL_TDOA_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_OWR_UL_TDOA_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;
import com.android.uwb.flags.FeatureFlags;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.BprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.HprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.EnumSet;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.params.FiraDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class FiraDecoderTest {
    public static final String TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1 =
            "000401010102" // Phy version
                    + "010401050103" // Mac version
                    + "020103" // Device roles
                    + "03011F" // Ranging method
                    + "040103" // STS config
                    + "050103" // Multi node modes
                    + "060100" // Ranging time struct
                    + "070100" // Scheduled mode
                    + "080100" // Hopping mode
                    + "090101" // Block striding
                    + "0A0101" // Uwb initiation time
                    + "0B0109" // Channels
                    + "0C010B" // Rframe config
                    + "0D0103" // Cc constraint length
                    + "0E0101" // Bprf parameter set
                    + "0F050300000000" // hprf parameter set
                    + "10010F" // Aoa
                    + "110101" // Extended mac
                    + "E30101"
                    + "E40401010101"
                    + "E50403000000"
                    + "E601FF"
                    + "E70101"
                    + "E80401010101"
                    + "E90401000000";
    private static final byte[] TEST_FIRA_SPECIFICATION_TLV_DATA_VER_1 =
            UwbUtil.getByteArray(TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1);
    public static final int TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1 = 25;

    public static final String TEST_FIRA_SPECIFICATION_TLV_STRING_VER_2 =
            "000120" // Max message size
                    + "010110" // Max data payload size
                    + "020401010102" // Phy version
                    + "030401050103" // Mac version
                    + "040101" // Device type
                    + "05020301" // Device roles
                    + "0602FF00" // Ranging method
                    + "070103" // STS config
                    + "080103" // Multi node modes
                    + "090100" // Ranging time struct
                    + "0A0100" // Scheduled mode
                    + "0B0100" // Hopping mode
                    + "0C0101" // Block striding
                    + "0D0101" // Uwb initiation time
                    + "0E0109" // Channels
                    + "0F010B" // Rframe config
                    + "100103" // Cc constraint length
                    + "110101" // Bprf parameter set
                    + "12050300000000"// hprf parameter set
                    + "13010F" // Aoa
                    + "140101" // Extended mac
                    + "150100" // Suspend ranging
                    + "160101" // Session key length
                    + "180110" // Dt tag max active ranging rounds
                    + "190101" //Dt tag block skipping
                    + "1A0100" //Psdu length support
                    + "E30101"
                    + "E40401010101"
                    + "E50403000000"
                    + "E601FF"
                    + "E70101"
                    + "E80401010101"
                    + "E90401000000";
    private static final byte[] TEST_FIRA_SPECIFICATION_TLV_DATA_VER_2 =
            UwbUtil.getByteArray(TEST_FIRA_SPECIFICATION_TLV_STRING_VER_2);
    public static final int TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_2 = 33;

    private FiraDecoder mFiraDecoder;

    @Mock
    private UwbInjector mUwbInjector;
    @Mock private FeatureFlags mFeatureFlags;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbInjector.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.cr423CleanupIntervalScheduling()).thenReturn(true);

        mFiraDecoder = new FiraDecoder(mUwbInjector);
    }

    public static void verifyFiraSpecificationVersion2(
            FiraSpecificationParams firaSpecificationParams) {
        assertThat(firaSpecificationParams).isNotNull();

        assertThat(firaSpecificationParams.getMinPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 1}, 0));
        assertThat(firaSpecificationParams.getMaxPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 2}, 0));
        assertThat(firaSpecificationParams.getMinMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 5}, 0));
        assertThat(firaSpecificationParams.getMaxMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 3}, 0));

        assertThat(firaSpecificationParams.getDeviceRoleCapabilities()).isEqualTo(
                EnumSet.of(HAS_CONTROLEE_RESPONDER_SUPPORT, HAS_CONTROLLER_RESPONDER_SUPPORT,
                        HAS_CONTROLEE_INITIATOR_SUPPORT, HAS_CONTROLLER_INITIATOR_SUPPORT,
                        HAS_DT_TAG_SUPPORT));

        assertThat(firaSpecificationParams.getRangingRoundCapabilities()).isEqualTo(
                EnumSet.of(HAS_DS_TWR_SUPPORT, HAS_SS_TWR_SUPPORT, HAS_OWR_UL_TDOA_SUPPORT,
                        HAS_OWR_DL_TDOA_SUPPORT, HAS_OWR_AOA_SUPPORT, HAS_ESS_TWR_SUPPORT));
        assertThat(firaSpecificationParams.hasNonDeferredModeSupport()).isTrue();

        assertThat(firaSpecificationParams.getStsCapabilities()).isEqualTo(
                EnumSet.of(HAS_STATIC_STS_SUPPORT, HAS_DYNAMIC_STS_SUPPORT));

        assertThat(firaSpecificationParams.getMultiNodeCapabilities()).isEqualTo(
                EnumSet.of(HAS_ONE_TO_MANY_SUPPORT, HAS_UNICAST_SUPPORT));

        assertThat(firaSpecificationParams.hasBlockStridingSupport()).isEqualTo(true);

        assertThat(firaSpecificationParams.hasRssiReportingSupport()).isTrue();

        assertThat(firaSpecificationParams.hasDiagnosticsSupport()).isTrue();

        assertThat(firaSpecificationParams.getSupportedChannels()).isEqualTo(List.of(5, 9));

        assertThat(firaSpecificationParams.getMaxRangingSessionNumber()).isEqualTo(1);

        assertThat(firaSpecificationParams.getRframeCapabilities()).isEqualTo(
                EnumSet.of(HAS_SP0_RFRAME_SUPPORT, HAS_SP1_RFRAME_SUPPORT,
                        HAS_SP3_RFRAME_SUPPORT));

        assertThat(firaSpecificationParams.getPrfCapabilities()).isEqualTo(
                EnumSet.of(HAS_BPRF_SUPPORT, HAS_HPRF_SUPPORT));
        assertThat(firaSpecificationParams.getPsduDataRateCapabilities()).isEqualTo(
                EnumSet.of(HAS_6M81_SUPPORT, HAS_7M80_SUPPORT, HAS_27M2_SUPPORT, HAS_31M2_SUPPORT));

        assertThat(firaSpecificationParams.getAoaCapabilities()).isEqualTo(
                EnumSet.of(HAS_AZIMUTH_SUPPORT, HAS_ELEVATION_SUPPORT, HAS_FULL_AZIMUTH_SUPPORT,
                        HAS_FOM_SUPPORT, HAS_INTERLEAVING_SUPPORT));

        assertThat(firaSpecificationParams.getBprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(BprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT));

        assertThat(firaSpecificationParams.getHprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(HprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT,
                        HprfParameterSetCapabilityFlag.HAS_SET_2_SUPPORT));

        assertThat(firaSpecificationParams.getRangeDataNtfConfigCapabilities()).isEqualTo(
                EnumSet.of(RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_DISABLE,
                        RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_ENABLE));

        assertEquals(firaSpecificationParams.getDeviceType(), RANGING_DEVICE_TYPE_CONTROLLER);
        assertFalse(firaSpecificationParams.hasSuspendRangingSupport());
        assertEquals(firaSpecificationParams.getSessionKeyLength(), KEY_LENGTH_256_BITS_SUPPORTED);
        assertEquals(firaSpecificationParams.getDtTagMaxActiveRr(), 16);

    }

    @Test
    public void testGetFiraSpecificationVersion2() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA_VER_2,
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_2);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = mFiraDecoder.getParams(
                tlvDecoderBuffer, FiraSpecificationParams.class, PROTOCOL_VERSION_2_0);
        verifyFiraSpecificationVersion2(firaSpecificationParams);
    }

    @Test
    public void testGetFiraSpecificationViaTlvDecoderVersion2() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA_VER_2,
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_2);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = TlvDecoder
                .getDecoder(FiraParams.PROTOCOL_NAME, mUwbInjector)
                .getParams(tlvDecoderBuffer, FiraSpecificationParams.class, PROTOCOL_VERSION_2_0);
        verifyFiraSpecificationVersion2(firaSpecificationParams);
    }


    public static void verifyFiraSpecificationVersion1(
            FiraSpecificationParams firaSpecificationParams) {
        assertThat(firaSpecificationParams).isNotNull();

        assertThat(firaSpecificationParams.getMinPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 1}, 0));
        assertThat(firaSpecificationParams.getMaxPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 2}, 0));
        assertThat(firaSpecificationParams.getMinMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 5}, 0));
        assertThat(firaSpecificationParams.getMaxMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[]{1, 3}, 0));

        assertThat(firaSpecificationParams.getDeviceRoleCapabilities()).isEqualTo(
                EnumSet.of(HAS_CONTROLEE_RESPONDER_SUPPORT, HAS_CONTROLLER_RESPONDER_SUPPORT,
                        HAS_CONTROLEE_INITIATOR_SUPPORT, HAS_CONTROLLER_INITIATOR_SUPPORT));

        assertThat(firaSpecificationParams.getRangingRoundCapabilities()).isEqualTo(
                EnumSet.of(HAS_DS_TWR_SUPPORT, HAS_SS_TWR_SUPPORT));
        assertThat(firaSpecificationParams.hasNonDeferredModeSupport()).isTrue();

        assertThat(firaSpecificationParams.getStsCapabilities()).isEqualTo(
                EnumSet.of(HAS_STATIC_STS_SUPPORT, HAS_DYNAMIC_STS_SUPPORT));

        assertThat(firaSpecificationParams.getMultiNodeCapabilities()).isEqualTo(
                EnumSet.of(HAS_ONE_TO_MANY_SUPPORT, HAS_UNICAST_SUPPORT));

        assertThat(firaSpecificationParams.hasBlockStridingSupport()).isEqualTo(true);

        assertThat(firaSpecificationParams.hasRssiReportingSupport()).isTrue();

        assertThat(firaSpecificationParams.hasDiagnosticsSupport()).isTrue();

        assertThat(firaSpecificationParams.getSupportedChannels()).isEqualTo(List.of(5, 9));

        assertThat(firaSpecificationParams.getMaxRangingSessionNumber()).isEqualTo(1);

        assertThat(firaSpecificationParams.getRframeCapabilities()).isEqualTo(
                EnumSet.of(HAS_SP0_RFRAME_SUPPORT, HAS_SP1_RFRAME_SUPPORT,
                        HAS_SP3_RFRAME_SUPPORT));

        assertThat(firaSpecificationParams.getPrfCapabilities()).isEqualTo(
                EnumSet.of(HAS_BPRF_SUPPORT, HAS_HPRF_SUPPORT));
        assertThat(firaSpecificationParams.getPsduDataRateCapabilities()).isEqualTo(
                EnumSet.of(HAS_6M81_SUPPORT, HAS_7M80_SUPPORT, HAS_27M2_SUPPORT, HAS_31M2_SUPPORT));

        assertThat(firaSpecificationParams.getAoaCapabilities()).isEqualTo(
                EnumSet.of(HAS_AZIMUTH_SUPPORT, HAS_ELEVATION_SUPPORT, HAS_FULL_AZIMUTH_SUPPORT,
                        HAS_FOM_SUPPORT, HAS_INTERLEAVING_SUPPORT));

        assertThat(firaSpecificationParams.getBprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(BprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT));

        assertThat(firaSpecificationParams.getHprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(HprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT,
                        HprfParameterSetCapabilityFlag.HAS_SET_2_SUPPORT));

        assertThat(firaSpecificationParams.getRangeDataNtfConfigCapabilities()).isEqualTo(
                EnumSet.of(RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_DISABLE,
                        RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_ENABLE));
    }

    @Test
    public void testGetFiraSpecificationVersion1() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA_VER_1,
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = mFiraDecoder.getParams(
                tlvDecoderBuffer, FiraSpecificationParams.class, PROTOCOL_VERSION_1_1);
        verifyFiraSpecificationVersion1(firaSpecificationParams);
    }

    @Test
    public void testGetFiraSpecificationViaTlvDecoderVersion1() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA_VER_1,
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = TlvDecoder
                .getDecoder(FiraParams.PROTOCOL_NAME, mUwbInjector)
                .getParams(tlvDecoderBuffer, FiraSpecificationParams.class, PROTOCOL_VERSION_1_1);
        verifyFiraSpecificationVersion1(firaSpecificationParams);
    }
}
