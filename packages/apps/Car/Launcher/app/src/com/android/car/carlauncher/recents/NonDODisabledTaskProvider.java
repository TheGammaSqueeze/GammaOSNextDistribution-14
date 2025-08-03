/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.recents;

import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.car.carlauncher.R;

import com.google.common.annotations.VisibleForTesting;

/**
 * {@link RecentTasksViewModel.DisabledTaskProvider} to disable non Driving Optimised activities
 * when driving.
 */
public class NonDODisabledTaskProvider implements RecentTasksViewModel.DisabledTaskProvider,
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
    private static final String TAG = "NonDODisabledTaskProvider";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final Car mCar;
    private final CarUxRestrictionsManager mCarUxRestrictionsManager;
    private final CarPackageManager mCarPackageManager;
    private final PackageManager mPackageManager;
    private final RecentTasksViewModel mRecentTasksViewModel;
    private boolean mIsDistractionOptimizationRequired;

    public NonDODisabledTaskProvider(Context context) {
        this(context, Car.createCar(context), RecentTasksViewModel.getInstance());
    }

    @VisibleForTesting
    NonDODisabledTaskProvider(Context context, Car car,
            RecentTasksViewModel recentTasksViewModel) {
        mCar = car;
        mCarUxRestrictionsManager = (CarUxRestrictionsManager) mCar.getCarManager(
                Car.CAR_UX_RESTRICTION_SERVICE);
        mCarUxRestrictionsManager.registerListener(NonDODisabledTaskProvider.this);
        mCarPackageManager = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);
        mPackageManager = context.getPackageManager();
        mRecentTasksViewModel = recentTasksViewModel;
        mIsDistractionOptimizationRequired = isDistractionOptimizationRequired(
                mCarUxRestrictionsManager.getCurrentCarUxRestrictions());
    }


    /**
     * Should be called before destroying this Provider.
     */
    public void terminate() {
        if (mCar != null && mCar.isConnected()) {
            mCarUxRestrictionsManager.unregisterListener();
            mCar.disconnect();
        }
    }

    @Override
    public boolean isTaskDisabledFromRecents(ComponentName componentName) {
        return mCar.isConnected() && mIsDistractionOptimizationRequired
                && mCarPackageManager != null && !mCarPackageManager.isActivityDistractionOptimized(
                componentName.getPackageName(), componentName.getClassName());
    }

    @Override
    public View.OnClickListener getDisabledTaskClickListener(ComponentName componentName) {
        if (!mCar.isConnected()) {
            return null;
        }
        return v -> {
            ActivityInfo ai;
            try {
                ai = mPackageManager.getActivityInfo(componentName,
                        PackageManager.ComponentInfoFlags.of(0));
            } catch (PackageManager.NameNotFoundException e) {
                if (DEBUG) {
                    Log.e(TAG, e.toString());
                }
                return;
            }
            CharSequence appName = ai.loadLabel(mPackageManager);
            String warningText = v.getResources().getString(R.string.driving_toast_text, appName);
            Toast.makeText(v.getContext(), warningText, Toast.LENGTH_SHORT).show();
        };
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        boolean isDistractionOptimizationRequired = isDistractionOptimizationRequired(
                restrictionInfo);
        if (isDistractionOptimizationRequired != mIsDistractionOptimizationRequired) {
            mIsDistractionOptimizationRequired = isDistractionOptimizationRequired;
            mRecentTasksViewModel.refreshRecentTaskList();
        }
    }

    private boolean isDistractionOptimizationRequired(CarUxRestrictions carUxRestrictions) {
        return carUxRestrictions != null && carUxRestrictions.isRequiresDistractionOptimization();
    }

    @VisibleForTesting
    boolean getIsDistractionOptimizationRequired() {
        return mIsDistractionOptimizationRequired;
    }

    @VisibleForTesting
    void setIsDistractionOptimizationRequired(boolean isDistractionOptimizationRequired) {
        mIsDistractionOptimizationRequired = isDistractionOptimizationRequired;
    }
}
