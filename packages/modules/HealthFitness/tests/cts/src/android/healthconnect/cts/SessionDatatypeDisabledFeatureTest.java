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

package android.healthconnect.cts;

import static android.healthconnect.cts.utils.DataFactory.buildExerciseSession;
import static android.healthconnect.cts.utils.DataFactory.buildSleepSession;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class SessionDatatypeDisabledFeatureTest {
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private final TimeInstantRangeFilter mFilterAll =
            new TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        setSessionDatatypesFeatureEnabledFlag(true);
        TestUtils.verifyDeleteRecords(SleepSessionRecord.class, mFilterAll);
        TestUtils.verifyDeleteRecords(ExerciseSessionRecord.class, mFilterAll);
    }

    @Test
    public void testWriteExerciseSession_insertWithDisableFeature_throwsException()
            throws InterruptedException {
        setSessionDatatypesFeatureEnabledFlag(false);
        List<Record> records = List.of(buildExerciseSession());
        try {
            TestUtils.insertRecords(records);
            Assert.fail("Writing exercise session when flag is disabled should not be allowed");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_UNSUPPORTED_OPERATION);
        }
    }

    @Test
    public void testReadExerciseSession_insertAndRead_sessionIsNotAvailable()
            throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession());
        TestUtils.insertRecords(records);
        setSessionDatatypesFeatureEnabledFlag(false);
        ReadRecordsRequestUsingIds.Builder<ExerciseSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());
        List<ExerciseSessionRecord> readRecords = TestUtils.readRecords(request.build());
        assertThat(readRecords).isEmpty();
    }

    @Test
    public void testWriteSleepSession_insertWithDisableFeature_throwsException()
            throws InterruptedException {
        setSessionDatatypesFeatureEnabledFlag(false);
        List<Record> records = List.of(buildSleepSession());
        try {
            TestUtils.insertRecords(records);
            Assert.fail("Writing sleep session when flag is disabled should not be allowed");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_UNSUPPORTED_OPERATION);
        }
    }

    @Test
    public void testReadSleepSession_insertAndRead_sessionIsNotAvailable()
            throws InterruptedException {
        List<Record> records = List.of(buildSleepSession());
        TestUtils.insertRecords(records);
        setSessionDatatypesFeatureEnabledFlag(false);

        ReadRecordsRequestUsingIds.Builder<SleepSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SleepSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());
        List<SleepSessionRecord> readRecords = TestUtils.readRecords(request.build());
        assertThat(readRecords).isEmpty();
    }

    private void setSessionDatatypesFeatureEnabledFlag(boolean flag) throws InterruptedException {
        if (SdkLevel.isAtLeastU()) {
            mUiAutomation.adoptShellPermissionIdentity(
                    "android.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG");
        } else {
            mUiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_DEVICE_CONFIG");
        }

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_HEALTH_FITNESS,
                "session_types_enable",
                flag ? "true" : "false",
                false);
        mUiAutomation.dropShellPermissionIdentity();
        Thread.sleep(100);
    }
}
