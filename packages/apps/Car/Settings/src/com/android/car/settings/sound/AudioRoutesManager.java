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

import android.car.media.AudioZoneConfigurationsChangeCallback;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.SwitchAudioZoneConfigCallback;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.common.Logger;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the audio routes.
 */
public class AudioRoutesManager {
    private static final Logger LOG = new Logger(AudioRoutesManager.class);
    private Context mContext;
    private CarAudioManager mCarAudioManager;
    private LocalBluetoothManager mBluetoothManager;
    private int mAudioZone;
    private int mUsage;
    private String mActiveDeviceAddress;
    private String mFutureActiveDeviceAddress;
    private AudioZoneConfigUpdateListener mUpdateListener;
    private List<String> mAddressList;
    private Map<String, AudioRouteItem> mAudioRouteItemMap;

    /**
     * A listener for when the AudioZoneConfig is updated.
     */
    public interface AudioZoneConfigUpdateListener {
        void onAudioZoneConfigUpdated();
    }

    private final AudioZoneConfigurationsChangeCallback mAudioZoneConfigurationsChangeCallback =
            new AudioZoneConfigurationsChangeCallback() {
                @Override
                public void onAudioZoneConfigurationsChanged(
                        @NonNull List<CarAudioZoneConfigInfo> configs, int status) {
                    AudioZoneConfigurationsChangeCallback.super.onAudioZoneConfigurationsChanged(
                            configs, status);
                    if (status == CarAudioManager.CONFIG_STATUS_CHANGED) {
                        setAudioRouteActive();
                    }
                }
            };

    private final SwitchAudioZoneConfigCallback mSwitchAudioZoneConfigCallback =
            (zoneConfig, isSuccessful) -> {
                if (isSuccessful) {
                    mActiveDeviceAddress = mFutureActiveDeviceAddress;
                    mUpdateListener.onAudioZoneConfigUpdated();
                } else {
                    LOG.d("Switch audio zone failed.");
                }
            };

