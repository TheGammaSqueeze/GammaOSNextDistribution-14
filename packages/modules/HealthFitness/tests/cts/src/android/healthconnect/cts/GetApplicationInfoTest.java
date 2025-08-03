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

import static android.healthconnect.cts.utils.DataFactory.getTestRecords;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.ApplicationInfoResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.AppInfo;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class GetApplicationInfoTest {
    private static final String TAG = "GetApplicationInfoTest";
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    /** TODO(b/257796081): Cleanup the database after each test. */
    @Test
    public void testEmptyApplicationInfo() throws InterruptedException {
        sUiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        try {
            Context context = ApplicationProvider.getApplicationContext();
            HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
            CountDownLatch latch = new CountDownLatch(1);
            assertThat(service).isNotNull();
            AtomicReference<List<AppInfo>> response = new AtomicReference<>();
            service.getContributorApplicationsInfo(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(ApplicationInfoResponse result) {
                            response.set(result.getApplicationInfoList());
                            latch.countDown();
                        }

                        @Override
                        public void onError(HealthConnectException exception) {
                            Log.e(TAG, exception.getMessage());
                        }
                    });
            assertThat(latch.await(3, TimeUnit.SECONDS)).isEqualTo(true);
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }

        /** TODO(b/257796081): Test the response size after database clean up is implemented */
        // assertThat(response.get()).hasSize(0);
    }

    @Test
    public void testEmptyApplicationInfo_no_perm() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(service).isNotNull();
        AtomicReference<List<AppInfo>> response = new AtomicReference<>();
        AtomicReference<HealthConnectException> healthConnectExceptionAtomicReference =
                new AtomicReference<>();
        try {
            TestUtils.getApplicationInfo();
            Assert.fail("Reading app info must not be allowed without right HC permission");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void testGetApplicationInfo() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        TestUtils.insertRecords(getTestRecords());
        sUiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        try {
            // Wait for some time, as app info table will be  updated in the background so might
            // take some additional time.
            latch.await(1, TimeUnit.SECONDS);

            List<AppInfo> result = TestUtils.getApplicationInfo();
            assertThat(result).hasSize(1);

            AppInfo appInfo = result.get(0);

            assertThat(appInfo.getPackageName())
                    .isEqualTo(context.getApplicationInfo().packageName);
            assertThat(appInfo.getName())
                    .isEqualTo(
                            context.getPackageManager()
                                    .getApplicationLabel(context.getApplicationInfo()));
            assertThat(appInfo.getIcon()).isNotNull();
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
    }
}
