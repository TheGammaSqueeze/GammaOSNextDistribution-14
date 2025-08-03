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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static com.android.server.telecom.CallAudioRouteAdapter.ACTIVE_FOCUS;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_ACTIVE_DEVICE_GONE;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_ACTIVE_DEVICE_PRESENT;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_CONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_DISCONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_DEVICE_ADDED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_DEVICE_REMOVED;
import static com.android.server.telecom.CallAudioRouteAdapter.CONNECT_DOCK;
import static com.android.server.telecom.CallAudioRouteAdapter.CONNECT_WIRED_HEADSET;
import static com.android.server.telecom.CallAudioRouteAdapter.DISCONNECT_DOCK;
import static com.android.server.telecom.CallAudioRouteAdapter.DISCONNECT_WIRED_HEADSET;
import static com.android.server.telecom.CallAudioRouteAdapter.MUTE_OFF;
import static com.android.server.telecom.CallAudioRouteAdapter.MUTE_ON;
import static com.android.server.telecom.CallAudioRouteAdapter.NO_FOCUS;
import static com.android.server.telecom.CallAudioRouteAdapter.RINGING_FOCUS;
import static com.android.server.telecom.CallAudioRouteAdapter.SPEAKER_OFF;
import static com.android.server.telecom.CallAudioRouteAdapter.SPEAKER_ON;
import static com.android.server.telecom.CallAudioRouteAdapter.STREAMING_FORCE_DISABLED;
import static com.android.server.telecom.CallAudioRouteAdapter.STREAMING_FORCE_ENABLED;
import static com.android.server.telecom.CallAudioRouteAdapter.SWITCH_EARPIECE;
import static com.android.server.telecom.CallAudioRouteAdapter.SWITCH_FOCUS;
import static com.android.server.telecom.CallAudioRouteAdapter.USER_SWITCH_BLUETOOTH;
import static com.android.server.telecom.CallAudioRouteAdapter.USER_SWITCH_EARPIECE;
import static com.android.server.telecom.CallAudioRouteAdapter.USER_SWITCH_HEADSET;
import static com.android.server.telecom.CallAudioRouteAdapter.USER_SWITCH_SPEAKER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.UserHandle;
import android.telecom.CallAudioState;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.AudioRoute;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioRouteController;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.WiredHeadsetManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class CallAudioRouteControllerTest extends TelecomTestCase {
    private CallAudioRouteController mController;
    @Mock WiredHeadsetManager mWiredHeadsetManager;
    @Mock AudioManager mAudioManager;
    @Mock AudioDeviceInfo mEarpieceDeviceInfo;
    @Mock CallsManager mCallsManager;
    @Mock CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    @Mock IAudioService mAudioService;
    @Mock BluetoothRouteManager mBluetoothRouteManager;
    private AudioRoute mEarpieceRoute;
    private AudioRoute mSpeakerRoute;
    private static final String BT_ADDRESS_1 = "00:00:00:00:00:01";
    private static final BluetoothDevice BLUETOOTH_DEVICE_1 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:01");
    private static final Set<BluetoothDevice> BLUETOOTH_DEVICES;
    static {
        BLUETOOTH_DEVICES = new HashSet<>();
        BLUETOOTH_DEVICES.add(BLUETOOTH_DEVICE_1);
    }
    private static final int TEST_TIMEOUT = 500;
    AudioRoute.Factory mAudioRouteFactory = new AudioRoute.Factory() {
        @Override
        public AudioRoute create(@AudioRoute.AudioRouteType int type, String bluetoothAddress,
                                 AudioManager audioManager) {
            return new AudioRoute(type, bluetoothAddress, null);
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mWiredHeadsetManager.isPluggedIn()).thenReturn(false);
        when(mEarpieceDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        when(mAudioManager.getDevices(eq(AudioManager.GET_DEVICES_OUTPUTS))).thenReturn(
                new AudioDeviceInfo[] {
                        mEarpieceDeviceInfo
                });
        when(mAudioManager.getPreferredDeviceForStrategy(nullable(AudioProductStrategy.class)))
                .thenReturn(null);
        when(mAudioServiceFactory.getAudioService()).thenReturn(mAudioService);
        when(mContext.getAttributionTag()).thenReturn("");
        doNothing().when(mCallsManager).onCallAudioStateChanged(any(CallAudioState.class),
                any(CallAudioState.class));
        when(mCallsManager.getCurrentUserHandle()).thenReturn(
                new UserHandle(UserHandle.USER_SYSTEM));
        mController = new CallAudioRouteController(mContext, mCallsManager, mAudioServiceFactory,
                mAudioRouteFactory, mWiredHeadsetManager, mBluetoothRouteManager);
        mController.setAudioRouteFactory(mAudioRouteFactory);
        mController.setAudioManager(mAudioManager);
        mEarpieceRoute = new AudioRoute(AudioRoute.TYPE_EARPIECE, null, null);
        mSpeakerRoute = new AudioRoute(AudioRoute.TYPE_SPEAKER, null, null);
    }

    @After
    public void tearDown() throws Exception {
        mController.getAdapterHandler().getLooper().quit();
        mController.getAdapterHandler().getLooper().getThread().join();
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testInitializeWithEarpiece() {
        mController.initialize();
        assertEquals(mEarpieceRoute, mController.getCurrentRoute());
        assertEquals(2, mController.getAvailableRoutes().size());
        assertTrue(mController.getAvailableRoutes().contains(mSpeakerRoute));
    }

    @SmallTest
    @Test
    public void testInitializeWithoutEarpiece() {
        when(mAudioManager.getDevices(eq(AudioManager.GET_DEVICES_OUTPUTS))).thenReturn(
                new AudioDeviceInfo[] {});

        mController.initialize();
        assertEquals(mSpeakerRoute, mController.getCurrentRoute());
    }

    @SmallTest
    @Test
    public void testActivateAndRemoveBluetoothDeviceDuringCall() {
        doAnswer(invocation -> {
            mController.sendMessageWithSessionInfo(BT_AUDIO_CONNECTED, 0, BLUETOOTH_DEVICE_1);
            return true;
        }).when(mAudioManager).setCommunicationDevice(nullable(AudioDeviceInfo.class));

        mController.initialize();
        mController.setActive(true);
        mController.sendMessageWithSessionInfo(BT_DEVICE_ADDED, AudioRoute.TYPE_BLUETOOTH_SCO,
                BLUETOOTH_DEVICE_1);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, null, BLUETOOTH_DEVICES);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_PRESENT,
                AudioRoute.TYPE_BLUETOOTH_SCO, BT_ADDRESS_1);
        verify(mAudioManager, timeout(TEST_TIMEOUT)).setCommunicationDevice(
                nullable(AudioDeviceInfo.class));

        expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, BLUETOOTH_DEVICE_1, BLUETOOTH_DEVICES);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(BT_DEVICE_REMOVED, AudioRoute.TYPE_BLUETOOTH_SCO,
                BLUETOOTH_DEVICE_1);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testActiveDeactivateBluetoothDevice() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(BT_DEVICE_ADDED, AudioRoute.TYPE_BLUETOOTH_SCO,
                BLUETOOTH_DEVICE_1);

        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, BLUETOOTH_DEVICE_1, BLUETOOTH_DEVICES);
        mController.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_PRESENT,
                AudioRoute.TYPE_BLUETOOTH_SCO, BT_ADDRESS_1);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, null, BLUETOOTH_DEVICES);
        mController.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_GONE,
                AudioRoute.TYPE_BLUETOOTH_SCO);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testSwitchFocusForBluetoothDeviceSupportInbandRinging() {
        doAnswer(invocation -> {
            mController.sendMessageWithSessionInfo(BT_AUDIO_CONNECTED, 0, BLUETOOTH_DEVICE_1);
            return true;
        }).when(mAudioManager).setCommunicationDevice(nullable(AudioDeviceInfo.class));
        doAnswer(invocation -> {
            mController.sendMessageWithSessionInfo(BT_AUDIO_DISCONNECTED, 0, BLUETOOTH_DEVICE_1);
            return true;
        }).when(mAudioManager).clearCommunicationDevice();
        when(mBluetoothRouteManager.isInbandRingEnabled(eq(BLUETOOTH_DEVICE_1))).thenReturn(true);

        mController.initialize();
        mController.sendMessageWithSessionInfo(BT_DEVICE_ADDED, AudioRoute.TYPE_BLUETOOTH_SCO,
                BLUETOOTH_DEVICE_1);

        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, BLUETOOTH_DEVICE_1, BLUETOOTH_DEVICES);
        mController.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_PRESENT,
                AudioRoute.TYPE_BLUETOOTH_SCO, BT_ADDRESS_1);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
        assertFalse(mController.isActive());

        mController.sendMessageWithSessionInfo(SWITCH_FOCUS, RINGING_FOCUS);
        verify(mAudioManager, timeout(TEST_TIMEOUT)).setCommunicationDevice(
                nullable(AudioDeviceInfo.class));
        assertTrue(mController.isActive());

        mController.sendMessageWithSessionInfo(SWITCH_FOCUS, ACTIVE_FOCUS);
        assertTrue(mController.isActive());

        mController.sendMessageWithSessionInfo(SWITCH_FOCUS, NO_FOCUS);
        verify(mAudioManager, timeout(TEST_TIMEOUT)).clearCommunicationDevice();
        assertFalse(mController.isActive());
    }

    @SmallTest
    @Test
    public void testConnectAndDisconnectWiredHeadset() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(CONNECT_WIRED_HEADSET);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(DISCONNECT_WIRED_HEADSET);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testConnectAndDisconnectDock() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(CONNECT_DOCK);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(DISCONNECT_DOCK);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testSpeakerToggle() {
        mController.initialize();
        mController.setActive(true);
        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(SPEAKER_OFF);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testSpeakerToggleWhenDockConnected() {
        mController.initialize();
        mController.setActive(true);
        mController.sendMessageWithSessionInfo(CONNECT_DOCK);
        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(SPEAKER_OFF);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testSwitchEarpiece() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(USER_SWITCH_EARPIECE);
        mController.sendMessageWithSessionInfo(SPEAKER_OFF);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testSwitchBluetooth() {
        doAnswer(invocation -> {
            mController.sendMessageWithSessionInfo(BT_AUDIO_CONNECTED, 0, BLUETOOTH_DEVICE_1);
            return true;
        }).when(mAudioManager).setCommunicationDevice(nullable(AudioDeviceInfo.class));

        mController.initialize();
        mController.setActive(true);
        mController.sendMessageWithSessionInfo(BT_DEVICE_ADDED, AudioRoute.TYPE_BLUETOOTH_SCO,
                BLUETOOTH_DEVICE_1);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, null, BLUETOOTH_DEVICES);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(USER_SWITCH_BLUETOOTH, 0,
                BLUETOOTH_DEVICE_1.getAddress());
        mController.sendMessageWithSessionInfo(BT_AUDIO_CONNECTED, 0, BLUETOOTH_DEVICE_1);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH
                        | CallAudioState.ROUTE_SPEAKER, BLUETOOTH_DEVICE_1, BLUETOOTH_DEVICES);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void tesetSwitchSpeakerAndHeadset() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(CONNECT_WIRED_HEADSET);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(USER_SWITCH_SPEAKER);
        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(USER_SWITCH_HEADSET);
        mController.sendMessageWithSessionInfo(SPEAKER_OFF);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testEnableAndDisableStreaming() {
        mController.initialize();
        mController.sendMessageWithSessionInfo(STREAMING_FORCE_ENABLED);
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_STREAMING,
                CallAudioState.ROUTE_STREAMING, null, new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(SPEAKER_ON);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(CONNECT_WIRED_HEADSET);
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        mController.sendMessageWithSessionInfo(STREAMING_FORCE_DISABLED);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }

    @SmallTest
    @Test
    public void testToggleMute() throws Exception {
        when(mAudioManager.isMasterMute()).thenReturn(false);

        mController.initialize();
        mController.setActive(true);

        mController.sendMessageWithSessionInfo(MUTE_ON);
        CallAudioState expectedState = new CallAudioState(true, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mAudioService, timeout(TEST_TIMEOUT)).setMicrophoneMute(eq(true), anyString(),
                anyInt(), anyString());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));

        when(mAudioManager.isMasterMute()).thenReturn(true);
        mController.sendMessageWithSessionInfo(MUTE_OFF);
        expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER, null,
                new HashSet<>());
        verify(mAudioService, timeout(TEST_TIMEOUT)).setMicrophoneMute(eq(false), anyString(),
                anyInt(), anyString());
        verify(mCallsManager, timeout(TEST_TIMEOUT)).onCallAudioStateChanged(
                any(CallAudioState.class), eq(expectedState));
    }
}
