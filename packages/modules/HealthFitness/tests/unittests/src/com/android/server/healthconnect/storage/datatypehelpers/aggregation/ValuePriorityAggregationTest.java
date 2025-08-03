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

package com.android.server.healthconnect.storage.datatypehelpers.aggregation;

import static com.android.server.healthconnect.storage.datatypehelpers.aggregation.PriorityAggregationTestDataFactory.createStepsData;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.database.Cursor;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.storage.request.AggregateParams;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

public class ValuePriorityAggregationTest {
    @Mock Cursor mCursor;
    @Mock HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;

    PriorityRecordsAggregator mOneGroupAggregator;
    PriorityRecordsAggregator mMultiGroupAggregator;
    AggregateParams.PriorityAggregationExtraParams mParams =
            new AggregateParams.PriorityAggregationExtraParams("start", "end");

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectDeviceConfigManager.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mOneGroupAggregator =
                Mockito.spy(
                        new PriorityRecordsAggregator(
                                List.of(10L, 20L), Collections.emptyList(), 0, mParams, false));

        mMultiGroupAggregator =
                Mockito.spy(
                        new PriorityRecordsAggregator(
                                List.of(10L, 20L, 30L, 40L),
                                Collections.emptyList(),
                                0,
                                mParams,
                                false));
        when(HealthConnectDeviceConfigManager.getInitialisedInstance())
                .thenReturn(mHealthConnectDeviceConfigManager);
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(false);
    }

    @Test
    public void testOneStepsRecord_equalToWindow() {
        doReturn(createStepsData(10, 20, 10, 100, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testOneStepsRecord_doesntHavePriority_accountedForAggregation() {
        doReturn(createStepsData(10, 20, 10, Integer.MIN_VALUE, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testOneStepsRecord_startAndEndEqualToWindowStart_accountedForAggregation() {
        doReturn(createStepsData(10, 10, 10, 1, 1)).when(mOneGroupAggregator).readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testOneStepsRecord_startAndEndEqualToWindowEnd_notAccountedForAggregation() {
        doReturn(createStepsData(20, 20, 10, 1, 1)).when(mOneGroupAggregator).readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }

    @Test
    public void testOneStepsRecordMultigroup_recordInOneGroup_otherResultsAreNull() {
        for (int groupWithRecord = 0; groupWithRecord < 3; groupWithRecord++) {
            setUp();
            doReturn(
                            createStepsData(
                                    10 * (groupWithRecord + 1),
                                    10 * (groupWithRecord + 2),
                                    10,
                                    100,
                                    10))
                    .when(mMultiGroupAggregator)
                    .readNewData(mCursor);
            when(mCursor.moveToNext()).thenReturn(true, false);
            mMultiGroupAggregator.calculateAggregation(mCursor);

            for (int groupNumberToCheck = 0; groupNumberToCheck < 3; groupNumberToCheck++) {
                if (groupNumberToCheck == groupWithRecord) {
                    assertThat(mMultiGroupAggregator.getResultForGroup(groupNumberToCheck))
                            .isEqualTo(10);
                } else {
                    assertThat(mMultiGroupAggregator.getResultForGroup(groupNumberToCheck))
                            .isNull();
                }
            }
        }
    }

    @Test
    public void testOneStepsRecordMultigroup_instantRecordInOneGroup_otherResultsAreNull() {
        for (int groupWithRecord = 0; groupWithRecord < 3; groupWithRecord++) {
            setUp();
            doReturn(
                            createStepsData(
                                    10 * (groupWithRecord + 1),
                                    10 * (groupWithRecord + 1),
                                    10,
                                    100,
                                    10))
                    .when(mMultiGroupAggregator)
                    .readNewData(mCursor);
            when(mCursor.moveToNext()).thenReturn(true, false);
            mMultiGroupAggregator.calculateAggregation(mCursor);

            for (int groupNumberToCheck = 0; groupNumberToCheck < 3; groupNumberToCheck++) {
                if (groupNumberToCheck == groupWithRecord) {
                    assertThat(mMultiGroupAggregator.getResultForGroup(groupNumberToCheck))
                            .isEqualTo(10);
                } else {
                    assertThat(mMultiGroupAggregator.getResultForGroup(groupNumberToCheck))
                            .isNull();
                }
            }
        }
    }

    @Test
    public void testTwoStepsRecords_allTimesEqual_highestPriorityAccounted() {
        doReturn(createStepsData(15, 15, 10, 100, 10), createStepsData(15, 15, 20, 200, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20);
    }

    @Test
    public void testTwoStepsRecordMultigroup_overlapInTheMiddleGroup() {
        doReturn(createStepsData(15, 27, 12, 1, 10), createStepsData(22, 35, 130, 2, 10))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);

        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(5);
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(2 + 50 + 30);
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isEqualTo(50);
    }

    @Test
    public void testTwoStepsRecordMultigroup_overlapBetweenGroups() {
        doReturn(createStepsData(15, 27, 12, 1, 10), createStepsData(19, 30, 110, 2, 10))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);

        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(4 + 10);
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(100);
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isNull();
    }

    @Test
    public void testTwoStepsRecordMultigroup_overlapBetweenGroups_firstHigherPriority() {
        doReturn(createStepsData(15, 27, 12, 2, 10), createStepsData(19, 30, 110, 1, 10))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);

        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(4 + 1);
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(7 + 30);
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isNull();
    }

    @Test
    public void testTwoStepsRecords_allTimesEqual_highestPriorityAccounted2() {
        doReturn(createStepsData(15, 15, 10, 200, 10), createStepsData(15, 15, 20, 100, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10);
    }

    @Test
    public void testTwoStepsRecords_allTimesEqualToGroupStart_highestPriorityAccounted() {
        doReturn(createStepsData(10, 10, 10, 100, 10), createStepsData(10, 10, 20, 200, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20);
    }

    @Test
    public void testTwoStepsRecords_ovelapByOnePoint() {
        doReturn(createStepsData(10, 12, 10, 100, 10), createStepsData(12, 14, 20, 200, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(30);
    }

    @Test
    public void testTwoStepsRecords_allTimesEqualToGroupEnd_noResult() {
        doReturn(createStepsData(20, 20, 10, 100, 10), createStepsData(20, 20, 20, 200, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }

    @Test
    public void testTwoStepsRecords_instantAndInterval_instantHasHigherPriorityStart() {
        doReturn(createStepsData(10, 10, 10, 2, 10), createStepsData(10, 15, 20, 1, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(30);
    }

    @Test
    public void testTwoStepsRecords_instantAndInterval_instantHasHigherPriorityMiddle() {
        doReturn(createStepsData(12, 12, 10, 2, 10), createStepsData(10, 15, 20, 1, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(30);
    }

    @Test
    public void testTwoStepsRecords_instantAndInterval_instantHasHigherPriorityEnd() {
        doReturn(createStepsData(15, 15, 10, 2, 10), createStepsData(10, 15, 20, 1, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(30);
    }

    @Test
    public void testTwoStepsRecords_instantAndInterval_instantHasLowerPriority() {
        doReturn(createStepsData(10, 10, 10, 1, 10), createStepsData(10, 15, 20, 2, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20);
    }

    @Test
    public void testTwoStepsRecords_noOneHasPriority_latestAccountedForAggregation() {
        doReturn(
                        createStepsData(10, 20, 10, Integer.MIN_VALUE, 10),
                        createStepsData(10, 20, 20, Integer.MIN_VALUE, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20);
    }

    @Test
    public void testTwoStepsRecords_samePriority_latestRecordIsPicked() {
        doReturn(createStepsData(10, 20, 20, 1, 10), createStepsData(10, 20, 15, 1, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20.0);
    }

    @Test
    public void testTwoStepsRecords_samePriorityWithOverlap_latestRecordIsPicked() {
        doReturn(createStepsData(10, 17, 7, 1, 10), createStepsData(12, 20, 80, 1, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(7.0 + 30.0);
    }

    @Test
    public void testTwoStepsRecords_samePriorityWithOverlapSecondLater_latestRecordIsPicked() {
        doReturn(createStepsData(10, 17, 7, 1, 1), createStepsData(12, 20, 80, 1, 10))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(2.0 + 80.0);
    }

    @Test
    public void testThreeStepsRecords_onlyOneHasPriority_withPriorityAccountedForAggregation() {
        doReturn(
                        createStepsData(10, 20, 15, 1, 1),
                        createStepsData(10, 20, 20, Integer.MIN_VALUE, 10),
                        createStepsData(10, 20, 25, Integer.MIN_VALUE, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(15);
    }

    @Test
    public void testThreeStepsRecords_ovelapByOnePoint_instantHasMiddlePriority_instantIgnored() {
        doReturn(
                        createStepsData(10, 12, 10, 1, 10),
                        createStepsData(12, 12, 10, 2, 10),
                        createStepsData(12, 14, 20, 3, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(30);
    }

    @Test
    public void
            testThreeStepsRecords_ovelapByOnePoint_instantHasHighestPriority_instantAccounted() {
        doReturn(
                        createStepsData(10, 12, 10, 1, 10),
                        createStepsData(12, 12, 10, 4, 10),
                        createStepsData(12, 14, 20, 3, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(40);
    }

    @Test
    public void testOneStepRecord_newAggregation_noPriority_notAccountedForAggregation() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(createStepsData(10, 20, 10, Integer.MIN_VALUE, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }

    @Test
    public void
            testTwoStepRecords_newAggregation_recordWithNoPriority_notAccountedForAggregation() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(
                        createStepsData(15, 15, 10, Integer.MIN_VALUE, 10),
                        createStepsData(15, 15, 20, 100, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(20);
    }

    @Test
    public void
            testTwoStepRecords_newAggregation_noRecordsWithPriority_notAccountedForAggregation() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(
                        createStepsData(15, 15, 10, Integer.MIN_VALUE, 10),
                        createStepsData(15, 15, 20, Integer.MIN_VALUE, 20))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }
}
