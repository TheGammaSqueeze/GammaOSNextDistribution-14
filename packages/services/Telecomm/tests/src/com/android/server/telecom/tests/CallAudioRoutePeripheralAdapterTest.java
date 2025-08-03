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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.CallAudioRoutePeripheralAdapter;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.DockManager;
import com.android.server.telecom.WiredHeadsetManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class CallAudioRoutePeripheralAdapterTest extends TelecomTestCase {
    CallAudioRoutePeripheralAdapter mAdapter;

    @Mock private CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    @Mock private BluetoothRouteManager mBluetoothRouteManager;
    @Mock private WiredHeadsetManager mWiredHeadsetManager;
    @Mock private DockManager mDockManager;
    @Mock private AsyncRingtonePlayer mRingtonePlayer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mAdapter = new CallAudioRoutePeripheralAdapter(
                mCallAudioRouteStateMachine,
                mBluetoothRouteManager,
                mWiredHeadsetManager,
                mDockManager,
                mRingtonePlayer);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testIsBluetoothAudioOn() {
        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        assertFalse(mAdapter.isBluetoothAudioOn());

        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);
        assertTrue(mAdapter.isBluetoothAudioOn());
    }

    @SmallTest
    @Test
    public void testIsHearingAidDeviceOn() {
        when(mBluetoothRouteManager.isCachedHearingAidDevice(any())).thenReturn(false);
        assertFalse(mAdapter.isHearingAidDeviceOn());

        when(mBluetoothRouteManager.isCachedHearingAidDevice(any())).thenReturn(true);
        assertTrue(mAdapter.isHearingAidDeviceOn());
    }

    @SmallTest
    @Test
    public void testIsLeAudioDeviceOn() {
        when(mBluetoothRouteManager.isCachedLeAudioDevice(any())).thenReturn(false);
        assertFalse(mAdapter.isLeAudioDeviceOn());

        when(mBluetoothRouteManager.isCachedLeAudioDevice(any())).thenReturn(true);
        assertTrue(mAdapter.isLeAudioDeviceOn());
    }

    @SmallTest
    @Test
    public void testOnBluetoothDeviceListChanged() {
        mAdapter.onBluetoothDeviceListChanged();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BLUETOOTH_DEVICE_LIST_CHANGED);
    }

    @SmallTest
    @Test
    public void testOnBluetoothActiveDevicePresent() {
        mAdapter.onBluetoothActiveDevicePresent();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_PRESENT);
    }

    @SmallTest
    @Test
    public void testOnBluetoothActiveDeviceGone() {
        mAdapter.onBluetoothActiveDeviceGone();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_ACTIVE_DEVICE_GONE);
    }

    @SmallTest
    @Test
    public void testOnBluetoothAudioConnected() {
        mAdapter.onBluetoothAudioConnected();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        verify(mRingtonePlayer).updateBtActiveState(true);
    }

    @SmallTest
    @Test
    public void testOnBluetoothAudioConnecting() {
        mAdapter.onBluetoothAudioConnecting();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_AUDIO_CONNECTED);
        verify(mRingtonePlayer).updateBtActiveState(false);
    }

    @SmallTest
    @Test
    public void testOnBluetoothAudioDisconnected() {
        mAdapter.onBluetoothAudioDisconnected();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.BT_AUDIO_DISCONNECTED);
        verify(mRingtonePlayer).updateBtActiveState(false);
    }

    @SmallTest
    @Test
    public void testOnUnexpectedBluetoothStateChange() {
        mAdapter.onUnexpectedBluetoothStateChange();
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
    }

    @SmallTest
    @Test
    public void testOnWiredHeadsetPluggedInChangedNoChange() {
        mAdapter.onWiredHeadsetPluggedInChanged(false, false);
        mAdapter.onWiredHeadsetPluggedInChanged(true, true);
        verify(mCallAudioRouteStateMachine, never()).sendMessageWithSessionInfo(anyInt());
    }

    @SmallTest
    @Test
    public void testOnWiredHeadsetPluggedInChangedPlugged() {
        mAdapter.onWiredHeadsetPluggedInChanged(false, true);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET);
    }

    @SmallTest
    @Test
    public void testOnWiredHeadsetPluggedInChangedUnplugged() {
        mAdapter.onWiredHeadsetPluggedInChanged(true, false);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET);
    }

    @SmallTest
    @Test
    public void testOnDockChangedConnected() {
        mAdapter.onDockChanged(true);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.CONNECT_DOCK);
    }

    @SmallTest
    @Test
    public void testOnDockChangedDisconnected() {
        mAdapter.onDockChanged(false);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_DOCK);
    }
}
