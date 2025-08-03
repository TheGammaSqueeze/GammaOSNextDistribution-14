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

package com.android.car.settings.applications;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.PackageKillableState;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.applications.performance.PerfImpactingAppsUtils;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/** Controller for preference which turns on / off prioritize app performance setting. */
public class PrioritizeAppPerformancePreferenceController
        extends PreferenceController<TwoStatePreference> {
    private static final Logger LOG =
            new Logger(PrioritizeAppPerformancePreferenceController.class);

    @VisibleForTesting
    static final String TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG =
            "com.android.car.settings.applications.TurnOnPrioritizeAppPerformanceDialogTag";

    @Nullable
    private Car mCar;
    @Nullable
    private CarWatchdogManager mCarWatchdogManager;
    private String mPackageName;
    private UserHandle mUserHandle;

    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        if (!isCarConnected()) {
            return;
        }
        setKillableState(false);
        getPreference().setChecked(true);
    };

    public PrioritizeAppPerformancePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void onCreateInternal() {
        connectToCar();

        ConfirmationDialogFragment dialogFragment =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(
                dialogFragment, mConfirmListener, /* rejectListener= */ null,
                /* neutralListener= */ null);
    }

    @Override
    protected void onDestroyInternal() {
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    /**
     * Set the package info of the application.
     */
    public void setPackageInfo(PackageInfo packageInfo) {
        mPackageName = packageInfo.packageName;
        mUserHandle = UserHandle.getUserHandleForUid(packageInfo.applicationInfo.uid);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        if (!isCarConnected()) {
            return;
        }
        int killableState = PerfImpactingAppsUtils.getKillableState(mPackageName, mUserHandle,
                    mCarWatchdogManager);
        preference.setChecked(killableState == PackageKillableState.KILLABLE_STATE_NO);
        preference.setEnabled(killableState != PackageKillableState.KILLABLE_STATE_NEVER);
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean isToggledOn = (boolean) newValue;
        if (isToggledOn) {
            PerfImpactingAppsUtils.showPrioritizeAppConfirmationDialog(getContext(),
                    getFragmentController(), mConfirmListener,
                    TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG);
            return false;
        }
        if (!isCarConnected()) {
            return false;
        }
        setKillableState(true);
        return true;
    }

    private void setKillableState(boolean isKillable) {
        mCarWatchdogManager.setKillablePackageAsUser(mPackageName, mUserHandle, isKillable);
    }

    private boolean isCarConnected() {
        if (mCarWatchdogManager == null) {
            LOG.e("CarWatchdogManager is null. Could not set killable state for '" + mPackageName
                    + "'.");
            connectToCar();
            return false;
        }
        return true;
    }

    private void connectToCar() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        mCar = Car.createCar(getContext(), null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, isReady) -> {
                    mCarWatchdogManager = isReady
                            ? (CarWatchdogManager) car.getCarManager(Car.CAR_WATCHDOG_SERVICE)
                            : null;
                });
    }
}
