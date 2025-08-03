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
package android.car.cts.permissiontest.content.pm;

import static android.Manifest.permission.QUERY_ALL_PACKAGES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarPackageManager;
import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.feature.Flags;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.ComponentName;
import android.content.pm.PackageManager.NameNotFoundException;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for {@link CarPackageManager}.
 */
@RunWith(AndroidJUnit4.class)
public class CarPackageManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private CarPackageManager mPm;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mPm = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);
    }

    @Test
    public void testRestartTask() {
        assertThrows(SecurityException.class, () -> mPm.restartTask(0));
    }

    @Test
    public void testSetAppBlockingPolicy() {
        String packageName = "com.android";
        CarAppBlockingPolicy policy = new CarAppBlockingPolicy(null, null);
        assertThrows(SecurityException.class, () -> mPm.setAppBlockingPolicy(packageName, policy,
                0));
    }

    @Test
    public void testIsActivityDistractionOptimized() {
        assertThat(mPm.isActivityDistractionOptimized("blah", "someClass")).isFalse();
    }

    @Test
    public void testIsServiceDistractionOptimized() {
        assertThat(mPm.isServiceDistractionOptimized("blah", "someClass")).isFalse();
    }

    @Test
    public void testIsActivityBackedBySafeActivity() {
        assertThat(mPm.isActivityBackedBySafeActivity(new ComponentName("blah", "someClass")))
                .isFalse();
    }

    @Test
    public void testGetTargetCarApiVersion() {
        assertThrows(SecurityException.class, () -> mPm.getTargetCarVersion("Y U NO THROW?"));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_COMPATIBILITY)
    public void testActivityRequiresDisplayCompatFailsWithoutPermission() {
        assertThrows(SecurityException.class, () -> mPm.requiresDisplayCompat("blah"));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_COMPATIBILITY)
    @EnsureHasPermission({Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY, QUERY_ALL_PACKAGES})
    public void testActivityRequiresDisplayCompatWorksWithPermission()
            throws NameNotFoundException {
        assertThat(mPm.requiresDisplayCompat("android.car.cts.permissiontest"))
                .isFalse();
    }
}
