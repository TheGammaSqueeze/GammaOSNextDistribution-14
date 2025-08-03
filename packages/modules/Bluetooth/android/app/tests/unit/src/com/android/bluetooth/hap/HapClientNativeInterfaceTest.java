/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.hap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HapClientNativeInterfaceTest {
    private static final byte[] TEST_DEVICE_ADDRESS =
            new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    @Mock
    HapClientService mService;

    private HapClientNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        HapClientService.setHapClient(mService);
        mNativeInterface = HapClientNativeInterface.getInstance();
    }

    @After
    public void tearDown() {
        HapClientService.setHapClient(null);
    }

    @Test
    public void onConnectionStateChanged() {
        int state = BluetoothProfile.STATE_CONNECTED;
        mNativeInterface.onConnectionStateChanged(state, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        assertThat(event.getValue().valueInt1).isEqualTo(state);
    }

    @Test
    public void onDeviceAvailable() {
        int features = 1;
        mNativeInterface.onDeviceAvailable(TEST_DEVICE_ADDRESS, features);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        assertThat(event.getValue().valueInt1).isEqualTo(features);
    }

    @Test
    public void onFeaturesUpdate() {
        int features = 1;
        mNativeInterface.onFeaturesUpdate(TEST_DEVICE_ADDRESS, features);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_DEVICE_FEATURES);
        assertThat(event.getValue().valueInt1).isEqualTo(features);
    }

    @Test
    public void onActivePresetSelected() {
        int presetIndex = 0;
        mNativeInterface.onActivePresetSelected(TEST_DEVICE_ADDRESS, presetIndex);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECTED);
        assertThat(event.getValue().valueInt1).isEqualTo(presetIndex);
    }

    @Test
    public void onActivePresetGroupSelected() {
        int groupId = 1;
        int presetIndex = 0;
        mNativeInterface.onActivePresetGroupSelected(groupId, presetIndex);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECTED);
        assertThat(event.getValue().valueInt1).isEqualTo(presetIndex);
        assertThat(event.getValue().valueInt2).isEqualTo(groupId);
    }


    @Test
    public void onActivePresetSelectError() {
        int resultCode = -1;
        mNativeInterface.onActivePresetSelectError(TEST_DEVICE_ADDRESS, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECT_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
    }

    @Test
    public void onActivePresetGroupSelectError() {
        int groupId = 1;
        int resultCode = -2;
        mNativeInterface.onActivePresetGroupSelectError(groupId, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECT_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
        assertThat(event.getValue().valueInt2).isEqualTo(groupId);
    }

    @Test
    public void onPresetInfo() {
        int infoReason = HapClientStackEvent.PRESET_INFO_REASON_ALL_PRESET_INFO;
        BluetoothHapPresetInfo[] presets =
                {new BluetoothHapPresetInfo.Builder(0x01, "onPresetInfo")
                        .setWritable(true)
                        .setAvailable(false)
                        .build()};
        mNativeInterface.onPresetInfo(TEST_DEVICE_ADDRESS, infoReason, presets);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO);
        assertThat(event.getValue().valueInt2).isEqualTo(infoReason);
        assertThat(event.getValue().valueList.toArray()).isEqualTo(presets);
    }

    @Test
    public void onGroupPresetInfo() {
        int groupId = 100;
        int infoReason = HapClientStackEvent.PRESET_INFO_REASON_ALL_PRESET_INFO;
        BluetoothHapPresetInfo[] presets =
                {new BluetoothHapPresetInfo.Builder(0x01, "onPresetInfo")
                        .setWritable(true)
                        .setAvailable(false)
                        .build()};
        mNativeInterface.onGroupPresetInfo(groupId, infoReason, presets);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO);
        assertThat(event.getValue().valueInt2).isEqualTo(infoReason);
        assertThat(event.getValue().valueInt3).isEqualTo(groupId);
        assertThat(event.getValue().valueList.toArray()).isEqualTo(presets);
    }

    @Test
    public void onPresetNameSetError() {
        int presetIndex = 2;
        int resultCode = HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED;
        mNativeInterface.onPresetNameSetError(TEST_DEVICE_ADDRESS, presetIndex, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_NAME_SET_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
        assertThat(event.getValue().valueInt2).isEqualTo(presetIndex);
    }

    @Test
    public void onGroupPresetNameSetError() {
        int groupId = 5;
        int presetIndex = 2;
        int resultCode = HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED;
        mNativeInterface.onGroupPresetNameSetError(groupId, presetIndex, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_NAME_SET_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
        assertThat(event.getValue().valueInt2).isEqualTo(presetIndex);
        assertThat(event.getValue().valueInt3).isEqualTo(groupId);
    }

    @Test
    public void onPresetInfoError() {
        int presetIndex = 2;
        int resultCode = HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED;
        mNativeInterface.onPresetInfoError(TEST_DEVICE_ADDRESS, presetIndex, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
        assertThat(event.getValue().valueInt2).isEqualTo(presetIndex);
    }

    @Test
    public void onGroupPresetInfoError() {
        int groupId = 5;
        int presetIndex = 2;
        int resultCode = HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED;
        mNativeInterface.onGroupPresetInfoError(groupId, presetIndex, resultCode);

        ArgumentCaptor<HapClientStackEvent> event =
                ArgumentCaptor.forClass(HapClientStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO_ERROR);
        assertThat(event.getValue().valueInt1).isEqualTo(resultCode);
        assertThat(event.getValue().valueInt2).isEqualTo(presetIndex);
        assertThat(event.getValue().valueInt3).isEqualTo(groupId);
    }
}
