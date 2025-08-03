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

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.CarSettingsApplication;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AudioRoutesManagerTest {
    private static final int USAGE = USAGE_MEDIA;
    private static final int TEST_ZONE_ID = 0;
    private static final String AUDIO_DEVICE_NAME = "audio";
    private static final String AUDIO_DEVICE_ADDRESS = "audio_address";
    private static final String BT_DEVICE_NAME = "bluetooth";
    private static final String BT_DEVICE_ADDRESS = "bluetooth_address";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private MockitoSession mSession;
    private AudioRoutesManager mAudioRoutesManager;
    @Mock
    private AudioRoutesManager.AudioZoneConfigUpdateListener mUpdateListener;
    @Mock
    private CarSettingsApplication mCarSettingsApplication;
    @Mock
    private CarAudioManager mCarAudioManager;
    @Mock
    private LocalBluetoothManager mBluetoothManager;
    @Mock
    private CachedBluetoothDeviceManager mCachedBluetoothDeviceManager;
    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;
    @Mock
    private CarAudioZoneConfigInfo mCarAudioZoneConfigInfo;
    @Mock
    private CarVolumeGroupInfo mCarVolumeGroupInfo;
    @Mock
    private AudioAttributes mAudioAttributes;
    @Mock
    private AudioDeviceAttributes mAudioDeviceAttributes;
    @Mock
    private AudioDeviceAttributes mOtherAudioDeviceAttributes;
    @Mock
    private AudioDeviceInfo mAudioDeviceInfo;

    @Before
    public void setUp() throws Exception {
        initMocks();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void init_verifyAudioRouteList() {
        when(mAudioDeviceInfo.getAddress()).thenReturn(AUDIO_DEVICE_ADDRESS);
        when(mCarVolumeGroupInfo.getAudioDeviceAttributes())
                .thenReturn(new ArrayList<>(Collections.singleton(mAudioDeviceAttributes)));
        mAudioRoutesManager = new AudioRoutesManager(mContext, USAGE);
        mAudioRoutesManager.setUpdateListener(mUpdateListener);

        List<String> audioRouteList = mAudioRoutesManager.getAudioRouteList();
        assertThat(audioRouteList.size()).isEqualTo(2);
        assertThat(audioRouteList.get(0)).isEqualTo(AUDIO_DEVICE_ADDRESS);
        assertThat(audioRouteList.get(1)).isEqualTo(BT_DEVICE_ADDRESS);
    }

    @Test
    public void init_verifyAudioRouteList_repeatAddress() {
        when(mAudioDeviceInfo.getAddress()).thenReturn(AUDIO_DEVICE_ADDRESS);
        when(mCarVolumeGroupInfo.getAudioDeviceAttributes())
                .thenReturn(Arrays.asList(mAudioDeviceAttributes, mOtherAudioDeviceAttributes));
        mAudioRoutesManager = new AudioRoutesManager(mContext, USAGE);
        mAudioRoutesManager.setUpdateListener(mUpdateListener);

        List<String> audioRouteList = mAudioRoutesManager.getAudioRouteList();
        assertThat(audioRouteList.size()).isEqualTo(2);
        assertThat(audioRouteList.get(0)).isEqualTo(AUDIO_DEVICE_ADDRESS);
        assertThat(audioRouteList.get(1)).isEqualTo(BT_DEVICE_ADDRESS);
    }

    @Test
    public void init_verifyActiveDeviceAddress() {
        when(mAudioDeviceInfo.getAddress()).thenReturn(AUDIO_DEVICE_ADDRESS);
        when(mCarVolumeGroupInfo.getAudioDeviceAttributes())
                .thenReturn(new ArrayList<>(Collections.singleton(mAudioDeviceAttributes)));
        mAudioRoutesManager = new AudioRoutesManager(mContext, USAGE);
        mAudioRoutesManager.setUpdateListener(mUpdateListener);

        assertThat(mAudioRoutesManager.getActiveDeviceAddress()).isEqualTo(AUDIO_DEVICE_ADDRESS);
    }

    @Test
    public void init_verifyAudioRouteItemMap() {
        when(mAudioDeviceInfo.getAddress()).thenReturn(AUDIO_DEVICE_ADDRESS);
        when(mCarVolumeGroupInfo.getAudioDeviceAttributes())
                .thenReturn(new ArrayList<>(Collections.singleton(mAudioDeviceAttributes)));
        mAudioRoutesManager = new AudioRoutesManager(mContext, USAGE);
        mAudioRoutesManager.setUpdateListener(mUpdateListener);

        Map<String, AudioRouteItem> audioRouteItemMap = mAudioRoutesManager.getAudioRouteItemMap();
        assertThat(audioRouteItemMap.size()).isEqualTo(2);
        assertThat(audioRouteItemMap.containsKey(AUDIO_DEVICE_ADDRESS)).isTrue();
        assertThat(audioRouteItemMap.get(AUDIO_DEVICE_ADDRESS).getName())
                .isEqualTo(AUDIO_DEVICE_NAME);
        assertThat(audioRouteItemMap.get(AUDIO_DEVICE_ADDRESS).getAddress())
                .isEqualTo(AUDIO_DEVICE_ADDRESS);
        assertThat(audioRouteItemMap.get(AUDIO_DEVICE_ADDRESS).getAudioRouteType())
                .isEqualTo(TYPE_BUS);
        assertThat(audioRouteItemMap.get(AUDIO_DEVICE_ADDRESS).getAudioDeviceAttributes())
                .isEqualTo(mAudioDeviceAttributes);
        assertThat(audioRouteItemMap.get(AUDIO_DEVICE_ADDRESS).getBluetoothDevice()).isNull();

        assertThat(audioRouteItemMap.containsKey(BT_DEVICE_ADDRESS)).isTrue();
        assertThat(audioRouteItemMap.get(BT_DEVICE_ADDRESS).getName())
                .isEqualTo(BT_DEVICE_NAME);
        assertThat(audioRouteItemMap.get(BT_DEVICE_ADDRESS).getAddress())
                .isEqualTo(BT_DEVICE_ADDRESS);
        assertThat(audioRouteItemMap.get(BT_DEVICE_ADDRESS).getAudioRouteType())
                .isEqualTo(TYPE_BLUETOOTH_A2DP);
        assertThat(audioRouteItemMap.get(BT_DEVICE_ADDRESS).getAudioDeviceAttributes()).isNull();
        assertThat(audioRouteItemMap.get(BT_DEVICE_ADDRESS).getBluetoothDevice())
                .isEqualTo(mCachedBluetoothDevice);
    }

    private void initMocks() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBluetoothManager.class, withSettings().lenient()).startMocking();
        when(LocalBluetoothManager.getInstance(any(), any())).thenReturn(mBluetoothManager);
        when(mBluetoothManager.getCachedDeviceManager()).thenReturn(mCachedBluetoothDeviceManager);
        when(mCachedBluetoothDeviceManager.getCachedDevicesCopy())
                .thenReturn(Collections.singleton(mCachedBluetoothDevice));
        when(mCachedBluetoothDevice.getName()).thenReturn(BT_DEVICE_NAME);
        when(mCachedBluetoothDevice.getAddress()).thenReturn(BT_DEVICE_ADDRESS);
        when(mCachedBluetoothDevice.isConnectedA2dpDevice()).thenReturn(true);

        when(mContext.getApplicationContext()).thenReturn(mCarSettingsApplication);
        when(mCarSettingsApplication.getCarAudioManager()).thenReturn(mCarAudioManager);
        when(mCarSettingsApplication.getMyAudioZoneId()).thenReturn(TEST_ZONE_ID);
        when(mCarAudioManager.getAudioZoneConfigInfos(TEST_ZONE_ID))
                .thenReturn(new ArrayList<>(Collections.singleton(mCarAudioZoneConfigInfo)));
        when(mCarAudioZoneConfigInfo.isActive()).thenReturn(true);
        when(mCarAudioZoneConfigInfo.getConfigVolumeGroups())
                .thenReturn(new ArrayList<>(Collections.singleton(mCarVolumeGroupInfo)));
        when(mCarVolumeGroupInfo.getAudioAttributes())
                .thenReturn(new ArrayList<>(Collections.singleton(mAudioAttributes)));
        when(mAudioAttributes.getUsage()).thenReturn(USAGE);
        when(mAudioDeviceAttributes.getAddress()).thenReturn(AUDIO_DEVICE_ADDRESS);
        when(mAudioDeviceAttributes.getName()).thenReturn(AUDIO_DEVICE_NAME);
        when(mOtherAudioDeviceAttributes.getAddress()).thenReturn(BT_DEVICE_ADDRESS);
        when(mOtherAudioDeviceAttributes.getName()).thenReturn(BT_DEVICE_NAME);
        when(mCarAudioManager.getOutputDeviceForUsage(TEST_ZONE_ID, USAGE))
                .thenReturn(mAudioDeviceInfo);
    }
}
