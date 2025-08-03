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

package android.healthconnect.cts.ratelimiter;

import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.buildDevice;
import static android.healthconnect.cts.utils.DataFactory.getCompleteStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getUpdatedStepsRecord;

import static org.hamcrest.CoreMatchers.containsString;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class RateLimiterTest {
    private static final String TAG = "RateLimiterTest";
    private static final int MAX_FOREGROUND_WRITE_CALL_15M = 1000;
    private static final int MAX_FOREGROUND_READ_CALL_15M = 2000;
    private static final Duration WINDOW_15M = Duration.ofMinutes(15);
    public static final String ENABLE_RATE_LIMITER_FLAG = "enable_rate_limiter";
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final boolean mRateLimiterFlagValue = getRateLimiterFlagValue();

    @Rule public ExpectedException exception = ExpectedException.none();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
        if (!mRateLimiterFlagValue) {
            setEnableRateLimiterFlag(true);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
        if (!mRateLimiterFlagValue) {
            setEnableRateLimiterFlag(false);
        }
    }

    @Test
    public void testTryAcquireApiCallQuota_writeCallsInLimit() throws InterruptedException {
        tryAcquireCallQuotaNTimesForWrite(MAX_FOREGROUND_WRITE_CALL_15M);
    }

    @Test
    public void testTryAcquireApiCallQuota_readCallsInLimit() throws InterruptedException {
        List<Record> testRecord = List.of(getCompleteStepsRecord());

        tryAcquireCallQuotaNTimesForRead(testRecord, TestUtils.insertRecords(testRecord));
    }

    @Test
    @ApiTest(apis = {"android.health.connect#insertRecords"})
    public void testTryAcquireApiCallQuota_writeLimitExceeded_flagEnabled()
            throws InterruptedException {
        exception.expect(HealthConnectException.class);
        exception.expectMessage(containsString("API call quota exceeded"));
        exceedWriteQuota();
    }

    @Test
    @ApiTest(apis = {"android.health.connect#insertRecords"})
    public void testTryAcquireApiCallQuota_writeLimitExceeded_flagDisabled()
            throws InterruptedException {
        setEnableRateLimiterFlag(false);
        exceedWriteQuota();
    }

    @Test
    @ApiTest(apis = {"android.health.connect#readRecords"})
    public void testTryAcquireApiCallQuota_readLimitExceeded_flagEnabled()
            throws InterruptedException {
        exception.expect(HealthConnectException.class);
        exception.expectMessage(containsString("API call quota exceeded"));
        exceedReadQuota();
    }

    @Test
    @ApiTest(apis = {"android.health.connect#readRecords"})
    public void testTryAcquireApiCallQuota_readLimitExceeded_flagDisabled()
            throws InterruptedException {
        setEnableRateLimiterFlag(false);
        exceedReadQuota();
    }

    @Test
    public void testChunkSizeLimitExceeded() throws InterruptedException {
        exception.expect(HealthConnectException.class);
        exception.expectMessage(containsString("Records chunk size exceeded the max chunk limit"));
        exceedChunkMemoryQuota();
    }

    @Test
    public void testRecordSizeLimitExceeded() throws InterruptedException {
        exception.expect(HealthConnectException.class);
        exception.expectMessage(
                containsString("Record size exceeded the single record size limit"));
        exceedRecordMemoryQuota();
    }

    @Test
    @ApiTest(apis = {"android.health.connect#insertRecords"})
    public void testRecordMemoryRollingQuota_foregroundCall_exceedBackgroundLimit()
            throws InterruptedException {
        // No exception expected.
        exceedRecordMemoryRollingQuotaBackgroundLimit();
    }

    private void exceedChunkMemoryQuota() throws InterruptedException {
        List<Record> testRecord = Collections.nCopies(30000, getCompleteStepsRecord());

        TestUtils.insertRecords(testRecord);
    }

    private void exceedRecordMemoryQuota() throws InterruptedException {
        Device device = buildDevice();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("HRR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, Instant.now().plusMillis(100));
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords =
                new ArrayList<>(Collections.nCopies(85000, heartRateRecord));

        HeartRateRecord testHeartRateRecord =
                new HeartRateRecord.Builder(
                                testMetadataBuilder.build(),
                                Instant.now(),
                                Instant.now().plusMillis(500),
                                heartRateRecords)
                        .build();
        TestUtils.insertRecords(List.of(testHeartRateRecord));
    }

    private void exceedRecordMemoryRollingQuotaBackgroundLimit() throws InterruptedException {
        List<Record> testRecord = Collections.nCopies(350, getCompleteStepsRecord());
        for (int i = 0; i < 1000; i++) {
            TestUtils.insertRecords(testRecord);
        }
    }

    private void exceedWriteQuota() throws InterruptedException {
        Instant startTime = Instant.now();
        tryAcquireCallQuotaNTimesForWrite(MAX_FOREGROUND_WRITE_CALL_15M);
        Instant endTime = Instant.now();
        float quotaAcquired =
                getQuotaAcquired(startTime, endTime, WINDOW_15M, MAX_FOREGROUND_WRITE_CALL_15M);
        List<Record> testRecord = List.of(getCompleteStepsRecord());

        while (quotaAcquired > 1) {
            TestUtils.insertRecords(testRecord);
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            TestUtils.insertRecords(List.of(getCompleteStepsRecord()));

            tryWriteWithBuffer--;
        }
    }

    private void exceedReadQuota() throws InterruptedException {
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequestUsingFilters =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(true)
                        .build();
        Instant startTime = Instant.now();
        List<Record> testRecord = Arrays.asList(getCompleteStepsRecord());

        List<Record> insertedRecords = TestUtils.insertRecords(testRecord);
        tryAcquireCallQuotaNTimesForRead(testRecord, insertedRecords);
        Instant endTime = Instant.now();
        float quotaAcquired =
                getQuotaAcquired(startTime, endTime, WINDOW_15M, MAX_FOREGROUND_READ_CALL_15M);
        while (quotaAcquired > 1) {
            readStepsRecordUsingIds(insertedRecords);
            quotaAcquired--;
        }
        int tryReadWithBuffer = 20;
        while (tryReadWithBuffer > 0) {
            TestUtils.readRecords(readRecordsRequestUsingFilters);
            tryReadWithBuffer--;
        }
    }

    private float getQuotaAcquired(
            Instant startTime, Instant endTime, Duration window, int maxQuota) {
        Duration timeSpent = Duration.between(startTime, endTime);
        return timeSpent.toMillis() * ((float) maxQuota / (float) window.toMillis());
    }

    /**
     * This method tries to use the Maximum read quota possible. Distributes the load to
     * ChangeLogToken, ChangeLog, Read, and Aggregate APIs.
     */
    private void tryAcquireCallQuotaNTimesForRead(
            List<Record> testRecord, List<Record> insertedRecords) throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        // 200 calls.
        for (int i = 0; i < 100; i++) {
            getChangeLog(context);
        }

        for (int i = 0; i < MAX_FOREGROUND_READ_CALL_15M - 300; i++) {
            readStepsRecordUsingIds(insertedRecords);
        }

        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.ofEpochMilli(0))
                                        .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        // 100 calls.
        for (int i = 0; i < 100; i++) {
            TestUtils.getAggregateResponse(aggregateRecordsRequest, testRecord);
        }
    }

    private void getChangeLog(Context context) throws InterruptedException {
        // Use one read quota.
        ChangeLogTokenResponse tokenResponse =
                TestUtils.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(StepsRecord.class)
                                .build());

        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        // Use one read quota.
        TestUtils.getChangeLogs(changeLogsRequest);
    }

    /**
     * This method tries to use the Maximum write quota possible. Distributes the load across
     * Insert, and Update APIs. Also, we provide dataManagement permission to
     * MultiAppTestUtils.verifyDeleteRecords. We test unmetered rate limting as well here. No write
     * quota is used by MultiAppTestUtils.verifyDeleteRecords.
     */
    private void tryAcquireCallQuotaNTimesForWrite(int nTimes) throws InterruptedException {
        List<Record> testRecord = Arrays.asList(getCompleteStepsRecord());

        List<Record> insertedRecords = List.of();
        for (int i = 0; i < nTimes; i++) {
            if (i % 3 == 0) {
                insertedRecords = TestUtils.insertRecords(testRecord);
            } else if (i % 3 == 1) {
                List<Record> updateRecords = Arrays.asList(getCompleteStepsRecord());

                for (int itr = 0; itr < updateRecords.size(); itr++) {
                    updateRecords.set(
                            itr,
                            getUpdatedStepsRecord(
                                    updateRecords.get(itr),
                                    insertedRecords.get(itr).getMetadata().getId(),
                                    insertedRecords.get(itr).getMetadata().getClientRecordId()));
                }
                TestUtils.updateRecords(updateRecords);
            } else {
                TestUtils.insertRecords(testRecord);
                // Unmetered rate limiting as Holds data management is true for verify delete
                // records.
                TestUtils.verifyDeleteRecords(
                        new DeleteUsingFiltersRequest.Builder()
                                .addRecordType(StepsRecord.class)
                                .build());
            }
        }
    }

    private void readStepsRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        recordList.forEach(v -> request.addId(v.getMetadata().getId()));
        TestUtils.readRecords(request.build());
    }

    private boolean getRateLimiterFlagValue() {
        mUiAutomation.adoptShellPermissionIdentity("android.permission.READ_DEVICE_CONFIG");
        DeviceConfig.Properties properties =
                DeviceConfig.getProperties(
                        DeviceConfig.NAMESPACE_HEALTH_FITNESS, ENABLE_RATE_LIMITER_FLAG);
        boolean flagValue = true;
        if (properties.getKeyset().contains(ENABLE_RATE_LIMITER_FLAG)) {
            flagValue = properties.getBoolean(ENABLE_RATE_LIMITER_FLAG, true);
        }
        mUiAutomation.dropShellPermissionIdentity();
        return flagValue;
    }

    private void setEnableRateLimiterFlag(boolean flag) throws InterruptedException {
        if (SdkLevel.isAtLeastU()) {
            mUiAutomation.adoptShellPermissionIdentity(
                    "android.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG");
        } else {
            mUiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_DEVICE_CONFIG");
        }

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_HEALTH_FITNESS,
                ENABLE_RATE_LIMITER_FLAG,
                flag ? "true" : "false",
                true);
        mUiAutomation.dropShellPermissionIdentity();
        Thread.sleep(100);
    }
}
