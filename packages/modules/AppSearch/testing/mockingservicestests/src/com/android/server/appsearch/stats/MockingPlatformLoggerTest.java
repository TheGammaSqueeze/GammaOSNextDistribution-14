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

package com.android.server.appsearch.stats;

import static com.android.internal.util.ConcurrentUtils.DIRECT_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.appsearch.FrameworkAppSearchConfig;
import com.android.server.appsearch.FrameworkAppSearchConfigImpl;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.util.ApiCallRecord;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests covering the functionalities in {@link PlatformLogger} requiring overriding some flags
 * in {@link DeviceConfig}.
 *
 * <p>To add tests NOT rely on overriding the configs, please add them in
 * the tests for {@link PlatformLogger} in servicetests.
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("GuardedBy")
public class MockingPlatformLoggerTest {
    private static final int TEST_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS = 100;
    private static final int TEST_DEFAULT_SAMPLING_INTERVAL = 10;
    private static final String TEST_PACKAGE_NAME = "packageName";
    private FrameworkAppSearchConfig mAppSearchConfig;

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule
            mDeviceConfigRule = new TestableDeviceConfig.TestableDeviceConfigRule();

    @Before
    public void setUp() throws Exception {
        mAppSearchConfig = FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
    }

    @Test
    public void testCreateExtraStatsLocked_samplingIntervalNotSet_returnsDefault() {
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(TEST_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(TEST_DEFAULT_SAMPLING_INTERVAL),
                false);

        // Make sure default sampling interval is used if there is no config set.
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_UNKNOWN).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_INITIALIZE).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_SEARCH).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_FLUSH).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);
    }


    @Test
    public void testCreateExtraStatsLocked_samplingIntervalSet_returnsConfigured() {
        int putDocumentSamplingInterval = 1;
        int batchCallSamplingInterval = 2;
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(), mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(TEST_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(TEST_DEFAULT_SAMPLING_INTERVAL),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(putDocumentSamplingInterval),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                Integer.toString(batchCallSamplingInterval),
                false);

        // The default sampling interval should be used if no sampling interval is
        // provided for certain call type.
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_INITIALIZE).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_FLUSH).mSamplingInterval).isEqualTo(
                TEST_DEFAULT_SAMPLING_INTERVAL);

        // The configured sampling interval is used if sampling interval is available
        // for certain call type.
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_PUT_DOCUMENT).mSamplingInterval).isEqualTo(
                putDocumentSamplingInterval);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_PUT_DOCUMENTS).mSamplingInterval).isEqualTo(
                batchCallSamplingInterval);
        assertThat(logger.createExtraStatsLocked(TEST_PACKAGE_NAME,
                CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH).mSamplingInterval).isEqualTo(
                batchCallSamplingInterval);
    }

    @Test
    public void testShouldLogForTypeLocked_trueWhenSampleIntervalIsOne() {
        final String testPackageName = "packageName";
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Long.toString(1),
                false);

        // Sample should always be logged for the first time if sampling is disabled(value is one).
        assertThat(logger.shouldLogForTypeLocked(CallStats.CALL_TYPE_PUT_DOCUMENT)).isTrue();
        assertThat(logger.createExtraStatsLocked(testPackageName,
                CallStats.CALL_TYPE_PUT_DOCUMENT).mSkippedSampleCount).isEqualTo(0);
    }

    @Test
    public void testShouldLogForTypeLocked_falseWhenSampleIntervalIsNegative() {
        final String testPackageName = "packageName";
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Long.toString(-1),
                false);

        // Makes sure sample will be excluded due to sampling if sample interval is negative.
        assertThat(logger.shouldLogForTypeLocked(CallStats.CALL_TYPE_PUT_DOCUMENT)).isFalse();
        // Skipped count should be 0 since it doesn't pass the sampling.
        assertThat(logger.createExtraStatsLocked(testPackageName,
                CallStats.CALL_TYPE_PUT_DOCUMENT).mSkippedSampleCount).isEqualTo(0);
    }

    @Test
    public void testShouldLogForTypeLocked_falseWhenWithinCoolOffInterval() {
        // Next sample won't be excluded due to sampling.
        final int samplingInterval = 1;
        // Next sample would guaranteed to be too close.
        final int minTimeIntervalBetweenSamplesMillis = Integer.MAX_VALUE;
        final String testPackageName = "packageName";
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Long.toString(samplingInterval),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(minTimeIntervalBetweenSamplesMillis),
                false);
        logger.setLastPushTimeMillisLocked(SystemClock.elapsedRealtime());

        // Makes sure sample will be excluded due to rate limiting if samples are too close.
        assertThat(logger.shouldLogForTypeLocked(CallStats.CALL_TYPE_PUT_DOCUMENT)).isFalse();
        assertThat(logger.createExtraStatsLocked(testPackageName,
                CallStats.CALL_TYPE_PUT_DOCUMENT).mSkippedSampleCount).isEqualTo(1);
    }

    @Test
    public void testShouldLogForTypeLocked_trueWhenOutsideOfCoolOffInterval() {
        // Next sample won't be excluded due to sampling.
        final int samplingInterval = 1;
        // Next sample would guaranteed to be included.
        final int minTimeIntervalBetweenSamplesMillis = 0;
        final String testPackageName = "packageName";
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT,
                Long.toString(samplingInterval),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(minTimeIntervalBetweenSamplesMillis),
                false);
        logger.setLastPushTimeMillisLocked(SystemClock.elapsedRealtime());

        // Makes sure sample will be logged if it is not too close to previous sample.
        assertThat(logger.shouldLogForTypeLocked(CallStats.CALL_TYPE_PUT_DOCUMENT)).isTrue();
        assertThat(logger.createExtraStatsLocked(testPackageName,
                CallStats.CALL_TYPE_PUT_DOCUMENT).mSkippedSampleCount).isEqualTo(0);
    }

    @Test
    public void testAddStatsToQueueLocked_zeroCapacity() {
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT, "0", false);

        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package")
                        .setDatabase("test_database")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );

        // Since we allow 0 API call to be recorded, the list should be empty.
        assertThat(logger.getLastCalledApis()).isEmpty();
    }

    @Test
    public void testAddStatsToQueueLocked_negativeCapacity() {
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT, "-1", false);

        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package")
                        .setDatabase("test_database")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );

        // Negative capacity is treated the same as 0.
        assertThat(logger.getLastCalledApis()).isEmpty();
    }

    @Test
    public void testAddStatsToQueueLocked_oneApi() {
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT, "1", false);

        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package1")
                        .setDatabase("test_database1")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );
        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package2")
                        .setDatabase("test_database2")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );

        // If the queue is at capacity, the earliest stats will be dropped.
        assertThat(logger.getLastCalledApis()).hasSize(1);
        ApiCallRecord apiCallRecord = logger.getLastCalledApis().get(0);
        assertThat(apiCallRecord.toString()).contains("test_package2");
        assertThat(apiCallRecord.getTimeMillis()).isGreaterThan(0);
        assertThat(apiCallRecord.getCallType()).isEqualTo(CallStats.CALL_TYPE_SET_SCHEMA);
        assertThat(apiCallRecord.getPackageName()).isEqualTo("test_package2");
        assertThat(apiCallRecord.getDatabaseName()).isEqualTo("test_database2");
        assertThat(apiCallRecord.getStatusCode()).isEqualTo(0);
        assertThat(apiCallRecord.getTotalLatencyMillis()).isEqualTo(10);
    }

    @Test
    public void testAddStatsToQueueLocked_capacityChanged() {
        PlatformLogger logger = new PlatformLogger(
                ApplicationProvider.getApplicationContext(),
                mAppSearchConfig);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT, "2", false);

        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package1")
                        .setDatabase("test_database1")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );
        logger.addStatsToQueueLocked(
                new ApiCallRecord(new CallStats.Builder()
                        .setPackageName("test_package2")
                        .setDatabase("test_database2")
                        .setStatusCode(0)
                        .setTotalLatencyMillis(10)
                        .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                        .build())
        );

        assertThat(logger.getLastCalledApis()).hasSize(2);

        // Changing the capacity to 1 will drop the earliest stats.
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT, "1", false);
        assertThat(logger.getLastCalledApis()).hasSize(1);
        ApiCallRecord apiCallRecord = logger.getLastCalledApis().get(0);
        assertThat(apiCallRecord.toString()).contains("test_package2");
        assertThat(apiCallRecord.getTimeMillis()).isGreaterThan(0);
        assertThat(apiCallRecord.getCallType()).isEqualTo(CallStats.CALL_TYPE_SET_SCHEMA);
        assertThat(apiCallRecord.getPackageName()).isEqualTo("test_package2");
        assertThat(apiCallRecord.getDatabaseName()).isEqualTo("test_database2");
        assertThat(apiCallRecord.getStatusCode()).isEqualTo(0);
        assertThat(apiCallRecord.getTotalLatencyMillis()).isEqualTo(10);
    }
}
