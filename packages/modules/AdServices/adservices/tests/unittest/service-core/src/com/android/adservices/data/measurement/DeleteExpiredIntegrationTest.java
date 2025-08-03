/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.measurement;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * Tests for {@link MeasurementDao} app deletion that affect the database.
 */
@RunWith(Parameterized.class)
public class DeleteExpiredIntegrationTest extends AbstractDbIntegrationTest {

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open(
                "measurement_delete_expired_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, /*prepareAdditionalData=*/ null);
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public DeleteExpiredIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            String name) {
        super(input, output, flagsMap);
    }

    /** Runs the action we want to test. */
    public void runActionToTest() {
        long earliestValidInsertion =
                System.currentTimeMillis() - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
        int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;

        AdServicesErrorLogger errorLogger = Mockito.mock(AdServicesErrorLogger.class);
        new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), errorLogger)
                .runInTransaction(
                        dao -> dao.deleteExpiredRecords(earliestValidInsertion, retryLimit));
    }
}
