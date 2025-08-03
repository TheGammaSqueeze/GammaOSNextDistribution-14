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

package com.android.server.healthconnect.storage.request;

import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.StepsRecord;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ReadTransactionRequestTest {
    @Rule public final HealthConnectDatabaseTestRule testRule = new HealthConnectDatabaseTestRule();

    @Before
    public void setup() {
        HealthConnectUserContext context = testRule.getUserContext();
        TransactionManager.getInstance(context);
    }

    @Test
    public void createReadByFilterRequest_noPageToken_correctPaginationInfo() {
        PageTokenWrapper expectedToken = PageTokenWrapper.ofAscending(false);
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(false)
                        .setPageSize(500)
                        .build();

        ReadTransactionRequest request =
                getReadTransactionRequest(readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isEqualTo(expectedToken);
        assertThat(request.getPageSize()).isEqualTo(Optional.of(500));
    }

    @Test
    public void createReadByFilterRequest_hasPageToken_correctPaginationInfo() {
        PageTokenWrapper expectedToken = PageTokenWrapper.of(true, 9876, 2);
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageToken(expectedToken.encode())
                        .setPageSize(500)
                        .build();
        ReadTransactionRequest request =
                getReadTransactionRequest(readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isEqualTo(expectedToken);
        assertThat(request.getPageSize()).isEqualTo(Optional.of(500));
    }

    @Test
    public void createReadByIdRequest_singleType_noPaginationInfo() {
        ReadRecordsRequestUsingIds<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("id")
                        .build();
        ReadTransactionRequest request =
                getReadTransactionRequest(readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isNull();
        assertThat(request.getPageSize()).isEqualTo(Optional.empty());
    }

    @Test
    public void createReadByIdRequest_multipleType_noPaginationInfo() {
        List<UUID> ramdonUuids = ImmutableList.of(UUID.randomUUID());
        ReadTransactionRequest request =
                getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS, ramdonUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE, ramdonUuids));

        assertThat(request.getReadRequests()).hasSize(2);
        assertThat(request.getPageToken()).isNull();
        assertThat(request.getPageSize()).isEqualTo(Optional.empty());
    }
}
