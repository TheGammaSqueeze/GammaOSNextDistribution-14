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

package com.android.tv.settings.system;

import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.icu.text.MessageFormat;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fragment to control TV input settings.
 */
@Keep
public class InputsFragment extends SettingsPreferenceFragment {

    private static final String KEY_CONNECTED_INPUTS = "connected_inputs";
    private static final String KEY_STANDBY_INPUTS = "standby_inputs";
    private static final String KEY_DISCONNECTED_INPUTS = "disconnected_inputs";
    private static final String KEY_HDMI_CONTROL = "hdmi_control";
    private static final String KEY_DEVICE_AUTO_OFF = "device_auto_off";
    private static final String KEY_TV_AUTO_ON = "tv_auto_on";
    private static final String KEY_CEC_VOLUME = "volume_control_enabled";
    private static final String ICU_PLURAL_COUNT = "count";

    private PreferenceGroup mConnectedGroup;
    private PreferenceGroup mStandbyGroup;
    private PreferenceGroup mDisconnectedGroup;

    private TwoStatePreference mHdmiControlPref;
    private TwoStatePreference mDeviceAutoOffPref;
    private TwoStatePreference mTvAutoOnPref;
    private TwoStatePreference mCecVolumePref;

    private TvInputManager mTvInputManager;
    private HdmiControlManager mHdmiControlManager;
    private Map<String, String> mCustomLabels;
    private Set<String> mHiddenIds;

    public static InputsFragment newInstance() {
        return new InputsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTvInputManager = (TvInputManager) getContext().getSystemService(Context.TV_INPUT_SERVICE);
        mHdmiControlManager = getContext().getSystemService(HdmiControlManager.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        final Context context = getContext();
        mCustomLabels =
                TvInputInfo.TvInputSettings.getCustomLabels(context, UserHandle.USER_SYSTEM);
        mHiddenIds =
                TvInputInfo.TvInputSettings.getHiddenTvInputIds(context, UserHandle.USER_SYSTEM);
        refresh();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.inputs, null);

        mConnectedGroup = (PreferenceGroup) findPreference(KEY_CONNECTED_INPUTS);
        mStandbyGroup = (PreferenceGroup) findPreference(KEY_STANDBY_INPUTS);
        mDisconnectedGroup = (PreferenceGroup) findPreference(KEY_DISCONNECTED_INPUTS);

        mHdmiControlPref = (TwoStatePreference) findPreference(KEY_HDMI_CONTROL);
        mDeviceAutoOffPref = (TwoStatePreference) findPreference(KEY_DEVICE_AUTO_OFF);
        mTvAutoOnPref = (TwoStatePreference) findPreference(KEY_TV_AUTO_ON);
        mCecVolumePref = (TwoStatePreference) findPreference(KEY_CEC_VOLUME);
    }

