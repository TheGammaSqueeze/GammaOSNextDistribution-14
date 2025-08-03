/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerWebSettings;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ServiceWorkerWebSettingsTest extends SharedWebViewTest {
    private ServiceWorkerWebSettings mSettings;
    private WebViewOnUiThread mOnUiThread;

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
        mSettings = ServiceWorkerController.getInstance().getServiceWorkerWebSettings();
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    @Override
    protected SharedWebViewTestEnvironment createTestEnvironment() {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        SharedWebViewTestEnvironment.Builder builder = new SharedWebViewTestEnvironment.Builder();

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        activity -> {
                            WebView webView = ((WebViewCtsActivity) activity).getWebView();
                            builder.setHostAppInvoker(
                                            SharedWebViewTestEnvironment.createHostAppInvoker(
                                                    activity))
                                    .setContext(activity)
                                    .setWebView(webView);
                        });

        return builder.build();
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerWebSettingsCompatTest#testCacheMode. Modifications to this test
     * should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testCacheMode() {
        int i = WebSettings.LOAD_DEFAULT;
        assertEquals(i, mSettings.getCacheMode());
        for (; i <= WebSettings.LOAD_CACHE_ONLY; i++) {
            mSettings.setCacheMode(i);
            assertEquals(i, mSettings.getCacheMode());
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerWebSettingsCompatTest#testAllowContentAccess. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testAllowContentAccess() {
        assertEquals(mSettings.getAllowContentAccess(), true);
        for (boolean b : new boolean[]{false, true}) {
            mSettings.setAllowContentAccess(b);
            assertEquals(b, mSettings.getAllowContentAccess());
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerWebSettingsCompatTest#testAllowFileAccess. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testAllowFileAccess() {
        assertEquals(mSettings.getAllowFileAccess(), true);
        for (boolean b : new boolean[]{false, true}) {
            mSettings.setAllowFileAccess(b);
            assertEquals(b, mSettings.getAllowFileAccess());
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerWebSettingsCompatTest#testBlockNetworkLoads. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testBlockNetworkLoads() {
        // Note: we cannot test this setter unless we provide the INTERNET permission, otherwise we
        // get a SecurityException when we pass 'false'.
        final boolean hasInternetPermission = true;

        assertEquals(mSettings.getBlockNetworkLoads(), !hasInternetPermission);
        for (boolean b : new boolean[]{false, true}) {
            mSettings.setBlockNetworkLoads(b);
            assertEquals(b, mSettings.getBlockNetworkLoads());
        }
    }
}
