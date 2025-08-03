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

import static android.content.Context.ACCESSIBILITY_SERVICE;

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY;
import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.accessibility.util.AccessibilityUtils;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;
import java.util.Set;

/** State corresponding to {@code AccessibilityStateCompat} */
public class AccessibilityState implements State {
    private static final String TAG = "AccessibilityState";
    private static final String TOGGLE_HIGH_TEXT_CONTRAST_KEY = "toggle_high_text_contrast";
    private static final String TOGGLE_AUDIO_DESCRIPTION_KEY = "toggle_audio_description";
    private static final String ACCESSIBILITY_SERVICES_KEY = "system_accessibility_services";
    private static final String TOGGLE_BOLD_TEXT_KEY = "toggle_bold_text";
    private static final int BOLD_TEXT_ADJUSTMENT = 500;

    private PreferenceCompat mServicesPref;
    private PreferenceCompat mHighContrastPreference;
    private PreferenceCompat mAudioDescriptionPreference;
    private PreferenceCompat mBoldTextPreference;

    private final AccessibilityManager.AccessibilityStateChangeListener
            mAccessibilityStateChangeListener = enabled -> refreshServices();

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;
    private PreferenceCompatManager mPreferenceCompatManager;

    public AccessibilityState(Context context, UIUpdateCallback callback) {
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
        mHighContrastPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                TOGGLE_HIGH_TEXT_CONTRAST_KEY);
        mHighContrastPreference.setChecked(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED, 0) == 1);

        mAudioDescriptionPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                TOGGLE_AUDIO_DESCRIPTION_KEY);
        mAudioDescriptionPreference.setChecked(Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT, 0) == 1);

        mBoldTextPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                TOGGLE_BOLD_TEXT_KEY);
        mBoldTextPreference.setChecked(Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.FONT_WEIGHT_ADJUSTMENT, 0) == BOLD_TEXT_ADJUSTMENT);

        mServicesPref = mPreferenceCompatManager.getOrCreatePrefCompat(ACCESSIBILITY_SERVICES_KEY);
        refreshServices();
        AccessibilityManager am = (AccessibilityManager)
                mContext.getSystemService(ACCESSIBILITY_SERVICE);
        if (am != null) {
            am.addAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
        }
    }

    @Override
    public void onStart() {
        // no-op
    }

    @Override
    public void onResume() {
        refreshServices();
    }

    @Override
    public void onPause() {
        // no-op
    }

    @Override
    public void onStop() {
        AccessibilityManager am = (AccessibilityManager)
                mContext.getSystemService(ACCESSIBILITY_SERVICE);
        if (am != null && mServicesPref != null) {
            am.removeAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
        }
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
        if (TextUtils.equals(key[0], TOGGLE_HIGH_TEXT_CONTRAST_KEY)) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
                    (mHighContrastPreference.getChecked() == STATUS_ON ? 1 : 0));
            return true;
        } else if (TextUtils.equals(key[0], TOGGLE_AUDIO_DESCRIPTION_KEY)) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT,
                    (mAudioDescriptionPreference.getChecked() == STATUS_ON ? 1 : 0));
            return true;
        } else if (TextUtils.equals(key[0], TOGGLE_BOLD_TEXT_KEY)) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.FONT_WEIGHT_ADJUSTMENT,
                    (mBoldTextPreference.getChecked() == STATUS_ON ? BOLD_TEXT_ADJUSTMENT : 0));
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        if (TextUtils.equals(key[0], TOGGLE_HIGH_TEXT_CONTRAST_KEY)) {
            final boolean value = (Boolean) newValue;
            mHighContrastPreference.setChecked(value);
            refreshToggleHighTextContrastUI();
            return true;
        } else if (TextUtils.equals(key[0], TOGGLE_AUDIO_DESCRIPTION_KEY)) {
            final boolean value = (Boolean) newValue;
            mAudioDescriptionPreference.setChecked(value);
            refreshToggleAudioDescriptionUI();
            return true;
        } else if (TextUtils.equals(key[0], TOGGLE_BOLD_TEXT_KEY)) {
            final boolean value = (Boolean) newValue;
            mBoldTextPreference.setChecked(value);
            refreshToggleBoldTextUI();
            return true;
        }
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY;
    }

    private void refreshServicesUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mServicesPref);
        }
    }

    private void refreshToggleHighTextContrastUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mHighContrastPreference);
        }
    }

    private void refreshToggleAudioDescriptionUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAudioDescriptionPreference);
        }
    }

    private void refreshToggleBoldTextUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mBoldTextPreference);
        }
    }

    private void refreshServices() {
        if (mServicesPref == null) {
            return;
        }
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        final List<AccessibilityServiceInfo> installedServiceInfos =
                mContext.getSystemService(AccessibilityManager.class)
                        .getInstalledAccessibilityServiceList();
        final Set<ComponentName> enabledServices =
                AccessibilityUtils.getEnabledServicesFromSettings(mContext, UserHandle.myUserId());
        final List<String> permittedServices = dpm.getPermittedAccessibilityServices(
                UserHandle.myUserId());

        final boolean accessibilityEnabled = Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;

        mServicesPref.initChildPreferences();
        for (final AccessibilityServiceInfo accInfo : installedServiceInfos) {
            final ServiceInfo serviceInfo = accInfo.getResolveInfo().serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name);

            final boolean serviceEnabled = accessibilityEnabled
                    && enabledServices.contains(componentName);
            // permittedServices null means all accessibility services are allowed.
            final boolean serviceAllowed = permittedServices == null
                    || permittedServices.contains(serviceInfo.packageName);

            final String title = accInfo.getResolveInfo()
                    .loadLabel(mContext.getPackageManager()).toString();

            final String key = "ServicePref:" + componentName.flattenToString();
            PreferenceCompat servicePref = mPreferenceCompatManager.getOrCreatePrefCompat(key);
            servicePref.setRestricted(true);
            servicePref.setTitle(title);
            servicePref.setSummary(
                    serviceEnabled ? ResourcesUtil.getString(mContext, "settings_on")
                            : ResourcesUtil.getString(mContext, "settings_off"));
            Bundle extra = new Bundle();
            AccessibilityServiceState.prepareArgs(extra, serviceInfo.packageName,
                    serviceInfo.name,
                    accInfo.getSettingsActivityName(),
                    title);
            servicePref.setExtras(extra);

            if (serviceAllowed || serviceEnabled) {
                servicePref.setEnabled(true);
                servicePref.setNextState(ManagerUtil.STATE_ACCESSIBILITY_SERVICE);
            } else {
                // Disable accessibility service that are not permitted.
                final RestrictedLockUtils.EnforcedAdmin admin =
                        RestrictedLockUtilsInternal.checkIfAccessibilityServiceDisallowed(
                                mContext, serviceInfo.packageName, UserHandle.myUserId());
                if (admin != null) {
                    servicePref.setDisabledByAdmin(true);
                } else {
                    servicePref.setEnabled(false);
                }
                servicePref.setNextState(null);
            }

            mServicesPref.addChildPrefCompat(servicePref);
        }
        refreshServicesUI();
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
