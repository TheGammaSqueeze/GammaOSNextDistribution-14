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
 * limitations under the License.
 */
package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.car.Car;
import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarPackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.filters.Suppress;

import com.android.car.pm.CarPackageManagerService;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarPackageManagerTest extends MockedCarTestBase {
    private static final String TAG = CarPackageManagerTest.class.getSimpleName();

    private static final int POLLING_MAX_RETRY = 10;
    private static final long POLLING_SLEEP = 100;

    private CarPackageManager mCarPm;
    private CarPackageManagerService mCarPmService;

    private void init(boolean policyFromService) throws Exception {
        Log.i(TAG, "init started");
        TestAppBlockingPolicyService.controlPolicySettingFromService(policyFromService);
        mCarPm = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
        assertThat(mCarPm).isNotNull();
        mCarPmService = CarLocalServices.getService(CarPackageManagerService.class);
        assertThat(mCarPmService).isNotNull();
        mCarPmService.startAppBlockingPolicies();
    }

    @Test
    public void testServiceLaunched() throws Exception {
        init(true);
        Log.i(TAG, "testServiceLaunched, init called");
        assertThat(pollingCheck(new PollingChecker() {
            @Override
            public boolean check() {
                Log.i(TAG, "checking instance ...");
                return TestAppBlockingPolicyService.getInstance() != null;
            }
        }, POLLING_MAX_RETRY, POLLING_SLEEP)).isTrue();
        final String thisPackage = getContext().getPackageName();
        final String serviceClassName = "DOES_NOT_MATTER";
        assertThat(pollingCheck(
                () -> mCarPm.isServiceDistractionOptimized(thisPackage, serviceClassName),
                POLLING_MAX_RETRY,
                POLLING_SLEEP)).isTrue();
        assertThat(mCarPm.isServiceDistractionOptimized(thisPackage, null)).isTrue();
        assertThat(mCarPm.isServiceDistractionOptimized(serviceClassName,
                serviceClassName)).isFalse();
        assertThat(mCarPm.isServiceDistractionOptimized(serviceClassName, null)).isFalse();
    }

    // TODO(b/113531788): Suppress this temporarily. Need to find the cause of issue and re-evaluate
    // if the test is necessary.
    @Suppress
    @Test
    @FlakyTest
    public void testSettingAllowlist() throws Exception {
        init(false);
        final String carServicePackageName = "com.android.car";
        final String activityAllowed = "NO_SUCH_ACTIVITY_BUT_ALLOWED";
        final String activityNotAllowed = "NO_SUCH_ACTIVITY_AND_NOT_ALLOWED";
        final String acticityAllowed2 = "NO_SUCH_ACTIVITY_BUT_ALLOWED2";
        final String thisPackage = getContext().getPackageName();

        AppBlockingPackageInfo info = new AppBlockingPackageInfo(carServicePackageName, 0, 0,
                AppBlockingPackageInfo.FLAG_SYSTEM_APP, null, new String[] { activityAllowed });
        CarAppBlockingPolicy policy = new CarAppBlockingPolicy(new AppBlockingPackageInfo[] { info }
                , null);
        Log.i(TAG, "setting policy");
        mCarPm.setAppBlockingPolicy(thisPackage, policy,
                CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE);
        Log.i(TAG, "setting policy done");
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityAllowed)).isTrue();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityNotAllowed)).isFalse();

        // replace policy
        info = new AppBlockingPackageInfo(carServicePackageName, 0, 0,
                AppBlockingPackageInfo.FLAG_SYSTEM_APP, null, new String[] { acticityAllowed2 });
        policy = new CarAppBlockingPolicy(new AppBlockingPackageInfo[] { info }
                , null);
        mCarPm.setAppBlockingPolicy(thisPackage, policy,
                CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE);
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityAllowed)).isFalse();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                acticityAllowed2)).isTrue();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityNotAllowed)).isFalse();

        //add, it replace the whole package policy. So activities are not added.
        info = new AppBlockingPackageInfo(carServicePackageName, 0, 0,
                AppBlockingPackageInfo.FLAG_SYSTEM_APP, null, new String[] { activityAllowed });
        policy = new CarAppBlockingPolicy(new AppBlockingPackageInfo[] { info }
                , null);
        mCarPm.setAppBlockingPolicy(thisPackage, policy,
                CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE |
                CarPackageManager.FLAG_SET_POLICY_ADD);
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityAllowed)).isTrue();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                acticityAllowed2)).isFalse();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityNotAllowed)).isFalse();

        //remove
        info = new AppBlockingPackageInfo(carServicePackageName, 0, 0,
                AppBlockingPackageInfo.FLAG_SYSTEM_APP, null, new String[] { activityAllowed });
        policy = new CarAppBlockingPolicy(new AppBlockingPackageInfo[] { info }
                , null);
        mCarPm.setAppBlockingPolicy(thisPackage, policy,
                CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE |
                CarPackageManager.FLAG_SET_POLICY_REMOVE);
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityAllowed)).isFalse();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                acticityAllowed2)).isFalse();
        assertThat(mCarPm.isActivityDistractionOptimized(carServicePackageName,
                activityNotAllowed)).isFalse();
    }

    interface PollingChecker {
        boolean check();
    }

    static boolean pollingCheck(PollingChecker checker, int maxRetry, long sleepMs)
            throws Exception {
        int retry = 0;
        boolean checked = checker.check();
        while (!checked && (retry < maxRetry)) {
            Thread.sleep(sleepMs);
            retry++;
            checked = checker.check();
        }
        return checked;
    }
}
