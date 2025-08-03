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

package com.android.adservices.service.measurement.aggregation;

import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.AbstractDbIntegrationTest;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DbState;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/** Integration tests for {@link AggregateEncryptionKeyManager} */
@RunWith(Parameterized.class)
public class AggregateEncryptionKeyManagerIntegrationTest extends AbstractDbIntegrationTest {
    private static final int NUM_KEYS_REQUESTED = 5;
    private static final String AGGREGATION_COORDINATOR_ORIGIN_1 =
            WebUtil.validUrl("https://not-going-to-be-visited.test");
    private static final String AGGREGATION_COORDINATOR_ORIGIN_2 =
            WebUtil.validUrl("https://again-not-going-to-be-visited.test");

    private static final String MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_PATH = "test/path";

    @Mock Clock mClock;

    @Spy
    AggregateEncryptionKeyFetcher mFetcher =
            new AggregateEncryptionKeyFetcher(ApplicationProvider.getApplicationContext());

    @Mock HttpsURLConnection mUrlConnection;
    @Mock AdServicesErrorLogger mErrorLogger;

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("aggregate_encryption_key_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, /*prepareAdditionalData=*/null);
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AggregateEncryptionKeyManagerIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            String name) throws IOException {
        super(input, output, flagsMap);
        MockitoAnnotations.initMocks(this);
        when(mClock.millis()).thenReturn(AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        AggregateEncryptionKeyTestUtil.prepareMockAggregateEncryptionKeyFetcher(
                mFetcher, mUrlConnection, AggregateEncryptionKeyTestUtil.getDefaultResponseBody());
    }

    @Override
    public void runActionToTest() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        AggregateEncryptionKeyManager aggregateEncryptionKeyManager =
                new AggregateEncryptionKeyManager(
                        datastoreManager,
                        mFetcher,
                        mClock,
                        String.join(
                                ",",
                                List.of(
                                        AGGREGATION_COORDINATOR_ORIGIN_1,
                                        AGGREGATION_COORDINATOR_ORIGIN_2)),
                        MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_PATH);
        List<AggregateEncryptionKey> providedKeys =
                aggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        Uri.parse(AGGREGATION_COORDINATOR_ORIGIN_1), NUM_KEYS_REQUESTED);
        Assert.assertTrue(
                "aggregationEncryptionKeyManager.getAggregateEncryptionKeys returned "
                        + "unexpected results:"
                        + AggregateEncryptionKeyTestUtil.prettify(providedKeys),
                AggregateEncryptionKeyTestUtil.isSuperset(
                                mOutput.getAggregateEncryptionKeyList(), providedKeys)
                        && providedKeys.size() == NUM_KEYS_REQUESTED);
    }
}
