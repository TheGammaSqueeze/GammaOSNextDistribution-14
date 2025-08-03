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

package com.android.car.settings.applications.performance;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.PackageKillableState;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;
import com.android.settingslib.Utils;

import java.io.File;
import java.util.List;

/**
 * Displays the list of apps which have been disabled due to resource overuse by CarWatchdogService.
 *
 * <p>When a user taps the app, the app's detail setting page is shown. On the other hand, if a
 * user presses the "Prioritize app" button, they are shown a dialog which allows them to prioritize
 * the app, meaning the app can run in the background once again.
 */
public final class PerfImpactingAppsPreferenceController extends
        PreferenceController<PreferenceGroup> {
    private static final Logger LOG = new Logger(PerfImpactingAppsPreferenceController.class);

    @VisibleForTesting
    static final String TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG =
            "com.android.car.settings.applications.performance.PrioritizeAppPerformanceDialogTag";

    @Nullable
    private Car mCar;
    @Nullable
    private CarWatchdogManager mCarWatchdogManager;
    @Nullable
    private List<ApplicationInfo> mEntries;

    public PerfImpactingAppsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        connectToCar();
        updateEntries();
    }

    @Override
    protected void onDestroyInternal() {
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        if (mEntries == null) {
            return;
        }
        preference.removeAll();
        for (int i = 0; i < mEntries.size(); i++) {
            ApplicationInfo entry = mEntries.get(i);
            PerformanceImpactingAppPreference appPreference =
                    new PerformanceImpactingAppPreference(getContext(), entry);
            setOnPreferenceClickListeners(appPreference, entry);
            preference.addPreference(appPreference);
        }
    }

    private void setOnPreferenceClickListeners(PerformanceImpactingAppPreference preference,
            ApplicationInfo info) {
        String packageName = info.packageName;
        UserHandle userHandle = UserHandle.getUserHandleForUid(info.uid);
        preference.setOnPreferenceClickListener(p -> {
            Intent settingsIntent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + packageName));
            getContext().startActivity(settingsIntent);
            return true;
        });
        preference.setOnSecondaryActionClickListener(
                () -> PerfImpactingAppsUtils.showPrioritizeAppConfirmationDialog(getContext(),
                        getFragmentController(),
                        args -> prioritizeApp(packageName, userHandle),
                        TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG));
    }

    private void prioritizeApp(String packageName, UserHandle userHandle) {
        if (mCarWatchdogManager == null) {
            LOG.e("CarWatchdogManager is null. Could not prioritize '" + packageName + "'.");
            connectToCar();
            return;
        }
        int killableState = PerfImpactingAppsUtils.getKillableState(packageName, userHandle,
                mCarWatchdogManager);
        if (killableState == PackageKillableState.KILLABLE_STATE_NEVER) {
            LOG.wtf("Package '" + packageName + "' for user " + userHandle.getIdentifier()
                    + " is disabled for resource overuse but has KILLABLE_STATE_NEVER");
            // Given wtf might not kill the process, we enable package in order to
            // remove from resource overuse disabled package list.
            PackageManager packageManager = getContext().getPackageManager();
            packageManager.setApplicationEnabledSetting(packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, /* flags= */ 0);
            new Handler(Looper.getMainLooper()).postDelayed(this::updateEntries,
                    /* delayMillis= */ 1000);
        } else {
            mCarWatchdogManager.setKillablePackageAsUser(packageName, userHandle,
                    /* isKillable= */ false);
        }
        updateEntries();
    }

    private void updateEntries() {
        mEntries = PerfImpactingAppsUtils.getDisabledAppInfos(getContext());
        refreshUi();
    }

    private void connectToCar() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        mCar = Car.createCar(getContext(), /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                /* statusChangeListener= */ (car, isReady) -> mCarWatchdogManager = isReady
                        ? (CarWatchdogManager) car.getCarManager(Car.CAR_WATCHDOG_SERVICE)
                        : null);
    }

    static class PerformanceImpactingAppPreference extends CarUiTwoActionTextPreference {

        PerformanceImpactingAppPreference(Context context, ApplicationInfo info) {
            super(context);

            boolean apkExists = new File(info.sourceDir).exists();
            setKey(info.packageName + "|" + info.uid);
            setTitle(getLabel(context, info, apkExists));
            setIcon(getIconDrawable(context, info, apkExists));
            setPersistent(false);
            setSecondaryActionText(R.string.performance_impacting_apps_button_label);
        }

        @Override
        protected void init(@Nullable AttributeSet attrs) {
            super.init(attrs);

            setLayoutResourceInternal(R.layout.car_ui_preference_two_action_text_borderless);
        }

        private String getLabel(Context context, ApplicationInfo info, boolean apkExists) {
            if (!apkExists) {
                return info.packageName;
            }
            CharSequence label = info.loadLabel(context.getPackageManager());
            return label != null ? label.toString() : info.packageName;
        }

        private Drawable getIconDrawable(Context context, ApplicationInfo info,
                boolean apkExists) {
            if (apkExists) {
                return Utils.getBadgedIcon(context, info);
            }
            return context.getDrawable(
                    com.android.internal.R.drawable.sym_app_on_sd_unavailable_icon);
        }
    }
}
