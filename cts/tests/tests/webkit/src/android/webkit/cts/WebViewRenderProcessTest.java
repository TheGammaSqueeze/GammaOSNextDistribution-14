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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewRenderProcess;

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

import java.util.concurrent.Future;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewRenderProcessTest extends SharedWebViewTest {
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

    private boolean terminateRenderProcessOnUiThread(
            final WebViewRenderProcess renderer) {
        return WebkitUtils.onMainThreadSync(() -> {
            return renderer.terminate();
        });
    }

    WebViewRenderProcess getRenderProcessOnUiThread(final WebView webView) {
        return WebkitUtils.onMainThreadSync(() -> {
            return webView.getWebViewRenderProcess();
        });
    }

    private Future<WebViewRenderProcess> startAndGetRenderProcess(
            final WebView webView) throws Throwable {
        final SettableFuture<WebViewRenderProcess> future = SettableFuture.create();

        WebkitUtils.onMainThread(() -> {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    WebViewRenderProcess result = webView.getWebViewRenderProcess();
                    future.set(result);
                }
            });
            webView.loadUrl("about:blank");
        });

        return future;
    }

    Future<Boolean> catchRenderProcessTermination(final WebView webView) {
        final SettableFuture<Boolean> future = SettableFuture.create();

        WebkitUtils.onMainThread(() -> {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(
                        WebView view,
                        RenderProcessGoneDetail detail) {
                    view.destroy();
                    future.set(true);
                    return true;
                }
            });
        });

        return future;
    }

    @Test
    public void testGetWebViewRenderProcess() throws Throwable {
        final WebView webView = mOnUiThread.getWebView();
        final WebViewRenderProcess preStartRenderProcess = getRenderProcessOnUiThread(webView);

        assertNotNull(
                "Should be possible to obtain a renderer handle before the renderer has started.",
                preStartRenderProcess);
        assertFalse(
                "Should not be able to terminate an unstarted renderer.",
                terminateRenderProcessOnUiThread(preStartRenderProcess));

        final WebViewRenderProcess renderer = WebkitUtils.waitForFuture(startAndGetRenderProcess(webView));
        assertSame(
                "The pre- and post-start renderer handles should be the same object.",
                renderer, preStartRenderProcess);

        assertSame(
                "When getWebViewRender is called a second time, it should return the same object.",
                renderer, WebkitUtils.waitForFuture(startAndGetRenderProcess(webView)));

        Future<Boolean> terminationFuture = catchRenderProcessTermination(webView);
        assertTrue(
                "A started renderer should be able to be terminated.",
                terminateRenderProcessOnUiThread(renderer));
        assertTrue(
                "Terminating a renderer should result in onRenderProcessGone being called.",
                WebkitUtils.waitForFuture(terminationFuture));

        assertFalse(
                "It should not be possible to terminate a renderer that has already terminated.",
                terminateRenderProcessOnUiThread(renderer));

        WebView webView2 = mOnUiThread.createWebView();
        try {
            assertNotSame(
                    "After a renderer restart, the new renderer handle object should be different.",
                    renderer, WebkitUtils.waitForFuture(startAndGetRenderProcess(webView2)));
        } finally {
            // Ensure that we clean up webView2. webView has been destroyed by the WebViewClient
            // installed by catchRenderProcessTermination
            WebkitUtils.onMainThreadSync(() -> webView2.destroy());
        }
    }
}
