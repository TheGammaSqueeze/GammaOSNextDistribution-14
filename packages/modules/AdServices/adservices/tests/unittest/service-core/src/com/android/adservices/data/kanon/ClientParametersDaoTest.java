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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ClientParametersDaoTest {

    private ClientParametersDao mClientParametersDao;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final UUID CLIENT_ID_1 = UUID.randomUUID();
    private static final String CLIENT_PARAMS_VERSION_1 = "Client parameter version";
    private static final byte[] CLIENT_PUBLIC_PARAMS = {1, 2};
    private static final byte[] CLIENT_PRIVATE_PARAMS = {3, 4};
    private static final Instant STATIC_INSTANT = Instant.now();
    private static final Instant EXPIRY_INSTANT_1 = STATIC_INSTANT;

    private final DBClientParameters.Builder mDefaultDBClientParametersBuilder =
            DBClientParameters.builder()
                    .setClientId(CLIENT_ID_1)
                    .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                    .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                    .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                    .setClientParamsVersion(CLIENT_PARAMS_VERSION_1);

    @Before
    public void setup() {
        mClientParametersDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class)
                        .build()
                        .clientParametersDao();
    }

    @After
    public void cleanup() {
        mClientParametersDao.deleteAllClientParameters();
    }

    @Test
    public void testGetActiveClientParameters_noActiveParamsPresents_returnsNull() {
        List<DBClientParameters> dbClientParametersList =
                mClientParametersDao.getActiveClientParameters(Instant.now());

        assertThat(dbClientParametersList).isEmpty();
    }

    @Test
    public void testGetActiveClientParameters_activeParamsPresent_shouldReturnParamsSuccessfully() {
        DBClientParameters dbClientParameters =
                mDefaultDBClientParametersBuilder
                        .setClientParametersExpiryInstant(STATIC_INSTANT.plusSeconds(1234))
                        .build();
        mClientParametersDao.insertClientParameters(dbClientParameters);

        List<DBClientParameters> dbClientParametersList =
                mClientParametersDao.getActiveClientParameters(STATIC_INSTANT);

        assertThat(dbClientParametersList).isNotEmpty();
        assertThat(dbClientParametersList.size()).isEqualTo(1);
        assertThat(dbClientParametersList.get(0).getClientId())
                .isEqualTo(dbClientParameters.getClientId());
    }

    @Test
    public void testGetActiveClientParameters_inactiveClientParamsPresent_shouldReturnNull() {
        DBClientParameters expiredClientParams =
                mDefaultDBClientParametersBuilder
                        .setClientParametersExpiryInstant(STATIC_INSTANT.minusSeconds(100))
                        .build();
        mClientParametersDao.insertClientParameters(expiredClientParams);

        List<DBClientParameters> dbClientParametersList =
                mClientParametersDao.getActiveClientParameters(STATIC_INSTANT);

        assertThat(dbClientParametersList).isEmpty();
    }

    @Test
    public void testRemoveExpiredParameters_onlyDeletesExpiredParameters() {
        long activeClientParametersId = 12;
        DBClientParameters expiredClientParams =
                mDefaultDBClientParametersBuilder
                        .setClientParametersExpiryInstant(STATIC_INSTANT.minusSeconds(100))
                        .build();
        DBClientParameters activeParams =
                mDefaultDBClientParametersBuilder
                        .setClientParametersId(activeClientParametersId)
                        .setClientParametersExpiryInstant(STATIC_INSTANT.plusSeconds(100))
                        .build();
        mClientParametersDao.insertClientParameters(expiredClientParams);
        mClientParametersDao.insertClientParameters(activeParams);

        mClientParametersDao.removeExpiredClientParameters(STATIC_INSTANT);

        List<DBClientParameters> dbClientParametersList =
                mClientParametersDao.getActiveClientParameters(STATIC_INSTANT);

        assertThat(dbClientParametersList.size()).isEqualTo(1);
        assertThat(dbClientParametersList.get(0).getClientParametersId())
                .isEqualTo(activeClientParametersId);
    }

    @Test
    public void testDeleteAllParameters_deletesAllParameters() {
        DBClientParameters expiredClientParams =
                mDefaultDBClientParametersBuilder
                        .setClientParametersExpiryInstant(STATIC_INSTANT.minusSeconds(100))
                        .build();
        DBClientParameters activeParams =
                mDefaultDBClientParametersBuilder
                        .setClientParametersExpiryInstant(STATIC_INSTANT.minusSeconds(101))
                        .build();
        mClientParametersDao.insertClientParameters(expiredClientParams);
        mClientParametersDao.insertClientParameters(activeParams);

        mClientParametersDao.deleteAllClientParameters();

        List<DBClientParameters> dbClientParametersList =
                mClientParametersDao.getActiveClientParameters(STATIC_INSTANT);
        assertThat(dbClientParametersList).isEmpty();
    }
}
