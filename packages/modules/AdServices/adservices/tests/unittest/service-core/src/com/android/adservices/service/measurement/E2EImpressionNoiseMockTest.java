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

package com.android.adservices.service.measurement;


import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterWebSource;
import com.android.adservices.service.measurement.actions.ReportObjects;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests, testing functionality when the test adds trigger data noise with 100% probability.
 */
@RunWith(Parameterized.class)
public class E2EImpressionNoiseMockTest extends E2EMockTest {
    private static final String TEST_DIR_NAME = "msmt_e2e_noise_tests";
    private final Map<String, Map<String, Integer>>
            mActualTriggerDataDistributions = new HashMap<>();
    private final Map<String, Map<String, Integer>>
            mExpectedTriggerDataDistributions = new HashMap<>();

    private interface PayloadKeys {
        String EVENT_ID = "source_event_id";
        String TRIGGER_DATA = "trigger_data";
    }

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME, E2ETest::preprocessTestJson);
    }

    public E2EImpressionNoiseMockTest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            ParamsProvider paramsProvider,
            String name,
            Map<String, String> phFlagsMap)
            throws RemoteException {
        super(actions, expectedOutput, paramsProvider, name, phFlagsMap);
        mAttributionHelper =
                TestObjectProvider.getAttributionJobHandler(
                        mDatastoreManager, mFlags, mErrorLogger);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        mDatastoreManager,
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mMockContentResolver);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        TestObjectProvider.Type.NOISY,
                        mDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        mDebugReportApi,
                        mFlags);
        getExpectedTriggerDataDistributions();
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws IOException, JSONException {
        super.processAction(sourceRegistration);
        if (sourceRegistration.mDebugReporting) {
            processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        }
    }

    @Override
    void processAction(RegisterWebSource sourceRegistration) throws IOException, JSONException {
        super.processAction(sourceRegistration);
        if (sourceRegistration.mDebugReporting) {
            processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        }
    }

    @Override
    void processActualEventReports(
            List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads)
            throws JSONException {
        // Each report-destination × event-ID should have the same count of trigger_data as in the
        // expected output, but the trigger_data value distribution should be different. The test
        // is currently supporting only one reporting job, which batches multiple reports at once,
        // although each is a separate network request.
        for (int i = 0; i < destinations.size(); i++) {
            String uri = getReportUrl(ReportType.EVENT, destinations.get(i).toString());
            JSONObject payload = payloads.get(i);
            String eventId = payload.getString(PayloadKeys.EVENT_ID);
            String triggerData = payload.getString(PayloadKeys.TRIGGER_DATA);
            mActualTriggerDataDistributions.computeIfAbsent(
                    getKey(uri, eventId), k -> new HashMap<String, Integer>()).merge(
                        triggerData, 1, Integer::sum);
        }
    }

    @Override
    void evaluateResults() {
        for (String key : mActualTriggerDataDistributions.keySet()) {
            if (!mExpectedTriggerDataDistributions.containsKey(key)) {
                Assert.assertTrue(getTestFailureMessage(
                        "Missing key in expected trigger data distributions"
                        + getDatastoreState()), false);
            }
        }
        boolean testPassed = false;
        for (String key1 : mExpectedTriggerDataDistributions.keySet()) {
            // No reports for a source is valid impression noise.
            if (!mActualTriggerDataDistributions.containsKey(key1)) {
                continue;
            }
            Map<String, Integer> expectedDistribution = mExpectedTriggerDataDistributions.get(key1);
            Map<String, Integer> actualDistribution = mActualTriggerDataDistributions.get(key1);
            for (String key2 : expectedDistribution.keySet()) {
                if (!actualDistribution.containsKey(key2) || !actualDistribution.get(key2).equals(
                        expectedDistribution.get(key2))) {
                    testPassed = true;
                    break;
                }
            }
        }
        Assert.assertTrue(
                getTestFailureMessage(
                        "Trigger data distributions were the same " + getDatastoreState()),
                testPassed);
    }

    private void getExpectedTriggerDataDistributions() {
        for (JSONObject reportObject : mExpectedOutput.mEventReportObjects) {
            String uri = reportObject.optString(TestFormatJsonMapping.REPORT_TO_KEY);
            JSONObject payload = reportObject.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
            String eventId = payload.optString(PayloadKeys.EVENT_ID);
            String triggerData = payload.optString(PayloadKeys.TRIGGER_DATA);
            mExpectedTriggerDataDistributions.computeIfAbsent(
                    getKey(uri, eventId), k -> new HashMap<String, Integer>()).merge(
                        triggerData, 1, Integer::sum);
        }
    }

    private String getTestFailureMessage(String message) {
        return String.format("%s:\n\nExpected distributions: %s\n\nActual distributions: %s",
                message, mExpectedTriggerDataDistributions, mActualTriggerDataDistributions);
    }

    private String getKey(String uri, String eventId) {
        return uri + "," + eventId;
    }
}