    public AudioRoutesManager(Context context, int usage) {
        mContext = context;
        mCarAudioManager = ((CarSettingsApplication) mContext.getApplicationContext())
                .getCarAudioManager();
        mAudioZone = ((CarSettingsApplication) mContext.getApplicationContext())
                .getMyAudioZoneId();
        mBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */ null);
        mUsage = usage;
        mAudioRouteItemMap = new ArrayMap<>();
        mAddressList = new ArrayList<>();
        mCarAudioManager.clearAudioZoneConfigsCallback();
        mCarAudioManager.setAudioZoneConfigsChangeCallback(ContextCompat.getMainExecutor(mContext),
                mAudioZoneConfigurationsChangeCallback);
        updateAudioRoutesList();
    }

    private void updateAudioRoutesList() {
        List<CarAudioZoneConfigInfo> carAudioZoneConfigInfoList =
                getCarAudioManager().getAudioZoneConfigInfos(mAudioZone);
        for (CarAudioZoneConfigInfo carAudioZoneConfigInfo : carAudioZoneConfigInfoList) {
            if (!carAudioZoneConfigInfo.isActive()) {
                continue;
            }
            List<CarVolumeGroupInfo> carVolumeGroupInfoList =
                    carAudioZoneConfigInfo.getConfigVolumeGroups();
            for (CarVolumeGroupInfo carVolumeGroupInfo : carVolumeGroupInfoList) {
                boolean isCorrectVolumeGroup = false;
                for (AudioAttributes audioAttributes : carVolumeGroupInfo.getAudioAttributes()) {
                    if (audioAttributes.getUsage() == mUsage) {
                        isCorrectVolumeGroup = true;
                        break;
                    }
                }

                if (isCorrectVolumeGroup) {
                    List<AudioDeviceAttributes> audioDeviceAttributesList =
                            carVolumeGroupInfo.getAudioDeviceAttributes();
                    for (AudioDeviceAttributes audioDeviceAttr : audioDeviceAttributesList) {
                        AudioRouteItem audioRouteItem = new AudioRouteItem(audioDeviceAttr);
                        mAddressList.add(audioRouteItem.getAddress());
                        mAudioRouteItemMap.put(audioRouteItem.getAddress(), audioRouteItem);
                    }
                }
            }
        }

        List<CachedBluetoothDevice> bluetoothDevices =
                mBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy().stream().toList();
        for (CachedBluetoothDevice bluetoothDevice : bluetoothDevices) {
            if (bluetoothDevice.isConnectedA2dpDevice()
                    && (!mAudioRouteItemMap.containsKey(bluetoothDevice.getAddress()))) {
                AudioRouteItem audioRouteItem = new AudioRouteItem(bluetoothDevice);
                mAddressList.add(audioRouteItem.getAddress());
                mAudioRouteItemMap.put(audioRouteItem.getAddress(), audioRouteItem);
            }
        }

        AudioDeviceInfo deviceInfo =
                mCarAudioManager.getOutputDeviceForUsage(mAudioZone, mUsage);
        mActiveDeviceAddress = deviceInfo.getAddress();
        mFutureActiveDeviceAddress = mActiveDeviceAddress;
        if (!mAudioRouteItemMap.containsKey(mActiveDeviceAddress)) {
            LOG.d("The active device is not in the AudioDeviceAttributes list");
        }
    }

    /**
     * Sets the {@link AudioZoneConfigUpdateListener}.
     */
    public void setUpdateListener(AudioZoneConfigUpdateListener listener) {
        mUpdateListener = listener;
    }

    public List<String> getAudioRouteList() {
        return mAddressList;
    }

    public Map<String, AudioRouteItem> getAudioRouteItemMap() {
        return mAudioRouteItemMap;
    }

    public String getActiveDeviceAddress() {
        return mActiveDeviceAddress;
    }

    public CarAudioManager getCarAudioManager() {
        return mCarAudioManager;
    }

    public void tearDown() {
        if (mCarAudioManager != null) {
            mCarAudioManager.clearAudioZoneConfigsCallback();
        }
    }

    /**
     * Update to a new audio destination of the provided address.
     */
    public AudioRouteItem updateAudioRoute(String address) {
        mFutureActiveDeviceAddress = address;
        AudioRouteItem audioRouteItem = mAudioRouteItemMap.get(address);
        if (audioRouteItem.getAudioRouteType() == TYPE_BLUETOOTH_A2DP) {
            CachedBluetoothDevice bluetoothDevice = audioRouteItem.getBluetoothDevice();
            bluetoothDevice.setActive();
        } else {
            setAudioRouteActive();
        }
        return audioRouteItem;
    }

    private void setAudioRouteActive() {
        List<CarAudioZoneConfigInfo> zoneConfigInfoList =
                mCarAudioManager.getAudioZoneConfigInfos(mAudioZone);
        for (CarAudioZoneConfigInfo carAudioZoneConfigInfo : zoneConfigInfoList) {
            for (CarVolumeGroupInfo carVolumeGroupInfo :
                    carAudioZoneConfigInfo.getConfigVolumeGroups()) {
                boolean hasCorrectUsage = false;
                for (AudioAttributes audioAttributes : carVolumeGroupInfo.getAudioAttributes()) {
                    if (audioAttributes.getUsage() == mUsage) {
                        hasCorrectUsage = true;
                        break;
                    }
                }

                boolean hasCorrectAddress = false;
                for (AudioDeviceAttributes audioDeviceAttributes :
                        carVolumeGroupInfo.getAudioDeviceAttributes()) {
                    if (mFutureActiveDeviceAddress.equals(audioDeviceAttributes.getAddress())) {
                        hasCorrectAddress = true;
                        break;
                    }
                }

                if (hasCorrectUsage && hasCorrectAddress) {
                    mCarAudioManager.switchAudioZoneToConfig(carAudioZoneConfigInfo,
                            ContextCompat.getMainExecutor(mContext),
                            mSwitchAudioZoneConfigCallback);
                    return;
                }
            }
        }
    }
}
