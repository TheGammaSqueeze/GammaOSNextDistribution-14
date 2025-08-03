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

package com.android.server.uwb;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import static com.android.modules.utils.build.SdkLevel.isAtLeastV;
import static com.android.server.uwb.UwbSessionManager.SESSION_OPEN_RANGING;
import static com.android.server.uwb.UwbTestUtils.DATA_PAYLOAD;
import static com.android.server.uwb.UwbTestUtils.MAX_DATA_SIZE;
import static com.android.server.uwb.UwbTestUtils.PEER_BAD_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS_2;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS_2_LONG;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS_LONG;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_SHORT_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_SHORT_MAC_ADDRESS_LONG;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_SHORT_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_UWB_ADDRESS_2;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_MAC_ADDRESS_LONG;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PERSISTABLE_BUNDLE;
import static com.android.server.uwb.UwbTestUtils.RANGING_MEASUREMENT_TYPE_UNDEFINED;
import static com.android.server.uwb.UwbTestUtils.TEST_SESSION_ID;
import static com.android.server.uwb.UwbTestUtils.TEST_SESSION_ID_2;
import static com.android.server.uwb.UwbTestUtils.TEST_SESSION_TYPE;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_EXTENDED;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_SHORT;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_ADVERTISER;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_OBSERVER;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_OWR_AOA_MEASUREMENT;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_DATA_TRANSFER_ERROR_DATA_TRANSFER;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_DATA_TRANSFER_OK;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_DATA_TRANSFER_REPETITION_OK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_NAME;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_DISABLE;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_ENABLE;
import static com.google.uwb.support.fira.FiraParams.SESSION_TYPE_RANGING;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_OK;
import static com.google.uwb.support.radar.RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.OnUidImportanceListener;
import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.permission.flags.Flags;
import android.util.Pair;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbAddress;
import android.uwb.UwbOemExtensionCallbackListener;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.UwbSessionManager.WaitObj;
import com.android.server.uwb.advertisement.UwbAdvertiseManager;
import com.android.server.uwb.data.DtTagUpdateRangingRoundsStatus;
import com.android.server.uwb.data.UwbDeviceInfoResponse;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRadarData;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.server.uwb.params.TlvUtil;

import com.google.uwb.support.aliro.AliroOpenRangingParams;
import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroPulseShapeCombo;
import com.google.uwb.support.aliro.AliroRangingStartedParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.aliro.AliroStartRangingParams;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdate;
import com.google.uwb.support.fira.FiraDataTransferPhaseConfig;
import com.google.uwb.support.fira.FiraDataTransferPhaseConfigStatusCode;
import com.google.uwb.support.fira.FiraHybridSessionConfig;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

public class UwbSessionManagerTest {
    private static final String TEST_CHIP_ID = "testChipId";
    private static final long MAX_FIRA_SESSION_NUM = 5;
    private static final long MAX_ALIRO_SESSION_NUM = 1;
    private static final long MAX_CCC_SESSION_NUM = 1;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final int UID_2 = 67;
    private static final String PACKAGE_NAME_2 = "com.android.uwb.2";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final AttributionSource ATTRIBUTION_SOURCE_2 =
            new AttributionSource.Builder(UID_2).setPackageName(PACKAGE_NAME_2).build();
    private static final SessionHandle SESSION_HANDLE =
            new SessionHandle(TEST_SESSION_ID, ATTRIBUTION_SOURCE, 1);
    private static final SessionHandle SESSION_HANDLE_2 =
            new SessionHandle(TEST_SESSION_ID_2, ATTRIBUTION_SOURCE_2, 2);
    private static final UwbAddress UWB_DEST_ADDRESS =
            UwbAddress.fromBytes(new byte[] {(byte) 0x03, (byte) 0x04 });
    private static final UwbAddress UWB_DEST_ADDRESS_2 =
            UwbAddress.fromBytes(new byte[] {(byte) 0x05, (byte) 0x06 });
    private static final UwbAddress UWB_DEST_ADDRESS_3 =
            UwbAddress.fromBytes(new byte[] {(byte) 0x07, (byte) 0x08 });
    private static final int TEST_RANGING_INTERVAL_MS = 200;
    private static final short DATA_SEQUENCE_NUM = 0;
    private static final short DATA_SEQUENCE_NUM_1 = 2;
    private static final int DATA_TRANSMISSION_COUNT = 1;
    private static final int DATA_TRANSMISSION_COUNT_3 = 3;
    private static final FiraProtocolVersion FIRA_VERSION_1_0 = new FiraProtocolVersion(1, 0);
    private static final FiraProtocolVersion FIRA_VERSION_1_1 = new FiraProtocolVersion(1, 1);
    private static final FiraProtocolVersion FIRA_VERSION_2_0 = new FiraProtocolVersion(2, 0);
    private static final FiraSpecificationParams FIRA_SPECIFICATION_PARAMS =
            new FiraSpecificationParams.Builder()
                    .setMinPhyVersionSupported(FIRA_VERSION_1_0)
                    .setMaxPhyVersionSupported(FIRA_VERSION_1_1)
                    .setSupportedChannels(List.of(9))
                    .setRangeDataNtfConfigCapabilities(
                            EnumSet.of(
                                        HAS_RANGE_DATA_NTF_CONFIG_DISABLE,
                                        HAS_RANGE_DATA_NTF_CONFIG_ENABLE))
                    .build();

    private static final UwbDeviceInfoResponse UWB_DEVICE_INFO_RESPONSE_1_1 =
            new UwbDeviceInfoResponse(
                    UwbUciConstants.STATUS_CODE_OK,
                    /* UciVersion 1.1 = */ 0x1001,
                    /* MacVersion 1.1 = */ 0x1001,
                    /* PhyVersion 1.1 = */ 0x1001,
                    /* UciTestVersion 1.1 = */ 0x1001,
                    /* vendor_spec_info = */ new byte[]{0x0a, 0x0b, 0x0c, 0x0d});
    private static final UwbDeviceInfoResponse UWB_DEVICE_INFO_RESPONSE_2_0 =
            new UwbDeviceInfoResponse(
                    UwbUciConstants.STATUS_CODE_OK,
                    /* UciVersion 2.0 = */ 0x0002,
                    /* MacVersion 2.0 = */ 0x0002,
                    /* PhyVersion 2.0 = */ 0x0002,
                    /* UciTestVersion 2.0 = */ 0x0002,
                    /* vendor_spec_info = */ new byte[]{0x0a, 0x0b, 0x0c, 0x0d});
    private static final AliroOpenRangingParams ALIRO_OPEN_RANGING_PARAMS_DEFAULT =
            new AliroOpenRangingParams.Builder()
                    .setProtocolVersion(AliroParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(AliroParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new AliroPulseShapeCombo(
                                    AliroParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    AliroParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(AliroParams.UWB_CHANNEL_9)
                    .setNumChapsPerSlot(AliroParams.CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(AliroParams.SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(AliroParams.HOPPING_CONFIG_MODE_NONE)
                    .setHoppingSequence(AliroParams.HOPPING_SEQUENCE_DEFAULT)
                    .build();
    private static final CccOpenRangingParams CCC_OPEN_RANGING_PARAMS_DEFAULT =
            new CccOpenRangingParams.Builder()
                .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                .setUwbConfig(CccParams.UWB_CONFIG_0)
                .setPulseShapeCombo(
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                .setSessionId(1)
                .setRanMultiplier(4)
                .setChannel(CccParams.UWB_CHANNEL_9)
                .setNumChapsPerSlot(CccParams.CHAPS_PER_SLOT_3)
                .setNumResponderNodes(1)
                .setNumSlotsPerRound(CccParams.SLOTS_PER_ROUND_6)
                .setSyncCodeIndex(1)
                .setHoppingConfigMode(CccParams.HOPPING_CONFIG_MODE_NONE)
                .setHoppingSequence(CccParams.HOPPING_SEQUENCE_DEFAULT)
                .build();
    private static final long UWBS_TIMESTAMP = 2000000L;
    private static final int HANDLE_ID = 12;
    private static final int MAX_RX_DATA_PACKETS_TO_STORE = 10;
    private static final int PID = Process.myPid();
    private static final int REFERENCE_SESSION_HANDLE = 10;

    @Mock
    private UwbConfigurationManager mUwbConfigurationManager;
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbMetrics mUwbMetrics;
    @Mock
    private UwbAdvertiseManager mUwbAdvertiseManager;
    @Mock
    private UwbSessionNotificationManager mUwbSessionNotificationManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ExecutorService mExecutorService;
    @Mock
    private AlarmManager mAlarmManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private UwbServiceCore mUwbServiceCore;
    @Mock
    private DeviceConfigFacade mDeviceConfigFacade;
    @Mock
    private AliroSpecificationParams mAliroSpecificationParams;
    @Mock
    private CccSpecificationParams mCccSpecificationParams;
    @Mock
    private UwbMultichipData mUwbMultichipData;
    private TestLooper mTestLooper = new TestLooper();
    private UwbSessionManager mUwbSessionManager;
    @Captor
    private ArgumentCaptor<OnUidImportanceListener> mOnUidImportanceListenerArgumentCaptor;
    private GenericSpecificationParams.Builder mSpecificationParamsBuilder;

    @Before
    public void setup() throws ExecutionException, InterruptedException, TimeoutException {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mUwbInjector.getMultichipData()).thenReturn(mUwbMultichipData);
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t.get();
        }).when(mUwbInjector).runTaskOnSingleThreadExecutor(any(FutureTask.class), anyInt());
        mSpecificationParamsBuilder = new GenericSpecificationParams.Builder()
                .setAliroSpecificationParams(mAliroSpecificationParams)
                .setCccSpecificationParams(mCccSpecificationParams)
                .setFiraSpecificationParams(FIRA_SPECIFICATION_PARAMS);
        when(mUwbServiceCore.getCachedSpecificationParams(any())).thenReturn(
                mSpecificationParamsBuilder.build());
        when(mAliroSpecificationParams.getMaxRangingSessionNumber()).thenReturn(
                (int) MAX_ALIRO_SESSION_NUM);
        when(mCccSpecificationParams.getMaxRangingSessionNumber()).thenReturn(
                (int) MAX_CCC_SESSION_NUM);
        when(mUwbMultichipData.getDefaultChipId()).thenReturn("default");
        when(mDeviceConfigFacade.isBackgroundRangingEnabled()).thenReturn(false);
        when(mDeviceConfigFacade.isRangingErrorStreakTimerEnabled()).thenReturn(true);

        // TODO: Don't use spy.
        mUwbSessionManager = spy(new UwbSessionManager(
                mUwbConfigurationManager,
                mNativeUwbManager,
                mUwbMetrics,
                mUwbAdvertiseManager,
                mUwbSessionNotificationManager,
                mUwbInjector,
                mAlarmManager,
                mActivityManager,
                mTestLooper.getLooper()));

        verify(mActivityManager).addOnUidImportanceListener(
                mOnUidImportanceListenerArgumentCaptor.capture(), anyInt());
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() { }

    @Test
    public void onDataReceived_extendedMacAddressFormat_owrAoA() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void onDataReceived_unsupportedMacAddressLength_owrAoa() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_BAD_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession, never()).addReceivedDataInfo(
                isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics, never()).logDataRx(eq(mockUwbSession),
                eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void onDataReceived_shortMacAddressFormat_owrAoa() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void onDataReceived_shortMacAddressFormat_dsTwr() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(
                ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS, DATA_PAYLOAD);

        verify(mUwbSessionNotificationManager).onDataReceived(
                isA(UwbSession.class), eq(PEER_EXTENDED_SHORT_UWB_ADDRESS),
                isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mockUwbSession, never()).addReceivedDataInfo(
                isA(UwbSessionManager.ReceivedDataInfo.class));
    }

    @Test
    public void onRangeDataNotificationReceivedWithValidUwbSession_twoWay() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
    }

    @Test
    public void onRangeDataNotificationReceivedWithInvalidSession_twoWay() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager, never())
                .onRangingResult(any(), eq(uwbRangingData));
    }

