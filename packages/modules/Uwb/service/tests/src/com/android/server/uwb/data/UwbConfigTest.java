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

package com.android.server.uwb.data;

import static com.android.server.uwb.data.UwbConfig.CENTRAL;
import static com.android.server.uwb.data.UwbConfig.CONTROLEE_AND_RESPONDER;
import static com.android.server.uwb.data.UwbConfig.CONTROLLER_AND_INITIATOR;
import static com.android.server.uwb.data.UwbConfig.OOB_TYPE_BLE;
import static com.android.server.uwb.data.UwbConfig.PERIPHERAL;

import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_DISABLE;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_16;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;
import static com.google.uwb.support.fira.FiraParams.TIME_SCHEDULED_RANGING;
import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.UWB_PREAMBLE_CODE_INDEX_10;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.pm.PacsControleeSession;
import com.android.server.uwb.pm.PacsControllerSession;
import com.android.server.uwb.pm.PacsProfile;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class UwbConfigTest {
    @Mock
    private AttributionSource mAttributionSource;
    @Mock
    private Context mContext;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ServiceProfileData.ServiceProfileInfo mServiceProfileInfo;
    @Mock
    private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock
    private Handler mHandler;
    @Mock
    private Looper mLooper;
    private static final String TEST_CHIP_ID = "testChipId";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mHandler.getLooper()).thenReturn(mLooper);
    }

    @Test
    public void testUwbconfig() {
        int uwbRole = CONTROLEE_AND_RESPONDER;
        int multiNodeMode = MULTI_NODE_MODE_ONE_TO_MANY;
        int stsConfig = STS_CONFIG_DYNAMIC;
        int oobType = OOB_TYPE_BLE;
        int oobBleRole = PERIPHERAL;
        int rangingRoundUsage = RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
        int rframeConfig = RFRAME_CONFIG_SP3;
        int roundHopping = HOPPING_MODE_DISABLE;
        @FiraParams.SchedulingMode int scheduledMode = TIME_SCHEDULED_RANGING;
        int maxContentionPhaseLength = 0;
        boolean tofReport = true;
        boolean aoaAzimuthReport = false;
        boolean aoaElevationReport = false;
        boolean aoaFomReport = false;
        boolean blockStriding = false;
        int slotDurationRstu = 2400;
        int slotsPerRangingRound = 30;
        int rangingIntervalMs = 200;
        int uwbChannel = UWB_CHANNEL_9;
        int uwbPreambleCodeIndex = UWB_PREAMBLE_CODE_INDEX_10;
        int sp0PhyParameterSet = 1;
        int sp1PhyParameterSet = 3;
        int sp3PhyParameterSet = 4;
        int maxRetry = 0;
        int constraintLengthConvolutionalCode = PRF_MODE_BPRF;
        int uwbInitiationTimeMs = 0;
        int keyRotationRate = 0;
        int macFcsType = MAC_FCS_TYPE_CRC_16;
        int rangingRoundControl = 0;
        byte[] vendorID = {0x0C, 0x23};
        byte[] staticStsIV = {0x0A, 0x04, 0x00, 0x01, 0x03, 0x05};

        UwbConfig uwbConfig = new UwbConfig.Builder()
                .setUwbRole(uwbRole)
                .setMultiNodeMode(multiNodeMode)
                .setStsConfig(stsConfig)
                .setOobType(oobType)
                .setOobBleRole(oobBleRole)
                .setRangingRoundUsage(rangingRoundUsage)
                .setRframeConfig(rframeConfig)
                .setRoundHopping(roundHopping)
                .setScheduleMode(scheduledMode)
                .setMaxContentionPhaseLength(maxContentionPhaseLength)
                .setTofReport(tofReport)
                .setAoaAzimuthReport(aoaAzimuthReport)
                .setAoaElevationReport(aoaElevationReport)
                .setAoaFomReport(aoaFomReport)
                .setBlockStriding(blockStriding)
                .setSlotDurationRstu(slotDurationRstu)
                .setSlotsPerRangingRound(slotsPerRangingRound)
                .setRangingIntervalMs(rangingIntervalMs)
                .setUwbChannel(uwbChannel)
                .setUwbPreambleCodeIndex(uwbPreambleCodeIndex)
                .setSp0PhyParameterSet(sp0PhyParameterSet)
                .setSp1PhyParameterSet(sp1PhyParameterSet)
                .setSp3PhyParameterSet(sp3PhyParameterSet)
                .setMaxRetry(maxRetry)
                .setConstraintLengthConvolutionalCode(constraintLengthConvolutionalCode)
                .setUwbInitiationTimeMs(uwbInitiationTimeMs)
                .setKeyRotationRate(keyRotationRate)
                .setKMacFcsType(macFcsType)
                .setRangingRoundControl(rangingRoundControl)
                .setVendorID(vendorID)
                .setStaticStsIV(staticStsIV)
                .setOobType(oobType)
                .setOobBleRole(oobBleRole)
                .build();

        assertEquals(uwbConfig.mUwbRole, uwbRole);
        assertEquals(uwbConfig.mRangingRoundUsage, rangingRoundUsage);
        assertEquals(uwbConfig.mMultiNodeMode, multiNodeMode);
        assertEquals(uwbConfig.mRframeConfig, rframeConfig);
        assertEquals(uwbConfig.mStsConfig, stsConfig);
        assertEquals(uwbConfig.mRoundHopping, roundHopping);
        assertEquals(uwbConfig.mScheduleMode, scheduledMode);
        assertEquals(uwbConfig.mMaxContentionPhaseLength, maxContentionPhaseLength);
        assertEquals(uwbConfig.mTofReport, tofReport);
        assertEquals(uwbConfig.mAoaAzimuthReport, aoaAzimuthReport);
        assertEquals(uwbConfig.mAoaElevationReport, aoaElevationReport);
        assertEquals(uwbConfig.mAoaFomReport, aoaFomReport);
        assertEquals(uwbConfig.mBlockStriding, blockStriding);
        assertEquals(uwbConfig.mSlotDurationRstu, slotDurationRstu);
        assertEquals(uwbConfig.mSlotsPerRangingRound, slotsPerRangingRound);
        assertEquals(uwbConfig.mRangingIntervalMs, rangingIntervalMs);
        assertEquals(uwbConfig.mUwbChannel, uwbChannel);
        assertEquals(uwbConfig.mUwbPreambleCodeIndex, uwbPreambleCodeIndex);
        assertEquals(uwbConfig.mSp0PhyParameterSet, sp0PhyParameterSet);
        assertEquals(uwbConfig.mSp1PhyParameterSet, sp1PhyParameterSet);
        assertEquals(uwbConfig.mSp3PhyParameterSet, sp3PhyParameterSet);
        assertEquals(uwbConfig.mMaxRetry, maxRetry);
        assertEquals(uwbConfig.mConstraintLengthConvolutionalCode,
                constraintLengthConvolutionalCode);
        assertEquals(uwbConfig.mUwbInitiationTimeMs, uwbInitiationTimeMs);
        assertEquals(uwbConfig.mKeyRotationRate, keyRotationRate);
        assertEquals(uwbConfig.mMacFcsType, macFcsType);
        assertEquals(uwbConfig.mRangingRoundControl, rangingRoundControl);
        assertEquals(uwbConfig.mVendorID, vendorID);
        assertEquals(uwbConfig.mStaticStsIV, staticStsIV);
        assertEquals(uwbConfig.mOobType, oobType);
        assertEquals(uwbConfig.mOobBleRole, oobBleRole);
    }

    @Test
    public void testPacsControleeProfile() {
        UwbConfig uwbConfig = PacsProfile.getPacsControleeProfile();

        assertEquals(uwbConfig.mUwbRole, CONTROLEE_AND_RESPONDER);
        assertEquals(uwbConfig.mStsConfig, STS_CONFIG_DYNAMIC);
        assertEquals(uwbConfig.mMultiNodeMode, MULTI_NODE_MODE_ONE_TO_MANY);
        assertEquals(uwbConfig.mRframeConfig, RFRAME_CONFIG_SP3);
        assertTrue(uwbConfig.mTofReport);
        assertEquals(uwbConfig.mOobType, OOB_TYPE_BLE);
        assertEquals(uwbConfig.mOobBleRole, PERIPHERAL);
    }

    @Test
    public void testPacsControllerProfile() {
        UwbConfig uwbConfig = PacsProfile.getPacsControllerProfile();

        assertEquals(uwbConfig.mUwbRole, CONTROLLER_AND_INITIATOR);
        assertEquals(uwbConfig.mStsConfig, STS_CONFIG_DYNAMIC);
        assertEquals(uwbConfig.mMultiNodeMode, MULTI_NODE_MODE_ONE_TO_MANY);
        assertEquals(uwbConfig.mRframeConfig, RFRAME_CONFIG_SP3);
        assertTrue(uwbConfig.mTofReport);
        assertEquals(uwbConfig.mOobType, OOB_TYPE_BLE);
        assertEquals(uwbConfig.mOobBleRole, CENTRAL);
    }

    @Test
    public void testGetOpenSessionParams() {
        UwbConfig controleeConfig = PacsProfile.getPacsControleeProfile();
        SessionHandle sessionHandleControlee = mock(SessionHandle.class);

        PacsControleeSession pacsControleeSession = new PacsControleeSession(
                sessionHandleControlee, mAttributionSource, mContext, mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks, mHandler, TEST_CHIP_ID);

        pacsControleeSession.mSessionInfo.setSessionId(10);
        pacsControleeSession.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        pacsControleeSession.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        FiraOpenSessionParams controleeParams =
                UwbConfig.getOpenSessionParams(pacsControleeSession.mSessionInfo, controleeConfig);

        assertEquals(controleeParams.getSessionId(), 10);
        assertEquals(controleeParams.getDeviceRole(), RANGING_DEVICE_ROLE_RESPONDER);
        assertEquals(controleeParams.getDeviceType(), RANGING_DEVICE_TYPE_CONTROLEE);

        UwbConfig controllerConfig = PacsProfile.getPacsControleeProfile();
        SessionHandle sessionHandleController = mock(SessionHandle.class);

        PacsControllerSession pacsControllerSession = new PacsControllerSession(
                sessionHandleController, mAttributionSource, mContext, mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks, mHandler, TEST_CHIP_ID);

        pacsControllerSession.mSessionInfo.setSessionId(15);
        pacsControllerSession.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        pacsControllerSession.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        FiraOpenSessionParams controllerParams =
                UwbConfig.getOpenSessionParams(pacsControllerSession.mSessionInfo,
                        controllerConfig);

        assertEquals(controllerParams.getSessionId(), 15);
        assertEquals(controllerParams.getSubSessionId(), 0);
        assertEquals(controllerParams.getDeviceRole(), RANGING_DEVICE_ROLE_RESPONDER);
        assertEquals(controllerParams.getDeviceType(), RANGING_DEVICE_TYPE_CONTROLEE);
    }

    @Test
    public void testGetOpenSessionParamsForDynamicStsIndKey() {
        UwbConfig controleeConfig = PacsProfile.getPacsControlleeProfileForDynamicStsInd();
        SessionHandle sessionHandleControlee = mock(SessionHandle.class);

        PacsControleeSession pacsControleeSession = new PacsControleeSession(
                sessionHandleControlee, mAttributionSource, mContext, mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks, mHandler, TEST_CHIP_ID);

        pacsControleeSession.mSessionInfo.setSessionId(10);
        pacsControleeSession.mSessionInfo.setSubSessionId(24);
        pacsControleeSession.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        pacsControleeSession.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        FiraOpenSessionParams controleeParams =
                UwbConfig.getOpenSessionParams(pacsControleeSession.mSessionInfo, controleeConfig);

        assertEquals(controleeParams.getSessionId(), 10);
        assertEquals(controleeParams.getSubSessionId(), 24);
        assertEquals(controleeParams.getDeviceRole(), RANGING_DEVICE_ROLE_RESPONDER);
        assertEquals(controleeParams.getDeviceType(), RANGING_DEVICE_TYPE_CONTROLEE);
    }

    @Test
    public void testPacsControllerProfileForProvSTSIndKey() {
        byte[] subSessionKey = new byte[]{0x6, 0x79, 0x6, 0x79, 0x6, 0x79, 0x6,
                                 0x79, 0x6, 0x79, 0x6, 0x79, 0x6, 0x79, 0x6, 0x79};
        byte[] sessionKey = new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                                 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78};
        UwbConfig uwbConfig = PacsProfile.getPacsControlleeProfileForProvSTSInd();
        SessionHandle sessionHandleControlee = mock(SessionHandle.class);

        PacsControleeSession pacsControleeSession = new PacsControleeSession(
                sessionHandleControlee, mAttributionSource, mContext, mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks, mHandler, TEST_CHIP_ID);

        pacsControleeSession.mSessionInfo.setSessionId(10);
        pacsControleeSession.mSessionInfo.setSubSessionId(24);
        pacsControleeSession.mSessionInfo.setSubSessionKey(subSessionKey);
        pacsControleeSession.mSessionInfo.setSessionKey(sessionKey);
        pacsControleeSession.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        pacsControleeSession.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        FiraOpenSessionParams controleeParams =
                UwbConfig.getOpenSessionParams(pacsControleeSession.mSessionInfo, uwbConfig);

        assertEquals(controleeParams.getSessionId(), 10);
        assertEquals(controleeParams.getSubSessionId(), 24);
        assertEquals(controleeParams.getSubsessionKey(), subSessionKey);
        assertEquals(controleeParams.getSessionKey(), sessionKey);
        assertEquals(controleeParams.getDeviceRole(), RANGING_DEVICE_ROLE_RESPONDER);
        assertEquals(controleeParams.getDeviceType(), RANGING_DEVICE_TYPE_CONTROLEE);
    }
}
