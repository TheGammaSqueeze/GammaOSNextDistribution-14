/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.HandlerThread;
import android.telecom.CallAudioState;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioCommunicationDeviceTracker;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.StatusBarNotifier;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.WiredHeadsetManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class CallAudioRouteStateMachineTest extends TelecomTestCase {

    private static final BluetoothDevice bluetoothDevice1 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:01");
    private static final BluetoothDevice bluetoothDevice2 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:02");
    private static final BluetoothDevice bluetoothDevice3 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:03");

    @Mock CallsManager mockCallsManager;
    @Mock BluetoothRouteManager mockBluetoothRouteManager;
    @Mock IAudioService mockAudioService;
    @Mock ConnectionServiceWrapper mockConnectionServiceWrapper;
    @Mock WiredHeadsetManager mockWiredHeadsetManager;
    @Mock StatusBarNotifier mockStatusBarNotifier;
    @Mock Call fakeSelfManagedCall;
    @Mock Call fakeCall;
    @Mock CallAudioManager mockCallAudioManager;
    @Mock BluetoothDevice mockWatchDevice;

    private CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    private static final int TEST_TIMEOUT = 500;
    private AudioManager mockAudioManager;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };
    private HandlerThread mThreadHandler;
    CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mThreadHandler = new HandlerThread("CallAudioRouteStateMachineTest");
        mThreadHandler.start();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mockAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mCommunicationDeviceTracker = new CallAudioCommunicationDeviceTracker(mContext);
        mCommunicationDeviceTracker.setBluetoothRouteManager(mockBluetoothRouteManager);

        mAudioServiceFactory = new CallAudioManager.AudioServiceFactory() {
            @Override
            public IAudioService getAudioService() {
                return mockAudioService;
            }
        };

        when(mockCallsManager.getForegroundCall()).thenReturn(fakeCall);
        when(mockCallsManager.getTrackedCalls()).thenReturn(Set.of(fakeCall));
        when(mockCallsManager.getLock()).thenReturn(mLock);
        when(mockCallsManager.hasVideoCall()).thenReturn(false);
        when(fakeCall.getConnectionService()).thenReturn(mockConnectionServiceWrapper);
        when(fakeCall.isAlive()).thenReturn(true);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(CallAudioState.ROUTE_ALL);
        when(fakeSelfManagedCall.getConnectionService()).thenReturn(mockConnectionServiceWrapper);
        when(fakeSelfManagedCall.isAlive()).thenReturn(true);
        when(fakeSelfManagedCall.getSupportedAudioRoutes()).thenReturn(CallAudioState.ROUTE_ALL);
        when(fakeSelfManagedCall.isSelfManaged()).thenReturn(true);
        when(mFeatureFlags.transitRouteBeforeAudioDisconnectBt()).thenReturn(false);

        doNothing().when(mockConnectionServiceWrapper).onCallAudioStateChanged(any(Call.class),
                any(CallAudioState.class));
        when(mFeatureFlags.ignoreAutoRouteToWatchDevice()).thenReturn(false);
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mThreadHandler.quit();
        mThreadHandler.join();
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testEarpieceAutodetect() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_AUTO_DETECT,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);

        // Since we don't know if we're on a platform with an earpiece or not, all we can do
        // is ensure the stateMachine construction didn't fail.  But at least we exercised the
        // autodetection code...
        assertNotNull(stateMachine);
    }

    @SmallTest
    @Test
    public void testTrackedCallsReceiveAudioRouteChange() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_AUTO_DETECT,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        Set<Call> trackedCalls = new HashSet<>(Arrays.asList(fakeCall, fakeSelfManagedCall));
        when(mockCallsManager.getTrackedCalls()).thenReturn(trackedCalls);

        // start state --> ROUTE_EARPIECE
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.setCallAudioManager(mockCallAudioManager);

        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_EARPIECE);

        // ROUTE_EARPIECE  --> ROUTE_SPEAKER
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_SPEAKER,
                CallAudioRouteStateMachine.SPEAKER_ON);

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // assert expected end state
        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_SPEAKER);
        // should update the audio route on all tracked calls ...
        verify(mockConnectionServiceWrapper, times(trackedCalls.size()))
                .onCallAudioStateChanged(any(), any());
    }

    @SmallTest
    @Test
    public void testSystemAudioStateIsNotUpdatedFlagOff() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_AUTO_DETECT,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        Set<Call> trackedCalls = new HashSet<>(Arrays.asList(fakeCall, fakeSelfManagedCall));
        when(mockCallsManager.getTrackedCalls()).thenReturn(trackedCalls);
        when(mFeatureFlags.availableRoutesNeverUpdatedAfterSetSystemAudioState()).thenReturn(false);

        // start state --> ROUTE_EARPIECE
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.setCallAudioManager(mockCallAudioManager);

        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_EARPIECE);

        // ROUTE_EARPIECE  --> ROUTE_SPEAKER
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_SPEAKER,
                CallAudioRouteStateMachine.SPEAKER_ON);

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);

        waitForHandlerAction(stateMachine.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getHandler(), TEST_TIMEOUT);

        CallAudioState expectedCallAudioState = stateMachine.getLastKnownCallAudioState();

        // assert expected end state
        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_SPEAKER);
        // should update the audio route on all tracked calls ...
        verify(mockConnectionServiceWrapper, times(trackedCalls.size()))
                .onCallAudioStateChanged(any(), any());

        assertNotEquals(expectedCallAudioState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testSystemAudioStateIsUpdatedFlagOn() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_AUTO_DETECT,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        Set<Call> trackedCalls = new HashSet<>(Arrays.asList(fakeCall, fakeSelfManagedCall));
        when(mockCallsManager.getTrackedCalls()).thenReturn(trackedCalls);
        when(mFeatureFlags.availableRoutesNeverUpdatedAfterSetSystemAudioState()).thenReturn(true);

        // start state --> ROUTE_EARPIECE
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.setCallAudioManager(mockCallAudioManager);

        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_EARPIECE);

        // ROUTE_EARPIECE  --> ROUTE_SPEAKER
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_SPEAKER,
                CallAudioRouteStateMachine.SPEAKER_ON);

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        CallAudioState expectedCallAudioState = stateMachine.getLastKnownCallAudioState();

        // assert expected end state
        assertEquals(stateMachine.getCurrentCallAudioState().getRoute(),
                CallAudioRouteStateMachine.ROUTE_SPEAKER);
        // should update the audio route on all tracked calls ...
        verify(mockConnectionServiceWrapper, times(trackedCalls.size()))
                .onCallAudioStateChanged(any(), any());

        assertEquals(expectedCallAudioState, stateMachine.getCurrentCallAudioState());
    }

    @MediumTest
    @Test
    public void testStreamRingMuteChange() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        // Make sure we register a receiver for the STREAM_MUTE_CHANGED_ACTION so we can see if the
        // ring stream unmutes.
        ArgumentCaptor<BroadcastReceiver> brCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> filterCaptor = ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext, times(3)).registerReceiver(brCaptor.capture(), filterCaptor.capture());
        boolean foundValid = false;
        for (int ix = 0; ix < brCaptor.getAllValues().size(); ix++) {
            BroadcastReceiver receiver = brCaptor.getAllValues().get(ix);
            IntentFilter filter = filterCaptor.getAllValues().get(ix);
            if (!filter.hasAction(AudioManager.STREAM_MUTE_CHANGED_ACTION)) {
                continue;
            }

            // Fake out a call to the broadcast receiver and make sure we call into audio manager
            // to trigger re-evaluation of ringing.
            Intent intent = new Intent(AudioManager.STREAM_MUTE_CHANGED_ACTION);
            intent.putExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false);
            intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_RING);
            receiver.onReceive(mContext, intent);
            verify(mockCallAudioManager).onRingerModeChange();
            foundValid = true;
        }
        assertTrue(foundValid);
        verify(mockBluetoothRouteManager, timeout(1000L)).getBluetoothAudioConnectedDevice();
    }

    @MediumTest
    @Test
    public void testSpeakerPersistence() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(true);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                when(mockAudioManager.isSpeakerphoneOn()).thenReturn((Boolean) args[0]);
                return null;
            }
        }).when(mockAudioManager).setSpeakerphoneOn(any(Boolean.class));
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET);
        CallAudioState expectedMiddleState = new CallAudioState(false,
                CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(initState, expectedMiddleState);
        resetMocks();

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(expectedMiddleState, initState);
    }

    @MediumTest
    @Test
    public void testUserBluetoothSwitchOff() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(true);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.USER_SWITCH_BASELINE_ROUTE,
                CallAudioRouteStateMachine.NO_INCLUDE_BLUETOOTH_IN_BASELINE);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(initState, expectedEndState);
        resetMocks();
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_GONE);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_PRESENT);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        assertEquals(expectedEndState, stateMachine.getCurrentCallAudioState());
    }

    @MediumTest
    @Test
    public void testUserBluetoothSwitchOffAndOnAgain() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        Collection<BluetoothDevice> availableDevices = Collections.singleton(bluetoothDevice1);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);

        // Switch off the BT route explicitly.
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.USER_SWITCH_BASELINE_ROUTE,
                CallAudioRouteStateMachine.NO_INCLUDE_BLUETOOTH_IN_BASELINE);
        CallAudioState expectedMidState = new CallAudioState(false,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                null, availableDevices);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(initState, expectedMidState);
        // clear out the handler state before resetting mocks in order to avoid introducing a
        // CallAudioState that has a null list of supported BT devices
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        resetMocks();

        // Now, switch back to BT explicitly
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);
        doAnswer(invocation -> {
            when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                    .thenReturn(bluetoothDevice1);
            stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
            return null;
        }).when(mockBluetoothRouteManager).connectBluetoothAudio(nullable(String.class));
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.USER_SWITCH_BLUETOOTH);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                bluetoothDevice1, availableDevices);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        // second wait needed for the BT_AUDIO_CONNECTED message
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(expectedMidState, expectedEndState);

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_GONE);
        when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                .thenReturn(null);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_PRESENT);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        // second wait needed for the BT_AUDIO_CONNECTED message
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        // Verify that we're still on bluetooth.
        assertEquals(expectedEndState, stateMachine.getCurrentCallAudioState());
    }

    @MediumTest
    @Test
    public void testBluetoothRinging() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices())
                .thenReturn(Collections.singletonList(bluetoothDevice1));
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.RINGING_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(nullable(String.class));

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verify(mockBluetoothRouteManager, times(1)).connectBluetoothAudio(nullable(String.class));
    }

    @MediumTest
    @Test
    public void testConnectBluetoothDuringRinging() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        setInBandRing(false);
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(false);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.RINGING_FOCUS);
        // Wait for the state machine to finish transiting to ActiveEarpiece before hooking up
        // bluetooth mocks
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices())
                .thenReturn(Collections.singletonList(bluetoothDevice1));
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BLUETOOTH_DEVICE_LIST_CHANGED);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_PRESENT);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                null, Collections.singletonList(bluetoothDevice1));
        verifyNewSystemCallAudioState(initState, expectedEndState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verify(mockBluetoothRouteManager, times(1)).connectBluetoothAudio(null);

        when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                .thenReturn(bluetoothDevice1);
        stateMachine.sendMessage(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        // It is possible that this will be called twice from ActiveBluetoothRoute#enter. The extra
        // call to setBluetoothOn will trigger BT_AUDIO_CONNECTED, which also ends up invoking
        // CallAudioManager#onRingerModeChange.
        verify(mockCallAudioManager, atLeastOnce()).onRingerModeChange();
    }

    @SmallTest
    @Test
    public void testConnectSpecificBluetoothDevice() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        List<BluetoothDevice> availableDevices =
                Arrays.asList(bluetoothDevice1, bluetoothDevice2, bluetoothDevice3);

        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);
        doAnswer(invocation -> {
            when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                    .thenReturn(bluetoothDevice2);
            stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
            return null;
        }).when(mockBluetoothRouteManager).connectBluetoothAudio(bluetoothDevice2.getAddress());

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, null,
                availableDevices);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.USER_SWITCH_BLUETOOTH,
                0, bluetoothDevice2.getAddress());
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        verify(mockBluetoothRouteManager).connectBluetoothAudio(bluetoothDevice2.getAddress());
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                bluetoothDevice2,
                availableDevices);

        assertEquals(expectedEndState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testCallDisconnectedWhenAudioRoutedToBluetooth() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        List<BluetoothDevice> availableDevices = Arrays.asList(bluetoothDevice1);

        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);
        when(mockBluetoothRouteManager.isInbandRingingEnabled()).thenReturn(true);
        when(mFeatureFlags.transitRouteBeforeAudioDisconnectBt()).thenReturn(true);
        doAnswer(invocation -> {
            when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                    .thenReturn(bluetoothDevice1);
            stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
            return null;
        }).when(mockBluetoothRouteManager).connectBluetoothAudio(bluetoothDevice1.getAddress());
        doAnswer(invocation -> {
            when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                    .thenReturn(bluetoothDevice1);
            stateMachine.sendMessageWithSessionInfo(
                    CallAudioRouteStateMachine.BT_AUDIO_DISCONNECTED);
            return null;
        }).when(mockBluetoothRouteManager).disconnectAudio();

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, null,
                availableDevices);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.NO_FOCUS, bluetoothDevice1.getAddress());
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        verify(mockBluetoothRouteManager).disconnectAudio();
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                bluetoothDevice1,
                availableDevices);

        assertEquals(expectedEndState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testDockWhenInQuiescentState() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        // Raise a dock connect event.
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.CONNECT_DOCK);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        assertTrue(!stateMachine.isInActiveState());
        verify(mockAudioManager, never()).setSpeakerphoneOn(eq(true));

        // Raise a dock disconnect event.
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.DISCONNECT_DOCK);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        assertTrue(!stateMachine.isInActiveState());
        verify(mockAudioManager, never()).setSpeakerphoneOn(eq(false));
    }

    @SmallTest
    @Test
    public void testFocusChangeFromQuiescentSpeaker() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        // Switch to active, pretending that a call came in.
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active speaker route and that we've
        // called setSpeakerOn
        assertTrue(stateMachine.isInActiveState());
        ArgumentCaptor<AudioDeviceInfo> infoArgumentCaptor = ArgumentCaptor.forClass(
                AudioDeviceInfo.class);
        verify(mockAudioManager).setCommunicationDevice(infoArgumentCaptor.capture());
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, infoArgumentCaptor.getValue().getType());
    }

    @SmallTest
    @Test
    public void testFocusChangeWithAlreadyActiveBtDevice() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);
        List<BluetoothDevice> availableDevices =
                Arrays.asList(bluetoothDevice1, bluetoothDevice2);

        // Set up a state where there's an HFP connected bluetooth device already.
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);
        when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                .thenReturn(bluetoothDevice1);

        // We want to be in the QuiescentBluetoothRoute because there's no call yet
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                bluetoothDevice1, availableDevices);
        stateMachine.initialize(initState);

        // Switch to active, pretending that a call came in.
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active BT route and that we've
        // called connectAudio on the right device.
        verify(mockBluetoothRouteManager, atLeastOnce())
                .connectBluetoothAudio(eq(bluetoothDevice1.getAddress()));
        assertTrue(stateMachine.isInActiveState());
    }

    @SmallTest
    @Test
    public void testSetAndClearEarpieceCommunicationDevice() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        AudioDeviceInfo earpiece = mock(AudioDeviceInfo.class);
        when(earpiece.getType()).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        when(earpiece.getAddress()).thenReturn("");
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(earpiece);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(earpiece)))
                .thenReturn(true);
        when(mockAudioManager.getCommunicationDevice()).thenReturn(earpiece);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER |
                        CallAudioState.ROUTE_WIRED_HEADSET);
        stateMachine.initialize(initState);

        // Switch to active
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active earpiece and that we set the
        // communication device.
        assertTrue(stateMachine.isInActiveState());
        ArgumentCaptor<AudioDeviceInfo> infoArgumentCaptor = ArgumentCaptor.forClass(
                AudioDeviceInfo.class);
        verify(mockAudioManager).setCommunicationDevice(infoArgumentCaptor.capture());
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                infoArgumentCaptor.getValue().getType());

        // Route earpiece to speaker
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_SPEAKER,
                CallAudioRouteStateMachine.SPEAKER_ON);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Assert that communication device was cleared
        verify(mockAudioManager).clearCommunicationDevice();
    }

    @SmallTest
    @Test
    public void testSetAndClearWiredHeadsetCommunicationDevice() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        verifySetAndClearHeadsetCommunicationDevice(AudioDeviceInfo.TYPE_WIRED_HEADSET);
    }

    @SmallTest
    @Test
    public void testSetAndClearUsbHeadsetCommunicationDevice() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        verifySetAndClearHeadsetCommunicationDevice(AudioDeviceInfo.TYPE_USB_HEADSET);
    }

    @SmallTest
    @Test
    public void testActiveFocusRouteSwitchFromQuiescentBluetooth() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        // Start the route in quiescent and ensure that a switch to ACTIVE_FOCUS transitions to
        // the corresponding active route even when there aren't any active BT devices available.
        CallAudioState initState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_EARPIECE);
        stateMachine.initialize(initState);

        // Switch to active
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active route on BT
        assertTrue(stateMachine.isInActiveState());
    }

    @SmallTest
    @Test
    public void testInitializationWithEarpieceNoHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithEarpieceAndHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithEarpieceAndHeadsetAndBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithEarpieceAndBluetoothNoHeadset() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER
                        | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithNoEarpieceNoHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_DISABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithHeadsetNoBluetoothNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_DISABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithHeadsetAndBluetoothNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_DISABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithBluetoothNoHeadsetNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, CallAudioRouteStateMachine.EARPIECE_FORCE_DISABLED);
    }

    @SmallTest
    @Test
    public void testInitializationWithAvailableButInactiveBtDevice() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_EARPIECE);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.hasBtActiveDevice()).thenReturn(false);

        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.initialize();
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testStreamingRoute() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
            CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.STREAMING_FORCE_ENABLED);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_STREAMING, CallAudioState.ROUTE_STREAMING);

        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(initState, expectedEndState);
        resetMocks();
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.STREAMING_FORCE_DISABLED);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        assertEquals(initState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testIgnoreSpeakerOffMessage() {
        when(mockBluetoothRouteManager.isInbandRingingEnabled()).thenReturn(true);
        when(mockBluetoothRouteManager.getBluetoothAudioConnectedDevice())
                .thenReturn(bluetoothDevice1);
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER
                        | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        doAnswer(
                (address) -> {
                    stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SPEAKER_OFF);
                    stateMachine.sendMessageDelayed(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED,
                            5000L);
                    return null;
        }).when(mockBluetoothRouteManager).connectBluetoothAudio(anyString());
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.USER_SWITCH_BLUETOOTH);

        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_EARPIECE);
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    @MediumTest
    @Test
    public void testIgnoreImplicitBTSwitchWhenDeviceIsWatch() {
        when(mFeatureFlags.ignoreAutoRouteToWatchDevice()).thenReturn(true);
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        AudioDeviceInfo headset = mock(AudioDeviceInfo.class);
        when(headset.getType()).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET);
        when(headset.getAddress()).thenReturn("");
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(headset);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(headset)))
                .thenReturn(true);
        when(mockAudioManager.getCommunicationDevice()).thenReturn(headset);

        CallAudioState initState = new CallAudioState(false,
                CallAudioState.ROUTE_WIRED_HEADSET, CallAudioState.ROUTE_WIRED_HEADSET
                | CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        // Switch to active
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active headset.
        assertTrue(stateMachine.isInActiveState());
        ArgumentCaptor<AudioDeviceInfo> infoArgumentCaptor = ArgumentCaptor.forClass(
                AudioDeviceInfo.class);
        verify(mockAudioManager).setCommunicationDevice(infoArgumentCaptor.capture());
        assertEquals(AudioDeviceInfo.TYPE_WIRED_HEADSET, infoArgumentCaptor.getValue().getType());

        // Set up watch device as only available BT device.
        Collection<BluetoothDevice> availableDevices = Collections.singleton(mockWatchDevice);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockBluetoothRouteManager.getConnectedDevices()).thenReturn(availableDevices);
        when(mockBluetoothRouteManager.isWatch(any(BluetoothDevice.class))).thenReturn(true);

        // Disconnect wired headset to force switch to BT (verify that we ignore the implicit switch
        // to BT when the watch is the only connected device and that we move into the next
        // available route.
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH,
                null, availableDevices);
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testQuiescentBluetoothRouteResetMute() {
        when(mFeatureFlags.resetMuteWhenEnteringQuiescentBtRoute()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        CallAudioState initState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        // Switch to active and mute
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        assertTrue(stateMachine.isInActiveState());

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.MUTE_ON);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        CallAudioState expectedState = new CallAudioState(true,
                CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.NO_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        expectedState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        // TODO: Re-enable this part of the test; this is now failing because we have to
        // revert ag/23783145.
        // assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    @SmallTest
    @Test
    public void testSupportRouteMaskUpdateWhenBtAudioConnected() {
        when(mFeatureFlags.updateRouteMaskWhenBtConnected()).thenReturn(true);
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        CallAudioState initState = new CallAudioState(false,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER
                        | CallAudioState.ROUTE_BLUETOOTH);
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    private void initializationTestHelper(CallAudioState expectedState,
            int earpieceControl) {
        when(mockWiredHeadsetManager.isPluggedIn()).thenReturn(
                (expectedState.getSupportedRouteMask() & CallAudioState.ROUTE_WIRED_HEADSET) != 0);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(
                (expectedState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0);
        when(mockBluetoothRouteManager.hasBtActiveDevice()).thenReturn(
                (expectedState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0);

        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                earpieceControl,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.initialize();
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    private void verifyNewSystemCallAudioState(CallAudioState expectedOldState,
            CallAudioState expectedNewState) {
        ArgumentCaptor<CallAudioState> oldStateCaptor = ArgumentCaptor.forClass(
                CallAudioState.class);
        ArgumentCaptor<CallAudioState> newStateCaptor1 = ArgumentCaptor.forClass(
                CallAudioState.class);
        ArgumentCaptor<CallAudioState> newStateCaptor2 = ArgumentCaptor.forClass(
                CallAudioState.class);
        verify(mockCallsManager, timeout(TEST_TIMEOUT).atLeastOnce()).onCallAudioStateChanged(
                oldStateCaptor.capture(), newStateCaptor1.capture());
        verify(mockConnectionServiceWrapper, timeout(TEST_TIMEOUT).atLeastOnce())
                .onCallAudioStateChanged(same(fakeCall), newStateCaptor2.capture());

        assertTrue(oldStateCaptor.getAllValues().get(0).equals(expectedOldState));
        assertTrue(newStateCaptor1.getValue().equals(expectedNewState));
        assertTrue(newStateCaptor2.getValue().equals(expectedNewState));
    }

    private void setInBandRing(boolean enabled) {
        when(mockBluetoothRouteManager.isInbandRingingEnabled()).thenReturn(enabled);
    }

    private void resetMocks() {
        reset(mockAudioManager, mockBluetoothRouteManager, mockCallsManager,
                mockConnectionServiceWrapper);
        fakeCall = mock(Call.class);
        when(mockCallsManager.getForegroundCall()).thenReturn(fakeCall);
        when(mockCallsManager.getTrackedCalls()).thenReturn(Set.of(fakeCall));
        when(fakeCall.getConnectionService()).thenReturn(mockConnectionServiceWrapper);
        when(fakeCall.isAlive()).thenReturn(true);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(CallAudioState.ROUTE_ALL);
        when(mockCallsManager.getLock()).thenReturn(mLock);
        doNothing().when(mockConnectionServiceWrapper).onCallAudioStateChanged(any(Call.class),
                any(CallAudioState.class));
    }

    private void verifySetAndClearHeadsetCommunicationDevice(int audioType) {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                CallAudioRouteStateMachine.EARPIECE_FORCE_ENABLED,
                mThreadHandler.getLooper(),
                Runnable::run /** do async stuff sync for test purposes */,
                mCommunicationDeviceTracker,
                mFeatureFlags);
        stateMachine.setCallAudioManager(mockCallAudioManager);

        AudioDeviceInfo headset = mock(AudioDeviceInfo.class);
        when(headset.getType()).thenReturn(audioType);
        when(headset.getAddress()).thenReturn("");
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(headset);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(headset)))
                .thenReturn(true);
        when(mockAudioManager.getCommunicationDevice()).thenReturn(headset);

        CallAudioState initState = new CallAudioState(false,
                CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_EARPIECE);
        stateMachine.initialize(initState);

        // Switch to active
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Make sure that we've successfully switched to the active headset and that we set the
        // communication device.
        assertTrue(stateMachine.isInActiveState());
        ArgumentCaptor<AudioDeviceInfo> infoArgumentCaptor = ArgumentCaptor.forClass(
                AudioDeviceInfo.class);
        verify(mockAudioManager).setCommunicationDevice(infoArgumentCaptor.capture());
        assertEquals(audioType, infoArgumentCaptor.getValue().getType());

        // Route out of headset route
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.USER_SWITCH_EARPIECE);
        waitForHandlerAction(stateMachine.getAdapterHandler(), TEST_TIMEOUT);

        // Assert that communication device was cleared
        verify(mockAudioManager).clearCommunicationDevice();
    }
}
