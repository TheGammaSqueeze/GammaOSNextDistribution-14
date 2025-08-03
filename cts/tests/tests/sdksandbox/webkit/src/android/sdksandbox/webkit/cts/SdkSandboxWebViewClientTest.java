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

package android.sdksandbox.webkit.cts;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdkSandboxWebViewClientTest {
    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.WebViewClientTest");

    @Test
    public void testShouldOverrideUrlLoadingDefault() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldOverrideUrlLoadingDefault");
    }

    @Test
    public void testShouldOverrideUrlLoading() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldOverrideUrlLoading");
    }

    @Test
    public void testShouldOverrideUrlLoadingOnCreateWindow() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldOverrideUrlLoadingOnCreateWindow");
    }

    @Test
    public void testLoadPage() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadPage");
    }

    @Test
    public void testOnReceivedLoginRequest() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedLoginRequest");
    }

    @Test
    public void testOnReceivedError() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedError");
    }

    @Test
    public void testOnReceivedErrorForSubresource() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedErrorForSubresource");
    }

    @Test
    public void testOnReceivedHttpError() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedHttpError");
    }

    @Test
    public void testOnFormResubmission() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnFormResubmission");
    }

    @Test
    public void testDoUpdateVisitedHistory() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDoUpdateVisitedHistory");
    }

    @Test
    public void testOnReceivedHttpAuthRequest() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedHttpAuthRequest");
    }

    @Test
    public void testShouldOverrideKeyEvent() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldOverrideKeyEvent");
    }

    @Test
    public void testOnUnhandledKeyEvent() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnUnhandledKeyEvent");
    }

    @Test
    public void testOnScaleChanged() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnScaleChanged");
    }

    @Test
    public void testShouldInterceptRequestParams() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldInterceptRequestParams");
    }

    @Test
    public void testShouldInterceptRequestResponse() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testShouldInterceptRequestResponse");
    }

    @Test
    public void testOnRenderProcessGoneDefault() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnRenderProcessGoneDefault");
    }

    @Test
    public void testOnRenderProcessGone() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnRenderProcessGone");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingHitBackToSafety() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingHitBackToSafety");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingHitProceed() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingHitProceed");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingMalwareCode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingMalwareCode");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingPhishingCode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingPhishingCode");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingUnwantedSoftwareCode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingUnwantedSoftwareCode");
    }

    // TODO(crbug/1245351): Remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void testOnSafeBrowsingBillingCode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnSafeBrowsingBillingCode");
    }

    @Test
    public void testOnPageCommitVisibleCalled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnPageCommitVisibleCalled");
    }
}
