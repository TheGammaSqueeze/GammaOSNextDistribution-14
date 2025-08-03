/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.webkit;

import android.os.StrictMode;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.cts.CtsTestServer;
import android.webkit.cts.SslMode;
import android.webkit.cts.WebViewSyncLoader;

import com.android.compatibility.common.util.NullWebViewUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test class testing different aspects of WebView loading.
 * The test methods in this class should be run one-and-one from the host-side to ensure we
 * don't run the tests in the same process (since we can only load WebView into a process
 * once - after that we will reuse the same webview provider).
 * This works because the instrumentation used to run device-tests from the host-side terminates the
 * testing process after each run.
 * OBS! When adding a test here - remember to add a corresponding host-side test that will start the
 * device-test added here! See com.android.cts.webkit.WebViewHostSideStartupTest.
 */
public class WebViewDeviceSideStartupTest
        extends ActivityInstrumentationTestCase2<WebViewStartupCtsActivity> {

    private static final String TAG = WebViewDeviceSideStartupTest.class.getSimpleName();
    private static final long TEST_TIMEOUT_MS = 3000;

    private WebViewStartupCtsActivity mActivity;

    public WebViewDeviceSideStartupTest() {
        super("com.android.cts.webkit", WebViewStartupCtsActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testCookieManagerBlockingUiThread() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        CtsTestServer server = new CtsTestServer(mActivity, SslMode.INSECURE);
        final String url = server.getCookieUrl("death.html");

        Thread background = new Thread(new Runnable() {
            @Override
            public void run() {
                CookieSyncManager csm = CookieSyncManager.createInstance(mActivity);
                CookieManager cookieManager = CookieManager.getInstance();

                cookieManager.removeAllCookie();
                cookieManager.setAcceptCookie(true);
                cookieManager.setCookie(url, "count=41");
                Log.i(TAG, "done setting cookie before creating webview");
            }
        });

        background.start();
        background.join();

        // Now create WebView and test that setting the cookie beforehand really worked.
        mActivity.createAndAttachWebView();
        WebView webView = mActivity.getWebView();
        WebViewSyncLoader syncLoader = new WebViewSyncLoader(webView);
        syncLoader.loadUrlAndWaitForCompletion(url);
        assertEquals("1|count=41", webView.getTitle()); // outgoing cookie
        CookieManager cookieManager = CookieManager.getInstance();
        String cookie = cookieManager.getCookie(url);
        assertNotNull(cookie);
        final Pattern pat = Pattern.compile("count=(\\d+)");
        Matcher m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("42", m.group(1)); // value got incremented
        syncLoader.detach();
    }

    @UiThreadTest
    public void testStrictModeNotViolatedOnStartup() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        StrictMode.ThreadPolicy oldThreadPolicy = StrictMode.getThreadPolicy();
        StrictMode.VmPolicy oldVmPolicy = StrictMode.getVmPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .permitExplicitGc()
                .penaltyLog()
                .penaltyDeath()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                // TODO(b/151974299): Remove this after fixing existing context usage violation.
                .permitIncorrectContextUse()
                .penaltyLog()
                .penaltyDeath()
                .build());

        try {
            createWebViewAndNavigate();
            // Try to force Garbage Collection to catch any StrictMode violations triggered in
            // finalizers.
            for(int n = 0; n < 5; n++) {
                Runtime.getRuntime().gc();
                Thread.sleep(200);
            }
        } finally {
            StrictMode.setThreadPolicy(oldThreadPolicy);
            StrictMode.setVmPolicy(oldVmPolicy);
        }
    }

    private void createWebViewAndNavigate() {
        // Try to call some WebView APIs to ensure they don't cause strictmode violations
        mActivity.createAndAttachWebView();
        WebViewSyncLoader syncLoader = new WebViewSyncLoader(mActivity.getWebView());
        syncLoader.loadUrlAndWaitForCompletion("about:blank");
        syncLoader.loadUrlAndWaitForCompletion("");
        syncLoader.detach();
    }

}
