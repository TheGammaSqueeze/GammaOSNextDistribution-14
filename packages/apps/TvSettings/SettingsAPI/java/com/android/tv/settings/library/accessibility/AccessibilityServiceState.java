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

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SERVICE;
import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.device.apps.AppActionPreferenceController;
import com.android.tv.settings.library.settingslib.AccessibilityUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;
import java.util.Set;

/** State corresponding to {@code AccessibilityServiceFragmentCompat}. */
public class AccessibilityServiceState implements State {
    private static final String ARG_PACKAGE_NAME = "packageName";
    private static final String ARG_SERVICE_NAME = "serviceName";
    private static final String ARG_SETTINGS_ACTIVITY_NAME = "settingsActivityName";
    private static final String ARG_LABEL = "label";
    private static final String EXTRA_CHECKED = "checked";
    private static final String KEY_SCREEN = "screen";
    private static final String KEY_ENABLE = "enable";
    private static final String KEY_SETTING = "setting";

    private static final int REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE = 1;

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;
    private PreferenceCompatManager mPreferenceCompatManager;
    private Bundle mExtras;

    private PreferenceCompat mScreen;
    private PreferenceCompat mEnablePref;

    public AccessibilityServiceState(Context context, UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
        mContext = context;
    }

    /**
     * Put args in bundle
     *
     * @param args         Bundle to prepare
     * @param packageName  Package of accessibility service
     * @param serviceName  Class of accessibility service
     * @param activityName Class of accessibility service settings activity
     * @param label        Screen title
     */
    public static Bundle prepareArgs(Bundle args, String packageName, String serviceName,
            String activityName, String label) {
        args.putString(ARG_PACKAGE_NAME, packageName);
        args.putString(ARG_SERVICE_NAME, serviceName);
        args.putString(ARG_SETTINGS_ACTIVITY_NAME, activityName);
        args.putString(ARG_LABEL, label);
        return args;
    }

    @Override
    public void onAttach() {
        // no-op
    }

    @Override
    public void onCreate(Bundle extras) {
        mPreferenceCompatManager = new PreferenceCompatManager();
        mExtras = extras;

        mScreen = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SCREEN);

        mEnablePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_ENABLE);
        mEnablePref.setRestricted(true);
        mEnablePref.setTitle(
                ResourcesUtil.getString(mContext, "system_accessibility_status"));
        mEnablePref.setType(PreferenceCompat.TYPE_SWITCH);

        final PreferenceCompat settingsPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SETTING);
        settingsPref.setTitle(
                ResourcesUtil.getString(mContext, "system_accessibility_config"));
        final String activityName = extras.getString(ARG_SETTINGS_ACTIVITY_NAME);
        if (!TextUtils.isEmpty(activityName)) {
            final String packageName = extras.getString(ARG_PACKAGE_NAME);
            settingsPref.setIntent(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(packageName, activityName)));
        } else {
            settingsPref.setEnabled(false);
        }
        settingsPref.setType(PreferenceCompat.TYPE_PREFERENCE);

        mScreen.addChildPrefCompat(mEnablePref);
        mScreen.addChildPrefCompat(settingsPref);

        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mScreen);
        mUIUpdateCallback.notifyUpdateScreenTitle(getStateIdentifier(),
                extras.getString(ARG_LABEL));
    }

    @Override
    public void onStart() {
        // no-op
    }

    @Override
    public void onResume() {
        updateEnablePref();
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
        if (key[0].equals(KEY_ENABLE) && !mEnablePref.isDisabledByAdmin()) {
            // Prepare confirmation dialog and reverts switch until result comes back.
            updateEnablePref();
            return true;
        } else {
            return false;
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE:
                if (resultCode == Activity.RESULT_OK) {
                    final String packageName = mExtras.getString(ARG_PACKAGE_NAME);
                    final String serviceName = mExtras.getString(ARG_SERVICE_NAME);
                    final ComponentName componentName = new ComponentName(packageName, serviceName);
                    final boolean enabled = data.getBooleanExtra(EXTRA_CHECKED, false);
                    AccessibilityUtils.setAccessibilityServiceState(mContext, componentName,
                            enabled);
                    if (mEnablePref != null) {
                        mEnablePref.setChecked(enabled);
                    }
                }
                break;
            default:
        }
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        final String serviceKey = key[0];
        switch (serviceKey) {
            case KEY_ENABLE:
                Intent intentEnableConfirm = new Intent(
                        AppActionPreferenceController.INTENT_CONFIRMATION);
                intentEnableConfirm.putExtra(AppActionPreferenceController.EXTRA_GUIDANCE_TITLE,
                        mExtras.getString(ARG_LABEL));
                intentEnableConfirm.putExtra(EXTRA_CHECKED,
                        !(mEnablePref.getChecked() == STATUS_ON));
                ((Activity) mContext).startActivityForResult(intentEnableConfirm,
                        ManagerUtil.calculateCompoundCode(
                                getStateIdentifier(),
                                REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE));
                return true;
            default:
        }
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY_SERVICE;
    }

    private void updateEnablePref() {
        final String packageName = mExtras.getString(ARG_PACKAGE_NAME);
        final String serviceName = mExtras.getString(ARG_SERVICE_NAME);
        final ComponentName serviceComponent = new ComponentName(packageName, serviceName);
        final Set<ComponentName> enabledServices =
                AccessibilityUtils.getEnabledServicesFromSettings(mContext);
        final boolean enabled = enabledServices.contains(serviceComponent);
        mEnablePref.setChecked(enabled);

        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        final List<String> permittedServices = dpm.getPermittedAccessibilityServices(
                UserHandle.myUserId());
        final boolean serviceAllowed = permittedServices == null
                || permittedServices.contains(packageName);

        if (serviceAllowed || enabled) {
            mEnablePref.setEnabled(true);
        } else {
            // Disable accessibility service that are not permitted.
            final RestrictedLockUtils.EnforcedAdmin admin =
                    RestrictedLockUtilsInternal.checkIfAccessibilityServiceDisallowed(
                            mContext, packageName, UserHandle.myUserId());
            if (admin != null) {
                mEnablePref.setDisabledByAdmin(true);
            } else {
                mEnablePref.setEnabled(false);
            }
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mScreen);
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
