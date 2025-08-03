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

package com.android.car.tool.apibuilder.tests;

import static com.google.common.truth.Truth.assertThat;

import com.android.car.tool.apibuilder.ParsedDataBuilder;
import com.android.car.tool.apibuilder.ParsedDataHelper;
import com.android.car.tool.data.ParsedData;

import org.junit.Test;

import java.util.List;

public final class ParsedDataHelperTest extends TestHelper {

    @Test
    public void testGetClassNamesOnly() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getClassNamesOnly(data);
        assertThat(result).containsExactly("android.car.user.Test1$UserLifecycleListener",
                "android.car.user.Test1$UserLifecycleEvent",
                "android.car.user.Test1",
                "android.car.user.Test1$UserLifecycleEvent$UserLifecycleListener2");
    }

    @Test
    public void testGetAddedInOrBeforeApisOnly() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getAddedInOrBeforeApisOnly(data);
        assertThat(result).containsExactly("android.car.user.Test1.FIELD_1",
                "android.car.user.Test1.FIELD_6",
                "android.car.user.Test1.UserLifecycleListener.onEvent",
                "android.car.user.Test1.UserLifecycleEvent.getEventType",
                "android.car.user.Test1.method_2",
                "android.car.user.Test1.method_3",
                "android.car.user.Test1.UserLifecycleEvent.UserLifecycleListener2.onEvent");
    }

    @Test
    public void testGetHiddenApisOnly() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getHiddenApisOnly(data);
        assertThat(result).containsExactly("android.car.user Test1 String FIELD_1",
                "android.car.user Test1 void method_2()");
    }

    @Test
    public void testGetHiddenApisWithHiddenConstructor() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getHiddenApisWithHiddenConstructor(data);
        assertThat(result).containsExactly("android.car.user Test1 String FIELD_1",
                "android.car.user Test1 void method_2()",
                "android.car.user Test1.UserLifecycleEvent UserLifecycleEvent UserLifecycleEvent"
                + "(int eventType, int from, int to)",
                "android.car.user Test1 Test1 Test1(Car car, IBinder service)");
    }

    @Test
    public void testGetAllApis() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getAllApis(data);
        assertThat(result).containsExactly("android.car.user Test1 String FIELD_1",
                "android.car.user Test1 int FIELD_2",
                "android.car.user Test1 int FIELD_3",
                "android.car.user Test1 int FIELD_4",
                "android.car.user Test1 int FIELD_5",
                "android.car.user Test1 int FIELD_6",
                "android.car.user Test1 void method_1(UserStopRequest request, Executor executor, "
                + "ResultCallback<UserStopResponse> callback)",
                "android.car.user Test1 void method_2()",
                "android.car.user Test1 int method_3()",
                "android.car.user Test1 int method_4()",
                "android.car.user Test1.UserLifecycleListener void onEvent"
                + "(UserLifecycleEvent event)",
                "android.car.user Test1.UserLifecycleEvent int getEventType()",
                "android.car.user Test1.UserLifecycleEvent.UserLifecycleListener2 void onEvent"
                + "(UserLifecycleEvent event)");
    }

    @Test
    public void testGetAllApisWithConstructor() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getAllApisWithConstructor(data);
        assertThat(result).containsExactly("android.car.user Test1 String FIELD_1",
                "android.car.user Test1 int FIELD_2",
                "android.car.user Test1 int FIELD_3",
                "android.car.user Test1 int FIELD_4",
                "android.car.user Test1 int FIELD_5",
                "android.car.user Test1 int FIELD_6",
                "android.car.user Test1 void method_1(UserStopRequest request, Executor executor, "
                + "ResultCallback<UserStopResponse> callback)",
                "android.car.user Test1 void method_2()",
                "android.car.user Test1 int method_3()",
                "android.car.user Test1 int method_4()",
                "android.car.user Test1.UserLifecycleListener void onEvent"
                + "(UserLifecycleEvent event)",
                "android.car.user Test1.UserLifecycleEvent int getEventType()",
                "android.car.user Test1.UserLifecycleEvent.UserLifecycleListener2 void onEvent"
                + "(UserLifecycleEvent event)",
                "android.car.user Test1.UserLifecycleEvent UserLifecycleEvent UserLifecycleEvent"
                + "(int eventType, int from, int to)",
                "android.car.user Test1.UserLifecycleEvent UserLifecycleEvent UserLifecycleEvent"
                + "(int eventType, int to)",
                "android.car.user Test1 Test1 Test1(Car car, IBinder service)");
    }

    @Test
    public void testCheckAssertPlatformVersionAtLeast() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> results = ParsedDataHelper.checkAssertPlatformVersionAtLeast(data);
        assertThat(results.stream().anyMatch(result -> result.contains(
                "android.car.user Test1 void method_1(UserStopRequest request, Executor executor,"
                        + " ResultCallback<UserStopResponse> callback) | 89 |"))).isTrue();
    }

    @Test
    public void testGetApisWithVersion() throws Exception {
        ParsedData data = new ParsedData();
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);
        List<String> result = ParsedDataHelper.getApisWithVersion(data);
        assertThat(result).containsExactly("android.car.user Test1 String FIELD_1 | TIRAMISU_0",
                "android.car.user Test1 int FIELD_2 | ",
                "android.car.user Test1 int FIELD_3 | ",
                "android.car.user Test1 int FIELD_4 | TIRAMISU_0",
                "android.car.user Test1 int FIELD_5 | UPSIDE_DOWN_CAKE_0",
                "android.car.user Test1 int FIELD_6 | TIRAMISU_0",
                "android.car.user Test1 void method_1(UserStopRequest request, Executor executor, "
                        + "ResultCallback<UserStopResponse> callback) | UPSIDE_DOWN_CAKE_0",
                "android.car.user Test1 void method_2() | TIRAMISU_0",
                "android.car.user Test1 int method_3() | TIRAMISU_0",
                "android.car.user Test1 int method_4() | UPSIDE_DOWN_CAKE_0",
                "android.car.user Test1.UserLifecycleListener void onEvent"
                        + "(UserLifecycleEvent event) | TIRAMISU_0",
                "android.car.user Test1.UserLifecycleEvent int getEventType() | TIRAMISU_0",
                "android.car.user Test1.UserLifecycleEvent.UserLifecycleListener2 void onEvent"
                        + "(UserLifecycleEvent event) | TIRAMISU_0");
    }

}
