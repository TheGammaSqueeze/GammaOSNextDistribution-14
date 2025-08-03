/*
 * Copyright 2022 The Android Open Source Project
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

import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_SHORT;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.util.UwbUtil.convertFloatToQFormat;
import static com.android.server.uwb.util.UwbUtil.degreeToRadian;

import static com.google.uwb.support.radar.RadarParams.BITS_PER_SAMPLES_48;
import static com.google.uwb.support.radar.RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;

import android.os.PersistableBundle;
import android.util.Pair;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.UwbAddress;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.data.UwbDlTDoAMeasurement;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.data.UwbRadarData;
import com.android.server.uwb.data.UwbRadarSweepData;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.params.TlvUtil;

import com.google.uwb.support.dltdoa.DlTDoAMeasurement;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.oemextension.RangingReportMetadata;
import com.google.uwb.support.radar.RadarData;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSweepData;

public class UwbTestUtils {
    public static final int TEST_SESSION_ID = 7;
    public static final int TEST_SESSION_ID_2 = 8;
    public static final byte TEST_SESSION_TYPE = FiraParams.SESSION_TYPE_RANGING;
    public static final byte[] PEER_SHORT_MAC_ADDRESS = {0x35, 0x37};
    public static final long PEER_SHORT_MAC_ADDRESS_LONG = 0x3735L;
    public static final byte[] PEER_EXTENDED_SHORT_MAC_ADDRESS =
            {0x35, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final long PEER_EXTENDED_SHORT_MAC_ADDRESS_LONG = 0x3735L;
    public static final byte[] PEER_EXTENDED_MAC_ADDRESS =
            {0x12, 0x14, 0x16, 0x18, 0x31, 0x33, 0x35, 0x37};
    public static final long PEER_EXTENDED_MAC_ADDRESS_LONG = 0x3735333118161412L;
    public static final byte[] PEER_EXTENDED_MAC_ADDRESS_2 =
            {0x2, 0x4, 0x6, 0x8, 0x1, 0x3, 0x5, 0x7};
    public static final long  PEER_EXTENDED_MAC_ADDRESS_2_LONG = 0x0705030108060402L;
    public static final byte[] PEER_BAD_MAC_ADDRESS = {0x12, 0x14, 0x16, 0x18};
    public static final UwbAddress PEER_EXTENDED_UWB_ADDRESS = UwbAddress.fromBytes(
            PEER_EXTENDED_MAC_ADDRESS);
    public static final UwbAddress PEER_EXTENDED_UWB_ADDRESS_2 = UwbAddress.fromBytes(
            PEER_EXTENDED_MAC_ADDRESS_2);
    public static final UwbAddress PEER_SHORT_UWB_ADDRESS = UwbAddress.fromBytes(
            PEER_SHORT_MAC_ADDRESS);
    public static final UwbAddress PEER_EXTENDED_SHORT_UWB_ADDRESS = UwbAddress.fromBytes(
            PEER_EXTENDED_SHORT_MAC_ADDRESS);

    public static final PersistableBundle PERSISTABLE_BUNDLE = new PersistableBundle();
    public static final byte[] DATA_PAYLOAD = new byte[] {0x13, 0x15, 0x18};
    public static final int RANGING_MEASUREMENT_TYPE_UNDEFINED = 0; // RFU in spec
    public static final int MAX_DATA_SIZE = 100;

    private static final byte[] TEST_RAW_NTF_DATA = {0x10, 0x01, 0x05};
    private static final long TEST_SEQ_COUNTER = 5;
    private static final long TEST_SEQ_COUNTER2 = 6;
    private static final int TEST_RCR_INDICATION = 7;
    private static final long TEST_CURR_RANGING_INTERVAL = 100;
    private static final int TEST_RANGING_MEASURES_TYPE = RANGING_MEASUREMENT_TYPE_TWO_WAY;
    private static final int TEST_MAC_ADDRESS_MODE = 1;
    public static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 0;
    private static final int TEST_DISTANCE = 101;
    private static final float TEST_AOA_AZIMUTH = 67;
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final int TEST_BAD_AOA_AZIMUTH_FOM = 150;
    private static final float TEST_AOA_ELEVATION = 37;
    private static final int TEST_AOA_ELEVATION_FOM = 90;
    private static final float TEST_AOA_DEST_AZIMUTH = 67;
    private static final int TEST_AOA_DEST_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_DEST_ELEVATION = 37;
    private static final int TEST_AOA_DEST_ELEVATION_FOM = 90;
    private static final int TEST_FRAME_SEQUENCE_NUMBER = 1;
    private static final int TEST_BLOCK_IDX = 100;
    private static final int TEST_SLOT_IDX = 10;
    private static final int TEST_MESSAGE_TYPE = 1;
    private static final int TEST_MESSAGE_CONTROL = 1331;
    private static final int TEST_BLOCK_INDEX = 5;
    private static final int TEST_ROUND_INDEX = 1;
    private static final long TEST_TIMESTAMP = 500_000L;
    private static final long TEST_TIMESTAMP2 = 600_000L;
    private static final float TEST_ANCHOR_CFO = 12.50f;
    private static final float TEST_CFO = 15.50f;
    private static final long TEST_INTIATOR_REPLY_TIME = 500_000L;
    private static final long TEST_RESPONDER_REPLY_TIME = 300_000L;
    private static final int TEST_INITIATOR_RESPONDER_TOF = 500;
    private static final byte[] TEST_ANCHOR_LOCATION = {0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x07, 0x08, 0x09, 0x10};
    private static final byte[] TEST_ACTIVE_RANGING_ROUNDS = {0x02, 0x08};
    private static final int TEST_RSSI = 150;

    private static final int TEST_SAMPLES_PER_SWEEP = 64;
    private static final int TEST_BITS_PER_SAMPLE = BITS_PER_SAMPLES_48;
    private static final int TEST_SWEEP_OFFSET = -10;
    private static final byte[] TEST_VENDOR_SPECIFIC_DATA = {0x04, 0x07};
    private static final byte[] TEST_SAMPLE_DATA = {0x06, 0x08, (byte) 0Xff};

    private UwbTestUtils() {}

    /** Build UwbRangingData for all Ranging Measurement Type(s). */
    public static UwbRangingData generateRangingData(
            int rangingMeasurementType, int macAddressingMode, int rangingStatus) {
        byte[] macAddress = (macAddressingMode == MAC_ADDRESSING_MODE_SHORT)
                ? PEER_SHORT_MAC_ADDRESS : PEER_EXTENDED_MAC_ADDRESS;
        return generateRangingData(
                rangingMeasurementType, macAddressingMode, macAddress, rangingStatus);
    }

    /** Build UwbRangingData for all Ranging Measurement Type(s). */
    public static UwbRangingData generateRangingData(
            int rangingMeasurementType, int macAddressingMode, byte[] macAddress,
            int rangingStatus) {
        switch (rangingMeasurementType) {
            case RANGING_MEASUREMENT_TYPE_TWO_WAY:
                return generateTwoWayMeasurementRangingData(rangingStatus);
            case RANGING_MEASUREMENT_TYPE_OWR_AOA:
                return generateOwrAoaMeasurementRangingData(
                        macAddressingMode, macAddress, rangingStatus);
            case RANGING_MEASUREMENT_TYPE_DL_TDOA:
                return generateDlTDoAMeasurementRangingData(macAddressingMode, rangingStatus);
            default:
                return generateDefaultRangingData();
        }
    }

    private static UwbRangingData generateTwoWayMeasurementRangingData(int rangingStatus) {
        final int noOfRangingMeasures = 1;
        final UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        uwbTwoWayMeasurements[0] = new UwbTwoWayMeasurement(PEER_SHORT_MAC_ADDRESS, rangingStatus,
                TEST_LOS, TEST_DISTANCE, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, convertFloatToQFormat(TEST_AOA_DEST_AZIMUTH, 9, 7),
                TEST_AOA_DEST_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_DEST_ELEVATION, 9, 7),
                TEST_AOA_DEST_ELEVATION_FOM, TEST_SLOT_IDX, TEST_RSSI);
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_TWO_WAY,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbTwoWayMeasurements,
                TEST_RAW_NTF_DATA);
    }

    private static UwbRangingData generateOwrAoaMeasurementRangingData(
            int macAddressingMode, byte[] macAddress, int rangingStatus) {
        final int noOfRangingMeasures = 1;
        final UwbOwrAoaMeasurement uwbOwrAoaMeasurement  = new UwbOwrAoaMeasurement(
                macAddress, rangingStatus, TEST_LOS,
                TEST_FRAME_SEQUENCE_NUMBER, TEST_BLOCK_IDX,
                convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7), TEST_AOA_AZIMUTH_FOM,
                convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7), TEST_AOA_ELEVATION_FOM);
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_OWR_AOA,
                macAddressingMode, noOfRangingMeasures, uwbOwrAoaMeasurement,
                TEST_RAW_NTF_DATA);
    }

    /** Generate an OWR ranging data with a bad AoA Azimuth FOM */
    public static UwbRangingData generateBadOwrAoaMeasurementRangingData(
            int macAddressingMode, byte[] macAddress) {
        final int noOfRangingMeasures = 1;
        final UwbOwrAoaMeasurement uwbOwrAoaMeasurement  = new UwbOwrAoaMeasurement(
                macAddress, TEST_STATUS, TEST_LOS,
                TEST_FRAME_SEQUENCE_NUMBER, TEST_BLOCK_IDX,
                convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7), TEST_BAD_AOA_AZIMUTH_FOM,
                convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7), TEST_AOA_ELEVATION_FOM);
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_OWR_AOA,
                macAddressingMode, noOfRangingMeasures, uwbOwrAoaMeasurement,
                TEST_RAW_NTF_DATA);
    }

    private static UwbRangingData generateDlTDoAMeasurementRangingData(
            int macAddressingMode, int rangingStatus) {
        final int noOfRangingMeasures = 1;
        byte[] macAddress = (macAddressingMode == MAC_ADDRESSING_MODE_SHORT)
                ? PEER_SHORT_MAC_ADDRESS : PEER_EXTENDED_MAC_ADDRESS;
        final UwbDlTDoAMeasurement[] uwbDlTDoAMeasurements =
                new UwbDlTDoAMeasurement[noOfRangingMeasures];
        uwbDlTDoAMeasurements[0] = new UwbDlTDoAMeasurement(macAddress, rangingStatus,
                TEST_MESSAGE_TYPE, TEST_MESSAGE_CONTROL, TEST_BLOCK_INDEX, TEST_ROUND_INDEX,
                TEST_LOS, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, TEST_RSSI, TEST_TIMESTAMP, TEST_TIMESTAMP,
                convertFloatToQFormat(TEST_ANCHOR_CFO, 6, 10),
                convertFloatToQFormat(TEST_CFO, 6, 10), TEST_INTIATOR_REPLY_TIME,
                TEST_RESPONDER_REPLY_TIME, TEST_INITIATOR_RESPONDER_TOF, TEST_ANCHOR_LOCATION,
                TEST_ACTIVE_RANGING_ROUNDS);

        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_DL_TDOA,
                macAddressingMode, noOfRangingMeasures, uwbDlTDoAMeasurements,
                TEST_RAW_NTF_DATA);
    }

    // Create a UwbRangingData with no measurements, for negative test cases (example: incorrect
    // ranging measurement type).
    private static UwbRangingData generateDefaultRangingData() {
        final int noOfRangingMeasures = 0;
        final UwbTwoWayMeasurement[] uwbEmptyTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_UNDEFINED,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbEmptyTwoWayMeasurements,
                TEST_RAW_NTF_DATA);
    }

    // Helper method to generate a UwbRangingData instance and corresponding RangingMeasurement
    public static Pair<UwbRangingData, RangingReport> generateRangingDataAndRangingReport(
            byte[] macAddress, int macAddressingMode, int rangingMeasurementType,
            boolean isAoaAzimuthEnabled, boolean isAoaElevationEnabled,
            boolean isDestAoaAzimuthEnabled, boolean isDestAoaElevationEnabled,
            long elapsedRealtimeNanos) {
        UwbRangingData uwbRangingData = generateRangingData(rangingMeasurementType,
                macAddressingMode, TEST_STATUS);

        PersistableBundle rangingReportMetadata = new RangingReportMetadata.Builder()
                .setSessionId(0)
                .setRawNtfData(new byte[] {0x10, 0x01, 0x05})
                .build()
                .toBundle();

        AngleOfArrivalMeasurement aoaMeasurement = null;
        AngleOfArrivalMeasurement aoaDestMeasurement = null;
        if (isAoaAzimuthEnabled || isAoaElevationEnabled) {
            AngleMeasurement aoaAzimuth = null;
            AngleMeasurement aoaElevation = null;
            AngleOfArrivalMeasurement.Builder aoaBuilder = null;

            if (isAoaAzimuthEnabled) {
                aoaAzimuth = new AngleMeasurement(
                        degreeToRadian(TEST_AOA_AZIMUTH), 0,
                        TEST_AOA_AZIMUTH_FOM / (double) 100);
                aoaBuilder = new AngleOfArrivalMeasurement.Builder(aoaAzimuth);
            }
            if (isAoaElevationEnabled && aoaBuilder != null) {
                aoaElevation = new AngleMeasurement(
                        degreeToRadian(TEST_AOA_ELEVATION), 0,
                        TEST_AOA_ELEVATION_FOM / (double) 100);
                aoaBuilder.setAltitude(aoaElevation);
            }

            aoaMeasurement = (aoaBuilder != null) ? aoaBuilder.build() : null;
        }
        if (isDestAoaAzimuthEnabled || isDestAoaElevationEnabled) {
            AngleMeasurement aoaDestAzimuth = null;
            AngleMeasurement aoaDestElevation = null;
            AngleOfArrivalMeasurement.Builder aoaBuilder = null;

            if (isDestAoaAzimuthEnabled) {
                aoaDestAzimuth =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_AZIMUTH), 0,
                                TEST_AOA_DEST_AZIMUTH_FOM / (double) 100);
                aoaBuilder = new AngleOfArrivalMeasurement.Builder(aoaDestAzimuth);
            }
            if (isDestAoaElevationEnabled && aoaBuilder != null) {
                aoaDestElevation =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_ELEVATION), 0,
                                TEST_AOA_DEST_ELEVATION_FOM / (double) 100);
                aoaBuilder.setAltitude(aoaDestElevation);
            }
            aoaDestMeasurement = (aoaBuilder != null) ? aoaBuilder.build() : null;
        }

        RangingReport rangingReport = buildRangingReport(macAddress, rangingMeasurementType,
                aoaMeasurement, aoaDestMeasurement, elapsedRealtimeNanos, rangingReportMetadata);
        return Pair.create(uwbRangingData, rangingReport);
    }

    private static UwbAddress getComputedMacAddress(byte[] address) {
        if (!SdkLevel.isAtLeastU()) {
            return UwbAddress.fromBytes(TlvUtil.getReverseBytes(address));
        }
        return UwbAddress.fromBytes(address);
    }

    private static RangingReport buildRangingReport(byte[] macAddress, int rangingMeasurementType,
            AngleOfArrivalMeasurement aoaMeasurement, AngleOfArrivalMeasurement aoaDestMeasurement,
            long elapsedRealtimeNanos, PersistableBundle rangingReportMetadata) {

        PersistableBundle rangingMeasurementMetadata = new PersistableBundle();

        RangingMeasurement.Builder rangingMeasurementBuilder = new RangingMeasurement.Builder()
                .setRemoteDeviceAddress(getComputedMacAddress(macAddress))
                .setStatus(TEST_STATUS)
                .setElapsedRealtimeNanos(elapsedRealtimeNanos)
                .setAngleOfArrivalMeasurement(aoaMeasurement)
                .setLineOfSight(TEST_LOS);

        if (rangingMeasurementType == RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            rangingMeasurementBuilder
                    .setDistanceMeasurement(
                            new DistanceMeasurement.Builder()
                                    .setMeters(TEST_DISTANCE / (double) 100)
                                    .setErrorMeters(0)
                                    .setConfidenceLevel(1.0)
                                    .build())
                    .setDestinationAngleOfArrivalMeasurement(aoaDestMeasurement)
                    .setRssiDbm(-TEST_RSSI / 2)
                    .setRangingMeasurementMetadata(rangingMeasurementMetadata);
        }

        if (rangingMeasurementType == RANGING_MEASUREMENT_TYPE_DL_TDOA) {
            DlTDoAMeasurement dlTDoAMeasurement = new DlTDoAMeasurement.Builder()
                    .setMessageType(TEST_MESSAGE_TYPE)
                    .setMessageControl(TEST_MESSAGE_CONTROL)
                    .setBlockIndex(TEST_BLOCK_INDEX)
                    .setNLoS(TEST_LOS)
                    .setTxTimestamp(TEST_TIMESTAMP)
                    .setRxTimestamp(TEST_TIMESTAMP)
                    .setAnchorCfo(TEST_ANCHOR_CFO)
                    .setCfo(TEST_CFO)
                    .setInitiatorReplyTime(TEST_INTIATOR_REPLY_TIME)
                    .setResponderReplyTime(TEST_RESPONDER_REPLY_TIME)
                    .setInitiatorResponderTof(TEST_INITIATOR_RESPONDER_TOF)
                    .setAnchorLocation(TEST_ANCHOR_LOCATION)
                    .setActiveRangingRounds(TEST_ACTIVE_RANGING_ROUNDS)
                    .setRoundIndex(TEST_ROUND_INDEX)
                    .build();
            rangingMeasurementBuilder.setRangingMeasurementMetadata(dlTDoAMeasurement.toBundle());
        }

        return new RangingReport.Builder()
                .addMeasurement(rangingMeasurementBuilder.build())
                .addRangingReportMetadata(rangingReportMetadata)
                .build();
    }

    /** Build UwbRadarData for all Radar Data Type(s). */
    public static UwbRadarData generateUwbRadarData(int radarDataType, int rangingStatus) {
        switch (radarDataType) {
            case RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES:
                return generateRadarSweepSamplesUwbRadarData(rangingStatus);
            default:
                return generateDefaultUwbRadarData();
        }
    }

    /** Build UwbRadarData for Radar Sweep Samples Radar Data Type. */
    public static UwbRadarData generateRadarSweepSamplesUwbRadarData(int rangingStatus) {
        final int noOfRadarSweeps = 2;
        final UwbRadarSweepData[] uwbRadarSweepData = new UwbRadarSweepData[noOfRadarSweeps];
        uwbRadarSweepData[0] =
                new UwbRadarSweepData(
                        TEST_SEQ_COUNTER,
                        TEST_TIMESTAMP,
                        TEST_VENDOR_SPECIFIC_DATA,
                        TEST_SAMPLE_DATA);
        uwbRadarSweepData[1] =
                new UwbRadarSweepData(
                        TEST_SEQ_COUNTER2,
                        TEST_TIMESTAMP2,
                        TEST_VENDOR_SPECIFIC_DATA,
                        TEST_SAMPLE_DATA);
        return new UwbRadarData(
                TEST_SESSION_ID,
                rangingStatus,
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
                TEST_SAMPLES_PER_SWEEP,
                TEST_BITS_PER_SAMPLE,
                TEST_SWEEP_OFFSET,
                uwbRadarSweepData);
    }

    /* Create a UwbRadarData with no measurements */
    private static UwbRadarData generateDefaultUwbRadarData() {
        final int noOfRadarSweeps = 0;
        final UwbRadarSweepData[] uwbEmptyRadarSweepData = new UwbRadarSweepData[noOfRadarSweeps];
        return new UwbRadarData(
                TEST_SESSION_ID,
                TEST_STATUS,
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
                TEST_SAMPLES_PER_SWEEP,
                TEST_BITS_PER_SAMPLE,
                TEST_SWEEP_OFFSET,
                uwbEmptyRadarSweepData);
    }

    /** Build RadarData for all Radar Data Type(s). */
    public static RadarData generateRadarData(UwbRadarData uwbRadarData) {
        RadarData.Builder radarDataBuilder =
                new RadarData.Builder()
                        .setStatusCode(uwbRadarData.statusCode)
                        .setRadarDataType(uwbRadarData.radarDataType)
                        .setSamplesPerSweep(uwbRadarData.samplesPerSweep)
                        .setBitsPerSample(uwbRadarData.bitsPerSample)
                        .setSweepOffset(uwbRadarData.sweepOffset);
        if (uwbRadarData.radarDataType == RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES) {
            for (UwbRadarSweepData sweepData : uwbRadarData.radarSweepData) {
                radarDataBuilder.addSweepData(
                        new RadarSweepData.Builder()
                                .setSequenceNumber(sweepData.sequenceNumber)
                                .setTimestamp(sweepData.timestamp)
                                .setVendorSpecificData(sweepData.vendorSpecificData)
                                .setSampleData(sweepData.sampleData)
                                .build());
            }
        }
        return radarDataBuilder.build();
    }

    /**
     * Helper method to generate a {@link UwbRadarData} instance and a corresponding
     * {@link RadarData}.
     */
    public static Pair<UwbRadarData, RadarData> generateUwbRadarDataAndRadarData(
            int radarDataType) {
        UwbRadarData uwbRadarData = generateUwbRadarData(radarDataType, TEST_STATUS);
        RadarData radarData = generateRadarData(uwbRadarData);
        return Pair.create(uwbRadarData, radarData);
    }
}
