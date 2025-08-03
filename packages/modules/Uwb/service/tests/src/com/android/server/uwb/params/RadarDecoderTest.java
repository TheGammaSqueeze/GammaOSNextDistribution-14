/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.radar.RadarData;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarParams.RadarCapabilityFlag;
import com.google.uwb.support.radar.RadarSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.EnumSet;

/** Unit tests for {@link com.android.server.uwb.params.RadarDecoder}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RadarDecoderTest {
    private static final FiraProtocolVersion PROTOCOL_VERSION_DUMMY = new FiraProtocolVersion(0, 0);
    public static final String TEST_RADAR_SPECIFICATION_TLV_DATA_STRING = "b00101";
    private static final byte[] TEST_RADAR_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray(TEST_RADAR_SPECIFICATION_TLV_DATA_STRING);
    public static final int TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS = 1;
    private static final EnumSet<RadarCapabilityFlag> RADAR_CAPABILITIES =
            EnumSet.of(RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT);
    private final RadarDecoder mRadarDecoder = new RadarDecoder();
    @Mock
    private UwbInjector mUwbInjector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    public static void verifyRadarSpecification(RadarSpecificationParams radarSpecificationParams) {
        assertThat(radarSpecificationParams).isNotNull();
        assertThat(radarSpecificationParams.getRadarCapabilities()).isEqualTo(RADAR_CAPABILITIES);
    }

    @Test
    public void testGetParams_invalidParamType() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_RADAR_SPECIFICATION_TLV_DATA, TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS);

        assertThat(mRadarDecoder.getParams(tlvDecoderBuffer, RadarData.class,
                                 PROTOCOL_VERSION_DUMMY)).isNull();
    }

    @Test
    public void testGetRadarSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_RADAR_SPECIFICATION_TLV_DATA, TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        RadarSpecificationParams radarSpecificationParams =
                mRadarDecoder.getParams(tlvDecoderBuffer, RadarSpecificationParams.class,
                                 PROTOCOL_VERSION_DUMMY);
        verifyRadarSpecification(radarSpecificationParams);
    }

    @Test
    public void testGetRadarSpecificationViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_RADAR_SPECIFICATION_TLV_DATA, TEST_RADAR_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        RadarSpecificationParams radarSpecificationParams =
                TlvDecoder.getDecoder(RadarParams.PROTOCOL_NAME, mUwbInjector)
                        .getParams(tlvDecoderBuffer, RadarSpecificationParams.class,
                                 PROTOCOL_VERSION_DUMMY);
        verifyRadarSpecification(radarSpecificationParams);
    }
}
