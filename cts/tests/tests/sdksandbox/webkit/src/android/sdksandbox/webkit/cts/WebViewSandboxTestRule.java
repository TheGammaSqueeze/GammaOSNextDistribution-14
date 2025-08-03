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
package android.sdksandbox.webkit.cts;

import android.app.sdksandbox.testutils.testscenario.SdkSandboxScenarioRule;
import android.os.Bundle;
import android.webkit.cts.SharedWebViewTest;
import android.webkit.cts.SharedWebViewTestEnvironment;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assume;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This rule is used to invoke webview tests inside a test sdk.
 * This rule is a wrapper for using the
 * {@link WebViewSandboxTestSdk}, for detailed implementation
 * details please refer to its parent class
 * {@link SdkSandboxScenarioRule}.
 */
public class WebViewSandboxTestRule extends SdkSandboxScenarioRule {

    public WebViewSandboxTestRule(String webViewTestClassName) {
        super(
                "com.android.cts.sdk.webviewsandboxtest",
                getSetupParams(webViewTestClassName),
                SharedWebViewTestEnvironment.createHostAppInvoker(
                        ApplicationProvider.getApplicationContext(), true),
                ENABLE_LIFE_CYCLE_ANNOTATIONS);
    }

    private static Bundle getSetupParams(String webViewTestClassName) {
        Bundle params = new Bundle();
        params.putString(SharedWebViewTest.WEB_VIEW_TEST_CLASS_NAME, webViewTestClassName);
        return params;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        // If WebView is not available, simply skip loading the SDK and then throw an assumption
        // failure for each test run attempt.
        // We can't throw the assumptions in the apply because WebViewSandboxTestRule can be used as
        // a class rule.
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return base;
        }

        return super.apply(base, description);
    }

    @Override
    public void assertSdkTestRunPasses(String testMethodName, Bundle params) throws Throwable {
        // This will prevent shared webview tests from running if a WebView provider does not exist.
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        super.assertSdkTestRunPasses(testMethodName, params);
    }
}
