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
 * limitations under the License.
 */

package com.google.android.car.kitchensink.audio;

import static android.R.layout.simple_spinner_dropdown_item;
import static android.R.layout.simple_spinner_item;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.CONFIG_STATUS_CHANGED;

import android.annotation.Nullable;
import android.car.feature.Flags;
import android.car.media.AudioZoneConfigurationsChangeCallback;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.SwitchAudioZoneConfigCallback;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.car.kitchensink.R;

import java.util.List;

import javax.annotation.concurrent.GuardedBy;

final class ZoneConfigSelectionController {

    private static final boolean DBG = true;
    private static final String TAG = "CAR.AUDIO.KS";
    private final Context mContext;
    private final Spinner mZoneConfigurationSpinner;
    private final LinearLayout mZoneConfigurationLayout;
    private final ArrayAdapter<CarAudioZoneConfigInfoWrapper> mZoneConfigurationAdapter;
    private final Object mLock = new Object();
    private final TextView mCurrentZoneConfigurationView;

    private final CarAudioManager mCarAudioManager;
    private final int mAudioZone;
    private final CarAudioZoneConfigSelectedListener mConfigSelectedListener;
    @Nullable
    private final CarAudioZoneConfigsUpdatedListener mConfigUpdatedListener;
    private final SwitchAudioZoneConfigCallback mSwitchAudioZoneConfigCallback =
            (zoneConfig, isSuccessful) -> {
                Log.i(TAG, "Car audio zone switching to " + zoneConfig + " successful? "
                        + isSuccessful);
                if (!isSuccessful) {
                    return;
                }
                updateSelectedAudioZoneConfig(zoneConfig, /* autoSelected= */ false);
            };
    @GuardedBy("mLock")
    private CarAudioZoneConfigInfo mZoneConfigInfoSelected;

    ZoneConfigSelectionController(View view, CarAudioManager carAudioManager, Context context,
            int audioZone, CarAudioZoneConfigSelectedListener configSelectedListener,
            @Nullable CarAudioZoneConfigsUpdatedListener configUpdatedListener) {
        mCarAudioManager = carAudioManager;
        mContext = context;
        mAudioZone = audioZone;
        mConfigSelectedListener = configSelectedListener;
        mConfigUpdatedListener = configUpdatedListener;
        mZoneConfigurationLayout = view.findViewById(R.id.audio_zone_configuration_layout);
        mCurrentZoneConfigurationView = view.findViewById(R.id.text_current_configuration);
        mZoneConfigurationSpinner = view.findViewById(R.id.zone_configuration_spinner);
        mZoneConfigurationSpinner.setEnabled(false);
        mZoneConfigurationSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        handleZoneConfigurationsSelection();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        List<CarAudioZoneConfigInfo> zoneConfigInfos = mCarAudioManager.getAudioZoneConfigInfos(
                mAudioZone);

        Button zoneConfigSwitchButton = view.findViewById(
                R.id.switch_zone_configuration_key_event_button);
        zoneConfigSwitchButton.setOnClickListener((v) -> switchToZoneConfigSelected());

        CarAudioZoneConfigInfoWrapper[] zoneConfigArray =
                new CarAudioZoneConfigInfoWrapper[zoneConfigInfos.size()];
        for (int index = 0; index < zoneConfigArray.length; index++) {
            zoneConfigArray[index] = new CarAudioZoneConfigInfoWrapper(zoneConfigInfos.get(index));
        }
        mZoneConfigurationAdapter = new ArrayAdapter<>(mContext, simple_spinner_item,
                zoneConfigArray) {
            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                    ViewGroup parent) {
                View v = super.getDropDownView(position, /* convertView= */ null, parent);
                CarAudioZoneConfigInfo info = getItem(position).getZoneConfigInfo();
                CarAudioZoneConfigInfo updatedInfo = getUpdatedConfigInfo(info);
                if (Flags.carAudioDynamicDevices()) {
                    if (!updatedInfo.isActive()) {
                        v.setBackgroundColor(Color.LTGRAY);
                    }
                    if (updatedInfo.isSelected()) {
                        v.setBackgroundColor(Color.CYAN);
                    }
                }
                return v;
            }
        };
        mZoneConfigurationAdapter.setDropDownViewResource(simple_spinner_dropdown_item);

        if (!mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            mZoneConfigurationLayout.setVisibility(View.GONE);
            return;
        }

        mZoneConfigurationLayout.setVisibility(View.VISIBLE);

        CarAudioZoneConfigInfo currentZoneConfigInfo =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(mAudioZone);
        mCurrentZoneConfigurationView.setText(currentZoneConfigInfo.getName());
        synchronized (mLock) {
            mZoneConfigInfoSelected = currentZoneConfigInfo;
        }
        mZoneConfigurationSpinner.setAdapter(mZoneConfigurationAdapter);
        mZoneConfigurationSpinner.setEnabled(true);
        int selected = mZoneConfigurationAdapter.getPosition(
                new CarAudioZoneConfigInfoWrapper(currentZoneConfigInfo));
        mZoneConfigurationSpinner.setSelection(selected);

