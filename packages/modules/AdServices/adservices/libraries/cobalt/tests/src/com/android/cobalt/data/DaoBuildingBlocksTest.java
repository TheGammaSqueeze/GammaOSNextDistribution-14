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

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.hash.HashCode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class DaoBuildingBlocksTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final ReportKey[] sReportKey = {
        ReportKey.create(0, 0, 0, 0), ReportKey.create(1, 1, 1, 1)
    };
    private static final int[] sDayIndex = {0, 1};
    private static final EventVector[] sEventVector = {
        EventVector.create(0), EventVector.create(1)
    };
    private static final int[] sSystemProfileHash = {0, 1};
    private static final SystemProfile[] sSystemProfile = {
        SystemProfile.newBuilder().setSystemVersion("a").build(),
        SystemProfile.newBuilder().setSystemVersion("b").build()
    };
    private static final AggregateValue[] sAggregateValue = {
        AggregateValue.newBuilder().setIntegerValue(0).build(),
        AggregateValue.newBuilder().setIntegerValue(1).build()
    };
    private static final HashCode[] sHashCodes = {
        HashCode.fromInt(1), HashCode.fromInt(2), HashCode.fromInt(3)
    };

    private DaoBuildingBlocks mDaoBuildingBlocks;
    private TestOnlyDao mTestOnlyDao;
    private CobaltDatabase mCobaltDatabase;

    @Before
    public void createDb() {
        mCobaltDatabase = Room.inMemoryDatabaseBuilder(sContext, CobaltDatabase.class).build();
        mDaoBuildingBlocks = mCobaltDatabase.daoBuildingBlocks();
        mTestOnlyDao = mCobaltDatabase.testOnlyDao();
    }

    @After
    public void closeDb() throws IOException {
        mCobaltDatabase.close();
    }

    private void insertOrReplaceGlobalValue(GlobalValueEntity.Key key, String value) {
        mDaoBuildingBlocks.insertOrReplaceGlobalValue(GlobalValueEntity.create(key, value));
    }

    private void insertSystemProfileAndReport(
            long systemProfileHash,
            SystemProfile systemProfile,
            ReportKey reportKey,
            int lastSentDayIndex) {
        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(systemProfileHash, systemProfile));
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(reportKey, lastSentDayIndex));
    }

    private void insertAggregateValue(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHash,
            AggregateValue aggregateValue) {
        mDaoBuildingBlocks.insertAggregateValue(
                AggregateStoreEntity.create(
                        reportKey, dayIndex, eventVector, systemProfileHash, aggregateValue));
    }

    private void insertStringHash(
            ReportKey reportKey, int dayIndex, int listIndex, HashCode stringHashHint) {
        mDaoBuildingBlocks.insertStringHash(
                StringHashEntity.create(reportKey, dayIndex, listIndex, stringHashHint));
    }

    private void insertObservations(ObservationStoreEntity... observationStoreEntities) {
        mDaoBuildingBlocks.insertObservations(Arrays.asList(observationStoreEntities));
    }

    private void insertLastSentDayIndex(ReportKey reportKey, int dayIndex) {
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(reportKey, dayIndex));
    }

    private void updateLastSentDayIndex(ReportKey reportKey, int dayIndex) {
        mDaoBuildingBlocks.updateLastSentDayIndex(ReportEntity.create(reportKey, dayIndex));
    }

    private void updateAggregateValue(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHash,
            AggregateValue newAggregateValue) {
        mDaoBuildingBlocks.updateAggregateValue(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex,
                eventVector,
                systemProfileHash,
                newAggregateValue);
    }

    private void updateSystemProfileHash(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long currentSystemProfileHash,
            long newSystemProfileHash) {
        mDaoBuildingBlocks.updateSystemProfileHash(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex,
                eventVector,
                currentSystemProfileHash,
                newSystemProfileHash);
    }

    private void updateSystemProfileHashAndAggregateValue(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long currentSystemProfileHash,
            long newSystemProfileHash,
            AggregateValue newAggregateValue) {
        mDaoBuildingBlocks.updateSystemProfileHashAndAggregateValue(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex,
                eventVector,
                currentSystemProfileHash,
                newSystemProfileHash,
                newAggregateValue);
    }

    private Optional<SystemProfileAndAggregateValue> queryOneSystemProfileAndAggregateValue(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHashHint) {
        return mDaoBuildingBlocks.queryOneSystemProfileAndAggregateValue(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex,
                eventVector,
                systemProfileHashHint);
    }

    private int queryCountEventVectors(ReportKey reportKey, int dayIndex, long systemProfileHash) {
        return mDaoBuildingBlocks.queryCountEventVectors(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex,
                systemProfileHash);
    }

    private List<EventRecordAndSystemProfile> queryEventRecordsForDay(
            ReportKey reportKey, int dayIndex) {
        return mDaoBuildingBlocks.queryEventRecordsForDay(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId(),
                dayIndex);
    }

    private Optional<Integer> queryLastSentDayIndex(ReportKey reportKey) {
        return mDaoBuildingBlocks.queryLastSentDayIndex(
                reportKey.customerId(),
                reportKey.projectId(),
                reportKey.metricId(),
                reportKey.reportId());
    }

    @Test
    public void testInsertGlobalValues() throws Exception {
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, "a");
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, "b");

        Map<GlobalValueEntity.Key, String> globalValues = mDaoBuildingBlocks.queryEnablementTimes();
        assertThat(globalValues)
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME,
                        "a",
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME,
                        "b");
    }

    @Test
    public void testReplaceGlobalValues() throws Exception {
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, "a");
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, "b");

        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, "b");
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, "a");

        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME,
                        "b",
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME,
                        "a");
    }

    @Test
    public void testDeleteDisabledTime() throws Exception {
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, "a");
        insertOrReplaceGlobalValue(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, "b");

        mDaoBuildingBlocks.deleteDisabledTime();
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .doesNotContainKey(GlobalValueEntity.Key.INITIAL_DISABLED_TIME);
    }

    @Test
    public void testDeleteDisabledTime_doesNotExist() throws Exception {
        mDaoBuildingBlocks.deleteDisabledTime();
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .doesNotContainKey(GlobalValueEntity.Key.INITIAL_DISABLED_TIME);
    }

    @Test
    public void testInsertAggregateValue_systemProfileDoesNotExist() throws Exception {

        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(sReportKey[0], sDayIndex[0]));
        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        insertAggregateValue(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[0],
                                sAggregateValue[0]));
    }

    @Test
    public void testInsertAggregateValue_reportDoesNotExist() throws Exception {
        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(sSystemProfileHash[0], sSystemProfile[0]));
        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        insertAggregateValue(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[0],
                                sAggregateValue[0]));
    }

    @Test
    public void testInsertAggregateValue_systemProfileDoesNotMatch() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        insertAggregateValue(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[1],
                                sAggregateValue[0]));
    }

    @Test
    public void testUpdateAggregateValue() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        updateAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[1]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);
        assertThat(systemProfileAndAggregateValue)
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        sSystemProfileHash[0], sAggregateValue[1])));
    }

    @Test
    public void testUpdateAggregateValue_doesNotExist() throws Exception {
        updateAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testUpdateSystemProfileHash() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertSystemProfileAndReport(
                sSystemProfileHash[1], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        updateSystemProfileHash(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sSystemProfileHash[1]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);

        assertThat(systemProfileAndAggregateValue)
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        sSystemProfileHash[1], sAggregateValue[0])));
    }

    @Test
    public void testUpdateSystemProfileHash_newSystemProfileHashDoesNotExist() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        updateSystemProfileHash(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[0],
                                sSystemProfileHash[1]));
    }

    @Test
    public void testUpdateSystemProfileHash_doesNotExist() throws Exception {
        updateSystemProfileHash(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sSystemProfileHash[1]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[1]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testUpdateSystemProfileHashAndAggregateValue() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertSystemProfileAndReport(
                sSystemProfileHash[1], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        updateSystemProfileHashAndAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sSystemProfileHash[1],
                sAggregateValue[1]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);

        assertThat(systemProfileAndAggregateValue)
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        sSystemProfileHash[1], sAggregateValue[1])));
    }

    @Test
    public void testUpdateSystemProfileHashAndAggregateValue_newSystemProfileHashDoesNotExist()
            throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        updateSystemProfileHashAndAggregateValue(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[0],
                                sSystemProfileHash[1],
                                sAggregateValue[1]));
    }

    @Test
    public void testUpdateSystemProfileHashAndAggregateValue_doesNotExist() throws Exception {
        updateSystemProfileHashAndAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sSystemProfileHash[1],
                sAggregateValue[0]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[1]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testQuerySystemProfileAndAggregateValue_systemProfileHashHintExists()
            throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertSystemProfileAndReport(
                sSystemProfileHash[1], sSystemProfile[1], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[1],
                sAggregateValue[1]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);
        assertThat(systemProfileAndAggregateValue)
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        sSystemProfileHash[0], sAggregateValue[0])));

        systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[0], sDayIndex[0], sEventVector[0], sSystemProfileHash[1]);
        assertThat(systemProfileAndAggregateValue)
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        sSystemProfileHash[1], sAggregateValue[1])));
    }

    @Test
    public void testQuerySystemProfileAndAggregateValue_systemProfileHashHintDoesNotExist()
            throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[1], sDayIndex[0], sEventVector[0], sSystemProfileHash[1]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testQuerySystemProfileAndAggregateValue_doesNotExist() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[1], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testQuerySystemProfileAndAggregateValue_tableEmpty() throws Exception {
        Optional<SystemProfileAndAggregateValue> systemProfileAndAggregateValue =
                queryOneSystemProfileAndAggregateValue(
                        sReportKey[1], sDayIndex[0], sEventVector[0], sSystemProfileHash[0]);
        assertThat(systemProfileAndAggregateValue).isEqualTo(Optional.empty());
    }

    @Test
    public void testQueryCountEventVectors() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[1],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[1],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        assertThat(queryCountEventVectors(sReportKey[0], sDayIndex[0], sSystemProfileHash[0]))
                .isEqualTo(2);
        assertThat(queryCountEventVectors(sReportKey[0], sDayIndex[1], sSystemProfileHash[0]))
                .isEqualTo(1);
    }

    @Test
    public void testQueryEventRecordsForDay() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[1],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[1],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        List<EventRecordAndSystemProfile> eventRecords =
                queryEventRecordsForDay(sReportKey[0], sDayIndex[0]);
        assertThat(eventRecords)
                .containsExactly(
                        EventRecordAndSystemProfile.create(
                                sSystemProfile[0], sEventVector[0], sAggregateValue[0]),
                        EventRecordAndSystemProfile.create(
                                sSystemProfile[0], sEventVector[1], sAggregateValue[0]));

        eventRecords = queryEventRecordsForDay(sReportKey[0], sDayIndex[1]);
        assertThat(eventRecords)
                .containsExactly(
                        EventRecordAndSystemProfile.create(
                                sSystemProfile[0], sEventVector[0], sAggregateValue[0]));
    }

    @Test
    public void testQueryStringListIndex_nothingFoundReturnsZero() throws Exception {
        int bufferMax = 0;
        int index = 12;

        // Insert two (hash, index) pairs to confirm the index of the matching hash is selected.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);

        // Search for a string hash for a different report.
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[1], sDayIndex[0], bufferMax, sHashCodes[0]))
                .isEqualTo(0);

        // Search for a string hash on a different day.
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[1], bufferMax, sHashCodes[0]))
                .isEqualTo(0);
    }

    @Test
    public void testQueryStringListIndex_matchPicked_noBufferMax() throws Exception {
        int bufferMax = 0;
        int index = 12;

        // Insert two (hash, index) pairs to confirm a the index of the matching hash is selected.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[0], bufferMax, sHashCodes[0]))
                .isEqualTo(index);
    }

    @Test
    public void testQueryStringListIndex_matchPicked_bufferMaxIgnored() throws Exception {
        int bufferMax = 1;
        int index = 12;

        // Insert two (hash, index) pairs to confirm a the index of the matching hash is selected.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[0], bufferMax, sHashCodes[0]))
                .isEqualTo(index);
    }

    @Test
    public void testQueryStringListIndex_newHashGetsNextIndex_noBufferMax() throws Exception {
        int bufferMax = 0;
        int index = 12;

        // Insert two (hash, index) pairs to confirm a new hash gets and index equal to 1 more than
        // the max index.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[0], bufferMax, sHashCodes[2]))
                .isEqualTo(index + 2);
    }

    @Test
    public void testQueryStringListIndex_newHashGetsNextIndex_indexLessThanBufferMax()
            throws Exception {
        int bufferMax = 100;
        int index = 12;

        // Insert two (hash, index) pairs to confirm a new hash gets and index equal to 1 more than
        // the max index.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[0], bufferMax, sHashCodes[2]))
                .isEqualTo(index + 2);
    }

    @Test
    public void testQueryStringListIndex_newHashGetsNegativeOne_bufferMaxReached()
            throws Exception {
        int bufferMax = 14;
        int index = 12;

        // Insert two (hash, index) pairs to confirm a new hash gets and index equal to 1 more than
        // the max index.
        insertStringHash(sReportKey[0], sDayIndex[0], index, sHashCodes[0]);
        insertStringHash(sReportKey[0], sDayIndex[0], index + 1, sHashCodes[1]);
        assertThat(
                        mDaoBuildingBlocks.queryStringListIndex(
                                sReportKey[0], sDayIndex[0], bufferMax, sHashCodes[2]))
                .isEqualTo(-1);
    }

    @Test
    public void testDeleteOldAggregates() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);

        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[1],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        mDaoBuildingBlocks.deleteOldAggregates(sDayIndex[1]);
        assertThat(queryCountEventVectors(sReportKey[0], sDayIndex[0], sSystemProfileHash[0]))
                .isEqualTo(0);
        assertThat(queryCountEventVectors(sReportKey[0], sDayIndex[1], sSystemProfileHash[0]))
                .isEqualTo(1);
    }

    @Test
    public void testDeletesAggregatesOnReportsDelete() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        insertAggregateValue(
                sReportKey[0],
                sDayIndex[0],
                sEventVector[0],
                sSystemProfileHash[0],
                sAggregateValue[0]);

        mTestOnlyDao.deleteAllReports();

        Cursor cursor = mCobaltDatabase.query("SELECT COUNT(*) FROM AggregateStore", null);
        assertThat(cursor.moveToFirst()).isTrue();
        assertThat(cursor.getInt(0)).isEqualTo(0);
    }

    @Test
    public void testDeleteUnusedSystemProfileHashes() throws Exception {
        insertSystemProfileAndReport(
                sSystemProfileHash[0], sSystemProfile[0], sReportKey[0], sDayIndex[0]);
        mDaoBuildingBlocks.deleteUnusedSystemProfileHashes();

        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        insertAggregateValue(
                                sReportKey[0],
                                sDayIndex[0],
                                sEventVector[0],
                                sSystemProfileHash[0],
                                sAggregateValue[0]));
    }

    @Test
    public void testInsertLastSentDayIndex_reportAlreadyExists() throws Exception {
        insertLastSentDayIndex(sReportKey[0], sDayIndex[0]);
        insertLastSentDayIndex(sReportKey[0], sDayIndex[1]);

        Optional<Integer> dayIndex = queryLastSentDayIndex(sReportKey[0]);
        assertThat(dayIndex).isEqualTo(Optional.of(sDayIndex[0]));
    }

    @Test
    public void testUpdateLastSentDayIndex() throws Exception {
        insertLastSentDayIndex(sReportKey[0], sDayIndex[0]);
        updateLastSentDayIndex(sReportKey[0], sDayIndex[1]);

        Optional<Integer> dayIndex = queryLastSentDayIndex(sReportKey[0]);
        assertThat(dayIndex).isEqualTo(Optional.of(sDayIndex[1]));
    }

    @Test
    public void testUpdateLastSentDayIndex_reportDoesNotExist() throws Exception {
        updateLastSentDayIndex(sReportKey[0], sDayIndex[0]);

        Optional<Integer> dayIndex = queryLastSentDayIndex(sReportKey[0]);
        assertThat(dayIndex).isEqualTo(Optional.empty());
    }

    @Test
    public void testQueryLastSentDayIndex() throws Exception {
        insertLastSentDayIndex(sReportKey[0], sDayIndex[0]);

        Optional<Integer> dayIndex = queryLastSentDayIndex(sReportKey[0]);
        assertThat(dayIndex).isEqualTo(Optional.of(sDayIndex[0]));
    }

    @Test
    public void testQueryLastSentDayIndex_reportDoesNotExist() throws Exception {
        Optional<Integer> dayIndex = queryLastSentDayIndex(sReportKey[0]);
        assertThat(dayIndex).isEqualTo(Optional.empty());
    }

    @Test
    public void testQueryOldestObservations() throws Exception {
        UnencryptedObservationBatch observationBatch =
                UnencryptedObservationBatch.getDefaultInstance();
        insertObservations(
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch));

        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, observationBatch),
                        ObservationStoreEntity.create(2, observationBatch),
                        ObservationStoreEntity.create(3, observationBatch),
                        ObservationStoreEntity.create(4, observationBatch),
                        ObservationStoreEntity.create(5, observationBatch));
    }

    @Test
    public void testDeleteByObservationId() throws Exception {
        UnencryptedObservationBatch observationBatch =
                UnencryptedObservationBatch.getDefaultInstance();
        insertObservations(
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch));

        mDaoBuildingBlocks.deleteByObservationId(List.of(2, 3, 4));
        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, observationBatch),
                        ObservationStoreEntity.create(5, observationBatch));
    }

    @Test
    public void testDeleteByObservationId_observationIdDoesNotExist() throws Exception {
        UnencryptedObservationBatch observationBatch =
                UnencryptedObservationBatch.getDefaultInstance();
        insertObservations(
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch),
                ObservationStoreEntity.create(0, observationBatch));

        mDaoBuildingBlocks.deleteByObservationId(List.of(6));
        assertThat(mDaoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(1, observationBatch),
                        ObservationStoreEntity.create(2, observationBatch),
                        ObservationStoreEntity.create(3, observationBatch),
                        ObservationStoreEntity.create(4, observationBatch),
                        ObservationStoreEntity.create(5, observationBatch));
    }
}
