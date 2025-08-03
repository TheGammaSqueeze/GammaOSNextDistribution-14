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

import android.net.Uri;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
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
public class AppDeletionIntegrationTest extends AbstractDbIntegrationTest {
    private final Uri mUri;
    private final AdServicesLogger mLogger;
    private final AdServicesErrorLogger mErrorLogger;

    @Parameterized.Parameters(name = "{4}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open(
                "measurement_app_uninstall_deletion_test.json");
        return AbstractDbIntegrationTest.getTestCasesFrom(inputStream, (testObj) ->
                Uri.parse(testObj.getString("uri")));
    }

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    public AppDeletionIntegrationTest(
            DbState input,
            DbState output,
            Map<String, String> flagsMap,
            Uri uri, String name) {
        super(input, output, flagsMap);
        mUri = uri;
        mLogger = Mockito.mock(AdServicesLogger.class);
        mErrorLogger = Mockito.mock(AdServicesErrorLogger.class);
    }

    public void runActionToTest() {
        new MeasurementDataDeleter(
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger),
                        FlagsFactory.getFlags(),
                        mLogger)
                .deleteAppUninstalledData(mUri);
    }
}
