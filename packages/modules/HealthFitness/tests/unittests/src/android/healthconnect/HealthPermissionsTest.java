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

package android.healthconnect;

import static android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP;
import static android.health.connect.HealthPermissions.READ_EXERCISE_ROUTE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.reset;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class HealthPermissionsTest {
    private static final String FAIL_MESSAGE =
            "Add new health permission to ALL_EXPECTED_HEALTH_PERMISSIONS and "
                    + " android.healthconnect.cts.HealthPermissionsPresenceTest.HEALTH_PERMISSIONS "
                    + "sets.";

    // Add new health permission to ALL_EXPECTED_HEALTH_PERMISSIONS and
    // {@link android.healthconnect.cts.HealthPermissionsPresenceTest.HEALTH_PERMISSIONS}
    // sets.
    private static final Set<String> ALL_EXPECTED_HEALTH_PERMISSIONS =
            Set.of(
                    HealthPermissions.READ_HEALTH_DATA_HISTORY,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                    HealthPermissions.READ_ACTIVE_CALORIES_BURNED,
                    HealthPermissions.READ_DISTANCE,
                    HealthPermissions.READ_ELEVATION_GAINED,
                    HealthPermissions.READ_EXERCISE,
                    HealthPermissions.READ_EXERCISE_ROUTES,
                    HealthPermissions.READ_PLANNED_EXERCISE,
                    HealthPermissions.READ_FLOORS_CLIMBED,
                    HealthPermissions.READ_STEPS,
                    HealthPermissions.READ_TOTAL_CALORIES_BURNED,
                    HealthPermissions.READ_VO2_MAX,
                    HealthPermissions.READ_WHEELCHAIR_PUSHES,
                    HealthPermissions.READ_POWER,
                    HealthPermissions.READ_SPEED,
                    HealthPermissions.READ_BASAL_METABOLIC_RATE,
                    HealthPermissions.READ_BODY_FAT,
                    HealthPermissions.READ_BODY_WATER_MASS,
                    HealthPermissions.READ_BONE_MASS,
                    HealthPermissions.READ_HEIGHT,
                    HealthPermissions.READ_LEAN_BODY_MASS,
                    HealthPermissions.READ_WEIGHT,
                    HealthPermissions.READ_CERVICAL_MUCUS,
                    HealthPermissions.READ_INTERMENSTRUAL_BLEEDING,
                    HealthPermissions.READ_MENSTRUATION,
                    HealthPermissions.READ_OVULATION_TEST,
                    HealthPermissions.READ_SEXUAL_ACTIVITY,
                    HealthPermissions.READ_HYDRATION,
                    HealthPermissions.READ_NUTRITION,
                    HealthPermissions.READ_SLEEP,
                    HealthPermissions.READ_BASAL_BODY_TEMPERATURE,
                    HealthPermissions.READ_BLOOD_GLUCOSE,
                    HealthPermissions.READ_BLOOD_PRESSURE,
                    HealthPermissions.READ_BODY_TEMPERATURE,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.READ_HEART_RATE_VARIABILITY,
                    HealthPermissions.READ_OXYGEN_SATURATION,
                    HealthPermissions.READ_RESPIRATORY_RATE,
                    HealthPermissions.READ_RESTING_HEART_RATE,
                    HealthPermissions.READ_SKIN_TEMPERATURE,
                    HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED,
                    HealthPermissions.WRITE_DISTANCE,
                    HealthPermissions.WRITE_ELEVATION_GAINED,
                    HealthPermissions.WRITE_EXERCISE,
                    HealthPermissions.WRITE_EXERCISE_ROUTE,
                    HealthPermissions.WRITE_PLANNED_EXERCISE,
                    HealthPermissions.WRITE_FLOORS_CLIMBED,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.WRITE_TOTAL_CALORIES_BURNED,
                    HealthPermissions.WRITE_VO2_MAX,
                    HealthPermissions.WRITE_WHEELCHAIR_PUSHES,
                    HealthPermissions.WRITE_POWER,
                    HealthPermissions.WRITE_SPEED,
                    HealthPermissions.WRITE_BASAL_METABOLIC_RATE,
                    HealthPermissions.WRITE_BODY_FAT,
                    HealthPermissions.WRITE_BODY_WATER_MASS,
                    HealthPermissions.WRITE_BONE_MASS,
                    HealthPermissions.WRITE_HEIGHT,
                    HealthPermissions.WRITE_LEAN_BODY_MASS,
                    HealthPermissions.WRITE_WEIGHT,
                    HealthPermissions.WRITE_CERVICAL_MUCUS,
                    HealthPermissions.WRITE_INTERMENSTRUAL_BLEEDING,
                    HealthPermissions.WRITE_MENSTRUATION,
                    HealthPermissions.WRITE_OVULATION_TEST,
                    HealthPermissions.WRITE_SEXUAL_ACTIVITY,
                    HealthPermissions.WRITE_HYDRATION,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_BASAL_BODY_TEMPERATURE,
                    HealthPermissions.WRITE_BLOOD_GLUCOSE,
                    HealthPermissions.WRITE_BLOOD_PRESSURE,
                    HealthPermissions.WRITE_BODY_TEMPERATURE,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.WRITE_HEART_RATE_VARIABILITY,
                    HealthPermissions.WRITE_OXYGEN_SATURATION,
                    HealthPermissions.WRITE_RESPIRATORY_RATE,
                    HealthPermissions.WRITE_RESTING_HEART_RATE,
                    HealthPermissions.WRITE_SKIN_TEMPERATURE);
    private PackageManager mPackageManager;
    private Context mContext;
    @Mock private PackageInfo mPackageInfo1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() {
        reset(mPackageInfo1);
    }

    @Test
    public void testHealthGroupPermissions_noUnexpectedPermissionsDefined() throws Exception {
        PermissionInfo[] permissionInfos = getHealthPermissionInfos();
        for (PermissionInfo permissionInfo : permissionInfos) {
            if (permissionInfo.group != null
                    && permissionInfo.group.equals(HEALTH_PERMISSION_GROUP)) {
                assertWithMessage(FAIL_MESSAGE)
                        .that(ALL_EXPECTED_HEALTH_PERMISSIONS)
                        .contains(permissionInfo.name);
            }
        }
    }

    @Test
    public void testHealthConnectManager_noUnexpectedPermissionsReturned() throws Exception {
        assertWithMessage(FAIL_MESSAGE)
                .that(HealthConnectManager.getHealthPermissions(mContext))
                .isEqualTo(ALL_EXPECTED_HEALTH_PERMISSIONS);
    }

    @Test
    public void testReadExerciseRoutePerm_hasSignatureProtection() throws Exception {
        assertThat(
                        mPackageManager
                                .getPermissionInfo(READ_EXERCISE_ROUTE, /* flags= */ 0)
                                .getProtection())
                .isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
    }

    @Test
    public void testReadExerciseRoutePerm_controllerUiHoldsIt() throws Exception {
        String healthControllerPackageName = getControllerUiPackageName();
        assertThat(
                        mContext.getPackageManager()
                                .checkPermission(READ_EXERCISE_ROUTE, healthControllerPackageName))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void testGetDataCategoriesWithWritePermissionsForPackage_returnsCorrectSet() {
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        Set<Integer> expectedResult =
                Set.of(HealthDataCategory.ACTIVITY, HealthDataCategory.CYCLE_TRACKING);
        Set<Integer> actualResult =
                HealthPermissions.getDataCategoriesWithWritePermissionsForPackage(
                        mPackageInfo1, mContext);
        assertThat(actualResult).isEqualTo(expectedResult);
    }

    @Test
    public void
            testPackageHasWriteHealthPermissionsForCategory_ifNoWritePermissions_returnsFalse() {
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_EXERCISE,
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        assertThat(
                        HealthPermissions.getPackageHasWriteHealthPermissionsForCategory(
                                mPackageInfo1, HealthDataCategory.SLEEP, mContext))
                .isFalse();
    }

    @Test
    public void testPackageHasWriteHealthPermissionsForCategory_ifWritePermissions_returnsTrue() {
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        assertThat(
                        HealthPermissions.getPackageHasWriteHealthPermissionsForCategory(
                                mPackageInfo1, HealthDataCategory.ACTIVITY, mContext))
                .isTrue();
    }

    private PermissionInfo[] getHealthPermissionInfos() throws Exception {
        String healthControllerPackageName = getControllerUiPackageName();

        PackageInfo packageInfo =
                mPackageManager.getPackageInfo(
                        healthControllerPackageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
        return packageInfo.permissions;
    }

    private String getControllerUiPackageName() throws Exception {
        return mPackageManager.getPermissionInfo(HealthPermissions.READ_STEPS, /* flags= */ 0)
                .packageName;
    }
}
