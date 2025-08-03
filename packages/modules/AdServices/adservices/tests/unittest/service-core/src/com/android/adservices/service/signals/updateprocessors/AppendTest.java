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

package com.android.adservices.service.signals.updateprocessors;

import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_2;
import static com.android.adservices.service.signals.SignalsFixture.ID_3;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.assertSignalsBuilderUnorderedListEquals;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBProtectedSignal;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppendTest {

    /*
     * I feel that hardcoding the names here is appropriate here since the JSON names are an
     * external contract and changing them should require test changes.
     */
    private static final String MAX_SIGNALS = "max_signals";
    private static final String VALUES = "values";
    private static final String APPEND = "append";

    private Append mAppend = new Append();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testGetName() {
        assertEquals(APPEND, mAppend.getName());
    }

    @Test
    public void testAppendSingle() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 1);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        UpdateOutput output = mAppend.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testAppendMultipleValues() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);
        valuesJson.put(BASE64_VALUE_2);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        UpdateOutput output = mAppend.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(
                        DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1),
                        DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_2));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testAppendMultipleKeys() throws Exception {
        JSONArray valuesJson1 = new JSONArray();
        valuesJson1.put(BASE64_VALUE_1);
        valuesJson1.put(BASE64_VALUE_2);

        JSONArray valuesJson2 = new JSONArray();
        valuesJson2.put(BASE64_VALUE_2);

        JSONObject appendJson1 = new JSONObject();
        appendJson1.put(VALUES, valuesJson1);
        appendJson1.put(MAX_SIGNALS, 2);

        JSONObject appendJson2 = new JSONObject();
        appendJson2.put(VALUES, valuesJson2);
        appendJson2.put(MAX_SIGNALS, 1);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson1);
        updatesJson.put(BASE64_KEY_2, appendJson2);

        UpdateOutput output = mAppend.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(new HashSet<>(Arrays.asList(BB_KEY_1, BB_KEY_2)), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(
                        DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1),
                        DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_2),
                        DBProtectedSignal.builder().setKey(KEY_2).setValue(VALUE_2));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testOverwriteExisting() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toOverwrite =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        DBProtectedSignal toKeep = createSignal(KEY_1, VALUE_2, ID_2, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toOverwrite, toKeep)));

        UpdateOutput output = mAppend.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toOverwrite), output.getToRemove());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testOverwriteMultipleExisting() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toOverwrite1 =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        DBProtectedSignal toOverwrite2 =
                createSignal(KEY_1, VALUE_1, ID_2, NOW.minus(Duration.ofDays(2)));
        DBProtectedSignal toKeep = createSignal(KEY_1, VALUE_2, ID_3, NOW);
        existingSignals.put(
                BB_KEY_1, new HashSet<>(Arrays.asList(toOverwrite1, toOverwrite2, toKeep)));

        UpdateOutput output = mAppend.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertThat(Arrays.asList(toOverwrite1, toOverwrite2))
                .containsExactlyElementsIn(output.getToRemove());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testAddToExisting() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 3);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal existing1 =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        DBProtectedSignal existing2 = createSignal(KEY_1, VALUE_2, ID_1, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(existing1, existing2)));

        UpdateOutput output = mAppend.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testBadMaxValueThrowsException() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);
        valuesJson.put(BASE64_VALUE_2);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 1);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        assertThrows(
                IllegalArgumentException.class,
                () -> mAppend.processUpdates(updatesJson, Collections.emptyMap()));
    }
}
