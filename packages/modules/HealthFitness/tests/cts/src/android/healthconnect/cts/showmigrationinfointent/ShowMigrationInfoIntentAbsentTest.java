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

package android.healthconnect.cts.showmigrationinfointent;

import static android.Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.migration.MigrationException;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.os.ext.SdkExtensions;
import android.platform.test.annotations.AppModeFull;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class ShowMigrationInfoIntentAbsentTest {
    private Context mContext;
    private HealthConnectManager mManager;
    UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mManager = mContext.getSystemService(HealthConnectManager.class);
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test(expected = MigrationException.class)
    public void testInsertMinDataMigrationSdkExtensionVersion_throwsException()
            throws InterruptedException {
        int version = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) + 1;
        uiAutomation.adoptShellPermissionIdentity(MIGRATE_HEALTH_CONNECT_DATA);
        TestUtils.insertMinDataMigrationSdkExtensionVersion(version);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test(expected = MigrationException.class)
    public void testStartMigration_throwsException() throws InterruptedException {
        uiAutomation.adoptShellPermissionIdentity(MIGRATE_HEALTH_CONNECT_DATA);
        TestUtils.startMigration();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test(expected = MigrationException.class)
    public void testFinishMigration_throwsException() throws InterruptedException {
        uiAutomation.adoptShellPermissionIdentity(MIGRATE_HEALTH_CONNECT_DATA);
        TestUtils.finishMigration();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test(expected = MigrationException.class)
    public void testWriteMigrationData_throwsException() throws InterruptedException {
        uiAutomation.adoptShellPermissionIdentity(MIGRATE_HEALTH_CONNECT_DATA);
        writeMigrationData();
        uiAutomation.dropShellPermissionIdentity();
    }

    private void writeMigrationData() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MigrationException> migrationExceptionAtomicReference =
                new AtomicReference<>();
        mManager.writeMigrationData(
                Collections.emptyList(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onResult(Void result) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(MigrationException exception) {
                        migrationExceptionAtomicReference.set(exception);
                        latch.countDown();
                    }
                });
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        if (migrationExceptionAtomicReference.get() != null) {
            throw migrationExceptionAtomicReference.get();
        }
    }
}
