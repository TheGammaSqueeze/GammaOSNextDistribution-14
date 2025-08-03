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

package com.google.uwb.support;

import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
import static com.google.uwb.support.fira.FiraParams.AOA_TYPE_AZIMUTH_AND_ELEVATION;
import static com.google.uwb.support.fira.FiraParams.BPRF_PHR_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_32;
import static com.google.uwb.support.fira.FiraParams.MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_MANY_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T32_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_HPRF;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_7M80;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.SESSION_TYPE_RANGING;
import static com.google.uwb.support.fira.FiraParams.SFD_ID_VALUE_3;
import static com.google.uwb.support.fira.FiraParams.STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.STS_LENGTH_128_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.STS_SEGMENT_COUNT_VALUE_2;
import static com.google.uwb.support.fira.FiraParams.TX_TIMESTAMP_40_BIT;
import static com.google.uwb.support.fira.FiraParams.UL_TDOA_DEVICE_ID_16_BIT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraMulticastListUpdateStatusCode;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.fira.FiraStateChangeReasonCode;
import com.google.uwb.support.fira.FiraStatusCode;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FiraTests {
    @Test
    public void testOpenSessionParams() {
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
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;
        int rangeDataNtfProximityNear = 50;
        int rangeDataNtfProximityFar = 200;
        double rangeDataNtfAoaAzimuthLower = -0.5;
        double rangeDataNtfAoaAzimuthUpper = +1.5;
        double rangeDataNtfAoaElevationLower = -1.5;
        double rangeDataNtfAoaElevationUpper = +1.2;
        boolean hasTimeOfFlightReport = true;
        boolean hasAngleOfArrivalAzimuthReport = true;
        boolean hasAngleOfArrivalElevationReport = true;
        boolean hasAngleOfArrivalFigureOfMeritReport = true;
        int aoaType = AOA_TYPE_AZIMUTH_AND_ELEVATION;
        int numOfMsrmtFocusOnRange = 1;
        int numOfMsrmtFocusOnAoaAzimuth = 2;
        int numOfMsrmtFocusOnAoaElevation = 3;
        int ulTdoaTxIntervalMs = 1_000;
        int ulTdoaRandomWindowMS = 100;
        int ulTdoaDeviceIdType = UL_TDOA_DEVICE_ID_16_BIT;
        byte[] ulTdoaDeviceId = new byte[] {(byte) 0x0C, (byte) 0x0B};
        int ulTdoaTxTimestampType = TX_TIMESTAMP_40_BIT;
        int maxNumberOfMeasurements = 1;

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
                        .setRangeDataNtfAoaAzimuthLower(rangeDataNtfAoaAzimuthLower)
                        .setRangeDataNtfAoaAzimuthUpper(rangeDataNtfAoaAzimuthUpper)
                        .setRangeDataNtfAoaElevationLower(rangeDataNtfAoaElevationLower)
                        .setRangeDataNtfAoaElevationUpper(rangeDataNtfAoaElevationUpper)
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
                        .setUlTdoaTxIntervalMs(ulTdoaTxIntervalMs)
                        .setUlTdoaRandomWindowMs(ulTdoaRandomWindowMS)
                        .setUlTdoaDeviceIdType(ulTdoaDeviceIdType)
                        .setUlTdoaDeviceId(ulTdoaDeviceId)
                        .setUlTdoaTxTimestampType(ulTdoaTxTimestampType)
                        .setMaxNumberOfMeasurements(maxNumberOfMeasurements)
                        .build();

        assertEquals(params.getProtocolVersion(), protocolVersion);
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getSessionType(), sessionType);
        assertEquals(params.getDeviceType(), deviceType);
        assertEquals(params.getDeviceRole(), deviceRole);
        assertEquals(params.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(params.getMultiNodeMode(), multiNodeMode);
        assertEquals(params.getDeviceAddress(), deviceAddress);
        assertEquals(params.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(params.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(params.getInitiationTime(), initiationTime);
        assertEquals(params.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(params.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(params.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(params.getBlockStrideLength(), blockStrideLength);
        assertEquals(params.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(params.getSessionPriority(), sessionPriority);
        assertEquals(params.getMacAddressMode(), addressMode);
        assertEquals(params.hasRangingResultReportMessage(), hasRangingResultReportMessage);
        assertEquals(params.hasControlMessage(), hasControlMessage);
        assertEquals(params.hasRangingControlPhase(), hasRangingControlPhase);
        assertEquals(params.getMeasurementReportType(), measurementReportType);
        assertEquals(params.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(params.getChannelNumber(), channelNumber);
        assertEquals(params.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(params.getRframeConfig(), rframeConfig);
        assertEquals(params.getPrfMode(), prfMode);
        assertEquals(params.getPreambleDuration(), preambleDuration);
        assertEquals(params.getSfdId(), sfdId);
        assertEquals(params.getStsSegmentCount(), stsSegmentCount);
        assertEquals(params.getStsLength(), stsLength);
        assertArrayEquals(params.getSessionKey(), sessionKey);
        assertArrayEquals(params.getSubsessionKey(), subsessionKey);
        assertEquals(params.getPsduDataRate(), psduDataRate);
        assertEquals(params.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(params.getFcsType(), fcsType);
        assertEquals(params.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(params.getStsConfig(), stsConfig);
        assertEquals(params.getSubSessionId(), subSessionId);
        assertArrayEquals(params.getVendorId(), vendorId);
        assertArrayEquals(params.getStaticStsIV(), staticStsIV);
        assertEquals(params.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(params.getKeyRotationRate(), keyRotationRate);
        assertEquals(params.getAoaResultRequest(), aoaResultRequest);
        assertEquals(params.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(params.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(params.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(params.getRangeDataNtfAoaAzimuthLower(), rangeDataNtfAoaAzimuthLower, 0.0d);
        assertEquals(params.getRangeDataNtfAoaAzimuthUpper(), rangeDataNtfAoaAzimuthUpper, 0.0d);
        assertEquals(params.getRangeDataNtfAoaElevationLower(), rangeDataNtfAoaElevationLower,
                0.0d);
        assertEquals(params.getRangeDataNtfAoaElevationUpper(), rangeDataNtfAoaElevationUpper,
                0.0d);
        assertEquals(params.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(params.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(params.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                params.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(params.getAoaType(), aoaType);
        assertEquals(params.getNumOfMsrmtFocusOnRange(), numOfMsrmtFocusOnRange);
        assertEquals(params.getNumOfMsrmtFocusOnAoaAzimuth(), numOfMsrmtFocusOnAoaAzimuth);
        assertEquals(params.getNumOfMsrmtFocusOnAoaElevation(), numOfMsrmtFocusOnAoaElevation);
        assertEquals(params.getUlTdoaTxIntervalMs(), ulTdoaTxIntervalMs);
        assertEquals(params.getUlTdoaRandomWindowMs(), ulTdoaRandomWindowMS);
        assertEquals(params.getUlTdoaDeviceIdType(), ulTdoaDeviceIdType);
        assertArrayEquals(params.getUlTdoaDeviceId(), ulTdoaDeviceId);
        assertEquals(params.getUlTdoaTxTimestampType(), ulTdoaTxTimestampType);
        assertEquals(params.getMaxNumberOfMeasurements(), maxNumberOfMeasurements);

        FiraOpenSessionParams fromBundle = FiraOpenSessionParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(fromBundle.getMultiNodeMode(), multiNodeMode);

        assertEquals(fromBundle.getDeviceAddress(), deviceAddress);
        assertEquals(fromBundle.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(fromBundle.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(fromBundle.getInitiationTime(), initiationTime);
        assertEquals(fromBundle.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(fromBundle.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(fromBundle.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(fromBundle.getBlockStrideLength(), blockStrideLength);
        assertEquals(fromBundle.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(fromBundle.getSessionPriority(), sessionPriority);
        assertEquals(fromBundle.getMacAddressMode(), addressMode);
        assertEquals(fromBundle.hasRangingResultReportMessage(), hasRangingResultReportMessage);
        assertEquals(fromBundle.hasControlMessage(), hasControlMessage);
        assertEquals(fromBundle.hasRangingControlPhase(), hasRangingControlPhase);
        assertEquals(fromBundle.getMeasurementReportType(), measurementReportType);
        assertEquals(fromBundle.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(fromBundle.getChannelNumber(), channelNumber);
        assertEquals(fromBundle.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(fromBundle.getRframeConfig(), rframeConfig);
        assertEquals(fromBundle.getPrfMode(), prfMode);
        assertEquals(fromBundle.getPreambleDuration(), preambleDuration);
        assertEquals(fromBundle.getSfdId(), sfdId);
        assertEquals(fromBundle.getStsSegmentCount(), stsSegmentCount);
        assertEquals(fromBundle.getStsLength(), stsLength);
        assertArrayEquals(fromBundle.getSessionKey(), sessionKey);
        assertArrayEquals(fromBundle.getSubsessionKey(), subsessionKey);
        assertEquals(fromBundle.getPsduDataRate(), psduDataRate);
        assertEquals(fromBundle.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(fromBundle.getFcsType(), fcsType);
        assertEquals(fromBundle.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(fromBundle.getStsConfig(), stsConfig);
        assertEquals(fromBundle.getSubSessionId(), subSessionId);
        assertArrayEquals(fromBundle.getVendorId(), vendorId);
        assertArrayEquals(fromBundle.getStaticStsIV(), staticStsIV);
        assertEquals(fromBundle.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(fromBundle.getKeyRotationRate(), keyRotationRate);
        assertEquals(fromBundle.getAoaResultRequest(), aoaResultRequest);
        assertEquals(fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(fromBundle.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear, 0.0d);
        assertEquals(fromBundle.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar, 0.0d);
        assertEquals(fromBundle.getRangeDataNtfAoaAzimuthLower(), rangeDataNtfAoaAzimuthLower,
                0.0d);
        assertEquals(fromBundle.getRangeDataNtfAoaAzimuthUpper(), rangeDataNtfAoaAzimuthUpper,
                0.0d);
        assertEquals(fromBundle.getRangeDataNtfAoaElevationLower(), rangeDataNtfAoaElevationLower,
                0.0d);
        assertEquals(fromBundle.getRangeDataNtfAoaElevationUpper(), rangeDataNtfAoaElevationUpper,
                0.0d);
        assertEquals(fromBundle.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(fromBundle.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(fromBundle.getAoaType(), aoaType);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnRange(), numOfMsrmtFocusOnRange);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnAoaAzimuth(), numOfMsrmtFocusOnAoaAzimuth);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnAoaElevation(), numOfMsrmtFocusOnAoaElevation);
        assertEquals(fromBundle.getUlTdoaTxIntervalMs(), ulTdoaTxIntervalMs);
        assertEquals(fromBundle.getUlTdoaRandomWindowMs(), ulTdoaRandomWindowMS);
        assertEquals(fromBundle.getUlTdoaDeviceIdType(), ulTdoaDeviceIdType);
        assertArrayEquals(fromBundle.getUlTdoaDeviceId(), ulTdoaDeviceId);
        assertEquals(fromBundle.getUlTdoaTxTimestampType(), ulTdoaTxTimestampType);
        assertEquals(fromBundle.getMaxNumberOfMeasurements(), maxNumberOfMeasurements);

        verifyProtocolPresent(fromBundle);
        verifyBundlesEqual(params, fromBundle);

        FiraOpenSessionParams fromCopy = new FiraOpenSessionParams.Builder(params).build();

        assertEquals(fromCopy.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(fromCopy.getMultiNodeMode(), multiNodeMode);

        assertEquals(fromCopy.getDeviceAddress(), deviceAddress);
        assertEquals(fromCopy.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(fromCopy.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(fromCopy.getInitiationTime(), initiationTime);
        assertEquals(fromCopy.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(fromCopy.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(fromCopy.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(fromCopy.getBlockStrideLength(), blockStrideLength);
        assertEquals(fromCopy.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(fromCopy.getSessionPriority(), sessionPriority);
        assertEquals(fromCopy.getMacAddressMode(), addressMode);
        assertEquals(fromCopy.hasRangingResultReportMessage(), hasRangingResultReportMessage);
        assertEquals(fromCopy.hasControlMessage(), hasControlMessage);
        assertEquals(fromCopy.hasRangingControlPhase(), hasRangingControlPhase);
        assertEquals(fromCopy.getMeasurementReportType(), measurementReportType);
        assertEquals(fromCopy.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(fromCopy.getChannelNumber(), channelNumber);
        assertEquals(fromCopy.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(fromCopy.getRframeConfig(), rframeConfig);
        assertEquals(fromCopy.getPrfMode(), prfMode);
        assertEquals(fromCopy.getPreambleDuration(), preambleDuration);
        assertEquals(fromCopy.getSfdId(), sfdId);
        assertEquals(fromCopy.getStsSegmentCount(), stsSegmentCount);
        assertEquals(fromCopy.getStsLength(), stsLength);
        assertArrayEquals(fromCopy.getSessionKey(), sessionKey);
        assertArrayEquals(fromCopy.getSubsessionKey(), subsessionKey);
        assertEquals(fromCopy.getPsduDataRate(), psduDataRate);
        assertEquals(fromCopy.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(fromCopy.getFcsType(), fcsType);
        assertEquals(fromCopy.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(fromCopy.getStsConfig(), stsConfig);
        assertEquals(fromCopy.getSubSessionId(), subSessionId);
        assertArrayEquals(fromCopy.getVendorId(), vendorId);
        assertArrayEquals(fromCopy.getStaticStsIV(), staticStsIV);
        assertEquals(fromCopy.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(fromCopy.getKeyRotationRate(), keyRotationRate);
        assertEquals(fromCopy.getAoaResultRequest(), aoaResultRequest);
        assertEquals(fromCopy.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(fromCopy.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear, 0.0d);
        assertEquals(fromCopy.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar, 0.0d);
        assertEquals(fromCopy.getRangeDataNtfAoaAzimuthLower(), rangeDataNtfAoaAzimuthLower,
                0.0d);
        assertEquals(fromCopy.getRangeDataNtfAoaAzimuthUpper(), rangeDataNtfAoaAzimuthUpper,
                0.0d);
        assertEquals(fromCopy.getRangeDataNtfAoaElevationLower(), rangeDataNtfAoaElevationLower,
                0.0d);
        assertEquals(fromCopy.getRangeDataNtfAoaElevationUpper(), rangeDataNtfAoaElevationUpper,
                0.0d);
        assertEquals(fromCopy.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(fromCopy.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(
                fromCopy.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                fromCopy.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(fromCopy.getAoaType(), aoaType);
        assertEquals(fromCopy.getNumOfMsrmtFocusOnRange(), numOfMsrmtFocusOnRange);
        assertEquals(fromCopy.getNumOfMsrmtFocusOnAoaAzimuth(), numOfMsrmtFocusOnAoaAzimuth);
        assertEquals(fromCopy.getNumOfMsrmtFocusOnAoaElevation(), numOfMsrmtFocusOnAoaElevation);
        assertEquals(fromCopy.getUlTdoaTxIntervalMs(), ulTdoaTxIntervalMs);
        assertEquals(fromCopy.getUlTdoaRandomWindowMs(), ulTdoaRandomWindowMS);
        assertEquals(fromCopy.getUlTdoaDeviceIdType(), ulTdoaDeviceIdType);
        assertArrayEquals(fromCopy.getUlTdoaDeviceId(), ulTdoaDeviceId);
        assertEquals(fromCopy.getUlTdoaTxTimestampType(), ulTdoaTxTimestampType);
        assertEquals(fromCopy.getMaxNumberOfMeasurements(), maxNumberOfMeasurements);

        verifyProtocolPresent(fromCopy);
        verifyBundlesEqual(params, fromCopy);
    }

    @Test
    public void testRangingReconfigureParams() {
        int action = MULTICAST_LIST_UPDATE_ACTION_DELETE;
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int blockStrideLength = 5;
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
        int rangeDataProximityNear = 100;
        int rangeDataProximityFar = 500;
        double rangeDataAoaAzimuthLower = -0.5;
        double rangeDataAoaAzimuthUpper = +1.5;
        double rangeDataAoaElevationLower = -1.5;
        double rangeDataAoaElevationUpper = +1.2;

        int[] subSessionIdList = new int[] {3, 4};
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setAction(action)
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        assertEquals((int) params.getAction(), action);
        assertArrayEquals(params.getAddressList(), addressList);
        assertArrayEquals(params.getSubSessionIdList(), subSessionIdList);
        FiraRangingReconfigureParams fromBundle =
                FiraRangingReconfigureParams.fromBundle(params.toBundle());
        assertEquals((int) fromBundle.getAction(), action);
        assertArrayEquals(fromBundle.getAddressList(), addressList);
        assertArrayEquals(fromBundle.getSubSessionIdList(), subSessionIdList);
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);

        params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(blockStrideLength)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataProximityNear(rangeDataProximityNear)
                        .setRangeDataProximityFar(rangeDataProximityFar)
                        .setRangeDataAoaAzimuthLower(rangeDataAoaAzimuthLower)
                        .setRangeDataAoaAzimuthUpper(rangeDataAoaAzimuthUpper)
                        .setRangeDataAoaElevationLower(rangeDataAoaElevationLower)
                        .setRangeDataAoaElevationUpper(rangeDataAoaElevationUpper)
                        .build();
        assertEquals((int) params.getBlockStrideLength(), blockStrideLength);
        assertEquals((int) params.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals((int) params.getRangeDataProximityNear(), rangeDataProximityNear);
        assertEquals((int) params.getRangeDataProximityFar(), rangeDataProximityFar);
        assertEquals((double) params.getRangeDataAoaAzimuthLower(), rangeDataAoaAzimuthLower,
                0.0d);
        assertEquals((double) params.getRangeDataAoaAzimuthUpper(), rangeDataAoaAzimuthUpper,
                0.0d);
        assertEquals((double) params.getRangeDataAoaElevationLower(), rangeDataAoaElevationLower,
                0.0d);
        assertEquals((double) params.getRangeDataAoaElevationUpper(), rangeDataAoaElevationUpper,
                0.0d);

        fromBundle = FiraRangingReconfigureParams.fromBundle(params.toBundle());
        assertEquals((int) fromBundle.getBlockStrideLength(), blockStrideLength);
        assertEquals((int) fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals((int) fromBundle.getRangeDataProximityNear(), rangeDataProximityNear);
        assertEquals((int) fromBundle.getRangeDataProximityFar(), rangeDataProximityFar);
        assertEquals((double) fromBundle.getRangeDataAoaAzimuthLower(), rangeDataAoaAzimuthLower,
                0.0d);
        assertEquals((double) fromBundle.getRangeDataAoaAzimuthUpper(), rangeDataAoaAzimuthUpper,
                0.0d);
        assertEquals((double) fromBundle.getRangeDataAoaElevationLower(),
                rangeDataAoaElevationLower, 0.0d);
        assertEquals((double) fromBundle.getRangeDataAoaElevationUpper(),
                rangeDataAoaElevationUpper, 0.0d);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testControleeParams() {
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        assertArrayEquals(params.getAddressList(), addressList);
        assertArrayEquals(params.getSubSessionIdList(), subSessionIdList);
        FiraControleeParams fromBundle =
                FiraControleeParams.fromBundle(params.toBundle());
        assertArrayEquals(fromBundle.getAddressList(), addressList);
        assertArrayEquals(fromBundle.getSubSessionIdList(), subSessionIdList);
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStatusCode() {
        int statusCode = STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
        FiraStatusCode params = new FiraStatusCode.Builder().setStatusCode(statusCode).build();
        assertEquals(params.getStatusCode(), statusCode);
        assertTrue(FiraStatusCode.isBundleValid(params.toBundle()));
        FiraStatusCode fromBundle = FiraStatusCode.fromBundle(params.toBundle());
        assertEquals(fromBundle.getStatusCode(), statusCode);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testMulticastListUpdateStatusCode() {
        int statusCode = MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL;
        FiraMulticastListUpdateStatusCode params =
                new FiraMulticastListUpdateStatusCode.Builder().setStatusCode(statusCode).build();
        assertEquals(params.getStatusCode(), statusCode);
        assertTrue(FiraMulticastListUpdateStatusCode.isBundleValid(params.toBundle()));

        FiraMulticastListUpdateStatusCode fromBundle =
                FiraMulticastListUpdateStatusCode.fromBundle(params.toBundle());
        assertEquals(fromBundle.getStatusCode(), statusCode);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStateChangeReasonCode() {
        int reasonCode = STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL;
        FiraStateChangeReasonCode params =
                new FiraStateChangeReasonCode.Builder().setReasonCode(reasonCode).build();
        assertEquals(reasonCode, params.getReasonCode());
        assertTrue(FiraStateChangeReasonCode.isBundleValid(params.toBundle()));

        FiraStateChangeReasonCode fromBundle =
                FiraStateChangeReasonCode.fromBundle(params.toBundle());
        assertEquals(reasonCode, fromBundle.getReasonCode());

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    private void verifyProtocolPresent(Params params) {
        assertTrue(Params.isProtocol(params.toBundle(), FiraParams.PROTOCOL_NAME));
    }

    private void verifyBundlesEqual(Params params, Params fromBundle) {
        PersistableBundle.kindofEquals(params.toBundle(), fromBundle.toBundle());
    }

    @Test
    public void testSpecificationParams() {
        FiraProtocolVersion minPhyVersionSupported = new FiraProtocolVersion(1, 0);
        FiraProtocolVersion maxPhyVersionSupported = new FiraProtocolVersion(2, 0);
        FiraProtocolVersion minMacVersionSupported = new FiraProtocolVersion(1, 2);
        FiraProtocolVersion maxMacVersionSupported = new FiraProtocolVersion(1, 2);
        List<Integer> supportedChannels = List.of(5, 6, 8, 9);
        EnumSet<FiraParams.AoaCapabilityFlag> aoaCapabilities =
                EnumSet.of(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);

        EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoleCapabilities =
                EnumSet.allOf(FiraParams.DeviceRoleCapabilityFlag.class);
        boolean hasBlockStridingSupport = true;
        boolean hasNonDeferredModeSupport = true;
        boolean hasInitiationTimeSupport = true;
        boolean hasRssiReportingSupport = true;
        boolean hasDiagnosticsSupport = true;
        EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeCapabilities =
                EnumSet.allOf(FiraParams.MultiNodeCapabilityFlag.class);
        EnumSet<FiraParams.PrfCapabilityFlag> prfCapabilities =
                EnumSet.allOf(FiraParams.PrfCapabilityFlag.class);
        EnumSet<FiraParams.RangingRoundCapabilityFlag> rangingRoundCapabilities =
                EnumSet.allOf(FiraParams.RangingRoundCapabilityFlag.class);
        EnumSet<FiraParams.RframeCapabilityFlag> rframeCapabilities =
                EnumSet.allOf(FiraParams.RframeCapabilityFlag.class);
        EnumSet<FiraParams.StsCapabilityFlag> stsCapabilities =
                EnumSet.allOf(FiraParams.StsCapabilityFlag.class);
        EnumSet<FiraParams.PsduDataRateCapabilityFlag> psduDataRateCapabilities =
                EnumSet.allOf(FiraParams.PsduDataRateCapabilityFlag.class);
        EnumSet<FiraParams.BprfParameterSetCapabilityFlag> bprfCapabilities =
                EnumSet.allOf(FiraParams.BprfParameterSetCapabilityFlag.class);
        EnumSet<FiraParams.HprfParameterSetCapabilityFlag> hprfCapabilities =
                EnumSet.allOf(FiraParams.HprfParameterSetCapabilityFlag.class);
        EnumSet<FiraParams.RangeDataNtfConfigCapabilityFlag> rangeDataNtfConfigCapabilities =
                EnumSet.allOf(FiraParams.RangeDataNtfConfigCapabilityFlag.class);
        int deviceType = RANGING_DEVICE_TYPE_CONTROLLER;
        boolean suspendRangingSupport = true;
        int sessionKeyLength = 1;
        boolean hasBackgroundSupport = true;

        FiraSpecificationParams params =
                new FiraSpecificationParams.Builder()
                        .setMinPhyVersionSupported(minPhyVersionSupported)
                        .setMaxPhyVersionSupported(maxPhyVersionSupported)
                        .setMinMacVersionSupported(minMacVersionSupported)
                        .setMaxMacVersionSupported(maxMacVersionSupported)
                        .setSupportedChannels(supportedChannels)
                        .setAoaCapabilities(aoaCapabilities)
                        .setDeviceRoleCapabilities(deviceRoleCapabilities)
                        .hasBlockStridingSupport(hasBlockStridingSupport)
                        .hasNonDeferredModeSupport(hasNonDeferredModeSupport)
                        .hasInitiationTimeSupport(hasInitiationTimeSupport)
                        .hasRssiReportingSupport(hasRssiReportingSupport)
                        .hasDiagnosticsSupport(hasDiagnosticsSupport)
                        .setMultiNodeCapabilities(multiNodeCapabilities)
                        .setPrfCapabilities(prfCapabilities)
                        .setRangingRoundCapabilities(rangingRoundCapabilities)
                        .setRframeCapabilities(rframeCapabilities)
                        .setStsCapabilities(stsCapabilities)
                        .setPsduDataRateCapabilities(psduDataRateCapabilities)
                        .setBprfParameterSetCapabilities(bprfCapabilities)
                        .setHprfParameterSetCapabilities(hprfCapabilities)
                        .setRangeDataNtfConfigCapabilities(rangeDataNtfConfigCapabilities)
                        .setDeviceType(deviceType)
                        .setSuspendRangingSupport(suspendRangingSupport)
                        .setSessionKeyLength(sessionKeyLength)
                        .setBackgroundRangingSupport(false)
                        .build();
        assertEquals(minPhyVersionSupported, params.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, params.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, params.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, params.getMaxMacVersionSupported());
        assertEquals(supportedChannels, params.getSupportedChannels());
        assertEquals(aoaCapabilities, params.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, params.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, params.hasBlockStridingSupport());
        assertEquals(hasNonDeferredModeSupport, params.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, params.hasInitiationTimeSupport());
        assertEquals(hasRssiReportingSupport, params.hasRssiReportingSupport());
        assertEquals(hasDiagnosticsSupport, params.hasDiagnosticsSupport());
        assertEquals(multiNodeCapabilities, params.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, params.getPrfCapabilities());
        assertEquals(rangingRoundCapabilities, params.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, params.getRframeCapabilities());
        assertEquals(stsCapabilities, params.getStsCapabilities());
        assertEquals(psduDataRateCapabilities, params.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, params.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, params.getHprfParameterSetCapabilities());
        assertEquals(rangeDataNtfConfigCapabilities, params.getRangeDataNtfConfigCapabilities());
        assertEquals(deviceType, params.getDeviceType());
        assertEquals(suspendRangingSupport, params.hasSuspendRangingSupport());
        assertEquals(sessionKeyLength, params.getSessionKeyLength());
        assertEquals(false, params.hasBackgroundRangingSupport());

        FiraSpecificationParams fromBundle = FiraSpecificationParams.fromBundle(params.toBundle());
        assertEquals(minPhyVersionSupported, fromBundle.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, fromBundle.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, fromBundle.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, fromBundle.getMaxMacVersionSupported());
        assertEquals(supportedChannels, fromBundle.getSupportedChannels());
        assertEquals(aoaCapabilities, fromBundle.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, fromBundle.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, fromBundle.hasBlockStridingSupport());
        assertEquals(hasNonDeferredModeSupport, fromBundle.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, params.hasInitiationTimeSupport());
        assertEquals(multiNodeCapabilities, fromBundle.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, fromBundle.getPrfCapabilities());
        assertEquals(rangingRoundCapabilities, fromBundle.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, fromBundle.getRframeCapabilities());
        assertEquals(stsCapabilities, fromBundle.getStsCapabilities());
        assertEquals(psduDataRateCapabilities, fromBundle.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, fromBundle.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, fromBundle.getHprfParameterSetCapabilities());
        assertEquals(rangeDataNtfConfigCapabilities,
                fromBundle.getRangeDataNtfConfigCapabilities());
        assertEquals(deviceType, fromBundle.getDeviceType());
        assertEquals(suspendRangingSupport, fromBundle.hasSuspendRangingSupport());
        assertEquals(sessionKeyLength, fromBundle.getSessionKeyLength());
        assertEquals(false, fromBundle.hasBackgroundRangingSupport());
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);

        FiraSpecificationParams fromCopy = new FiraSpecificationParams.Builder(params)
                .setBackgroundRangingSupport(hasBackgroundSupport)
                .build();

        assertEquals(minPhyVersionSupported, fromCopy.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, fromCopy.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, fromCopy.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, fromCopy.getMaxMacVersionSupported());
        assertEquals(supportedChannels, fromCopy.getSupportedChannels());
        assertEquals(aoaCapabilities, fromCopy.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, fromCopy.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, fromCopy.hasBlockStridingSupport());
        assertEquals(hasNonDeferredModeSupport, fromCopy.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, params.hasInitiationTimeSupport());
        assertEquals(multiNodeCapabilities, fromCopy.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, fromCopy.getPrfCapabilities());
        assertEquals(rangingRoundCapabilities, fromCopy.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, fromCopy.getRframeCapabilities());
        assertEquals(stsCapabilities, fromCopy.getStsCapabilities());
        assertEquals(psduDataRateCapabilities, fromCopy.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, fromCopy.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, fromCopy.getHprfParameterSetCapabilities());
        assertEquals(rangeDataNtfConfigCapabilities,
                fromCopy.getRangeDataNtfConfigCapabilities());
        assertEquals(deviceType, fromCopy.getDeviceType());
        assertEquals(suspendRangingSupport, fromCopy.hasSuspendRangingSupport());
        assertEquals(sessionKeyLength, fromCopy.getSessionKeyLength());
        assertEquals(hasBackgroundSupport, fromCopy.hasBackgroundRangingSupport());
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromCopy);
    }

    @Test
    public void testSpecificationParams_whenNoChannelsSet() {
        FiraSpecificationParams params =
                new FiraSpecificationParams.Builder()
                        .build();
        assertEquals(List.of(), params.getSupportedChannels());

        FiraSpecificationParams fromBundle = FiraSpecificationParams.fromBundle(params.toBundle());
        assertEquals(List.of(), fromBundle.getSupportedChannels());
    }

    @Test
    public void testFiraProtocolVersion() {
        FiraProtocolVersion firaVersion = new FiraProtocolVersion(1, 2);
        assertEquals(1, firaVersion.getMajor());
        assertEquals(2, firaVersion.getMinor());

        firaVersion = FiraProtocolVersion.fromString("1.3");
        assertEquals(1, firaVersion.getMajor());
        assertEquals(3, firaVersion.getMinor());

        firaVersion = FiraProtocolVersion.fromBytes(new byte[]{0x1, 0x4}, 0);
        assertEquals(1, firaVersion.getMajor());
        assertEquals(4, firaVersion.getMinor());

        firaVersion = FiraProtocolVersion.fromLEShort((short) 0x1002);
        assertEquals(2, firaVersion.getMajor());
        assertEquals(1, firaVersion.getMinor());
    }
}
