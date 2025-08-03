/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertNull;

import android.platform.test.annotations.AppModeFull;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebBackForwardListTest extends SharedWebViewTest {

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private WebViewOnUiThread mOnUiThread;

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
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

    @AppModeFull(reason = "Instant apps cannot bind sockets")
    @Test
    public void testGetCurrentItem() throws Exception {
        WebBackForwardList list = mOnUiThread.copyBackForwardList();

        assertNull(list.getCurrentItem());
        assertEquals(0, list.getSize());
        assertEquals(-1, list.getCurrentIndex());
        assertNull(list.getItemAtIndex(-1));
        assertNull(list.getItemAtIndex(2));

        SharedSdkWebServer server = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        try {
            String url1 = server.getAssetUrl(TestHtmlConstants.HTML_URL1);
            String url2 = server.getAssetUrl(TestHtmlConstants.HTML_URL2);
            String url3 = server.getAssetUrl(TestHtmlConstants.HTML_URL3);

            mOnUiThread.loadUrlAndWaitForCompletion(url1);
            checkBackForwardList(url1);

            mOnUiThread.loadUrlAndWaitForCompletion(url2);
            checkBackForwardList(url1, url2);

            mOnUiThread.loadUrlAndWaitForCompletion(url3);
            checkBackForwardList(url1, url2, url3);
        } finally {
            server.shutdown();
        }
    }

    private void checkBackForwardList(final String... url) {
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                if (mOnUiThread.getProgress() < 100) {
                    return false;
                }
                WebBackForwardList list = mOnUiThread.copyBackForwardList();
                if (list.getSize() != url.length) {
                    return false;
                }
                if (list.getCurrentIndex() != url.length - 1) {
                    return false;
                }
                for (int i = 0; i < url.length; i++) {
                    WebHistoryItem item = list.getItemAtIndex(i);
                    if (!url[i].equals(item.getUrl())) {
                        return false;
                    }
                }
                return true;
            }

        }.run();
    }

}
