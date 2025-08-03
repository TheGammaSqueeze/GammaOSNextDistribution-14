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

package com.android.server.uwb.secure.csml;

import static com.android.server.uwb.config.CapabilityParam.DS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.OWR_UL_TDOA;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_NON_DEFERRED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_3;
import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_7;
import static com.google.uwb.support.fira.FiraParams.CONTENTION_BASED_RANGING;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_2_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DL_TDOA;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP0;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.TIME_SCHEDULED_RANGING;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.config.CapabilityParam;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class UwbCapabilityTest {

    @Test
    public void phyVersionCompatibilityTest() {
        UwbCapability capA = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(1, 1))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(2, 2))
                .build();

        UwbCapability capB = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(2, 3))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(4, 4))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isFalse();

        capB = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(3, 0))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(4, 4))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isFalse();

        capB = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(2, 2))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(3, 3))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isTrue();

        capB = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(2, 0))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(3, 3))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isTrue();
    }

    @Test
    public void macVersionCompatibilityTest() {
        UwbCapability capA = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(1, 1))
                .setMaxMacVersionSupported(new FiraProtocolVersion(2, 2))
                .build();
        UwbCapability capB = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(2, 3))
                .setMaxMacVersionSupported(new FiraProtocolVersion(4, 4))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isFalse();

        capB = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(3, 0))
                .setMaxMacVersionSupported(new FiraProtocolVersion(4, 4))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isFalse();

        capB = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(2, 2))
                .setMaxMacVersionSupported(new FiraProtocolVersion(3, 3))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isTrue();

        capB = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(2, 0))
                .setMaxMacVersionSupported(new FiraProtocolVersion(3, 3))
                .build();

        assertThat(capA.isCompatibleTo(capB)).isTrue();
    }

    @Test
    public void getPreferredPhyVersion() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setMinPhyVersionSupported(new FiraProtocolVersion(3, 3))
                .setMaxPhyVersionSupported(new FiraProtocolVersion(5, 5))
                .build();

        assertThat(uwbCapability.getPreferredPhyVersion(new FiraProtocolVersion(3, 2)))
                .isEqualTo(new FiraProtocolVersion(3, 3));
        assertThat(uwbCapability.getPreferredPhyVersion(new FiraProtocolVersion(2, 1)))
                .isEqualTo(new FiraProtocolVersion(3 , 3));
        assertThat(uwbCapability.getPreferredPhyVersion(new FiraProtocolVersion(3, 4)))
                .isEqualTo(new FiraProtocolVersion(3, 4));
        assertThat(uwbCapability.getPreferredPhyVersion(new FiraProtocolVersion(4, 4)))
                .isEqualTo(new FiraProtocolVersion(4, 4));
    }

    @Test
    public void getPreferredMacVersion() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setMinMacVersionSupported(new FiraProtocolVersion(3, 3))
                .setMaxMacVersionSupported(new FiraProtocolVersion(5, 5))
                .build();

        assertThat(uwbCapability.getPreferredMacVersion(new FiraProtocolVersion(3, 2)))
                .isEqualTo(new FiraProtocolVersion(3, 3));
        assertThat(uwbCapability.getPreferredMacVersion(new FiraProtocolVersion(2, 1)))
                .isEqualTo(new FiraProtocolVersion(3 , 3));
        assertThat(uwbCapability.getPreferredMacVersion(new FiraProtocolVersion(3, 4)))
                .isEqualTo(new FiraProtocolVersion(3, 4));
        assertThat(uwbCapability.getPreferredMacVersion(new FiraProtocolVersion(4, 4)))
                .isEqualTo(new FiraProtocolVersion(4, 4));
    }

    @Test
    public void getPreferredMacAddressMode() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .build();

        assertThat(uwbCapability.getPreferredMacAddressMode(Optional.empty()))
                .isEqualTo(MAC_ADDRESS_MODE_2_BYTES);
        assertThat(uwbCapability.getPreferredMacAddressMode(
                Optional.of((byte) CapabilityParam.EXTENDED_MAC_ADDRESS)))
                .isEqualTo(MAC_ADDRESS_MODE_2_BYTES);
        assertThat(uwbCapability.getPreferredMacAddressMode(Optional.of((byte) 0)))
                .isEqualTo(MAC_ADDRESS_MODE_2_BYTES);

        uwbCapability = new UwbCapability.Builder()
                .setExtendedMacSupport((byte) CapabilityParam.EXTENDED_MAC_ADDRESS)
                .build();
        assertThat(uwbCapability.getPreferredMacAddressMode(
                Optional.of((byte) CapabilityParam.EXTENDED_MAC_ADDRESS)))
                .isEqualTo(MAC_ADDRESS_MODE_8_BYTES);
    }

    @Test
    public void getPreferredScheduleMode() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .build();
        byte timeBasedSupport = (byte) CapabilityParam.TIME_SCHEDULED_RANGING;
        byte contentionBasedSupport = (byte) CapabilityParam.CONTENTION_BASED_RANGING;
        byte timeAndContentionSupport =
                (byte) (timeBasedSupport | contentionBasedSupport | 0xFF);

        assertThat(uwbCapability.getPreferredScheduleMode(Optional.of(timeBasedSupport)))
                .isEqualTo(TIME_SCHEDULED_RANGING);

        uwbCapability = new UwbCapability.Builder()
                .setScheduledMode(timeBasedSupport)
                .build();

        assertThat(uwbCapability.getPreferredScheduleMode(Optional.empty()))
                .isEqualTo(TIME_SCHEDULED_RANGING);

        uwbCapability = new UwbCapability.Builder().setScheduledMode(timeAndContentionSupport)
                .build();

        assertThat(uwbCapability.getPreferredScheduleMode(Optional.of(timeBasedSupport)))
                .isEqualTo(TIME_SCHEDULED_RANGING);
        assertThat(uwbCapability.getPreferredScheduleMode(Optional.of(contentionBasedSupport)))
                .isEqualTo(CONTENTION_BASED_RANGING);
        assertThat(uwbCapability.getPreferredScheduleMode(Optional.of((byte) 0)))
                .isEqualTo(TIME_SCHEDULED_RANGING);
    }

    @Test
    public void getPreferredRframeConfig() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .build();

        assertThat(uwbCapability.getPreferredRframeConfig(Optional.empty()))
                .isEqualTo(RFRAME_CONFIG_SP3);

        EnumSet<FiraParams.RframeCapabilityFlag> sp3Flag = EnumSet.of(
                FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
        EnumSet<FiraParams.RframeCapabilityFlag> sp3sp1Flag = EnumSet.of(
                FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT,
                FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
        EnumSet<FiraParams.RframeCapabilityFlag> sp3sp1sp0Flag = EnumSet.of(
                FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT,
                FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT,
                FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
        uwbCapability = new UwbCapability.Builder()
                .setRFrameConfig(sp3Flag)
                .build();

        assertThat(uwbCapability.getPreferredRframeConfig(Optional.empty()))
                .isEqualTo(RFRAME_CONFIG_SP3);
        assertThat(uwbCapability.getPreferredRframeConfig(Optional.of(sp3sp1Flag)))
                .isEqualTo(RFRAME_CONFIG_SP3);

        EnumSet<FiraParams.RframeCapabilityFlag> sp1Flag = EnumSet.of(
                FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
        uwbCapability = new UwbCapability.Builder()
                .setRFrameConfig(sp1Flag)
                .build();

        assertThat(uwbCapability.getPreferredRframeConfig(Optional.of(sp3sp1sp0Flag)))
                .isEqualTo(RFRAME_CONFIG_SP1);

        EnumSet<FiraParams.RframeCapabilityFlag> sp0Flag = EnumSet.of(
                FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
        uwbCapability = new UwbCapability.Builder()
                .setRFrameConfig(sp0Flag)
                .build();

        assertThat(uwbCapability.getPreferredRframeConfig(Optional.of(sp3sp1sp0Flag)))
                .isEqualTo(RFRAME_CONFIG_SP0);
        assertThat(uwbCapability.getPreferredRframeConfig(
                Optional.of(EnumSet.noneOf(FiraParams.RframeCapabilityFlag.class))))
                .isEqualTo(RFRAME_CONFIG_SP3);
    }

    @Test
    public void getPreferredStsConfig() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .build();

        assertThat(uwbCapability.getPreferredStsConfig(Optional.empty(), true))
                .isEqualTo(STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY);
        assertThat(uwbCapability.getPreferredStsConfig(Optional.empty(), false))
                .isEqualTo(STS_CONFIG_DYNAMIC);

        EnumSet<FiraParams.StsCapabilityFlag> individualSupport = EnumSet.of(
                FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
        EnumSet<FiraParams.StsCapabilityFlag> noIndividualSupport = EnumSet.of(
                FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
        EnumSet<FiraParams.StsCapabilityFlag> bothSupport = EnumSet.of(
                FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT,
                FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);

        uwbCapability = new UwbCapability.Builder()
                .setStsConfig(bothSupport)
                .build();

        assertThat(uwbCapability.getPreferredStsConfig(Optional.of(bothSupport), true))
                .isEqualTo(STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY);
        assertThat(uwbCapability.getPreferredStsConfig(Optional.of(noIndividualSupport), true))
                .isEqualTo(STS_CONFIG_DYNAMIC);
        assertThat(uwbCapability.getPreferredStsConfig(Optional.of(individualSupport), true))
                .isEqualTo(STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY);
        assertThat(uwbCapability.getPreferredStsConfig(Optional.empty(), true))
                .isEqualTo(STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY);
    }

    @Test
    public void getPreferredChannel() {
        ImmutableList<Integer> channels1 = ImmutableList.of(1, 3, 5, 9);
        ImmutableList<Integer> channels2 = ImmutableList.of(2, 4, 5);
        ImmutableList<Integer> channels3 = ImmutableList.of(2, 8);
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setChannels(channels1)
                .build();

        assertThat(uwbCapability.getPreferredChannel(Optional.empty()).get())
                .isEqualTo(9);
        assertThat(uwbCapability.getPreferredChannel(Optional.of(channels2)).get())
                .isEqualTo(5);
        assertThat(uwbCapability.getPreferredChannel(Optional.of(channels3)).isEmpty())
                .isTrue();
    }

    @Test
    public void getPreferredHoppingMode() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setHoppingMode(true)
                .build();

        assertThat(uwbCapability.getPreferredHoppingMode(Optional.empty()))
                .isFalse();
        assertThat(uwbCapability.getPreferredHoppingMode(Optional.of(false)))
                .isFalse();
        assertThat(uwbCapability.getPreferredHoppingMode(Optional.of(true)))
                .isTrue();
    }

    @Test
    public void getPreferredConstrainLengthOfConvolutionalCode() {
        byte k3 = (byte) CapabilityParam.CC_CONSTRAINT_LENGTH_K3;
        byte k7 = (byte) CapabilityParam.CC_CONSTRAINT_LENGTH_K7;
        byte k3AndK7 = (byte) (0xFF | k3 | k7);
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setCcConstraintLength(k3AndK7)
                .build();

        assertThat(uwbCapability.getPreferredConstrainLengthOfConvolutionalCode(Optional.empty()))
                .isEqualTo(CONSTRAINT_LENGTH_3);
        assertThat(uwbCapability.getPreferredConstrainLengthOfConvolutionalCode(Optional.of(k3)))
                .isEqualTo(CONSTRAINT_LENGTH_3);
        assertThat(uwbCapability.getPreferredConstrainLengthOfConvolutionalCode(Optional.of(k7)))
                .isEqualTo(CONSTRAINT_LENGTH_7);
        assertThat(uwbCapability.getPreferredConstrainLengthOfConvolutionalCode(
                Optional.of(k3AndK7)))
                .isEqualTo(CONSTRAINT_LENGTH_7);
    }

    @Test
    public void getPreferredRangingMethod() {
        byte owr = (byte) OWR_UL_TDOA & 0xFF;
        byte ds = (byte) DS_TWR_DEFERRED & 0xFF;
        byte dsNon = (byte) DS_TWR_NON_DEFERRED & 0xFF;
        byte ss = (byte) SS_TWR_DEFERRED;
        byte ssNon = (byte) SS_TWR_NON_DEFERRED;
        byte allSupport = (byte) (owr | ds | dsNon | ss | ssNon);
        byte dsAndSs = (byte) (ds | ss);

        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setRangingMethod(allSupport)
                .build();

        assertThat(uwbCapability.getPreferredRangingMethod(Optional.empty()))
                .isEqualTo(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(ds)))
                .isEqualTo(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(dsAndSs)))
                .isEqualTo(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(dsNon)))
                .isEqualTo(RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(ss)))
                .isEqualTo(RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(ssNon)))
                .isEqualTo(RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE);
        assertThat(uwbCapability.getPreferredRangingMethod(Optional.of(owr)))
                .isEqualTo(RANGING_ROUND_USAGE_DL_TDOA);
    }

    @Test
    public void getPreferredBlockStriding() {
        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setBlockStriding(true)
                .build();

        assertThat(uwbCapability.getPreferredBlockStriding(Optional.empty()))
                .isFalse();
        assertThat(uwbCapability.getPreferredBlockStriding(Optional.of(false)))
                .isFalse();
        assertThat(uwbCapability.getPreferredBlockStriding(Optional.of(true)))
                .isTrue();
    }
}
