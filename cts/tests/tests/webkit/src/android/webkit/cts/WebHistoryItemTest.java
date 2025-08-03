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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.platform.test.annotations.AppModeFull;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebIconDatabase;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;

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

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebHistoryItemTest extends SharedWebViewTest {
    private SharedSdkWebServer mWebServer;
    private WebViewOnUiThread mOnUiThread;
    private WebIconDatabase mIconDb;
    private Context mContext;

    class WaitForIconClient extends WaitForProgressClient {
        private boolean mReceivedIcon;

        public WaitForIconClient(WebViewOnUiThread onUiThread) {
            super(onUiThread);
        }

        @Override
        public synchronized void onReceivedIcon(WebView webview, Bitmap icon) {
            mReceivedIcon = true;
        }

        public synchronized boolean receivedIcon() { return mReceivedIcon; }
    };

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
        mContext = getTestEnvironment().getContext();
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            mWebServer.shutdown();
        }
        if (mIconDb != null) {
            mIconDb.removeAllIcons();
            mIconDb.close();
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

    @Test
    public void testWebHistoryItem() throws Throwable {
        final WaitForIconClient waitForIconClient = new WaitForIconClient(mOnUiThread);
        mOnUiThread.setWebChromeClient(waitForIconClient);
        WebkitUtils.onMainThreadSync(() -> {
            // getInstance must run on the UI thread
            mIconDb = WebIconDatabase.getInstance();
            String dbPath = mContext.getFilesDir().toString() + "/icons";
            mIconDb.open(dbPath);
        });

        WebBackForwardList list = mOnUiThread.copyBackForwardList();
        assertEquals(0, list.getSize());

        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return waitForIconClient.receivedIcon();
            }
        }.run();

        list = mOnUiThread.copyBackForwardList();
        assertEquals(1, list.getSize());
        WebHistoryItem item = list.getCurrentItem();
        assertNotNull(item);
        assertEquals(url, item.getUrl());
        assertEquals(url, item.getOriginalUrl());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, item.getTitle());
        Bitmap icon = mOnUiThread.getFavicon();
        assertTrue(icon.sameAs(item.getFavicon()));

        url = mWebServer.getAssetUrl(TestHtmlConstants.BR_TAG_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        list = mOnUiThread.copyBackForwardList();
        assertEquals(2, list.getSize());
        item = list.getCurrentItem();
        assertNotNull(item);
        assertEquals(TestHtmlConstants.BR_TAG_TITLE, item.getTitle());
    }
}
