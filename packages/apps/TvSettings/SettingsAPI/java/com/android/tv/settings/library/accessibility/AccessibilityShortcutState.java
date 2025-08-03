/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.accessibility;

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SHORTCUT;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.AccessibilityUtils;

import java.util.List;

/** State corresponding to {@code AccessibilityShortcutFragmentCompat} */
public class AccessibilityShortcutState implements State {
    private static final String KEY_ENABLE = "enable";
    private static final String KEY_SERVICE = "service";
    private static final String ACCESSIBILITY_SHORTCUT_STORE = "accessibility_shortcut";
    private static final String LAST_SHORTCUT_SERVICE = "last_shortcut_service";

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;
    private PreferenceCompatManager mPreferenceCompatManager;
    private SharedPreferences mSharedPref;

    public AccessibilityShortcutState(Context context, UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
        mContext = context;
    }

    @Override
    public void onAttach() {
        // no-op
    }

    @Override
    public void onCreate(Bundle extras) {
        mPreferenceCompatManager = new PreferenceCompatManager();

        final PreferenceCompat enablePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_ENABLE);
        String enabledComponents = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        mSharedPref = mContext.getSharedPreferences(
                ACCESSIBILITY_SHORTCUT_STORE, Context.MODE_PRIVATE);
        boolean shortcutEnabled = !TextUtils.isEmpty(enabledComponents)
                || TextUtils.isEmpty(getLastShortcutService());
        enablePref.setChecked(shortcutEnabled);
        setAccessibilityShortcutEnabled(shortcutEnabled);
    }

    @Override
    public void onStart() {
        // no-op
    }

    @Override
    public void onResume() {
        updateServicePrefSummary(/*notifyUIUpdate=*/true);
    }

    @Override
    public void onPause() {
        // no-op
    }

    @Override
    public void onStop() {
        // no-op
    }

    @Override
    public void onDestroy() {
        // no-op
    }

    @Override
    public void onDetach() {
        // no-op
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY_SHORTCUT;
    }

    private void updateServicePrefSummary(boolean notifyUIUpdate) {
        final PreferenceCompat servicePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SERVICE);
        final List<AccessibilityServiceInfo> installedServices = mContext
                .getSystemService(AccessibilityManager.class)
                .getInstalledAccessibilityServiceList();
        final PackageManager packageManager = mContext.getPackageManager();
        final String currentService = getCurrentService(mContext);
        for (AccessibilityServiceInfo service : installedServices) {
            final String serviceString = service.getComponentName().flattenToString();
            if (TextUtils.equals(currentService, serviceString)) {
                if (servicePref != null) {
                    servicePref.setSummary(
                            service.getResolveInfo().loadLabel(packageManager).toString());
                }
                putLastShortcutService(currentService);
            }
        }
        if (notifyUIUpdate) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), servicePref);
        }
    }

    public void setAccessibilityShortcutEnabled(boolean enabled) {
        if (enabled) {
            String updatedComponent = getLastShortcutService();
            if (!TextUtils.isEmpty(updatedComponent)) {
                Settings.Secure.putString(mContext.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, updatedComponent);
                updateServicePrefSummary(/*notifyUIUpdate=*/false);
            }
        } else {
            Settings.Secure.putString(mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, "");
            final PreferenceCompat servicePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                    KEY_SERVICE);
            servicePref.setSummary("");
        }
        final PreferenceCompat servicePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SERVICE);
        servicePref.setEnabled(enabled);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), servicePref);
    }

    public static String getCurrentService(Context context) {
        String shortcutServiceString = AccessibilityUtils
                .getShortcutTargetServiceComponentNameString(context, UserHandle.myUserId());
        if (shortcutServiceString != null) {
            ComponentName shortcutName = ComponentName.unflattenFromString(shortcutServiceString);
            if (shortcutName != null) {
                return shortcutName.flattenToString();
            }
        }
        return null;
    }

    private String getLastShortcutService() {
        return mSharedPref.getString(LAST_SHORTCUT_SERVICE, "");
    }

    private void putLastShortcutService(String s) {
        mSharedPref.edit().putString(LAST_SHORTCUT_SERVICE, s).apply();
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
