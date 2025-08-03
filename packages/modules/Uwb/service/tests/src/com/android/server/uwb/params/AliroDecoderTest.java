/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.google.uwb.support.aliro.AliroParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.aliro.AliroParams.CHAPS_PER_SLOT_9;
import static com.google.uwb.support.aliro.AliroParams.HOPPING_CONFIG_MODE_ADAPTIVE;
import static com.google.uwb.support.aliro.AliroParams.HOPPING_CONFIG_MODE_CONTINUOUS;
import static com.google.uwb.support.aliro.AliroParams.HOPPING_SEQUENCE_AES;
import static com.google.uwb.support.aliro.AliroParams.PULSE_SHAPE_PRECURSOR_FREE;
import static com.google.uwb.support.aliro.AliroParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL;
import static com.google.uwb.support.aliro.AliroParams.UWB_CONFIG_0;

import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroProtocolVersion;
import com.google.uwb.support.aliro.AliroPulseShapeCombo;
import com.google.uwb.support.aliro.AliroRangingStartedParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.params.AliroDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class AliroDecoderTest {
    private static final byte[] TEST_ALIRO_RANGING_OPENED_TLV_DATA =
            UwbUtil.getByteArray("0a0402000100"
                            + "a01001000200000000000000000000000000"
                            + "a1080200010002000100"
                            + "090402000100"
                            + "140101");
    private static final int TEST_ALIRO_RANGING_OPENED_TLV_NUM_PARAMS = 5;
    public static final String TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING =
            "a00111"
                    + "a10400000082"
                    + "a20168"
                    + "a30103"
                    + "a4020102"
                    + "a50100"
                    + "a60112"
                    + "a7040a000000"
                    + "a80401000000"
                    + "a90401000000";

    public static final String TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING_PRIORITIZED_CHANNELS =
            "a00111"
                    + "a10400000082"
                    + "a20168"
                    + "a4020102"
                    + "a50100"
                    + "a60112"
                    + "a7040a000000"
                    + "a80401000000"
                    + "a90401000000"
                    + "aa020509";

    private static final String TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING_UWBS_MAX_PPM =
            "a00111"
                    + "a10400000082"
                    + "a20168"
                    + "a30103"
                    + "a4020102"
                    + "a50100"
                    + "a60112"
                    + "a7040a000000"
                    + "a80401000000"
                    + "a90401000000"
                    + "ab02012f";

    private static final byte[] TEST_ALIRO_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray(TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING);

    private static final byte[] TEST_ALIRO_SPECIFICATION_TLV_DATA_PRIORITIZED_CHANNELS =
            UwbUtil.getByteArray(TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING_PRIORITIZED_CHANNELS);
    private static final byte[] TEST_ALIRO_SPECIFICATION_TLV_DATA_UWBS_MAX_PPM =
            UwbUtil.getByteArray(TEST_ALIRO_SPECIFICATION_TLV_DATA_STRING_UWBS_MAX_PPM);

    public static final int TEST_ALIRO_SPECIFICATION_TLV_NUM_PARAMS = 10;
    public static final int TEST_ALIRO_SPECIFICATION_TLV_DATA_UWBS_MAX_PPM_NUM_PARAMS = 11;

    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private DeviceConfigFacade mDeviceConfigFacade;
    private AliroDecoder mAliroDecoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isCccSupportedSyncCodesLittleEndian()).thenReturn(true);
        mAliroDecoder = new AliroDecoder(mUwbInjector);
    }

    private void verifyAliroRangingOpend(AliroRangingStartedParams aliroRangingStartedParams) {
        assertThat(aliroRangingStartedParams).isNotNull();

        assertThat(aliroRangingStartedParams.getStartingStsIndex()).isEqualTo(0x00010002);
        assertThat(aliroRangingStartedParams.getHopModeKey()).isEqualTo(0x00020001);
        assertThat(aliroRangingStartedParams.getUwbTime0()).isEqualTo(0x0001000200010002L);
        assertThat(aliroRangingStartedParams.getRanMultiplier()).isEqualTo(0x00010002 / 96);
    }

    public static void verifyAliroSpecification(AliroSpecificationParams aliroSpecificationParams) {
        assertThat(aliroSpecificationParams).isNotNull();

        assertThat(aliroSpecificationParams.getProtocolVersions()).isEqualTo(List.of(
                AliroProtocolVersion.fromBytes(new byte[] {1, 2}, 0)));
        assertThat(aliroSpecificationParams.getUwbConfigs()).isEqualTo(List.of(UWB_CONFIG_0));
        assertThat(aliroSpecificationParams.getPulseShapeCombos()).isEqualTo(
                List.of(new AliroPulseShapeCombo(
                        PULSE_SHAPE_PRECURSOR_FREE, PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)));
        assertThat(aliroSpecificationParams.getRanMultiplier()).isEqualTo(10);
        assertThat(aliroSpecificationParams.getChapsPerSlot()).isEqualTo(
                List.of(CHAPS_PER_SLOT_3, CHAPS_PER_SLOT_9));
        assertThat(aliroSpecificationParams.getSyncCodes()).isEqualTo(
                List.of(26, 32));
        assertThat(aliroSpecificationParams.getChannels()).isEqualTo(List.of(5, 9));
        assertThat(aliroSpecificationParams.getHoppingConfigModes()).isEqualTo(
                List.of(HOPPING_CONFIG_MODE_CONTINUOUS, HOPPING_CONFIG_MODE_ADAPTIVE));
        assertThat(aliroSpecificationParams.getHoppingSequences()).isEqualTo(
                List.of(HOPPING_SEQUENCE_AES));
        assertThat(aliroSpecificationParams.getMaxRangingSessionNumber()).isEqualTo(1);
        assertThat(aliroSpecificationParams.getMinUwbInitiationTimeMs()).isEqualTo(1);
    }

    @Test
    public void testGetAliroRangingOpened() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_RANGING_OPENED_TLV_DATA,
                        TEST_ALIRO_RANGING_OPENED_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroRangingStartedParams aliroRangingStartedParams = mAliroDecoder.getParams(
                tlvDecoderBuffer,
                AliroRangingStartedParams.class,
                AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroRangingOpend(aliroRangingStartedParams);
    }

    @Test
    public void testGetAliroSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_SPECIFICATION_TLV_DATA, TEST_ALIRO_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroSpecificationParams aliroSpecificationParams = mAliroDecoder.getParams(
                tlvDecoderBuffer, AliroSpecificationParams.class, AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroSpecification(aliroSpecificationParams);
    }

    @Test
    public void testGetAliroRangingOpenedViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_RANGING_OPENED_TLV_DATA,
                        TEST_ALIRO_RANGING_OPENED_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroRangingStartedParams aliroRangingStartedParams = TlvDecoder
                .getDecoder(AliroParams.PROTOCOL_NAME, mUwbInjector)
                .getParams(tlvDecoderBuffer, AliroRangingStartedParams.class,
                        AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroRangingOpend(aliroRangingStartedParams);
    }

    @Test
    public void testGetAliroSpecificationViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_SPECIFICATION_TLV_DATA, TEST_ALIRO_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroSpecificationParams aliroSpecificationParams = TlvDecoder
                .getDecoder(AliroParams.PROTOCOL_NAME, mUwbInjector)
                .getParams(tlvDecoderBuffer, AliroSpecificationParams.class,
                        AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroSpecification(aliroSpecificationParams);
    }

    @Test
    public void testGetAliroSpecificationWithPrioritizedChannel() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_SPECIFICATION_TLV_DATA_PRIORITIZED_CHANNELS,
                        TEST_ALIRO_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroSpecificationParams aliroSpecificationParams = mAliroDecoder.getParams(
                tlvDecoderBuffer, AliroSpecificationParams.class, AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroSpecification(aliroSpecificationParams);
    }

    @Test
    public void testGetAliroSpecificationWithUwbsMaxPPM() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_ALIRO_SPECIFICATION_TLV_DATA_UWBS_MAX_PPM,
                        TEST_ALIRO_SPECIFICATION_TLV_DATA_UWBS_MAX_PPM_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        AliroSpecificationParams aliroSpecificationParams = mAliroDecoder.getParams(
                tlvDecoderBuffer, AliroSpecificationParams.class, AliroParams.PROTOCOL_VERSION_1_0);
        verifyAliroSpecification(aliroSpecificationParams);
    }
}
