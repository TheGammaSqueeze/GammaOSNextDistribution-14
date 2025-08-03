/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.server.telecom.tests.BluetoothRouteManagerTest.DEVICE1;
import static com.android.server.telecom.tests.BluetoothRouteManagerTest.DEVICE2;
import static com.android.server.telecom.tests.BluetoothRouteManagerTest.DEVICE3;
import static com.android.server.telecom.tests.BluetoothRouteManagerTest.executeRoutingAction;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.ContentResolver;
import android.telecom.Log;

import androidx.test.filters.SmallTest;

import com.android.internal.os.SomeArgs;
import com.android.server.telecom.CallAudioCommunicationDeviceTracker;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class BluetoothRouteTransitionTests extends TelecomTestCase {
    private enum ListenerUpdate {
        DEVICE_LIST_CHANGED, ACTIVE_DEVICE_PRESENT, ACTIVE_DEVICE_GONE,
        AUDIO_CONNECTING, AUDIO_CONNECTED, AUDIO_DISCONNECTED, UNEXPECTED_STATE_CHANGE
    }

    private static class BluetoothRouteTestParametersBuilder {
        private String name;
        private String initialBluetoothState;
        private BluetoothDevice initialDevice;
        private BluetoothDevice audioOnDevice;
        private int messageType;
        private BluetoothDevice messageDevice;
        private ListenerUpdate[] expectedListenerUpdates;
        private int expectedBluetoothInteraction;
        private BluetoothDevice expectedConnectionDevice;
        private String expectedFinalStateName;
        private BluetoothDevice[] connectedDevices;
        // the active device as returned by BluetoothAdapter#getActiveDevices
        private BluetoothDevice activeDevice = null;
        private List<BluetoothDevice> hearingAidBtDevices = Collections.emptyList();
        private List<BluetoothDevice> leAudioDevices = Collections.emptyList();

        public BluetoothRouteTestParametersBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setInitialBluetoothState(
                String initialBluetoothState) {
            this.initialBluetoothState = initialBluetoothState;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setInitialDevice(BluetoothDevice
                initialDevice) {
            this.initialDevice = initialDevice;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setMessageType(int messageType) {
            this.messageType = messageType;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setMessageDevice(BluetoothDevice messageDevice) {
            this.messageDevice = messageDevice;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setExpectedListenerUpdates(
                ListenerUpdate... expectedListenerUpdates) {
            this.expectedListenerUpdates = expectedListenerUpdates;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setExpectedBluetoothInteraction(
                int expectedBluetoothInteraction) {
            this.expectedBluetoothInteraction = expectedBluetoothInteraction;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setExpectedConnectionDevice(
                BluetoothDevice expectedConnectionDevice) {
            this.expectedConnectionDevice = expectedConnectionDevice;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setExpectedFinalStateName(
                String expectedFinalStateName) {
            this.expectedFinalStateName = expectedFinalStateName;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setConnectedDevices(
                BluetoothDevice... connectedDevices) {
            this.connectedDevices = connectedDevices;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setAudioOnDevice(BluetoothDevice device) {
            this.audioOnDevice = device;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setActiveDevice(BluetoothDevice device) {
            this.activeDevice = device;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setHearingAidBtDevices(
                List<BluetoothDevice> hearingAidBtDevices) {
            this.hearingAidBtDevices = hearingAidBtDevices;
            return this;
        }

        public BluetoothRouteTestParametersBuilder setLeAudioDevices(
                List<BluetoothDevice> leAudioDevices) {
            this.leAudioDevices = leAudioDevices;
            return this;
        }

        public BluetoothRouteTestParameters build() {
            return new BluetoothRouteTestParameters(name,
                    initialBluetoothState,
                    initialDevice,
                    messageType,
                    expectedListenerUpdates,
                    expectedBluetoothInteraction,
                    expectedConnectionDevice,
                    expectedFinalStateName,
                    connectedDevices,
                    messageDevice,
                    audioOnDevice,
                    activeDevice,
                    hearingAidBtDevices,
                    leAudioDevices);

        }
    }

    private static class BluetoothRouteTestParameters {
        public String name;
        public String initialBluetoothState; // One of the state names or prefixes from BRM.
        public BluetoothDevice initialDevice; // null if we start from AudioOff
        public BluetoothDevice audioOnDevice; // The device (if any) that is active
        public int messageType; // Any of the commands from the state machine
        public BluetoothDevice messageDevice; // The device that should be specified in the message.
        public ListenerUpdate[] expectedListenerUpdates; // what the listener should expect.
        // NONE, CONNECT, CONNECT_SWITCH_DEVICE or DISCONNECT
        public int expectedBluetoothInteraction;
        public BluetoothDevice expectedConnectionDevice; // Expected device to connect to.
        public String expectedFinalStateName; // Expected name of the final state.
        public BluetoothDevice[] connectedDevices; // array of connected devices
        // the active device as returned by BluetoothAdapter#getActiveDevices
        private BluetoothDevice activeDevice = null;
        private List<BluetoothDevice> hearingAidBtDevices;
        private List<BluetoothDevice> leAudioDevices;

        public BluetoothRouteTestParameters(String name, String initialBluetoothState,
                BluetoothDevice initialDevice, int messageType, ListenerUpdate[]
                expectedListenerUpdates, int expectedBluetoothInteraction, BluetoothDevice
                expectedConnectionDevice, String expectedFinalStateName,
                BluetoothDevice[] connectedDevices, BluetoothDevice messageDevice,
                BluetoothDevice audioOnDevice, BluetoothDevice activeDevice,
                List<BluetoothDevice> hearingAidBtDevices, List<BluetoothDevice> leAudioDevices) {
            this.name = name;
            this.initialBluetoothState = initialBluetoothState;
            this.initialDevice = initialDevice;
            this.messageType = messageType;
            this.expectedListenerUpdates = expectedListenerUpdates;
            this.expectedBluetoothInteraction = expectedBluetoothInteraction;
            this.expectedConnectionDevice = expectedConnectionDevice;
            this.expectedFinalStateName = expectedFinalStateName;
            this.connectedDevices = connectedDevices;
            this.messageDevice = messageDevice;
            this.audioOnDevice = audioOnDevice;
            this.activeDevice = activeDevice;
            this.hearingAidBtDevices = hearingAidBtDevices;
            this.leAudioDevices = leAudioDevices;
        }

        @Override
        public String toString() {
            String expectedListenerUpdatesStr = expectedListenerUpdates == null ? ""
                    : Arrays.stream(expectedListenerUpdates).map(ListenerUpdate::name)
                            .collect(Collectors.joining(","));
            return "BluetoothRouteTestParameters{" +
                    "name='" + name + '\'' +
                    ", initialBluetoothState='" + initialBluetoothState + '\'' +
                    ", initialDevice=" + initialDevice +
                    ", messageType=" + messageType +
                    ", messageDevice='" + messageDevice + '\'' +
                    ", expectedListenerUpdate='" + expectedListenerUpdatesStr + '\'' +
                    ", expectedBluetoothInteraction=" + expectedBluetoothInteraction +
                    ", expectedConnectionDevice='" + expectedConnectionDevice + '\'' +
                    ", expectedFinalStateName='" + expectedFinalStateName + '\'' +
                    ", connectedDevices=" + Arrays.toString(connectedDevices) +
                    ", activeDevice='" + activeDevice + '\'' +
                    ", hearingAidBtDevices ='" + hearingAidBtDevices + '\'' +
                    ", leAudioDevices ='" + leAudioDevices + '\'' +
                    '}';
        }
    }

    private static final int NONE = 1;
    private static final int CONNECT = 2;
    private static final int DISCONNECT = 3;
    private static final int CONNECT_SWITCH_DEVICE = 4;

    private static final int TEST_TIMEOUT = 1000;

    private final BluetoothRouteTestParameters mParams;
    @Mock private BluetoothDeviceManager mDeviceManager;
    @Mock private BluetoothAdapter mBluetoothAdapter;
    @Mock private BluetoothHeadset mBluetoothHeadset;
    @Mock private BluetoothHearingAid mBluetoothHearingAid;
    @Mock private BluetoothLeAudio mBluetoothLeAudio;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private BluetoothRouteManager.BluetoothStateListener mListener;
    @Mock private CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mDeviceManager.getBluetoothAdapter()).thenReturn(mBluetoothAdapter);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public BluetoothRouteTransitionTests(BluetoothRouteTestParameters params) {
        mParams = params;
    }

    @Test
    @SmallTest
    public void testTransitions() {
        BluetoothRouteManager sm = setupStateMachine(
                mParams.initialBluetoothState, mParams.initialDevice);

        int deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEADSET;
        if (mParams.hearingAidBtDevices.contains(mParams.messageDevice)) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID;
        } else if (mParams.hearingAidBtDevices.contains(mParams.messageDevice)) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO;
        }

        setupConnectedDevices(mParams.connectedDevices,
                mParams.audioOnDevice, mParams.activeDevice);
        sm.setActiveDeviceCacheForTesting(mParams.activeDevice, deviceType);
        if (mParams.initialDevice != null) {
            doAnswer(invocation -> {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = Log.createSubsession();
                args.arg2 = mParams.initialDevice.getAddress();
                when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEADSET)))
                    .thenReturn(Arrays.asList((BluetoothDevice) null));
                sm.sendMessage(BluetoothRouteManager.BT_AUDIO_LOST, args);
                return BluetoothStatusCodes.SUCCESS;
            }).when(mDeviceManager).disconnectAudio();
        }

        // Go through the utility methods for these two messages
        if (mParams.messageType == BluetoothRouteManager.NEW_DEVICE_CONNECTED) {
            sm.onDeviceAdded(mParams.messageDevice.getAddress());
            sm.onActiveDeviceChanged(mParams.messageDevice, deviceType);
        } else if (mParams.messageType == BluetoothRouteManager.LOST_DEVICE) {
            sm.onActiveDeviceChanged(null, deviceType);
            if (mParams.hearingAidBtDevices.contains(mParams.messageDevice)) {
                when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEARING_AID)))
                    .thenReturn(Arrays.asList(null, null));
                when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.LE_AUDIO)))
                    .thenReturn(mParams.leAudioDevices.stream()
                       .filter(device -> device != mParams.messageDevice)
                       .collect(Collectors.toList()));
            } else {
                when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEADSET)))
                    .thenReturn(Arrays.asList((BluetoothDevice) null));
            }
            sm.onDeviceLost(mParams.messageDevice.getAddress());
        } else {
            executeRoutingAction(sm, mParams.messageType,
                    mParams.messageDevice == null ? null : mParams.messageDevice.getAddress());
        }

        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        assertEquals(mParams.expectedFinalStateName, sm.getCurrentState().getName());

        for (ListenerUpdate lu : mParams.expectedListenerUpdates) {
            switch (lu) {
                case DEVICE_LIST_CHANGED:
                    verify(mListener).onBluetoothDeviceListChanged();
                    break;
                case ACTIVE_DEVICE_PRESENT:
                    verify(mListener).onBluetoothActiveDevicePresent();
                    break;
                case ACTIVE_DEVICE_GONE:
                    verify(mListener).onBluetoothActiveDeviceGone();
                    break;
                case AUDIO_CONNECTING:
                    verify(mListener).onBluetoothAudioConnecting();
                    break;
                case AUDIO_CONNECTED:
                    verify(mListener).onBluetoothAudioConnected();
                    break;
                case AUDIO_DISCONNECTED:
                    verify(mListener).onBluetoothAudioDisconnected();
                    break;
            }
        }

        switch (mParams.expectedBluetoothInteraction) {
            case NONE:
                verify(mDeviceManager, never()).connectAudio(nullable(String.class),
                    any(boolean.class));
                break;
            case CONNECT:
                verify(mDeviceManager).connectAudio(mParams.expectedConnectionDevice.getAddress(),
                    false);
                verify(mDeviceManager, never()).disconnectAudio();
                break;
            case CONNECT_SWITCH_DEVICE:
                verify(mDeviceManager).disconnectAudio();
                verify(mDeviceManager).connectAudio(mParams.expectedConnectionDevice.getAddress(),
                    true);
            break;
            case DISCONNECT:
                verify(mDeviceManager, never()).connectAudio(nullable(String.class),
                    any(boolean.class));
                verify(mDeviceManager).disconnectAudio();
                break;
        }

        sm.getHandler().removeMessages(BluetoothRouteManager.CONNECTION_TIMEOUT);
        sm.quitNow();
    }

    private void setupConnectedDevices(BluetoothDevice[] devices,
            BluetoothDevice audioOnDevice, BluetoothDevice activeDevice) {
        when(mDeviceManager.getNumConnectedDevices()).thenReturn(devices.length);
        when(mDeviceManager.getConnectedDevices()).thenReturn(Arrays.asList(devices));
        when(mBluetoothHeadset.getConnectedDevices()).thenReturn(Arrays.asList(devices));
        when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEADSET)))
            .thenReturn(Arrays.asList(activeDevice));
        when(mBluetoothHeadset.getAudioState(nullable(BluetoothDevice.class)))
                .thenReturn(BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        if (audioOnDevice != null) {
            when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEADSET)))
                .thenReturn(Arrays.asList(audioOnDevice));
            when(mBluetoothHeadset.getAudioState(audioOnDevice))
                    .thenReturn(BluetoothHeadset.STATE_AUDIO_CONNECTED);
        }
    }

    private BluetoothRouteManager setupStateMachine(String initialState,
            BluetoothDevice initialDevice) {
        resetMocks();
        when(mDeviceManager.getBluetoothHeadset()).thenReturn(mBluetoothHeadset);
        when(mDeviceManager.getBluetoothHearingAid()).thenReturn(mBluetoothHearingAid);
        when(mDeviceManager.getLeAudioService()).thenReturn(mBluetoothLeAudio);
        when(mDeviceManager.connectAudio(nullable(String.class), any(boolean.class)))
            .thenReturn(true);
        when(mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                nullable(ContentResolver.class))).thenReturn(100000L);
        when(mTimeoutsAdapter.getBluetoothPendingTimeoutMillis(
                nullable(ContentResolver.class))).thenReturn(100000L);
        BluetoothRouteManager sm = new BluetoothRouteManager(mContext,
                new TelecomSystem.SyncRoot() { }, mDeviceManager,
                mTimeoutsAdapter, mCommunicationDeviceTracker, mFeatureFlags);
        sm.setListener(mListener);
        sm.setInitialStateForTesting(initialState, initialDevice);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        resetMocks();
        return sm;
    }

    private void resetMocks() {
        clearInvocations(mDeviceManager, mListener, mBluetoothHeadset, mTimeoutsAdapter);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<BluetoothRouteTestParameters> generateTestCases() {
        List<BluetoothRouteTestParameters> result = new ArrayList<>();
        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("New device connected while audio off")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .setInitialDevice(null)
                .setConnectedDevices(DEVICE1)
                .setMessageType(BluetoothRouteManager.NEW_DEVICE_CONNECTED)
                .setMessageDevice(DEVICE1)
                .setExpectedListenerUpdates(ListenerUpdate.DEVICE_LIST_CHANGED,
                        ListenerUpdate.ACTIVE_DEVICE_PRESENT)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Nonspecific connection request while audio off with BT-active device")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .setInitialDevice(null)
                .setConnectedDevices(DEVICE2, DEVICE1)
                .setActiveDevice(DEVICE1)
                .setMessageType(BluetoothRouteManager.CONNECT_BT)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTING)
                .setExpectedBluetoothInteraction(CONNECT)
                .setExpectedConnectionDevice(DEVICE1)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX
                        + ":" + DEVICE1)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Connection to a device succeeds after pending")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setAudioOnDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_IS_ON)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE2)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device loses BT audio but remains connected. No fallback.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_LOST)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device loses BT audio but remains connected."
                        + " No fallback even though other devices available.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1, DEVICE3)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_LOST)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Switch the device that audio is being routed to")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1, DEVICE3)
                .setMessageType(BluetoothRouteManager.CONNECT_BT)
                .setMessageDevice(DEVICE3)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTING)
                .setExpectedBluetoothInteraction(CONNECT_SWITCH_DEVICE)
                .setExpectedConnectionDevice(DEVICE3)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX
                        + ":" + DEVICE3)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Switch to another device before first device has connected")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1, DEVICE3)
                .setMessageType(BluetoothRouteManager.CONNECT_BT)
                .setMessageDevice(DEVICE3)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTING)
                .setExpectedBluetoothInteraction(CONNECT_SWITCH_DEVICE)
                .setExpectedConnectionDevice(DEVICE3)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX
                        + ":" + DEVICE3)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device gets disconnected while active. No fallback.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setActiveDevice(DEVICE2)
                .setConnectedDevices()
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED, ListenerUpdate.ACTIVE_DEVICE_GONE)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device gets disconnected while active."
                        + " No fallback even though other devices available.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE3)
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedConnectionDevice(null)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Connection to DEVICE2 times out but device 1 still connected.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1)
                .setAudioOnDevice(DEVICE1)
                .setMessageType(BluetoothRouteManager.CONNECTION_TIMEOUT)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE1)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("DEVICE1 somehow becomes active when DEVICE2 is still pending.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1)
                .setAudioOnDevice(DEVICE1)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_IS_ON)
                .setMessageDevice(DEVICE1)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE1)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device gets disconnected while pending."
                        + " No fallback even though other devices available.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE3)
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Audio disconnect comes with a null device")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_LOST)
                .setMessageDevice(null)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device gets disconnected while pending. No fallback.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices()
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Device gets audio-off while in another device's audio on state")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE1)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_LOST)
                .setMessageDevice(DEVICE1)
                .setExpectedListenerUpdates(ListenerUpdate.UNEXPECTED_STATE_CHANGE)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE2)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Audio routing requests BT disconnection while a device is active")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setMessageType(BluetoothRouteManager.DISCONNECT_BT)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED)
                .setExpectedBluetoothInteraction(DISCONNECT)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Audio routing requests BT disconnection while a device is pending")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTING_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setMessageType(BluetoothRouteManager.DISCONNECT_BT)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED)
                .setExpectedBluetoothInteraction(DISCONNECT)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Bluetooth turns itself on.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .setInitialDevice(null)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setMessageType(BluetoothRouteManager.BT_AUDIO_IS_ON)
                .setMessageDevice(DEVICE3)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE3)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("Hearing aid device disconnects with headset present")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setHearingAidBtDevices(Collections.singletonList(DEVICE2))
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("connect BT to an already active device when in audio off.")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .setAudioOnDevice(DEVICE2)
                .setActiveDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setHearingAidBtDevices(Collections.singletonList(DEVICE2))
                .setMessageType(BluetoothRouteManager.CONNECT_BT)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_CONNECTED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE2)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("le audio device disconnects with hearing aid present")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE2)
                .setConnectedDevices(DEVICE2, DEVICE3)
                .setLeAudioDevices(Collections.singletonList(DEVICE2))
                .setHearingAidBtDevices(Collections.singletonList(DEVICE3))
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE2)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        result.add(new BluetoothRouteTestParametersBuilder()
                .setName("le audio device disconnects with another one connected")
                .setInitialBluetoothState(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX)
                .setInitialDevice(DEVICE1)
                .setConnectedDevices(DEVICE1, DEVICE2, DEVICE3)
                .setHearingAidBtDevices(Collections.singletonList(DEVICE3))
                .setLeAudioDevices(Arrays.asList(DEVICE1, DEVICE2))
                .setMessageType(BluetoothRouteManager.LOST_DEVICE)
                .setMessageDevice(DEVICE1)
                .setExpectedListenerUpdates(ListenerUpdate.AUDIO_DISCONNECTED,
                        ListenerUpdate.DEVICE_LIST_CHANGED)
                .setExpectedBluetoothInteraction(NONE)
                .setExpectedFinalStateName(BluetoothRouteManager.AUDIO_OFF_STATE_NAME)
                .build());

        return result;
    }
}
