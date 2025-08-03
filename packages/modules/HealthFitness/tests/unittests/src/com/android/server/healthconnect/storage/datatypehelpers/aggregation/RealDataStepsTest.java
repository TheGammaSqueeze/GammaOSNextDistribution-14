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

import com.android.server.healthconnect.storage.request.AggregateParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

public class RealDataStepsTest {
    @Mock Cursor mCursor;
    PriorityRecordsAggregator mOneGroupAggregator;
    AggregateParams.PriorityAggregationExtraParams mParams =
            new AggregateParams.PriorityAggregationExtraParams("start", "end");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mOneGroupAggregator =
                Mockito.spy(
                        new PriorityRecordsAggregator(
                                List.of(
                                        Instant.EPOCH.toEpochMilli(),
                                        Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli()),
                                Collections.emptyList(),
                                0,
                                mParams,
                                false));
    }

    @Test
    public void testAppStepsRecord_returnCorrectAggregation() {
        doReturn(
                        createStepsData(1680130181252L, 1680130327908L, 86, 1, 1),
                        createStepsData(1680130171300L, 1680130181253L, 8, 1, 1),
                        createStepsData(1680129556036L, 1680129604922L, 59, 1, 1),
                        createStepsData(1680129486567L, 1680129527735L, 58, 1, 1),
                        createStepsData(1680128485699L, 1680129085194L, 147, 1, 1),
                        createStepsData(1680127875928L, 1680128434242L, 37, 1, 1),
                        createStepsData(1680124827055L, 1680124829629L, 6, 1, 1),
                        createStepsData(1680124798753L, 1680124801327L, 8, 1, 1),
                        createStepsData(1680124767878L, 1680124798754L, 14, 1, 1),
                        createStepsData(1680124724138L, 1680124767879L, 20, 1, 1),
                        createStepsData(1680124646950L, 1680124706128L, 25, 1, 1),
                        createStepsData(1680124508012L, 1680124564618L, 20, 1, 1),
                        createStepsData(1680124441116L, 1680124443690L, 10, 1, 1),
                        createStepsData(1680118073143L, 1680118134891L, 34, 1, 1),
                        createStepsData(1680118049988L, 1680118073144L, 12, 1, 1),
                        createStepsData(1680117463392L, 1680118049989L, 111, 1, 1),
                        createStepsData(1680117458246L, 1680117463393L, 11, 1, 1),
                        createStepsData(1680117399073L, 1680117450529L, 44, 1, 1),
                        createStepsData(1680117332181L, 1680117375919L, 31, 1, 1),
                        createStepsData(1680117131504L, 1680117162378L, 28, 1, 1),
                        createStepsData(1680117051746L, 1680117105777L, 76, 1, 1),
                        createStepsData(1680116992571L, 1680117051747L, 42, 1, 1),
                        createStepsData(1680116915387L, 1680116953980L, 58, 1, 1),
                        createStepsData(1680115353711L, 1680115479776L, 19, 1, 1),
                        createStepsData(1680114641056L, 1680115109300L, 87, 1, 1),
                        createStepsData(1680113884657L, 1680113910385L, 40, 1, 1),
                        createStepsData(1680113825483L, 1680113835775L, 9, 1, 1),
                        createStepsData(1680113763736L, 1680113822911L, 10, 1, 1),
                        createStepsData(1680113745727L, 1680113758591L, 27, 1, 1),
                        createStepsData(1680113676260L, 1680113719999L, 46, 1, 1),
                        createStepsData(1680112063079L, 1680112072972L, 15, 1, 1),
                        createStepsData(1680111563949L, 1680112063080L, 47, 1, 1),
                        createStepsData(1680107784458L, 1680107864216L, 74, 1, 1),
                        createStepsData(1680107179842L, 1680107362515L, 115, 1, 1),
                        createStepsData(1680105551308L, 1680105793127L, 111, 1, 1),
                        createStepsData(1680104218764L, 1680104751270L, 478, 1, 1),
                        createStepsData(1680104190465L, 1680104218765L, 22, 1, 1),
                        createStepsData(1680103454690L, 1680103681086L, 39, 1, 1),
                        createStepsData(1680102855233L, 1680103452118L, 560, 1, 1),
                        createStepsData(1680102840283L, 1680102855234L, 13, 1, 1),
                        createStepsData(1680101823540L, 1680102273783L, 68, 1, 1),
                        createStepsData(1680096909264L, 1680096914411L, 18, 1, 1),
                        createStepsData(1680095167526L, 1680095288445L, 30, 1, 1),
                        createStepsData(1680094995376L, 1680095033952L, 64, 1, 1),
                        createStepsData(1680094210449L, 1680094794477L, 774, 1, 1),
                        createStepsData(1680093514337L, 1680093524629L, 13, 1, 1),
                        createStepsData(1680092489676L, 1680092708184L, 39, 1, 1),
                        createStepsData(1680091800739L, 1680092083674L, 61, 1, 1),
                        createStepsData(1680091746460L, 1680091800740L, 4, 1, 1),
                        createStepsData(1680091018755L, 1680091268242L, 121, 1, 1),
                        createStepsData(1680091003258L, 1680091018756L, 7, 1, 1),
                        createStepsData(1680090308769L, 1680090578862L, 84, 1, 1),
                        createStepsData(1680089005371L, 1680089023380L, 23, 1, 1),
                        createStepsData(1680087810615L, 1680088193923L, 185, 1, 1),
                        createStepsData(1680084206820L, 1680084479461L, 70, 1, 1),
                        createStepsData(1680079531785L, 1680079843045L, 163, 1, 1),
                        createStepsData(1680078057555L, 1680078445690L, 32, 1, 1),
                        createStepsData(1680076875248L, 1680076880394L, 18, 1, 1),
                        createStepsData(1680076607806L, 1680076615525L, 16, 1, 1),
                        createStepsData(1680076525481L, 1680076582080L, 37, 1, 1),
                        createStepsData(1680072888161L, 1680072890735L, 4, 1, 1),
                        createStepsData(1680072826420L, 1680072885590L, 45, 1, 1),
                        createStepsData(1680072224498L, 1680072324788L, 77, 1, 1),
                        createStepsData(1680072184127L, 1680072224499L, 26, 1, 1),
                        createStepsData(1680071303601L, 1680071704943L, 119, 1, 1),
                        createStepsData(1680069705093L, 1680069710240L, 12, 1, 1),
                        createStepsData(1680067067887L, 1680067070460L, 7, 1, 1),
                        createStepsData(1680067031036L, 1680067033610L, 11, 1, 1),
                        createStepsData(1680064629656L, 1680064650240L, 33, 1, 1),
                        createStepsData(1680064022872L, 1680064549953L, 35, 1, 1),
                        createStepsData(1680063981567L, 1680064022873L, 30, 1, 1),
                        createStepsData(1680063291100L, 1680063296247L, 9, 1, 1),
                        createStepsData(1680061885509L, 1680061987235L, 42, 1, 1),
                        createStepsData(1680061597465L, 1680061885510L, 38, 1, 1),
                        createStepsData(1680061540888L, 1680061597466L, 12, 1, 1),
                        createStepsData(1680061497153L, 1680061540889L, 36, 1, 1),
                        createStepsData(1680061218334L, 1680061497154L, 64, 1, 1))
                .when(mOneGroupAggregator)
                .readNewData(mCursor);
        when(mCursor.moveToNext())
                .thenReturn(
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, false);
        mOneGroupAggregator.calculateAggregation(mCursor);
        assertThat(mOneGroupAggregator.getResultForGroup(0)).isWithin(1.0).of(5084.0);
    }
}
