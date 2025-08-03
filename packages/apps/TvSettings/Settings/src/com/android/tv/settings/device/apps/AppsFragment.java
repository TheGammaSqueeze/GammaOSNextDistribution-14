/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.settings.device.apps;

import static android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION;

import static com.android.tv.settings.library.util.LibUtils.PROPERTY_APP_HIBERNATION_ENABLED;
import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.Activity;
import android.app.Application;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.util.SliceUtils;
import com.android.tv.twopanelsettings.slices.SlicePreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing recent apps, and apps permissions.
 */
@Keep
public class AppsFragment extends PreferenceControllerFragment {

    private static final String KEY_PERMISSIONS = "Permissions";
    private static final String KEY_SECURITY = "security";
    private static final String KEY_OVERLAY_SECURITY = "overlay_security";
    private static final String KEY_UPDATE = "update";
    private static final String TOP_LEVEL_SLICE_URI = "top_level_settings_slice_uri";
    private static final String KEY_HIBERNATED_APPS = "see_unused_apps";

    public static void prepareArgs(Bundle b, String volumeUuid, String volumeName) {
        b.putString(AppsActivity.EXTRA_VOLUME_UUID, volumeUuid);
        b.putString(AppsActivity.EXTRA_VOLUME_NAME, volumeName);
    }

    public static AppsFragment newInstance(String volumeUuid, String volumeName) {
        final Bundle b = new Bundle(2);
        prepareArgs(b, volumeUuid, volumeName);
        final AppsFragment f = new AppsFragment();
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Preference permissionPreference = findPreference(KEY_PERMISSIONS);
        final String volumeUuid = getArguments().getString(AppsActivity.EXTRA_VOLUME_UUID);
        permissionPreference.setVisible(TextUtils.isEmpty(volumeUuid));
        permissionPreference.setOnPreferenceClickListener(
                preference -> {
                    logEntrySelected(TvSettingsEnums.APPS_APP_PERMISSIONS);
                    return false;
                }
        );
        final Preference securityPreference = findPreference(KEY_SECURITY);
        final Preference overlaySecuritySlicePreference = findPreference(KEY_OVERLAY_SECURITY);
        final Preference updateSlicePreference = findPreference(KEY_UPDATE);
        final Preference hibernatedAppsPreference = findPreference(KEY_HIBERNATED_APPS);
        if (FlavorUtils.getFeatureFactory(getContext()).getBasicModeFeatureProvider()
                .isBasicMode(getContext())) {
            showSecurityPreference(securityPreference, overlaySecuritySlicePreference);
            if (updateSlicePreference != null) {
                updateSlicePreference.setVisible(false);
            }
        } else {
            if (isOverlaySecuritySlicePreferenceEnabled(overlaySecuritySlicePreference)) {
                showOverlaySecuritySlicePreference(
                        overlaySecuritySlicePreference, securityPreference);
            } else {
                showSecurityPreference(securityPreference, overlaySecuritySlicePreference);
            }
            if (updateSlicePreference != null) {
                updateSlicePreference.setVisible(
                        isUpdateSlicePreferenceEnabled(updateSlicePreference));
            }
        }
        if (hibernatedAppsPreference != null) {
            hibernatedAppsPreference.setVisible(isHibernationEnabled());
        }
    }

    private boolean isOverlaySecuritySlicePreferenceEnabled(
            @Nullable Preference overlaySecuritySlicePreference) {
        return overlaySecuritySlicePreference instanceof SlicePreference
                && SliceUtils.isSettingsSliceEnabled(getContext(),
                        ((SlicePreference) overlaySecuritySlicePreference).getUri(),
                                TOP_LEVEL_SLICE_URI);
    }

    private void showOverlaySecuritySlicePreference(
            @Nullable Preference overlaySecuritySlicePreference,
            @Nullable Preference securityPreference) {
        if (overlaySecuritySlicePreference != null) {
            overlaySecuritySlicePreference.setVisible(true);
        }
        if (securityPreference != null) {
            securityPreference.setVisible(false);
        }
    }

    private void showSecurityPreference(
            @Nullable Preference securityPreference,
            @Nullable Preference overlaySecuritySlicePreference) {
        if (securityPreference != null) {
            securityPreference.setVisible(true);
        }
        if (overlaySecuritySlicePreference != null) {
            overlaySecuritySlicePreference.setVisible(false);
        }
    }

    private boolean isUpdateSlicePreferenceEnabled(
            @Nullable Preference updateSlicePreference) {
        return updateSlicePreference instanceof SlicePreference
                && SliceUtils.isSettingsSliceEnabled(
                        getContext(),
                        ((SlicePreference) updateSlicePreference).getUri(),
                                TOP_LEVEL_SLICE_URI);
    }

    private static boolean isHibernationEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED, false);
    }

    @Override
    protected int getPreferenceScreenResId() {
        switch (FlavorUtils.getFlavor(getContext())) {
            case FlavorUtils.FLAVOR_X:
            case FlavorUtils.FLAVOR_VENDOR:
                return R.xml.apps_x;
            default:
                return R.xml.apps;
        }
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        final Activity activity = getActivity();
        final Application app = activity != null ? activity.getApplication() : null;
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new RecentAppsPreferenceController(getContext(), app));
        return controllers;
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.APPS;
    }
}
