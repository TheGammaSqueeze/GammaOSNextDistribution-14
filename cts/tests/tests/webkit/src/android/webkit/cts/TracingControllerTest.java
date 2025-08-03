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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.webkit.TracingConfig;
import android.webkit.TracingController;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TracingControllerTest {

    public static class TracingReceiver extends OutputStream {
        private int mChunkCount;
        private boolean mComplete;
        private ByteArrayOutputStream outputStream;

        public TracingReceiver() {
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public void write(byte[] chunk) {
            validateThread();
            mChunkCount++;
            try {
                outputStream.write(chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            validateThread();
            mComplete = true;
        }

        @Override
        public void flush() {
            fail("flush should not be called");
        }

        @Override
        public void write(int b) {
            fail("write(int) should not be called");
        }

        @Override
        public void write(byte[] b, int off, int len) {
            fail("write(byte[], int, int) should not be called");
        }

        private void validateThread() {
            assertTrue("Callbacks should be called on the correct (executor) thread",
                    Thread.currentThread().getName().startsWith(EXECUTOR_THREAD_PREFIX));
        }

        int getNbChunks() { return mChunkCount; }
        boolean getComplete() { return mComplete; }

        Callable<Boolean> getCompleteCallable() {
            return new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return getComplete();
                }
            };
        }

        ByteArrayOutputStream getOutputStream() { return outputStream; }
    }

    private static final int POLLING_TIMEOUT = 60 * 1000;
    private static final int EXECUTOR_TIMEOUT = 10; // timeout of executor shutdown in seconds
    private static final String EXECUTOR_THREAD_PREFIX = "TracingExecutorThread";
    private WebViewOnUiThread mOnUiThread;
    private ExecutorService mSingleThreadExecutor;

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            WebViewCtsActivity webViewCtsActivity = (WebViewCtsActivity) activity;
            WebView webview = webViewCtsActivity.getWebView();
            if (webview != null) {
                mOnUiThread = new WebViewOnUiThread(webview);
            }
        });
        mSingleThreadExecutor = Executors.newSingleThreadExecutor(getCustomThreadFactory());
    }

    @After
    public void tearDown() throws Exception {
        // make sure to stop everything and clean up
        if (NullWebViewUtils.isWebViewAvailable()) {
            ensureTracingStopped();
        }

        if (mSingleThreadExecutor != null) {
            mSingleThreadExecutor.shutdown();
            if (!mSingleThreadExecutor.awaitTermination(EXECUTOR_TIMEOUT, TimeUnit.SECONDS)) {
                fail("Failed to shutdown executor");
            }
        }
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    private void ensureTracingStopped() throws Exception {
        TracingController.getInstance().stop(null, mSingleThreadExecutor);
        Callable<Boolean> tracingStopped = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !TracingController.getInstance().isTracing();
            }
        };
        PollingCheck.check("Tracing did not stop", POLLING_TIMEOUT, tracingStopped);
    }

    private ThreadFactory getCustomThreadFactory() {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(EXECUTOR_THREAD_PREFIX + "_" + threadCount.incrementAndGet());
                return thread;
            }
        };
    }

    // Test that callbacks are invoked and tracing data is returned on the correct thread
    // (via executor). Tracing start/stop and webview loading happens on the UI thread.
    @Test
    public void testTracingControllerCallbacksOnUI() throws Throwable {
        final TracingReceiver tracingReceiver = new TracingReceiver();

        WebkitUtils.onMainThreadSync(() -> {
            runTracingTestWithCallbacks(tracingReceiver, mSingleThreadExecutor);
        });
        PollingCheck.check("Tracing did not complete", POLLING_TIMEOUT, tracingReceiver.getCompleteCallable());
        assertThat(tracingReceiver.getNbChunks(), greaterThan(0));
        assertThat(tracingReceiver.getOutputStream().size(), greaterThan(0));
        // currently the output is json (as of April 2018), but this could change in the future
        // so we don't explicitly test the contents of output stream.
    }

    // Test that callbacks are invoked and tracing data is returned on the correct thread
    // (via executor). Tracing start/stop happens on the testing thread; webview loading
    // happens on the UI thread.
    @Test
    public void testTracingControllerCallbacks() throws Throwable {
        final TracingReceiver tracingReceiver = new TracingReceiver();
        runTracingTestWithCallbacks(tracingReceiver, mSingleThreadExecutor);
        PollingCheck.check("Tracing did not complete", POLLING_TIMEOUT, tracingReceiver.getCompleteCallable());
        assertThat(tracingReceiver.getNbChunks(), greaterThan(0));
        assertThat(tracingReceiver.getOutputStream().size(), greaterThan(0));
    }

    // Test that tracing stop has no effect if tracing has not been started.
    @Test
    public void testTracingStopFalseIfNotTracing() {
        TracingController tracingController = TracingController.getInstance();
        assertFalse(tracingController.stop(null, mSingleThreadExecutor));
        assertFalse(tracingController.isTracing());
    }

    // Test that tracing cannot be started if already tracing.
    @Test
    public void testTracingCannotStartIfAlreadyTracing() throws Exception {
        TracingController tracingController = TracingController.getInstance();
        TracingConfig config = new TracingConfig.Builder().build();

        tracingController.start(config);
        assertTrue(tracingController.isTracing());
        try {
            tracingController.start(config);
        } catch (IllegalStateException e) {
            // as expected
            return;
        }
        assertTrue(tracingController.stop(null, mSingleThreadExecutor));
        fail("Tracing start should throw an exception when attempting to start while already tracing");
    }

    // Test that tracing cannot be invoked with excluded categories.
    @Test
    public void testTracingInvalidCategoriesPatternExclusion() {
        TracingController tracingController = TracingController.getInstance();
        TracingConfig config = new TracingConfig.Builder()
                .addCategories("android_webview","-blink")
                .build();
        try {
            tracingController.start(config);
        } catch (IllegalArgumentException e) {
            // as expected;
            assertFalse("TracingController should not be tracing", tracingController.isTracing());
            return;
        }

        fail("Tracing start should throw an exception due to invalid category pattern");
    }

    // Test that tracing cannot be invoked with categories containing commas.
    @Test
    public void testTracingInvalidCategoriesPatternComma() {
        TracingController tracingController = TracingController.getInstance();
        TracingConfig config = new TracingConfig.Builder()
                .addCategories("android_webview, blink")
                .build();
        try {
            tracingController.start(config);
        } catch (IllegalArgumentException e) {
            // as expected;
            assertFalse("TracingController should not be tracing", tracingController.isTracing());
            return;
        }

        fail("Tracing start should throw an exception due to invalid category pattern");
    }

    // Test that tracing cannot start with a configuration that is null.
    @Test
    public void testTracingWithNullConfig() {
        TracingController tracingController = TracingController.getInstance();
        try {
            tracingController.start(null);
        } catch (IllegalArgumentException e) {
            // as expected
            assertFalse("TracingController should not be tracing", tracingController.isTracing());
            return;
        }
        fail("Tracing start should throw exception if TracingConfig is null");
    }

    // Generic helper function for running tracing.
    private void runTracingTestWithCallbacks(TracingReceiver tracingReceiver, Executor executor) {
        TracingController tracingController = TracingController.getInstance();
        assertNotNull(tracingController);

        TracingConfig config = new TracingConfig.Builder()
                .addCategories(TracingConfig.CATEGORIES_WEB_DEVELOPER)
                .setTracingMode(TracingConfig.RECORD_CONTINUOUSLY)
                .build();
        assertFalse(tracingController.isTracing());
        tracingController.start(config);
        assertTrue(tracingController.isTracing());

        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
        assertTrue(tracingController.stop(tracingReceiver, executor));
    }
}

