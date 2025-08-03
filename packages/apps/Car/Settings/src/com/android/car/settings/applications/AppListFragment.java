/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.android.car.settings.common.SettingsFragment;

/**
 * Fragment base class for use in cases where a list of applications is displayed. Includes a
 * a shared preference instance that can be used to show/hide system apps in the list.
 */
public abstract class AppListFragment extends SettingsFragment {

    protected static final String SHARED_PREFERENCE_PATH =
            "com.android.car.settings.applications.AppListFragment";
    protected static final String KEY_HIDE_SYSTEM =
            "com.android.car.settings.applications.HIDE_SYSTEM";

    private boolean mHideSystem = true;

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if (KEY_HIDE_SYSTEM.equals(key)) {
                    mHideSystem = sharedPreferences.getBoolean(KEY_HIDE_SYSTEM,
                            /* defaultValue= */ true);
                    onToggleShowSystemApps(!mHideSystem);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences =
                getContext().getSharedPreferences(SHARED_PREFERENCE_PATH, Context.MODE_PRIVATE);
        if (savedInstanceState != null) {
            mHideSystem = savedInstanceState.getBoolean(KEY_HIDE_SYSTEM,
                    /* defaultValue= */ true);
            mSharedPreferences.edit().putBoolean(KEY_HIDE_SYSTEM, mHideSystem).apply();
        } else {
            mSharedPreferences.edit().putBoolean(KEY_HIDE_SYSTEM, true).apply();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        onToggleShowSystemApps(!mHideSystem);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
    }

    /** Called when a user toggles the option to show system applications in the list. */
    protected abstract void onToggleShowSystemApps(boolean showSystem);

    /** Returns {@code true} if system applications should be shown in the list. */
    protected final boolean shouldShowSystemApps() {
        return !mHideSystem;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HIDE_SYSTEM, mHideSystem);
    }
}