    // Test scenario for receiving Application payload data followed by a RANGE_DATA_NTF with an
    // OWR Aoa Measurement (such that the ExtendedMacAddress format is used for the remote device).
    @Test
    public void onRangeDataNotificationReceived_owrAoa_success_extendedMacAddress()
            throws RemoteException {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        UwbOemExtensionCallbackListener mUwbOemExtensionCallbackListener =
                mock(UwbOemExtensionCallbackListener.class);
        when(mUwbServiceCore.isOemExtensionCbRegistered()).thenReturn(true);
        when(mUwbServiceCore.getOemExtensionCallback())
                .thenReturn(mUwbOemExtensionCallbackListener);
        when(mUwbOemExtensionCallbackListener.onCheckPointedTarget(any())).thenReturn(true);

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG))
                .thenReturn(List.of(buildReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)));
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager)
                .onDataReceived(eq(mockUwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS_LONG);
        verify(mUwbMetrics).logDataToUpperLayer(eq(mockUwbSession), eq(1));
    }

    // Test scenario for receiving Application payload data followed by a RANGE_DATA_NTF with an
    // OWR Aoa Measurement (such that the ShortMacAddress format is used for the remote device).
    @Test
    public void onRangeDataNotificationReceived_owrAoa_success_shortMacAddress()
            throws RemoteException {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        UwbOemExtensionCallbackListener mUwbOemExtensionCallbackListener =
                mock(UwbOemExtensionCallbackListener.class);
        when(mUwbServiceCore.isOemExtensionCbRegistered()).thenReturn(true);
        when(mUwbServiceCore.getOemExtensionCallback())
                .thenReturn(mUwbOemExtensionCallbackListener);
        when(mUwbOemExtensionCallbackListener.onCheckPointedTarget(any())).thenReturn(true);

        // First call onDataReceived() to get the application payload data. This should always have
        // the MacAddress (in 8 Bytes), even for a Short MacAddress (MSB are zeroed out).
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_SHORT,
                UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_SHORT_MAC_ADDRESS)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_SHORT_MAC_ADDRESS_LONG))
                .thenReturn(List.of(buildReceivedDataInfo(PEER_EXTENDED_SHORT_MAC_ADDRESS_LONG)));
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager)
                .onDataReceived(eq(mockUwbSession), eq(PEER_SHORT_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_SHORT_MAC_ADDRESS_LONG);
        verify(mUwbMetrics).logDataToUpperLayer(eq(mockUwbSession), eq(1));
    }

    // Test scenario for receiving Application payload data followed by a RANGE_DATA_NTF with an
    // OWR Aoa Measurement, from Multiple advertiser devices in a UWB session.
    @Test
    public void onRangeDataNotificationReceived_owrAoa_success_multipleAdvertisers() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM_1, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS_2, DATA_PAYLOAD);
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM_1, PEER_EXTENDED_MAC_ADDRESS_2, DATA_PAYLOAD);

        verify(mockUwbSession, times(4)).addReceivedDataInfo(
                isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics, times(4)).logDataRx(
                eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData1 = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                PEER_EXTENDED_MAC_ADDRESS, UwbUciConstants.STATUS_CODE_OK);
        UwbRangingData uwbRangingData2 = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                PEER_EXTENDED_MAC_ADDRESS_2, UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS_2)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG))
                .thenReturn(List.of(
                        buildReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM),
                        buildReceivedDataInfo(
                                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM_1)));
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_2_LONG))
                .thenReturn(List.of(
                        buildReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_2_LONG, DATA_SEQUENCE_NUM),
                        buildReceivedDataInfo(
                                PEER_EXTENDED_MAC_ADDRESS_2_LONG, DATA_SEQUENCE_NUM_1)));
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData1);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData2);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData1));
        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData2));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData1.mRangingOwrAoaMeasure);
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData2.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager, times(2))
                .onDataReceived(eq(mockUwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager, times(2))
                .onDataReceived(eq(mockUwbSession), eq(PEER_EXTENDED_UWB_ADDRESS_2),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS_LONG);
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS_2_LONG);
        verify(mUwbMetrics, times(2)).logDataToUpperLayer(eq(mockUwbSession), eq(2));
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_CheckPointedTarget_Failed()
            throws RemoteException {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        UwbOemExtensionCallbackListener mUwbOemExtensionCallbackListener =
                mock(UwbOemExtensionCallbackListener.class);
        when(mUwbServiceCore.isOemExtensionCbRegistered()).thenReturn(true);
        when(mUwbServiceCore.getOemExtensionCallback())
                .thenReturn(mUwbOemExtensionCallbackListener);
        when(mUwbOemExtensionCallbackListener.onCheckPointedTarget(any())).thenReturn(false);

        // First call onDataReceived() to get the application payload data. This should always have
        // the MacAddress (in 8 Bytes), even for a Short MacAddress (MSB are zeroed out).
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS, DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_SHORT,
                UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_SHORT_MAC_ADDRESS)).thenReturn(true);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager, never())
                .onDataReceived(eq(mockUwbSession), eq(PEER_SHORT_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager, never()).removeAdvertiseTarget(PEER_SHORT_MAC_ADDRESS_LONG);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_missingUwbSession() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);

        // Setup the test scenario such that the UwbSession (from the RANGE_DATA_NTF) doesn't exist.
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verifyZeroInteractions(mUwbAdvertiseManager, mUwbSessionNotificationManager, mUwbMetrics);
    }

    @Test
    public void onRangeDataNotificationReceived_incorrectRangingMeasureType() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_UNDEFINED, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_incorrectRangingRoundUsage() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF (with an
        // incorrect RangingRoundUsage value).
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_DS_TWR_DEFERRED_MODE));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_incorrectDeviceRole() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_ADVERTISER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataNotCalled() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // Skip call to mUwbSessionManager.onDataReceived(). This means there is no application
        // payload data, and so mUwbSessionNotificationManager.onDataReceived() shouldn't be called.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG))
                .thenReturn(List.of());
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verifyZeroInteractions(mUwbSessionNotificationManager);
        verify(mUwbMetrics, never()).logDataToUpperLayer(eq(mockUwbSession), anyInt());
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataDifferentMacAddress() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // onDataReceived() called for a different MacAddress, which should be equivalent to it
        // not being called.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS_2, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG))
                .thenReturn(List.of());
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verifyZeroInteractions(mUwbSessionNotificationManager);
        verify(mUwbMetrics, never()).logDataToUpperLayer(eq(mockUwbSession), anyInt());
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataDifferentUwbSession() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        when(mockUwbSession2.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession2.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession2)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID_2));

        // onDataReceived() called for a different UwbSessionID, which should be equivalent to it
        // not being called.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID_2, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession2).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession2), eq(UwbUciConstants.STATUS_CODE_OK));

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF. Setup such
        // that there is no ReceivedDataInfo returned for the UwbSession (to simulate the test
        // scenario).
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        when(mockUwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG))
                .thenReturn(List.of());
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verifyZeroInteractions(mUwbSessionNotificationManager);
        verify(mUwbMetrics, never()).logDataToUpperLayer(eq(mockUwbSession), anyInt());
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_notPointedTarget() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getRangingRoundUsage()).thenReturn(ROUND_USAGE_OWR_AOA_MEASUREMENT);
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);
        verify(mockUwbSession).addReceivedDataInfo(isA(UwbSessionManager.ReceivedDataInfo.class));
        verify(mUwbMetrics).logDataRx(eq(mockUwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Setup isPointedTarget() to return false.
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(false);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager, never()).removeAdvertiseTarget(isA(Long.class));
        verifyZeroInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onMulticastListUpdateNotificationReceivedWithValidSession() {
        UwbMulticastListUpdateStatus mockUwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(anyInt());

        mUwbSessionManager.onMulticastListUpdateNotificationReceived(
                mockUwbMulticastListUpdateStatus);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession)
                .setMulticastListUpdateStatus(eq(mockUwbMulticastListUpdateStatus));
    }

    @Test
    public void onSessionStatusNotificationReceived_max_retry() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager).onRangingStoppedWithUciReasonCode(
                eq(mockUwbSession),
                eq(UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED));
    }

    @Test
    public void onSessionStatusNotificationReceived_session_mgmt_cmds() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager, never()).onRangingStoppedWithUciReasonCode(
                any(), anyInt());
    }

    @Test
    public void initSession_ExistedSession() throws RemoteException {
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        doReturn(true).when(mUwbSessionManager).isExistedSession(anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, TEST_SESSION_TYPE, "any", mock(Params.class), mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(
                any(), eq(RangingChangeReason.BAD_PARAMETERS), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initFiraSession_maxSessionsExceeded() throws RemoteException {
        doReturn(MAX_FIRA_SESSION_NUM).when(mUwbSessionManager).getFiraSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mock(Params.class),
                mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(),
                eq(RangingChangeReason.MAX_SESSIONS_REACHED), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initAliroSession_maxSessionsExceeded() throws RemoteException {
        doReturn(MAX_ALIRO_SESSION_NUM).when(mUwbSessionManager).getAliroSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, TEST_SESSION_TYPE, AliroParams.PROTOCOL_NAME, mock(Params.class),
                mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(),
                eq(RangingChangeReason.MAX_SESSIONS_REACHED), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initCccSession_maxSessionsExceeded() throws RemoteException {
        doReturn(MAX_CCC_SESSION_NUM).when(mUwbSessionManager).getCccSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, TEST_SESSION_TYPE, CccParams.PROTOCOL_NAME, mock(Params.class),
                mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(),
                eq(RangingChangeReason.MAX_SESSIONS_REACHED), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_UwbSession_RemoteException() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getAliroSessionCount();
        doReturn(0L).when(mUwbSessionManager).getCccSessionCount();
        doReturn(0L).when(mUwbSessionManager).getFiraSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        doThrow(new RemoteException()).when(mockBinder).linkToDeath(any(), anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(uwbSession).binderDied();
        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, atLeast(1)).unlinkToDeath(any(), anyInt());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_success() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getAliroSessionCount();
        doReturn(0L).when(mUwbSessionManager).getCccSessionCount();
        doReturn(0L).when(mUwbSessionManager).getFiraSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);

        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
    }

    @Test
    public void initSessionMaxSessions_lowestPrioritySessionReplaced() throws RemoteException {
        doReturn(false).when(mUwbInjector).isSystemApp(UID, PACKAGE_NAME);
        doReturn(true).when(mUwbInjector).isSystemApp(UID_2, PACKAGE_NAME_2);
        doReturn(1L).when(mUwbSessionManager).getMaxFiraSessionsNumber(TEST_CHIP_ID);
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);

        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mNativeUwbManager.deInitSession(anyInt(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        // Init session for 3rd party FG app
        UwbSession lowPrioUwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, SESSION_HANDLE,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(lowPrioUwbSession).when(mUwbSessionManager).createUwbSession(any(), any(),
                anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(lowPrioUwbSession).getSessionState();
        doReturn(mock(WaitObj.class)).when(lowPrioUwbSession).getWaitObj();
        doReturn(mockBinder).when(lowPrioUwbSession).getBinder();

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, SESSION_HANDLE, TEST_SESSION_ID,
                TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks,
                TEST_CHIP_ID);

        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(lowPrioUwbSession);
        mTestLooper.dispatchNext();

        // Init session for system app
        UwbSession highPrioUwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE_2, SESSION_HANDLE_2,
                        TEST_SESSION_ID_2, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(highPrioUwbSession).getBinder();
        doReturn(mock(WaitObj.class)).when(highPrioUwbSession).getWaitObj();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(highPrioUwbSession).getSessionState();
        doReturn(highPrioUwbSession).when(mUwbSessionManager).createUwbSession(any(), any(),
                anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE_2, SESSION_HANDLE_2, TEST_SESSION_ID_2,
                TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks,
                TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).initSession(TEST_SESSION_ID, TEST_SESSION_TYPE, TEST_CHIP_ID);
        verify(mNativeUwbManager).deInitSession(TEST_SESSION_ID, TEST_CHIP_ID);
        verify(mNativeUwbManager).initSession(TEST_SESSION_ID_2, TEST_SESSION_TYPE, TEST_CHIP_ID);
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mUwbSessionNotificationManager).onRangingOpened(lowPrioUwbSession);
        verify(mUwbSessionNotificationManager).onRangingClosed(lowPrioUwbSession,
                UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED);
        verify(mUwbSessionNotificationManager).onRangingOpened(highPrioUwbSession);
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isNull();
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID_2)).isEqualTo(
                highPrioUwbSession);
    }

    @Test
    public void testNeedsAppConfigUpdate_setAppConfigCalledOnStartRanging() throws RemoteException {
        UwbSession mockUwbSession = mock(UwbSession.class);

        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());
        doReturn(PROTOCOL_NAME).when(mockUwbSession).getProtocolName();
        doReturn(mock(WaitObj.class)).when(mockUwbSession).getWaitObj();
        when(mockUwbSession.getNeedsAppConfigUpdate()).thenReturn(true);

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));
        mTestLooper.dispatchAll();

        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_1_1));
    }

    @Test
    public void testCreateUwbSession_correctSessionPrioritiesSet() throws RemoteException {
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        FiraOpenSessionParams mockFiraOpenSessionParams = mock(FiraOpenSessionParams.class);
        Params mockCccParams = mock(CccParams.class);
        Params mockAliroParams = mock(AliroParams.class);
        FiraOpenSessionParams.Builder mockFiraBuilder = mock(FiraOpenSessionParams.Builder.class);

        when(mockFiraOpenSessionParams.toBuilder()).thenReturn(mockFiraBuilder);
        when(mockFiraBuilder.setSessionPriority(anyInt())).thenReturn(mockFiraBuilder);
        when(mockFiraBuilder.build()).thenReturn(mockFiraOpenSessionParams);
        when(mockFiraOpenSessionParams.getSessionPriority()).thenReturn(
                UwbSession.DEFAULT_SESSION_PRIORITY);

        // System session
        String systemPackageName = "com.google.uwb";
        when(mUwbInjector.isSystemApp(UID, systemPackageName)).thenReturn(true);
        AttributionSource attributionSourceSystemApp =
                new AttributionSource.Builder(UID).setPackageName(systemPackageName).build();
        UwbSession systemUwbSession =
                mUwbSessionManager.new UwbSession(attributionSourceSystemApp, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                        mockFiraOpenSessionParams,
                        mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(systemUwbSession.getStackSessionPriority()).isEqualTo(
                UwbSession.SYSTEM_APP_SESSION_PRIORITY);
        verify(mockFiraBuilder).setSessionPriority(UwbSession.SYSTEM_APP_SESSION_PRIORITY);

        // CCC session
        UwbSession cccUwbSession =
                mUwbSessionManager.new UwbSession(attributionSourceSystemApp, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, CccParams.PROTOCOL_NAME, mockCccParams,
                        mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(cccUwbSession.getStackSessionPriority()).isEqualTo(
                UwbSession.CCC_SESSION_PRIORITY);

        // ALIRO session
        UwbSession aliroUwbSession =
                mUwbSessionManager.new UwbSession(attributionSourceSystemApp, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, AliroParams.PROTOCOL_NAME,
                        mockAliroParams, mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(aliroUwbSession.getStackSessionPriority()).isEqualTo(
                UwbSession.ALIRO_SESSION_PRIORITY);

        // 3rd party foreground session
        String nonSystemPackageName = "com.something.app";
        when(mUwbInjector.isForegroundAppOrService(UID, nonSystemPackageName))
                .thenReturn(true);
        AttributionSource attributionSourceNonSystemApp =
                new AttributionSource.Builder(UID).setPackageName(nonSystemPackageName).build();
        UwbSession nonSystemFgUwbSession =
                mUwbSessionManager.new UwbSession(attributionSourceNonSystemApp, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                        mockFiraOpenSessionParams,
                        mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(nonSystemFgUwbSession.getStackSessionPriority()).isEqualTo(
                UwbSession.FG_SESSION_PRIORITY);
        verify(mockFiraBuilder).setSessionPriority(UwbSession.FG_SESSION_PRIORITY);


        // 3rd party background session
        when(mUwbInjector.isForegroundAppOrService(UID, nonSystemPackageName))
                .thenReturn(false);
        UwbSession nonSystemBgUwbSession =
                mUwbSessionManager.new UwbSession(attributionSourceNonSystemApp, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                        mockFiraOpenSessionParams,
                        mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(nonSystemBgUwbSession.getStackSessionPriority()).isEqualTo(
                UwbSession.BG_SESSION_PRIORITY);
        verify(mockFiraBuilder).setSessionPriority(UwbSession.BG_SESSION_PRIORITY);

    }

    @Test
    public void initSession_controleeList() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getCccSessionCount();
        doReturn(0L).when(mUwbSessionManager).getAliroSessionCount();
        doReturn(0L).when(mUwbSessionManager).getFiraSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        FiraOpenSessionParams mockParams = mock(FiraOpenSessionParams.class);
        FiraOpenSessionParams.Builder mockBuilder = mock(FiraOpenSessionParams.Builder.class);
        IBinder mockBinder = mock(IBinder.class);

        when(mockParams.getDestAddressList())
                .thenReturn(Collections.singletonList(UWB_DEST_ADDRESS));
        when(mockParams.toBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.setSessionPriority(anyInt())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockParams);

        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks,
                TEST_CHIP_ID);

        assertThat(uwbSession.getControleeList().size() == 1
                && uwbSession.getControleeList().get(0).getUwbAddress().equals(UWB_DEST_ADDRESS))
                .isTrue();

        assertThat(uwbSession.getControlee(UWB_DEST_ADDRESS)
                .getUwbAddress().equals(UWB_DEST_ADDRESS)).isTrue();

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
    }

    @Test
    public void startRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void startRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession uwbSession = mock(UwbSession.class);
        when(uwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        doReturn(uwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_currentCccSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession mockUwbSession = mock(UwbSession.class);
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        when(mockUwbSession.getProtocolName()).thenReturn(CccParams.PROTOCOL_NAME);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                any(), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_currentAliroSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession mockUwbSession = mock(UwbSession.class);
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        when(mockUwbSession.getProtocolName()).thenReturn(AliroParams.PROTOCOL_NAME);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                any(), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_currentSessionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager)
                .onRangingStartFailed(any(), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void stopRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void stopRanging_currentSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_currentSessionStateActive_owrAoa() {
        UwbSession mockUwbSession = mock(UwbSession.class);

        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        doReturn(PROTOCOL_NAME).when(mockUwbSession).getProtocolName();
        doReturn(0).when(mockUwbSession).getCurrentFiraRangingIntervalMs();

        // Setup the UwbSession to have the peer device's MacAddress stored (which happens when
        // a valid RANGE_DATA_NTF with an OWR AoA Measurement is received).
        doReturn(Set.of(PEER_EXTENDED_MAC_ADDRESS_LONG)).when(mockUwbSession)
                .getRemoteMacAddressList();

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));
        mTestLooper.dispatchNext();

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS_LONG);
    }

    @Test
    public void stopRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopped(any(),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
    }

    @Test
    public void stopRanging_currentSessionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopFailed(any(),
                eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void getUwbSession_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID);

        assertThat(actualUwbSession).isEqualTo(mockUwbSession);
    }

    @Test
    public void getUwbSession_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID - 1);

        assertThat(actualUwbSession).isNull();
    }

    @Test
    public void getSessionId_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle, mockUwbSession);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        int actualSessionId = mUwbSessionManager.getSessionId(mockSessionHandle);

        assertThat(actualSessionId).isEqualTo(TEST_SESSION_ID);
    }

    @Test
    public void getSessionId_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle, mockUwbSession);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        Integer actualSessionId = mUwbSessionManager.getSessionId(mock(SessionHandle.class));

        assertThat(actualSessionId).isNull();
    }

    @Test
    public void isExistedSession_sessionHandle_success() {
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionHandle_failed() {
        doReturn(null).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isFalse();
    }

    @Test
    public void isExistedSession_sessionId_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionId_failed() {
        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isFalse();
    }

    @Test
    public void stopAllRanging() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockUwbSession1.getChipId()).thenReturn(TEST_CHIP_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession1);

        UwbSession mockUwbSession2 = mock(UwbSession.class);
        when(mockUwbSession2.getSessionId()).thenReturn(TEST_SESSION_ID + 100);
        when(mockUwbSession2.getChipId()).thenReturn(TEST_CHIP_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession2);

        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID + 100), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopAllRanging();

        verify(mNativeUwbManager, times(2))
                .stopRanging(anyInt(), anyString());
        verify(mockUwbSession1, never()).setSessionState(anyInt());
        verify(mockUwbSession2).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
    }

    @Test
    public void setCurrentSessionState() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        mUwbSessionManager.setCurrentSessionState(
                TEST_SESSION_ID, UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_ACTIVE));
    }

    @Test
    public void getCurrentSessionState_nullSession() {
        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ERROR);
    }

    @Test
    public void getCurrentSessionState_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);
    }

    @Test
    public void getSessionIdSet() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        mUwbSessionManager.mSessionTable.put(mock(SessionHandle.class), mockUwbSession);

        Set<Integer> actualSessionIds = mUwbSessionManager.getSessionIdSet();

        assertThat(actualSessionIds).hasSize(1);
        assertThat(actualSessionIds.contains(TEST_SESSION_ID)).isTrue();
    }

    @Test
    public void reconfigure_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        int actualStatus = mUwbSessionManager.reconfigure(
                mock(SessionHandle.class), mock(Params.class));

        assertThat(actualStatus).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private UwbSession setUpUwbSessionForExecution(AttributionSource attributionSource) {
        return setUpUwbSessionForExecution(attributionSource, setupFiraParams());
    }

    private UwbSession setUpUwbSessionForExecution(AttributionSource attributionSource,
            Params params) {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getFiraSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        IBinder mockBinder = mock(IBinder.class);
        byte sessionType = TEST_SESSION_TYPE;
        if (params.getProtocolName().equals(RadarParams.PROTOCOL_NAME)
                && params instanceof RadarOpenSessionParams) {
            sessionType = (byte) RadarParams.SESSION_TYPE_RADAR;
        }
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(attributionSource, mockSessionHandle,
                        TEST_SESSION_ID, sessionType, params.getProtocolName(), params,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    private Params setupFiraParams() {
        return setupFiraParams(FIRA_VERSION_2_0);
    }

    private Params setupFiraParams(FiraProtocolVersion firaProtocolVersion) {
        return setupFiraParams(
            FiraParams.RANGING_DEVICE_ROLE_INITIATOR,
            /* rangingRoundusageOptional = */ Optional.empty(),
            firaProtocolVersion);
    }

    private Params setupFiraParams(
            int deviceRole,
            Optional<Integer> rangingRoundUsageOptional) {
        return setupFiraParams(deviceRole, rangingRoundUsageOptional, FIRA_VERSION_1_0);
    }

    private Params setupFiraParams(
            int deviceRole,
            Optional<Integer> rangingRoundUsageOptional,
            FiraProtocolVersion firaProtocolVersion) {
        FiraOpenSessionParams.Builder paramsBuilder = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UWB_DEST_ADDRESS))
                .setProtocolVersion(firaProtocolVersion)
                .setSessionId(10)
                .setSessionType(SESSION_TYPE_RANGING)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(deviceRole)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setRangingIntervalMs(TEST_RANGING_INTERVAL_MS);

        if (rangingRoundUsageOptional.isPresent()) {
            paramsBuilder.setRangingRoundUsage(rangingRoundUsageOptional.get());
        }

        return paramsBuilder.build();
    }

    private Params setupRadarParams() {
        return new RadarOpenSessionParams.Builder()
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
    }

    private UwbSession setUpCccUwbSessionForExecution(Params params) throws RemoteException {
        // Setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getCccSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, CccParams.PROTOCOL_NAME, params,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    private UwbSession setUpAliroUwbSessionForExecution(Params params) throws RemoteException {
        // Setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(0L).when(mUwbSessionManager).getAliroSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, TEST_SESSION_TYPE, AliroParams.PROTOCOL_NAME, params,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyByte(), anyString(), any(), any(), anyString());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    // Test SESSION_INIT for a FiRa ranging session on a UWBS controller (UCI ver 1.1+).
    @Test
    public void openRanging_success_fira_uwbs_v1_1() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                FiraParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_1_1,
                FIRA_VERSION_1_1);
    }

    // Test SESSION_INIT for a CCC ranging session on a UWBS controller (UCI ver 1.1+).
    @Test
    public void openRanging_success_ccc_uwbs_v1_1() throws Exception {
        UwbSession uwbSession = setUpCccUwbSessionForExecution(CCC_OPEN_RANGING_PARAMS_DEFAULT);
        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                CccParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_1_1,
                FIRA_VERSION_1_1);
    }

    // Test SESSION_INIT for an ALIRO ranging session on a UWBS controller (UCI ver 1.1+).
    @Test
    public void openRanging_success_aliro_uwbs_v1_1() throws Exception {
        UwbSession uwbSession = setUpAliroUwbSessionForExecution(ALIRO_OPEN_RANGING_PARAMS_DEFAULT);
        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                AliroParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_1_1,
                FIRA_VERSION_1_1);
    }

    // Test SESSION_INIT for a CCC ranging session on a UWBS controller (UCI ver 2.0+). Currently,
    // we don't expect this to set the {@code CccOpenRangingParams.mAbsoluteInitiationTimeUs}
    // (at SESSION_INIT time), or, result in the UWBS_TIMESTAMP being fetched from the UWBS.
    @Test
    public void openRanging_success_ccc_uwbs_v2_0_absoluteInitiationTimeComputationIsDisabled()
            throws Exception {
        // Setup the flag to "false" (which is also the default value), so that absolute
        // UWB initiation time computation is disabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(false);

        CccOpenRangingParams params = CCC_OPEN_RANGING_PARAMS_DEFAULT.toBuilder()
                .setInitiationTimeMs(1000)
                .build();
        UwbSession uwbSession = setUpCccUwbSessionForExecution(params);

        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                CccParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_2_0,
                FIRA_VERSION_2_0);
    }

    // Test SESSION_INIT for a ALIRO ranging session on a UWBS controller (UCI ver 2.0+). Currently,
    // we don't expect this to set the {@code AliroOpenRangingParams.mAbsoluteInitiationTimeUs}
    // (at SESSION_INIT time), or, result in the UWBS_TIMESTAMP being fetched from the UWBS.
    @Test
    public void openRanging_success_aliro_uwbs_v2_0_absoluteInitiationTimeComputationIsDisabled()
            throws Exception {
        // Setup the flag to "false" (which is also the default value), so that absolute
        // UWB initiation time computation is disabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(false);

        AliroOpenRangingParams params = ALIRO_OPEN_RANGING_PARAMS_DEFAULT.toBuilder()
                .setInitiationTimeMs(1000)
                .build();
        UwbSession uwbSession = setUpAliroUwbSessionForExecution(params);

        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                AliroParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_2_0,
                FIRA_VERSION_2_0);
    }

    // Test SESSION_INIT for a CCC ranging session on a UWBS controller (UCI ver 2.0+). Currently,
    // we don't expect this to set the {@code CccOpenRangingParams.mAbsoluteInitiationTimeUs}
    // (at SESSION_INIT time), or, result in the UWBS_TIMESTAMP being fetched from the UWBS.
    @Test
    public void openRanging_success_ccc_uwbs_v2_0_absoluteInitiationTimeComputationIsEnabled()
            throws Exception {
        // Setup the flag to "true", so that absolute UWB initiation time computation
        // is enabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = setUpCccUwbSessionForExecution(CCC_OPEN_RANGING_PARAMS_DEFAULT);

        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                CccParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_2_0,
                FIRA_VERSION_2_0);
    }

    // Test SESSION_INIT for a ALIRO ranging session on a UWBS controller (UCI ver 2.0+). Currently,
    // we don't expect this to set the {@code AliroOpenRangingParams.mAbsoluteInitiationTimeUs}
    // (at SESSION_INIT time), or, result in the UWBS_TIMESTAMP being fetched from the UWBS.
    @Test
    public void openRanging_success_aliro_uwbs_v2_0_absoluteInitiationTimeComputationIsEnabled()
            throws Exception {
        // Setup the flag to "true", so that absolute UWB initiation time computation
        // is enabled for a ALIRO ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = setUpAliroUwbSessionForExecution(ALIRO_OPEN_RANGING_PARAMS_DEFAULT);

        do_openRanging_success_absoluteInitiationTimeIsNotComputed(
                AliroParams.PROTOCOL_NAME, uwbSession, UWB_DEVICE_INFO_RESPONSE_2_0,
                FIRA_VERSION_2_0);
    }

    private void do_openRanging_success_absoluteInitiationTimeIsNotComputed(
            String protocolName, UwbSession uwbSession, UwbDeviceInfoResponse uwbDeviceInfoResponse,
            ProtocolVersion uwbsFiraVersion) throws Exception {
        // Setup the UWBS to return Fira UCI version as 2.0.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                uwbDeviceInfoResponse);

        // Stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, protocolName,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        // Verifications related to Open Ranging.
        verify(mNativeUwbManager).initSession(eq(TEST_SESSION_ID), anyByte(), eq(TEST_CHIP_ID));
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), any(), eq(TEST_CHIP_ID), eq(uwbsFiraVersion));
        verify(mUwbSessionNotificationManager).onRangingOpened(eq(uwbSession));
        verify(mUwbMetrics).logRangingInitEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Verify that queryUwbsTimestampMicros() is not called, as the "params" doesn't have the
        // "mInitiationTime" field set.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
    }

    @Test
    public void openRanging_success_Fira_2_0_absoluteInitiationTimeIsComputed()
            throws Exception {
        // Set the InitationTime in the FiraOpenSessionParams.
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_2_0))
                .setInitiationTime(100L)
                .build();
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE, firaParams);
        do_openRanging_success_uwbs_2_0_absoluteInitiationTimeIsComputed(
                FiraParams.PROTOCOL_NAME, uwbSession);
    }

    @Test
    public void openRanging_success_Ccc_absoluteInitiationTimeIsComputed()
            throws Exception {
        // Setup the flag to "true", so that absolute UWB initiation time computation
        // is enabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        CccOpenRangingParams params = CCC_OPEN_RANGING_PARAMS_DEFAULT.toBuilder()
                .setInitiationTimeMs(1000)
                .build();
        UwbSession uwbSession = setUpCccUwbSessionForExecution(params);

        do_openRanging_success_uwbs_2_0_absoluteInitiationTimeIsComputed(
                CccParams.PROTOCOL_NAME, uwbSession);
    }

    @Test
    public void
            openRanging_success_Aliro_absoluteInitiationTimeIsComputed()
                    throws Exception {
        // Setup the flag to "true", so that absolute UWB initiation time computation
        // is enabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        AliroOpenRangingParams params = ALIRO_OPEN_RANGING_PARAMS_DEFAULT.toBuilder()
                .setInitiationTimeMs(1000)
                .build();
        UwbSession uwbSession = setUpAliroUwbSessionForExecution(params);

        do_openRanging_success_uwbs_2_0_absoluteInitiationTimeIsComputed(
                AliroParams.PROTOCOL_NAME, uwbSession);
    }

    // Test SESSION_INIT for a Fira ranging session on a UWBS controller (UCI ver 2.0+). We expect
    // this to set the {@code FiraOpenSessionParams.mAbsoluteInitiationTime} (at SESSION_INIT time),
    // after fetching the UWBS_TIMESTAMP from the UWBS.
    private void do_openRanging_success_uwbs_2_0_absoluteInitiationTimeIsComputed(
            String protocolName, UwbSession uwbSession) throws Exception {
        // Setup the UWBS to return Fira UCI version as 2.0.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                UWB_DEVICE_INFO_RESPONSE_2_0);

        // Stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, protocolName,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        // Verifications related to CCC Open Ranging - we expect queryUwbsTimestampMicros()
        // to be called.
        verify(mNativeUwbManager).initSession(eq(TEST_SESSION_ID), anyByte(), eq(TEST_CHIP_ID));
        verify(mUwbServiceCore).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), any(), eq(TEST_CHIP_ID), eq(FIRA_VERSION_2_0));
        verify(mUwbSessionNotificationManager).onRangingOpened(eq(uwbSession));
        verify(mUwbMetrics).logRangingInitEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void openRanging_timeout() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenThrow(new IllegalStateException());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_nativeInitSessionFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_setAppConfigurationFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_wrongInitState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_wrongIdleState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession),
                        eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void testInitSessionWithNonSystemAppInFg() throws Exception {
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(true);

        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    @Test
    public void testInitSessionWithNonSystemAppNotInFg() throws Exception {
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(false);

        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        verify(uwbSession.getIUwbRangingCallbacks()).onRangingOpenFailed(
                eq(uwbSession.getSessionHandle()), eq(StateChangeReason.SYSTEM_POLICY), any());
        // No OPEN_RANGING message scheduled.
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    @Test
    public void testInitSessionWithNonSystemAppNotInFg_WhenBgRangingEnabled() throws Exception {
        when(mDeviceConfigFacade.isBackgroundRangingEnabled()).thenReturn(true);
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(false);

        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    private AttributionSource.Builder setNextAttributionSource(
            @NonNull AttributionSource.Builder builder,
            @Nullable AttributionSource nextAttributionSource) {
        if (isAtLeastV() && Flags.setNextAttributionSource()) {
            return builder.setNextAttributionSource(nextAttributionSource);
        } else {
            return builder.setNext(nextAttributionSource);
        }
    }

    private UwbSession initUwbSessionForNonSystemAppInFgInChain() throws Exception {
        when(mUwbInjector.isSystemApp(UID_2, PACKAGE_NAME_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(true);

        // simulate system app triggered the request on behalf of a fg app in fg.
        AttributionSource.Builder builder = new AttributionSource.Builder(UID)
                .setPackageName(PACKAGE_NAME);
        builder = setNextAttributionSource(builder, new AttributionSource.Builder(UID_2)
                        .setPackageName(PACKAGE_NAME_2)
                        .build());
        AttributionSource attributionSource = builder.build();

        UwbSession uwbSession = setUpUwbSessionForExecution(attributionSource);
        mUwbSessionManager.initSession(attributionSource, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        return uwbSession;
    }

    private UwbSession initUwbSessionForNonSystemAppNotInFgInChain() throws Exception {
        when(mUwbInjector.isSystemApp(UID_2, PACKAGE_NAME_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(false);

        // simulate system app triggered the request on behalf of a fg app in fg.
        AttributionSource attributionSource = new AttributionSource.Builder(UID)
                .setPackageName(PACKAGE_NAME)
                .setNext(new AttributionSource.Builder(UID_2)
                        .setPackageName(PACKAGE_NAME_2)
                        .build())
                .build();

        UwbSession uwbSession = setUpUwbSessionForExecution(attributionSource);
        mUwbSessionManager.initSession(attributionSource, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        return uwbSession;
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain() throws Exception {
        initUwbSessionForNonSystemAppInFgInChain();

        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_MoveToBgAndStayThere() throws Exception {
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();

        // Verify that an OPEN_RANGING message was scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);

        // Start Ranging
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Move the non-privileged app to background, this should result in the session getting
        // reconfigured (to disable the ranging data notifications).
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_BACKGROUND);
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID),
                eq(FIRA_VERSION_1_1));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
        verify(mUwbSessionNotificationManager, never()).onRangingReconfigured(eq(uwbSession));

        // Verify the appropriate timer is setup.
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).setExact(
                anyInt(), anyLong(), eq(UwbSession.NON_PRIVILEGED_BG_APP_TIMER_TAG),
                alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Now fire the timer callback.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE,
                 UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        alarmListenerCaptor.getValue().onAlarm();

        // Expect session stop.
        mTestLooper.dispatchAll();
        verify(mUwbSessionNotificationManager).onRangingStoppedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY), any());
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void
            testOpenRangingWithNonSystemAppInFgInChain_MoveToBgAndStayThere_WhenBgRangingEnabled()
            throws Exception {
        when(mDeviceConfigFacade.isBackgroundRangingEnabled()).thenReturn(true);
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();

        // Verify that an OPEN_RANGING message was scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);

        // Start Ranging
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Move the non-privileged app to background, this should result in the session getting
        // reconfigured (to disable the ranging data notifications).
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_BACKGROUND);
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID),
                eq(FIRA_VERSION_1_1));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
        verify(mUwbSessionNotificationManager, never()).onRangingReconfigured(eq(uwbSession));

        // Verify the timer is not setup.
        verify(mAlarmManager, never()).setExact(
                anyInt(), anyLong(), eq(UwbSession.NON_PRIVILEGED_BG_APP_TIMER_TAG),
                any(), any());
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_StartInBg_WhenBgRangingEnabled()
            throws Exception {
        when(mDeviceConfigFacade.isBackgroundRangingEnabled()).thenReturn(true);
        UwbSession uwbSession = initUwbSessionForNonSystemAppNotInFgInChain();

        // Verify that an OPEN_RANGING message was scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);

        // Start Ranging
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Ensure that we reconfigure the session immediately to disable range data notifications.
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID),
                eq(FIRA_VERSION_1_1));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
        verify(mUwbSessionNotificationManager, never()).onRangingReconfigured(eq(uwbSession));

        // Verify the timer is not setup.
        verify(mAlarmManager, never()).setExact(
                anyInt(), anyLong(), eq(UwbSession.NON_PRIVILEGED_BG_APP_TIMER_TAG),
                any(), any());
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_MoveToBgAndFg() throws Exception {
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();
        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        mTestLooper.dispatchAll();

        // Move to background.
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_BACKGROUND);
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID),
                eq(FIRA_VERSION_1_1));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
        verify(mUwbSessionNotificationManager, never()).onRangingReconfigured(eq(uwbSession));

        // Move to foreground.
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_FOREGROUND);
        mTestLooper.dispatchAll();
        paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager, times(2)).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID),
                eq(FIRA_VERSION_1_1));
        firaParams = (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE);
        verify(mUwbSessionNotificationManager, never()).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_MoveToBgTriggersSessionPriorityChange()
            throws Exception {
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();

        assertThat(uwbSession.getStackSessionPriority()).isEqualTo(UwbSession.FG_SESSION_PRIORITY);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        mTestLooper.dispatchAll();

        // Move to background.
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(false);
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        mTestLooper.dispatchAll();

        assertThat(uwbSession.getStackSessionPriority()).isEqualTo(UwbSession.BG_SESSION_PRIORITY);
    }

    @Test
    public void testOpenRangingWithNonSystemAppNotInFgInChain() throws Exception {
        when(mUwbInjector.isSystemApp(UID_2, PACKAGE_NAME_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(false);

        // simulate system app triggered the request on behalf of a fg app not in fg.
        AttributionSource.Builder builder = new AttributionSource.Builder(UID)
                .setPackageName(PACKAGE_NAME);
        builder = setNextAttributionSource(builder, new AttributionSource.Builder(UID_2)
                        .setPackageName(PACKAGE_NAME_2)
                        .build());
        AttributionSource attributionSource = builder.build();
        UwbSession uwbSession = setUpUwbSessionForExecution(attributionSource);
        mUwbSessionManager.initSession(attributionSource, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        verify(uwbSession.getIUwbRangingCallbacks()).onRangingOpenFailed(
                eq(uwbSession.getSessionHandle()), eq(StateChangeReason.SYSTEM_POLICY), any());
        // No OPEN_RANGING message scheduled.
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    private UwbSession prepareExistingUwbSessionCommon(UwbSession uwbSession) throws Exception {
        mUwbSessionManager.initSession(
                ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(), uwbSession.getSessionId(),
                uwbSession.getSessionType(), uwbSession.getProtocolName(), uwbSession.getParams(),
                uwbSession.getIUwbRangingCallbacks(), uwbSession.getChipId());
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }
    private UwbSession prepareExistingUwbSession(Params params) throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE, params);
        return prepareExistingUwbSessionCommon(uwbSession);
    }
    private UwbSession prepareExistingUwbSession() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        return prepareExistingUwbSessionCommon(uwbSession);
    }

    private UwbSession prepareExistingUwbSessionActive(Params params) throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE, params);
        uwbSession = prepareExistingUwbSessionCommon(uwbSession);
        return prepareExistingUwbSessionActiveCommon(uwbSession);
    }

    private UwbSession prepareExistingUwbSessionActive() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        uwbSession = prepareExistingUwbSessionCommon(uwbSession);
        return prepareExistingUwbSessionActiveCommon(uwbSession);
    }

    private UwbSession prepareExistingUwbSessionActiveCommon(UwbSession uwbSession)
            throws Exception {
        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        return uwbSession;
    }

    private UwbSession prepareExistingCccUwbSession() throws Exception {
        UwbSession uwbSession = setUpCccUwbSessionForExecution(CCC_OPEN_RANGING_PARAMS_DEFAULT);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, CccParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    private UwbSession prepareExistingAliroUwbSession() throws Exception {
        UwbSession uwbSession = setUpAliroUwbSessionForExecution(ALIRO_OPEN_RANGING_PARAMS_DEFAULT);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, AliroParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    @Test
    public void reconfigure_calledSuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .setRangeDataNtfConfig(1)
                        .setRangeDataProximityFar(10)
                        .setRangeDataProximityNear(2)
                        .build();

        int actualStatus = mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), params);

        assertThat(actualStatus).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what)
                .isEqualTo(UwbSessionManager.SESSION_RECONFIG_RANGING);

        // Verify the cache has been updated.
        FiraOpenSessionParams firaParams = (FiraOpenSessionParams) uwbSession.getParams();
        assertThat(firaParams.getBlockStrideLength()).isEqualTo(10);
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(1);
        assertThat(firaParams.getRangeDataNtfProximityFar()).isEqualTo(10);
        assertThat(firaParams.getRangeDataNtfProximityNear()).isEqualTo(2);
    }

    @Test
    public void startRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isTrue();
        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_success_fira_1_x_relativeUwbInitiationTime() throws Exception {
        FiraProtocolVersion protocolVersion = new FiraProtocolVersion(1, 1);
        Params startRangingParams = setupFiraParams(protocolVersion);
        UwbSession uwbSession = prepareExistingUwbSession();

        do_execStartRanging_success_uwbs_1_x_relativeUwbInitiationTime(
                uwbSession, startRangingParams, /* rangingStartedParams = */ null, protocolVersion);

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Verify that setAppConfigurations() is not called for a Fira ranging session, as the
        // "params.mInitiationTime" field is not set, and so no re-configuration is needed
        // before the UWB ranging is started.
        verify(mUwbConfigurationManager, never()).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_1_1));
    }

    @Test
    public void execStartRanging_success_ccc_1_x_relativeUwbInitiationTime() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .build();
        CccRangingStartedParams cccRangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_1_x_relativeUwbInitiationTime(
                uwbSession, cccStartRangingParams, cccRangingStartedParams,
                CccParams.PROTOCOL_VERSION_1_0);

        // Verify the absolute UWB initiation time is not set in the CccOpenRangingParams.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getAbsoluteInitiationTimeUs()).isEqualTo(0);
    }

    @Test
    public void execStartRanging_success_aliro_1_x_relativeUwbInitiationTime() throws Exception {
        UwbSession uwbSession = prepareExistingAliroUwbSession();
        AliroStartRangingParams aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .build();
        AliroRangingStartedParams aliroRangingStartedParams =
                new AliroRangingStartedParams.Builder()
                        .setStartingStsIndex(0)
                        .setUwbTime0(1)
                        .setHopModeKey(0)
                        .setSyncCodeIndex(1)
                        .setRanMultiplier(4)
                        .build();

        do_execStartRanging_success_uwbs_1_x_relativeUwbInitiationTime(
                uwbSession, aliroStartRangingParams, aliroRangingStartedParams,
                AliroParams.PROTOCOL_VERSION_1_0);

        // Verify the absolute UWB initiation time is not set in the AliroOpenRangingParams.
        AliroOpenRangingParams aliroOpenRangingParams =
                (AliroOpenRangingParams) uwbSession.getParams();
        assertThat(aliroOpenRangingParams.getAbsoluteInitiationTimeUs()).isEqualTo(0);
    }

    private void do_execStartRanging_success_uwbs_1_x_relativeUwbInitiationTime(
            UwbSession uwbSession, Params startRangingParams, Params rangingStartedParams,
            ProtocolVersion protocolVersion) throws Exception {
        // Setup the UWBS to return Fira version as 1.1.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                UWB_DEVICE_INFO_RESPONSE_1_1);

        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mUwbConfigurationManager.getAppConfigurations(
                eq(TEST_SESSION_ID), anyString(), any(), any(), eq(TEST_CHIP_ID),
                eq(protocolVersion)))
                .thenReturn(new Pair<>(UwbUciConstants.STATUS_CODE_OK, rangingStartedParams));

        // Start ranging on the UWB session
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), startRangingParams);
        mTestLooper.dispatchAll();

        // Verify that the ranging started successfully.
        verify(mNativeUwbManager).startRanging(eq(TEST_SESSION_ID), anyString());
        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Verify that queryUwbsTimestampMicros() is not called for FiRa 1.x.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
    }

    @Test
    public void execStartRanging_success_fira_2_0_noUwbInitiationTimeConfigured()
            throws Exception {
        Params firaParams = setupFiraParams(FIRA_VERSION_2_0);
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);

        do_execStartRanging_success_uwbs_2_0_noUwbInitiationTimeConfigured(
                uwbSession,
                /* startRangingParams = */ firaParams,
                /* rangingStartedParams = */ firaParams,
                FIRA_VERSION_2_0);

        // Verify that setAppConfigurations() is not called, as the "params.mInitiationTime" field
        // is not set, and so no re-configuration is needed for a Fira session, before the
        // UWB ranging is started.
        verify(mUwbConfigurationManager, never()).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    @Test
    public void execStartRanging_success_ccc_2_0_noUwbInitiationTimeConfigured()
            throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        Params startRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .build();
        CccRangingStartedParams rangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_2_0_noUwbInitiationTimeConfigured(
                uwbSession, startRangingParams, rangingStartedParams,
                CccParams.PROTOCOL_VERSION_1_0);
    }

    @Test
    public void execStartRanging_success_aliro_2_0_noUwbInitiationTimeConfigured()
            throws Exception {
        UwbSession uwbSession = prepareExistingAliroUwbSession();
        Params startRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .build();
        AliroRangingStartedParams rangingStartedParams = new AliroRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_2_0_noUwbInitiationTimeConfigured(
                uwbSession, startRangingParams, rangingStartedParams,
                AliroParams.PROTOCOL_VERSION_1_0);
    }

    // Test UWB StartRanging on a Fira UCI version 2.0+ device, when the App doesn't configure
    // any UWB initiation time (relative or absolute). In this case, the UwbSessionManager is not
    // expected to query the UWBS timestamp.
    private void do_execStartRanging_success_uwbs_2_0_noUwbInitiationTimeConfigured(
            UwbSession uwbSession, Params startRangingParams, Params rangingStartedParams,
            ProtocolVersion protocolVersion)
            throws Exception {
        // Setup the UWBS to return Fira UCI version as 2.0.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                UWB_DEVICE_INFO_RESPONSE_2_0);

        // Setup for start ranging.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);
        when(mUwbConfigurationManager.getAppConfigurations(
                eq(TEST_SESSION_ID), anyString(), any(), any(), eq(TEST_CHIP_ID),
                eq(protocolVersion)))
                .thenReturn(new Pair<>(UwbUciConstants.STATUS_CODE_OK, rangingStartedParams));

        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), startRangingParams);
        mTestLooper.dispatchAll();

        // Verify that the UWB ranging successfully started.
        verify(mNativeUwbManager).startRanging(eq(TEST_SESSION_ID), anyString());
        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Verify that queryUwbsTimestampMicros() is not called.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
    }

    @Test
    public void execStartRanging_success_fira_2_0_absoluteUwbInitiationTimeIsComputed()
            throws Exception {
        // Setup the UWBS to return Fira UCI version as 2.0.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                UWB_DEVICE_INFO_RESPONSE_2_0);

        // Setup the AbsoluteInitationTime in the FiraOpenSessionParams.
        Params params = setupFiraParams(FIRA_VERSION_2_0);
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder((FiraOpenSessionParams) params)
                        .setInitiationTime(100L)
                        .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);

        // Setup for start ranging.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mUwbServiceCore.queryUwbsTimestampMicros()).thenReturn(UWBS_TIMESTAMP);
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), params);
        mTestLooper.dispatchAll();

        // Verify that queryUwbsTimestampMicros() is called. Currently unable to verify that the
        // FiraOpenSessionParams is changed and the absoluteInitiationTime field set in it, as
        // equals() is not implemented.
        verify(mUwbServiceCore).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    // Test CCC StartRanging on a Fira UCI version 2.0+ device, when the App configures a relative
    // UWB initiation time, and the computation of an absolute UWB initiation time is enabled on
    // the device. In this case, the UwbSessionManager is expected to query the UWBS timestamp.
    @Test
    public void execStartRanging_success_ccc_2_0_absoluteInitiationTimeComputationIsComputed()
            throws Exception {
        // Setup the flag to be "true", so that absolute UWB initiation time computation is
        // enabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = prepareExistingCccUwbSession();
        Params cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setInitiationTimeMs(100)
                .build();
        CccRangingStartedParams cccRangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                cccStartRangingParams, cccRangingStartedParams, CccParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is called. Currently unable to verify that the
        // CccOpenRangingParams is changed and the absoluteInitiationTime field set in it, as
        // equals() is not implemented.
        verify(mUwbServiceCore).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    // Test ALIRO StartRanging on a Fira UCI version 2.0+ device, when the App configures a relative
    // UWB initiation time, and the computation of an absolute UWB initiation time is enabled on
    // the device. In this case, the UwbSessionManager is expected to query the UWBS timestamp.
    @Test
    public void execStartRanging_success_aliro_2_0_absoluteInitiationTimeComputationIsComputed()
            throws Exception {
        // Setup the flag to be "true", so that absolute UWB initiation time computation is
        // enabled for a CCC ranging session.
        //
        // We currently re-use the CCC flag for ALIRO.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = prepareExistingAliroUwbSession();
        Params aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setInitiationTimeMs(100)
                .build();
        AliroRangingStartedParams aliroRangingStartedParams =
                new AliroRangingStartedParams.Builder()
                        .setStartingStsIndex(0)
                        .setUwbTime0(1)
                        .setHopModeKey(0)
                        .setSyncCodeIndex(1)
                        .setRanMultiplier(4)
                        .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                aliroStartRangingParams, aliroRangingStartedParams,
                AliroParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is called. Currently unable to verify that the
        // AliroOpenRangingParams is changed and the absoluteInitiationTime field set in it, as
        // equals() is not implemented.
        verify(mUwbServiceCore).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    // Test CCC StartRanging on a Fira UCI version 2.0+ device, when the App configures a relative
    // UWB initiation time, and the computation of an absolute UWB initiation time is disabled on
    // the device. In this case, the UwbSessionManager is not expected to query the UWBS timestamp.
    @Test
    public void execStartRanging_success_ccc_2_0_absoluteInitiationTimeComputationIsDisabled()
            throws Exception {
        // Setup the flag to "false" (which is also the default value), so that absolute
        // UWB initiation time computation is disabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(false);

        UwbSession uwbSession = prepareExistingCccUwbSession();
        Params cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setInitiationTimeMs(100)
                .build();
        CccRangingStartedParams cccRangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                cccStartRangingParams, cccRangingStartedParams, CccParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is not called.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    // Test ALIRO StartRanging on a Fira UCI version 2.0+ device, when the App configures a relative
    // UWB initiation time, and the computation of an absolute UWB initiation time is disabled on
    // the device. In this case, the UwbSessionManager is not expected to query the UWBS timestamp.
    @Test
    public void execStartRanging_success_aliro_2_0_absoluteInitiationTimeComputationIsDisabled()
            throws Exception {
        // Setup the flag to "false" (which is also the default value), so that absolute
        // UWB initiation time computation is disabled for a CCC ranging session.
        //
        // We currently re-use the CCC flag for ALIRO.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(false);

        UwbSession uwbSession = prepareExistingAliroUwbSession();
        Params aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setInitiationTimeMs(100)
                .build();
        AliroRangingStartedParams aliroRangingStartedParams =
                new AliroRangingStartedParams.Builder()
                        .setStartingStsIndex(0)
                        .setUwbTime0(1)
                        .setHopModeKey(0)
                        .setSyncCodeIndex(1)
                        .setRanMultiplier(4)
                        .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                aliroStartRangingParams, aliroRangingStartedParams,
                AliroParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is not called.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    // Test FiRa StartRanging on a Fira UCI version 2.0+ device, when the App configures an absolute
    // UWB initiation time. In this case, the UwbSessionManager is not expected to query the UWBS
    // timestamp.
    @Test
    public void execStartRanging_success_fira_2_0_absoluteUwbInitiationTimeUserConfigured()
            throws Exception {
        Params params = setupFiraParams(FIRA_VERSION_2_0);

        // Setup the AbsoluteInitationTime in the FiraOpenSessionParams.
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder((FiraOpenSessionParams) params)
                .setAbsoluteInitiationTime(1000000L)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);

        do_execStartRanging_success_uwbs_2_0(uwbSession, params, null, FIRA_VERSION_2_0);

        // Verify that queryUwbsTimestampMicros() isn't called (which is the expected behavior as
        // the firaParams has the absolute_initiation_time field set).
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
    }

    // Test CCC StartRanging on a Fira UCI version 2.0+ device, when the App configures an absolute
    // UWB initiation time. In this case, the UwbSessionManager is not expected to query the UWBS
    // timestamp.
    @Test
    public void execStartRanging_success_ccc_2_0_absoluteInitiationTimeUserConfigured()
            throws Exception {
        // Setup the flag to be "true", so that absolute UWB initiation time computation is
        // enabled for a CCC ranging session.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = prepareExistingCccUwbSession();
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setAbsoluteInitiationTimeUs(8000)
                .build();
        CccRangingStartedParams cccRangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                cccStartRangingParams, cccRangingStartedParams, CccParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is not called when it is configured with
        // CccStartRangingParams
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    // Test ALIRO StartRanging on a Fira UCI version 2.0+ device, when the App configures an
    // absolute UWB initiation time. In this case, the UwbSessionManager is not expected to query
    // the UWBS timestamp.
    @Test
    public void execStartRanging_success_aliro_2_0_absoluteInitiationTimeUserConfigured()
            throws Exception {
        // Setup the flag to be "true", so that absolute UWB initiation time computation is
        // enabled for a ALIRO ranging session.
        //
        // We currently use the CCC flag for ALIRO sessions also.
        when(mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled()).thenReturn(true);

        UwbSession uwbSession = prepareExistingAliroUwbSession();
        AliroStartRangingParams aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(4)
                .setAbsoluteInitiationTimeUs(8000)
                .build();
        AliroRangingStartedParams aliroRangingStartedParams =
                new AliroRangingStartedParams.Builder()
                        .setStartingStsIndex(0)
                        .setUwbTime0(1)
                        .setHopModeKey(0)
                        .setSyncCodeIndex(1)
                        .setRanMultiplier(4)
                        .build();

        do_execStartRanging_success_uwbs_2_0(uwbSession,
                aliroStartRangingParams, aliroRangingStartedParams,
                AliroParams.PROTOCOL_VERSION_1_0);

        // Verify that queryUwbsTimestampMicros() is not called when it is configured with
        // AliroStartRangingParams.
        verify(mUwbServiceCore, never()).queryUwbsTimestampMicros();
        verify(mUwbConfigurationManager).setAppConfigurations(
                anyInt(), any(), any(), eq(FIRA_VERSION_2_0));
    }

    private void do_execStartRanging_success_uwbs_2_0(UwbSession uwbSession,
            Params cccStartRangingParams, Params rangingStartedParams,
            ProtocolVersion protocolVersion) throws Exception {
        // Setup the UWBS to return Fira UCI version as 2.0.
        when(mUwbServiceCore.getCachedDeviceInfoResponse(TEST_CHIP_ID)).thenReturn(
                UWB_DEVICE_INFO_RESPONSE_2_0);

        // Setup for start ranging.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mUwbServiceCore.queryUwbsTimestampMicros()).thenReturn(UWBS_TIMESTAMP);
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);
        when(mUwbConfigurationManager.getAppConfigurations(
                eq(TEST_SESSION_ID), anyString(), any(), any(), eq(TEST_CHIP_ID),
                eq(protocolVersion)))
                .thenReturn(new Pair<>(UwbUciConstants.STATUS_CODE_OK, rangingStartedParams));

        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), cccStartRangingParams);
        mTestLooper.dispatchAll();

        // Verify that the UWB ranging session was successfully started.
        verify(mNativeUwbManager).startRanging(eq(TEST_SESSION_ID), anyString());
        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void execStartRanging_onRangeDataNotification() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
    }

    @Test
    public void execStartRanging_twoWay_onRangeDataNotificationContinuousErrors() throws Exception {
        startRanging_onRangeDataNotificationContinuousErrors(RANGING_MEASUREMENT_TYPE_TWO_WAY);
    }

    @Test
    public void execStartRanging_owrAoa_onRangeDataNotificationContinuousErrors() throws Exception {
        startRanging_onRangeDataNotificationContinuousErrors(RANGING_MEASUREMENT_TYPE_OWR_AOA);
    }

    private void startRanging_onRangeDataNotificationContinuousErrors(
            int rangingMeasurementType) throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                rangingMeasurementType, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).setExact(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send one more error and ensure that the timer is not cancelled.
        uwbRangingData = UwbTestUtils.generateRangingData(
                rangingMeasurementType, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager, never()).cancel(any(AlarmManager.OnAlarmListener.class));

        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        // Now fire the timer callback.
        alarmListenerCaptor.getValue().onAlarm();

        // Expect session stop.
        mTestLooper.dispatchNext();
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.SYSTEM_POLICY), any());
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void
            execStartRanging_onRangeDataNotificationContinuousErrors_WhenErrorStreakTimerDisabled()
            throws Exception {
        when(mDeviceConfigFacade.isRangingErrorStreakTimerEnabled()).thenReturn(false);

        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        // Ensure error streak timer is not started.
        verify(mAlarmManager, never()).setExact(
                anyInt(), anyLong(), anyString(), any(), any());
    }

    @Test
    public void execStartRanging_onRangeDataNotificationErrorFollowedBySuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).setExact(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send success and ensure that the timer is cancelled.
        uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void session_receivedDataInfo() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to have multiple data packets (being received) for multiple remote
        // devices. This includes some duplicate packets (same sequence number from same remote
        // device), which should be ignored.
        UwbSessionManager.ReceivedDataInfo deviceOnePacketOne = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM);
        UwbSessionManager.ReceivedDataInfo deviceOnePacketTwo = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM_1);
        UwbSessionManager.ReceivedDataInfo deviceTwoPacketOne = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_2_LONG, DATA_SEQUENCE_NUM);
        UwbSessionManager.ReceivedDataInfo deviceTwoPacketTwo = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_2_LONG, DATA_SEQUENCE_NUM_1);
        when(mDeviceConfigFacade.getRxDataMaxPacketsToStore())
                .thenReturn(MAX_RX_DATA_PACKETS_TO_STORE);

        uwbSession.addReceivedDataInfo(deviceOnePacketOne);
        uwbSession.addReceivedDataInfo(deviceOnePacketTwo);
        uwbSession.addReceivedDataInfo(deviceOnePacketOne);

        uwbSession.addReceivedDataInfo(deviceTwoPacketOne);
        uwbSession.addReceivedDataInfo(deviceTwoPacketTwo);
        uwbSession.addReceivedDataInfo(deviceTwoPacketOne);

        // Verify that the first call to getAllReceivedDataInfo() for a device returns all it's
        // received packets, and the second call receives an empty list.
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of(deviceOnePacketOne, deviceOnePacketTwo));
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of());
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_2_LONG)).isEqualTo(
                List.of(deviceTwoPacketOne, deviceTwoPacketTwo));
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_2_LONG)).isEqualTo(
                List.of());
    }

    @Test
    public void session_receivedDataInfo_maxCapacity() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        UwbSessionManager.ReceivedDataInfo rxPacketOne = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM + 1);
        UwbSessionManager.ReceivedDataInfo rxPacketTwo = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM + 2);
        UwbSessionManager.ReceivedDataInfo rxPacketThree = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM + 3);
        UwbSessionManager.ReceivedDataInfo rxPacketFour = buildReceivedDataInfo(
                PEER_EXTENDED_MAC_ADDRESS_LONG, DATA_SEQUENCE_NUM + 4);

        // Setup the UwbSession to have multiple data packets (being received) from one remote
        // device, such that it's at the capacity. We send the packets out-of-order, but do want
        // to extract them in order.
        when(mDeviceConfigFacade.getRxDataMaxPacketsToStore()).thenReturn(3);

        // Case 1 - Setup the UwbSession to have multiple Rx data packets (beyond capacity), such
        // that the last packet is the smallest one and should be dropped.
        uwbSession.addReceivedDataInfo(rxPacketTwo);
        uwbSession.addReceivedDataInfo(rxPacketFour);
        uwbSession.addReceivedDataInfo(rxPacketThree);
        uwbSession.addReceivedDataInfo(rxPacketOne);

        // Verify that the first call to getAllReceivedDataInfo() returns the max capacity number of
        // packets (in-order), and the second call receives an empty list.
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of(rxPacketTwo, rxPacketThree, rxPacketFour));
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of());

        // Case 2 - Setup the UwbSession to have multiple Rx data packets (beyond capacity), such
        // that one of the stored packets is the smallest one and should be dropped.
        uwbSession.addReceivedDataInfo(rxPacketOne);
        uwbSession.addReceivedDataInfo(rxPacketTwo);
        uwbSession.addReceivedDataInfo(rxPacketFour);
        uwbSession.addReceivedDataInfo(rxPacketThree);

        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of(rxPacketTwo, rxPacketThree, rxPacketFour));
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of());

        // Case 3 - Setup the UwbSession to have multiple Rx data packets (beyond capacity), such
        // that one of the stored packets is repeated. The repeated packet should be ignored.
        uwbSession.addReceivedDataInfo(rxPacketTwo);
        uwbSession.addReceivedDataInfo(rxPacketFour);
        uwbSession.addReceivedDataInfo(rxPacketThree);
        uwbSession.addReceivedDataInfo(rxPacketFour);

        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of(rxPacketTwo, rxPacketThree, rxPacketFour));
        assertThat(uwbSession.getAllReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS_LONG)).isEqualTo(
                List.of());
    }

    @Test
    public void execStartCccRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(8)
                .build();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), cccStartRangingParams);
        mTestLooper.dispatchAll();

        // Verify the update logic.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(8);
    }

    @Test
    public void execStartAliroRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingAliroUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        AliroStartRangingParams aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(8)
                .build();
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), aliroStartRangingParams);
        mTestLooper.dispatchAll();

        // Verify the update logic.
        AliroOpenRangingParams aliroOpenRangingParams =
                (AliroOpenRangingParams) uwbSession.getParams();
        assertThat(aliroOpenRangingParams.getRanMultiplier()).isEqualTo(8);
    }

    @Test
    public void execStartCccRangingWithNoStartParams_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), null /* params */);
        mTestLooper.dispatchAll();

        // Verify that RAN multiplier from open is used.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(4);
    }

    @Test
    public void execStartAliroRangingWithNoStartParams_success() throws Exception {
        UwbSession uwbSession = prepareExistingAliroUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), null /* params */);
        mTestLooper.dispatchAll();

        // Verify that RAN multiplier from open is used.
        AliroOpenRangingParams aliroOpenRangingParams =
                (AliroOpenRangingParams) uwbSession.getParams();
        assertThat(aliroOpenRangingParams.getRanMultiplier()).isEqualTo(4);
    }

    @Test
    public void execStartRanging_executionException() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_nativeStartRangingFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_wrongSessionState() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        // In this scenario, there is no SESSION_STATUS_NTF received, so send an undefined
        // reasonCode (-1).
        verify(mUwbSessionNotificationManager).onRangingStartFailedWithUciReasonCode(
                eq(uwbSession), eq(-1));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_sessionStateIdle_reasonSessionKeyNotFound() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // set up for start ranging - it fails and session_state stays at IDLE.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        // UWBS sends the SESSION_STATS_NTF with ReasonCode as REASON_ERROR_SESSION_KEY_NOT_FOUND.
        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_ERROR_SESSION_KEY_NOT_FOUND);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailedWithUciReasonCode(
                eq(uwbSession), eq(UwbUciConstants.REASON_ERROR_SESSION_KEY_NOT_FOUND));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST));
    }

    private void doTest_sendData_success_validUwbSession(byte[] macAddress, int dataTransferStatus)
            throws Exception {
        UwbAddress uwbAddress = UwbAddress.fromBytes(macAddress);
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID), eq(macAddress),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), uwbAddress, PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID), eq(macAddress),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));

        // A DataTransferStatusNtf is received indicating success.
        mUwbSessionManager.onDataSendStatus(
                uwbSession.getSessionId(), dataTransferStatus, DATA_SEQUENCE_NUM,
                DATA_TRANSMISSION_COUNT);
        verify(mUwbSessionNotificationManager).onDataSent(
                eq(uwbSession), eq(uwbAddress), eq(PERSISTABLE_BUNDLE));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // extended MacAddress format). The DataTransferStatus notification returns a success status
    // code (STATUS_CODE_DATA_TRANSFER_REPETITION_OK).
    @Test
    public void sendData_success_validUwbSession_extendedMacAddress_statusRepetitionOk()
            throws Exception {
        doTest_sendData_success_validUwbSession(
                PEER_EXTENDED_MAC_ADDRESS, STATUS_CODE_DATA_TRANSFER_REPETITION_OK);
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // extended MacAddress format). The DataTransferStatus notification returns a success status
    // code (STATUS_CODE_OK).
    @Test
    public void sendData_success_validUwbSession_extendedMacAddress_statusOk()
            throws Exception {
        doTest_sendData_success_validUwbSession(PEER_EXTENDED_MAC_ADDRESS, STATUS_CODE_OK);
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // short MacAddress format). The DataTransferStatus notification returns a success status
    // code (STATUS_CODE_DATA_TRANSFER_REPETITION_OK).
    @Test
    public void sendData_success_validUwbSession_shortMacAddress_statusRepetitionOk()
            throws Exception {
        doTest_sendData_success_validUwbSession(
                PEER_EXTENDED_SHORT_MAC_ADDRESS, STATUS_CODE_DATA_TRANSFER_REPETITION_OK);
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // short MacAddress format). The DataTransferStatus notification returns a success status
    // code (STATUS_CODE_OK).
    @Test
    public void sendData_success_validUwbSession_shortMacAddress_statusOk() throws Exception {
        doTest_sendData_success_validUwbSession(PEER_EXTENDED_SHORT_MAC_ADDRESS, STATUS_CODE_OK);
    }

    @Test
    public void sendData_missingSessionHandle() throws Exception {
        // Setup a UwbSession to start ranging (and move it to active state).
        prepareExistingUwbSessionActive();

        // Send a Data packet with null SessionHandle, it should result in an error.
        mUwbSessionManager.sendData(
                null /* sessionHandle */, PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE,
                DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(null), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_invalidUwbSessionHandle() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state), and a
        // different sessionHandle that doesn't map to the uwbSession.
        prepareExistingUwbSessionActive();
        SessionHandle sessionHandle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);

        // Send a Data packet on the non-active UWB Session.
        mUwbSessionManager.sendData(
                sessionHandle, PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(null), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_invalidUwbSessionState() throws Exception {
        // Setup a uwbSession and don't start ranging, so it remains in IDLE state.
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, null);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_missingDataPayload() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, null);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_INVALID_PARAM), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_missingRemoteDevice() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), null, PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(null),
                eq(UwbUciConstants.STATUS_CODE_INVALID_PARAM), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_dataSendFailure() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();

        // Attempt to send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void onDataSendStatus_sessionNotFound() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();
        clearInvocations(mUwbSessionNotificationManager);

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // We receive a DataTransferStatusNtf with a sessionId for a different UwbSession, so it
        // should be dropped (no onDataSend()/onDataSendFailure() notifications sent).
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID_2, STATUS_CODE_OK, DATA_SEQUENCE_NUM,
                DATA_TRANSMISSION_COUNT);
        verifyNoMoreInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onDataSendStatus_dataSndPacketNotFound() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();
        clearInvocations(mUwbSessionNotificationManager);

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // We receive a DataTransferStatusNtf with an incorrect UCI sequence number (for which a
        // packet was never sent), so it should be dropped (no onDataSend()/onDataSendFailure()
        // notifications sent).
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID, STATUS_CODE_OK, DATA_SEQUENCE_NUM_1,
                DATA_TRANSMISSION_COUNT);
        verifyNoMoreInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onDataSendStatus_errorStatus() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();
        clearInvocations(mUwbSessionNotificationManager);

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // We receive a DataTransferStatusNtf with an error status code.
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID,
                STATUS_CODE_DATA_TRANSFER_ERROR_DATA_TRANSFER, DATA_SEQUENCE_NUM,
                DATA_TRANSMISSION_COUNT);
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(STATUS_CODE_DATA_TRANSFER_ERROR_DATA_TRANSFER), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void onDataSendStatus_neverReceived() throws Exception {
        // Setup a uwbSession UwbSession to start ranging (and move it to active state).
        UwbSession uwbSession = prepareExistingUwbSessionActive();
        clearInvocations(mUwbSessionNotificationManager);

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(DATA_SEQUENCE_NUM), eq(DATA_PAYLOAD), eq(TEST_CHIP_ID));
        verify(mUwbMetrics).logDataTx(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // We never receive a DataTransferStatusNtf, so no onDataSend()/onDataSendFailure()
        // notifications are sent.
        verifyNoMoreInteractions(mUwbSessionNotificationManager);
        assertNotNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));

        // Eventually Session DeInit is called, and the stored SendDataInfo(s) should be deleted.
        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();
        assertNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // short MacAddress format). Verifies the deletion of stored data depending upon the status
    // code (STATUS_CODE_DATA_TRANSFER_REPETITION_OK) when data repetition count = 0.
    @Test
    public void sendData_withZeroDataRepetitionCount() throws Exception {
        FiraOpenSessionParams params = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UWB_DEST_ADDRESS))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING_AND_IN_BAND_DATA)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setRangingIntervalMs(TEST_RANGING_INTERVAL_MS)
                .setDataRepetitionCount(0)
                .build();
        UwbSession uwbSession = prepareExistingUwbSessionActive(params);
        assertThat(uwbSession.getDataRepetitionCount()).isEqualTo(0);

        // Send the Data packet and simulate the onDataSendStatus() callback being received.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_SHORT_UWB_ADDRESS, PERSISTABLE_BUNDLE,
                DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        // Since txCount is 0, stored data should not be deleted
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID,
                STATUS_CODE_DATA_TRANSFER_REPETITION_OK, DATA_SEQUENCE_NUM, 0);
        assertNotNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));

        // Since txCount = DataRepetitionCount, stored data should removed
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID,
                STATUS_CODE_DATA_TRANSFER_OK, DATA_SEQUENCE_NUM, 1);
        assertNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));
    }

    // Test case for scenario when a Data packet is successfully sent to a remote device (in
    // short MacAddress format). Verifies the deletion of stored data depending upon the status
    // code (STATUS_CODE_DATA_TRANSFER_REPETITION_OK) and data repetition count of the
    // DataTransferStatus notification.
    @Test
    public void sendData_withNonZeroDataRepetitionCount() throws Exception {
        FiraOpenSessionParams params = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UWB_DEST_ADDRESS))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING_AND_IN_BAND_DATA)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setRangingIntervalMs(TEST_RANGING_INTERVAL_MS)
                .setDataRepetitionCount(DATA_TRANSMISSION_COUNT_3)
                .build();
        UwbSession uwbSession = prepareExistingUwbSessionActive(params);
        assertThat(uwbSession.getDataRepetitionCount()).isEqualTo(DATA_TRANSMISSION_COUNT_3);

        // Send the Data packet and simulate the onDataSendStatus() callback being received.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_SHORT_UWB_ADDRESS, PERSISTABLE_BUNDLE,
                DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID,
                STATUS_CODE_DATA_TRANSFER_REPETITION_OK, DATA_SEQUENCE_NUM,
                DATA_TRANSMISSION_COUNT);

        // Since txCount < DataRepetitionCount, stored data should not be deleted
        assertNotNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));

        // Now simulate the onDataSendStatus() callback being received with a higher TxCount value.
        mUwbSessionManager.onDataSendStatus(TEST_SESSION_ID,
                STATUS_CODE_DATA_TRANSFER_OK, DATA_SEQUENCE_NUM,
                DATA_TRANSMISSION_COUNT_3 + 1);

        // Since txCount = DataRepetitionCount, stored data should removed
        assertNull(uwbSession.getSendDataInfo(DATA_SEQUENCE_NUM));
    }

    @Test
    public void stopRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopped(
                eq(uwbSession),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void stopRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void execStopRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbInjector).runTaskOnSingleThreadExecutor(
                any(), eq(IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS));
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.LOCAL_API), any());
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStopRanging_exception() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager, never()).onRangingStopped(any(), anyInt());
    }

    @Test
    public void execStopRanging_nativeFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics, never()).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void testFiraSessionStoppedDuetoInbandSignal() throws Exception {
        //Assuming that when session is in active state,
        //in-band signal is received and session moved IDLE state
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_REJECTED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                eq(RangingChangeReason.SYSTEM_POLICY), any());
    }

    @Test
    public void testCCCSessionStoppedDuetoInbandSignal() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        CccRangingStartedParams cccRangingStartedParams = new CccRangingStartedParams.Builder()
                .setStartingStsIndex(0)
                .setUwbTime0(1)
                .setHopModeKey(0)
                .setSyncCodeIndex(1)
                .setRanMultiplier(4)
                .build();
        do_sessionStoppedDuetoInbandSignal(
                uwbSession, cccRangingStartedParams, CccParams.PROTOCOL_VERSION_1_0);
    }

    @Test
    public void testAliroSessionStoppedDuetoInbandSignal() throws Exception {
        UwbSession uwbSession = prepareExistingAliroUwbSession();
        AliroRangingStartedParams aliroRangingStartedParams =
                new AliroRangingStartedParams.Builder()
                        .setStartingStsIndex(0)
                        .setUwbTime0(1)
                        .setHopModeKey(0)
                        .setSyncCodeIndex(1)
                        .setRanMultiplier(4)
                        .build();
        do_sessionStoppedDuetoInbandSignal(
                uwbSession, aliroRangingStartedParams, AliroParams.PROTOCOL_VERSION_1_0);
    }

    private void do_sessionStoppedDuetoInbandSignal(UwbSession uwbSession,
             Params rangingStartedParams, ProtocolVersion protocolVersion) throws Exception {
        // Assuming that when session is in active state, an in-band signal is received and the
        // session is moved to IDLE state.
        when(mUwbConfigurationManager.getAppConfigurations(
                eq(TEST_SESSION_ID), anyString(), any(), any(), eq(TEST_CHIP_ID),
                eq(protocolVersion)))
                .thenReturn(new Pair<>(UwbUciConstants.STATUS_CODE_OK, rangingStartedParams));
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_REJECTED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.SYSTEM_POLICY), any());
    }

    @Test
    public void reconfigure_notExistingSession() {
        int status = mUwbSessionManager.reconfigure(mock(SessionHandle.class), mock(Params.class));

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private FiraRangingReconfigureParams buildReconfigureParams() {
        return buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD);
    }

    private FiraRangingReconfigureParams buildReconfigureParams(int action) {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(action)
                        .setSubSessionIdList(new int[] { 2 })
                        .build();

        return spy(reconfigureParams);
    }

    private FiraRangingReconfigureParams buildReconfigureParamsV2() {
        return buildReconfigureParamsV2(
                FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE);
    }

    private FiraRangingReconfigureParams buildReconfigureParamsV2(int action) {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(action)
                        .setSubSessionIdList(new int[] { 2 })
                        .setSubSessionKeyList(new byte[] {0, 0, 0, 0, 1, 1, 1, 1,
                                2, 2, 2, 2, 3, 3, 3, 3})
                        .build();

        return spy(reconfigureParams);
    }

    @Test
    public void reconfigure_existingSession() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);

        int status = mUwbSessionManager.reconfigure(
                uwbSession.getSessionHandle(), buildReconfigureParamsV2());

        assertThat(status).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(4); // SESSION_RECONFIGURE_RANGING
    }

    @Test
    public void execReconfigureAddControlee_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS_2});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the original address is still there.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        // Make sure this new address was added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isTrue();

        byte[] dstAddress =
                getComputedMacAddress(reconfigureParams.getAddressList()[0].toBytes());
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                dstAddress, reconfigureParams.getSubSessionIdList(), null,
                uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureAddControlee_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD);

        int status = mUwbSessionManager
                .reconfigure(uwbSession.getSessionHandle(), reconfigureParams);

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_REJECTED);
    }

    @Test
    public void execReconfigureRemoveControleeV1_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE);
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        // Make sure the address exists in the first place. This should have been set up by
        //  prepareExistingUwbSession
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the address was removed.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isFalse();

        byte[] dstAddress = getComputedMacAddress(reconfigureParams.getAddressList()[0].toBytes());
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                dstAddress, reconfigureParams.getSubSessionIdList(), null,
                uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeRemoved(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureRemoveControlee_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2(FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE);

        int status = mUwbSessionManager
                .reconfigure(uwbSession.getSessionHandle(), reconfigureParams);

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_REJECTED);
    }

    @Test
    public void execReconfigureAddControleeV2_success() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS_2});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the original address is still there.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        // Make sure this new address was added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isTrue();

        byte[] dstAddress = getComputedMacAddress(reconfigureParams.getAddressList()[0].toBytes());
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                dstAddress, reconfigureParams.getSubSessionIdList(),
                reconfigureParams.getSubSessionKeyList(), uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureAddControlee_fetchKeysFromSE_V2_success() throws Exception {
        // When both sessionKey and subSessionKey are not provided from APP,
        // it will be fetched from SE
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams(FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE);
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS_2});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the original address is still there.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        // Make sure this new address was added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isTrue();

        byte[] dstAddress = getComputedMacAddress(reconfigureParams.getAddressList()[0].toBytes());
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                dstAddress, reconfigureParams.getSubSessionIdList(),
                reconfigureParams.getSubSessionKeyList(), uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureAddControlee_onlyWithSessionKey_failed() throws Exception {
        //If sessionKey is only provided from app, reconfigure will be rejected from sessionManager
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams(FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE);

        int status = mUwbSessionManager
                .reconfigure(uwbSession.getSessionHandle(), reconfigureParams);

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_REJECTED);
    }

    @Test
    public void execReconfigureAddControlee_onlyWithSubSessionKey_failed() throws Exception {
        //If subSessionKeyList is only provided from app,
        //reconfigure will be rejected from sessionManager
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();

        int status = mUwbSessionManager
                .reconfigure(uwbSession.getSessionHandle(), reconfigureParams);

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_REJECTED);
    }

    @Test
    public void execReconfigure_nativeUpdateFailed() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateMixedSuccess() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(2);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses()).thenReturn(
                new UwbAddress[] { UWB_DEST_ADDRESS_2, UWB_DEST_ADDRESS_3 });
        // One fail, one success
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED, UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Fail callback for the first one.
        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        // Success callback for the second.
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));

        // Make sure the failed address was not added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isFalse();

        // Overall reconfigure fail.
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateFailed() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigureBlockStriding_success_stop() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .build();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigured(uwbSession);

        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbInjector).runTaskOnSingleThreadExecutor(
                any(), eq(TEST_RANGING_INTERVAL_MS * 4 * 11));
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.LOCAL_API), any());
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execReconfigure_setAppConfigurationsFailed() throws Exception {
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder(
                    (FiraOpenSessionParams) setupFiraParams(FIRA_VERSION_1_1))
                .setSessionKey(new byte[]{0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78, 0x5,
                    0x78, 0x5, 0x78, 0x5, 0x78, 0x5, 0x78})
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY)
                .build();
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void testSetDataTransferPhaseConfig() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        byte dtpcmRepetition = 0;
        byte dataTransferControl = 0;
        byte dtpmlSize = 2;
        byte[] macAddressBytes = new byte[] { 0x22, 0x11, 0x44, 0x33 };
        byte[] slotBitmapBytes = new byte[] { 0x10, 0x20 };

        List<FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList>
                    firaDataTransferPhaseManagementList =  new ArrayList<>();

        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[]{0x22, 0x11});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {(byte) 0x10}));

        uwbAddress = UwbAddress.fromBytes(new byte[] {0x44, 0x33});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {(byte) 0x20}));

        FiraDataTransferPhaseConfig firaDataTransferPhaseConfig =
                new FiraDataTransferPhaseConfig.Builder()
                   .setDtpcmRepetition((byte) dtpcmRepetition)
                   .setMacAddressMode((byte) 0)
                   .setSlotBitmapSize((byte) 1)
                   .setDataTransferPhaseManagementList(firaDataTransferPhaseManagementList)
                   .build();

        when(mNativeUwbManager.setDataTransferPhaseConfig(eq(TEST_SESSION_ID),
                eq(dtpcmRepetition), eq(dataTransferControl), eq(dtpmlSize), eq(macAddressBytes),
                eq(slotBitmapBytes), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.setDataTransferPhaseConfig(
                uwbSession.getSessionHandle(), firaDataTransferPhaseConfig.toBundle());
        mTestLooper.dispatchNext();

        assertThat(mNativeUwbManager.setDataTransferPhaseConfig(TEST_SESSION_ID,
                dtpcmRepetition, dataTransferControl, dtpmlSize, macAddressBytes,
                slotBitmapBytes, TEST_CHIP_ID)).isEqualTo(UwbUciConstants.STATUS_CODE_OK);
    }

    @Test
    public void testSetDataTransferPhaseConfigNonZeroslotBitMap() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        byte dtpcmRepetition = 0;
        byte dataTransferControl = 4;
        byte dtpmlSize = 2;
        byte[] macAddressBytes = new byte[] { 0x22, 0x11, 0x44, 0x33 };
        byte[] slotBitmapBytes = new byte[]
                { 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, (byte) 0x80 };

        List<FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList>
                    firaDataTransferPhaseManagementList =  new ArrayList<>();

        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[]{0x22, 0x11});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {0x10, 0x20, 0x30, 0x40}));

        uwbAddress = UwbAddress.fromBytes(new byte[] {0x44, 0x33});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {0x50, 0x60, 0x70, (byte) 0x80}));

        FiraDataTransferPhaseConfig firaDataTransferPhaseConfig =
                new FiraDataTransferPhaseConfig.Builder()
                   .setDtpcmRepetition((byte) dtpcmRepetition)
                   .setMacAddressMode((byte) 0)
                   .setSlotBitmapSize((byte) 4)
                   .setDataTransferPhaseManagementList(firaDataTransferPhaseManagementList)
                   .build();

        when(mNativeUwbManager.setDataTransferPhaseConfig(eq(TEST_SESSION_ID),
                eq(dtpcmRepetition), eq(dataTransferControl), eq(dtpmlSize), eq(macAddressBytes),
                eq(slotBitmapBytes), eq(TEST_CHIP_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.setDataTransferPhaseConfig(
                uwbSession.getSessionHandle(), firaDataTransferPhaseConfig.toBundle());
        mTestLooper.dispatchNext();

        assertThat(mNativeUwbManager.setDataTransferPhaseConfig(TEST_SESSION_ID,
                dtpcmRepetition, dataTransferControl, dtpmlSize, macAddressBytes,
                slotBitmapBytes, TEST_CHIP_ID)).isEqualTo(UwbUciConstants.STATUS_CODE_OK);
    }

    @Test
    public void testSetDataTransferPhaseConfigFailedDueToInvalidSessionType() throws Exception {
        byte dtpcmRepetition = 0;
        byte dataTransferControl = 0;
        byte dtpmlSize = 2;
        byte[] macAddressBytes = new byte[] { 0x22, 0x11, 0x44, 0x33 };
        byte[] slotBitmapBytes = new byte[] { 0x10, 0x20 };
        FiraOpenSessionParams params = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UWB_DEST_ADDRESS))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                /* Invalid session type for data transfer phase config */
                .setSessionType(FiraParams.SESSION_TYPE_RANGING)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setRangingIntervalMs(TEST_RANGING_INTERVAL_MS)
                .setDataRepetitionCount(0)
                .build();
        UwbSession uwbSession = prepareExistingUwbSessionActive(params);

        List<FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList>
                    firaDataTransferPhaseManagementList =  new ArrayList<>();
        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[]{0x22, 0x11});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {(byte) 0x10}));
        uwbAddress = UwbAddress.fromBytes(new byte[] {0x44, 0x33});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {(byte) 0x20, 0x30})); //Invalid slot bit map
        FiraDataTransferPhaseConfig firaDataTransferPhaseConfig =
                new FiraDataTransferPhaseConfig.Builder()
                   .setDtpcmRepetition((byte) dtpcmRepetition)
                   .setMacAddressMode((byte) 0)
                   .setSlotBitmapSize((byte) 1)
                   .setDataTransferPhaseManagementList(firaDataTransferPhaseManagementList)
                   .build();

        mUwbSessionManager.setDataTransferPhaseConfig(
                uwbSession.getSessionHandle(), firaDataTransferPhaseConfig.toBundle());
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).setDataTransferPhaseConfig(TEST_SESSION_ID,
                dtpcmRepetition, dataTransferControl, dtpmlSize, macAddressBytes,
                slotBitmapBytes, TEST_CHIP_ID);
    }


    @Test
    public void testSetDataTransferPhaseConfigFailedDueToInvalidList() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        byte dtpcmRepetition = 0;
        byte dataTransferControl = 0;
        byte dtpmlSize = 2;
        byte[] macAddressBytes = new byte[] { 0x22, 0x11, 0x44, 0x33 };
        byte[] slotBitmapBytes = new byte[] { 0x10, 0x20 };

        List<FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList>
                    firaDataTransferPhaseManagementList =  new ArrayList<>();

        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[]{0x22, 0x11});
        firaDataTransferPhaseManagementList.add(
                new FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList(
                uwbAddress, new byte[] {(byte) 0x10}));

        //Size of dtpml-Size is 2 but only one set of configs are provided
        FiraDataTransferPhaseConfig firaDataTransferPhaseConfig =
                new FiraDataTransferPhaseConfig.Builder()
                   .setDtpcmRepetition((byte) dtpcmRepetition)
                   .setMacAddressMode((byte) 0)
                   .setSlotBitmapSize((byte) 1)
                   .setDataTransferPhaseManagementList(firaDataTransferPhaseManagementList)
                   .build();

        mUwbSessionManager.setDataTransferPhaseConfig(
                uwbSession.getSessionHandle(), firaDataTransferPhaseConfig.toBundle());
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).setDataTransferPhaseConfig(TEST_SESSION_ID,
                dtpcmRepetition, dataTransferControl, dtpmlSize, macAddressBytes,
                slotBitmapBytes, TEST_CHIP_ID);
    }


    @Test
    public void testQueryDataSize() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        when(mNativeUwbManager.queryMaxDataSizeBytes(
                eq(uwbSession.getSessionId()), eq(TEST_CHIP_ID)))
                .thenReturn(MAX_DATA_SIZE);
        assertThat(mUwbSessionManager.queryMaxDataSizeBytes(uwbSession.getSessionHandle()))
                .isEqualTo(MAX_DATA_SIZE);
    }

    @Test
    public void testQueryDataSize_whenUwbSessionDoesNotExist() throws Exception {
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        assertThrows(IllegalStateException.class,
                () -> mUwbSessionManager.queryMaxDataSizeBytes(mockSessionHandle));
    }

    @Test
    public void testReferenceTimeBase() throws Exception {
        Params refParams = setupFiraParams(new FiraProtocolVersion(2, 0));
        FiraOpenSessionParams refFiraParams = new
                FiraOpenSessionParams.Builder((FiraOpenSessionParams) refParams)
                        .setSessionId(TEST_SESSION_ID)
                        .build();
        UwbSession refUwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE, refFiraParams);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, refUwbSession.getSessionHandle(),
                TEST_SESSION_ID, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                refUwbSession.getParams(), refUwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        when(mNativeUwbManager.getSessionToken(eq(TEST_SESSION_ID), anyString()))
                .thenReturn(REFERENCE_SESSION_HANDLE);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(refUwbSession).getSessionState();
        mTestLooper.dispatchAll();

        Params params = setupFiraParams(new FiraProtocolVersion(2, 0));
        FiraOpenSessionParams firaParams = new
                FiraOpenSessionParams.Builder((FiraOpenSessionParams) params)
                        .setSessionId(TEST_SESSION_ID_2)
                        .setSessionTimeBase(1, TEST_SESSION_ID, 200)
                        .build();
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE, firaParams);

        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID_2, TEST_SESSION_TYPE, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(uwbSession).updateFiraParamsForSessionTimeBase(REFERENCE_SESSION_HANDLE);
    }


    @Test
    public void testSetHybridSessionConfiguration() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraHybridSessionConfig.Builder mockFiraBuilder =
                mock(FiraHybridSessionConfig.Builder.class);

        SessionHandle sessionHandle = mock(SessionHandle.class);
        SessionHandle sessionHandle1 = mock(SessionHandle.class);
        SessionHandle sessionHandle2 = mock(SessionHandle.class);
        byte[] updateTime = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        int noOfPhases = 2;
        short startSlotIndex1 = 0x01, endSlotIndex1 = 0x34;
        short startSlotIndex2 = 0x37, endSlotIndex2 = 0x64;
        FiraHybridSessionConfig params = new FiraHybridSessionConfig.Builder()
                .setNumberOfPhases(noOfPhases)
                .setUpdateTime(updateTime)
                .addPhaseList(new FiraHybridSessionConfig.FiraHybridSessionPhaseList(
                        sessionHandle1.getId(), startSlotIndex1, endSlotIndex1))
                .addPhaseList(new FiraHybridSessionConfig.FiraHybridSessionPhaseList(
                        sessionHandle2.getId(), startSlotIndex2, endSlotIndex2))
                .build();

        // Setup the expected byte-array for the Hybrid configuration.
        ByteBuffer expectedHybridConfigBytes = ByteBuffer.allocate(noOfPhases * 8);
        expectedHybridConfigBytes.order(ByteOrder.LITTLE_ENDIAN);

        expectedHybridConfigBytes.putInt(sessionHandle1.getId());
        expectedHybridConfigBytes.putShort(startSlotIndex1);
        expectedHybridConfigBytes.putShort(endSlotIndex1);
        expectedHybridConfigBytes.putInt(sessionHandle2.getId());
        expectedHybridConfigBytes.putShort(startSlotIndex2);
        expectedHybridConfigBytes.putShort(endSlotIndex2);

        when(mNativeUwbManager.setHybridSessionConfiguration(
                eq(uwbSession.getSessionId()), eq(noOfPhases), eq(updateTime),
                eq(expectedHybridConfigBytes.array()), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        assertThat(mUwbSessionManager.setHybridSessionConfiguration(uwbSession.getSessionHandle(),
                params.toBundle()))
                .isEqualTo(UwbUciConstants.STATUS_CODE_OK);
    }

    @Test
    public void testSetHybridSessionConfiguration_whenUwbSessionDoesNotExist() throws Exception {
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        assertThrows(IllegalStateException.class,
                () -> mUwbSessionManager.setHybridSessionConfiguration(mockSessionHandle,
                        mock(PersistableBundle.class)));
    }

    @Test
    public void deInitSession_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        verify(mUwbSessionManager, never()).getSessionId(any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void deInitSession_success() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        UwbSession mockUwbSession = mock(UwbSession.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle, mockUwbSession);

        mUwbSessionManager.deInitSession(mockSessionHandle);

        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_DEINIT

        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void deInitSession_success_afterOwrAoaMeasurement() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle, mockUwbSession);

        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // Setup the UwbSession to have the peer device's MacAddress stored (which happens when
        // a valid RANGE_DATA_NTF with an OWR AoA Measurement is received).
        doReturn(Set.of(PEER_EXTENDED_MAC_ADDRESS_LONG)).when(mockUwbSession)
                .getRemoteMacAddressList();

        // Call deInitSession().
        IBinder mockBinder = mock(IBinder.class);
        doReturn(mockBinder).when(mockUwbSession).getBinder();
        doReturn(FiraParams.PROTOCOL_NAME).when(mockUwbSession).getProtocolName();
        doReturn(null).when(mockUwbSession).getAnyNonPrivilegedAppInAttributionSource();
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(TEST_SESSION_ID).when(mockUwbSession).getSessionId();

        mUwbSessionManager.deInitSession(mockSessionHandle);
        mTestLooper.dispatchNext();

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS_LONG);
    }

    @Test
    public void execDeInitSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_DEINIT
    }

    @Test
    public void execDeInitSession_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void execDeInitSession_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbAdvertiseManager, never()).removeAdvertiseTarget(isA(Long.class));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void execDeInitSession_multipleTimes() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        // Call deInitSession() twice on the same UWB Session, and then proceed to dispatch the two
        // messages for both the calls. The second message should not have any effect, and silently
        // stop processing.
        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();
        mTestLooper.dispatchNext();

        // Verify the DeInit steps.
        verify(mUwbSessionNotificationManager, times(1)).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void deinitAllSession() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        SessionHandle mockSessionHandle1 = mock(SessionHandle.class);
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockUwbSession1.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockUwbSession1.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mockUwbSession1.getSessionHandle()).thenReturn(mockSessionHandle1);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle1, mockUwbSession1);

        UwbSession mockUwbSession2 = mock(UwbSession.class);
        SessionHandle mockSessionHandle2 = mock(SessionHandle.class);
        when(mockUwbSession2.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession2.getSessionId()).thenReturn(TEST_SESSION_ID + 100);
        when(mockUwbSession2.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mockUwbSession2.getSessionHandle()).thenReturn(mockSessionHandle2);
        mUwbSessionManager.mSessionTable.put(mockSessionHandle2, mockUwbSession2);

        mUwbSessionManager.deinitAllSession();

        verify(mUwbSessionNotificationManager, times(2))
                .onRangingClosedWithApiReasonCode(any(), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbSessionManager, times(2)).removeSession(any());
        // TODO: enable it when the deviceReset is enabled.
        // verify(mNativeUwbManager).deviceReset(eq(UwbUciConstants.UWBS_RESET));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
    }

    @Test
    public void onSessionStatusNotification_session_deinit() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                uwbSession.getSessionId(), UwbUciConstants.UWB_SESSION_STATE_DEINIT,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
    }

    @Test
    public void onSessionStatusNotification_session_deinit_after_close() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.deinitAllSession();
        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);

        // Ignore the stale deinit
        mUwbSessionManager.handleOnDeInit(uwbSession);
        verifyNoMoreInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onSessionStatusNotification_session_deinit_owrAoa() throws Exception {
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        UwbSession uwbSession = prepareExistingUwbSession(firaParams);

        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);

        // First call onDataReceived() to get the application payload data.
        when(mDeviceConfigFacade.getRxDataMaxPacketsToStore())
                .thenReturn(MAX_RX_DATA_PACKETS_TO_STORE);
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF. Setup
        // isPointedTarget() to return "false", as in that scenario the stored AdvertiseTarget
        // is not removed.
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(false);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbAdvertiseManager).isPointedTarget(PEER_EXTENDED_MAC_ADDRESS);

        // Now call onSessionStatusNotificationReceived() on the same UwbSession, and verify that
        // removeAdvertiseTarget() is called to remove any stored OwR AoA Measurement(s).
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                uwbSession.getSessionId(), UwbUciConstants.UWB_SESSION_STATE_DEINIT,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(isA(Long.class));
    }

    @Test
    public void testHandleClientDeath() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        uwbSession.binderDied();

        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mUwbSessionManager.getAliroSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getCccSessionCount()).isEqualTo(0L);
        assertThat(mUwbSessionManager.getFiraSessionCount()).isEqualTo(0L);
    }

    @Test
    public void testDtTagRangingRoundsUpdate() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        byte[] indices = {1, 2};
        DtTagUpdateRangingRoundsStatus status = new DtTagUpdateRangingRoundsStatus(0,
                indices.length, indices);
        PersistableBundle bundle = new DlTDoARangingRoundsUpdate.Builder()
                .setSessionId(uwbSession.getSessionId())
                .setNoOfRangingRounds(indices.length)
                .setRangingRoundIndexes(indices)
                .build()
                .toBundle();

        when(mNativeUwbManager.sessionUpdateDtTagRangingRounds(anyInt(), anyInt(), any(),
                anyString())).thenReturn(status);

        mUwbSessionManager.rangingRoundsUpdateDtTag(uwbSession.getSessionHandle(), bundle);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).sessionUpdateDtTagRangingRounds(uwbSession.getSessionId(),
                indices.length, indices, uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onRangingRoundsUpdateStatus(any(), any());
    }


    @Test
    public void onDataTransferPhaseConfigNotificationReceived()
            throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        PersistableBundle bundle = new FiraDataTransferPhaseConfigStatusCode.Builder()
                 .setStatusCode(
                 UwbUciConstants.STATUS_CODE_DATA_TRANSFER_PHASE_CONFIG_DTPCM_CONFIG_SUCCESS)
                 .build().toBundle();
        //successfully setting the configuration
        mUwbSessionManager.onDataTransferPhaseConfigNotificationReceived(uwbSession.getSessionId(),
                UwbUciConstants.STATUS_CODE_DATA_TRANSFER_PHASE_CONFIG_DTPCM_CONFIG_SUCCESS);
        mTestLooper.dispatchAll();
        verify(mUwbSessionNotificationManager)
                .onDataTransferPhaseConfigured(
                isA(UwbSession.class), isA(PersistableBundle.class));

        //failed to set the configuration
        mUwbSessionManager.onDataTransferPhaseConfigNotificationReceived(uwbSession.getSessionId(),
                UwbUciConstants
                .STATUS_CODE_DATA_TRANSFER_PHASE_CONFIG_ERROR_DUPLICATE_SLOT_ASSIGMENT);
        mTestLooper.dispatchAll();
        verify(mUwbSessionNotificationManager).onDataTransferPhaseConfigFailed(
                isA(UwbSession.class), isA(PersistableBundle.class));
    }


    @Test
    public void onRadarDataMessageReceivedWithValidUwbSession() {
        UwbRadarData uwbRadarData = UwbTestUtils.generateUwbRadarData(
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRadarDataMessageReceived(uwbRadarData);

        verify(mUwbSessionNotificationManager)
                .onRadarDataMessageReceived(eq(mockUwbSession), eq(uwbRadarData));
    }

    @Test
    public void onRadarDataMessageReceivedWithInvalidSession() {
        UwbRadarData uwbRadarData = UwbTestUtils.generateUwbRadarData(
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
                UwbUciConstants.STATUS_CODE_OK);
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRadarDataMessageReceived(uwbRadarData);

        verify(mUwbSessionNotificationManager, never())
                .onRadarDataMessageReceived(any(), eq(uwbRadarData));
    }

    @Test
    public void execStartRanging_onRadarDataMessage() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession(setupRadarParams());
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a radar data notification.
        UwbRadarData uwbRadarData = UwbTestUtils.generateUwbRadarData(
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
                UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRadarDataMessageReceived(uwbRadarData);
        verify(mUwbSessionNotificationManager)
                .onRadarDataMessageReceived(uwbSession, uwbRadarData);
    }

    private UwbSessionManager.ReceivedDataInfo buildReceivedDataInfo(long macAddress) {
        return buildReceivedDataInfo(macAddress, DATA_SEQUENCE_NUM);
    }

    private UwbSessionManager.ReceivedDataInfo buildReceivedDataInfo(
            long macAddress, long sequenceNum) {
        UwbSessionManager.ReceivedDataInfo info = new UwbSessionManager.ReceivedDataInfo();
        info.sessionId = TEST_SESSION_ID;
        info.status = STATUS_CODE_OK;
        info.sequenceNum = sequenceNum;
        info.address = macAddress;
        info.payload = DATA_PAYLOAD;
        return info;
    }

    private static byte[] getComputedMacAddress(byte[] address) {
        if (!SdkLevel.isAtLeastU()) {
            return TlvUtil.getReverseBytes(address);
        }
        return address;
    }
}
