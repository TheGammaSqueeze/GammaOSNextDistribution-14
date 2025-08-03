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

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SHORTCUT_SERVICE;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.device.apps.AppActionPreferenceController;

import java.util.HashSet;
import java.util.List;

/** State corresponding to {@code AccessibilityShortcutServiceFragmentCompat}. */
public class AccessibilityShortcutServiceState implements State {
    private static final String TAG = "AccessibilityShortcutServiceState";
    private static final String EXTRA_SERVICE_KEY = "extra_service_key";
    private static final String SERVICE_RADIO_GROUP = "service_group";
    private static final String KEY_SCREEN = "screen";
    private static final int REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE = 1;

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;
    private final PreferenceCompatManager mPreferenceCompatManager = new PreferenceCompatManager();
    private PreferenceCompat mScreen;
    private final HashSet<String> mServiceKeys = new HashSet<>();

    public AccessibilityShortcutServiceState(Context context, UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
        mContext = context;
    }

    @Override
    public void onAttach() {
        // no-op
    }

    @Override
    public void onCreate(Bundle extras) {
        mScreen = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SCREEN);

        final List<AccessibilityServiceInfo> installedServices = mContext
                .getSystemService(AccessibilityManager.class)
                .getInstalledAccessibilityServiceList();
        final PackageManager packageManager = mContext.getPackageManager();
        final String currentService = AccessibilityShortcutState.getCurrentService(mContext);
        mScreen.initChildPreferences();
        mServiceKeys.clear();
        for (AccessibilityServiceInfo service : installedServices) {
            final String serviceString = service.getComponentName().flattenToString();
            mServiceKeys.add(serviceString);
            final PreferenceCompat preference = mPreferenceCompatManager.getOrCreatePrefCompat(
                    serviceString);
            preference.setType(PreferenceCompat.TYPE_RADIO);
            if (TextUtils.equals(currentService, serviceString)) {
                preference.setChecked(true);
            }
            preference.setPersistent(false);
            preference.setRadioGroup(SERVICE_RADIO_GROUP);
            preference.setTitle(service.getResolveInfo().loadLabel(packageManager).toString());

            mScreen.addChildPrefCompat(preference);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mScreen);
    }

    @Override
    public void onStart() {
        // no-op
    }

    @Override
    public void onResume() {
        // no-op
    }

    @Override
    public void onPause() {

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
        // no-op
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE:
                if (resultCode == Activity.RESULT_OK) {
                    final String componentString = data.getStringExtra(EXTRA_SERVICE_KEY);
                    Settings.Secure.putString(mContext.getContentResolver(),
                            Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
                            componentString);
                }
                updateSelection();
                break;
            default:
        }
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        final String serviceKey = key[0];
        if (mServiceKeys.contains(serviceKey)) {
            final String currentService = AccessibilityShortcutState.getCurrentService(
                    mContext);
            if ((Boolean) newValue && !TextUtils.equals(serviceKey, currentService)) {
                PreferenceCompat preference = mPreferenceCompatManager.getPrefCompat(key);
                final ComponentName cn = ComponentName.unflattenFromString(serviceKey);
                final CharSequence label = preference.getTitle();

                Intent i = new Intent(AppActionPreferenceController.INTENT_CONFIRMATION);
                i.putExtra(AppActionPreferenceController.EXTRA_GUIDANCE_TITLE,
                        preference.getTitle());
                i.putExtra(EXTRA_SERVICE_KEY, serviceKey);
                ((Activity) mContext).startActivityForResult(i,
                        ManagerUtil.calculateCompoundCode(
                                getStateIdentifier(),
                                REQUEST_SELECT_ACCESSIBILITY_SHORTCUT_SERVICE));
                return true;
            }
        }
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY_SHORTCUT_SERVICE;
    }

    private void updateSelection() {
        final String currentService = AccessibilityShortcutState.getCurrentService(mContext);
        final List<PreferenceCompat> childPrefs = mScreen.getChildPrefCompats();
        for (PreferenceCompat pref : childPrefs) {
            boolean shouldEnable = currentService.equals(pref.getKey()[0]);
            if (pref != null) {
                pref.setChecked(shouldEnable);
            }
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mScreen);
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
