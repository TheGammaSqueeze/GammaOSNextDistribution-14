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

package com.android.adservices;

import android.adservices.http.MockWebServerRule;

import androidx.test.core.app.ApplicationProvider;

/** Utility class for tests needing to mock web server calls */
public class MockWebServerRuleFactory {
    /**
     * @return A mock {@link MockWebServerRule} initialized to use HTTPS.
     */
    public static MockWebServerRule createForHttps() {
        return MockWebServerRule.forHttps(
                ApplicationProvider.getApplicationContext(),
                "adservices_test_server.p12",
                "adservices_test");
    }

    /**
     * @return A mock {@link MockWebServerRule} initialized to use HTTP cleartext.
     */
    public static MockWebServerRule createForHttp() {
        return MockWebServerRule.forHttp();
    }
}
