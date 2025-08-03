/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.aggregation;

import static android.health.connect.HealthDataCategory.ACTIVITY;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.aggregation.DataFactory.getTimeFilter;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.PKG_TEST_APP;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.insertStepsRecordViaTestApp;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;
import static android.healthconnect.cts.utils.TestUtils.updatePriorityWithManageHealthDataPermission;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class AggregateWithFiltersTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        deleteAllStagedRemoteData();
        setupAggregation(mPackageName, ACTIVITY);
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void dataOriginFilter_noFilter_everythingIncluded() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100,
                                startTime.plusMillis(1000),
                                startTime.plusMillis(2000),
                                /* clientId= */ "own_steps")));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(getTimeFilter(EPOCH, Instant.now()))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(150);
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL))
                .containsExactly(getDataOrigin(mPackageName), getDataOrigin(PKG_TEST_APP));
    }

    @Test
    public void dataOriginFilter_validFilter_onlyDataFromFilteredApps() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100,
                                startTime.plusMillis(1000),
                                startTime.plusMillis(2000),
                                /* clientId= */ "own_steps")));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(getTimeFilter(EPOCH, Instant.now()))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(PKG_TEST_APP))
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(50);
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL))
                .containsExactly(getDataOrigin(PKG_TEST_APP));
    }

    @Test
    public void dataOriginFilter_invalidApp_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100,
                                startTime.plusMillis(1000),
                                startTime.plusMillis(2000),
                                /* clientId= */ "own_steps")));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(getTimeFilter(EPOCH, Instant.now()))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin("invalid.app.pkg"))
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }

    @Test
    public void dataOriginFilter_appNotInPriorityList_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100,
                                startTime.plusMillis(1000),
                                startTime.plusMillis(2000),
                                /* clientId= */ "own_steps")));
        // remove mPackageName from priority list
        updatePriorityWithManageHealthDataPermission(ACTIVITY, ImmutableList.of(PKG_TEST_APP));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(getTimeFilter(EPOCH, Instant.now()))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }
}
