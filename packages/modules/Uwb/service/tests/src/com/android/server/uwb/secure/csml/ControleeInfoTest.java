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

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class ControleeInfoTest {

    @Test
    public void testConversion() {
        FiraProtocolVersion minPhyVersionSupported = new FiraProtocolVersion(0, 1);
        FiraProtocolVersion maxPhyVersionSupported = new FiraProtocolVersion(1, 1);
        FiraProtocolVersion minMacVersionSupported = new FiraProtocolVersion(0, 1);
        FiraProtocolVersion maxMacVersionSupported = new FiraProtocolVersion(1, 1);

        EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoles =
                EnumSet.noneOf(FiraParams.DeviceRoleCapabilityFlag.class);
        deviceRoles.add(FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
        deviceRoles.add(FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);

        byte rangingMethod = (byte) 0xFF & DS_TWR_DEFERRED;

        EnumSet<FiraParams.StsCapabilityFlag> stsConfig =
                EnumSet.noneOf(FiraParams.StsCapabilityFlag.class);
        stsConfig.add(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
        EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeMode = EnumSet.noneOf(
                FiraParams.MultiNodeCapabilityFlag.class);

        Byte rangingTimeStruct = 0;
        Byte scheduledMode = 1;
        Boolean hoppingMode = true;
        Boolean blockStriding = false;
        Boolean uwbInitiationTime = false;
        List<Integer> channels = new ArrayList<>();
        channels.add(9);
        EnumSet<FiraParams.RframeCapabilityFlag> rFrameConfig = EnumSet.noneOf(
                FiraParams.RframeCapabilityFlag.class);
        byte ccConstraintLength = 0;
        EnumSet<FiraParams.AoaCapabilityFlag> aoaSupport = EnumSet.noneOf(
                FiraParams.AoaCapabilityFlag.class);
        aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
        aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
        aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
        aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
        Byte extendedMacSupport = 0;

        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setMinPhyVersionSupported(minPhyVersionSupported)
                .setMaxPhyVersionSupported(maxPhyVersionSupported)
                .setMinMacVersionSupported(minMacVersionSupported)
                .setMaxMacVersionSupported(maxMacVersionSupported)
                .setDeviceRoles(deviceRoles)
                .setRangingMethod(rangingMethod)
                .setStsConfig(stsConfig)
                .setMultiMode(multiNodeMode)
                .setRangingTimeStruct(rangingTimeStruct)
                .setScheduledMode(scheduledMode)
                .setHoppingMode(hoppingMode)
                .setBlockStriding(blockStriding)
                .setUwbInitiationTime(uwbInitiationTime)
                .setChannels(channels)
                .setRFrameConfig(rFrameConfig)
                .setCcConstraintLength(ccConstraintLength)
                .setAoaSupport(aoaSupport)
                .setExtendedMacSupport(extendedMacSupport)
                .build();
        ControleeInfo controleeInfo = new ControleeInfo.Builder()
                .setUwbCapability(uwbCapability)
                .build();

        byte[] controleeRaw = controleeInfo.toBytes();

        ControleeInfo controleeInfo1 = ControleeInfo.fromBytes(controleeRaw);
        UwbCapability uwbCapability1 = controleeInfo1.mUwbCapability.get();

        assertEquals(uwbCapability1.mMinPhyVersionSupported, minPhyVersionSupported);
        assertEquals(uwbCapability1.mMaxPhyVersionSupported, maxPhyVersionSupported);
        assertEquals(uwbCapability1.mMinMacVersionSupported, minMacVersionSupported);
        assertEquals(uwbCapability1.mMaxMacVersionSupported, maxMacVersionSupported);
        assertEquals(uwbCapability1.mDeviceRoles.get(), deviceRoles);
        assertEquals((byte) uwbCapability1.mRangingMethod.get(), rangingMethod);
        assertEquals(uwbCapability1.mStsConfig.get(), stsConfig);
        assertEquals(uwbCapability1.mMultiNodeMode.get(), multiNodeMode);
        assertEquals(uwbCapability1.mRangingTimeStruct.get(), rangingTimeStruct);
        assertEquals(uwbCapability1.mScheduledMode.get(), scheduledMode);
        assertEquals(uwbCapability1.mHoppingMode.get(), hoppingMode);
        assertEquals(uwbCapability1.mBlockStriding.get(), blockStriding);
        assertEquals(uwbCapability1.mUwbInitiationTime.get(), uwbInitiationTime);
        assertEquals(uwbCapability1.mChannels.get(), channels);
        assertEquals(uwbCapability1.mRframeConfig.get(), rFrameConfig);
        assertEquals((byte) uwbCapability1.mCcConstraintLength.get(), ccConstraintLength);
        assertEquals(uwbCapability1.mAoaSupport.get(), aoaSupport);
        assertEquals(uwbCapability1.mExtendedMacSupport.get(), extendedMacSupport);
    }
}
