/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.webkit.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class WebViewDataDirTest {

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private static final String ALTERNATE_DIR_NAME = "test";
    private static final String COOKIE_URL = "https://www.webviewdatadirtest.com/";
    private static final String COOKIE_VALUE = "foo=main";
    private static final String SET_COOKIE_PARAMS = "; Max-Age=86400";

    private WebViewCtsActivity mActivity;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            mActivity = (WebViewCtsActivity) activity;
        });
    }

    static class TestDisableThenUseImpl extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            WebView.disableWebView();
            try {
                new WebView(ctx);
                fail("didn't throw IllegalStateException");
            } catch (IllegalStateException e) {}
        }
    }

    @Test
    public void testDisableThenUse() throws Throwable {
        try (TestProcessClient process = TestProcessClient.createProcessA(mActivity)) {
            process.run(TestDisableThenUseImpl.class);
        }
    }

    @Test
    public void testUseThenDisable() throws Throwable {
        assertNotNull(mActivity.getWebView());
        try {
            WebView.disableWebView();
            fail("didn't throw IllegalStateException");
        } catch (IllegalStateException e) {}
    }

    @Test
    public void testUseThenChangeDir() throws Throwable {
        assertNotNull(mActivity.getWebView());
        try {
            WebView.setDataDirectorySuffix(ALTERNATE_DIR_NAME);
            fail("didn't throw IllegalStateException");
        } catch (IllegalStateException e) {}
    }

    static class TestInvalidDirImpl extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            try {
                WebView.setDataDirectorySuffix("no/path/separators");
                fail("didn't throw IllegalArgumentException");
            } catch (IllegalArgumentException e) {}
        }
    }

    @Test
    public void testInvalidDir() throws Throwable {
        try (TestProcessClient process = TestProcessClient.createProcessA(mActivity)) {
            process.run(TestInvalidDirImpl.class);
        }
    }

    static class TestDefaultDirDisallowed extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            try {
                new WebView(ctx);
                fail("didn't throw RuntimeException");
            } catch (RuntimeException e) {}
        }
    }

    @Test
    public void testSameDirTwoProcesses() throws Throwable {
        assertNotNull(mActivity.getWebView());

        try (TestProcessClient processA = TestProcessClient.createProcessA(mActivity)) {
            processA.run(TestDefaultDirDisallowed.class);
        }
    }

    static class TestCookieInAlternateDir extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            WebView.setDataDirectorySuffix(ALTERNATE_DIR_NAME);
            CookieManager cm = CookieManager.getInstance();
            String cookie = cm.getCookie(COOKIE_URL);
            assertNull("cookie leaked to alternate cookie jar", cookie);
        }
    }

    @Test
    public void testCookieJarsSeparate() throws Throwable {
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie(COOKIE_URL, COOKIE_VALUE + SET_COOKIE_PARAMS);
        cm.flush();
        String cookie = cm.getCookie(COOKIE_URL);
        assertEquals("wrong cookie in default cookie jar", COOKIE_VALUE, cookie);

        try (TestProcessClient processA = TestProcessClient.createProcessA(mActivity)) {
            processA.run(TestCookieInAlternateDir.class);
        }
    }
}
