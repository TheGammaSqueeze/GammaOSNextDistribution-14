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

package com.android.adservices.service.js;

import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArrayArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;
import static com.android.adservices.service.js.JSScriptArgument.stringMapToRecordArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

public class JSScriptArgumentTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testStringArg() {
        JSScriptArgument arg = stringArg("stringArg", "value");
        assertThat(arg.variableDeclaration()).isEqualTo("const stringArg = \"value\";");
    }

    @Test
    public void testIntArg() {
        JSScriptArgument arg = numericArg("numericArg", 1);
        assertThat(arg.variableDeclaration()).isEqualTo("const numericArg = 1;");
    }

    @Test
    public void testFloatArg() {
        JSScriptArgument arg = numericArg("numericArg", 1.001);
        assertThat(arg.variableDeclaration()).isEqualTo("const numericArg = 1.001;");
    }

    @Test
    public void testJsonArg() throws JSONException {
        final String jsonValue = "{\"intField\": 123, \"stringField\": \"value\"}";
        JSScriptArgument arg = jsonArg("jsonArg", jsonValue);
        assertThat(arg.variableDeclaration())
                .isEqualTo(String.format("const jsonArg = %s;", jsonValue));
    }

    @Test
    public void testJsonArrayArg() throws JSONException {
        final String jsonArrayValue =
                "[\n"
                        + "    {\n"
                        + "        \"name\": \"John\",\n"
                        + "        \"age\": 30,\n"
                        + "        \"city\": \"New York\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "        \"name\": \"Alice\",\n"
                        + "        \"age\": 25,\n"
                        + "        \"city\": \"Los Angeles\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "        \"name\": \"Bob\",\n"
                        + "        \"age\": 35,\n"
                        + "        \"city\": \"Chicago\"\n"
                        + "    }\n"
                        + "]";
        JSScriptArgument arg = jsonArrayArg("jsonArrayArg", jsonArrayValue);
        assertThat(arg.variableDeclaration())
                .isEqualTo(String.format("const jsonArrayArg = %s;", jsonArrayValue));
    }

    @Test
    public void testJsonArgFailsForInvalidJson() throws JSONException {
        // Missing closing }
        final String jsonValue = "{\"intField\": 123, \"stringField\": \"value\"";
        assertThrows(JSONException.class, () -> jsonArg("jsonArg", jsonValue));
    }

    @Test
    public void testArrayArg() throws JSONException {
        JSScriptArgument arg =
                JSScriptArgument.arrayArg(
                        "arrayArg", stringArg("ignored", "value1"), stringArg("ignored", "value2"));

        assertThat(arg.variableDeclaration())
                .isEqualTo("const arrayArg = [\n\"value1\"," + "\n\"value2\"\n];");
    }

    @Test
    public void testRecordArg() throws JSONException {
        JSScriptArgument arg =
                recordArg(
                        "recordArg",
                        numericArg("intField", 123),
                        arrayArg(
                                "arrayField",
                                stringArg("ignored", "value1"),
                                stringArg("ignored", "value2")));
        assertThat(arg.variableDeclaration())
                .isEqualTo(
                        "const recordArg = {\n\"intField\": 123,\n\"arrayField\": [\n\"value1\","
                                + "\n\"value2\"\n]\n};");
    }

    @Test
    public void testStringMapToRecordArg() throws JSONException {
        Map<String, String> signals =
                ImmutableMap.of(
                        "key1",
                        "{\"signals\":1}",
                        "key2",
                        "{\"signals\":2}",
                        "key3",
                        "{\"signals\":3}");
        JSScriptArgument arg = stringMapToRecordArg("stringMapToRecordArg", signals);
        assertThat(arg.variableDeclaration())
                .isEqualTo(
                        "const stringMapToRecordArg = {\n"
                            + "\"key1\": {\"signals\":1},\n"
                            + "\"key2\": {\"signals\":2},\n"
                            + "\"key3\": {\"signals\":3}\n"
                            + "};");
    }
}
