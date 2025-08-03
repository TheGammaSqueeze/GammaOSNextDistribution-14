/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GenericTests {

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
        FiraSpecificationParams firaSpecificationParams =
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
                        .setMultiNodeCapabilities(multiNodeCapabilities)
                        .setPrfCapabilities(prfCapabilities)
                        .setRangingRoundCapabilities(rangingRoundCapabilities)
                        .setRframeCapabilities(rframeCapabilities)
                        .setStsCapabilities(stsCapabilities)
                        .setPsduDataRateCapabilities(psduDataRateCapabilities)
                        .setBprfParameterSetCapabilities(bprfCapabilities)
                        .setHprfParameterSetCapabilities(hprfCapabilities)
                        .build();

        CccProtocolVersion[] protocolVersions =
                new CccProtocolVersion[] {
                    new CccProtocolVersion(1, 0),
                    new CccProtocolVersion(2, 0),
                    new CccProtocolVersion(2, 1)
                };

        Integer[] uwbConfigs = new Integer[] {CccParams.UWB_CONFIG_0, CccParams.UWB_CONFIG_1};
        CccPulseShapeCombo[] pulseShapeCombos =
                new CccPulseShapeCombo[] {
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                            CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE),
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE,
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE),
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL,
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)
                };
        int ranMultiplier = 200;
        Integer[] chapsPerSlots =
                new Integer[] {CccParams.CHAPS_PER_SLOT_4, CccParams.CHAPS_PER_SLOT_12};
        Integer[] syncCodes = new Integer[] {10, 23};
        Integer[] channels = new Integer[] {CccParams.UWB_CHANNEL_5, CccParams.UWB_CHANNEL_9};
        Integer[] hoppingConfigModes =
                new Integer[] {
                    CccParams.HOPPING_CONFIG_MODE_ADAPTIVE, CccParams.HOPPING_CONFIG_MODE_CONTINUOUS
                };
        Integer[] hoppingSequences =
                new Integer[] {CccParams.HOPPING_SEQUENCE_AES, CccParams.HOPPING_SEQUENCE_DEFAULT};
        CccSpecificationParams.Builder paramsBuilder = new CccSpecificationParams.Builder();
        for (CccProtocolVersion p : protocolVersions) {
            paramsBuilder.addProtocolVersion(p);
        }
        for (int uwbConfig : uwbConfigs) {
            paramsBuilder.addUwbConfig(uwbConfig);
        }
        for (CccPulseShapeCombo pulseShapeCombo : pulseShapeCombos) {
            paramsBuilder.addPulseShapeCombo(pulseShapeCombo);
        }
        paramsBuilder.setRanMultiplier(ranMultiplier);
        for (int chapsPerSlot : chapsPerSlots) {
            paramsBuilder.addChapsPerSlot(chapsPerSlot);
        }
        for (int syncCode : syncCodes) {
            paramsBuilder.addSyncCode(syncCode);
        }
        for (int channel : channels) {
            paramsBuilder.addChannel(channel);
        }
        for (int hoppingConfigMode : hoppingConfigModes) {
            paramsBuilder.addHoppingConfigMode(hoppingConfigMode);
        }
        for (int hoppingSequence : hoppingSequences) {
            paramsBuilder.addHoppingSequence(hoppingSequence);
        }
        CccSpecificationParams cccSpecificationParams = paramsBuilder.build();

        RadarSpecificationParams radarSpecificationParams =
                new RadarSpecificationParams.Builder()
                        .addRadarCapability(
                                RadarParams.RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT)
                        .build();

        boolean hasPowerStatsSupport = true;
        GenericSpecificationParams genericSpecificationParams =
                new GenericSpecificationParams.Builder()
                        .setFiraSpecificationParams(firaSpecificationParams)
                        .setCccSpecificationParams(cccSpecificationParams)
                        .setRadarSpecificationParams(radarSpecificationParams)
                        .hasPowerStatsSupport(hasPowerStatsSupport)
                        .build();
        firaSpecificationParams = genericSpecificationParams.getFiraSpecificationParams();
        cccSpecificationParams = genericSpecificationParams.getCccSpecificationParams();
        radarSpecificationParams = genericSpecificationParams.getRadarSpecificationParams();

        assertEquals(minPhyVersionSupported, firaSpecificationParams.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, firaSpecificationParams.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, firaSpecificationParams.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, firaSpecificationParams.getMaxMacVersionSupported());
        assertEquals(supportedChannels, firaSpecificationParams.getSupportedChannels());
        assertEquals(aoaCapabilities, firaSpecificationParams.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, firaSpecificationParams.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, firaSpecificationParams.hasBlockStridingSupport());
        assertEquals(
                hasNonDeferredModeSupport, firaSpecificationParams.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, firaSpecificationParams.hasInitiationTimeSupport());
        assertEquals(multiNodeCapabilities, firaSpecificationParams.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, firaSpecificationParams.getPrfCapabilities());
        assertEquals(
                rangingRoundCapabilities, firaSpecificationParams.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, firaSpecificationParams.getRframeCapabilities());
        assertEquals(stsCapabilities, firaSpecificationParams.getStsCapabilities());
        assertEquals(
                psduDataRateCapabilities, firaSpecificationParams.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, firaSpecificationParams.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, firaSpecificationParams.getHprfParameterSetCapabilities());

        assertArrayEquals(cccSpecificationParams.getProtocolVersions().toArray(), protocolVersions);
        assertArrayEquals(cccSpecificationParams.getUwbConfigs().toArray(), uwbConfigs);
        assertArrayEquals(cccSpecificationParams.getPulseShapeCombos().toArray(), pulseShapeCombos);
        assertEquals(cccSpecificationParams.getRanMultiplier(), ranMultiplier);
        assertArrayEquals(cccSpecificationParams.getChapsPerSlot().toArray(), chapsPerSlots);
        assertArrayEquals(cccSpecificationParams.getSyncCodes().toArray(), syncCodes);
        assertArrayEquals(cccSpecificationParams.getChannels().toArray(), channels);
        assertArrayEquals(
                cccSpecificationParams.getHoppingConfigModes().toArray(), hoppingConfigModes);
        assertArrayEquals(cccSpecificationParams.getHoppingSequences().toArray(), hoppingSequences);

        assertEquals(hasPowerStatsSupport, genericSpecificationParams.hasPowerStatsSupport());
        assertEquals(radarSpecificationParams.getRadarCapabilities().size(), 1);
        assertTrue(
                radarSpecificationParams
                        .getRadarCapabilities()
                        .contains(RadarParams.RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT));
    }
}
