/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NullWebViewUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class testing different aspects of WebView loading.
 *
 * <p>Each test method in this class has to run in a freshly created process to ensure we don't run
 * the tests in the same process (since we can only load WebView into a process once - after that we
 * will reuse the same webview provider).
 *
 * <p>Tests in this class are moved from {@link com.android.cts.webkit.WebViewHostSideStartupTest},
 * see http://b/72376996 for the migration of these tests.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewStartupTest {
    private static final String TEST_PROCESS_DATA_DIR_SUFFIX = "WebViewStartupTestDir";
    private static final long TEST_TIMEOUT_MS = 3000;

    private static void runCurrentWebViewPackageTest(Context ctx, boolean alreadyOnMainThread)
            throws Throwable {
        // Have to set data dir suffix because this runs in a new process, and WebView might
        // already be used in other processes.
        WebView.setDataDirectorySuffix(TEST_PROCESS_DATA_DIR_SUFFIX);

        PackageManager pm = ctx.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
            PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
            // Ensure that getCurrentWebViewPackage returns a package recognized by the package
            // manager.
            assertPackageEquals(pm.getPackageInfo(webViewPackage.packageName, 0), webViewPackage);

            // Create WebView on the app's main thread
            if (alreadyOnMainThread) {
                WebView webView = new WebView(ctx);
                webView.destroy();
            } else {
                WebkitUtils.onMainThreadSync(() -> {
                  WebView webView = new WebView(ctx);
                  webView.destroy();
                });
            }

            // Ensure we are still using the same WebView package.
            assertPackageEquals(webViewPackage, WebView.getCurrentWebViewPackage());
        } else {
            // if WebView isn't supported the API should return null.
            assertNull(WebView.getCurrentWebViewPackage());
        }
    }

    private static void assertPackageEquals(PackageInfo expected, PackageInfo actual) {
        if (expected == null)
            assertNull(actual);
        assertEquals(expected.packageName, actual.packageName);
        assertEquals(expected.versionCode, actual.versionCode);
        assertEquals(expected.versionName, actual.versionName);
        assertEquals(expected.lastUpdateTime, actual.lastUpdateTime);
    }

    static class TestGetCurrentWebViewPackageOnUiThread
            extends TestProcessClient.UiThreadTestRunnable {
        @Override
        protected void runOnUiThread(Context ctx) throws Throwable {
            runCurrentWebViewPackageTest(ctx, true /* alreadyOnMainThread */);
        }
    }

    @Test
    public void testGetCurrentWebViewPackageOnUiThread() throws Throwable {
        // runCurrentWebViewPackageTest handles the case where WebView is not supported on device,
        // so we don't need to check NullWebViewUtils.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestGetCurrentWebViewPackageOnUiThread.class);
        }
    }

    static class TestGetCurrentWebViewPackageOnBackgroundThread
            extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) throws Throwable {
            runCurrentWebViewPackageTest(ctx, false /* alreadyOnMainThread */);
        }
    }

    @Test
    public void testGetCurrentWebViewPackageOnBackgroundThread() throws Throwable {
        // runCurrentWebViewPackageTest handles the case where WebView is not supported on device,
        // so we don't need to check NullWebViewUtils.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestGetCurrentWebViewPackageOnBackgroundThread.class);
        }
    }

    static class TestGetWebViewLooperOnUiThread extends TestProcessClient.UiThreadTestRunnable {
        @Override
        protected void runOnUiThread(Context ctx) {
            // Have to set data dir suffix because this runs in a new process, and WebView might
            // already be used in other processes.
            WebView.setDataDirectorySuffix(TEST_PROCESS_DATA_DIR_SUFFIX);

            WebView webView = createAndCheckWebViewLooper(ctx);
            webView.destroy();
        }
    }

    @Test
    public void testGetWebViewLooperOnUiThread() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestGetWebViewLooperOnUiThread.class);
        }
    }

    static class TestGetWebViewLooperCreatedOnUiThreadFromInstrThread
            extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            // Have to set data dir suffix because this runs in a new process, and WebView might
            // already be used in other processes.
            WebView.setDataDirectorySuffix(TEST_PROCESS_DATA_DIR_SUFFIX);

            // Create the WebView on the UI thread and then ensure webview.getWebViewLooper()
            // returns the UI thread.
            WebView webView =
                    WebkitUtils.onMainThreadSync(() -> createAndCheckWebViewLooper(ctx));
            assertEquals(Looper.getMainLooper(), webView.getWebViewLooper());
            WebkitUtils.onMainThreadSync(() -> webView.destroy());
        }
    }

    /**
     * Ensure that a WebView created on the UI thread returns that thread as its creator thread.
     * This ensures WebView.getWebViewLooper() is not implemented as 'return Looper.myLooper();'.
     */
    @Test
    public void testGetWebViewLooperCreatedOnUiThreadFromInstrThread() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestGetWebViewLooperCreatedOnUiThreadFromInstrThread.class);
        }
    }

    static class TestGetWebViewLooperCreatedOnBackgroundThreadFromInstThread
            extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) throws InterruptedException {
            // Have to set data dir suffix because this runs in a new process, and WebView might
            // already be used in other processes.
            WebView.setDataDirectorySuffix(TEST_PROCESS_DATA_DIR_SUFFIX);

            // Use a HandlerThread, because such a thread owns a Looper.
            HandlerThread backgroundThread = new HandlerThread("WebViewLooperCtsHandlerThread");
            backgroundThread.start();
            Handler backgroundHandler = new Handler(backgroundThread.getLooper());

            final SettableFuture<WebView> webViewFuture = SettableFuture.create();
            backgroundHandler.post(() -> {
                try {
                    webViewFuture.set(createAndCheckWebViewLooper(ctx));
                } catch (RuntimeException e) {
                    webViewFuture.setException(e);
                }
            });
            final WebView webview = WebkitUtils.waitForFuture(webViewFuture);
            assertEquals(backgroundThread.getLooper(), webview.getWebViewLooper());
            backgroundHandler.post(() -> { webview.destroy(); });
        }
    }

    /**
     * Ensure that a WebView created on a background thread returns that thread as its creator
     * thread. This ensures WebView.getWebViewLooper() is not bound to the UI thread regardless of
     * the thread it is created on..
     */
    @Test
    public void testGetWebViewLooperCreatedOnBackgroundThreadFromInstThread()
            throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestGetWebViewLooperCreatedOnBackgroundThreadFromInstThread.class);
        }
    }

    private static WebView createAndCheckWebViewLooper(Context context) {
        // Ensure we are running this on a thread with a Looper - otherwise there's no point.
        assertNotNull(Looper.myLooper());
        WebView webview = new WebView(context);
        assertEquals(Looper.myLooper(), webview.getWebViewLooper());
        return webview;
    }
}
