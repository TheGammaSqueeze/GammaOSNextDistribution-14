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

package com.android.cobalt.data;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import androidx.room.Room;
import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.cobalt.data.TestOnlyDao.AggregateStoreTableRow;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.LocalIndexHistogram;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
public final class DataServiceTest extends AdServicesMockitoTestCase {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static final Instant TIME = Instant.parse("2023-06-13T16:09:30.00Z");
    private static final int DAY_INDEX_1 = 19202;
    private static final int DAY_INDEX_2 = 19203;
    private static final int DAY_INDEX_ENABLED = DAY_INDEX_1 - 30;
    private static final ReportKey REPORT_1 = ReportKey.create(1, 2, 3, 4);
    private static final ReportKey REPORT_2 =
            ReportKey.create(
                    REPORT_1.customerId(),
                    REPORT_1.projectId(),
                    REPORT_1.metricId(),
                    REPORT_1.reportId() + 1);
    private static final SystemProfile SYSTEM_PROFILE_1 =
            SystemProfile.newBuilder().setSystemVersion("1.2.3").build();
    private static final SystemProfile SYSTEM_PROFILE_2 =
            SystemProfile.newBuilder().setSystemVersion("2.4.8").build();
    private static final EventVector EVENT_VECTOR_1 = EventVector.create(1, 5);
    private static final EventVector EVENT_VECTOR_2 = EventVector.create(2, 6);
    private static final EventVector EVENT_VECTOR_3 = EventVector.create(3, 7);
    private static final EventVector EVENT_VECTOR_4 = EventVector.create(4, 8);
    private static final int EVENT_COUNT_3 = 25;
    private static final int EVENT_COUNT_4 = 7;

    private static final EventRecordAndSystemProfile EVENT_RECORD_3 =
            createEventRecord(SYSTEM_PROFILE_1, EVENT_VECTOR_3, EVENT_COUNT_3);
    private static final EventRecordAndSystemProfile EVENT_RECORD_3_2 =
            createEventRecord(SYSTEM_PROFILE_2, EVENT_VECTOR_3, EVENT_COUNT_3);
    private static final EventRecordAndSystemProfile EVENT_RECORD_4 =
            createEventRecord(SYSTEM_PROFILE_1, EVENT_VECTOR_4, EVENT_COUNT_4);
    private static final EventRecordAndSystemProfile EVENT_RECORD_4_2 =
            createEventRecord(SYSTEM_PROFILE_2, EVENT_VECTOR_4, EVENT_COUNT_4);
    private static final UnencryptedObservationBatch OBSERVATION_1 =
            UnencryptedObservationBatch.newBuilder()
                    .setMetadata(
                            ObservationMetadata.newBuilder().setSystemProfile(SYSTEM_PROFILE_1))
                    .build();
    private static final UnencryptedObservationBatch OBSERVATION_2 =
            UnencryptedObservationBatch.newBuilder()
                    .setMetadata(
                            ObservationMetadata.newBuilder().setSystemProfile(SYSTEM_PROFILE_2))
                    .build();
    private static final ImmutableList<UnencryptedObservationBatch> EMPTY_OBSERVATIONS =
            ImmutableList.of();
    private static final ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile>
            EMPTY_EVENT_DATA = ImmutableListMultimap.of();

    private CobaltDatabase mCobaltDatabase;
    private DaoBuildingBlocks mDaoBuildingBlocks;
    private TestOnlyDao mTestOnlyDao;
    private DataService mDataService;
    @Mock private ObservationGenerator mGenerator;
    private Function<Integer, ObservationGenerator> mGeneratorSupplier;

    @Before
    public void setup() {
        mCobaltDatabase = Room.inMemoryDatabaseBuilder(mContext, CobaltDatabase.class).build();
        mDaoBuildingBlocks = mCobaltDatabase.daoBuildingBlocks();
        mTestOnlyDao = mCobaltDatabase.testOnlyDao();
        mDataService = new DataService(EXECUTOR, mCobaltDatabase);
        mGeneratorSupplier = (unused) -> mGenerator;
    }

    @After
    public void closeDb() throws IOException {
        mCobaltDatabase.close();
    }

    private Instant timePlusHours(int hours) {
        return TIME.plus(Duration.ofHours(hours));
    }

    private Instant timePlusDays(int days) {
        return TIME.plus(Duration.ofDays(days));
    }

    private Instant timeMinusDays(int days) {
        return TIME.minus(Duration.ofDays(days));
    }