        if (Flags.carAudioDynamicDevices()) {
            mCarAudioManager.setAudioZoneConfigsChangeCallback(mContext.getMainExecutor(),
                    new AudioZoneConfigurationsChangeCallback() {
                        @Override
                        public void onAudioZoneConfigurationsChanged(
                                List<CarAudioZoneConfigInfo> configs, int status) {
                            handleAudioZoneConfigsUpdated(configs, status);
                        }
                    });
        }
    }

    private void switchToZoneConfigSelected() {
        CarAudioZoneConfigInfo zoneConfigInfoSelected;
        synchronized (mLock) {
            zoneConfigInfoSelected = mZoneConfigInfoSelected;
        }
        if (DBG) {
            Log.d(TAG, "Switch to zone configuration selected: " + zoneConfigInfoSelected);
        }
        switchToAudioConfiguration(zoneConfigInfoSelected);
    }

    private void switchToAudioConfiguration(CarAudioZoneConfigInfo zoneConfigInfoSelected) {
        CarAudioZoneConfigInfo info = getUpdatedConfigInfo(zoneConfigInfoSelected);
        if (Flags.carAudioDynamicDevices()) {
            if (!info.isActive()) {
                showToast(info.getName() + ": not active");
                return;
            }
            if (info.isSelected()) {
                showToast(info.getName() + ": already selected");
                return;
            }
        }
        mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSelected, mContext.getMainExecutor(),
                mSwitchAudioZoneConfigCallback);
    }

    private void handleZoneConfigurationsSelection() {
        int position = mZoneConfigurationSpinner.getSelectedItemPosition();
        synchronized (mLock) {
            mZoneConfigInfoSelected = mZoneConfigurationAdapter.getItem(
                    position).getZoneConfigInfo();
            CarAudioZoneConfigInfo updatedInfo = getUpdatedConfigInfo(mZoneConfigInfoSelected);
            if (Flags.carAudioDynamicDevices()) {
                if (!updatedInfo.isActive()) {
                    showToast(updatedInfo.getName() + ": not active");
                    return;
                }
                if (updatedInfo.isSelected()) {
                    showToast(updatedInfo.getName() + ": already selected");
                }
            }
        }
    }

    private void updateSelectedAudioZoneConfig(CarAudioZoneConfigInfo zoneConfig,
            boolean autoSelected) {
        mCurrentZoneConfigurationView.setText(zoneConfig.getName());
        mConfigSelectedListener.configSelected(autoSelected);
    }

    private void handleAudioZoneConfigsUpdated(List<CarAudioZoneConfigInfo> configs, int status) {
        if (mConfigUpdatedListener != null) {
            mConfigUpdatedListener.configsUpdated(configs);
        }
        if (status == CONFIG_STATUS_CHANGED) {
            showToast("Config status changed " + status);
            return;
        }
        for (CarAudioZoneConfigInfo info : configs) {
            if (!info.isSelected()) {
                continue;
            }
            updateSelectedAudioZoneConfig(info, /* autoSelected= */ true);
            showToast(info.getName() + ": auto selected");
            return;
        }
    }

    private CarAudioZoneConfigInfo getUpdatedConfigInfo(CarAudioZoneConfigInfo zoneConfigInfo) {
        List<CarAudioZoneConfigInfo> configs = mCarAudioManager.getAudioZoneConfigInfos(
                zoneConfigInfo.getZoneId());
        return configs.stream().filter(
                c -> c.getConfigId() == zoneConfigInfo.getConfigId()).findFirst().orElse(
                zoneConfigInfo);
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

    public void release() {
        if (!Flags.carAudioDynamicDevices()) {
            return;
        }
        mCarAudioManager.clearAudioZoneConfigsCallback();
    }

    void connectToConfig(CarAudioZoneConfigInfo info) {
        switchToAudioConfiguration(info);
    }

    interface CarAudioZoneConfigSelectedListener {
        void configSelected(boolean autoSelected);
    }

    interface CarAudioZoneConfigsUpdatedListener {
        void configsUpdated(List<CarAudioZoneConfigInfo> configs);
    }

    private static final class CarAudioZoneConfigInfoWrapper {
        private final CarAudioZoneConfigInfo mZoneConfigInfo;

        CarAudioZoneConfigInfoWrapper(CarAudioZoneConfigInfo configInfo) {
            mZoneConfigInfo = configInfo;
        }

        CarAudioZoneConfigInfo getZoneConfigInfo() {
            return mZoneConfigInfo;
        }

        @Override
        public String toString() {
            return mZoneConfigInfo.getName() + ", Id: " + mZoneConfigInfo.getConfigId();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CarAudioZoneConfigInfoWrapper)) {
                return false;
            }
            CarAudioZoneConfigInfoWrapper wrapper = (CarAudioZoneConfigInfoWrapper) o;
            return Flags.carAudioDynamicDevices()
                    ? mZoneConfigInfo.hasSameConfigInfo(wrapper.mZoneConfigInfo)
                    : mZoneConfigInfo.equals(wrapper.mZoneConfigInfo);
        }

        @Override
        public int hashCode() {
            return mZoneConfigInfo.hashCode();
        }
    }
}
