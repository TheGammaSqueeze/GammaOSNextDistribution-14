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

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.graphics.Point;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Display;
import android.view.WindowManagerGlobal;
import android.view.IWindowManager;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

public class CustomResolutionFragment extends DashboardFragment {

    private static final String TAG = "CustomResolution";

    private static final String KEY_CURRENT = "custom_resolution_current";
    private static final String KEY_NATIVE = "custom_resolution_native";
    private static final String KEY_WIDTH = "custom_resolution_width";
    private static final String KEY_HEIGHT = "custom_resolution_height";
    private static final String KEY_APPLY = "custom_resolution_apply";
    private static final String KEY_RESET = "custom_resolution_reset";

    private IWindowManager mWindowManager;
    private int mDisplayId;

    private Preference mCurrentPref;
    private Preference mNativePref;
    private EditTextPreference mWidthPref;
    private EditTextPreference mHeightPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWindowManager = WindowManagerGlobal.getWindowManagerService();
        mDisplayId = Display.DEFAULT_DISPLAY;

        mCurrentPref = findPreference(KEY_CURRENT);
        mNativePref = findPreference(KEY_NATIVE);
        mWidthPref = findPreference(KEY_WIDTH);
        mHeightPref = findPreference(KEY_HEIGHT);

        Point initialSize = getInitialDisplaySize();
        Point baseSize = getBaseDisplaySize();

        mNativePref.setTitle(getString(R.string.custom_resolution_native,
                initialSize.x, initialSize.y));

        mWidthPref.setText(String.valueOf(baseSize.x));
        mWidthPref.setSummary(String.valueOf(baseSize.x));
        mWidthPref.setOnPreferenceChangeListener((pref, newValue) -> {
            mWidthPref.setSummary((String) newValue);
            return true;
        });

        mHeightPref.setText(String.valueOf(baseSize.y));
        mHeightPref.setSummary(String.valueOf(baseSize.y));
        mHeightPref.setOnPreferenceChangeListener((pref, newValue) -> {
            mHeightPref.setSummary((String) newValue);
            return true;
        });

        updateCurrentResolution();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_APPLY.equals(key)) {
            applyResolution();
            return true;
        } else if (KEY_RESET.equals(key)) {
            resetResolution();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void applyResolution() {
        try {
            int width = Integer.parseInt(mWidthPref.getText());
            int height = Integer.parseInt(mHeightPref.getText());
            if (width <= 0 || height <= 0) {
                Toast.makeText(getContext(), R.string.custom_resolution_invalid,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mWindowManager.setForcedDisplaySize(mDisplayId, width, height);
            updateCurrentResolution();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.custom_resolution_invalid,
                    Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            // Ignore
        }
    }

    private void resetResolution() {
        try {
            mWindowManager.clearForcedDisplaySize(mDisplayId);
            Point initialSize = getInitialDisplaySize();
            mWidthPref.setText(String.valueOf(initialSize.x));
            mWidthPref.setSummary(String.valueOf(initialSize.x));
            mHeightPref.setText(String.valueOf(initialSize.y));
            mHeightPref.setSummary(String.valueOf(initialSize.y));
            updateCurrentResolution();
        } catch (RemoteException e) {
            // Ignore
        }
    }

    private void updateCurrentResolution() {
        Point initialSize = getInitialDisplaySize();
        Point baseSize = getBaseDisplaySize();

        if (initialSize.equals(baseSize)) {
            mCurrentPref.setSummary(getString(R.string.custom_resolution_summary,
                    baseSize.x, baseSize.y));
        } else {
            mCurrentPref.setSummary(getString(R.string.custom_resolution_summary_custom,
                    baseSize.x, baseSize.y));
        }
    }

    private Point getInitialDisplaySize() {
        Point size = new Point();
        try {
            mWindowManager.getInitialDisplaySize(mDisplayId, size);
        } catch (RemoteException e) {
            // Ignore
        }
        return size;
    }

    private Point getBaseDisplaySize() {
        Point size = new Point();
        try {
            mWindowManager.getBaseDisplaySize(mDisplayId, size);
        } catch (RemoteException e) {
            // Ignore
        }
        return size;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.custom_resolution_settings;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
