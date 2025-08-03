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

package com.android.car.settings.location;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Process;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collection;

/**
 * Displays a list of ADAS apps with their privacy policy and a link to their location permission
 * settings.
 */
public final class AdasPrivacyPolicyDisclosurePreferenceController
        extends PreferenceController<LogicalPreferenceGroup> {

    private final PackageManager mPackageManager;
    private final LocationManager mLocationManager;

    public AdasPrivacyPolicyDisclosurePreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(
                context,
                preferenceKey,
                fragmentController,
                uxRestrictions,
                context.getPackageManager(),
                context.getSystemService(LocationManager.class));
    }

    @VisibleForTesting
    public AdasPrivacyPolicyDisclosurePreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions,
            PackageManager packageManager,
            LocationManager locationManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = packageManager;
        mLocationManager = locationManager;
    }

    @Override
    protected Class<LogicalPreferenceGroup> getPreferenceType() {
        return LogicalPreferenceGroup.class;
    }

    @Override
    public void updateState(LogicalPreferenceGroup preference) {
        super.updateState(preference);
        loadAppsWithLocationPermission();
    }

    private void loadAppsWithLocationPermission() {
        getPreference().removeAll();

        Collection<String> adasApps = mLocationManager.getAdasAllowlist().getPackages();
        for (String adasApp : adasApps) {
            CarUiTwoActionTextPreference perf =
                    AdasPrivacyPolicyUtil.createPrivacyPolicyPreference(
                            getContext(), mPackageManager, adasApp, Process.myUserHandle());
            if (perf != null) {
                getPreference().addPreference(perf);
            }
        }
    }
}
