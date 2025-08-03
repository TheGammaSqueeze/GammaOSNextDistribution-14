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

package com.android.adservices.service.signals;

import static com.android.adservices.service.signals.ProtectedSignalsArgumentUtil.INVALID_BASE64_SIGNAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtectedSignalsArgumentUtilTest {

    public static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    public static final String SIGNAL_FIELD_NAME = "signals";
    public static final Instant FIXED_NOW = CommonFixture.FIXED_NOW;
    private Map<String, List<ProtectedSignal>> mSignals;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mSignals = new HashMap<>();
    }

    @Test
    public void testJSArgument() throws JSONException {
        ProtectedSignal signal = generateSignal("signal1");
        mSignals.put(getBase64String("test_key"), List.of(signal));

        JSScriptArgument argument =
                ProtectedSignalsArgumentUtil.asScriptArgument(SIGNAL_FIELD_NAME, mSignals);

        assertEquals(SIGNAL_FIELD_NAME, argument.name());

        String expectedVariable =
                "const signals = [{\"746573745F6B6579\":"
                        + "[{\"val\":\"7369676E616C31\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}];";

        String actualVariable = argument.variableDeclaration();
        assertEquals(expectedVariable, actualVariable);
    }

    @Test
    public void testEmptySignals() {
        String expectedEmpty = "[]";
        assertEquals(expectedEmpty, ProtectedSignalsArgumentUtil.marshalToJson(mSignals));
    }

    @Test
    public void testSingleSignal() {
        ProtectedSignal signal = generateSignal("signal1");
        mSignals.put(getBase64String("test_key"), List.of(signal));

        String expectedJSON =
                "[{\"746573745F6B6579\":"
                        + "[{\"val\":\"7369676E616C31\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    @Test
    public void testHandleInvalidBase64Signal() {
        ProtectedSignal signal = generateSignal("signal1");
        mSignals.put("non_base64_string", List.of(signal));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            ProtectedSignalsArgumentUtil.marshalToJson(mSignals);
                        });
        assertEquals(INVALID_BASE64_SIGNAL, exception.getMessage());
    }

    @Test
    public void testMultipleSignals() {
        ProtectedSignal signalA1 = generateSignal("signalA1");
        ProtectedSignal signalA2 = generateSignal("signalA1");
        ProtectedSignal signalB1 = generateSignal("signalB1");

        mSignals.put(getBase64String("test_key_A"), List.of(signalA1, signalA2));
        mSignals.put(getBase64String("test_key_B"), List.of(signalB1));

        String expectedJSON =
                "[{\"746573745F6B65795F42\":"
                        + "[{\"val\":\"7369676E616C4231\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]},"
                        + "{\"746573745F6B65795F41\":"
                        + "[{\"val\":\"7369676E616C4131\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"},"
                        + "{\"val\":\"7369676E616C4131\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android"
                        + ".adservices.tests1\"}]}]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    @Test
    public void handleEmptyValue() {
        ProtectedSignal signal =
                ProtectedSignal.builder()
                        .setBase64EncodedValue(getBase64String(""))
                        .setCreationTime(FIXED_NOW)
                        .setPackageName(PACKAGE)
                        .build();
        mSignals.put(getBase64String("test_key"), List.of(signal));
        String expectedJSON =
                "[{\"746573745F6B6579\":"
                        + "[{\"val\":\"\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    private ProtectedSignal generateSignal(String value) {
        return ProtectedSignal.builder()
                .setBase64EncodedValue(getBase64String(value))
                .setCreationTime(FIXED_NOW)
                .setPackageName(PACKAGE)
                .build();
    }

    private boolean isValidJson(String jsonString) {
        try {
            JsonParser parser = new JsonParser();
            parser.parse(jsonString);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private String getBase64String(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes());
    }
}
