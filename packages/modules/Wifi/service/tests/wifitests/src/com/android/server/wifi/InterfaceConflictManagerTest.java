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

package com.android.server.wifi;

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_NAN;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_P2P;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiContext;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.LocalLog;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.util.WaitingState;
import com.android.server.wifi.util.WorkSourceHelper;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit test harness for InterfaceConflictManager.
 */
@SmallTest
public class InterfaceConflictManagerTest extends WifiBaseTest{
    private TestLooper mTestLooper;
    private InterfaceConflictManager mDut;

    @Mock WifiInjector mWifiInjector;
    @Mock WifiContext mWifiContext;
    @Mock Resources mResources;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock HalDeviceManager mHdm;
    @Mock StateMachine mStateMachine;
    @Mock State mTargetState;
    @Mock WaitingState mWaitingState;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock WifiDialogManager.DialogHandle mDialogHandle;
    @Mock LocalLog mLocalLog;
    @Mock WorkSourceHelper mWsHelper;
    @Mock WorkSourceHelper mExistingWsHelper;

    private static final int TEST_UID = 1234;
    private static final String TEST_PACKAGE_NAME = "some.package.name";
    private static final String TEST_APP_NAME = "Some App Name";
    private static final WorkSource TEST_WS = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);
    private static final int EXISTING_UID = 5678;
    private static final String EXISTING_PACKAGE_NAME = "existing.package.name";
    private static final String EXISTING_APP_NAME = "Existing App Name";
    private static final WorkSource EXISTING_WS =
            new WorkSource(EXISTING_UID, EXISTING_PACKAGE_NAME);

    ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> mCallbackCaptor =
            ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();

        // enable user approval (needed for most tests)
        when(mWifiContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority)).thenReturn(true);

        when(mFrameworkFacade.getAppName(any(), eq(TEST_PACKAGE_NAME), anyInt()))
                .thenReturn(TEST_APP_NAME);
        when(mFrameworkFacade.getAppName(any(), eq(EXISTING_PACKAGE_NAME), anyInt()))
                .thenReturn(EXISTING_APP_NAME);
        when(mWifiDialogManager.createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(mDialogHandle);

        when(mWifiInjector.makeWsHelper(eq(TEST_WS))).thenReturn(mWsHelper);
        when(mWifiInjector.makeWsHelper(eq(EXISTING_WS))).thenReturn(mExistingWsHelper);
        when(mWsHelper.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        when(mExistingWsHelper.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_FG_APP);
        when(mWsHelper.getWorkSource()).thenReturn(TEST_WS);
        when(mExistingWsHelper.getWorkSource()).thenReturn(EXISTING_WS);
    }

    private void initInterfaceConflictManager() {
        mDut = new InterfaceConflictManager(mWifiInjector, mWifiContext, mFrameworkFacade, mHdm,
                new WifiThreadRunner(new Handler(mTestLooper.getLooper())), mWifiDialogManager,
                mLocalLog);
        mDut.enableVerboseLogging(true);
        mDut.handleBootCompleted();
    }

    /**
     * Verify that w/o user approval enabled will always continue operation
     */
    @Test
    public void testUserApprovalDisabled() {
        // disable user approval
        when(mResources.getBoolean(
                R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority)).thenReturn(false);

        initInterfaceConflictManager();

        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", Message.obtain(),
                        mStateMachine, mWaitingState, mTargetState,
                        HalDeviceManager.HDM_CREATE_IFACE_NAN, TEST_WS, false));

        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();
    }

    /**
     * Verify that requests from packages exempt from user approval will always continue operation
     */
    @Test
    public void testUserApprovalDisabledForSpecificPackage() {
        // disable user approval for specific package
        when(mResources.getStringArray(
                R.array.config_wifiExcludedFromUserApprovalForD2dInterfacePriority)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});

        initInterfaceConflictManager();

        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", Message.obtain(),
                        mStateMachine, mWaitingState, mTargetState,
                        HalDeviceManager.HDM_CREATE_IFACE_NAN, TEST_WS, false));

        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();
    }

    /**
     * Verify that bypassDialog == true will continue operation even with user approval enabled.
     */
    @Test
    public void testBypassDialog() {
        initInterfaceConflictManager();

        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", Message.obtain(),
                        mStateMachine, mWaitingState, mTargetState,
                        HalDeviceManager.HDM_CREATE_IFACE_NAN, TEST_WS, true));

        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();
    }

    /**
     * Verify that if interface cannot be created or if interface can be created w/o side effects
     * then command simply proceeds.
     */
    @Test
    public void testUserApprovalNeededButCommandCanProceed() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_NAN;
        Message msg = Message.obtain();

        // can't create interface
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                null);
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mStateMachine, never()).deferMessage(msg);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();

        // can create interface w/o side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Collections.emptyList());
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mStateMachine, never()).deferMessage(msg);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();
    }

    /**
     * Verify flow with user approval.
     */
    @Test
    public void testUserApproved() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine).transitionTo(mWaitingState);
        verify(mStateMachine).deferMessage(msg);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), mCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();

        // user approve
        mCallbackCaptor.getValue().onPositiveButtonClicked();
        verify(mWaitingState).sendTransitionStateCommand(mTargetState);

        // re-execute command and get indication to proceed without waiting/dialog
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, times(1)).launchDialog();
    }

    /**
     * Verify flow with user rejection.
     */
    @Test
    public void testUserRejected() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine).transitionTo(mWaitingState);
        verify(mStateMachine).deferMessage(msg);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), mCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();

        // user rejects
        mCallbackCaptor.getValue().onNegativeButtonClicked();
        verify(mWaitingState).sendTransitionStateCommand(mTargetState);

        // re-execute command and get indication to abort
        assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, times(1)).launchDialog();
    }

    /**
     * Verify deferred requests are accepted if the user accepted.
     */
    @Test
    public void testUserApprovedWithDeferredMessages() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), mCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(1)).launchDialog();

        // user approve
        mCallbackCaptor.getValue().onPositiveButtonClicked();
        verify(mWaitingState).sendTransitionStateCommand(mTargetState);

        // re-execute command and get indication to proceed
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, times(1)).launchDialog();

        // Proceed with all deferred messages, since the created iface satisfies the request now.
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Collections.emptyList());
        Message waitingMsg = Message.obtain();
        // static mocking
        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                WaitingState.class).startMocking();
        try {
            // Interface was created by user approval, so no impact to create.
            when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS)))
                    .thenReturn(Collections.emptyList());
            when(WaitingState.wasMessageInWaitingState(waitingMsg)).thenReturn(true);
            assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                    mDut.manageInterfaceConflictForStateMachine("Some Tag", waitingMsg,
                            mStateMachine, mWaitingState, mTargetState, interfaceType, TEST_WS,
                            false));
            verify(mStateMachine, times(1)).transitionTo(mWaitingState);
            verify(mStateMachine, never()).deferMessage(waitingMsg);
            verify(mWifiDialogManager, times(1)).createSimpleDialog(
                    any(), any(), any(), any(), any(), any(), any());
            verify(mDialogHandle, times(1)).launchDialog();

            // Unexpected impact to create, launch the dialog again
            when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS)))
                    .thenReturn(Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN,
                            EXISTING_WS)));
            assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                    mDut.manageInterfaceConflictForStateMachine("Some Tag", waitingMsg,
                            mStateMachine, mWaitingState, mTargetState, interfaceType, TEST_WS,
                            false));
            verify(mStateMachine, times(2)).transitionTo(mWaitingState);
            verify(mStateMachine, times(1)).deferMessage(waitingMsg);
            verify(mWifiDialogManager, times(2)).createSimpleDialog(
                    any(), any(), any(), any(), any(), any(), any());
            verify(mDialogHandle, times(2)).launchDialog();
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Verify deferred requests are automatically aborted if the user rejected.
     */
    @Test
    public void testUserRejectedWithDeferredMessages() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), mCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(1)).launchDialog();

        // user rejects
        mCallbackCaptor.getValue().onNegativeButtonClicked();
        verify(mWaitingState).sendTransitionStateCommand(mTargetState);

        // re-execute command and get indication to abort
        assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mStateMachine,
                        mWaitingState, mTargetState, interfaceType, TEST_WS, false));
        verify(mStateMachine, times(1)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(msg);
        verify(mWifiDialogManager, times(1)).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, times(1)).launchDialog();

        // Reject all deferred messages
        Message waitingMsg = Message.obtain();
        // static mocking
        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                WaitingState.class).startMocking();
        try {
            when(WaitingState.wasMessageInWaitingState(waitingMsg)).thenReturn(true);
            assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                    mDut.manageInterfaceConflictForStateMachine("Some Tag", waitingMsg,
                            mStateMachine, mWaitingState, mTargetState, interfaceType, TEST_WS,
                            false));
            assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                    mDut.manageInterfaceConflictForStateMachine("Some Tag", waitingMsg,
                            mStateMachine, mWaitingState, mTargetState, interfaceType, TEST_WS,
                            false));
            assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                    mDut.manageInterfaceConflictForStateMachine("Some Tag", waitingMsg,
                            mStateMachine, mWaitingState, mTargetState, interfaceType, TEST_WS,
                            false));
            verify(mStateMachine, times(1)).transitionTo(mWaitingState);
            verify(mStateMachine, never()).deferMessage(waitingMsg);
            verify(mWifiDialogManager, times(1)).createSimpleDialog(
                    any(), any(), any(), any(), any(), any(), any());
            verify(mDialogHandle, times(1)).launchDialog();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testP2pInterfaceRemovalIsAutoApprovedWhenP2pIsDisconnected()
            throws Exception {
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalNotRequireForDisconnectedP2p))
                .thenReturn(true);

        initInterfaceConflictManager();

        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mWifiContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        // Notify that P2P is disconnected.
        Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        NetworkInfo info = new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P,
                0, "WIFI_P2P", "");
        info.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, null, null);
        intent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, info);
        receiver.getValue().onReceive(mWifiContext, intent);

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_NAN;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_P2P, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine, never()).transitionTo(mWaitingState);
        verify(mStateMachine, never()).deferMessage(msg);
        verify(mWifiDialogManager, never()).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();
    }

    @Test
    public void testP2pInterfaceRemovalNeedUserApprovalWhenP2pIsConnected()
            throws Exception {
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalNotRequireForDisconnectedP2p))
                .thenReturn(true);

        initInterfaceConflictManager();

        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mWifiContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        // Notify that P2P is connected.
        Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        NetworkInfo info = new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P,
                0, "WIFI_P2P", "");
        info.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
        intent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, info);
        receiver.getValue().onReceive(mWifiContext, intent);

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_NAN;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_P2P, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine).transitionTo(mWaitingState);
        verify(mStateMachine).deferMessage(msg);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle).launchDialog();
    }

    /**
     * Verify that reset() will dismiss any dialogs, transition the waiting state machine to their
     * target state, and reset InterfaceConflictManager to accept new requests.
     */
    @Test
    public void testReset() {
        initInterfaceConflictManager();

        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = Message.obtain();

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN, EXISTING_WS)));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine).transitionTo(mWaitingState);
        verify(mStateMachine).deferMessage(msg);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle).launchDialog();

        // reset
        mDut.reset();

        // State machine should have gone back to target
        verify(mWaitingState).sendTransitionStateCommand(mTargetState);
        // Dialog should have been dismissed
        verify(mDialogHandle).dismissDialog();
        // New request should launch dialog like normal.
        Message newMsg = Message.obtain();
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", newMsg,
                        mStateMachine, mWaitingState, mTargetState,
                        interfaceType, TEST_WS, false));
        verify(mStateMachine, times(2)).transitionTo(mWaitingState);
        verify(mStateMachine, times(1)).deferMessage(newMsg);
        verify(mWifiDialogManager, times(2)).createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, times(2)).launchDialog();
    }

    /**
     * Tests that
     * {@link InterfaceConflictManager#needsUserApprovalToDelete(int, WorkSourceHelper, int,
     * WorkSourceHelper)} returns true on the following conditions:
     * 1) Requested interface is AP, AP_BRIDGED, P2P, or NAN.
     * 2) Existing interface is AP, AP_BRIDGED, P2P, or NAN (but not the same as the requested).
     * 3) Requestor worksource has higher priority than PRIORITY_BG.
     * 4) Existing worksource is not PRIORITY_INTERNAL.
     * 5) User approval is required (by overlay or override).
     * 6) Requestor package is not exempt from user approval.
     */
    @Test
    public void testCanDeleteWithUserApproval() throws Exception {
        // No dialog if dialogs aren't enabled.
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority))
                .thenReturn(false);
        initInterfaceConflictManager();
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));

        // No dialog if requesting package is exempt.
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority))
                .thenReturn(true);
        when(mResources.getStringArray(
                R.array.config_wifiExcludedFromUserApprovalForD2dInterfacePriority)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});
        initInterfaceConflictManager();
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));

        // No dialog if override is set to false.
        when(mResources.getStringArray(
                R.array.config_wifiExcludedFromUserApprovalForD2dInterfacePriority))
                .thenReturn(null);
        initInterfaceConflictManager();
        mDut.setUserApprovalNeededOverride(true, false);
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));

        // Dialog if overlay is false but override is true.
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority))
                .thenReturn(false);
        initInterfaceConflictManager();
        mDut.setUserApprovalNeededOverride(true, true);
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));

        // No dialog if overlay is false and override is changed from true to false.
        mDut.setUserApprovalNeededOverride(false, false);
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));

        // Should show dialog for appropriate types if overlay is set to true.
        when(mResources.getBoolean(R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority))
                .thenReturn(true);
        initInterfaceConflictManager();

        // Requesting AP
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_AP_BRIDGE, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));

        // Requesting AP_BRIDGE
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP_BRIDGE, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP_BRIDGE, mWsHelper,
                HDM_CREATE_IFACE_AP_BRIDGE, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP_BRIDGE, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_AP_BRIDGE, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));

        // Requesting P2P
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_AP_BRIDGE, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_P2P, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));

        // Requesting NAN
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_AP, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_AP_BRIDGE, mExistingWsHelper));
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_NAN, mExistingWsHelper));
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));

        // Foreground should show dialog over Privileged
        when(mExistingWsHelper.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_PRIVILEGED);
        assertTrue(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));

        // Foreground should delete Internal without showing dialog
        when(mExistingWsHelper.getRequestorWsPriority())
                .thenReturn(WorkSourceHelper.PRIORITY_INTERNAL);
        assertFalse(mDut.needsUserApprovalToDelete(
                HDM_CREATE_IFACE_NAN, mWsHelper,
                HDM_CREATE_IFACE_P2P, mExistingWsHelper));
    }
}
