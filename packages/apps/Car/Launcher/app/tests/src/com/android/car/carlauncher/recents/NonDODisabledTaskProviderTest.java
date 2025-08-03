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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NonDODisabledTaskProviderTest {
    private static final String DO_PACKAGE_NAME = "DO_PACKAGE_NAME";
    private static final String DO_CLASS_NAME = "DO_CLASS_NAME";
    private static final String NON_DO_PACKAGE_NAME = "NON_DO_PACKAGE_NAME";
    private static final String NON_DO_CLASS_NAME = "NON_DO_CLASS_NAME";

    private NonDODisabledTaskProvider mNonDODisabledTaskProvider;
    private ComponentName mDOComponentName;
    private ComponentName mNonDOComponentName;

    @Mock
    private Car mCar;
    @Mock
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    @Mock
    private CarPackageManager mCarPackageManager;
    @Mock
    private RecentTasksViewModel mRecentTasksViewModel;
    @Mock
    private CarUxRestrictions mCarUxRestrictions;
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mCar.isConnected()).thenReturn(true);
        when(mCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)).thenReturn(
                mCarUxRestrictionsManager);
        when(mCar.getCarManager(Car.PACKAGE_SERVICE)).thenReturn(mCarPackageManager);
        mDOComponentName = new ComponentName(DO_PACKAGE_NAME, DO_CLASS_NAME);
        when(mCarPackageManager.isActivityDistractionOptimized(DO_PACKAGE_NAME,
                DO_CLASS_NAME)).thenReturn(true);
        mNonDOComponentName = new ComponentName(NON_DO_PACKAGE_NAME, NON_DO_CLASS_NAME);
        when(mCarPackageManager.isActivityDistractionOptimized(NON_DO_PACKAGE_NAME,
                NON_DO_CLASS_NAME)).thenReturn(false);
        mNonDODisabledTaskProvider = new NonDODisabledTaskProvider(mContext, mCar,
                mRecentTasksViewModel);
    }

    @Test
    public void init_parkState_isDistractionOptimizationRequired_setFalse() {
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(false);
        when(mCarUxRestrictionsManager.getCurrentCarUxRestrictions()).thenReturn(
                mCarUxRestrictions);

        mNonDODisabledTaskProvider = new NonDODisabledTaskProvider(mContext, mCar,
                mRecentTasksViewModel);

        assertThat(mNonDODisabledTaskProvider.getIsDistractionOptimizationRequired()).isFalse();
    }

    @Test
    public void init_driveState_isDistractionOptimizationRequired_setTrue() {
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        when(mCarUxRestrictionsManager.getCurrentCarUxRestrictions()).thenReturn(
                mCarUxRestrictions);

        mNonDODisabledTaskProvider = new NonDODisabledTaskProvider(mContext, mCar,
                mRecentTasksViewModel);

        assertThat(mNonDODisabledTaskProvider.getIsDistractionOptimizationRequired()).isTrue();
    }

    @Test
    public void onUxRestrictionsChanged_parkState_isDistractionOptimizationRequired_setFalse() {
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(false);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        assertThat(mNonDODisabledTaskProvider.getIsDistractionOptimizationRequired()).isFalse();
    }

    @Test
    public void onUxRestrictionsChanged_driveState_isDistractionOptimizationRequired_setTrue() {
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        assertThat(mNonDODisabledTaskProvider.getIsDistractionOptimizationRequired()).isTrue();
    }

    @Test
    public void onUxRestrictionsChanged_parkStateToDriveState_recentsRefreshed() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(false);
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        verify(mRecentTasksViewModel).refreshRecentTaskList();
    }

    @Test
    public void onUxRestrictionsChanged_driveStateToParkState_recentsRefreshed() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(true);
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(false);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        verify(mRecentTasksViewModel).refreshRecentTaskList();
    }

    @Test
    public void onUxRestrictionsChanged_parkStateNoChange_recentsNotRefreshed() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(false);
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(false);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        verify(mRecentTasksViewModel, never()).refreshRecentTaskList();
    }

    @Test
    public void onUxRestrictionsChanged_driveStateNoChange_recentsNotRefreshed() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(true);
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);

        mNonDODisabledTaskProvider.onUxRestrictionsChanged(mCarUxRestrictions);

        verify(mRecentTasksViewModel, never()).refreshRecentTaskList();
    }

    @Test
    public void isTaskDisabledFromRecents_driveState_nonDOActivity_returnsTrue() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(true);

        boolean ret = mNonDODisabledTaskProvider.isTaskDisabledFromRecents(mNonDOComponentName);

        assertThat(ret).isTrue();
    }

    @Test
    public void isTaskDisabledFromRecents_driveState_DOActivity_returnsFalse() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(true);

        boolean ret = mNonDODisabledTaskProvider.isTaskDisabledFromRecents(mDOComponentName);

        assertThat(ret).isFalse();
    }

    @Test
    public void isTaskDisabledFromRecents_parkState_nonDOActivity_returnsFalse() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(false);

        boolean ret = mNonDODisabledTaskProvider.isTaskDisabledFromRecents(mNonDOComponentName);

        assertThat(ret).isFalse();
    }

    @Test
    public void isTaskDisabledFromRecents_parkState_DOActivity_returnsFalse() {
        mNonDODisabledTaskProvider.setIsDistractionOptimizationRequired(false);

        boolean ret = mNonDODisabledTaskProvider.isTaskDisabledFromRecents(mDOComponentName);

        assertThat(ret).isFalse();
    }
}
