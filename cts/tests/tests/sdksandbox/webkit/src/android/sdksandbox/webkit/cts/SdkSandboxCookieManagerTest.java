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
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdkSandboxCookieManagerTest {
    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.CookieManagerTest");

    @Test
    public void testGetInstance() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetInstance");
    }

    @Test
    public void testFlush() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testFlush");
    }

    @Test
    public void testAcceptCookie() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAcceptCookie");
    }

    @Test
    public void testSetCookie() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetCookie");
    }

    @Test
    public void testSetCookieNullCallback() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetCookieNullCallback");
    }

    @Test
    public void testSetCookieCallback() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetCookieCallback");
    }

    @Test
    public void testRemoveCookies() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRemoveCookies");
    }

    @Test
    public void testRemoveCookiesNullCallback() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRemoveCookiesNullCallback");
    }

    @Test
    public void testRemoveCookiesCallback() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRemoveCookiesCallback");
    }

    @Test
    public void testThirdPartyCookie() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testThirdPartyCookie");
    }

    @Test
    public void testSameSiteLaxByDefault() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSameSiteLaxByDefault");
    }

    @Test
    public void testSameSiteNoneRequiresSecure() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSameSiteNoneRequiresSecure");
    }

    @Test
    public void testSchemefulSameSite() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSchemefulSameSite");
    }

    @Test
    public void testb3167208() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testb3167208");
    }
}
