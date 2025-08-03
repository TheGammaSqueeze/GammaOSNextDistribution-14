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

package android.healthconnect.tests.migration;

import static android.health.connect.HealthPermissions.MANAGE_HEALTH_PERMISSIONS;
import static android.health.connect.HealthPermissions.READ_ACTIVE_CALORIES_BURNED;
import static android.health.connect.HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;

import static com.android.compatibility.common.util.FeatureUtil.AUTOMOTIVE_FEATURE;
import static com.android.compatibility.common.util.FeatureUtil.hasSystemFeature;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.PermissionMigrationPayload;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.healthconnect.tests.IntegrationTestUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Integration tests for Health Connect permissions migration. */
@RunWith(AndroidJUnit4.class)
public class HealthConnectPermissionsMigrationTest {
    private static final String DEFAULT_APP_PACKAGE = "android.healthconnect.test.app";
    private static final String TAG = "HealthConnectPermissionsMigrationTest";
    private static final Period GRANT_TIME_TO_START_ACCESS_DATE_PERIOD = Period.ofDays(30);
    private Context mContext;
    private HealthConnectManager mHealthConnectManager;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mHealthConnectManager = mContext.getSystemService(HealthConnectManager.class);
        revokeAllHealthPermissions(DEFAULT_APP_PACKAGE, null);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, READ_ACTIVE_CALORIES_BURNED);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, WRITE_ACTIVE_CALORIES_BURNED);
        deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testMigratePermissions_permissionFirstGrantTimeMigrated()
            throws InterruptedException {
        final String entityId = "permissions";
        Instant firstGrantTime = Instant.now();
        assertThat(readDataHistoricalAccessStartDate(DEFAULT_APP_PACKAGE)).isNull();
        migrate(
                new MigrationEntity(
                        entityId,
                        new PermissionMigrationPayload.Builder(DEFAULT_APP_PACKAGE, firstGrantTime)
                                .addPermission(READ_ACTIVE_CALORIES_BURNED)
                                .addPermission(WRITE_ACTIVE_CALORIES_BURNED)
                                .build()));

        Instant readDataHistoricalAccessStartDate =
                readDataHistoricalAccessStartDate(DEFAULT_APP_PACKAGE);
        assertThat(firstGrantTime.minus(GRANT_TIME_TO_START_ACCESS_DATE_PERIOD).toEpochMilli())
                .isEqualTo(readDataHistoricalAccessStartDate.toEpochMilli());
    }

    private Instant readDataHistoricalAccessStartDate(String packageName) {
        AtomicReference<Instant> readGrantTime = new AtomicReference<>();
        runWithShellPermissionIdentity(
                () -> {
                    readGrantTime.set(
                            mHealthConnectManager.getHealthDataHistoricalAccessStartDate(
                                    packageName));
                },
                MANAGE_HEALTH_PERMISSIONS);
        return readGrantTime.get();
    }

    private void migrate(MigrationEntity... entities) {
        runWithShellPermissionIdentity(
                IntegrationTestUtils::startMigration,
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        runWithShellPermissionIdentity(
                () -> IntegrationTestUtils.writeMigrationData(List.of(entities)),
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        runWithShellPermissionIdentity(
                IntegrationTestUtils::finishMigration,
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
    }

    private void assertPermNotGrantedForApp(String packageName, String permName) {
        assertThat(mContext.getPackageManager().checkPermission(permName, packageName))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    private void revokeAllHealthPermissions(String packageName, String reason) {
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        mHealthConnectManager.revokeAllHealthPermissions(packageName, reason);
                    },
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private static boolean isHardwareAutomotive() {
        return hasSystemFeature(AUTOMOTIVE_FEATURE);
    }
}
