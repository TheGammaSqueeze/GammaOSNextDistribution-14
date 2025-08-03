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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.os.Message;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebChromeClientTest extends SharedWebViewTest{
    private static final String JAVASCRIPT_UNLOAD = "javascript unload";
    private static final String LISTENER_ADDED = "listener added";
    private static final String TOUCH_RECEIVED = "touch received";

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private SharedSdkWebServer mWebServer;
    private WebIconDatabase mIconDb;
    private WebViewOnUiThread mOnUiThread;
    private boolean mBlockWindowCreationSync;
    private boolean mBlockWindowCreationAsync;

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
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
                                    .setWebView(webView)
                                    .setRootLayout(((WebViewCtsActivity) activity).getRootLayout());
                        });

        SharedWebViewTestEnvironment environment = builder.build();
        return environment;
    }


    @Test
    public void testOnProgressChanged() {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        assertFalse(webChromeClient.hadOnProgressChanged());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnProgressChanged();
            }
        }.run();
    }

    @Test
    public void testOnReceivedTitle() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        assertFalse(webChromeClient.hadOnReceivedTitle());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnReceivedTitle();
            }
        }.run();
        assertTrue(webChromeClient.hadOnReceivedTitle());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, webChromeClient.getPageTitle());
    }

    @Test
    public void testOnReceivedIcon() throws Throwable {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        WebkitUtils.onMainThreadSync(() -> {
            // getInstance must run on the UI thread
            mIconDb = WebIconDatabase.getInstance();
            String dbPath = getTestEnvironment().getContext().getFilesDir().toString() + "/icons";
            mIconDb.open(dbPath);
        });
        getTestEnvironment().waitForIdleSync();
        Thread.sleep(100); // Wait for open to be received on the icon db thread.

        assertFalse(webChromeClient.hadOnReceivedIcon());
        assertNull(mOnUiThread.getFavicon());

        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnReceivedIcon();
            }
        }.run();
        assertNotNull(mOnUiThread.getFavicon());
    }

    public void runWindowTest(boolean expectWindowClose) throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        assertFalse(webChromeClient.hadOnCreateWindow());

        // Load a page that opens a child window and sets a timeout after which the child
        // will be closed.
        mOnUiThread.loadUrlAndWaitForCompletion(mWebServer.
                getAssetUrl(TestHtmlConstants.JS_WINDOW_URL));

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnCreateWindow();
            }
        }.run();

        if (expectWindowClose) {
            new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
                @Override
                protected boolean check() {
                    return webChromeClient.hadOnCloseWindow();
                }
            }.run();
        } else {
            assertFalse(webChromeClient.hadOnCloseWindow());
        }
    }
    @Test
    public void testWindows() throws Exception {
        runWindowTest(true);
    }

    @Test
    public void testBlockWindowsSync() throws Exception {
        mBlockWindowCreationSync = true;
        runWindowTest(false);
    }

    @Test
    public void testBlockWindowsAsync() throws Exception {
        mBlockWindowCreationAsync = true;
        runWindowTest(false);
    }

    // Note that test is still a little flaky. See b/119468441.
    @Test
    public void testOnJsBeforeUnloadIsCalled() throws Exception {
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        final BlockingQueue<String> pageTitleQueue = new ArrayBlockingQueue<>(3);
        final SettableFuture<Void> onJsBeforeUnloadFuture = SettableFuture.create();
        final MockWebChromeClient webChromeClientWaitTitle = new MockWebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                pageTitleQueue.add(title);
            }

            @Override
            public boolean onJsBeforeUnload(
                WebView view, String url, String message, JsResult result) {
                boolean ret = super.onJsBeforeUnload(view, url, message, result);
                onJsBeforeUnloadFuture.set(null);
                return ret;
            }
        };
        mOnUiThread.setWebChromeClient(webChromeClientWaitTitle);

        mOnUiThread.loadUrlAndWaitForCompletion(
            mWebServer.getAssetUrl(TestHtmlConstants.JS_UNLOAD_URL));

        assertEquals(JAVASCRIPT_UNLOAD, WebkitUtils.waitForNextQueueElement(pageTitleQueue));
        assertEquals(LISTENER_ADDED, WebkitUtils.waitForNextQueueElement(pageTitleQueue));
        // Send a user gesture, required for unload to execute since WebView version 60.
        tapWebView();
        assertEquals(TOUCH_RECEIVED, WebkitUtils.waitForNextQueueElement(pageTitleQueue));

        // unload should trigger when we try to navigate away
        mOnUiThread.loadUrlAndWaitForCompletion(
            mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));

        WebkitUtils.waitForFuture(onJsBeforeUnloadFuture);
    }

    @Test
    public void testOnJsAlert() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hadOnJsAlert());

        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_ALERT_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnJsAlert();
            }
        }.run();
        assertEquals(webChromeClient.getMessage(), "testOnJsAlert");
    }

    @Test
    public void testOnJsConfirm() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hadOnJsConfirm());

        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_CONFIRM_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnJsConfirm();
            }
        }.run();
        assertEquals(webChromeClient.getMessage(), "testOnJsConfirm");
    }

    @Test
    public void testOnJsPrompt() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hadOnJsPrompt());

        final String promptResult = "CTS";
        webChromeClient.setPromptResult(promptResult);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_PROMPT_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return webChromeClient.hadOnJsPrompt();
            }
        }.run();
        // the result returned by the client gets set as the page title
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals(promptResult);
            }
        }.run();
        assertEquals(webChromeClient.getMessage(), "testOnJsPrompt");
    }

    @Test
    public void testOnConsoleMessage() throws Exception {
        int numConsoleMessages = 4;
        final BlockingQueue<ConsoleMessage> consoleMessageQueue =
                new ArrayBlockingQueue<>(numConsoleMessages);
        final MockWebChromeClient webChromeClient = new MockWebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                consoleMessageQueue.add(message);
                // return false for default handling; i.e. printing the message.
                return false;
            }
        };
        mOnUiThread.setWebChromeClient(webChromeClient);

        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        // Note: we assert line numbers, which are relative to the line in the HTML file. So, "\n"
        // is significant in this test, and make sure to update consoleLineNumberOffset when
        // editing the HTML.
        final int consoleLineNumberOffset = 3;
        final String unencodedHtml = "<html>\n"
                + "<script>\n"
                + "  console.log('message0');\n"
                + "  console.warn('message1');\n"
                + "  console.error('message2');\n"
                + "  console.info('message3');\n"
                + "</script>\n"
                + "</html>\n";
        final String mimeType = null;
        final String encoding = "base64";
        String encodedHtml = Base64.encodeToString(unencodedHtml.getBytes(), Base64.NO_PADDING);
        mOnUiThread.loadDataAndWaitForCompletion(encodedHtml, mimeType, encoding);

        // Expected message levels correspond to the order of the console messages defined above.
        ConsoleMessage.MessageLevel[] expectedMessageLevels = {
            ConsoleMessage.MessageLevel.LOG,
            ConsoleMessage.MessageLevel.WARNING,
            ConsoleMessage.MessageLevel.ERROR,
            ConsoleMessage.MessageLevel.LOG,
        };
        for (int k = 0; k < numConsoleMessages; k++) {
            final ConsoleMessage consoleMessage =
                    WebkitUtils.waitForNextQueueElement(consoleMessageQueue);
            final ConsoleMessage.MessageLevel expectedMessageLevel = expectedMessageLevels[k];
            assertEquals("message " + k + " had wrong level",
                    expectedMessageLevel,
                    consoleMessage.messageLevel());
            final String expectedMessage = "message" + k;
            assertEquals("message " + k + " had wrong message",
                    expectedMessage,
                    consoleMessage.message());
            final int expectedLineNumber = k + consoleLineNumberOffset;
            assertEquals("message " + k + " had wrong line number",
                    expectedLineNumber,
                    consoleMessage.lineNumber());
        }
    }

    /**
     * Taps in the the center of a webview.
     */
    private void tapWebView() {
        int[] location = mOnUiThread.getLocationOnScreen();
        int middleX = location[0] + mOnUiThread.getWebView().getWidth() / 2;
        int middleY = location[1] + mOnUiThread.getWebView().getHeight() / 2;

        long timeDown = SystemClock.uptimeMillis();
        getTestEnvironment().sendPointerSync(
                MotionEvent.obtain(timeDown, timeDown, MotionEvent.ACTION_DOWN,
                        middleX, middleY, 0));

        long timeUp = SystemClock.uptimeMillis();
        getTestEnvironment().sendPointerSync(
                MotionEvent.obtain(timeUp, timeUp, MotionEvent.ACTION_UP,
                        middleX, middleY, 0));

        // Wait for the system to process all events in the queue
        getTestEnvironment().waitForIdleSync();
    }

    private class MockWebChromeClient extends WaitForProgressClient {
        private boolean mHadOnProgressChanged;
        private boolean mHadOnReceivedTitle;
        private String mPageTitle;
        private boolean mHadOnJsAlert;
        private boolean mHadOnJsConfirm;
        private boolean mHadOnJsPrompt;
        private boolean mHadOnJsBeforeUnload;
        private String mMessage;
        private String mPromptResult;
        private boolean mHadOnCloseWindow;
        private boolean mHadOnCreateWindow;
        private boolean mHadOnRequestFocus;
        private boolean mHadOnReceivedIcon;
        private WebView mChildWebView;

        public MockWebChromeClient() {
            super(mOnUiThread);
        }

        public void setPromptResult(String promptResult) {
            mPromptResult = promptResult;
        }

        public boolean hadOnProgressChanged() {
            return mHadOnProgressChanged;
        }

        public boolean hadOnReceivedTitle() {
            return mHadOnReceivedTitle;
        }

        public String getPageTitle() {
            return mPageTitle;
        }

        public boolean hadOnJsAlert() {
            return mHadOnJsAlert;
        }

        public boolean hadOnJsConfirm() {
            return mHadOnJsConfirm;
        }

        public boolean hadOnJsPrompt() {
            return mHadOnJsPrompt;
        }

        public boolean hadOnJsBeforeUnload() {
            return mHadOnJsBeforeUnload;
        }

        public boolean hadOnCreateWindow() {
            return mHadOnCreateWindow;
        }

        public boolean hadOnCloseWindow() {
            return mHadOnCloseWindow;
        }

        public boolean hadOnRequestFocus() {
            return mHadOnRequestFocus;
        }

        public boolean hadOnReceivedIcon() {
            return mHadOnReceivedIcon;
        }

        public String getMessage() {
            return mMessage;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            mHadOnProgressChanged = true;
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            mPageTitle = title;
            mHadOnReceivedTitle = true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            super.onJsAlert(view, url, message, result);
            mHadOnJsAlert = true;
            mMessage = message;
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            super.onJsConfirm(view, url, message, result);
            mHadOnJsConfirm = true;
            mMessage = message;
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message,
                String defaultValue, JsPromptResult result) {
            super.onJsPrompt(view, url, message, defaultValue, result);
            mHadOnJsPrompt = true;
            mMessage = message;
            result.confirm(mPromptResult);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            super.onJsBeforeUnload(view, url, message, result);
            mHadOnJsBeforeUnload = true;
            mMessage = message;
            result.confirm();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            super.onCloseWindow(window);
            mHadOnCloseWindow = true;

            if (mChildWebView != null) {
                ViewParent parent =  mChildWebView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(mChildWebView);
                }
                mChildWebView.destroy();
            }

        }

        @Override
        public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                Message resultMsg) {
            mHadOnCreateWindow = true;
            if (mBlockWindowCreationSync) {
                return false;
            }
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            if (mBlockWindowCreationAsync) {
                transport.setWebView(null);
            } else {
                mChildWebView = new WebView(getTestEnvironment().getContext());
                final WebSettings settings = mChildWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                mChildWebView.setWebChromeClient(this);
                transport.setWebView(mChildWebView);
                getTestEnvironment().addContentView(mChildWebView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onRequestFocus(WebView view) {
            mHadOnRequestFocus = true;
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            mHadOnReceivedIcon = true;
        }
    }
}
