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

package android.car.apitest;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SmallTest
public class DrivingSafetyRegionTest extends CarApiTestBase {
    private static final String REGION1 = "com.android.car.test.drivingsafetyregion.1";
    private static final String REGION2 = "com.android.car.test.drivingsafetyregion.2";
    private static final String REGION3 = "com.android.car.test.drivingsafetyregion.3";

    private static final String TEST_PACKAGE_NAME = "android.car.apitest";

    private CarPackageManager mCarPackageManager;
    private String mOriginalDrivingSafetyRegion = null;

    private final int mCurrentUser = ActivityManager.getCurrentUser();

    @Before
    public void setUp() {
        mCarPackageManager = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);

        assertThat(mCarPackageManager).isNotNull();

        mOriginalDrivingSafetyRegion = mCarPackageManager.getCurrentDrivingSafetyRegion();

        assertThat(mOriginalDrivingSafetyRegion).isNotNull();

        // cannot run this in user build as region change is not allowed in user build for shell.
        assumeTrue(Build.IS_ENG || Build.IS_USERDEBUG);
    }

    @After
    public void tearDown() {
        if (mOriginalDrivingSafetyRegion != null) {
            setDrivingSafetyRegion(mOriginalDrivingSafetyRegion);
        }
    }

    @Test
    public void testImplicitAllRegions() throws Exception {
        doTestAllRegions(TestDrivingSafetyAllRegionActivity.class.getName());
    }

    @Test
    public void testExplicitAllRegions() throws Exception {
        doTestAllRegions(TestDrivingSafetyExplicitAllRegionsActivity.class.getName());
    }

    private void doTestAllRegions(String activityClassName) throws Exception {
        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        List<String> regions = mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                TEST_PACKAGE_NAME, activityClassName, ActivityManager.getCurrentUser());

        assertThat(regions).containsExactly(CarPackageManager.DRIVING_SAFETY_REGION_ALL);

        // all region app should be safe always regardless of bypassing / region change
        setDrivingSafetyRegion(REGION1);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, true, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, false, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();
    }

    @Test
    public void testOneRegionOnly() throws Exception {
        String activityClassName = TestDrivingSafetyOneRegionActivity.class.getName();

        List<String> regions = mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                TEST_PACKAGE_NAME, activityClassName, ActivityManager.getCurrentUser());

        assertThat(regions).containsExactly(REGION1);

        setDrivingSafetyRegion(REGION1);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        setDrivingSafetyRegion(REGION2);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, true, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, false, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        setDrivingSafetyRegion(CarPackageManager.DRIVING_SAFETY_REGION_ALL);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();
    }

    @Test
    public void testTwoRegionsOnly() throws Exception {
        String activityClassName = TestDrivingSafetyTwoRegionsActivity.class.getName();

        List<String> regions = mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                TEST_PACKAGE_NAME, activityClassName, ActivityManager.getCurrentUser());

        assertThat(regions).containsExactly(REGION1, REGION2);

        setDrivingSafetyRegion(REGION1);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        setDrivingSafetyRegion(REGION2);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        setDrivingSafetyRegion(REGION3);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        setDrivingSafetyRegion(CarPackageManager.DRIVING_SAFETY_REGION_ALL);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();
    }

    @Test
    public void testRegion1OnlyActivity() throws Exception {
        doTestRegionOnlyOrNoRegionCase(TestDrivingSafetyRegion1OnlyActivity.class.getName());
    }

    @Test
    public void testRegionAllOnlyActivity() throws Exception {
        doTestRegionOnlyOrNoRegionCase(TestDrivingSafetyRegionAllOnlyActivity.class.getName());
    }

    @Test
    public void testRegionNoMetadataActivity() throws Exception {
        doTestRegionOnlyOrNoRegionCase(TestDrivingSafetyRegionNoMetadataActivity.class.getName());
    }

    private void doTestRegionOnlyOrNoRegionCase(String activityClassName) throws Exception {
        List<String> regions = mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                TEST_PACKAGE_NAME, activityClassName, ActivityManager.getCurrentUser());

        // not distraction optimized, so list should be empty.
        assertThat(regions).isEmpty();

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        // should not be safe for any region.
        setDrivingSafetyRegion(CarPackageManager.DRIVING_SAFETY_REGION_ALL);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        setDrivingSafetyRegion(REGION1);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        setDrivingSafetyRegion(REGION2);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, true, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isTrue();

        mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(TEST_PACKAGE_NAME,
                activityClassName, false, mCurrentUser);

        assertThat(mCarPackageManager.isActivityDistractionOptimized(TEST_PACKAGE_NAME,
                activityClassName)).isFalse();
    }

    @Test
    public void testNoPackage() {
        String noPkg = "NoSuchPackage";

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                        noPkg, "", mCurrentUser));

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(
                        noPkg, "", true, mCurrentUser));
    }

    @Test
    public void testNoActivity() {
        String noSuchActivity = "NoSuchActivity";

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mCarPackageManager.getSupportedDrivingSafetyRegionsForActivityAsUser(
                        TEST_PACKAGE_NAME, noSuchActivity, mCurrentUser));

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mCarPackageManager.controlTemporaryActivityBlockingBypassingAsUser(
                        TEST_PACKAGE_NAME, noSuchActivity, true, mCurrentUser));
    }

    @Test
    public void testResetEmptyRegion() {
        setDrivingSafetyRegion(REGION1);

        assertThat(mCarPackageManager.getCurrentDrivingSafetyRegion()).isEqualTo(REGION1);

        // no arg means all
        runShellCommand("cmd car_service set-drivingsafety-region");

        assertThat(mCarPackageManager.getCurrentDrivingSafetyRegion()).isEqualTo(
                CarPackageManager.DRIVING_SAFETY_REGION_ALL);
    }

    private void setDrivingSafetyRegion(String region) {
        runShellCommand("cmd car_service set-drivingsafety-region  " + region);
    }
}
