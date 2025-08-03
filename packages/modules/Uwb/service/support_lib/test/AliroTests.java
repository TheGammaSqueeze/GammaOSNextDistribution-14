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

package com.google.uwb.support;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.aliro.AliroOpenRangingParams;
import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroProtocolVersion;
import com.google.uwb.support.aliro.AliroPulseShapeCombo;
import com.google.uwb.support.aliro.AliroRangingError;
import com.google.uwb.support.aliro.AliroRangingReconfiguredParams;
import com.google.uwb.support.aliro.AliroRangingStartedParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.aliro.AliroStartRangingParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AliroTests {
    private static final AliroProtocolVersion[] PROTOCOL_VERSIONS =
            new AliroProtocolVersion[] {
                    new AliroProtocolVersion(1, 0),
                    new AliroProtocolVersion(2, 0),
                    new AliroProtocolVersion(2, 1)
            };

    private static final  Integer[] UWB_CONFIGS =
            new Integer[] {AliroParams.UWB_CONFIG_0, AliroParams.UWB_CONFIG_1};
    private static final AliroPulseShapeCombo[] PULSE_SHAPE_COMBOS =
            new AliroPulseShapeCombo[] {
                    new AliroPulseShapeCombo(
                            AliroParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                            AliroParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE),
                    new AliroPulseShapeCombo(
                            AliroParams.PULSE_SHAPE_PRECURSOR_FREE,
                            AliroParams.PULSE_SHAPE_PRECURSOR_FREE),
                    new AliroPulseShapeCombo(
                            AliroParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL,
                            AliroParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)
            };
    private static final int RAN_MULTIPLIER = 200;
    private static final Integer[] CHAPS_PER_SLOTS =
            new Integer[] {AliroParams.CHAPS_PER_SLOT_4, AliroParams.CHAPS_PER_SLOT_12};
    private static final Integer[] SYNC_CODES = new Integer[] {10, 23};
    private static final Integer[] CHANNELS =
            new Integer[] {AliroParams.UWB_CHANNEL_5, AliroParams.UWB_CHANNEL_9};
    private static final Integer[] HOPPING_CONFIG_MODES =
            new Integer[] {
                    AliroParams.HOPPING_CONFIG_MODE_ADAPTIVE,
                    AliroParams.HOPPING_CONFIG_MODE_CONTINUOUS
            };
    private static final Integer[] HOPPING_SEQUENCES =
            new Integer[] {AliroParams.HOPPING_SEQUENCE_AES, AliroParams.HOPPING_SEQUENCE_DEFAULT};

    @Test
    public void testOpenRangingParams() {
        AliroProtocolVersion protocolVersion = AliroParams.PROTOCOL_VERSION_1_0;
        @AliroParams.UwbConfig int uwbConfig = AliroParams.UWB_CONFIG_1;
        AliroPulseShapeCombo pulseShapeCombo =
                new AliroPulseShapeCombo(
                        AliroParams.PULSE_SHAPE_PRECURSOR_FREE,
                        AliroParams.PULSE_SHAPE_PRECURSOR_FREE);
        int sessionId = 10;
        int ranMultiplier = 128;
        @AliroParams.Channel int channel = AliroParams.UWB_CHANNEL_9;
        @AliroParams.ChapsPerSlot int chapsPerSlot = AliroParams.CHAPS_PER_SLOT_6;
        int numResponderNodes = 9;
        @AliroParams.SlotsPerRound int numSlotsPerRound = AliroParams.SLOTS_PER_ROUND_12;
        @AliroParams.SyncCodeIndex int syncCodeIdx = 22;
        @AliroParams.HoppingConfigMode int hoppingConfigMode =
                AliroParams.HOPPING_CONFIG_MODE_ADAPTIVE;
        @AliroParams.HoppingSequence int hoppingSequence = AliroParams.HOPPING_SEQUENCE_AES;
        long absoluteInitiationTimeUs = 20_000L;
        int rangeDataNtfConfig = AliroParams.RANGE_DATA_NTF_CONFIG_ENABLE;
        int rangeDataNtfProximityNear = 100;
        int rangeDataNtfProximityFar = 200;
        double rangeDataNtfAoaAzimuthLower = -0.7;
        double rangeDataNtfAoaAzimuthUpper = +1.3;
        double rangeDataNtfAoaElevationLower = -1.1;
        double rangeDataNtfAoaElevationUpper = +1.2;

        AliroOpenRangingParams params =
                new AliroOpenRangingParams.Builder()
                        .setProtocolVersion(protocolVersion)
                        .setUwbConfig(uwbConfig)
                        .setPulseShapeCombo(pulseShapeCombo)
                        .setSessionId(sessionId)
                        .setRanMultiplier(ranMultiplier)
                        .setChannel(channel)
                        .setNumChapsPerSlot(chapsPerSlot)
                        .setNumResponderNodes(numResponderNodes)
                        .setNumSlotsPerRound(numSlotsPerRound)
                        .setSyncCodeIndex(syncCodeIdx)
                        .setHoppingConfigMode(hoppingConfigMode)
                        .setHoppingSequence(hoppingSequence)
                        .setAbsoluteInitiationTimeUs(absoluteInitiationTimeUs)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataNtfProximityNear(rangeDataNtfProximityNear)
                        .setRangeDataNtfProximityFar(rangeDataNtfProximityFar)
                        .setRangeDataNtfAoaAzimuthLower(rangeDataNtfAoaAzimuthLower)
                        .setRangeDataNtfAoaAzimuthUpper(rangeDataNtfAoaAzimuthUpper)
                        .setRangeDataNtfAoaElevationLower(rangeDataNtfAoaElevationLower)
                        .setRangeDataNtfAoaElevationUpper(rangeDataNtfAoaElevationUpper)
                        .build();

        assertEquals(params.getProtocolVersion(), protocolVersion);
        assertEquals(params.getUwbConfig(), uwbConfig);
        assertEquals(
                params.getPulseShapeCombo().getInitiatorTx(), pulseShapeCombo.getInitiatorTx());
        assertEquals(
                params.getPulseShapeCombo().getResponderTx(), pulseShapeCombo.getResponderTx());
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getSessionType(), AliroParams.SESSION_TYPE_ALIRO);
        assertEquals(params.getRanMultiplier(), ranMultiplier);
        assertEquals(params.getChannel(), channel);
        assertEquals(params.getNumChapsPerSlot(), chapsPerSlot);
        assertEquals(params.getNumResponderNodes(), numResponderNodes);
        assertEquals(params.getNumSlotsPerRound(), numSlotsPerRound);
        assertEquals(params.getSyncCodeIndex(), syncCodeIdx);
        assertEquals(params.getHoppingConfigMode(), hoppingConfigMode);
        assertEquals(params.getHoppingSequence(), hoppingSequence);
        assertEquals(params.getAbsoluteInitiationTimeUs(), absoluteInitiationTimeUs);

        AliroOpenRangingParams fromBundle = AliroOpenRangingParams.fromBundle(params.toBundle());
        assertEquals(fromBundle.getProtocolVersion(), protocolVersion);
        assertEquals(fromBundle.getUwbConfig(), uwbConfig);
        assertEquals(
                fromBundle.getPulseShapeCombo().getInitiatorTx(), pulseShapeCombo.getInitiatorTx());
        assertEquals(
                fromBundle.getPulseShapeCombo().getResponderTx(), pulseShapeCombo.getResponderTx());
        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);
        assertEquals(fromBundle.getChannel(), channel);
        assertEquals(fromBundle.getNumChapsPerSlot(), chapsPerSlot);
        assertEquals(fromBundle.getNumResponderNodes(), numResponderNodes);
        assertEquals(fromBundle.getNumSlotsPerRound(), numSlotsPerRound);
        assertEquals(fromBundle.getSyncCodeIndex(), syncCodeIdx);
        assertEquals(fromBundle.getHoppingConfigMode(), hoppingConfigMode);
        assertEquals(fromBundle.getHoppingSequence(), hoppingSequence);
        assertEquals(fromBundle.getAbsoluteInitiationTimeUs(), absoluteInitiationTimeUs);
        assertEquals(fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(fromBundle.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(fromBundle.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(
                fromBundle.getRangeDataNtfAoaAzimuthLower(), rangeDataNtfAoaAzimuthLower, 0.1d);
        assertEquals(
                fromBundle.getRangeDataNtfAoaAzimuthUpper(), rangeDataNtfAoaAzimuthUpper, 0.1d);
        assertEquals(
                fromBundle.getRangeDataNtfAoaElevationLower(), rangeDataNtfAoaElevationLower, 0.1d);
        assertEquals(
                fromBundle.getRangeDataNtfAoaElevationUpper(), rangeDataNtfAoaElevationUpper, 0.1d);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingError() {
        @AliroParams.ProtocolError int error = AliroParams.PROTOCOL_ERROR_SE_BUSY;
        AliroRangingError params = new AliroRangingError.Builder().setError(error).build();

        assertEquals(params.getError(), error);

        AliroRangingError fromBundle = AliroRangingError.fromBundle(params.toBundle());
        assertEquals(fromBundle.getError(), error);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingReconfiguredParams() {
        AliroRangingReconfiguredParams params =
                new AliroRangingReconfiguredParams.Builder().build();

        AliroRangingReconfiguredParams fromBundle =
                AliroRangingReconfiguredParams.fromBundle(params.toBundle());

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStartRangingParams() {
        int sessionId = 10;
        int ranMultiplier = 128;
        long initiationTimeMs = 10;
        long absoluteInitiationTimeUs = 15_000L;

        AliroStartRangingParams params =
                new AliroStartRangingParams.Builder()
                        .setSessionId(sessionId)
                        .setRanMultiplier(ranMultiplier)
                        .setInitiationTimeMs(initiationTimeMs)
                        .setAbsoluteInitiationTimeUs(absoluteInitiationTimeUs)
                        .build();

        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getRanMultiplier(), ranMultiplier);
        assertEquals(params.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(params.getAbsoluteInitiationTimeUs(), absoluteInitiationTimeUs);

        AliroStartRangingParams fromBundle = AliroStartRangingParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);
        assertEquals(fromBundle.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(fromBundle.getAbsoluteInitiationTimeUs(), absoluteInitiationTimeUs);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingStartedParams() {
        int hopModeKey = 98876444;
        int startingStsIndex = 246802468;
        @AliroParams.SyncCodeIndex int syncCodeIndex = 10;
        long uwbTime0 = 50;
        int ranMultiplier = 10;

        AliroRangingStartedParams params =
                new AliroRangingStartedParams.Builder()
                        .setHopModeKey(hopModeKey)
                        .setStartingStsIndex(startingStsIndex)
                        .setSyncCodeIndex(syncCodeIndex)
                        .setUwbTime0(uwbTime0)
                        .setRanMultiplier(ranMultiplier)
                        .build();

        assertEquals(params.getHopModeKey(), hopModeKey);
        assertEquals(params.getStartingStsIndex(), startingStsIndex);
        assertEquals(params.getSyncCodeIndex(), syncCodeIndex);
        assertEquals(params.getUwbTime0(), uwbTime0);
        assertEquals(params.getRanMultiplier(), ranMultiplier);

        AliroRangingStartedParams fromBundle =
                AliroRangingStartedParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getHopModeKey(), hopModeKey);
        assertEquals(fromBundle.getStartingStsIndex(), startingStsIndex);
        assertEquals(fromBundle.getSyncCodeIndex(), syncCodeIndex);
        assertEquals(fromBundle.getUwbTime0(), uwbTime0);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testSpecificationParams() {
        AliroSpecificationParams.Builder paramsBuilder = new AliroSpecificationParams.Builder();
        for (AliroProtocolVersion p : PROTOCOL_VERSIONS) {
            paramsBuilder.addProtocolVersion(p);
        }

        for (int uwbConfig : UWB_CONFIGS) {
            paramsBuilder.addUwbConfig(uwbConfig);
        }

        for (AliroPulseShapeCombo pulseShapeCombo : PULSE_SHAPE_COMBOS) {
            paramsBuilder.addPulseShapeCombo(pulseShapeCombo);
        }

        paramsBuilder.setRanMultiplier(RAN_MULTIPLIER);

        for (int chapsPerSlot : CHAPS_PER_SLOTS) {
            paramsBuilder.addChapsPerSlot(chapsPerSlot);
        }

        for (int syncCode : SYNC_CODES) {
            paramsBuilder.addSyncCode(syncCode);
        }

        for (int channel : CHANNELS) {
            paramsBuilder.addChannel(channel);
        }

        for (int hoppingConfigMode : HOPPING_CONFIG_MODES) {
            paramsBuilder.addHoppingConfigMode(hoppingConfigMode);
        }

        for (int hoppingSequence : HOPPING_SEQUENCES) {
            paramsBuilder.addHoppingSequence(hoppingSequence);
        }

        AliroSpecificationParams params = paramsBuilder.build();
        assertArrayEquals(params.getProtocolVersions().toArray(), PROTOCOL_VERSIONS);
        assertArrayEquals(params.getUwbConfigs().toArray(), UWB_CONFIGS);
        assertArrayEquals(params.getPulseShapeCombos().toArray(), PULSE_SHAPE_COMBOS);
        assertEquals(params.getRanMultiplier(), RAN_MULTIPLIER);
        assertArrayEquals(params.getChapsPerSlot().toArray(), CHAPS_PER_SLOTS);
        assertArrayEquals(params.getSyncCodes().toArray(), SYNC_CODES);
        assertArrayEquals(params.getChannels().toArray(), CHANNELS);
        assertArrayEquals(params.getHoppingConfigModes().toArray(), HOPPING_CONFIG_MODES);
        assertArrayEquals(params.getHoppingSequences().toArray(), HOPPING_SEQUENCES);

        AliroSpecificationParams fromBundle =
                AliroSpecificationParams.fromBundle(params.toBundle());
        assertArrayEquals(fromBundle.getProtocolVersions().toArray(), PROTOCOL_VERSIONS);
        assertArrayEquals(fromBundle.getUwbConfigs().toArray(), UWB_CONFIGS);
        assertArrayEquals(fromBundle.getPulseShapeCombos().toArray(), PULSE_SHAPE_COMBOS);
        assertEquals(fromBundle.getRanMultiplier(), RAN_MULTIPLIER);
        assertArrayEquals(fromBundle.getChapsPerSlot().toArray(), CHAPS_PER_SLOTS);
        assertArrayEquals(fromBundle.getSyncCodes().toArray(), SYNC_CODES);
        assertArrayEquals(fromBundle.getChannels().toArray(), CHANNELS);
        assertArrayEquals(fromBundle.getHoppingConfigModes().toArray(), HOPPING_CONFIG_MODES);
        assertArrayEquals(fromBundle.getHoppingSequences().toArray(), HOPPING_SEQUENCES);

        verifyProtocolPresent(params);
        assertTrue(params.equals(fromBundle));

        // Add random channel to params builder to force inequality.
        paramsBuilder.addChannel(0);
        // Rebuild params.
        params = paramsBuilder.build();
        // Test that params and fromBundle are not equal.
        assertTrue(!params.equals(fromBundle));
    }

    @Test
    public void testSpecificationParams_whenNoChannelsSet() {
        AliroSpecificationParams.Builder paramsBuilder = new AliroSpecificationParams.Builder();
        for (AliroProtocolVersion p : PROTOCOL_VERSIONS) {
            paramsBuilder.addProtocolVersion(p);
        }
        for (int uwbConfig : UWB_CONFIGS) {
            paramsBuilder.addUwbConfig(uwbConfig);
        }
        for (AliroPulseShapeCombo pulseShapeCombo : PULSE_SHAPE_COMBOS) {
            paramsBuilder.addPulseShapeCombo(pulseShapeCombo);
        }
        paramsBuilder.setRanMultiplier(RAN_MULTIPLIER);
        for (int chapsPerSlot : CHAPS_PER_SLOTS) {
            paramsBuilder.addChapsPerSlot(chapsPerSlot);
        }
        for (int syncCode : SYNC_CODES) {
            paramsBuilder.addSyncCode(syncCode);
        }
        for (int hoppingConfigMode : HOPPING_CONFIG_MODES) {
            paramsBuilder.addHoppingConfigMode(hoppingConfigMode);
        }
        for (int hoppingSequence : HOPPING_SEQUENCES) {
            paramsBuilder.addHoppingSequence(hoppingSequence);
        }
        AliroSpecificationParams params = paramsBuilder.build();
        assertEquals(List.of(), params.getChannels());

        AliroSpecificationParams fromBundle =
                AliroSpecificationParams.fromBundle(params.toBundle());
        assertEquals(List.of(), fromBundle.getChannels());
    }

    private void verifyProtocolPresent(Params params) {
        assertTrue(Params.isProtocol(params.toBundle(), AliroParams.PROTOCOL_NAME));
    }

    private void verifyBundlesEqual(Params params, Params fromBundle) {
        assertTrue(PersistableBundle.kindofEquals(params.toBundle(), fromBundle.toBundle()));
    }
}
