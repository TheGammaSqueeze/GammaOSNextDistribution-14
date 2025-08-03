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
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.assertSignalsBuilderUnorderedListEquals;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBProtectedSignal;

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

public class PutTest {

    /*
     * I feel that hardcoding the names here is appropriate here since the JSON names are an
     * external contract and changing them should require test changes.
     */
    private static final String PUT = "put";

    private Put mPut = new Put();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testGetName() {
        assertEquals(PUT, mPut.getName());
    }

    @Test
    public void testPutSingle() throws Exception {
        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, BASE64_VALUE_1);

        UpdateOutput output = mPut.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testPutMultipleKeys() throws Exception {
        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, BASE64_VALUE_1);
        updatesJson.put(BASE64_KEY_2, BASE64_VALUE_2);

        UpdateOutput output = mPut.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(new HashSet<>(Arrays.asList(BB_KEY_1, BB_KEY_2)), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(
                        DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1),
                        DBProtectedSignal.builder().setKey(KEY_2).setValue(VALUE_2));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testOverwriteExisting() throws Exception {
        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, BASE64_VALUE_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toOverwrite =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toOverwrite)));

        UpdateOutput output = mPut.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toOverwrite), output.getToRemove());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }

    @Test
    public void testOverwriteMultipleExisting() throws Exception {

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, BASE64_VALUE_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toOverwrite1 =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        DBProtectedSignal toOverwrite2 =
                createSignal(KEY_1, VALUE_2, ID_2, NOW.minus(Duration.ofDays(1)));
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toOverwrite1, toOverwrite2)));

        UpdateOutput output = mPut.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertThat(Arrays.asList(toOverwrite1, toOverwrite2))
                .containsExactlyElementsIn(output.getToRemove());
        List<DBProtectedSignal.Builder> expected =
                Arrays.asList(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE_1));
        assertSignalsBuilderUnorderedListEquals(expected, output.getToAdd());
    }
}
