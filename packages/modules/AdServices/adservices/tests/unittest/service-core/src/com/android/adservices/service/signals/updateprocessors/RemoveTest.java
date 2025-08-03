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
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBProtectedSignal;

import org.json.JSONArray;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RemoveTest {

    /*
     * I feel that hardcoding the names here is appropriate here since the JSON names are an
     * external contract and changing them should require test changes.
     */
    private static final String REMOVE = "remove";

    private Remove mRemove = new Remove();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testGetName() {
        assertEquals(REMOVE, mRemove.getName());
    }

    @Test
    public void testRemoveSingleNotPresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        UpdateOutput output = mRemove.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        assertTrue(output.getToAdd().isEmpty());
    }

    @Test
    public void testRemoveSinglePresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toRemove = createSignal(KEY_1, VALUE_1, ID_1, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toRemove)));

        UpdateOutput output = mRemove.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toRemove), output.getToRemove());
        assertTrue(output.getToAdd().isEmpty());
    }

    @Test
    public void testRemoveMultiplePresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toRemove = createSignal(KEY_1, VALUE_1, ID_1, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toRemove)));

        UpdateOutput output = mRemove.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toRemove), output.getToRemove());
        assertTrue(output.getToAdd().isEmpty());
    }
}
