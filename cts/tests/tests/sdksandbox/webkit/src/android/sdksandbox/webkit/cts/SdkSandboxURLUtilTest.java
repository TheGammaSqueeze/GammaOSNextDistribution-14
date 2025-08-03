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
public class SdkSandboxURLUtilTest {
    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.URLUtilTest");

    @Test
    public void testIsAssetUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsAssetUrl");
    }

    @Test
    public void testIsAboutUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsAboutUrl");
    }

    @Test
    public void testIsContentUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsContentUrl");
    }

    @Test
    public void testIsCookielessProxyUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsCookielessProxyUrl");
    }

    @Test
    public void testIsDataUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsDataUrl");
    }

    @Test
    public void testIsFileUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsFileUrl");
    }

    @Test
    public void testIsHttpsUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsHttpsUrl");
    }

    @Test
    public void testIsHttpUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsHttpUrl");
    }

    @Test
    public void testIsJavaScriptUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsJavaScriptUrl");
    }

    @Test
    public void testIsNetworkUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsNetworkUrl");
    }

    @Test
    public void testIsValidUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIsValidUrl");
    }

    @Test
    public void testComposeSearchUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testComposeSearchUrl");
    }

    @Test
    public void testDecode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDecode");
    }

    @Test
    public void testGuessFileName() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGuessFileName");
    }

    @Test
    public void testGuessUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGuessUrl");
    }

    @Test
    public void testStripAnchor() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testStripAnchor");
    }
}
