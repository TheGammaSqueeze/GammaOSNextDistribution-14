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

import static com.android.server.uwb.params.CccDecoderTest.TEST_CCC_SPECIFICATION_TLV_DATA_STRING;
import static com.android.server.uwb.params.CccDecoderTest.TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS;
import static com.android.server.uwb.params.FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1;
import static com.android.server.uwb.params.FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_2;
import static com.android.server.uwb.params.FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1;
import static com.android.server.uwb.params.RadarDecoderTest.TEST_RADAR_SPECIFICATION_TLV_DATA_STRING;
import static com.android.server.uwb.params.RadarDecoderTest.TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_1_1;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_2_0;

import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;
import com.android.uwb.flags.FeatureFlags;

import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link com.android.server.uwb.params.GenericDecoder}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class GenericDecoderTest {
    private static final byte[] TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_1 =
            UwbUtil.getByteArray(
                    "C00101" // SUPPORTED_POWER_STATS_QUERY
                            + TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1
                            + TEST_CCC_SPECIFICATION_TLV_DATA_STRING
                            + TEST_RADAR_SPECIFICATION_TLV_DATA_STRING);
    private static final int TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_1 =
            1
                    + TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1
                    + TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS
                    + TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS;

    private static final byte[] TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_2 =
            UwbUtil.getByteArray(
                    "C00101" // SUPPORTED_POWER_STATS_QUERY
                            + FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_STRING_VER_2
                            + TEST_CCC_SPECIFICATION_TLV_DATA_STRING
                            + TEST_RADAR_SPECIFICATION_TLV_DATA_STRING);
    private static final int TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_2 =
            1
                    + TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_2
                    + TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS
                    + TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS;

    @Mock private UwbInjector mUwbInjector;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private FeatureFlags mFeatureFlags;

    private GenericDecoder mGenericDecoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isCccSupportedSyncCodesLittleEndian()).thenReturn(true);

        when(mUwbInjector.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.cr423CleanupIntervalScheduling()).thenReturn(true);

        mGenericDecoder = new GenericDecoder(mUwbInjector);
    }

    @Test
    public void testGetGenericSpecificationVersion1() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_1,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_1);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                           PROTOCOL_VERSION_1_1);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecificationVersion1(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoderVersion1() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_1,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_1);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_1_1);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecificationVersion1(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
    }

    @Test
    public void testGetGenericSpecificationVersion2() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_2,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_2);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_2_0);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecificationVersion2(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoderVersion2() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERIC_SPECIFICATION_TLV_DATA_VER_2,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS_VER_2);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_2_0);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecificationVersion2(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoderVersion1_WithoutCCC() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        UwbUtil.getByteArray(
                                TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1
                                        + TEST_RADAR_SPECIFICATION_TLV_DATA_STRING),
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1
                                + TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_1_1);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isFalse();
        FiraDecoderTest.verifyFiraSpecificationVersion1(
                genericSpecificationParams.getFiraSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
        assertThat(genericSpecificationParams.getCccSpecificationParams()).isNull();
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoderVersion1_WithoutFira() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        UwbUtil.getByteArray(
                                TEST_CCC_SPECIFICATION_TLV_DATA_STRING
                                        + TEST_RADAR_SPECIFICATION_TLV_DATA_STRING),
                        TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS
                                + TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_1_1);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isFalse();
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        RadarDecoderTest.verifyRadarSpecification(
                genericSpecificationParams.getRadarSpecificationParams());
        assertThat(genericSpecificationParams.getFiraSpecificationParams()).isNull();
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoderVersion1_WithoutRadar() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        UwbUtil.getByteArray(
                                TEST_FIRA_SPECIFICATION_TLV_STRING_VER_1
                                        + TEST_CCC_SPECIFICATION_TLV_DATA_STRING),
                        TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS_VER_1
                                + TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams =
                mGenericDecoder.getParams(tlvDecoderBuffer, GenericSpecificationParams.class,
                            PROTOCOL_VERSION_1_1);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isFalse();
        FiraDecoderTest.verifyFiraSpecificationVersion1(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
        assertThat(genericSpecificationParams.getRadarSpecificationParams()).isNull();
    }
}
