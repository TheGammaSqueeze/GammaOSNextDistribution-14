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

package com.google.uwb.support;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.radar.RadarData;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarParams.RadarCapabilityFlag;
import com.google.uwb.support.radar.RadarSpecificationParams;
import com.google.uwb.support.radar.RadarSweepData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RadarTests {
    private static final int SESSION_ID = 77;
    private static final long SEQUENCE_NUMBER = 10;
    private static final long TIMESTAMP = 1000;
    private static final byte[] VENDOR_SPECIFIC_DATA = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
    private static final byte[] SAMPLE_DATA = new byte[] {0x05, 0x04, 0x03, 0x02, 0x01};
    private static final PersistableBundle INVALID_BUNDLE =
            new FiraSpecificationParams.Builder().build().toBundle();

    @Test
    public void testOpenSessionParams_missingRequiredParams() {
        assertThrows(
                IllegalStateException.class, () -> new RadarOpenSessionParams.Builder().build());
    }

    @Test
    public void testOpenSessionParams_fromBundleWithInvalidProtocol() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RadarOpenSessionParams.fromBundle(INVALID_BUNDLE));
    }

    @Test
    public void testOpenSessionParams() {
        @RadarParams.BurstPeriod int burstPeriod = 100;
        @RadarParams.SweepPeriod int sweepPeriod = 40;
        @RadarParams.SweepsPerBurst int sweepsPerBurst = 16;
        @RadarParams.SamplesPerSweep int samplesPerSweep = 128;
        @FiraParams.UwbChannel int channelNumber = FiraParams.UWB_CHANNEL_9;
        @RadarParams.SweepOffset int sweepOffset = -1;
        @FiraParams.RframeConfig int rframeConfig = FiraParams.RFRAME_CONFIG_SP0;
        @RadarParams.PreambleDuration
        int preambleDuration = RadarParams.PREAMBLE_DURATION_T1024_SYMBOLS;
        @RadarParams.PreambleCodeIndex int preambleCodeIndex = 90;
        @RadarParams.SessionPriority int sessionPriority = 255;
        @RadarParams.BitsPerSample int bitsPerSample = RadarParams.BITS_PER_SAMPLES_64;
        @FiraParams.PrfMode int prfMode = FiraParams.PRF_MODE_HPRF;
        @RadarParams.NumberOfBursts int numberOfBursts = 1000;
        @RadarParams.RadarDataType
        int radarDataType = RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;

        RadarOpenSessionParams params =
                new RadarOpenSessionParams.Builder()
                        .setSessionId(SESSION_ID)
                        .setBurstPeriod(burstPeriod)
                        .setSweepPeriod(sweepPeriod)
                        .setSweepsPerBurst(sweepsPerBurst)
                        .setSamplesPerSweep(samplesPerSweep)
                        .setChannelNumber(channelNumber)
                        .setSweepOffset(sweepOffset)
                        .setRframeConfig(rframeConfig)
                        .setPreambleDuration(preambleDuration)
                        .setPreambleCodeIndex(preambleCodeIndex)
                        .setSessionPriority(sessionPriority)
                        .setBitsPerSample(bitsPerSample)
                        .setPrfMode(prfMode)
                        .setNumberOfBursts(numberOfBursts)
                        .setRadarDataType(radarDataType)
                        .build();

        assertEquals(params.getSessionId(), SESSION_ID);
        assertEquals(params.getSessionType(), RadarParams.SESSION_TYPE_RADAR);
        assertEquals(params.getBurstPeriod(), burstPeriod);
        assertEquals(params.getSweepPeriod(), sweepPeriod);
        assertEquals(params.getSweepsPerBurst(), sweepsPerBurst);
        assertEquals(params.getSamplesPerSweep(), samplesPerSweep);
        assertEquals(params.getChannelNumber(), channelNumber);
        assertEquals(params.getSweepOffset(), sweepOffset);
        assertEquals(params.getRframeConfig(), rframeConfig);
        assertEquals(params.getPreambleDuration(), preambleDuration);
        assertEquals(params.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(params.getSessionPriority(), sessionPriority);
        assertEquals(params.getBitsPerSample(), bitsPerSample);
        assertEquals(params.getPrfMode(), prfMode);
        assertEquals(params.getNumberOfBursts(), numberOfBursts);
        assertEquals(params.getRadarDataType(), radarDataType);

        RadarOpenSessionParams fromBundle = RadarOpenSessionParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getSessionId(), SESSION_ID);
        assertEquals(fromBundle.getSessionType(), RadarParams.SESSION_TYPE_RADAR);
        assertEquals(fromBundle.getBurstPeriod(), burstPeriod);
        assertEquals(fromBundle.getSweepPeriod(), sweepPeriod);
        assertEquals(fromBundle.getSweepsPerBurst(), sweepsPerBurst);
        assertEquals(fromBundle.getSamplesPerSweep(), samplesPerSweep);
        assertEquals(fromBundle.getChannelNumber(), channelNumber);
        assertEquals(fromBundle.getSweepOffset(), sweepOffset);
        assertEquals(fromBundle.getRframeConfig(), rframeConfig);
        assertEquals(fromBundle.getPreambleDuration(), preambleDuration);
        assertEquals(fromBundle.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(fromBundle.getSessionPriority(), sessionPriority);
        assertEquals(fromBundle.getBitsPerSample(), bitsPerSample);
        assertEquals(fromBundle.getPrfMode(), prfMode);
        assertEquals(fromBundle.getNumberOfBursts(), numberOfBursts);
        assertEquals(fromBundle.getRadarDataType(), radarDataType);
        assertEquals(params, fromBundle);

        RadarOpenSessionParams.Builder builder = new RadarOpenSessionParams.Builder(params);
        RadarOpenSessionParams fromBuilder = builder.build();

        assertEquals(params, fromBuilder);

        fromBuilder = new RadarOpenSessionParams.Builder(builder).build();

        assertEquals(params, fromBuilder);
    }

    @Test
    public void testSpecificationParams_fromBundleWithInvalidProtocol() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RadarSpecificationParams.fromBundle(INVALID_BUNDLE));
    }

    @Test
    public void testSpecificationParams() {
        EnumSet<RadarCapabilityFlag> radarCapabilities =
                EnumSet.of(RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT);
        RadarSpecificationParams.Builder paramsBuilder = new RadarSpecificationParams.Builder();
        paramsBuilder.setRadarCapabilities(radarCapabilities);
        paramsBuilder.addRadarCapability(RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT);

        RadarSpecificationParams params = paramsBuilder.build();

        assertEquals(radarCapabilities, params.getRadarCapabilities());

        RadarSpecificationParams fromBundle =
                RadarSpecificationParams.fromBundle(params.toBundle());

        assertEquals(radarCapabilities, fromBundle.getRadarCapabilities());
        assertEquals(params, fromBundle);
    }

    @Test
    public void testSpecificationParams_emptyCapabilities() {
        RadarSpecificationParams params = new RadarSpecificationParams.Builder().build();
        assertEquals(EnumSet.noneOf(RadarCapabilityFlag.class), params.getRadarCapabilities());

        RadarSpecificationParams fromBundle =
                RadarSpecificationParams.fromBundle(params.toBundle());
        assertEquals(EnumSet.noneOf(RadarCapabilityFlag.class), fromBundle.getRadarCapabilities());
    }

    @Test
    public void testRadarSweepData_missingRequiredParams() {
        assertThrows(IllegalStateException.class, () -> new RadarSweepData.Builder().build());
    }

    @Test
    public void testRadarSweepData_fromBundleWithInvalidProtocol() {
        assertThrows(
                IllegalArgumentException.class, () -> RadarSweepData.fromBundle(INVALID_BUNDLE));
    }

    @Test
    public void testRadarSweepData_invalidParamsSequenceNumber() {
        RadarSweepData.Builder builder =
                new RadarSweepData.Builder()
                        .setSequenceNumber(-1) // Invalid as timestamp must be greater than 0.
                        .setTimestamp(10)
                        .setSampleData(new byte[] {0x01});
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testRadarSweepData_invalidParamsTimestamp() {
        RadarSweepData.Builder builder =
                new RadarSweepData.Builder()
                        .setSequenceNumber(1)
                        .setTimestamp(-10) // Invalid as timestamp must be greater than 0.
                        .setSampleData(new byte[] {0x01});
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testRadarSweepData_invalidParamsSampleData() {
        RadarSweepData.Builder builder =
                new RadarSweepData.Builder()
                        .setSequenceNumber(1)
                        .setTimestamp(10)
                        .setSampleData(new byte[] {}); // Empty sampleData.
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testRadarSweepData() {
        RadarSweepData data =
                new RadarSweepData.Builder()
                        .setSequenceNumber(SEQUENCE_NUMBER)
                        .setTimestamp(TIMESTAMP)
                        .setVendorSpecificData(VENDOR_SPECIFIC_DATA)
                        .setSampleData(SAMPLE_DATA)
                        .build();

        assertEquals(data.getSequenceNumber(), SEQUENCE_NUMBER);
        assertEquals(data.getTimestamp(), TIMESTAMP);
        assertArrayEquals(data.getVendorSpecificData(), VENDOR_SPECIFIC_DATA);
        assertArrayEquals(data.getSampleData(), SAMPLE_DATA);

        RadarSweepData fromBundle = RadarSweepData.fromBundle(data.toBundle());

        assertEquals(fromBundle.getSequenceNumber(), SEQUENCE_NUMBER);
        assertEquals(fromBundle.getTimestamp(), TIMESTAMP);
        assertArrayEquals(fromBundle.getVendorSpecificData(), VENDOR_SPECIFIC_DATA);
        assertArrayEquals(fromBundle.getSampleData(), SAMPLE_DATA);
        assertEquals(data, fromBundle);

        data =
                new RadarSweepData.Builder()
                        .setSequenceNumber(SEQUENCE_NUMBER)
                        .setTimestamp(TIMESTAMP)
                        .setSampleData(SAMPLE_DATA)
                        .build();

        assertEquals(data.getVendorSpecificData(), null);

        fromBundle = RadarSweepData.fromBundle(data.toBundle());

        assertEquals(fromBundle.getVendorSpecificData(), null);
        assertEquals(data, fromBundle);
    }

    @Test
    public void testRadarData_missingRequiredParams() {
        assertThrows(IllegalStateException.class, () -> new RadarData.Builder().build());
    }

    @Test
    public void testRadarData_fromBundleWithInvalidProtocol() {
        assertThrows(IllegalArgumentException.class, () -> RadarData.fromBundle(INVALID_BUNDLE));
    }

    @Test
    public void testRadarData_invalidParams() {
        RadarData.Builder builder =
                new RadarData.Builder()
                        .setStatusCode(FiraParams.STATUS_CODE_OK)
                        .setRadarDataType(RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES)
                        .setSamplesPerSweep(5)
                        .setBitsPerSample(RadarParams.BITS_PER_SAMPLES_64)
                        .setSweepOffset(-1); // Empty SweepData
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testRadarData() {
        @FiraParams.StatusCode int statusCode = FiraParams.STATUS_CODE_OK;
        @RadarParams.RadarDataType
        int radarDataType = RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;
        @RadarParams.SamplesPerSweep int samplesPerSweep = 5;
        @RadarParams.BitsPerSample int bitsPerSample = RadarParams.BITS_PER_SAMPLES_32;
        @RadarParams.SweepOffset int sweepOffset = -1;

        RadarSweepData sweepData1 =
                new RadarSweepData.Builder()
                        .setSequenceNumber(SEQUENCE_NUMBER)
                        .setTimestamp(TIMESTAMP)
                        .setVendorSpecificData(VENDOR_SPECIFIC_DATA)
                        .setSampleData(SAMPLE_DATA)
                        .build();
        RadarSweepData sweepData2 =
                new RadarSweepData.Builder()
                        .setSequenceNumber(SEQUENCE_NUMBER)
                        .setTimestamp(TIMESTAMP)
                        .setVendorSpecificData(VENDOR_SPECIFIC_DATA)
                        .setSampleData(SAMPLE_DATA)
                        .build();

        List<RadarSweepData> sweepDataList = new ArrayList<>();
        sweepDataList.add(sweepData1);
        sweepDataList.add(sweepData2);

        RadarData data =
                new RadarData.Builder()
                        .setStatusCode(statusCode)
                        .setRadarDataType(radarDataType)
                        .setSamplesPerSweep(5)
                        .setBitsPerSample(RadarParams.BITS_PER_SAMPLES_32)
                        .setSweepOffset(-1)
                        .setSweepData(sweepDataList)
                        .build();

        assertEquals(data.getStatusCode(), statusCode);
        assertEquals(data.getRadarDataType(), radarDataType);
        assertEquals(data.getSamplesPerSweep(), samplesPerSweep);
        assertEquals(data.getBitsPerSample(), bitsPerSample);
        assertEquals(data.getSweepOffset(), sweepOffset);
        assertEquals(data.getSweepData().get(0), sweepData1);
        assertEquals(data.getSweepData().get(1), sweepData2);

        RadarData fromBundle = RadarData.fromBundle(data.toBundle());

        assertEquals(fromBundle.getStatusCode(), statusCode);
        assertEquals(fromBundle.getRadarDataType(), radarDataType);
        assertEquals(fromBundle.getSamplesPerSweep(), samplesPerSweep);
        assertEquals(fromBundle.getBitsPerSample(), bitsPerSample);
        assertEquals(fromBundle.getSweepOffset(), sweepOffset);
        assertEquals(fromBundle.getSweepData().get(0), sweepData1);
        assertEquals(fromBundle.getSweepData().get(1), sweepData2);
        assertEquals(data, fromBundle);
    }
}