    private static EventRecordAndSystemProfile createEventRecord(
            SystemProfile systemProfile, EventVector eventVector, int aggregateValue) {
        return EventRecordAndSystemProfile.create(
                systemProfile,
                eventVector,
                AggregateValue.newBuilder().setIntegerValue(aggregateValue).build());
    }

    /** Returns a {@link LocalIndexHistogram} with at least one bucket. */
    private static LocalIndexHistogram createIndexHistogram(
            LocalIndexHistogram.Bucket b0, LocalIndexHistogram.Bucket... bRest) {
        return LocalIndexHistogram.newBuilder()
                .addBuckets(b0)
                .addAllBuckets(Arrays.asList(bRest))
                .build();
    }

    private static LocalIndexHistogram.Bucket createBucket(int index, int count) {
        return LocalIndexHistogram.Bucket.newBuilder().setIndex(index).setCount(count).build();
    }

    @Test
    public void loggerEnabled_oneTime_stored() throws Exception {
        assertThat(mDataService.loggerEnabled(TIME).get()).isEqualTo(TIME);
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerEnabled_multipleTimes_firstIsStored() throws Exception {
        // Set the logger as enabled, and again an hour later.
        mDataService.loggerEnabled(TIME).get();
        assertThat(mDataService.loggerEnabled(timePlusHours(1)).get()).isEqualTo(TIME);

        // Check that the original initial enabled time is returned and set in the database
        assertThat(mDataService.loggerEnabled(TIME).get()).isEqualTo(TIME);
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerDisabled_notYetEnabled_stored() throws Exception {
        // Set the logger as disabled.
        mDataService.loggerDisabled(TIME).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerDisabled_afterEnabled_stored() throws Exception {
        // Set the logger as enabled.
        Instant enabledTime = TIME.minus(Duration.ofDays(1));
        mDataService.loggerEnabled(enabledTime).get();

        // Set the logger as disabled.
        mDataService.loggerDisabled(TIME).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, enabledTime.toString(),
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerDisabled_multipleTimesAfterEnabled_stored() throws Exception {
        // Set the logger as enabled.
        Instant enabledTime = timeMinusDays(1);
        mDataService.loggerEnabled(enabledTime).get();

        // Set the logger as disabled, and again an hour later.
        mDataService.loggerDisabled(TIME).get();
        mDataService.loggerDisabled(timePlusHours(1)).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, enabledTime.toString(),
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerEnabled_reenabledShortlyAfterDisabled_originalEnabledTime() throws Exception {
        // Set the logger as enabled.
        mDataService.loggerEnabled(TIME).get();

        // Set the logger as disabled 10 days later.
        mDataService.loggerDisabled(timePlusDays(10)).get();

        // Re-enable the logger after a day. Less than 2 days so the original enabled time is kept.
        assertThat(mDataService.loggerEnabled(timePlusDays(11)).get()).isEqualTo(TIME);

        // Check that the original initial time is kept and the disabled time is no longer set in
        // the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, TIME.toString());
    }

    @Test
    public void loggerEnabled_reenabledAfterMoreThanTwoDaysDisabled_newEnabledTime()
            throws Exception {
        // Set the logger as enabled.
        mDataService.loggerEnabled(TIME).get();

        // Set the logger as disabled 10 days later.
        mDataService.loggerDisabled(timePlusDays(10)).get();

        // Re-enable the logger after 3 days. More than 2 days so the initial enabled time is reset.
        assertThat(mDataService.loggerEnabled(timePlusDays(13)).get()).isEqualTo(timePlusDays(13));

        // Check that the initial time is reset and the disabled time is no longer set in the
        // database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, timePlusDays(13).toString());
    }

    @Test
    public void aggregateCount_multipleCalls_aggregatedTogether() throws Exception {
        // Mark a Count report as having occurred.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        100)
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(100).build())
                                .build());

        // Add to the existing count.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        50)
                .get();

        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(150).build())
                                .build());
    }

    @Test
    public void aggregateCount_multipleReportsDaysEventVectors_aggregatedSeparately()
            throws Exception {
        // Mark a Count report as having occurred.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        100)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        150)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        175)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        185)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        195)
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(100).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(150).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(175).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(185).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_2)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(195).build())
                                .build());
    }

    @Test
    public void aggregateCount_eventVectorBufferMaxLimit_firstEventVectorsAggregated()
            throws Exception {
        // Two event vectors occur with counts and are aggregated.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 2,
                        100)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 2,
                        150)
                .get();
        // A 3rd event vector is over the limit and is dropped.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 2,
                        175)
                .get();
        // A previous event vector occurs again and it's count is aggregated.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 2,
                        185)
                .get();
        // 3rd event vector occurs but now with different system profile, and is aggregated.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 2,
                        195)
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(285).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_2)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(150).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_3)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(195).build())
                                .build());
    }

    @Test
    public void aggregateString_multipleCalls_aggregatedTogether() throws Exception {
        // Mark a string count report as having occurred.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "A")
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build());
        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "A"));

        // Add an occurrence of the existing string.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "A")
                .get();

        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 2)))
                                                .build())
                                .build());

        // Add a new string.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "B")
                .get();

        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 2),
                                                                createBucket(
                                                                        /* index= */ 1,
                                                                        /* count= */ 1)))
                                                .build())
                                .build());
        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "A"),
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 1, "B"));
    }

    @Test
    public void aggregateString_multipleReportsDaysEventVectors_aggregatedSeparately()
            throws Exception {
        // Mark various string count reports as having occurred on different days for different
        // event vectors.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "A")
                .get();
        mDataService
                .aggregateString(
                        REPORT_2,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "B")
                .get();
        mDataService
                .aggregateString(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "C")
                .get();
        mDataService
                .aggregateString(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "D")
                .get();
        mDataService
                .aggregateString(
                        REPORT_2,
                        DAY_INDEX_2,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        "E")
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 1,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_2)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_2)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 2,
                                                                        /* count= */ 1)))
                                                .build())
                                .build());
        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "A"),
                        StringHashEntity.create(REPORT_2, DAY_INDEX_1, /* listIndex= */ 0, "B"),
                        StringHashEntity.create(REPORT_2, DAY_INDEX_2, /* listIndex= */ 0, "C"),
                        StringHashEntity.create(REPORT_2, DAY_INDEX_2, /* listIndex= */ 1, "D"),
                        StringHashEntity.create(REPORT_2, DAY_INDEX_2, /* listIndex= */ 2, "E"));
    }

    @Test
    public void aggregateString_eventVectorBufferMaxLimit_firstEventVectorsAggregated()
            throws Exception {
        // Two event vectors occur with different strings and are aggregated.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 2,
                        /* stringBufferMax= */ 0,
                        "A")
                .get();
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 2,
                        /* stringBufferMax= */ 0,
                        "B")
                .get();
        // A 3rd event vector is over the limit and is dropped.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 2,
                        /* stringBufferMax= */ 0,
                        "C")
                .get();
        // A previous event vector occurs again and its string is aggregated.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 2,
                        /* stringBufferMax= */ 0,
                        "D")
                .get();
        // 3rd event vector occurs but now with a different system profile and is aggregated.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 2,
                        /* stringBufferMax= */ 0,
                        "E")
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1),
                                                                createBucket(
                                                                        /* index= */ 2,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_2)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 1,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_2)
                                .setEventVector(EVENT_VECTOR_3)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 3,
                                                                        /* count= */ 1)))
                                                .build())
                                .build());
        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "A"),
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 1, "B"),
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 2, "D"),
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 3, "E"));
    }

    @Test
    public void aggregateString_stringBufferMaxLimit_firstStringsAggregated() throws Exception {
        // Two strings occur and are aggregated.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 2,
                        "A")
                .get();
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 2,
                        "B")
                .get();
        // A 3rd string is over the limit and is dropped.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 2,
                        "C")
                .get();
        // A previous string occurs again and is aggregated.
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_4,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 2,
                        "A")
                .get();
        // A new string occurs but now for a different report and is aggregated.
        mDataService
                .aggregateString(
                        REPORT_2,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 2,
                        "D")
                .get();

        // Check that the data is found in the database.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_2)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 1,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_1)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_4)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(DAY_INDEX_1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder()
                                                .setIndexHistogram(
                                                        createIndexHistogram(
                                                                createBucket(
                                                                        /* index= */ 0,
                                                                        /* count= */ 1)))
                                                .build())
                                .build());
        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "A"),
                        StringHashEntity.create(REPORT_1, DAY_INDEX_1, /* listIndex= */ 1, "B"),
                        StringHashEntity.create(REPORT_2, DAY_INDEX_1, /* listIndex= */ 0, "D"));
    }

    @Test
    public void generateObservations_oneEvent_oneObservationStored() throws Exception {
        // Initialize a report as up to date for sending observations up to the previous day.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 1));

        // Mark a Count report as having occurred on the current day.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Expect one EventRecordAndSystemProfile to be passed to the Obseration Generator.
        ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> expectedEventRecord =
                ImmutableListMultimap.of(SYSTEM_PROFILE_1, EVENT_RECORD_3);
        when(mGenerator.generateObservations(DAY_INDEX_1, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_1));

        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1, expectedEventRecord);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(ObservationStoreEntity.create(1, OBSERVATION_1));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_multipleEventVectors_oneObservationStored() throws Exception {
        // Initialize a report as up to date for sending observations up to the previous day.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 1));

        // Mark a Count report as having occurred on the current day with 2 event vectors.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_4,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_4)
                .get();

        // Expect one EventRecordAndSystemProfile with two event vectors to be passed to the
        // observation generator.
        ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> expectedEventRecord =
                ImmutableListMultimap.of(
                        SYSTEM_PROFILE_1, EVENT_RECORD_3, SYSTEM_PROFILE_1, EVENT_RECORD_4);
        when(mGenerator.generateObservations(DAY_INDEX_1, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_1));

        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1, expectedEventRecord);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(ObservationStoreEntity.create(1, OBSERVATION_1));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_multipleSystemProfiles_twoObservationsStored()
            throws Exception {
        // Initialize a report as up to date for sending observations up to the previous day.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 1));

        // Mark a Count report as having occurred on the current day with 2 system profiles.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_4,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_4)
                .get();
        // Expect two EventRecordAndSystemProfiles to be passed to the Obseration Generator.
        ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> expectedEventRecord =
                ImmutableListMultimap.of(
                        SYSTEM_PROFILE_1, EVENT_RECORD_3,
                        SYSTEM_PROFILE_2, EVENT_RECORD_4_2);
        when(mGenerator.generateObservations(DAY_INDEX_1, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_1, OBSERVATION_2));

        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1, expectedEventRecord);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, OBSERVATION_1),
                        ObservationStoreEntity.create(2, OBSERVATION_2));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_multipleSystemProfilesAndEventVectors_allStored()
            throws Exception {
        // Initialize a report as up to date for sending observations up to the previous day.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 1));

        // Mark a Count report as having occurred on the current day with 2 system profiles each
        // with 2 event vectors.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_4,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_4)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_4,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_4)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Expect two EventRecordAndSystemProfiles to be passed to the Obseration Generator.
        ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> expectedEventRecord =
                ImmutableListMultimap.of(
                        SYSTEM_PROFILE_1,
                        EVENT_RECORD_3,
                        SYSTEM_PROFILE_1,
                        EVENT_RECORD_4,
                        SYSTEM_PROFILE_2,
                        EVENT_RECORD_3_2,
                        SYSTEM_PROFILE_2,
                        EVENT_RECORD_4_2);
        when(mGenerator.generateObservations(DAY_INDEX_1, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_1, OBSERVATION_2));

        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1, expectedEventRecord);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, OBSERVATION_1),
                        ObservationStoreEntity.create(2, OBSERVATION_2));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_newReport_noInitialBackfill() throws Exception {
        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations()).isEmpty();
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_initialBackfillAfterReenabled_onlySinceEnbaledTime()
            throws Exception {
        // Initialize a report as up to date for sending observations a week ago.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 8));

        // Expect empty EventRecordAndSystemProfile to be passed to the Obseration Generator
        // for the days since the
        // logger was re-enabled.
        when(mGenerator.generateObservations(DAY_INDEX_1 - 1, EMPTY_EVENT_DATA))
                .thenReturn(EMPTY_OBSERVATIONS);
        when(mGenerator.generateObservations(DAY_INDEX_1, EMPTY_EVENT_DATA))
                .thenReturn(EMPTY_OBSERVATIONS);

        // Generate and store the one observation for the current day.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_1 - 1, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1 - 1, EMPTY_EVENT_DATA);
        verify(mGenerator).generateObservations(DAY_INDEX_1, EMPTY_EVENT_DATA);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations()).isEmpty();
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void generateObservations_backfillDailyReport_twoDaysStored() throws Exception {
        // Initialize a report as up to date for sending observations a week ago.
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(REPORT_1, DAY_INDEX_1 - 8));

        // Mark an AtLeastOnce report as having occurred two days ago and today.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1 - 2,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Expect one EventRecordAndSystemProfile to be passed to the Obseration Generator for
        // each of the two days.
        ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> expectedEventRecord =
                ImmutableListMultimap.of(SYSTEM_PROFILE_1, EVENT_RECORD_3);
        when(mGenerator.generateObservations(DAY_INDEX_1 - 3, EMPTY_EVENT_DATA))
                .thenReturn(EMPTY_OBSERVATIONS);
        when(mGenerator.generateObservations(DAY_INDEX_1 - 2, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_1));
        when(mGenerator.generateObservations(DAY_INDEX_1 - 1, EMPTY_EVENT_DATA))
                .thenReturn(EMPTY_OBSERVATIONS);
        // After 7 days of generating observations, there should be no event data.
        when(mGenerator.generateObservations(DAY_INDEX_1, expectedEventRecord))
                .thenReturn(ImmutableList.of(OBSERVATION_2));

        // Generate and store the observations for the three days of backfill.
        mDataService
                .generateObservations(REPORT_1, DAY_INDEX_1, DAY_INDEX_ENABLED, mGeneratorSupplier)
                .get();

        // Check that the Obseration Generator was called correctly.
        verify(mGenerator).generateObservations(DAY_INDEX_1 - 3, EMPTY_EVENT_DATA);
        verify(mGenerator).generateObservations(DAY_INDEX_1 - 2, expectedEventRecord);
        verify(mGenerator).generateObservations(DAY_INDEX_1 - 1, EMPTY_EVENT_DATA);
        verify(mGenerator).generateObservations(DAY_INDEX_1, expectedEventRecord);
        verifyNoMoreInteractions(mGenerator);

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, OBSERVATION_1),
                        ObservationStoreEntity.create(2, OBSERVATION_2));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(DAY_INDEX_1));
    }

    @Test
    public void cleanup_removesAggregates_beforeOldestDayIndex() throws Exception {
        int oldestDayIndex = 12345;

        mDataService
                .aggregateString(
                        REPORT_1,
                        oldestDayIndex - 1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        /* stringValue= */ "STRING")
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        oldestDayIndex,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* count= */ 1)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        oldestDayIndex + 1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* count= */ 1)
                .get();

        mDataService.cleanup(ImmutableList.of(REPORT_1, REPORT_2), oldestDayIndex).get();

        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(oldestDayIndex)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(1).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(oldestDayIndex + 1)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(1).build())
                                .build());
    }

    @Test
    public void cleanup_removesIrrelevantReports() throws Exception {
        int oldestDayIndex = 12345;

        mDataService
                .aggregateString(
                        REPORT_1,
                        oldestDayIndex,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        /* stringValue= */ "STRING")
                .get();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        oldestDayIndex,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* count= */ 1)
                .get();

        mDataService.cleanup(ImmutableList.of(REPORT_2), oldestDayIndex).get();

        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(REPORT_2)
                                .setDayIndex(oldestDayIndex)
                                .setSystemProfile(SYSTEM_PROFILE_1)
                                .setEventVector(EVENT_VECTOR_1)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(1).build())
                                .build());
    }

    @Test
    public void cleanup_removesUnusedSystemProfileHashes() throws Exception {
        int oldestDayIndex = 12345;

        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(
                        SystemProfileEntity.getSystemProfileHash(SYSTEM_PROFILE_1),
                        SYSTEM_PROFILE_1));
        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(
                        SystemProfileEntity.getSystemProfileHash(SYSTEM_PROFILE_2),
                        SYSTEM_PROFILE_2));

        mDataService
                .aggregateCount(
                        REPORT_1,
                        oldestDayIndex,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* count= */ 1)
                .get();

        mDataService.cleanup(ImmutableList.of(REPORT_1), oldestDayIndex).get();

        assertThat(mTestOnlyDao.getSystemProfiles())
                .containsExactly(
                        SystemProfileEntity.create(
                                SystemProfileEntity.getSystemProfileHash(SYSTEM_PROFILE_1),
                                SYSTEM_PROFILE_1));
    }

    @Test
    public void cleanup_removesUnusedStringHashes() throws Exception {
        mDataService
                .aggregateString(
                        REPORT_1,
                        DAY_INDEX_1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* stringBufferMax= */ 0,
                        /* stringValue= */ "STRING")
                .get();

        // Insert a string hash with a bad report key.
        mDaoBuildingBlocks.insertStringHash(
                StringHashEntity.create(REPORT_2, DAY_INDEX_1, 0/* unused= */ , "STRING"));

        // Insert a string hash with a bad day index.
        mDaoBuildingBlocks.insertStringHash(
                StringHashEntity.create(REPORT_1, DAY_INDEX_1 + 1, 0/* unused= */ , "STRING"));

        mDataService.cleanup(ImmutableList.of(REPORT_1), DAY_INDEX_1).get();

        assertThat(mTestOnlyDao.getStringHashes())
                .containsExactly(
                        StringHashEntity.create(
                                REPORT_1, DAY_INDEX_1, /* listIndex= */ 0, "STRING"));
    }
}
