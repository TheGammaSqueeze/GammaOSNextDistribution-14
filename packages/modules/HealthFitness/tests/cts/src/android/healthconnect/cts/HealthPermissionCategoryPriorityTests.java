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

package android.healthconnect.cts;

import static android.health.connect.HealthDataCategory.ACTIVITY;
import static android.health.connect.HealthDataCategory.BODY_MEASUREMENTS;
import static android.health.connect.HealthDataCategory.CYCLE_TRACKING;
import static android.health.connect.HealthDataCategory.NUTRITION;
import static android.health.connect.HealthDataCategory.SLEEP;
import static android.health.connect.HealthDataCategory.VITALS;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.TestUtils.getPriority;
import static android.healthconnect.cts.utils.TestUtils.getPriorityWithManageHealthDataPermission;
import static android.healthconnect.cts.utils.TestUtils.updatePriority;
import static android.healthconnect.cts.utils.TestUtils.updatePriorityWithManageHealthDataPermission;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.health.connect.FetchDataOriginsPriorityOrderResponse;
import android.health.connect.HealthConnectException;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class HealthPermissionCategoryPriorityTests {
    private static final Set<Integer> sAllDataCategories =
            Set.of(ACTIVITY, BODY_MEASUREMENTS, CYCLE_TRACKING, NUTRITION, SLEEP, VITALS);
    private static final String TAG = "PermissionCategoryPriorityTests";
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    public static final String PACKAGE_NAME = "android.healthconnect.cts";
    public static final String OTHER_PACKAGE_NAME = "";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testGetPriority() throws InterruptedException {
        for (Integer permissionCategory : sAllDataCategories) {
            assertThat(getPriorityWithManageHealthDataPermission(permissionCategory)).isNotNull();
        }
    }

    @Test
    public void testGetPriority_no_perm() throws InterruptedException {
        for (Integer permissionCategory : sAllDataCategories) {
            try {
                getPriority(permissionCategory);
                Assert.fail("Get Priority must not be allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testUpdatePriority_withNewApps_updatesCorrectly() throws InterruptedException {
        sUiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        try {
            for (Integer permissionCategory : sAllDataCategories) {

                FetchDataOriginsPriorityOrderResponse currentPriority =
                        getPriorityWithManageHealthDataPermission(permissionCategory);
                assertThat(currentPriority).isNotNull();
                // The initial priority list is empty at this stage because permissions have
                // been granted through packageManager
                // TODO (b/314092270) - remove when the priority list is updated via the package
                // manager
                assertThat(currentPriority.getDataOriginsPriorityOrder()).isEmpty();

                List<String> newPriorityListPackages = Arrays.asList(PACKAGE_NAME);
                updatePriorityWithManageHealthDataPermission(
                        permissionCategory, newPriorityListPackages);
                FetchDataOriginsPriorityOrderResponse newPriority =
                        getPriorityWithManageHealthDataPermission(permissionCategory);

                assertThat(newPriority.getDataOriginsPriorityOrder().size()).isEqualTo(1);
                assertThat(newPriority.getDataOriginsPriorityOrder().get(0).getPackageName())
                        .isEqualTo(PACKAGE_NAME);
            }
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testUpdatePriority_no_perm() throws InterruptedException {
        for (Integer permissionCategory : sAllDataCategories) {
            try {
                updatePriority(permissionCategory, Arrays.asList("a", "b", "c"));
                Assert.fail("Update priority must not be allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    // TODO(b/261618513): Test actual priority order by using other test apps
}
