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

import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class SdkSandboxPostMessageTest {
    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.PostMessageTest");

    @Test
    public void testSimpleMessageToMainFrame() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSimpleMessageToMainFrame");
    }

    @Test
    public void testWildcardOriginMatchesAnything() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testWildcardOriginMatchesAnything");
    }

    @Test
    public void testEmptyStringOriginMatchesAnything() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testEmptyStringOriginMatchesAnything");
    }

    @Test
    public void testMultipleMessagesToMainFrame() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testMultipleMessagesToMainFrame");
    }

    @Test
    public void testMessageChannel() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testMessageChannel");
    }

    @Test
    public void testClose() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testClose");
    }

    @Test
    public void testReceiveMessagePort() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testReceiveMessagePort");
    }

    @Test
    public void testWebMessageHandler() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testWebMessageHandler");
    }

    @Test
    public void testWebMessageDefaultHandler() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testWebMessageDefaultHandler");
    }
}
