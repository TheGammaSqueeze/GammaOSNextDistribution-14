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

package com.android.adservices.service.measurement.attribution;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.AbstractDbIntegrationTest;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DbState;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * Integration tests for {@link AttributionJobHandler}
 */
@RunWith(Parameterized.class)
public class AttributionJobHandlerIntegrationTest extends AbstractDbIntegrationTest {

    private final AdServicesLogger mLogger;
    private final AdServicesErrorLogger mErrorLogger;

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open("attribution_service_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(
                inputStream, /*prepareAdditionalData=*/ null);
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AttributionJobHandlerIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            String name) {
        super(input, output, flagsMap);
        mLogger = Mockito.mock(AdServicesLogger.class);
        mErrorLogger = Mockito.mock(AdServicesErrorLogger.class);
    }

    @Override
    public void runActionToTest() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        Assert.assertEquals(
                "Attribution failed.",
                AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
                (new AttributionJobHandler(
                                datastoreManager,
                                FlagsFactory.getFlags(),
                                new DebugReportApi(sContext, FlagsFactory.getFlags()),
                                new EventReportWindowCalcDelegate(FlagsFactory.getFlags()),
                                new SourceNoiseHandler(FlagsFactory.getFlags()),
                                mLogger,
                                new XnaSourceCreator(FlagsFactory.getFlags()))
                        .performPendingAttributions()));
    }
}
