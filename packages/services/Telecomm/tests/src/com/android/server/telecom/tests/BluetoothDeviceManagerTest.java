/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.CallAudioCommunicationDeviceTracker;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class BluetoothDeviceManagerTest extends TelecomTestCase {
    private static final String DEVICE_ADDRESS_1 = "00:00:00:00:00:01";

    @Mock BluetoothRouteManager mRouteManager;
    @Mock BluetoothHeadset mBluetoothHeadset;
    @Mock BluetoothAdapter mAdapter;
    @Mock BluetoothHearingAid mBluetoothHearingAid;
    @Mock BluetoothLeAudio mBluetoothLeAudio;
    @Mock AudioManager mockAudioManager;
    @Mock AudioDeviceInfo mSpeakerInfo;

    BluetoothDeviceManager mBluetoothDeviceManager;
    BluetoothProfile.ServiceListener serviceListenerUnderTest;
    BluetoothStateReceiver receiverUnderTest;
    CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;
    ArgumentCaptor<BluetoothLeAudio.Callback> leAudioCallbacksTest;

    private BluetoothDevice device1;
    private BluetoothDevice device2;
    private BluetoothDevice device3;
    private BluetoothDevice device4;
    private BluetoothDevice device5;
    private BluetoothDevice device6;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        device1 = makeBluetoothDevice("00:00:00:00:00:01");
        // hearing aid
        device2 = makeBluetoothDevice("00:00:00:00:00:02");
        device3 = makeBluetoothDevice("00:00:00:00:00:03");
        // hearing aid
        device4 = makeBluetoothDevice("00:00:00:00:00:04");
        // le audio
        device5 = makeBluetoothDevice("00:00:00:00:00:05");
        device6 = makeBluetoothDevice("00:00:00:00:00:06");

        when(mBluetoothHearingAid.getHiSyncId(device2)).thenReturn(100L);
        when(mBluetoothHearingAid.getHiSyncId(device4)).thenReturn(100L);

        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mCommunicationDeviceTracker = new CallAudioCommunicationDeviceTracker(mContext);
        mBluetoothDeviceManager = new BluetoothDeviceManager(mContext, mAdapter,
                mCommunicationDeviceTracker, mFeatureFlags);
        mBluetoothDeviceManager.setBluetoothRouteManager(mRouteManager);
        mCommunicationDeviceTracker.setBluetoothRouteManager(mRouteManager);

        mockAudioManager = mContext.getSystemService(AudioManager.class);

        ArgumentCaptor<BluetoothProfile.ServiceListener> serviceCaptor =
                ArgumentCaptor.forClass(BluetoothProfile.ServiceListener.class);
        verify(mAdapter).getProfileProxy(eq(mContext),
                serviceCaptor.capture(), eq(BluetoothProfile.HEADSET));
        serviceListenerUnderTest = serviceCaptor.getValue();

        receiverUnderTest = new BluetoothStateReceiver(mBluetoothDeviceManager,
                mRouteManager, mCommunicationDeviceTracker, mFeatureFlags);

        mBluetoothDeviceManager.setHeadsetServiceForTesting(mBluetoothHeadset);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(mBluetoothHearingAid);

        leAudioCallbacksTest =
                         ArgumentCaptor.forClass(BluetoothLeAudio.Callback.class);
        mBluetoothDeviceManager.setLeAudioServiceForTesting(mBluetoothLeAudio);
        verify(mBluetoothLeAudio).registerCallback(any(), leAudioCallbacksTest.capture());

        when(mSpeakerInfo.getType()).thenReturn(TYPE_BUILTIN_SPEAKER);
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testSingleDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testAddDeviceFailsWhenServicesAreNull() {
        mBluetoothDeviceManager.setHeadsetServiceForTesting(null);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(null);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testMultiDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mBluetoothLeAudio.getGroupId(device5)).thenReturn(1);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 2);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(2)).thenReturn(device6);
        when(mBluetoothLeAudio.getGroupId(device6)).thenReturn(1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        assertEquals(3, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testLeAudioMissedGroupCallbackBeforeConnected() {
        /* This should be called on connection state changed */
        when(mBluetoothLeAudio.getGroupId(device5)).thenReturn(1);
        when(mBluetoothLeAudio.getGroupId(device6)).thenReturn(1);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(1, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testLeAudioGroupAvailableBeforeConnect() {
        /* Device is known (e.g. from storage) */
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        /* Make sure getGroupId is not called for known devices */
        verify(mBluetoothLeAudio, never()).getGroupId(device5);
        verify(mBluetoothLeAudio, never()).getGroupId(device6);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(1, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testHearingAidDedup() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device4,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        assertEquals(3, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(2, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testLeAudioDedup() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothProfile.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothProfile.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothProfile.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        when(mBluetoothLeAudio.getGroupId(device5)).thenReturn(1);
        when(mBluetoothLeAudio.getGroupId(device6)).thenReturn(1);
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(2, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testHeadsetServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEADSET);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        verify(mRouteManager).onDeviceLost(device1.getAddress());
        verify(mRouteManager).onDeviceLost(device3.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device2.getAddress());
        assertNull(mBluetoothDeviceManager.getBluetoothHeadset());
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEARING_AID);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        verify(mRouteManager).onDeviceLost(device2.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device1.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device3.getAddress());
        assertNull(mBluetoothDeviceManager.getBluetoothHearingAid());
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testLeAudioServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.LE_AUDIO);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        verify(mRouteManager).onDeviceLost(device5.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device1.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device3.getAddress());
        assertNull(mBluetoothDeviceManager.getLeAudioService());
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidChangesIgnoredWhenNotInCall() {
        receiverUnderTest.setIsInCall(false);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        Intent activeDeviceChangedIntent =
                new Intent(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
        activeDeviceChangedIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device2);
        receiverUnderTest.onReceive(mContext, activeDeviceChangedIntent);

        verify(mRouteManager).onActiveDeviceChanged(device2,
                BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID);
        verify(mRouteManager, never()).sendMessage(BluetoothRouteManager.BT_AUDIO_IS_ON);
    }

    @SmallTest
    @Test
    public void testLeAudioGroupChangesIgnoredWhenNotInCall() {
        receiverUnderTest.setIsInCall(false);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        Intent activeDeviceChangedIntent =
                        new Intent(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        activeDeviceChangedIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device5);
        receiverUnderTest.onReceive(mContext, activeDeviceChangedIntent);

        verify(mRouteManager).onActiveDeviceChanged(device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO);
        verify(mRouteManager, never()).sendMessage(BluetoothRouteManager.BT_AUDIO_IS_ON);
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHeadset() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                    eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device1.getAddress(), false);
        verify(mAdapter).setActiveDevice(eq(device1),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL));
        mBluetoothDeviceManager.disconnectAudio();
        verify(mBluetoothHeadset).disconnectAudio();
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHearingAid() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        AudioDeviceInfo mockAudioDeviceInfo = createMockAudioDeviceInfo(device5.getAddress(),
                AudioDeviceInfo.TYPE_HEARING_AID);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(mockAudioDeviceInfo)))
                .thenReturn(true);

        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));

        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device5,
                BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));

        when(mockAudioManager.getCommunicationDevice()).thenReturn(mockAudioDeviceInfo);
        mBluetoothDeviceManager.disconnectAudio();
        verify(mockAudioManager, atLeastOnce()).clearCommunicationDevice();
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioLeAudio() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        AudioDeviceInfo mockAudioDeviceInfo = createMockAudioDeviceInfo(device5.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                        .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(mockAudioDeviceInfo))
                       .thenReturn(true);

        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_AUDIO));

        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device5,
                BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        mBluetoothDeviceManager.disconnectAudio();
        verify(mockAudioManager, atLeastOnce()).clearCommunicationDevice();
    }

    @SmallTest
    @Test
    public void testConnectEarbudLeAudio() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));

        when(mAdapter.getActiveDevices(eq(BluetoothProfile.LE_AUDIO)))
                .thenReturn(Arrays.asList(device5, device6));

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        mBluetoothDeviceManager.connectAudio(device6.getAddress(), false);
        verify(mAdapter).setActiveDevice(device6, BluetoothAdapter.ACTIVE_DEVICE_ALL);
    }

    @SmallTest
    @Test
    public void testConnectMultipleLeAudioDevices() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device1, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device2, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        List<AudioDeviceInfo> devices = new ArrayList<>();
        AudioDeviceInfo leAudioDevice1 = createMockAudioDeviceInfo(device1.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        AudioDeviceInfo leAudioDevice2 = createMockAudioDeviceInfo(device2.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        devices.add(leAudioDevice1);
        devices.add(leAudioDevice2);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(any(AudioDeviceInfo.class)))
                .thenReturn(true);

        // Connect LE audio device
        mBluetoothDeviceManager.connectAudio(device1.getAddress(), false);
        verify(mAdapter).setActiveDevice(device1, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        // Verify that we set the communication device for device 1
        verify(mockAudioManager).setCommunicationDevice(leAudioDevice1);

        // Change active device to other LE audio device
        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device2,
                BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        // Verify call to clearLeAudioCommunicationDevice
        verify(mRouteManager).onAudioLost(eq(DEVICE_ADDRESS_1));
        // Verify that we set the communication device for device2
        verify(mockAudioManager).setCommunicationDevice(leAudioDevice2);
    }

    @SmallTest
    @Test
    public void testClearCommunicationDeviceOnActiveDeviceChange() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        receiverUnderTest.setIsInCall(true);

        List<AudioDeviceInfo> devices = new ArrayList<>();
        AudioDeviceInfo leAudioDevice1 = createMockAudioDeviceInfo(device1.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        devices.add(leAudioDevice1);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(any(AudioDeviceInfo.class)))
                .thenReturn(true);

        // Pretend that the speaker device is currently the requested device set for communication.
        // This test ensures that the set/clear communication logic for audio switching in/out of BT
        // is properly working when the receiver processes an active device change intent.
        mCommunicationDeviceTracker.setTestCommunicationDevice(TYPE_BUILTIN_SPEAKER);

        // Notify that LE audio device has been turned on
        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device1,
                BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        // Verify call to clear speaker communication device
        verify(mockAudioManager).clearCommunicationDevice();
        // Verify that LE audio communication device was set after clearing the speaker device
        verify(mockAudioManager).setCommunicationDevice(leAudioDevice1);
    }

    @SmallTest
    @Test
    public void testConnectDualModeEarbud() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        receiverUnderTest.setIsInCall(true);

        // LE Audio earbuds connected
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        // HFP device connected
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        AudioDeviceInfo mockAudioDevice5Info = createMockAudioDeviceInfo(device5.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        AudioDeviceInfo mockAudioDevice6Info = createMockAudioDeviceInfo(device6.getAddress(),
                AudioDeviceInfo.TYPE_BLE_HEADSET);
        when(mockAudioDevice5Info.getType()).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET);
        when(mockAudioDevice6Info.getType()).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDevice5Info);
        devices.add(mockAudioDevice6Info);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(mockAudioDevice5Info))
                .thenReturn(true);

        Bundle hfpPreferred = new Bundle();
        hfpPreferred.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.HEADSET);
        Bundle leAudioPreferred = new Bundle();
        leAudioPreferred.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.LE_AUDIO);

        // TEST 1: LE Audio preferred for DUPLEX
        when(mAdapter.getPreferredAudioProfiles(device5)).thenReturn(leAudioPreferred);
        when(mAdapter.getPreferredAudioProfiles(device6)).thenReturn(leAudioPreferred);
        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter, times(1)).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        verify(mockAudioManager).setCommunicationDevice(mockAudioDevice5Info);

        when(mAdapter.getActiveDevices(eq(BluetoothProfile.LE_AUDIO)))
                .thenReturn(Arrays.asList(device5, device6));

        // Check disconnect during a call
        devices.remove(mockAudioDevice5Info);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeRemoved(device5, 1);

        mBluetoothDeviceManager.connectAudio(device6.getAddress(), false);
        verify(mAdapter).setActiveDevice(device6, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));
        verify(mockAudioManager, times(1)).clearCommunicationDevice();

        // Reconnect other LE Audio earbud
        devices.add(mockAudioDevice5Info);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);

        // Disconnects audio
        mBluetoothDeviceManager.disconnectAudio();
        verify(mockAudioManager, times(2)).clearCommunicationDevice();
        verify(mBluetoothHeadset, times(1)).disconnectAudio();

        // TEST 2: HFP preferred for DUPLEX
        when(mAdapter.getPreferredAudioProfiles(device5)).thenReturn(hfpPreferred);
        when(mAdapter.getPreferredAudioProfiles(device6)).thenReturn(hfpPreferred);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL);
        verify(mAdapter, times(1)).setActiveDevice(device5,
                BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset).connectAudio();
        mBluetoothDeviceManager.disconnectAudio();
        verify(mBluetoothHeadset, times(2)).disconnectAudio();
    }

    @SmallTest
    @Test
    public void testClearHearingAidCommunicationDeviceLegacy() {
        assertClearHearingAidOrLeCommunicationDevice(false, AudioDeviceInfo.TYPE_HEARING_AID);
    }

    @SmallTest
    @Test
    public void testClearHearingAidCommunicationDeviceWithFlag() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        assertClearHearingAidOrLeCommunicationDevice(true, AudioDeviceInfo.TYPE_HEARING_AID);
    }

    @SmallTest
    @Test
    public void testClearLeAudioCommunicationDeviceLegacy() {
        assertClearHearingAidOrLeCommunicationDevice(false, AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    @SmallTest
    @Test
    public void testClearLeAudioCommunicationDeviceWithFlag() {
        when(mFeatureFlags.callAudioCommunicationDeviceRefactor()).thenReturn(true);
        assertClearHearingAidOrLeCommunicationDevice(true, AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    @SmallTest
    @Test
    public void testConnectedDevicesDoNotContainDuplicateDevices() {
        BluetoothDevice hfpDevice = mock(BluetoothDevice.class);
        when(hfpDevice.getAddress()).thenReturn("00:00:00:00:00:00");
        when(hfpDevice.getType()).thenReturn(BluetoothDeviceManager.DEVICE_TYPE_HEADSET);
        BluetoothDevice leDevice = mock(BluetoothDevice.class);
        when(hfpDevice.getAddress()).thenReturn("00:00:00:00:00:00");
        when(hfpDevice.getType()).thenReturn(BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO);

        mBluetoothDeviceManager.onDeviceConnected(hfpDevice,
                BluetoothDeviceManager.DEVICE_TYPE_HEADSET);
        mBluetoothDeviceManager.onDeviceConnected(leDevice,
                BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO);

        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testInBandRingingEnabledForLeDevice() {
        when(mBluetoothHeadset.isInbandRingingEnabled()).thenReturn(false);
        when(mBluetoothLeAudio.isInbandRingtoneEnabled(1)).thenReturn(true);
        when(mBluetoothLeAudio.getGroupId(eq(device3))).thenReturn(1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device3, 1);
        when(mRouteManager.getBluetoothAudioConnectedDevice()).thenReturn(device3);
        when(mRouteManager.isCachedLeAudioDevice(eq(device3))).thenReturn(true);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device3);
        when(mRouteManager.getMostRecentlyReportedActiveDevice()).thenReturn(device3);
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertTrue(mBluetoothDeviceManager.isInbandRingingEnabled());
    }

    private void assertClearHearingAidOrLeCommunicationDevice(
            boolean flagEnabled, int device_type
    ) {
        AudioDeviceInfo mockAudioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mockAudioDeviceInfo.getAddress()).thenReturn(DEVICE_ADDRESS_1);
        when(mockAudioDeviceInfo.getType()).thenReturn(device_type);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(mockAudioDeviceInfo)))
                .thenReturn(true);

        if (flagEnabled) {
            BluetoothDevice btDevice = device_type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    ? device1 : null;
            mCommunicationDeviceTracker.setCommunicationDevice(device_type, btDevice);
        } else {
            if (device_type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                mBluetoothDeviceManager.setLeAudioCommunicationDevice();
            } else {
                mBluetoothDeviceManager.setHearingAidCommunicationDevice();
            }
        }
        when(mockAudioManager.getCommunicationDevice()).thenReturn(mSpeakerInfo);
        if (flagEnabled) {
            mCommunicationDeviceTracker.clearCommunicationDevice(device_type);
            assertFalse(mCommunicationDeviceTracker.isAudioDeviceSetForType(device_type));
        } else {
            if (device_type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                mBluetoothDeviceManager.clearLeAudioCommunicationDevice();
                assertFalse(mBluetoothDeviceManager.isLeAudioCommunicationDevice());
            } else {
                mBluetoothDeviceManager.clearHearingAidCommunicationDevice();
                assertFalse(mBluetoothDeviceManager.isHearingAidSetAsCommunicationDevice());
            }
        }
        verify(mRouteManager).onAudioLost(eq(DEVICE_ADDRESS_1));
    }

    private AudioDeviceInfo createMockAudioDeviceInfo(String address, int audioType) {
        AudioDeviceInfo mockAudioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mockAudioDeviceInfo.getType()).thenReturn(audioType);
        if (address != null) {
            when(mockAudioDeviceInfo.getAddress()).thenReturn(address);
        }
        return mockAudioDeviceInfo;
    }

    private Intent buildConnectionActionIntent(int state, BluetoothDevice device, int deviceType) {
        String intentString;

        switch (deviceType) {
            case BluetoothDeviceManager.DEVICE_TYPE_HEADSET:
                intentString = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID:
                intentString = BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO:
                intentString = BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED;
                break;
            default:
                return null;
        }

        Intent i = new Intent(intentString);
        i.putExtra(BluetoothHeadset.EXTRA_STATE, state);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        return i;
    }


    private Intent buildActiveDeviceChangeActionIntent(BluetoothDevice device, int deviceType) {
        String intentString;

        switch (deviceType) {
            case BluetoothDeviceManager.DEVICE_TYPE_HEADSET:
                intentString = BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID:
                intentString = BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO:
                intentString = BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED;
                break;
            default:
                return null;
        }

        Intent i = new Intent(intentString);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        return i;
    }

    private BluetoothDevice makeBluetoothDevice(String address) {
        Parcel p1 = Parcel.obtain();
        p1.writeString(address);
        p1.setDataPosition(0);
        BluetoothDevice device = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
        return device;
    }
}
