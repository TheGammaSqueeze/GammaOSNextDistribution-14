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

package com.android.server.uwb;

import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
import static com.google.uwb.support.fira.FiraParams.AOA_TYPE_AZIMUTH_AND_ELEVATION;
import static com.google.uwb.support.fira.FiraParams.BPRF_PHR_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_32;
import static com.google.uwb.support.fira.FiraParams.MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_MANY_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T32_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_HPRF;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_1_1;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_7M80;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.SESSION_TYPE_RANGING;
import static com.google.uwb.support.fira.FiraParams.SFD_ID_VALUE_3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.STS_LENGTH_128_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.STS_SEGMENT_COUNT_VALUE_2;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;
import android.uwb.UwbAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.data.UwbConfigStatusData;
import com.android.server.uwb.data.UwbTlvData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.proto.UwbStatsLog;
import com.android.uwb.flags.FeatureFlags;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link UwbConfigurationManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbConfigurationManagerTest {
    private static final String TEST_CHIP_ID = "testChipId";
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock private FeatureFlags mFeatureFlags;
    private UwbConfigurationManager mUwbConfigurationManager;
    @Mock
    private UwbSessionManager.UwbSession mUwbSession;
    private FiraOpenSessionParams mFiraParams;
    @Mock
    private UwbSessionManager.UwbSession mRadarSession;

    private static final RadarOpenSessionParams TEST_RADAR_OPEN_SESSION_PARAMS =
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
                    .setRadarDataType(RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES)
                    .build();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUwbConfigurationManager = new UwbConfigurationManager(mNativeUwbManager, mUwbInjector);
        mFiraParams = getFiraParams();

        when(mUwbSession.getSessionId()).thenReturn(1);
        when(mUwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mUwbSession.getProfileType()).thenReturn(
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA);
        when(mUwbSession.getParams()).thenReturn(mFiraParams);

        when(mRadarSession.getSessionId()).thenReturn(1);
        when(mRadarSession.getProtocolName()).thenReturn(RadarParams.PROTOCOL_NAME);
        when(mRadarSession.getParams()).thenReturn(TEST_RADAR_OPEN_SESSION_PARAMS);

        // Setup the unit tests to have the default behavior of using the UWBS UCI version.
        when(mUwbInjector.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.useUwbsUciVersion()).thenReturn(true);
    }

    @Test
    public void testSetAppConfigurations() throws Exception {
        byte[] cfgStatus = {0x01, UwbUciConstants.STATUS_CODE_OK};
        UwbConfigStatusData appConfig = new UwbConfigStatusData(UwbUciConstants.STATUS_CODE_OK,
                1, cfgStatus);
        when(mNativeUwbManager.setAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), anyString())).thenReturn(appConfig);

        int status = mUwbConfigurationManager
                .setAppConfigurations(mUwbSession.getSessionId(), mFiraParams, TEST_CHIP_ID,
                        PROTOCOL_VERSION_1_1);

        verify(mNativeUwbManager).setAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), eq(TEST_CHIP_ID));
        assertEquals(UwbUciConstants.STATUS_CODE_OK, status);
    }

    @Test
    public void testSetAppConfigurations_radarSession() throws Exception {
        byte[] cfgStatus = {0x01, UwbUciConstants.STATUS_CODE_OK};
        UwbConfigStatusData appConfig = new UwbConfigStatusData(UwbUciConstants.STATUS_CODE_OK,
                1, cfgStatus);
        when(mNativeUwbManager.setRadarAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), anyString())).thenReturn(appConfig);

        int status = mUwbConfigurationManager.setAppConfigurations(
                mRadarSession.getSessionId(), TEST_RADAR_OPEN_SESSION_PARAMS, TEST_CHIP_ID,
                PROTOCOL_VERSION_1_1);

        verify(mNativeUwbManager).setRadarAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), eq(TEST_CHIP_ID));
        assertEquals(UwbUciConstants.STATUS_CODE_OK, status);
    }

    @Test
    public void testGetAppConfigurations() throws Exception {
        byte[] tlvs = {0x01, 0x02, 0x02, 0x03};
        UwbTlvData getAppConfig = new UwbTlvData(UwbUciConstants.STATUS_CODE_OK, 1, tlvs);
        when(mNativeUwbManager.getAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), anyString())).thenReturn(getAppConfig);

        mUwbConfigurationManager.getAppConfigurations(mUwbSession.getSessionId(),
                mFiraParams.getProtocolName(), new byte[0], FiraOpenSessionParams.class,
                TEST_CHIP_ID, FiraParams.PROTOCOL_VERSION_1_1);

        verify(mNativeUwbManager).getAppConfigurations(anyInt(), anyInt(), anyInt(),
                any(byte[].class), eq(TEST_CHIP_ID));
    }

    @Test
    public void testGetCapsInfo() throws Exception {
        byte[] tlvs = {0x01, 0x02, 0x02, 0x03};
        UwbTlvData getAppConfig = new UwbTlvData(UwbUciConstants.STATUS_CODE_OK, 1, tlvs);
        when(mNativeUwbManager.getCapsInfo(anyString())).thenReturn(getAppConfig);

        mUwbConfigurationManager.getCapsInfo(mFiraParams.getProtocolName(),
                FiraOpenSessionParams.class, TEST_CHIP_ID, any());

        verify(mNativeUwbManager).getCapsInfo(TEST_CHIP_ID);
    }

    private FiraOpenSessionParams getFiraParams() {
        FiraProtocolVersion protocolVersion = FiraParams.PROTOCOL_VERSION_1_1;
        int sessionId = 10;
        int sessionType = SESSION_TYPE_RANGING;
        int deviceType = RANGING_DEVICE_TYPE_CONTROLEE;
        int deviceRole = RANGING_DEVICE_ROLE_INITIATOR;
        int rangingRoundUsage = RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
        int multiNodeMode = MULTI_NODE_MODE_MANY_TO_MANY;
        int addressMode = MAC_ADDRESS_MODE_8_BYTES;
        UwbAddress deviceAddress = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress1 = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress2 =
                UwbAddress.fromBytes(new byte[] {(byte) 0xFF, (byte) 0xFE, 3, 4, 5, 6, 7, 8});
        List<UwbAddress> destAddressList = new ArrayList<>();
        destAddressList.add(destAddress1);
        destAddressList.add(destAddress2);
        int initiationTime = 100;
        int slotDurationRstu = 2400;
        int slotsPerRangingRound = 10;
        int rangingIntervalMs = 100;
        int blockStrideLength = 2;
        int maxRangingRoundRetries = 3;
        int sessionPriority = 100;
        boolean hasRangingResultReportMessage = true;
        boolean hasControlMessage = true;
        boolean hasRangingControlPhase = false;
        int measurementReportType = MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
        int inBandTerminationAttemptCount = 8;
        int channelNumber = 10;
        int preambleCodeIndex = 12;
        int rframeConfig = RFRAME_CONFIG_SP1;
        int prfMode = PRF_MODE_HPRF;
        int preambleDuration = PREAMBLE_DURATION_T32_SYMBOLS;
        int sfdId = SFD_ID_VALUE_3;
        int stsSegmentCount = STS_SEGMENT_COUNT_VALUE_2;
        int stsLength = STS_LENGTH_128_SYMBOLS;
        byte[] sessionKey = new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
        byte[] subsessionKey = new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
        int psduDataRate = PSDU_DATA_RATE_7M80;
        int bprfPhrDataRate = BPRF_PHR_DATA_RATE_6M81;
        int fcsType = MAC_FCS_TYPE_CRC_32;
        boolean isTxAdaptivePayloadPowerEnabled = true;
        int stsConfig = STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
        int subSessionId = 24;
        byte[] vendorId = new byte[] {(byte) 0xFE, (byte) 0xDC};
        byte[] staticStsIV = new byte[] {(byte) 0xDF, (byte) 0xCE, (byte) 0xAB, 0x12, 0x34, 0x56};
        boolean isKeyRotationEnabled = true;
        int keyRotationRate = 15;
        int aoaResultRequest = AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;
        int rangeDataNtfProximityNear = 50;
        int rangeDataNtfProximityFar = 200;
        boolean hasTimeOfFlightReport = true;
        boolean hasAngleOfArrivalAzimuthReport = true;
        boolean hasAngleOfArrivalElevationReport = true;
        boolean hasAngleOfArrivalFigureOfMeritReport = true;
        int aoaType = AOA_TYPE_AZIMUTH_AND_ELEVATION;
        int numOfMsrmtFocusOnRange = 1;
        int numOfMsrmtFocusOnAoaAzimuth = 2;
        int numOfMsrmtFocusOnAoaElevation = 3;

        FiraOpenSessionParams params =
                new FiraOpenSessionParams.Builder()
                        .setProtocolVersion(protocolVersion)
                        .setSessionId(sessionId)
                        .setSessionType(sessionType)
                        .setDeviceType(deviceType)
                        .setDeviceRole(deviceRole)
                        .setRangingRoundUsage(rangingRoundUsage)
                        .setMultiNodeMode(multiNodeMode)
                        .setDeviceAddress(deviceAddress)
                        .setDestAddressList(destAddressList)
                        .setInitiationTime(initiationTime)
                        .setSlotDurationRstu(slotDurationRstu)
                        .setSlotsPerRangingRound(slotsPerRangingRound)
                        .setRangingIntervalMs(rangingIntervalMs)
                        .setBlockStrideLength(blockStrideLength)
                        .setMaxRangingRoundRetries(maxRangingRoundRetries)
                        .setSessionPriority(sessionPriority)
                        .setMacAddressMode(addressMode)
                        .setHasRangingResultReportMessage(hasRangingResultReportMessage)
                        .setHasControlMessage(hasControlMessage)
                        .setHasRangingControlPhase(hasRangingControlPhase)
                        .setMeasurementReportType(measurementReportType)
                        .setInBandTerminationAttemptCount(inBandTerminationAttemptCount)
                        .setChannelNumber(channelNumber)
                        .setPreambleCodeIndex(preambleCodeIndex)
                        .setRframeConfig(rframeConfig)
                        .setPrfMode(prfMode)
                        .setPreambleDuration(preambleDuration)
                        .setSfdId(sfdId)
                        .setStsSegmentCount(stsSegmentCount)
                        .setStsLength(stsLength)
                        .setSessionKey(sessionKey)
                        .setSubsessionKey(subsessionKey)
                        .setPsduDataRate(psduDataRate)
                        .setBprfPhrDataRate(bprfPhrDataRate)
                        .setFcsType(fcsType)
                        .setIsTxAdaptivePayloadPowerEnabled(isTxAdaptivePayloadPowerEnabled)
                        .setStsConfig(stsConfig)
                        .setSubSessionId(subSessionId)
                        .setVendorId(vendorId)
                        .setStaticStsIV(staticStsIV)
                        .setIsKeyRotationEnabled(isKeyRotationEnabled)
                        .setKeyRotationRate(keyRotationRate)
                        .setAoaResultRequest(aoaResultRequest)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataNtfProximityNear(rangeDataNtfProximityNear)
                        .setRangeDataNtfProximityFar(rangeDataNtfProximityFar)
                        .setHasTimeOfFlightReport(hasTimeOfFlightReport)
                        .setHasAngleOfArrivalAzimuthReport(hasAngleOfArrivalAzimuthReport)
                        .setHasAngleOfArrivalElevationReport(hasAngleOfArrivalElevationReport)
                        .setHasAngleOfArrivalFigureOfMeritReport(
                                hasAngleOfArrivalFigureOfMeritReport)
                        .setAoaType(aoaType)
                        .setMeasurementFocusRatio(
                                numOfMsrmtFocusOnRange,
                                numOfMsrmtFocusOnAoaAzimuth,
                                numOfMsrmtFocusOnAoaElevation)
                        .build();
        return params;
    }
}
