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

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import static com.google.android.car.kitchensink.R.string.config_name;
import static com.google.android.car.kitchensink.audio.AudioUtils.getCarAudioZoneConfigInfoOrNull;
import static com.google.android.car.kitchensink.audio.AudioUtils.getCurrentZoneId;

import android.app.AlertDialog;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.car.kitchensink.R;

import java.util.List;

public final class AudioConfigurationTestFragment extends Fragment {
    public static final String FRAGMENT_NAME = "audio configurations";
    private static final String TAG = "CAR.AUDIO.KS.CONFIGURATION";
    private static final String CONFIG_ID_PREFIX = "CONFIG_ID_FOR_ZONE_";
    private static final int INVALID_CONFIG_ID = -1;

    private Context mContext;
    private Car mCar;
    private CarAudioManager mCarAudioManager;
    private ZoneConfigSelectionController mZoneConfigController;
    private RadioGroup mAutoConnectGroup;
    private TextView mAutoConfigNameTextView;
    private AudioConfigurationInformationTabs mAudioConfigsTabs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.audio_configuration, container, false);
        connectCar(view);

        return view;
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");
        mZoneConfigController.release();
        mCar.disconnect();
        super.onDestroyView();
    }

    private void connectCar(View view) {
        mContext = getContext();
        mCar = Car.createCar(mContext, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
                    if (!mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
                        setUpConfigurationViewVisibility(view, View.GONE);
                        return;
                    }
                    setUpConfigurationViewVisibility(view, View.VISIBLE);
                    FragmentManager fragmentManager = getChildFragmentManager();
                    AudioPlayersTabControllers.setUpAudioPlayersTab(view, fragmentManager);
                    handleSetUpConfigurationInfoTabs(view, fragmentManager);
                    handleSetUpZoneConfigurationSelection(view);
                    handleSetUpConfigurationAutoSelection(view);
                });
    }

    private static void setUpConfigurationViewVisibility(View view, int visibility) {
        TextView title = view.findViewById(R.id.audio_zone_configuration_title);
        int titleResource = visibility == View.GONE ? R.string.audio_zone_configuration_title_off
                : R.string.audio_zone_configuration_title_on;
        view.findViewById(R.id.audio_zone_configuration).setVisibility(visibility);
        title.setText(titleResource);
    }

    private void handleSetUpConfigurationInfoTabs(View view, FragmentManager fragmentManager) {
        int zoneId = PRIMARY_AUDIO_ZONE;
        try {
            zoneId = getCurrentZoneId(mContext, mCarAudioManager);
        } catch (Exception e) {
            Log.e(TAG, "handleSetUpConfigurationInfoTabs could not get audio zone");
        }
        mAudioConfigsTabs = new AudioConfigurationInformationTabs(view, mCarAudioManager,
                fragmentManager, zoneId);
    }

    private void handleSetUpConfigurationAutoSelection(View view) {
        mAutoConfigNameTextView = view.findViewById(R.id.auto_connect_config);
        mAutoConnectGroup = view.findViewById(R.id.reconnect_behavior_selection);
        mAutoConnectGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int zoneId = PRIMARY_AUDIO_ZONE;
            try {
                zoneId = getCurrentZoneId(mContext, mCarAudioManager);
            } catch (Exception e) {
                Log.e(TAG, "Could not query zone id for configuration auto connect", e);
                return;
            }
            switch (checkedId) {
                case R.id.connect_to_last_selected:
                    CarAudioZoneConfigInfo info =
                            mCarAudioManager.getCurrentAudioZoneConfigInfo(zoneId);
                    setAutoConnectConfiguration(info);
                    return;
                case R.id.connect_to_configuration:
                    showConfigSelectionDialog(zoneId);
                    return;
                case R.id.do_not_auto_connect:
                default:
                    setAutoConnectConfiguration(null);
            }

        });
        setUpAutoConnectConfiguration();
    }

    private void setUpAutoConnectConfiguration() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        int zoneId = INVALID_AUDIO_ZONE;
        int configId = INVALID_CONFIG_ID;
        try {
            zoneId = getCurrentZoneId(mContext, mCarAudioManager);
            configId = preferences.getInt(getConfigIdKeyForZone(zoneId), INVALID_CONFIG_ID);
        } catch (Exception e) {
            Log.e(TAG, "setUpAutoConnectConfiguration could not get zone");
        }
        if (zoneId == INVALID_AUDIO_ZONE || configId == INVALID_CONFIG_ID) {
            setAutoConnectConfiguration(null);
            return;
        }
        List<CarAudioZoneConfigInfo> configurations =
                mCarAudioManager.getAudioZoneConfigInfos(zoneId);
        CarAudioZoneConfigInfo autoConnectInfo =
                getCarAudioZoneConfigInfoOrNull(configurations, zoneId, configId);
        setAutoConnectConfiguration(autoConnectInfo);
        configsUpdatedListener(configurations);
    }

    private void showConfigSelectionDialog(int zoneId) {
        List<CarAudioZoneConfigInfo> infos = mCarAudioManager.getAudioZoneConfigInfos(zoneId);
        String[] configurations = infos.stream().map(i -> i.getName()).toArray(String[]::new);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("Select a configuration to auto connect");
        builder.setItems(configurations, (DialogInterface.OnClickListener) (dialog, which) -> {
            CarAudioZoneConfigInfo selectedInfo = infos.get(which);
            setAutoConnectConfiguration(selectedInfo);
        });
        builder.show();
    }

    private void setAutoConnectConfiguration(@Nullable CarAudioZoneConfigInfo info) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        int zoneId;
        int configId = INVALID_CONFIG_ID;
        String name = "None";
        if (info == null) {
            try {
                zoneId = getCurrentZoneId(mContext, mCarAudioManager);
            } catch (Exception e) {
                Log.e(TAG, "setAutoConnectConfiguration could not get zone id", e);
                return;
            }
        } else {
            zoneId = info.getZoneId();
            configId = info.getConfigId();
            name = info.getName();
        }
        editor.putInt(getConfigIdKeyForZone(zoneId), configId).apply();
        mAutoConfigNameTextView.setText(mContext.getString(config_name, name));
    }

    private void handleSetUpZoneConfigurationSelection(View view) {
        try {
            mZoneConfigController = new ZoneConfigSelectionController(view, mCarAudioManager,
                    mContext, getCurrentZoneId(mContext, mCarAudioManager),
                    this::configurationSelected, this::configsUpdatedListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup car audio zone config selection view", e);
        }
    }

    private void configsUpdatedListener(List<CarAudioZoneConfigInfo> configs) {
        handleUpdateConfigTabs(configs);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        int zoneId = INVALID_AUDIO_ZONE;
        try {
            zoneId = getCurrentZoneId(mContext, mCarAudioManager);
        } catch (Exception e) {
            Log.e(TAG, "configsUpdatedListener could not get zone id");
        }
        if (zoneId == INVALID_AUDIO_ZONE) {
            return;
        }
        int configId = preferences.getInt(getConfigIdKeyForZone(zoneId), INVALID_CONFIG_ID);
        if (configId == INVALID_CONFIG_ID) {
            return;
        }
        CarAudioZoneConfigInfo info = getCarAudioZoneConfigInfoOrNull(configs, zoneId, configId);
        if (info == null) {
            return;
        }
        if (!info.isActive() || info.isSelected()) {
            return;
        }
        mZoneConfigController.connectToConfig(info);
    }

    private void handleUpdateConfigTabs(List<CarAudioZoneConfigInfo> configs) {
        mAudioConfigsTabs.updateConfigs(configs);
    }

    private String getConfigIdKeyForZone(int zoneId) {
        return CONFIG_ID_PREFIX + zoneId;
    }

    private void configurationSelected(boolean autoSelected) {
        int zoneId = PRIMARY_AUDIO_ZONE;
        try {
            zoneId = getCurrentZoneId(mContext, mCarAudioManager);
        } catch (Exception e) {
            Log.e(TAG, "Could not find zone to query configurations", e);
        }
        handleUpdateConfigTabs(mCarAudioManager.getAudioZoneConfigInfos(zoneId));
        if (autoSelected) {
            return;
        }
        if (mAutoConnectGroup.getCheckedRadioButtonId() != R.id.connect_to_last_selected) {
            return;
        }
        setAutoConnectConfiguration(mCarAudioManager.getCurrentAudioZoneConfigInfo(zoneId));
    }
}
