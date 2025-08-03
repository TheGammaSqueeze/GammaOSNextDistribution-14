/*
 * Copyright 2019 The Android Open Source Project
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
import static org.junit.Assert.assertSame;

import android.platform.test.annotations.AppModeFull;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewRenderProcess;
import android.webkit.WebViewRenderProcessClient;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewRenderProcessClientTest extends SharedWebViewTest {
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
                                    .setWebView(webView);
                        });

        return builder.build();
    }

    private static class JSBlocker {
        // A CoundDownLatch is used here, instead of a Future, because that makes it
        // easier to support requiring variable numbers of releaseBlock() calls
        // to unblock.
        private CountDownLatch mLatch;
        private SettableFuture<Void> mIsBlocked;

        JSBlocker(int requiredReleaseCount) {
            mLatch = new CountDownLatch(requiredReleaseCount);
            mIsBlocked = SettableFuture.create();
        }

        JSBlocker() {
            this(1);
        }

        public void releaseBlock() {
            mLatch.countDown();
        }

        @JavascriptInterface
        public void block() throws Exception {
            // This blocks indefinitely (until signalled) on a background thread.
            // The actual test timeout is not determined by this wait, but by other
            // code waiting for the onRenderProcessUnresponsive() call.
            mIsBlocked.set(null);
            mLatch.await();
        }

        public void waitForBlocked() {
            WebkitUtils.waitForFuture(mIsBlocked);
        }
    }

    private void blockRenderProcess(final JSBlocker blocker) {
        WebkitUtils.onMainThreadSync(() -> {
            WebView webView = mOnUiThread.getWebView();
            webView.evaluateJavascript("blocker.block();", null);
        });
        // Wait on the test instrumentation thread not the main thread. Blocking the main thread
        // may block other async calls such as initializing the GPU service channel that happens on
        // the UI thread and has to finish before the renderer can execute any javascript,
        // see https://crbug.com/1269552.
        blocker.waitForBlocked();
        WebkitUtils.onMainThreadSync(() -> {
            WebView webView = mOnUiThread.getWebView();
            // Sending an input event that does not get acknowledged will cause
            // the unresponsive renderer event to fire.
            webView.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        });
    }

    private void addJsBlockerInterface(final JSBlocker blocker) {
        WebkitUtils.onMainThreadSync(new Runnable() {
            @Override
            public void run() {
                WebView webView = mOnUiThread.getWebView();
                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(blocker, "blocker");
            }
        });
    }

    private void testWebViewRenderProcessClientOnExecutor(Executor executor) throws Throwable {
        final JSBlocker blocker = new JSBlocker();
        final SettableFuture<Void> rendererUnblocked = SettableFuture.create();

        WebViewRenderProcessClient client = new WebViewRenderProcessClient() {
            @Override
            public void onRenderProcessUnresponsive(WebView view, WebViewRenderProcess renderer) {
                // Let the renderer unblock.
                blocker.releaseBlock();
            }

            @Override
            public void onRenderProcessResponsive(WebView view, WebViewRenderProcess renderer) {
                // Notify that the renderer has been unblocked.
                rendererUnblocked.set(null);
            }
        };
        if (executor == null) {
            mOnUiThread.setWebViewRenderProcessClient(client);
        } else {
            mOnUiThread.setWebViewRenderProcessClient(executor, client);
        }

        addJsBlockerInterface(blocker);
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
        blockRenderProcess(blocker);
        WebkitUtils.waitForFuture(rendererUnblocked);
    }

    @Test
    public void testWebViewRenderProcessClientWithoutExecutor() throws Throwable {
        testWebViewRenderProcessClientOnExecutor(null);
    }

    @Test
    public void testWebViewRenderProcessClientWithExecutor() throws Throwable {
        final AtomicInteger executorCount = new AtomicInteger();
        testWebViewRenderProcessClientOnExecutor(new Executor() {
            @Override
            public void execute(Runnable r) {
                executorCount.incrementAndGet();
                r.run();
            }
        });
        assertEquals(2, executorCount.get());
    }

    @Test
    public void testSetWebViewRenderProcessClient() throws Throwable {
        assertNull("Initially the renderer client should be null",
                mOnUiThread.getWebViewRenderProcessClient());

        final WebViewRenderProcessClient webViewRenderProcessClient = new WebViewRenderProcessClient() {
            @Override
            public void onRenderProcessUnresponsive(WebView view, WebViewRenderProcess renderer) {}

            @Override
            public void onRenderProcessResponsive(WebView view, WebViewRenderProcess renderer) {}
        };
        mOnUiThread.setWebViewRenderProcessClient(webViewRenderProcessClient);

        assertSame(
                "After the renderer client is set, getting it should return the same object",
                webViewRenderProcessClient, mOnUiThread.getWebViewRenderProcessClient());
    }
}
