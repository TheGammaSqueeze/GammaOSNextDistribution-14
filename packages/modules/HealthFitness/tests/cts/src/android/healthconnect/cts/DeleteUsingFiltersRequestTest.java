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

import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.BloodGlucoseRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeartRateRecord;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Set;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class DeleteUsingFiltersRequestTest {
    private static final String TAG = "DeleteUsingFiltersRequestTest";

    @Test
    public void testCreateDeleteUsingFiltersRequest() {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        DataOrigin dataOrigin = new DataOrigin.Builder().setPackageName("test_package").build();
        DeleteUsingFiltersRequest deleteUsingFiltersRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodGlucoseRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .addDataOrigin(dataOrigin)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build();

        assertThat(deleteUsingFiltersRequest.getTimeRangeFilter()).isEqualTo(timeRangeFilter);
        assertThat(deleteUsingFiltersRequest.getDataOrigins()).isEqualTo(Set.of(dataOrigin));
        assertThat(deleteUsingFiltersRequest.getRecordTypes())
                .isEqualTo(Set.of(BloodGlucoseRecord.class, HeartRateRecord.class));
    }

    @Test
    public void testCreateDeleteUsingFiltersRequest_clearDataOriginsAndRecords() {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        DataOrigin dataOrigin = new DataOrigin.Builder().setPackageName("test_package").build();
        DeleteUsingFiltersRequest deleteUsingFiltersRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodGlucoseRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .addDataOrigin(dataOrigin)
                        .clearDataOrigins()
                        .clearRecordTypes()
                        .setTimeRangeFilter(timeRangeFilter)
                        .build();

        assertThat(deleteUsingFiltersRequest.getTimeRangeFilter()).isEqualTo(timeRangeFilter);
        assertThat(deleteUsingFiltersRequest.getDataOrigins()).isEqualTo(Set.of());
        assertThat(deleteUsingFiltersRequest.getRecordTypes()).isEqualTo(Set.of());
    }
}
