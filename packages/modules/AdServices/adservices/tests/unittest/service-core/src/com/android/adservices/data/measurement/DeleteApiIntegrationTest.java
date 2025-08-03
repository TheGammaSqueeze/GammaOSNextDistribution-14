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

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.content.res.AssetManager;
import android.net.Uri;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link MeasurementDao} browser deletion that affect the database.
 */
@RunWith(Parameterized.class)
public class DeleteApiIntegrationTest extends AbstractDbIntegrationTest {
    private static final String TEST_DIR = "msmt_browser_deletion_tests";
    private final JSONObject mParam;
    private final AdServicesLogger mLogger;
    private final AdServicesErrorLogger mErrorLogger;

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    @SuppressWarnings("unused")
    public DeleteApiIntegrationTest(
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

    @Parameterized.Parameters(name = "{4}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        AssetManager assetManager = sContext.getAssets();
        List<InputStream> inputStreams = new ArrayList<>();
        String[] testFileList = assetManager.list(TEST_DIR);
        for (String testFile : testFileList) {
            inputStreams.add(assetManager.open(TEST_DIR + "/" + testFile));
        }
        return AbstractDbIntegrationTest.getTestCasesFromMultipleStreams(
                inputStreams, (testObj) -> testObj.getJSONObject("param"));
    }

    public void runActionToTest() {
        final String registrantValue = (String) get("registrant");
        Long startValue = (Long) get("start");
        Long endValue = (Long) get("end");
        final List<Uri> originList = getUriList("origins");
        final List<Uri> domainList = getUriList("domains");
        Integer matchBehavior = (Integer) get("matchBehavior");
        Integer deletionMode = (Integer) get("deletionMode");
        if (matchBehavior == null) {
            matchBehavior = DeletionRequest.MATCH_BEHAVIOR_DELETE;
        }
        if (deletionMode == null) {
            deletionMode = DeletionRequest.DELETION_MODE_ALL;
        }
        Instant startValueInstant =
                (startValue == null)
                        ? Instant.ofEpochMilli(Long.MIN_VALUE)
                        : Instant.ofEpochMilli(startValue);
        Instant endValueInstant =
                (endValue == null)
                        ? Instant.ofEpochMilli(Long.MAX_VALUE)
                        : Instant.ofEpochMilli(endValue);

        Integer finalMatchBehavior = matchBehavior;
        Integer finalDeletionMode = deletionMode;

        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        MeasurementDataDeleter measurementDataDeleter =
                new MeasurementDataDeleter(
                        datastoreManager, FlagsFactory.getFlags(), mLogger);
        measurementDataDeleter.delete(
                new DeletionParam.Builder(
                                originList,
                                domainList,
                                startValueInstant,
                                endValueInstant,
                                registrantValue,
                                /* sdkPackageName = */ "")
                        .setMatchBehavior(finalMatchBehavior)
                        .setDeletionMode(finalDeletionMode)
                        .build());
    }

    private Object get(String name) {
        try {
            return mParam.has(name) ? mParam.get(name) : null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }

    private List<Uri> getUriList(String name) {
        try {
            if (mParam.isNull(name)) {
                return Collections.emptyList();
            } else {
                JSONArray arr = mParam.getJSONArray(name);
                List<Uri> strList = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    strList.add(Uri.parse(arr.getString(i)));
                }
                return strList;
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }
}