    private void refresh() {
        mHdmiControlPref.setChecked(mHdmiControlManager.getHdmiCecEnabled()
                == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED);
        mDeviceAutoOffPref.setChecked(mHdmiControlManager.getTvSendStandbyOnSleep()
                == HdmiControlManager.TV_SEND_STANDBY_ON_SLEEP_ENABLED);
        mTvAutoOnPref.setChecked(mHdmiControlManager.getTvWakeOnOneTouchPlay()
                == HdmiControlManager.TV_WAKE_ON_ONE_TOUCH_PLAY_ENABLED);
        mCecVolumePref.setChecked(mHdmiControlManager.getHdmiCecVolumeControlEnabled()
                == HdmiControlManager.VOLUME_CONTROL_ENABLED);

        for (TvInputInfo info : mTvInputManager.getTvInputList()) {
            if (info.getType() == TvInputInfo.TYPE_TUNER
                    || !TextUtils.isEmpty(info.getParentId())) {
                continue;
            }

            int state;
            try {
                state = mTvInputManager.getInputState(info.getId());
            } catch (IllegalArgumentException e) {
                // Input is gone while iterating. Ignore.
                continue;
            }

            InputPreference inputPref = (InputPreference) findPreference(makeInputPrefKey(info));
            if (inputPref == null) {
                inputPref = new InputPreference(getPreferenceManager().getContext());
            }
            inputPref.refresh(info);

            switch (state) {
                case TvInputManager.INPUT_STATE_CONNECTED:
                    mStandbyGroup.removePreference(inputPref);
                    mDisconnectedGroup.removePreference(inputPref);
                    mConnectedGroup.addPreference(inputPref);
                    break;
                case TvInputManager.INPUT_STATE_CONNECTED_STANDBY:
                    mConnectedGroup.removePreference(inputPref);
                    mDisconnectedGroup.removePreference(inputPref);
                    mStandbyGroup.addPreference(inputPref);
                    break;
                case TvInputManager.INPUT_STATE_DISCONNECTED:
                    mConnectedGroup.removePreference(inputPref);
                    mStandbyGroup.removePreference(inputPref);
                    mDisconnectedGroup.addPreference(inputPref);
                    break;
            }
        }

        final int connectedCount = mConnectedGroup.getPreferenceCount();
        MessageFormat msgFormat = new MessageFormat(
                getResources().getString(R.string.inputs_header_connected_input),
                Locale.getDefault());
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(ICU_PLURAL_COUNT, connectedCount);
        String songsFound = msgFormat.format(arguments);
        mConnectedGroup.setTitle(msgFormat.format(arguments));
        mConnectedGroup.setVisible(connectedCount > 0);

        final int standbyCount = mStandbyGroup.getPreferenceCount();
        msgFormat = new MessageFormat(
                getResources().getString(R.string.inputs_header_standby_input),
                Locale.getDefault());
        arguments = new HashMap<>();
        arguments.put(ICU_PLURAL_COUNT, standbyCount);
        mStandbyGroup.setTitle(msgFormat.format(arguments));
        mStandbyGroup.setVisible(standbyCount > 0);

        final int disconnectedCount = mDisconnectedGroup.getPreferenceCount();
        msgFormat = new MessageFormat(
                getResources().getString(R.string.inputs_header_disconnected_input),
                Locale.getDefault());
        arguments = new HashMap<>();
        arguments.put(ICU_PLURAL_COUNT, disconnectedCount);
        mDisconnectedGroup.setTitle(msgFormat.format(arguments));
        mDisconnectedGroup.setVisible(disconnectedCount > 0);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (key) {
            case KEY_HDMI_CONTROL:
                mHdmiControlManager.setHdmiCecEnabled(mHdmiControlPref.isChecked()
                        ? HdmiControlManager.HDMI_CEC_CONTROL_ENABLED
                        : HdmiControlManager.HDMI_CEC_CONTROL_DISABLED);
                return true;
            case KEY_DEVICE_AUTO_OFF:
                mHdmiControlManager.setTvSendStandbyOnSleep(mDeviceAutoOffPref.isChecked()
                        ? HdmiControlManager.TV_SEND_STANDBY_ON_SLEEP_ENABLED
                        : HdmiControlManager.TV_SEND_STANDBY_ON_SLEEP_DISABLED);
                return true;
            case KEY_TV_AUTO_ON:
                mHdmiControlManager.setTvWakeOnOneTouchPlay(mTvAutoOnPref.isChecked()
                        ? HdmiControlManager.TV_WAKE_ON_ONE_TOUCH_PLAY_ENABLED
                        : HdmiControlManager.TV_WAKE_ON_ONE_TOUCH_PLAY_DISABLED);
                return true;
            case KEY_CEC_VOLUME:
                mHdmiControlManager.setHdmiCecVolumeControlEnabled(mCecVolumePref.isChecked()
                        ? HdmiControlManager.VOLUME_CONTROL_ENABLED
                        : HdmiControlManager.VOLUME_CONTROL_DISABLED);
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private class InputPreference extends Preference {
        public InputPreference(Context context) {
            super(context);
        }

        public void refresh(TvInputInfo inputInfo) {
            setKey(makeInputPrefKey(inputInfo));

            setTitle(inputInfo.loadLabel(getContext()));

            String customLabel;
            if (mHiddenIds.contains(inputInfo.getId())) {
                customLabel = getString(R.string.inputs_hide);
            } else {
                customLabel = mCustomLabels.get(inputInfo.getId());
                if (TextUtils.isEmpty(customLabel)) {
                    customLabel = inputInfo.loadLabel(getContext()).toString();
                }
            }
            setSummary(customLabel);
            setFragment(InputOptionsFragment.class.getName());
            InputOptionsFragment.prepareArgs(getExtras(), inputInfo);
        }
    }

    public static String makeInputPrefKey(TvInputInfo inputInfo) {
        return "InputPref:" + inputInfo.getId();
    }
}
