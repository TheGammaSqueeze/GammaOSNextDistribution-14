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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class TimeRangeFilterTest {
    private static final String TAG = "TimeRangeFilterTest";

    @Test
    public void testLocalTimeRangeFilter() {
        LocalDateTime startTime = LocalDateTime.MIN;
        LocalDateTime endTime = LocalDateTime.MAX;
        LocalTimeRangeFilter timeRangeFilter =
                new LocalTimeRangeFilter.Builder()
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build();

        assertThat(timeRangeFilter.getStartTime()).isEqualTo(startTime);
        assertThat(timeRangeFilter.getEndTime()).isEqualTo(endTime);
        assertThat(timeRangeFilter.isBounded()).isTrue();
    }

    @Test
    public void testTimeInstantRangeFilter() {
        Instant startTime = Instant.now();
        Instant endTime = Instant.now().plusMillis(1000);
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build();

        assertThat(timeRangeFilter.getStartTime()).isEqualTo(startTime);
        assertThat(timeRangeFilter.getEndTime()).isEqualTo(endTime);
        assertThat(timeRangeFilter.isBounded()).isTrue();
    }

    @Test
    public void testStartTimeInstantRangeFilter_startTimeNull() {
        Instant endTime = Instant.now().plusMillis(1000);
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder().setEndTime(endTime).build();
        assertThat(timeRangeFilter.getStartTime()).isEqualTo(Instant.EPOCH);
    }

    @Test
    public void testEndTimeInstantRangeFilter_endTimeNull() {
        Instant startTime = Instant.now();
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder().setStartTime(startTime).build();
        assertThat(
                        Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli()
                                - timeRangeFilter.getEndTime().toEpochMilli())
                .isLessThan(100);
    }

    @Test
    public void testStartTimeLocalRangeFilter_startTimeNull() {
        LocalDateTime endTime = LocalDateTime.MAX;
        LocalTimeRangeFilter timeRangeFilter =
                new LocalTimeRangeFilter.Builder().setEndTime(endTime).build();
        assertThat(timeRangeFilter.getStartTime().toInstant(ZoneOffset.MAX))
                .isLessThan(Instant.EPOCH);
    }

    @Test
    public void testEndTimeLocalRangeFilter_endTimeNull() {
        LocalDateTime startTime = LocalDateTime.MIN;
        LocalTimeRangeFilter timeRangeFilter =
                new LocalTimeRangeFilter.Builder().setStartTime(startTime).build();
        assertThat(
                        timeRangeFilter.getEndTime().toInstant(ZoneOffset.MAX).toEpochMilli()
                                - Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli())
                .isLessThan(100);
    }

    @Test
    public void instantTimeRange_startTimeNotLaterThanEndTime_throws() {
        Instant time = Instant.ofEpochMilli(123456789);
        TimeInstantRangeFilter.Builder builder =
                new TimeInstantRangeFilter.Builder().setStartTime(time).setEndTime(time);
        Throwable thrown = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(thrown).hasMessageThat().contains("end time needs to be after start time");

        builder.setEndTime(time.minusMillis(1));
        thrown = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(thrown).hasMessageThat().contains("end time needs to be after start time");
    }

    @Test
    public void localTimeRange_startTimeNotLaterThanEndTime_throws() {
        LocalDateTime time = LocalDateTime.of(2024, 2, 1, 18, 0, 0);
        LocalTimeRangeFilter.Builder builder =
                new LocalTimeRangeFilter.Builder().setStartTime(time).setEndTime(time);
        Throwable thrown = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(thrown).hasMessageThat().contains("end time needs to be after start time");

        builder.setEndTime(time.minusSeconds(1));
        thrown = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(thrown).hasMessageThat().contains("end time needs to be after start time");
    }

    @Test
    public void instantTimeRange_bothEndsOpen_throws() {
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new TimeInstantRangeFilter.Builder().build());
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Both start time and end time cannot be null.");
    }

    @Test
    public void localTimeRange_bothEndsOpen_throws() {
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new LocalTimeRangeFilter.Builder().build());
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Both start time and end time cannot be null.");
    }
}
