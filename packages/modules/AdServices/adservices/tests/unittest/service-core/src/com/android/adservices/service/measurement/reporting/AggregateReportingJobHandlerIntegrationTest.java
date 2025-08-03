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

package com.android.adservices.service.measurement.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.AbstractDbIntegrationTest;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DbState;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integration tests for {@link AggregateReportingJobHandler} */
@RunWith(Parameterized.class)
public class AggregateReportingJobHandlerIntegrationTest extends AbstractDbIntegrationTest {
    private final JSONObject mParam;
    private final AdServicesLogger mLogger;
    private final AdServicesErrorLogger mErrorLogger;

    @Parameterized.Parameters(name = "{4}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("aggregate_report_service_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, (testObj) -> ((JSONObject) testObj).getJSONObject("param"));
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AggregateReportingJobHandlerIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            JSONObject param,
            String name) {
        super(input, output, flagsMap);
        mParam = param;
        mLogger = Mockito.mock(AdServicesLogger.class);
        mErrorLogger = Mockito.mock(AdServicesErrorLogger.class);
    }

    public enum Action {
        SINGLE_REPORT,
        ALL_REPORTS
    }

    @Override
    public void runActionToTest() {
        final Integer returnCode = (Integer) get("responseCode");
        final String action = (String) get("action");
        final String registration_origin = (String) get("registration_origin");

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), captorNumberOfKeys.capture()))
                .thenAnswer(
                        invocation -> {
                            List<AggregateEncryptionKey> keys = new ArrayList<>();
                            for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                                keys.add(AggregateCryptoFixture.getKey());
                            }
                            return keys;
                        });
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        AggregateReportingJobHandler spyReportingService =
                Mockito.spy(
                        new AggregateReportingJobHandler(
                                datastoreManager,
                                mockKeyManager,
                                FlagsFactory.getFlagsForTest(),
                                mLogger,
                                sContext));
        try {
            Mockito.doReturn(returnCode)
                    .when(spyReportingService)
                    .makeHttpPostRequest(Mockito.eq(Uri.parse(registration_origin)), any());
        } catch (IOException e) {
            Assert.fail();
        }

        switch (Action.valueOf(action)) {
            case ALL_REPORTS:
                final long startValue = ((Number) Objects.requireNonNull(get("start"))).longValue();
                final long endValue = ((Number) Objects.requireNonNull(get("end"))).longValue();
                Assert.assertTrue(
                        "Aggregate report failed.",
                        spyReportingService.performScheduledPendingReportsInWindow(
                                startValue, endValue));
                break;
            case SINGLE_REPORT:
                final int result = ((Number) Objects.requireNonNull(get("result"))).intValue();
                final String id = (String) get("id");
                Assert.assertEquals(
                        "Aggregate report failed.",
                        result,
                        spyReportingService.performReport(
                                id, AggregateCryptoFixture.getKey(), new ReportingStatus()));
                break;
        }
    }

    private Object get(String name) {
        try {
            return mParam.has(name) ? mParam.get(name) : null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }
}
