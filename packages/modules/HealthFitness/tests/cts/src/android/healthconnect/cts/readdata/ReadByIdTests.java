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

package android.healthconnect.cts.readdata;

import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecord;
import static android.healthconnect.cts.utils.TestUtils.getReadRecordsResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class ReadByIdTests {
    private static final int MAX_RECORD_NUM_PER_REQUEST = 5000;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setup() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void readDataById_noPageTokenSet() throws Exception {
        List<Record> records = insertRecords(List.of(getDistanceRecord()));
        String uuid = records.get(0).getMetadata().getId();
        ReadRecordsRequestUsingIds<DistanceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(DistanceRecord.class).addId(uuid).build();
        ReadRecordsResponse<?> response = getReadRecordsResponse(request);
        assertThat(response.getRecords()).isNotEmpty();
        assertThat(response.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void readDataByClientId_noPageTokenSet() throws Exception {
        String clientId = "calories";
        insertRecords(List.of(getTotalCaloriesBurnedRecord(clientId)));
        ReadRecordsRequestUsingIds<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(TotalCaloriesBurnedRecord.class)
                        .addClientRecordId(clientId)
                        .build();

        ReadRecordsResponse<?> response = getReadRecordsResponse(request);
        assertThat(response.getRecords()).isNotEmpty();
        assertThat(response.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void readDataByEmptyId_throws() {
        ReadRecordsRequestUsingIds.Builder<BasalMetabolicRateRecord> requestBuilder =
                new ReadRecordsRequestUsingIds.Builder<>(BasalMetabolicRateRecord.class);
        Throwable thrown = assertThrows(IllegalArgumentException.class, requestBuilder::build);
        assertThat(thrown).hasMessageThat().contains("RecordIdFilter list is empty");
    }

    @Test
    public void readDataById_nullId_throws() {
        ReadRecordsRequestUsingIds.Builder<SleepSessionRecord> requestBuilder =
                new ReadRecordsRequestUsingIds.Builder<>(SleepSessionRecord.class);

        assertThrows(NullPointerException.class, () -> requestBuilder.addId(null));
        assertThrows(NullPointerException.class, () -> requestBuilder.addClientRecordId(null));
    }

    @Test
    public void readDataById_maxPageSizeExceeded_throws() {
        ReadRecordsRequestUsingIds.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        for (int i = 0; i < MAX_RECORD_NUM_PER_REQUEST; i++) {
            request.addClientRecordId("client.id" + i);
        }
        Throwable thrown =
                assertThrows(IllegalArgumentException.class, () -> request.addId("extra_id"));
        assertThat(thrown).hasMessageThat().contains("Maximum allowed pageSize is 5000");
        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> request.addClientRecordId("extra_client_id"));
        assertThat(thrown).hasMessageThat().contains("Maximum allowed pageSize is 5000");
    }
}
