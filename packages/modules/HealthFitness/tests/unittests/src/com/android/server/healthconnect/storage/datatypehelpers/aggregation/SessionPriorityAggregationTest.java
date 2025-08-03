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

import static com.android.server.healthconnect.storage.datatypehelpers.aggregation.PriorityAggregationTestDataFactory.createSessionData;

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

public class SessionPriorityAggregationTest {
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

    @Mock Cursor mCursor;
    @Mock HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;

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
    public void testOneSession_startedEarlierThanWindow() {
        doReturn(createSessionData(5, 15, 1)).when(mOneGroupAggregator).readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(5.0);
    }

    @Test
    public void testOneSession_biggerThanWindow() {
        doReturn(createSessionData(5, 25, 1)).when(mOneGroupAggregator).readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testOneSession_equalToWindow() {
        doReturn(createSessionData(10, 20, 1)).when(mOneGroupAggregator).readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testOneSession_oneExcludeInterval() {
        doReturn(createSessionData(5, 15, 1, List.of(10L), List.of(15L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(0.0);
    }

    @Test
    public void testOneSession_severalExcludeIntervals() {
        doReturn(createSessionData(5, 25, 1, List.of(5L, 14L, 18L), List.of(11L, 16L, 22L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0 - 1.0 - 2.0 - 2.0);
    }

    @Test
    public void testOneSession_severalExcludeIntervalsInMultiGroup() {
        doReturn(createSessionData(15, 35, 1, List.of(18L, 24L, 28L), List.of(22L, 26L, 34L)))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);
        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(3.0); // Group 10-20
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(4.0); // Group 20-30
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isEqualTo(1.0); // Group 30-40
    }

    @Test
    public void testOneSession_emptyBucketResultIsNull() {
        doReturn(createSessionData(20, 35, 1, List.of(20L, 24L, 28L), List.of(22L, 26L, 34L)))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);
        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isNull(); // Group 10-20
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(4.0); // Group 20-30
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isEqualTo(1.0); // Group 30-40
    }

    @Test
    public void testOneSession_newAggregation_noPriority_returnsNothing() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(createSessionData(10, 20, Integer.MIN_VALUE))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }

    @Test
    public void testTwoSessions_newAggregation_sessionWithoutPriority_isNotCounted() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(
                        createSessionData(5, 18, 1),
                        createSessionData(12, 25, Integer.MIN_VALUE, List.of(13L), List.of(16L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(8.0);
    }

    // TODO testTwoSessions_noneHasPriority_returnsNothing
    @Test
    public void testTwoSessions_newAggregation_sessionsWithoutPriority_returnsNull() {
        when(mHealthConnectDeviceConfigManager.isAggregationSourceControlsEnabled())
                .thenReturn(true);
        doReturn(
                        createSessionData(5, 18, Integer.MIN_VALUE),
                        createSessionData(12, 25, Integer.MIN_VALUE, List.of(13L), List.of(16L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isNull();
    }

    @Test
    public void testTwoSessions_noOverlaps() {
        doReturn(createSessionData(5, 12, 1), createSessionData(15, 25, 2))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(7.0);
    }

    @Test
    public void testTwoSessions_excludeIntervalInTheOverlap_ignoredAsLowerPriority() {
        doReturn(
                        createSessionData(5, 18, 1),
                        createSessionData(12, 25, 0, List.of(13L), List.of(16L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(10.0);
    }

    @Test
    public void testTwoSessions_excludeIntervalInTheOverlap_accountedAsHigherPriority() {
        doReturn(
                        createSessionData(5, 18, 2),
                        createSessionData(12, 25, 3, List.of(13L), List.of(16L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(7.0);
    }

    @Test
    public void testTwoInternalSessions_excludeIntervalInTheOverlap_accountedAsHigherPriority() {
        doReturn(
                        createSessionData(12, 16, 1),
                        createSessionData(14, 18, 2, List.of(14L), List.of(16L)))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isEqualTo(4.0);
    }

    @Test
    public void testTwoSession_severalExcludeIntervalsInMultiGroup() {
        doReturn(
                        createSessionData(10, 29, 2),
                        createSessionData(
                                15, 35, 1, List.of(18L, 24L, 28L), List.of(22L, 26L, 34L)))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);
        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(10.0); // Group 10-20
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(9.0); // Group 20-30
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isEqualTo(1.0); // Group 30-40
    }

    @Test
    public void testMultiSessions_multiGroups() {
        doReturn(
                        createSessionData(15, 22, 1),
                        createSessionData(25, 28, 1),
                        createSessionData(29, 35, 1))
                .when(mMultiGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, true, false);
        mMultiGroupAggregator.calculateAggregation(mCursor);
        assertThat(mMultiGroupAggregator.getResultForGroup(0)).isEqualTo(5.0);
        assertThat(mMultiGroupAggregator.getResultForGroup(1)).isEqualTo(2.0 + 3.0 + 1.0);
        assertThat(mMultiGroupAggregator.getResultForGroup(2)).isEqualTo(5.0);
    }
}
