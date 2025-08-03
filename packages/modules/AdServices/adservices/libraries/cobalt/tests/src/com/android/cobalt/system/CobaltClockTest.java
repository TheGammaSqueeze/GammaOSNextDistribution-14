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

package com.android.cobalt.system;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.TimeZonePolicy;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.zone.ZoneRulesException;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class CobaltClockTest {
    private static final Instant CURRENT_TIME = Instant.parse("2022-07-29T01:15:30.00Z");
    private static final int DAY_INDEX_UTC = 19202;

    private final CobaltClock mClock = new CobaltClock(CURRENT_TIME.toEpochMilli());

    @Test
    public void dayIndexUtc_succeeds() throws Exception {
        assertThat(mClock.dayIndexUtc()).isEqualTo(DAY_INDEX_UTC);
    }

    @Test
    public void dayIndexLocal_succeeds() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        // One day earlier than the day index in UTC.
        assertThat(mClock.dayIndexLocal()).isEqualTo(DAY_INDEX_UTC - 1);
    }

    @Test
    public void dayIndex_mtv_succeeds() throws Exception {
        // One day earlier than the day index in UTC.
        assertThat(mClock.dayIndex("America/Los_Angeles")).isEqualTo(DAY_INDEX_UTC - 1);
    }

    @Test
    public void dayIndex_eu_succeeds() throws Exception {
        // Same day as the day index in UTC.
        assertThat(mClock.dayIndex("Europe/Lisbon")).isEqualTo(DAY_INDEX_UTC);
    }

    @Test
    public void dayIndex_invalidTimeZone_throwsException() throws Exception {
        assertThrows(ZoneRulesException.class, () -> mClock.dayIndex("Pacific"));
    }

    @Test
    public void dayIndex_metricInUtc_succeeds() throws Exception {
        assertThat(
                        mClock.dayIndex(
                                MetricDefinition.newBuilder()
                                        .setTimeZonePolicy(TimeZonePolicy.UTC)
                                        .build()))
                .isEqualTo(DAY_INDEX_UTC);
    }

    @Test
    public void dayIndex_metricLocal_succeeds() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Lisbon"));
        assertThat(
                        mClock.dayIndex(
                                MetricDefinition.newBuilder()
                                        .setTimeZonePolicy(TimeZonePolicy.LOCAL)
                                        .build()))
                .isEqualTo(DAY_INDEX_UTC);
    }

    @Test
    public void dayIndex_metricInMtv_succeeds() throws Exception {
        assertThat(
                        mClock.dayIndex(
                                MetricDefinition.newBuilder()
                                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                                        .setOtherTimeZone("America/Los_Angeles")
                                        .build()))
                .isEqualTo(DAY_INDEX_UTC - 1);
    }

    @Test
    public void dayIndex_unrecognizedPolicy_throwsException() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mClock.dayIndex(
                                MetricDefinition.newBuilder()
                                        .setTimeZonePolicyValue(478923642)
                                        .build()));
    }
}
