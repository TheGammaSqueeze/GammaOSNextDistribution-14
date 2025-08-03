/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.cts;

import static android.Manifest.permission.QUERY_ALL_PACKAGES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.feature.Flags;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot see other packages")
public final class CarPackageManagerTest extends AbstractCarTestCase {

    private CarPackageManager mCarPm;
    private static String TAG = CarPackageManagerTest.class.getSimpleName();

    /** Name of the meta-data attribute for the automotive application XML resource */
    private static final String METADATA_ATTRIBUTE = "android.car.application";

    @Before
    public void setUp() throws Exception {
        mCarPm = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
    }

    @Test
    public void testActivityDistractionOptimized() throws Exception {
        assertThat(mCarPm.isActivityDistractionOptimized("com.basic.package", "DummyActivity"))
                .isFalse();

        try {
            mCarPm.isActivityDistractionOptimized("com.android.settings", null);
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            mCarPm.isActivityDistractionOptimized(null, "Any");
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            mCarPm.isActivityDistractionOptimized(null, null);
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void testDistractionOptimizedActivityIsAllowed() {
        assertThat(mCarPm.isActivityDistractionOptimized("com.android.car",
                "com.android.car.DistractionOptimizedActivityForTesting")).isTrue();
        if (Build.TYPE.equalsIgnoreCase("user")) {
            // Should be false as not from trusted source.
            assertThat(mCarPm.isActivityDistractionOptimized("android.car.cts",
                    "android.car.cts.drivingstate.DistractionOptimizedActivity")).isFalse();
        } else {
            assertThat(mCarPm.isActivityDistractionOptimized("android.car.cts",
                    "android.car.cts.drivingstate.DistractionOptimizedActivity")).isTrue();
        }
    }

    @Test
    @ApiTest(
            apis = {
                    "android.car.content.pm.CarPackageManager#isActivityBackedBySafeActivity"
            })
    public void testIsActivityBackedBySafeActivity_carNotMoving_nonDoActivity_returnsTrue() {
        ComponentName nonDoActivityComponent = ComponentName.createRelative(mContext,
                ".drivingstate.NonDistractionOptimizedActivity");

        InstrumentationRegistry.getInstrumentation().startActivitySync(
                Intent.makeMainActivity(nonDoActivityComponent)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), /* option */ null);

        assertThat(mCarPm.isActivityBackedBySafeActivity(nonDoActivityComponent)).isTrue();
    }

    @Test
    public void testIsServiceDistractionOptimized() {
        assertThat(mCarPm.isServiceDistractionOptimized("com.android.car",
                "anything.anything")).isTrue();
        if (Build.TYPE.equalsIgnoreCase("user")) {
            // Should be false as not from trusted source.
            assertThat(mCarPm.isServiceDistractionOptimized("android.car.cts",
                    "anything.anything")).isFalse();
        } else {
            assertThat(mCarPm.isServiceDistractionOptimized("android.car.cts",
                    "anything.anything")).isTrue();
        }
    }

    @Test
    public void testNonDistractionOptimizedActivityNotAllowed() {
        // Not distraction optimized, but from system app
        assertThat(mCarPm.isActivityDistractionOptimized("com.android.car",
                "com.android.car.NonDistractionOptimizedActivityForTesting")).isFalse();
        // Not distraction optimized, also not from trusted source
        assertThat(mCarPm.isActivityDistractionOptimized("android.car.cts",
                "android.car.cts.drivingstate.NonDistractionOptimizedActivity")).isFalse();
    }

    @Test
    public void testPendingIntentToDistractionOptimizedActivityIsAllowed() {
        assertThat(mCarPm.isPendingIntentDistractionOptimized(
                createIntent("com.android.car", ".DistractionOptimizedActivityForTesting")))
                        .isTrue();
    }

    @Test
    public void testPendingIntentToNonDistractionOptimizedActivityNotAllowed() {
        assertThat(mCarPm.isPendingIntentDistractionOptimized(
                createIntent("com.android.car", ".NonDistractionOptimizedActivityForTesting")))
                        .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.car.content.pm.CarPackageManager#requiresDisplayCompat"})
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_COMPATIBILITY)
    @Ignore("b/303074481") // Can't read flags when using `sdk_version: "test_current",`
    @EnsureHasPermission({Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY, QUERY_ALL_PACKAGES})
    public void testApplicationRequiresDisplayCompat() throws NameNotFoundException {
        assertThat(mCarPm.requiresDisplayCompat("android.car.cts.apps.displaycompat"))
                .isTrue();
    }

    private PendingIntent createIntent(String packageName, String relativeClassName) {
        Intent intent = new Intent();
        intent.setClassName(packageName, packageName + relativeClassName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }
}
