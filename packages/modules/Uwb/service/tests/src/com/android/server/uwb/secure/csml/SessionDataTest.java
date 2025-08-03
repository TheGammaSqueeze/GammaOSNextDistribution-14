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

import static com.android.server.uwb.config.CapabilityParam.RESPONDER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_7;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;
import android.uwb.UwbAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class SessionDataTest {

    @Test
    public void testConversion() {
        int sessionId = 1010;
        int subSessionId = 2020;
        FiraProtocolVersion phyVersion = new FiraProtocolVersion(1, 1);
        FiraProtocolVersion macVersion = new FiraProtocolVersion(1, 1);
        int deviceRole = RESPONDER;
        @FiraParams.RangingRoundUsage int rangingMethod = RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
        @FiraParams.StsConfig int stsConfig = STS_CONFIG_DYNAMIC;
        @FiraParams.MultiNodeMode int multiNodeMode = MULTI_NODE_MODE_ONE_TO_MANY;
        Byte rangingTimeStruct = 1;
        @FiraParams.SchedulingMode int scheduledMode = 0;
        Boolean hoppingMode = true;
        Boolean blockStriding = false;
        Boolean uwbInitiationTime = false;
        Integer channel = 9;
        @FiraParams.RframeConfig int rFrameConfig = RFRAME_CONFIG_SP3;
        @FiraParams.CcConstraintLength int ccConstraintLength = CONSTRAINT_LENGTH_7;
        Integer prfMode = PRF_MODE_BPRF;
        UwbAddress controleeShortAddress = UwbAddress.fromBytes(new byte[]{0x0A, 0x10});
        UwbAddress controllerMacAddress = UwbAddress.fromBytes(
                new byte[]{0x10, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20});

        EnumSet<FiraParams.AoaCapabilityFlag> rangingReportConfig = EnumSet.noneOf(
                FiraParams.AoaCapabilityFlag.class);
        rangingReportConfig.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
        rangingReportConfig.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
        rangingReportConfig.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
        rangingReportConfig.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);

        ConfigurationParams configurationParams = new ConfigurationParams.Builder()
                .setPhyVersion(phyVersion)
                .setMacVersion(macVersion)
                .setDeviceRole(deviceRole)
                .setRangingMethod(rangingMethod)
                .setStsConfig(stsConfig)
                .setMultiNodeMode(multiNodeMode)
                .setRangingTimeStruct(rangingTimeStruct)
                .setScheduleMode(scheduledMode)
                .setHoppingMode(hoppingMode)
                .setBlockStriding(blockStriding)
                .setUwbInitiationTime(uwbInitiationTime)
                .setChannel(channel)
                .setRframeConfig(rFrameConfig)
                .setCcConstraintLength(ccConstraintLength)
                .setPrfMode(prfMode)
                .setControleeShortMacAddress(controleeShortAddress)
                .setControllerMacAddress(controllerMacAddress)
                .setResultReportConfig(rangingReportConfig)
                .build();

        byte[] expectedSessionKeyInfo = new byte[] { (byte) 1, (byte) 2 };
        byte[] expectedSubSessionKeyInfo = new byte[] { (byte) 1, (byte) 2 };
        byte[] expectedSusAdditionalParams = new byte[] { (byte) 5, (byte) 6 };
        SecureRangingInfo secureRangingInfo = new SecureRangingInfo.Builder()
                .setUwbSessionKeyInfo(expectedSessionKeyInfo)
                .setUwbSubSessionKeyInfo(expectedSubSessionKeyInfo)
                .setSusAdditionalParams(expectedSusAdditionalParams)
                .build();

        SessionData sessionData = new SessionData.Builder()
                .setSessionId(sessionId)
                .setSubSessionId(subSessionId)
                .setConfigParams(configurationParams)
                .setSecureRangingInfo(secureRangingInfo)
                .build();

        byte[] sessionDataRaw = sessionData.toBytes();
        SessionData sessionData1 = SessionData.fromBytes(sessionDataRaw);

        assertThat(sessionData1).isNotNull();
        assertEquals(sessionData1.mSessionId, sessionId);
        assertEquals(sessionData1.mSubSessionId.get().intValue(), subSessionId);
        ConfigurationParams configParams1 = sessionData1.mConfigurationParams.get();

        assertEquals(configParams1.mPhyVersion, phyVersion);
        assertEquals(configParams1.mMacVersion, macVersion);
        assertEquals(configParams1.mDeviceRole.get(), Integer.valueOf(deviceRole));
        assertEquals((int) configParams1.mRangingMethod.get(), rangingMethod);
        assertEquals((int) configParams1.mStsConfig.get(), stsConfig);
        assertEquals((int) configParams1.mMultiNodeMode.get(), multiNodeMode);
        assertEquals(configParams1.mRangingTimeStruct.get(), rangingTimeStruct);
        assertEquals((int) configParams1.mScheduleMode.get(), scheduledMode);
        assertEquals(configParams1.mHoppingMode.get(), hoppingMode);
        assertEquals(configParams1.mBlockStriding.get(), blockStriding);
        assertEquals(configParams1.mUwbInitiationTime.get(), uwbInitiationTime);
        assertEquals(configParams1.mChannel.get(), channel);
        assertEquals((int) configParams1.mRframeConfig.get(), rFrameConfig);
        assertEquals((int) configParams1.mCcConstraintLength.get(), ccConstraintLength);
        assertEquals(configParams1.mPrfMode.get(), prfMode);
        assertEquals(configParams1.mControleeShortMacAddress.get(), controleeShortAddress);
        assertEquals(configParams1.mControllerMacAddress.get(), controllerMacAddress);
        assertEquals(configParams1.mResultReportConfig.get(), rangingReportConfig);

        SecureRangingInfo actualSecureRangingInfo = sessionData1.mSecureRangingInfo.get();
        assertArrayEquals(actualSecureRangingInfo.uwbSubSessionKeyInfo.get(),
                expectedSessionKeyInfo);
        assertArrayEquals(actualSecureRangingInfo.uwbSubSessionKeyInfo.get(),
                expectedSubSessionKeyInfo);
        assertArrayEquals(actualSecureRangingInfo.susAdditionalParams.get(),
                expectedSusAdditionalParams);
    }
}
