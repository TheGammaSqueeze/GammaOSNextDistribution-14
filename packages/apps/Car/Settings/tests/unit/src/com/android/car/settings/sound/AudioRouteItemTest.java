/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.settings.sound;

import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.media.AudioDeviceAttributes;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AudioRouteItemTest {

    @Mock
    private AudioDeviceAttributes mAudioDeviceAttributes;
    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void init_withAudioDeviceAttributes() {
        String name = "audio";
        String address = "audioAddress";
        when(mAudioDeviceAttributes.getName()).thenReturn(name);
        when(mAudioDeviceAttributes.getAddress()).thenReturn(address);
        AudioRouteItem audioRouteItem = new AudioRouteItem(mAudioDeviceAttributes);

        assertThat(audioRouteItem.getName()).isEqualTo(name);
        assertThat(audioRouteItem.getAddress()).isEqualTo(address);
        assertThat(audioRouteItem.getAudioRouteType()).isEqualTo(TYPE_BUS);
        assertThat(audioRouteItem.getAudioDeviceAttributes()).isEqualTo(mAudioDeviceAttributes);
        assertThat(audioRouteItem.getBluetoothDevice()).isNull();
    }

    @Test
    public void init_withCachedBluetoothDevice() {
        String name = "bluetooth";
        String address = "bluetoothAddress";
        when(mCachedBluetoothDevice.getName()).thenReturn(name);
        when(mCachedBluetoothDevice.getAddress()).thenReturn(address);
        AudioRouteItem audioRouteItem = new AudioRouteItem(mCachedBluetoothDevice);

        assertThat(audioRouteItem.getName()).isEqualTo(name);
        assertThat(audioRouteItem.getAddress()).isEqualTo(address);
        assertThat(audioRouteItem.getAudioRouteType()).isEqualTo(TYPE_BLUETOOTH_A2DP);
        assertThat(audioRouteItem.getAudioDeviceAttributes()).isNull();
        assertThat(audioRouteItem.getBluetoothDevice()).isEqualTo(mCachedBluetoothDevice);
    }
}
