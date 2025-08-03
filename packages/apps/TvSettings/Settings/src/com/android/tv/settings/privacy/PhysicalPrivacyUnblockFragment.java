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

package com.android.tv.settings.privacy;

import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_HARDWARE;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorPrivacyManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;


/**
 * A fragment that shows info on how to unblock sensors blocked by a physical privacy switch.
 * Dismisses itself when physical privacy is disabled.
 */
@Keep
public class PhysicalPrivacyUnblockFragment extends SettingsPreferenceFragment {
    public static final String TOGGLE_EXTRA = "toggle";

    private PrivacyToggle mToggle;
    private SensorPrivacyManager mSensorPrivacyManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mPrivacyChangedListener =
            (sensor, enabled) -> updateSensorPrivacyState();
    private Preference mImagePreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mSensorPrivacyManager = (SensorPrivacyManager)
                getContext().getSystemService(Context.SENSOR_PRIVACY_SERVICE);

        mToggle = (PrivacyToggle) getArguments().get(TOGGLE_EXTRA);
        if (mToggle == null) {
            throw new IllegalArgumentException("PrivacyToggle extra missing");
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mImagePreference != null) {
            Drawable image = mImagePreference.getIcon();
            if (image instanceof Animatable) {
                ((Animatable) image).start();
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSensorPrivacyManager.addSensorPrivacyListener(mToggle.sensor,
                mPrivacyChangedListener);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        Context themedContext = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(themedContext);

        screen.setTitle(mToggle.physicalPrivacyEnabledInfoTitle);

        mImagePreference = new Preference(themedContext);
        mImagePreference.setLayoutResource(R.layout.image_preference);
        mImagePreference.setIcon(mToggle.physicalPrivacyEnabledInfoPanelImage);
        mImagePreference.setSelectable(false);
        screen.addPreference(mImagePreference);

        Preference preference = new Preference(themedContext);
        preference.setSummary(mToggle.physicalPrivacyEnabledInfoPanelText);
        screen.addPreference(preference);

        updateSensorPrivacyState();

        setPreferenceScreen(screen);
    }

    private void updateSensorPrivacyState() {
        boolean physicalPrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                TOGGLE_TYPE_HARDWARE, mToggle.sensor);
        if (!physicalPrivacyEnabled) {
            FragmentActivity activity = getActivity();
            if (isResumed() && !getParentFragmentManager().popBackStackImmediate()
                    && activity != null) {
                activity.onBackPressed();
            }
        }
    }

    @Override
    public void onDestroyView() {
        mSensorPrivacyManager.removeSensorPrivacyListener(mToggle.sensor, mPrivacyChangedListener);
        super.onDestroyView();
    }
}
