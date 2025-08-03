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

import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.net.Uri;


import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;

import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SetFlagDisabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@RequiresSdkLevelAtLeastT
public final class SignalsCtsDebuggableTest extends ForegroundDebuggableCtsTest {
    private static final String POSTFIX = "signals";
    private static final String FIRST_POSTFIX = "signalsFirst";
    private static final String SECOND_POSTFIX = "signalsSecond";

    private String mServerBaseAddress;
    private MockWebServer mMockWebServer;

    private ProtectedSignalsClient mProtectedSignalsClient;

    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Rule(order = 12)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    sContext, "adservices_untrusted_test_server.p12", "adservices_test");

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(sContext)
                        .setExecutor(executor)
                        .build();
    }

    @After
    public void tearDown() throws IOException {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
    }

    @Test
    public void testUpdateSignals_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-default.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri firstUri = Uri.parse(mServerBaseAddress + FIRST_POSTFIX);
        Uri secondUri = Uri.parse(mServerBaseAddress + SECOND_POSTFIX);
        UpdateSignalsRequest firstRequest = new UpdateSignalsRequest.Builder(firstUri).build();
        UpdateSignalsRequest secondRequest = new UpdateSignalsRequest.Builder(secondUri).build();
        mProtectedSignalsClient.updateSignals(firstRequest).get();
        mProtectedSignalsClient.updateSignals(secondRequest).get();

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testUpdateSignals_badUri_failure() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-default.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri uri = Uri.parse("");
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.updateSignals(request).get());
        assertEquals("SecurityException", e.getCause().getClass().getSimpleName());
    }

    @Test
    public void testUpdateSignals_badJson_failure() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-bad-json.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri uri = Uri.parse(mServerBaseAddress + POSTFIX);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.updateSignals(request).get());
        assertTrue(e.getCause() instanceof IllegalArgumentException);
    }

    // TODO(b/299336069) Get rid of the repeated code here.
    private void setupDefaultMockWebServer(ScenarioDispatcher dispatcher) throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);
        mServerBaseAddress = getServerBaseAddress();
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s/", mMockWebServer.getHostName(), mMockWebServer.getPort());
    }
}
