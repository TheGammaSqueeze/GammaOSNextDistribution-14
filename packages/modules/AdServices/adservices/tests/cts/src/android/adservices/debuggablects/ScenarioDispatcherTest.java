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

package android.adservices.debuggablects;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;

import androidx.test.filters.FlakyTest;

import com.google.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class ScenarioDispatcherTest {

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRule.forHttp();

    @Test
    public void testScenarioDispatcher_happyPath_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-001.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        makeSimpleGetRequest(new URL(mMockWebServerRule.getServerBaseAddress() + "/bidding"));

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/bidding"));
        server.shutdown();
    }

    @Test
    public void testScenarioDispatcher_withPrefix_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-001.json", "/hello");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        URL url = new URL(mMockWebServerRule.getServerBaseAddress() + "/hello/bidding");
        makeSimpleGetRequest(url);

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/bidding"));
        server.shutdown();
    }

    @Test
    public void testScenarioDispatcher_withVerifyCalled_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-002.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        String baseAddress = mMockWebServerRule.getServerBaseAddress();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));

        assertThat(dispatcher.getCalledPaths())
                .containsExactlyElementsIn(dispatcher.getVerifyCalledPaths());
        server.shutdown();
    }

    @Test
    @FlakyTest(bugId = 315327589)
    public void testScenarioDispatcher_withVerifyNotCalled_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-003.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        String baseAddress = mMockWebServerRule.getServerBaseAddress();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding")); // Call something else.

        assertThat(dispatcher.getCalledPaths())
                .doesNotContain(dispatcher.getVerifyNotCalledPaths());
        server.shutdown();
    }

    @Test
    public void testScenarioDispatcher_withTwoSecondDelay_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-004.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        String baseAddress = mMockWebServerRule.getServerBaseAddress();
        long startTime = System.currentTimeMillis();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding")); // Call something else.
        long endTime = System.currentTimeMillis();

        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
        assertThat(endTime - startTime).isAtLeast(2000);
        server.shutdown();
    }

    @Test
    public void testScenarioDispatcher_withMultiplePathSegments_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-005.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        makeSimpleGetRequest(new URL(mMockWebServerRule.getServerBaseAddress() + "/hello/world"));

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/hello/world"));
        server.shutdown();
    }

    @Test
    @FlakyTest(bugId = 315327589)
    public void testScenarioDispatcher_withDuplicatePathCalls_doesNotReturnEarly()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-006.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        String baseAddress = mMockWebServerRule.getServerBaseAddress();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));

        assertThat(dispatcher.getCalledPaths())
                .containsExactlyElementsIn(List.of("/bidding", "/scoring"));
        server.shutdown();
    }

    // Regression test for bug b/325677040.
    @Test
    public void testScenarioDispatcher_withComplexUrlStructure_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/scenario-test-007.json", "");
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);

        String baseAddress = mMockWebServerRule.getServerBaseAddress();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));
        makeSimpleGetRequest(new URL(baseAddress + "/seller/reportImpression"));
        makeSimpleGetRequest(
                new URL(
                        String.format(
                                "%s/seller/reportImpression?render_uri=%s/render/shirts/0?bid=5",
                                baseAddress, baseAddress)));

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(List.of("/seller/reportImpression"));
        server.shutdown();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String makeSimpleGetRequest(URL url) throws Exception {
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStreamReader isReader = new InputStreamReader(conn.getInputStream());
        try (BufferedReader reader = new BufferedReader(isReader)) {
            String line;
            do {
                line = reader.readLine();
                result.append(line);
            } while (line != null);
        }
        return result.toString();
    }
}
