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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.util.Pair;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TriggerSpecsTest {

    private static final long BASE_TIME = System.currentTimeMillis();
    private static final String PRIVACY_PARAMETERS_JSON = "{\"flip_probability\" :0.0024}";

    @Test
    public void equals_constructorThreeParameters_returnsTrue() throws JSONException {
        // Assertion
        assertEquals(
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, null),
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, null));
    }

    @Test
    public void equals_constructorThreeParameters_maxBucketIncrementsDifferent_returnsFalse()
            throws JSONException {
        // Assertion
        assertNotEquals(
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, null),
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 4, null));
    }

    @Test
    public void equals_constructorThreeParameters_triggerSpecContentDifferent_returnsFalse()
            throws JSONException {
        assertNotEquals(
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecValueCountJsonTwoTriggerSpecs(), 3, null),
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, null));
    }

    @Test
    public void constructorThreeParameters_completeExpectation_success() throws JSONException {
        TriggerSpecs testObject = new TriggerSpecs(
                SourceFixture.getTriggerSpecValueCountJsonTwoTriggerSpecs(), 3, null);

        // Assertion
        assertEquals(3, testObject.getMaxReports());
        assertEquals(2, testObject.getTriggerSpecs().length);
        assertEquals(
                new ArrayList<>(
                        Arrays.asList(
                                new UnsignedLong(1L), new UnsignedLong(2L), new UnsignedLong(3L))),
                testObject.getTriggerSpecs()[0].getTriggerData());
        assertEquals(
                new ArrayList<>(
                        Arrays.asList(
                                new UnsignedLong(4L),
                                new UnsignedLong(5L),
                                new UnsignedLong(6L),
                                new UnsignedLong(7L))),
                testObject.getTriggerSpecs()[1].getTriggerData());
        assertEquals(
                new ArrayList<>(
                        Arrays.asList(
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))),
                testObject.getTriggerSpecs()[0].getEventReportWindowsEnd());
        assertEquals(
                new ArrayList<>(Collections.singletonList(TimeUnit.DAYS.toMillis(3))),
                testObject.getTriggerSpecs()[1].getEventReportWindowsEnd());
    }

    @Test
    public void equals_fourParamConstructor_returnsTrue() throws JSONException {
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", BASE_TIME);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord1.put("dedup_key", new UnsignedLong(34567L).toString());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", BASE_TIME + 100);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).toString());
        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setAttributedTriggers(null)
                        .build();
        source.buildAttributedTriggers();
        // Assertion
        assertEquals(
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecCountEncodedJsonValidBaseline(),
                        "3",
                        source,
                        PRIVACY_PARAMETERS_JSON),
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecCountEncodedJsonValidBaseline(),
                        "3",
                        source,
                        PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void equals_fourParamConstructorFromRawJSON_returnsTrue() throws JSONException {
        String triggerSpecsString = SourceFixture.getTriggerSpecCountEncodedJsonValidBaseline();
        // Assertion
        assertEquals(
                new TriggerSpecs(triggerSpecsString, "3", null, PRIVACY_PARAMETERS_JSON),
                new TriggerSpecs(triggerSpecsString, "3", null, PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void equals_threeParamConstructor_returnsTrue() throws JSONException {
        TriggerSpec[] triggerSpecsArray = SourceFixture.getTriggerSpecArrayCountValidBaseline();
        // Assertion
        assertEquals(
                new TriggerSpecs(triggerSpecsArray, 3, null),
                new TriggerSpecs(triggerSpecsArray, 3, null));
    }

    @Test
    public void equals_fourParamConstructor_differentAttributions_returnsFalse()
            throws JSONException {
        JSONArray existingAttributes1 = new JSONArray();
        JSONArray existingAttributes2 = new JSONArray();

        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", BASE_TIME);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord1.put("dedup_key", new UnsignedLong(34567L).toString());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", BASE_TIME + 100);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).toString());
        existingAttributes1.put(triggerRecord1);
        existingAttributes2.put(triggerRecord2);

        Source source1 =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes1.toString())
                        .setAttributedTriggers(null)
                        .build();
        source1.buildAttributedTriggers();
        Source source2 =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes2.toString())
                        .setAttributedTriggers(null)
                        .build();
        source2.buildAttributedTriggers();

        // Assertion
        assertNotEquals(
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecCountEncodedJsonValidBaseline(),
                        "3",
                        source1,
                        PRIVACY_PARAMETERS_JSON),
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecCountEncodedJsonValidBaseline(),
                        "3",
                        source2,
                        PRIVACY_PARAMETERS_JSON));
    }

    @Test
    public void encodeToJson_equal() throws JSONException {
        TriggerSpecs testObject1 = new TriggerSpecs(
                SourceFixture.getTriggerSpecValueSumEncodedJsonValidBaseline(),
                "3",
                null,
                PRIVACY_PARAMETERS_JSON);

        String encodedJSON = testObject1.encodeToJson();

        TriggerSpecs testObject2 = new TriggerSpecs(
                encodedJSON, "3", null, PRIVACY_PARAMETERS_JSON);
        // Assertion
        assertEquals(testObject1, testObject2);
    }

    @Test
    public void getPrivacyParamsForComputation_equal() throws JSONException {
        Source source = SourceFixture.getMinimalValidSourceBuilder().build();
        TriggerSpecs testObject = new TriggerSpecs(
                SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, source);
        // Oblige building privacy parameters for the trigger specs
        testObject.getInformationGain(source, FlagsFactory.getFlagsForTest());
        // Assertion
        assertEquals(3, testObject.getPrivacyParamsForComputation()[0][0]);
        assertArrayEquals(new int[] {3, 3, 3}, testObject.getPrivacyParamsForComputation()[1]);
        assertArrayEquals(new int[] {4, 4, 4}, testObject.getPrivacyParamsForComputation()[2]);
    }

    @Test
    public void getPrivacyParamsForComputationV2_equal() throws JSONException {
        TriggerSpecs testObject = SourceFixture.getValidTriggerSpecsCountBased();
        // Assertion
        assertEquals(3, testObject.getPrivacyParamsForComputation()[0][0]);
        assertArrayEquals(new int[] {2, 2}, testObject.getPrivacyParamsForComputation()[1]);
        assertArrayEquals(new int[] {2, 2}, testObject.getPrivacyParamsForComputation()[2]);
    }

    @Test
    public void getTriggerDataFromIndex_equal() throws JSONException {
        TriggerSpecs triggerSpecs = new TriggerSpecs(
                SourceFixture.getTriggerSpecValueCountJsonTwoTriggerSpecs(), 3, null);
        // Assertion
        assertEquals(new UnsignedLong(1L), triggerSpecs.getTriggerDataFromIndex(0));
        assertEquals(new UnsignedLong(3L), triggerSpecs.getTriggerDataFromIndex(2));
        assertEquals(new UnsignedLong(5L), triggerSpecs.getTriggerDataFromIndex(4));
        assertEquals(new UnsignedLong(7L), triggerSpecs.getTriggerDataFromIndex(6));
    }

    @Test
    public void getSummaryBucketFromIndex_baseline_equal() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [2, 4, 6, 8, 10]}]";
        TriggerSpecs testTriggerSpecs = new TriggerSpecs(
                TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString), 5, null);

        // Assertion
        List<Long> summaryBucket = testTriggerSpecs.getSummaryBucketsForTriggerData(
                new UnsignedLong(1L));
        assertEquals(
                new Pair<>(2L, 3L), TriggerSpecs.getSummaryBucketFromIndex(0, summaryBucket));
        assertEquals(
                new Pair<>(4L, 5L), TriggerSpecs.getSummaryBucketFromIndex(1, summaryBucket));
        assertEquals(
                new Pair<>(10L, TriggerSpecs.MAX_BUCKET_THRESHOLD),
                TriggerSpecs.getSummaryBucketFromIndex(4, summaryBucket));
    }

    @Test
    public void containsTriggerData_variousTriggerDataTypes_correctlyDetermines()
            throws JSONException {
        TriggerSpecs testTriggerSpecs = new TriggerSpecs(
                SourceFixture.getTriggerSpecValueCountJsonTwoTriggerSpecs(), 3, null);
        // Assertion
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(1L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(2L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(3L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(4L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(5L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(6L)));
        assertTrue(testTriggerSpecs.containsTriggerData(new UnsignedLong(7L)));
        assertFalse(testTriggerSpecs.containsTriggerData(new UnsignedLong(0L)));
        assertFalse(testTriggerSpecs.containsTriggerData(new UnsignedLong(8L)));
    }
}
