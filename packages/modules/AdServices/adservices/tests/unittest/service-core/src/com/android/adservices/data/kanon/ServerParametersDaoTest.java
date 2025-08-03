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

package com.android.adservices.data.kanon;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class ServerParametersDaoTest {
    private ServerParametersDao mServerParametersDao;
    private static final String SERVER_PARAMS_VERSION_1 = "server parameter version";
    private static final Instant INSTANT_1 = Instant.now();
    private static final Instant INSTANT_2 = Instant.now().plusSeconds(123);
    private static final Instant INSTANT_3 = Instant.now().plusSeconds(1245);
    private static final byte[] SERVER_PUBLIC_PARAMS = {1, 2};
    private static final Instant STATIC_INSTANT = Instant.now();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private final DBServerParameters.Builder mDefaultDBServerParametersBuilder =
            DBServerParameters.builder()
                    .setServerParamsVersion(SERVER_PARAMS_VERSION_1)
                    .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                    .setServerParamsSignExpiryInstant(INSTANT_1)
                    .setServerParamsJoinExpiryInstant(INSTANT_2)
                    .setCreationInstant(INSTANT_3);

    @Before
    public void setup() {
        mServerParametersDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class)
                        .build()
                        .serverParametersDao();
    }

    @Test
    public void testGetActiveServerParameters_noActiveParamsPresents_returnsNull() {
        List<DBServerParameters> dbServerParameters =
                mServerParametersDao.getActiveServerParameters(Instant.now());

        assertThat(dbServerParameters).isEmpty();
    }

    @Test
    public void testGetActiveServerParameters_activeParamsPresent_shouldReturnParams() {
        DBServerParameters dbServerParameters =
                mDefaultDBServerParametersBuilder
                        .setServerParamsSignExpiryInstant(STATIC_INSTANT.plusSeconds(1234))
                        .build();
        mServerParametersDao.insertServerParameters(dbServerParameters);

        List<DBServerParameters> fetchedServerParametersList =
                mServerParametersDao.getActiveServerParameters(STATIC_INSTANT);

        assertThat(fetchedServerParametersList).isNotEmpty();
    }

    @Test
    public void testGetActiveServerParameters_inactiveServerParamsPresent_shouldReturnNull() {
        DBServerParameters expiredServerParams =
                mDefaultDBServerParametersBuilder
                        .setServerParamsSignExpiryInstant(STATIC_INSTANT.minusSeconds(100))
                        .build();
        mServerParametersDao.insertServerParameters(expiredServerParams);

        List<DBServerParameters> fetchedServerParametersList =
                mServerParametersDao.getActiveServerParameters(STATIC_INSTANT);

        assertThat(fetchedServerParametersList).isEmpty();
    }

    @Test
    public void testRemoveExpiredParameters_onlyDeletesExpiredParameters() {
        byte[] activePublicParams = {2, 5, 2, 1};
        DBServerParameters expiredServerParams =
                mDefaultDBServerParametersBuilder
                        .setServerParamsSignExpiryInstant(STATIC_INSTANT.minusSeconds(100))
                        .build();
        DBServerParameters activeParams =
                mDefaultDBServerParametersBuilder
                        .setServerPublicParameters(activePublicParams)
                        .setServerParamsSignExpiryInstant(STATIC_INSTANT.plusSeconds(100))
                        .build();
        mServerParametersDao.insertServerParameters(expiredServerParams);
        mServerParametersDao.insertServerParameters(activeParams);

        mServerParametersDao.removeExpiredServerParameters(STATIC_INSTANT);

        List<DBServerParameters> fetchedServerParametersList =
                mServerParametersDao.getActiveServerParameters(STATIC_INSTANT);
        assertThat(fetchedServerParametersList.size()).isEqualTo(1);
        assertThat(fetchedServerParametersList.get(0).getServerPublicParameters())
                .isEqualTo(activePublicParams);
    }

    @Test
    public void testDeleteAllParameters_deletesAllParameters() {
        DBServerParameters expiredServerParams =
                mDefaultDBServerParametersBuilder
                        .setServerParamsSignExpiryInstant(Instant.now().minusSeconds(100))
                        .build();
        DBServerParameters activeParams =
                mDefaultDBServerParametersBuilder
                        .setServerParamsSignExpiryInstant(Instant.now().minusSeconds(100))
                        .build();
        mServerParametersDao.insertServerParameters(expiredServerParams);
        mServerParametersDao.insertServerParameters(activeParams);

        mServerParametersDao.deleteAllServerParameters();

        List<DBServerParameters> fetchedServerParametersList =
                mServerParametersDao.getActiveServerParameters(Instant.now());
        assertThat(fetchedServerParametersList).isEmpty();
    }
}
