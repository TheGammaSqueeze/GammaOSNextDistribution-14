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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebIconDatabase;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebView.PictureListener;
import android.webkit.WebView.VisualStateCallback;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewTest extends SharedWebViewTest {
    private static final int INITIAL_PROGRESS = 100;
    private static final String X_REQUESTED_WITH = "X-Requested-With";
    private static final String PRINTER_TEST_FILE = "print.pdf";
    private static final String PDF_PREAMBLE = "%PDF-1";
    // Snippet of HTML that will prevent favicon requests to the test server.
    private static final String HTML_HEADER =
            "<html><head><link rel=\"shortcut icon\" href=\"%23\" /></head>";
    private static final String SIMPLE_HTML = "<html><body>simple html</body></html>";

    /**
     * This is the minimum number of milliseconds to wait for scrolling to start. If no scrolling
     * has started before this timeout then it is assumed that no scrolling will happen.
     */
    private static final long MIN_SCROLL_WAIT_MS = 1000;

    /**
     * This is the minimum number of milliseconds to wait for findAll to find all the matches. If
     * matches are not found, the Listener would call findAll again until it times out.
     */
    private static final long MIN_FIND_WAIT_MS = 3000;

    /**
     * Once scrolling has started, this is the interval that scrolling is checked to see if there is
     * a change. If no scrolling change has happened in the given time then it is assumed that
     * scrolling has stopped.
     */
    private static final long SCROLL_WAIT_INTERVAL_MS = 200;

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private Context mContext;
    private SharedSdkWebServer mWebServer;
    private WebIconDatabase mIconDb;
    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    @Before
    public void setUp() throws Exception {
        mWebView = getTestEnvironment().getWebView();
        mOnUiThread = new WebViewOnUiThread(mWebView);
        mContext = getTestEnvironment().getContext();
    }

    @After
    public void cleanup() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            mWebServer.shutdown();
        }
        if (mIconDb != null) {
            mIconDb.removeAllIcons();
            mIconDb.close();
            mIconDb = null;
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

        // Wait for window focus and clean up the snapshot before
        // returning the test environment.
        if (environment.getWebView() != null) {
            new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
                @Override
                protected boolean check() {
                    return ((Activity) environment.getContext()).hasWindowFocus();
                }
            }.run();
            File f = environment.getContext().getFileStreamPath("snapshot");
            if (f.exists()) {
                f.delete();
            }
        }

        return environment;
    }

    @Test
    public void testConstructor() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    WebView webView = new WebView(mContext);
                    webView.destroy();
                    webView = new WebView(mContext, null);
                    webView.destroy();
                    webView = new WebView(mContext, null, 0);
                    webView.destroy();
                });
    }

    @Test
    public void testCreatingWebViewWithDeviceEncrpytionFails() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    Context deviceEncryptedContext = mContext.createDeviceProtectedStorageContext();
                    try {
                        new WebView(deviceEncryptedContext);
                        fail(
                                "WebView should have thrown exception when creating with a device "
                                        + "protected storage context");
                    } catch (IllegalArgumentException e) {
                    }
                });
    }

    @Test
    public void testCreatingWebViewWithMultipleEncryptionContext() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    // Credential encryption is the default. Create one here for the sake of
                    // clarity.
                    Context credentialEncryptedContext =
                            mContext.createCredentialProtectedStorageContext();
                    Context deviceEncryptedContext = mContext.createDeviceProtectedStorageContext();

                    // No exception should be thrown with credential encryption context.
                    WebView webView = new WebView(credentialEncryptedContext);
                    webView.destroy();

                    try {
                        new WebView(deviceEncryptedContext);
                        fail(
                                "WebView should have thrown exception when creating with a device "
                                        + "protected storage context");
                    } catch (IllegalArgumentException e) {
                    }
                });
    }

    @Test
    public void testCreatingWebViewCreatesCookieSyncManager() throws Exception {
        WebkitUtils.onMainThreadSync(
                () -> {
                    WebView webView = new WebView(mContext);
                    assertNotNull(CookieSyncManager.getInstance());
                    webView.destroy();
                });
    }

    @Test
    // Static methods should be safe to call on non-UI threads
    public void testFindAddress() {
        /*
         * Info about USPS
         * http://en.wikipedia.org/wiki/Postal_address#United_States
         * http://www.usps.com/
         */
        // full address
        assertEquals(
                "455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA 92826",
                WebView.findAddress("455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA 92826"));
        // Zipcode is optional.
        assertEquals(
                "455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA",
                WebView.findAddress("455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA"));
        // not an address
        assertNull(WebView.findAddress("This is not an address: no town, no state, no zip."));

        // would be an address, except for numbers that are not ASCII
        assertNull(
                WebView.findAddress(
                        "80\uD835\uDFEF \uD835\uDFEF\uD835\uDFEFth Avenue Sunnyvale, CA 94089"));
    }

    @Test
    public void testScrollBarOverlay() throws Throwable {
        WebkitUtils.onMainThreadSync(
                () -> {
                    // These functions have no effect; just verify they don't crash
                    mWebView.setHorizontalScrollbarOverlay(true);
                    mWebView.setVerticalScrollbarOverlay(false);

                    assertTrue(mWebView.overlayHorizontalScrollbar());
                    assertFalse(mWebView.overlayVerticalScrollbar());
                });
    }

    @Test
    @Presubmit
    public void testLoadUrl() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    assertNull(mWebView.getUrl());
                    assertNull(mWebView.getOriginalUrl());
                    assertEquals(INITIAL_PROGRESS, mWebView.getProgress());

                    String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
                    mOnUiThread.loadUrlAndWaitForCompletion(url);
                    assertEquals(100, mWebView.getProgress());
                    assertEquals(url, mWebView.getUrl());
                    assertEquals(url, mWebView.getOriginalUrl());
                    assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mWebView.getTitle());
                });
    }

    @Test
    public void testPostUrlWithNonNetworkUrl() throws Exception {
        final String nonNetworkUrl = "file:///android_asset/" + TestHtmlConstants.HELLO_WORLD_URL;

        mOnUiThread.postUrlAndWaitForCompletion(nonNetworkUrl, new byte[1]);

        WebkitUtils.onMainThreadSync(
                () -> {
                    assertEquals(
                            "Non-network URL should have loaded",
                            TestHtmlConstants.HELLO_WORLD_TITLE,
                            mWebView.getTitle());
                });
    }

    @Test
    public void testPostUrlWithNetworkUrl() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        final String networkUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final String postDataString = "username=my_username&password=my_password";
        final byte[] postData = getTestEnvironment().getEncodingBytes(postDataString, "BASE64");

        mOnUiThread.postUrlAndWaitForCompletion(networkUrl, postData);

        HttpRequest request = mWebServer.getLastAssetRequest(TestHtmlConstants.HELLO_WORLD_URL);
        assertEquals("The last request should be POST", request.getMethod(), "POST");
        assertEquals(request.getBody(), postDataString);
    }

    @Test
    public void testLoadUrlDoesNotStripParamsWhenLoadingContentUrls() throws Exception {
        WebkitUtils.onMainThreadSync(
                () -> {
                    Uri.Builder uriBuilder =
                            new Uri.Builder()
                                    .scheme(ContentResolver.SCHEME_CONTENT)
                                    .authority(MockContentProvider.AUTHORITY);
                    uriBuilder.appendPath("foo.html").appendQueryParameter("param", "bar");
                    String url = uriBuilder.build().toString();
                    mOnUiThread.loadUrlAndWaitForCompletion(url);
                    // verify the parameter is not stripped.
                    Uri uri = Uri.parse(mWebView.getTitle());
                    assertEquals("bar", uri.getQueryParameter("param"));
                });
    }

    @Test
    public void testAppInjectedXRequestedWithHeaderIsNotOverwritten() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
                    HashMap<String, String> map = new HashMap<String, String>();
                    final String requester = "foo";
                    map.put(X_REQUESTED_WITH, requester);
                    mOnUiThread.loadUrlAndWaitForCompletion(url, map);

                    // verify that the request also includes X-Requested-With header
                    // but is not overwritten by the webview
                    HttpRequest request =
                            mWebServer.getLastAssetRequest(TestHtmlConstants.HELLO_WORLD_URL);
                    String[] matchingHeaders = request.getHeaders(X_REQUESTED_WITH);
                    assertEquals(1, matchingHeaders.length);

                    String header = matchingHeaders[0];
                    assertEquals(requester, header);
                });
    }

    @Test
    public void testAppCanInjectHeadersViaImmutableMap() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
                    HashMap<String, String> map = new HashMap<String, String>();
                    final String requester = "foo";
                    map.put(X_REQUESTED_WITH, requester);
                    mOnUiThread.loadUrlAndWaitForCompletion(url, Collections.unmodifiableMap(map));

                    // verify that the request also includes X-Requested-With header
                    // but is not overwritten by the webview
                    HttpRequest request =
                            mWebServer.getLastAssetRequest(TestHtmlConstants.HELLO_WORLD_URL);
                    String[] matchingHeaders = request.getHeaders(X_REQUESTED_WITH);
                    assertEquals(1, matchingHeaders.length);

                    String header = matchingHeaders[0];
                    assertEquals(requester, header);
                });
    }

    @Test
    public void testCanInjectHeaders() throws Exception {
        final String X_FOO = "X-foo";
        final String X_FOO_VALUE = "test";

        final String X_REFERER = "Referer";
        final String X_REFERER_VALUE = "http://www.example.com/";
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(X_FOO, X_FOO_VALUE);
        map.put(X_REFERER, X_REFERER_VALUE);
        mOnUiThread.loadUrlAndWaitForCompletion(url, map);

        HttpRequest request = mWebServer.getLastAssetRequest(TestHtmlConstants.HELLO_WORLD_URL);
        for (Map.Entry<String, String> value : map.entrySet()) {
            String header = value.getKey();
            String[] matchingHeaders = request.getHeaders(header);
            assertEquals("header " + header + " not found", 1, matchingHeaders.length);
            assertEquals(value.getValue(), matchingHeaders[0]);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testGetVisibleTitleHeight() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
                    mOnUiThread.loadUrlAndWaitForCompletion(url);
                    assertEquals(0, mWebView.getVisibleTitleHeight());
                });
    }

    @Test
    public void testGetOriginalUrl() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    final String finalUrl =
                            mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
                    final String redirectUrl =
                            mWebServer.getRedirectingAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

                    assertNull(mWebView.getUrl());
                    assertNull(mWebView.getOriginalUrl());

                    // By default, WebView sends an intent to ask the system to
                    // handle loading a new URL. We set a WebViewClient as
                    // WebViewClient.shouldOverrideUrlLoading() returns false, so
                    // the WebView will load the new URL.
                    mWebView.setWebViewClient(new WaitForLoadedClient(mOnUiThread));
                    mOnUiThread.loadUrlAndWaitForCompletion(redirectUrl);

                    assertEquals(finalUrl, mWebView.getUrl());
                    assertEquals(redirectUrl, mWebView.getOriginalUrl());
                });
    }

    @Test
    public void testStopLoading() throws Exception {
        assertEquals(INITIAL_PROGRESS, mOnUiThread.getProgress());

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getDelayedAssetUrl(TestHtmlConstants.STOP_LOADING_URL);

        class JsInterface {
            private boolean mPageLoaded;

            @JavascriptInterface
            public synchronized void pageLoaded() {
                mPageLoaded = true;
                notify();
            }

            public synchronized boolean getPageLoaded() {
                return mPageLoaded;
            }
        }

        JsInterface jsInterface = new JsInterface();

        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.addJavascriptInterface(jsInterface, "javabridge");
        mOnUiThread.loadUrl(url);
        mOnUiThread.stopLoading();

        // We wait to see that the onload callback in the HTML is not fired.
        synchronized (jsInterface) {
            jsInterface.wait(3000);
        }

        assertFalse(jsInterface.getPageLoaded());
    }

    @Test
    public void testGoBackAndForward() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    assertGoBackOrForwardBySteps(false, -1);
                    assertGoBackOrForwardBySteps(false, 1);

                    String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
                    String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
                    String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

                    mOnUiThread.loadUrlAndWaitForCompletion(url1);
                    pollingCheckWebBackForwardList(url1, 0, 1);
                    assertGoBackOrForwardBySteps(false, -1);
                    assertGoBackOrForwardBySteps(false, 1);

                    mOnUiThread.loadUrlAndWaitForCompletion(url2);
                    pollingCheckWebBackForwardList(url2, 1, 2);
                    assertGoBackOrForwardBySteps(true, -1);
                    assertGoBackOrForwardBySteps(false, 1);

                    mOnUiThread.loadUrlAndWaitForCompletion(url3);
                    pollingCheckWebBackForwardList(url3, 2, 3);
                    assertGoBackOrForwardBySteps(true, -2);
                    assertGoBackOrForwardBySteps(false, 1);

                    mWebView.goBack();
                    pollingCheckWebBackForwardList(url2, 1, 3);
                    assertGoBackOrForwardBySteps(true, -1);
                    assertGoBackOrForwardBySteps(true, 1);

                    mWebView.goForward();
                    pollingCheckWebBackForwardList(url3, 2, 3);
                    assertGoBackOrForwardBySteps(true, -2);
                    assertGoBackOrForwardBySteps(false, 1);

                    mWebView.goBackOrForward(-2);
                    pollingCheckWebBackForwardList(url1, 0, 3);
                    assertGoBackOrForwardBySteps(false, -1);
                    assertGoBackOrForwardBySteps(true, 2);

                    mWebView.goBackOrForward(2);
                    pollingCheckWebBackForwardList(url3, 2, 3);
                    assertGoBackOrForwardBySteps(true, -2);
                    assertGoBackOrForwardBySteps(false, 1);
                });
    }

    @Test
    public void testAddJavascriptInterface() throws Exception {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        final class TestJavaScriptInterface {
            private boolean mWasProvideResultCalled;
            private String mResult;

            private synchronized String waitForResult() {
                while (!mWasProvideResultCalled) {
                    try {
                        wait(WebkitUtils.TEST_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    if (!mWasProvideResultCalled) {
                        fail("Unexpected timeout");
                    }
                }
                return mResult;
            }

            public synchronized boolean wasProvideResultCalled() {
                return mWasProvideResultCalled;
            }

            @JavascriptInterface
            public synchronized void provideResult(String result) {
                mWasProvideResultCalled = true;
                mResult = result;
                notify();
            }
        }

        final TestJavaScriptInterface obj = new TestJavaScriptInterface();
        mOnUiThread.addJavascriptInterface(obj, "interface");
        assertFalse(obj.wasProvideResultCalled());

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.ADD_JAVA_SCRIPT_INTERFACE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("Original title", obj.waitForResult());

        // Verify that only methods annotated with @JavascriptInterface are exposed
        // on the JavaScript interface object.
        assertEquals(
                "\"function\"",
                mOnUiThread.evaluateJavascriptSync("typeof interface.provideResult"));

        assertEquals(
                "\"undefined\"",
                mOnUiThread.evaluateJavascriptSync("typeof interface.wasProvideResultCalled"));

        assertEquals(
                "\"undefined\"", mOnUiThread.evaluateJavascriptSync("typeof interface.getClass"));
    }

    @Test
    public void testAddJavascriptInterfaceNullObject() throws Exception {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        String setTitleToPropertyTypeHtml =
                "<html><head></head><body onload=\"document.title = typeof"
                        + " window.injectedObject;\"></body></html>";

        // Test that the property is initially undefined.
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("undefined", mOnUiThread.getTitle());

        // Test that adding a null object has no effect.
        mOnUiThread.addJavascriptInterface(null, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("undefined", mOnUiThread.getTitle());

        // Test that adding an object gives an object type.
        final Object obj = new Object();
        mOnUiThread.addJavascriptInterface(obj, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("object", mOnUiThread.getTitle());

        // Test that trying to replace with a null object has no effect.
        mOnUiThread.addJavascriptInterface(null, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("object", mOnUiThread.getTitle());
    }

    @Test
    public void testRemoveJavascriptInterface() throws Exception {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        String setTitleToPropertyTypeHtml =
                "<html><head></head><body onload=\"document.title = typeof"
                        + " window.injectedObject;\"></body></html>";

        // Test that adding an object gives an object type.
        mOnUiThread.addJavascriptInterface(new Object(), "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("object", mOnUiThread.getTitle());

        // Test that reloading the page after removing the object leaves the property undefined.
        mOnUiThread.removeJavascriptInterface("injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml, "text/html", null);
        assertEquals("undefined", mOnUiThread.getTitle());
    }

    @Test
    public void testUseRemovedJavascriptInterface() throws Throwable {
        class RemovedObject {
            @Override
            @JavascriptInterface
            public String toString() {
                return "removedObject";
            }

            @JavascriptInterface
            public void remove() throws Throwable {
                mOnUiThread.removeJavascriptInterface("removedObject");
                System.gc();
            }
        }
        class ResultObject {
            private String mResult;
            private boolean mIsResultAvailable;

            @JavascriptInterface
            public synchronized void setResult(String result) {
                mResult = result;
                mIsResultAvailable = true;
                notify();
            }

            public synchronized String getResult() {
                while (!mIsResultAvailable) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
                return mResult;
            }
        }
        final ResultObject resultObject = new ResultObject();

        // Test that an object is still usable if removed while the page is in use, even if we have
        // no external references to it.
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.addJavascriptInterface(new RemovedObject(), "removedObject");
        mOnUiThread.addJavascriptInterface(resultObject, "resultObject");
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head></head>"
                        + "<body onload=\"window.removedObject.remove();"
                        + "resultObject.setResult(removedObject.toString());\"></body></html>",
                "text/html",
                null);
        assertEquals("removedObject", resultObject.getResult());
    }

    @Test
    public void testAddJavascriptInterfaceExceptions() throws Exception {
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        final AtomicBoolean mJsInterfaceWasCalled =
                new AtomicBoolean(false) {
                    @JavascriptInterface
                    public synchronized void call() {
                        set(true);
                        // The main purpose of this test is to ensure an exception here does not
                        // crash the implementation.
                        throw new RuntimeException("Javascript Interface exception");
                    }
                };

        mOnUiThread.addJavascriptInterface(mJsInterfaceWasCalled, "interface");

        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        assertFalse(mJsInterfaceWasCalled.get());

        assertEquals(
                "\"pass\"",
                mOnUiThread.evaluateJavascriptSync(
                        "try {interface.call(); 'fail'; } catch (exception) { 'pass'; } "));
        assertTrue(mJsInterfaceWasCalled.get());
    }

    @Test
    public void testJavascriptInterfaceCustomPropertiesClearedOnReload() throws Exception {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);

        mOnUiThread.addJavascriptInterface(new Object(), "interface");
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        assertEquals("42", mOnUiThread.evaluateJavascriptSync("interface.custom_property = 42"));

        assertEquals("true", mOnUiThread.evaluateJavascriptSync("'custom_property' in interface"));

        mOnUiThread.reloadAndWaitForCompletion();

        assertEquals("false", mOnUiThread.evaluateJavascriptSync("'custom_property' in interface"));
    }

    @Test
    @FlakyTest(bugId = 171702662)
    public void testJavascriptInterfaceForClientPopup() throws Exception {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mOnUiThread.getSettings().setSupportMultipleWindows(true);

        class TestJavaScriptInterface {
            @JavascriptInterface
            public int test() {
                return 42;
            }
        }
        final TestJavaScriptInterface obj = new TestJavaScriptInterface();

        final WebView childWebView = mOnUiThread.createWebView();
        try {
            WebViewOnUiThread childOnUiThread = new WebViewOnUiThread(childWebView);
            childOnUiThread.getSettings().setJavaScriptEnabled(true);
            childOnUiThread.addJavascriptInterface(obj, "interface");

            final SettableFuture<Void> onCreateWindowFuture = SettableFuture.create();
            mOnUiThread.setWebChromeClient(
                    new WebViewSyncLoader.WaitForProgressClient(mOnUiThread) {
                        @Override
                        public boolean onCreateWindow(
                                WebView view,
                                boolean isDialog,
                                boolean isUserGesture,
                                Message resultMsg) {
                            getTestEnvironment().addContentView(
                                    childWebView,
                                    new ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.FILL_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT));
                            WebView.WebViewTransport transport =
                                    (WebView.WebViewTransport) resultMsg.obj;
                            transport.setWebView(childWebView);
                            resultMsg.sendToTarget();
                            onCreateWindowFuture.set(null);
                            return true;
                        }
                    });

            mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
            mOnUiThread.loadUrlAndWaitForCompletion(
                    mWebServer.getAssetUrl(TestHtmlConstants.POPUP_URL));
            WebkitUtils.waitForFuture(onCreateWindowFuture);

            childOnUiThread.loadUrlAndWaitForCompletion("about:blank");

            assertEquals("true", childOnUiThread.evaluateJavascriptSync("'interface' in window"));

            assertEquals(
                    "The injected object should be functional",
                    "42",
                    childOnUiThread.evaluateJavascriptSync("interface.test()"));
        } finally {
            WebkitUtils.onMainThreadSync(() -> {
                ViewParent parent = childWebView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(childWebView);
                }
                childWebView.destroy();
            });
        }
    }

    private final class TestPictureListener implements PictureListener {
        public int callCount;

        @Override
        public void onNewPicture(WebView view, Picture picture) {
            // Need to inform the listener tracking new picture
            // for the "page loaded" knowledge since it has been replaced.
            mOnUiThread.onNewPicture();
            this.callCount += 1;
        }
    }

    private Picture waitForPictureToHaveColor(int color, final TestPictureListener listener)
            throws Throwable {
        final int MAX_ON_NEW_PICTURE_ITERATIONS = 5;
        final AtomicReference<Picture> pictureRef = new AtomicReference<Picture>();
        for (int i = 0; i < MAX_ON_NEW_PICTURE_ITERATIONS; i++) {
            final int oldCallCount = listener.callCount;
            WebkitUtils.onMainThreadSync(
                    () -> {
                        pictureRef.set(mWebView.capturePicture());
                    });
            if (isPictureFilledWithColor(pictureRef.get(), color)) break;
            new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
                @Override
                protected boolean check() {
                    return listener.callCount > oldCallCount;
                }
            }.run();
        }
        return pictureRef.get();
    }

    @Test
    public void testCapturePicture() throws Exception, Throwable {
        final TestPictureListener listener = new TestPictureListener();

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.BLANK_PAGE_URL);
        mOnUiThread.setPictureListener(listener);
        // Showing the blank page will fill the picture with the background color.
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // The default background color is white.
        Picture oldPicture = waitForPictureToHaveColor(Color.WHITE, listener);

        WebkitUtils.onMainThread(
                () -> {
                    mWebView.setBackgroundColor(Color.CYAN);
                });
        mOnUiThread.reloadAndWaitForCompletion();
        waitForPictureToHaveColor(Color.CYAN, listener);

        assertTrue(
                "The content of the previously captured picture should not update automatically",
                isPictureFilledWithColor(oldPicture, Color.WHITE));
    }

    @Test
    public void testSetPictureListener() throws Exception, Throwable {
        final class MyPictureListener implements PictureListener {
            public int callCount;
            public WebView webView;
            public Picture picture;

            @Override
            public void onNewPicture(WebView view, Picture picture) {
                // Need to inform the listener tracking new picture
                // for the "page loaded" knowledge since it has been replaced.
                mOnUiThread.onNewPicture();
                this.callCount += 1;
                this.webView = view;
                this.picture = picture;
            }
        }

        final MyPictureListener listener = new MyPictureListener();
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setPictureListener(listener);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return listener.callCount > 0;
            }
        }.run();
        assertEquals(mWebView, listener.webView);
        assertNull(listener.picture);

        final int oldCallCount = listener.callCount;
        final String newUrl = mWebServer.getAssetUrl(TestHtmlConstants.SMALL_IMG_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(newUrl);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return listener.callCount > oldCallCount;
            }
        }.run();
    }

    @Test
    public void testAccessHttpAuthUsernamePassword() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    try {
                        WebViewDatabase.getInstance(mContext).clearHttpAuthUsernamePassword();

                        String host = "http://localhost:8080";
                        String realm = "testrealm";
                        String userName = "user";
                        String password = "password";

                        String[] result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNull(result);

                        mWebView.setHttpAuthUsernamePassword(host, realm, userName, password);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertEquals(password, result[1]);

                        String newPassword = "newpassword";
                        mWebView.setHttpAuthUsernamePassword(host, realm, userName, newPassword);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertEquals(newPassword, result[1]);

                        String newUserName = "newuser";
                        mWebView.setHttpAuthUsernamePassword(host, realm, newUserName, newPassword);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(newUserName, result[0]);
                        assertEquals(newPassword, result[1]);

                        // the user is set to null, can not change any thing in the future
                        mWebView.setHttpAuthUsernamePassword(host, realm, null, password);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertNull(result[0]);
                        assertEquals(password, result[1]);

                        mWebView.setHttpAuthUsernamePassword(host, realm, userName, null);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertNull(result[1]);

                        mWebView.setHttpAuthUsernamePassword(host, realm, null, null);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertNull(result[0]);
                        assertNull(result[1]);

                        mWebView.setHttpAuthUsernamePassword(host, realm, newUserName, newPassword);
                        result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(newUserName, result[0]);
                        assertEquals(newPassword, result[1]);
                    } finally {
                        WebViewDatabase.getInstance(mContext).clearHttpAuthUsernamePassword();
                    }
                });
    }

    @Test
    public void testWebViewDatabaseAccessHttpAuthUsernamePassword() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    WebViewDatabase webViewDb = WebViewDatabase.getInstance(mContext);
                    try {
                        webViewDb.clearHttpAuthUsernamePassword();

                        String host = "http://localhost:8080";
                        String realm = "testrealm";
                        String userName = "user";
                        String password = "password";

                        String[] result = mWebView.getHttpAuthUsernamePassword(host, realm);
                        assertNull(result);

                        webViewDb.setHttpAuthUsernamePassword(host, realm, userName, password);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertEquals(password, result[1]);

                        String newPassword = "newpassword";
                        webViewDb.setHttpAuthUsernamePassword(host, realm, userName, newPassword);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertEquals(newPassword, result[1]);

                        String newUserName = "newuser";
                        webViewDb.setHttpAuthUsernamePassword(
                                host, realm, newUserName, newPassword);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(newUserName, result[0]);
                        assertEquals(newPassword, result[1]);

                        // the user is set to null, can not change any thing in the future
                        webViewDb.setHttpAuthUsernamePassword(host, realm, null, password);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertNull(result[0]);
                        assertEquals(password, result[1]);

                        webViewDb.setHttpAuthUsernamePassword(host, realm, userName, null);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(userName, result[0]);
                        assertNull(result[1]);

                        webViewDb.setHttpAuthUsernamePassword(host, realm, null, null);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertNull(result[0]);
                        assertNull(result[1]);

                        webViewDb.setHttpAuthUsernamePassword(
                                host, realm, newUserName, newPassword);
                        result = webViewDb.getHttpAuthUsernamePassword(host, realm);
                        assertNotNull(result);
                        assertEquals(newUserName, result[0]);
                        assertEquals(newPassword, result[1]);
                    } finally {
                        webViewDb.clearHttpAuthUsernamePassword();
                    }
                });
    }

    @Test
    public void testLoadData() throws Throwable {
        final String firstTitle = "Hello, World!";
        final String HTML_CONTENT =
                "<html><head><title>" + firstTitle + "</title></head><body></body>" + "</html>";
        mOnUiThread.loadDataAndWaitForCompletion(HTML_CONTENT, "text/html", null);
        assertEquals(firstTitle, mOnUiThread.getTitle());

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String crossOriginUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        final String secondTitle = "Foo bar";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head><title>"
                        + secondTitle
                        + "</title></head><body onload=\""
                        + "document.title = "
                        + "document.getElementById('frame').contentWindow.location.href;"
                        + "\"><iframe id=\"frame\" src=\""
                        + crossOriginUrl
                        + "\"></body></html>",
                "text/html",
                null);
        assertEquals(
                "Page title should not change, because it should be an error to access a "
                        + "cross-site frame's href.",
                secondTitle,
                mOnUiThread.getTitle());
    }

    @Test
    public void testLoadDataWithBaseUrl_resolvesRelativeToBaseUrl() throws Throwable {
        assertNull(mOnUiThread.getUrl());
        String imgUrl = TestHtmlConstants.SMALL_IMG_URL; // relative

        // Trying to resolve a relative URL against a data URL without a base URL
        // will fail and we won't make a request to the test web server.
        // By using the test web server as the base URL we expect to see a request
        // for the relative URL in the test server.
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String baseUrl = mWebServer.getAssetUrl("foo.html");
        mWebServer.resetRequestState();
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                baseUrl,
                HTML_HEADER + "<body><img src=\"" + imgUrl + "\"/></body></html>",
                "text/html",
                "UTF-8",
                null);
        assertTrue(
                "The resource request should make it to the server",
                mWebServer.wasResourceRequested(imgUrl));
    }

    @Test
    public void testLoadDataWithBaseUrl_historyUrl() throws Throwable {
        final String baseUrl = "http://www.baseurl.com/";
        final String historyUrl = "http://www.example.com/";
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                baseUrl, SIMPLE_HTML, "text/html", "UTF-8", historyUrl);
        assertEquals(historyUrl, mOnUiThread.getUrl());
    }

    @Test
    public void testLoadDataWithBaseUrl_nullHistoryUrlShowsAsAboutBlank() throws Throwable {
        // Check that reported URL is "about:blank" when supplied history URL
        // is null.
        final String baseUrl = "http://www.baseurl.com/";
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                baseUrl, SIMPLE_HTML, "text/html", "UTF-8", null);
        assertEquals("about:blank", mOnUiThread.getUrl());
    }

    @Test
    public void testLoadDataWithBaseUrl_javascriptCanAccessOrigin() throws Throwable {
        // Test that JavaScript can access content from the same origin as the base URL.
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String baseUrl = mWebServer.getAssetUrl("foo.html");
        final String crossOriginUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                baseUrl,
                HTML_HEADER
                        + "<body onload=\"document.title ="
                        + " document.getElementById('frame').contentWindow.location.href;\"><iframe"
                        + " id=\"frame\" src=\""
                        + crossOriginUrl
                        + "\"></body></html>",
                "text/html",
                "UTF-8",
                null);
        assertEquals(crossOriginUrl, mOnUiThread.getTitle());
    }

    @Test
    public void testLoadDataWithBaseUrl_dataBaseUrlIgnoresHistoryUrl() throws Throwable {
        // Check that when the base URL uses the 'data' scheme, a 'data' scheme URL is used and the
        // history URL is ignored.
        final String baseUrl = "data:foo";
        final String historyUrl = "http://www.example.com/";
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                baseUrl, SIMPLE_HTML, "text/html", "UTF-8", historyUrl);

        final String currentUrl = mOnUiThread.getUrl();
        assertEquals(
                "Current URL (" + currentUrl + ") should be a data URI",
                0,
                mOnUiThread.getUrl().indexOf("data:text/html"));
        assertThat(
                "Current URL (" + currentUrl + ") should contain the simple HTML we loaded",
                mOnUiThread.getUrl().indexOf("simple html"),
                greaterThan(0));
    }

    @Test
    public void testLoadDataWithBaseUrl_unencodedContentHttpBaseUrl() throws Throwable {
        // Check that when a non-data: base URL is used, we treat the String to load as
        // a raw string and just dump it into the WebView, i.e. not decoding any URL entities.
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                "http://www.foo.com",
                HTML_HEADER + "<title>Hello World%21</title><body>bar</body></html>",
                "text/html",
                "UTF-8",
                null);
        assertEquals("Hello World%21", mOnUiThread.getTitle());
    }

    @Test
    public void testLoadDataWithBaseUrl_urlEncodedContentDataBaseUrl() throws Throwable {
        // Check that when a data: base URL is used, we treat the String to load as a data: URL
        // and run load steps such as decoding URL entities (i.e., contrary to the test case
        // above.)
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                "data:foo",
                HTML_HEADER + "<title>Hello World%21</title></html>",
                "text/html",
                "UTF-8",
                null);
        assertEquals("Hello World!", mOnUiThread.getTitle());
    }

    @Test
    public void testLoadDataWithBaseUrl_nullSafe() throws Throwable {
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(null, null, null, null, null);
        assertEquals("about:blank", mOnUiThread.getUrl());
    }

    private void deleteIfExists(File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
    }

    private String readTextFile(File file, Charset encoding)
            throws FileNotFoundException, IOException {
        FileInputStream stream = new FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];
        stream.read(bytes);
        stream.close();
        return new String(bytes, encoding);
    }

    private void doSaveWebArchive(String baseName, boolean autoName, final String expectName)
            throws Throwable {
        final Semaphore saving = new Semaphore(0);
        ValueCallback<String> callback =
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String savedName) {
                        assertEquals(expectName, savedName);
                        saving.release();
                    }
                };

        mOnUiThread.saveWebArchive(baseName, autoName, callback);
        assertTrue(saving.tryAcquire(WebkitUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSaveWebArchive() throws Throwable {
        final String testPage = "testSaveWebArchive test page";

        File dir = mContext.getFilesDir();
        String dirStr = dir.toString();

        File test = new File(dir, "test.mht");
        deleteIfExists(test);
        String testStr = test.getAbsolutePath();

        File index = new File(dir, "index.mht");
        deleteIfExists(index);
        String indexStr = index.getAbsolutePath();

        File index1 = new File(dir, "index-1.mht");
        deleteIfExists(index1);
        String index1Str = index1.getAbsolutePath();

        mOnUiThread.loadDataAndWaitForCompletion(testPage, "text/html", "UTF-8");

        try {
            // Save test.mht
            doSaveWebArchive(testStr, false, testStr);

            // Check the contents of test.mht
            String testMhtml = readTextFile(test, StandardCharsets.UTF_8);
            assertTrue(testMhtml.contains(testPage));

            // Save index.mht
            doSaveWebArchive(dirStr + "/", true, indexStr);

            // Check the contents of index.mht
            String indexMhtml = readTextFile(index, StandardCharsets.UTF_8);
            assertTrue(indexMhtml.contains(testPage));

            // Save index-1.mht since index.mht already exists
            doSaveWebArchive(dirStr + "/", true, index1Str);

            // Check the contents of index-1.mht
            String index1Mhtml = readTextFile(index1, StandardCharsets.UTF_8);
            assertTrue(index1Mhtml.contains(testPage));

            // Try a file in a bogus directory
            doSaveWebArchive("/bogus/path/test.mht", false, null);

            // Try a bogus directory
            doSaveWebArchive("/bogus/path/", true, null);
        } finally {
            deleteIfExists(test);
            deleteIfExists(index);
            deleteIfExists(index1);
        }
    }

    private static class WaitForFindResultsListener implements WebView.FindListener {
        private final SettableFuture<Integer> mFuture;
        private final WebView mWebView;
        private final int mMatchesWanted;
        private final String mStringWanted;
        private final boolean mRetry;

        WaitForFindResultsListener(WebView wv, String wanted, int matches, boolean retry) {
            mFuture = SettableFuture.create();
            mWebView = wv;
            mMatchesWanted = matches;
            mStringWanted = wanted;
            mRetry = retry;
        }

        public Future<Integer> future() {
            return mFuture;
        }

        @Override
        public void onFindResultReceived(
                int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
            try {
                assertEquals(
                        "WebView.FindListener callbacks should occur on the UI thread",
                        Looper.myLooper(),
                        Looper.getMainLooper());
            } catch (Throwable t) {
                mFuture.setException(t);
            }
            if (isDoneCounting) {
                // If mRetry set to true and matches aren't equal, call findAll again
                if (mRetry && numberOfMatches != mMatchesWanted) {
                    mWebView.findAll(mStringWanted);
                } else {
                    mFuture.set(numberOfMatches);
                }
            }
        }
    }

    @Test
    public void testFindAll() throws Throwable {
        // Make the page scrollable, so we can detect the scrolling to make sure the
        // content fully loaded.
        mOnUiThread.setInitialScale(100);
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = Math.max(metrics.widthPixels, metrics.heightPixels);
        // create a paragraph high enough to take up the entire screen
        String p =
                "<p style=\"height:"
                        + dimension
                        + "px;\">"
                        + "Find all instances of find on the page and highlight them.</p>";

        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "</body></html>", "text/html", null);

        WaitForFindResultsListener l = new WaitForFindResultsListener(mWebView, "find", 2, true);
        mOnUiThread.setFindListener(l);
        mOnUiThread.findAll("find");
        assertEquals(2, (int) WebkitUtils.waitForFuture(l.future()));
    }

    @Test
    public void testFindNext() throws Throwable {
        // Reset the scaling so that finding the next "all" text will require scrolling.
        mOnUiThread.setInitialScale(100);

        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = Math.max(metrics.widthPixels, metrics.heightPixels);
        // create a paragraph high enough to take up the entire screen
        String p =
                "<p style=\"height:"
                        + dimension
                        + "px;\">"
                        + "Find all instances of a word on the page and highlight them.</p>";

        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + p + "</body></html>", "text/html", null);
        WaitForFindResultsListener l = new WaitForFindResultsListener(mWebView, "all", 2, true);
        mOnUiThread.setFindListener(l);

        // highlight all the strings found and wait for all the matches to be found
        mOnUiThread.findAll("all");
        WebkitUtils.waitForFuture(l.future());
        mOnUiThread.setFindListener(null);

        int previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the second page and assert that the view scrolls.
        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertThat(mOnUiThread.getScrollY(), greaterThan(previousScrollY));
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the first page and assert that the view scrolls.
        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertThat(mOnUiThread.getScrollY(), lessThan(previousScrollY));
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the second page and assert that the view scrolls.
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertThat(mOnUiThread.getScrollY(), greaterThan(previousScrollY));
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the first page and assert that the view scrolls.
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertThat(mOnUiThread.getScrollY(), lessThan(previousScrollY));
        previousScrollY = mOnUiThread.getScrollY();

        // clear the result
        mOnUiThread.clearMatches();
        getTestEnvironment().waitForIdleSync();

        // can not scroll any more
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertEquals(mOnUiThread.getScrollY(), previousScrollY);

        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertEquals(mOnUiThread.getScrollY(), previousScrollY);
    }

    @Test
    public void testDocumentHasImages() throws Exception, Throwable {
        final class DocumentHasImageCheckHandler extends Handler {
            private SettableFuture<Integer> mFuture;

            public DocumentHasImageCheckHandler(Looper looper) {
                super(looper);
                mFuture = SettableFuture.create();
            }

            @Override
            public void handleMessage(Message msg) {
                mFuture.set(msg.arg1);
            }

            public Future<Integer> future() {
                return mFuture;
            }
        }

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String imgUrl = mWebServer.getAssetUrl(TestHtmlConstants.SMALL_IMG_URL);

        // Create a handler on the UI thread.
        final DocumentHasImageCheckHandler handler =
                new DocumentHasImageCheckHandler(mWebView.getHandler().getLooper());

        WebkitUtils.onMainThreadSync(
                () -> {
                    mOnUiThread.loadDataAndWaitForCompletion(
                            "<html><body><img src=\"" + imgUrl + "\"/></body></html>",
                            "text/html",
                            null);
                    Message response = new Message();
                    response.setTarget(handler);
                    assertFalse(handler.future().isDone());
                    mWebView.documentHasImages(response);
                });
        assertEquals(1, (int) WebkitUtils.waitForFuture(handler.future()));
    }

    private static void waitForFlingDone(WebViewOnUiThread webview) {
        class ScrollDiffPollingCheck extends PollingCheck {
            private static final long TIME_SLICE = 50;
            WebViewOnUiThread mWebView;
            private int mScrollX;
            private int mScrollY;

            ScrollDiffPollingCheck(WebViewOnUiThread webview) {
                mWebView = webview;
                mScrollX = mWebView.getScrollX();
                mScrollY = mWebView.getScrollY();
            }

            @Override
            protected boolean check() {
                try {
                    Thread.sleep(TIME_SLICE);
                } catch (InterruptedException e) {
                    // Intentionally ignored.
                }
                int newScrollX = mWebView.getScrollX();
                int newScrollY = mWebView.getScrollY();
                boolean flingDone = newScrollX == mScrollX && newScrollY == mScrollY;
                mScrollX = newScrollX;
                mScrollY = newScrollY;
                return flingDone;
            }
        }
        new ScrollDiffPollingCheck(webview).run();
    }

    @Test
    public void testPageScroll() throws Throwable {
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = 2 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p =
                "<p style=\"height:"
                        + dimension
                        + "px;\">"
                        + "Scroll by half the size of the page.</p>";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + p + "</body></html>", "text/html", null);

        // Wait for UI thread to settle and receive page dimentions from renderer
        // such that we can invoke page down.
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.pageDown(false);
            }
        }.run();

        do {
            waitForFlingDone(mOnUiThread);
        } while (mOnUiThread.pageDown(false));

        waitForFlingDone(mOnUiThread);
        final int bottomScrollY = mOnUiThread.getScrollY();

        assertTrue(mOnUiThread.pageUp(false));

        do {
            waitForFlingDone(mOnUiThread);
        } while (mOnUiThread.pageUp(false));

        waitForFlingDone(mOnUiThread);
        final int topScrollY = mOnUiThread.getScrollY();

        // jump to the bottom
        assertTrue(mOnUiThread.pageDown(true));
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return bottomScrollY == mOnUiThread.getScrollY();
            }
        }.run();

        // jump to the top
        assertTrue(mOnUiThread.pageUp(true));
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return topScrollY == mOnUiThread.getScrollY();
            }
        }.run();
    }

    @Test
    public void testGetContentHeight() throws Throwable {
        mOnUiThread.loadDataAndWaitForCompletion("<html><body></body></html>", "text/html", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getScale() != 0
                        && mOnUiThread.getContentHeight() != 0
                        && mOnUiThread.getHeight() != 0;
            }
        }.run();

        final int tolerance = 2;
        // getHeight() returns physical pixels and it is from web contents' size, getContentHeight()
        // returns CSS pixels and it is from compositor. In order to compare these two values, we
        // need to scale getContentHeight() by the device scale factor. This also amplifies any
        // rounding errors. Internally, getHeight() could also have rounding error first and then
        // times device scale factor, so we are comparing two rounded numbers below.
        // We allow 2 * getScale() as the delta, because getHeight() and getContentHeight() may
        // use different rounding algorithms and the results are from different computation
        // sequences. The extreme case is that in CSS pixel we have 2 as differences (0.9999 rounded
        // down and 1.0001 rounded up), therefore we ended with 2 * getScale().
        assertEquals(
                mOnUiThread.getHeight(),
                mOnUiThread.getContentHeight() * mOnUiThread.getScale(),
                tolerance * Math.max(mOnUiThread.getScale(), 1.0f));

        // Make pageHeight bigger than the larger dimension of the device, so the page is taller
        // than viewport. Because when layout_height set to match_parent, getContentHeight() will
        // give maximum value between the actual web content height and the viewport height. When
        // viewport height is bigger, |extraSpace| below is not the extra space on the web page.
        // Note that we are passing physical pixels rather than CSS pixels here, when screen density
        // scale is lower than 1.0f, we need to scale it up.
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        final float scaleFactor = Math.max(1.0f, 1.0f / mOnUiThread.getScale());
        final int pageHeight =
                (int)
                        (Math.ceil(
                                Math.max(metrics.widthPixels, metrics.heightPixels) * scaleFactor));

        // set the margin to 0
        final String p =
                "<p style=\"height:"
                        + pageHeight
                        + "px;margin:0px auto;\">Get the height of HTML content.</p>";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "</body></html>", "text/html", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() > pageHeight;
            }
        }.run();

        final int extraSpace = mOnUiThread.getContentHeight() - pageHeight;
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + p + "</body></html>", "text/html", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                // |pageHeight| is accurate, |extraSpace| = getContentheight() - |pageHeight|, so it
                // might have rounding error +-1, also getContentHeight() might have rounding error
                // +-1, so we allow error 2. Note that |pageHeight|, |extraSpace| and
                // getContentHeight() are all CSS pixels.
                final int expectedContentHeight = pageHeight + pageHeight + extraSpace;
                return Math.abs(expectedContentHeight - mOnUiThread.getContentHeight())
                        <= tolerance;
            }
        }.run();
    }

    @Test
    public void testPlatformNotifications() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    WebView.enablePlatformNotifications();
                    WebView.disablePlatformNotifications();
                });
    }

    @Test
    public void testAccessPluginList() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    assertNotNull(WebView.getPluginList());

                    // can not find a way to install plugins
                    mWebView.refreshPlugins(false);
                });
    }

    @Test
    public void testDestroy() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    // Create a new WebView, since we cannot call destroy() on a view in the
                    // hierarchy
                    WebView localWebView = new WebView(mContext);
                    localWebView.destroy();
                });
    }

    @Test
    public void testFlingScroll() throws Throwable {
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        final int dimension = 10 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p =
                "<p style=\"height:"
                        + dimension
                        + "px;"
                        + "width:"
                        + dimension
                        + "px\">Test fling scroll.</p>";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "</body></html>", "text/html", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() >= dimension;
            }
        }.run();

        getTestEnvironment().waitForIdleSync();

        final int previousScrollX = mOnUiThread.getScrollX();
        final int previousScrollY = mOnUiThread.getScrollY();

        mOnUiThread.flingScroll(10000, 10000);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getScrollX() > previousScrollX
                        && mOnUiThread.getScrollY() > previousScrollY;
            }
        }.run();
    }

    @Test
    public void testRequestFocusNodeHref() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        final String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        final String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        final String links =
                "<DL><p><DT><A HREF=\""
                        + url1
                        + "\">HTML_URL1</A><DT><A HREF=\""
                        + url2
                        + "\">HTML_URL2</A></DL><p>";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + links + "</body></html>", "text/html", null);
        getTestEnvironment().waitForIdleSync();

        final HrefCheckHandler handler = new HrefCheckHandler(mWebView.getHandler().getLooper());
        final Message hrefMsg = new Message();
        hrefMsg.setTarget(handler);

        // focus on first link
        handler.reset();
        getTestEnvironment().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        mOnUiThread.requestFocusNodeHref(hrefMsg);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                boolean done = false;
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestFocusNodeHref(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(url1, handler.getResultUrl());

        // focus on second link
        handler.reset();
        final Message hrefMsg2 = new Message();
        hrefMsg2.setTarget(handler);
        getTestEnvironment().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        mOnUiThread.requestFocusNodeHref(hrefMsg2);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                boolean done = false;
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null && handler.mResultUrl.equals(url2)) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestFocusNodeHref(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(url2, handler.getResultUrl());

        mOnUiThread.requestFocusNodeHref(null);
    }

    @Test
    public void testRequestImageRef() throws Exception, Throwable {
        final class ImageLoaded {
            public SettableFuture<Void> mImageLoaded = SettableFuture.create();

            @JavascriptInterface
            public void loaded() {
                mImageLoaded.set(null);
            }

            public Future<Void> future() {
                return mImageLoaded;
            }
        }
        final ImageLoaded imageLoaded = new ImageLoaded();
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.addJavascriptInterface(imageLoaded, "imageLoaded");

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String imgUrl = mWebServer.getAssetUrl(TestHtmlConstants.LARGE_IMG_URL);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head><title>Title</title><style type='text/css'>"
                        + "%23imgElement { -webkit-transform: translate3d(0,0,1); }"
                        + "%23imgElement.finish { -webkit-transform: translate3d(0,0,0);"
                        + " -webkit-transition-duration: 1ms; }</style>"
                        + "<script type='text/javascript'>function imgLoad() {"
                        + "imgElement = document.getElementById('imgElement');"
                        + "imgElement.addEventListener('webkitTransitionEnd',"
                        + "function(e) { imageLoaded.loaded(); });"
                        + "imgElement.className = 'finish';}</script>"
                        + "</head><body><img id='imgElement' src='"
                        + imgUrl
                        + "' width='100%' height='100%' onLoad='imgLoad()'/>"
                        + "</body></html>",
                "text/html",
                null);
        WebkitUtils.waitForFuture(imageLoaded.future());
        getTestEnvironment().waitForIdleSync();

        final HrefCheckHandler handler = new HrefCheckHandler(mWebView.getHandler().getLooper());
        final Message msg = new Message();
        msg.setTarget(handler);

        // touch the image
        handler.reset();
        int[] location = mOnUiThread.getLocationOnScreen();
        int middleX = location[0] + mOnUiThread.getWebView().getWidth() / 2;
        int middleY = location[1] + mOnUiThread.getWebView().getHeight() / 2;

        long time = SystemClock.uptimeMillis();
        getTestEnvironment()
                .sendPointerSync(
                        MotionEvent.obtain(
                                time, time, MotionEvent.ACTION_DOWN, middleX, middleY, 0));
        getTestEnvironment().waitForIdleSync();
        mOnUiThread.requestImageRef(msg);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                boolean done = false;
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestImageRef(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(imgUrl, handler.mResultUrl);
    }

    @Test
    public void testDebugDump() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    mWebView.debugDump();
                });
    }

    @Test
    public void testGetHitTestResult() throws Throwable {
        final String anchor =
                "<p><a href=\"" + TestHtmlConstants.EXT_WEB_URL1 + "\">normal anchor</a></p>";
        final String blankAnchor = "<p><a href=\"\">blank anchor</a></p>";
        final String form =
                "<p><form><input type=\"text\" name=\"Test\"><br>"
                        + "<input type=\"submit\" value=\"Submit\"></form></p>";
        String phoneNo = "3106984000";
        final String tel = "<p><a href=\"tel:" + phoneNo + "\">Phone</a></p>";
        String email = "test@gmail.com";
        final String mailto = "<p><a href=\"mailto:" + email + "\">Email</a></p>";
        String location = "shanghai";
        final String geo = "<p><a href=\"geo:0,0?q=" + location + "\">Location</a></p>";

        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(
                "fake://home",
                "<html><body>"
                        + anchor
                        + blankAnchor
                        + form
                        + tel
                        + mailto
                        + geo
                        + "</body></html>",
                "text/html",
                "UTF-8",
                null);
        getTestEnvironment().waitForIdleSync();

        // anchor
        moveFocusDown();
        HitTestResult hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.SRC_ANCHOR_TYPE, hitTestResult.getType());
        assertEquals(TestHtmlConstants.EXT_WEB_URL1, hitTestResult.getExtra());

        // blank anchor
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.SRC_ANCHOR_TYPE, hitTestResult.getType());
        assertEquals("fake://home", hitTestResult.getExtra());

        // text field
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.EDIT_TEXT_TYPE, hitTestResult.getType());
        assertNull(hitTestResult.getExtra());

        // submit button
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.UNKNOWN_TYPE, hitTestResult.getType());
        assertNull(hitTestResult.getExtra());

        // phone number
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.PHONE_TYPE, hitTestResult.getType());
        assertEquals(phoneNo, hitTestResult.getExtra());

        // email
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.EMAIL_TYPE, hitTestResult.getType());
        assertEquals(email, hitTestResult.getExtra());

        // geo address
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.GEO_TYPE, hitTestResult.getType());
        assertEquals(location, hitTestResult.getExtra());
    }

    @Test
    public void testSetInitialScale() throws Throwable {
        final String p = "<p style=\"height:1000px;width:1000px\">Test setInitialScale.</p>";
        final float defaultScale = mContext.getResources().getDisplayMetrics().density;

        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "</body></html>", "text/html", null);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(0);
        // modify content to fool WebKit into re-loading
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "2" + "</body></html>", "text/html", null);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(50);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "3" + "</body></html>", "text/html", null);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return Math.abs(0.5 - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(0);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "4" + "</body></html>", "text/html", null);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();
    }

    @Test
    public void testClearHistory() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
                    String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
                    String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

                    mOnUiThread.loadUrlAndWaitForCompletion(url1);
                    pollingCheckWebBackForwardList(url1, 0, 1);

                    mOnUiThread.loadUrlAndWaitForCompletion(url2);
                    pollingCheckWebBackForwardList(url2, 1, 2);

                    mOnUiThread.loadUrlAndWaitForCompletion(url3);
                    pollingCheckWebBackForwardList(url3, 2, 3);

                    mWebView.clearHistory();

                    // only current URL is left after clearing
                    pollingCheckWebBackForwardList(url3, 0, 1);
                });
    }

    @Test
    public void testSaveAndRestoreState() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        WebkitUtils.onMainThreadSync(
                () -> {
                    assertNull(
                            "Should return null when there's nothing to save",
                            mWebView.saveState(new Bundle()));

                    String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
                    String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
                    String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

                    // make a history list
                    mOnUiThread.loadUrlAndWaitForCompletion(url1);
                    pollingCheckWebBackForwardList(url1, 0, 1);
                    mOnUiThread.loadUrlAndWaitForCompletion(url2);
                    pollingCheckWebBackForwardList(url2, 1, 2);
                    mOnUiThread.loadUrlAndWaitForCompletion(url3);
                    pollingCheckWebBackForwardList(url3, 2, 3);

                    // save the list
                    Bundle bundle = new Bundle();
                    WebBackForwardList saveList = mWebView.saveState(bundle);
                    assertNotNull(saveList);
                    assertEquals(3, saveList.getSize());
                    assertEquals(2, saveList.getCurrentIndex());
                    assertEquals(url1, saveList.getItemAtIndex(0).getUrl());
                    assertEquals(url2, saveList.getItemAtIndex(1).getUrl());
                    assertEquals(url3, saveList.getItemAtIndex(2).getUrl());

                    // change the content to a new "blank" web view without history
                    final WebView newWebView = new WebView(mContext);

                    WebBackForwardList copyListBeforeRestore = newWebView.copyBackForwardList();
                    assertNotNull(copyListBeforeRestore);
                    assertEquals(0, copyListBeforeRestore.getSize());

                    // restore the list
                    final WebBackForwardList restoreList = newWebView.restoreState(bundle);
                    assertNotNull(restoreList);
                    assertEquals(3, restoreList.getSize());
                    assertEquals(2, saveList.getCurrentIndex());

                    // wait for the list items to get inflated
                    new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
                        @Override
                        protected boolean check() {
                            return restoreList.getItemAtIndex(0).getUrl() != null
                                    && restoreList.getItemAtIndex(1).getUrl() != null
                                    && restoreList.getItemAtIndex(2).getUrl() != null;
                        }
                    }.run();
                    assertEquals(url1, restoreList.getItemAtIndex(0).getUrl());
                    assertEquals(url2, restoreList.getItemAtIndex(1).getUrl());
                    assertEquals(url3, restoreList.getItemAtIndex(2).getUrl());

                    WebBackForwardList copyListAfterRestore = newWebView.copyBackForwardList();
                    assertNotNull(copyListAfterRestore);
                    assertEquals(3, copyListAfterRestore.getSize());
                    assertEquals(2, copyListAfterRestore.getCurrentIndex());
                    assertEquals(url1, copyListAfterRestore.getItemAtIndex(0).getUrl());
                    assertEquals(url2, copyListAfterRestore.getItemAtIndex(1).getUrl());
                    assertEquals(url3, copyListAfterRestore.getItemAtIndex(2).getUrl());

                    newWebView.destroy();
                });
    }

    @Test
    public void testRequestChildRectangleOnScreen() throws Throwable {
        // It is needed to make test pass on some devices.
        mOnUiThread.setLayoutToMatchParent();

        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        final int dimension = 2 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p = "<p style=\"height:" + dimension + "px;width:" + dimension + "px\">&nbsp;</p>";
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body>" + p + "</body></html>", "text/html", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() >= dimension;
            }
        }.run();

        int origX = mOnUiThread.getScrollX();
        int origY = mOnUiThread.getScrollY();

        int half = dimension / 2;
        Rect rect = new Rect(half, half, half + 1, half + 1);
        assertTrue(mOnUiThread.requestChildRectangleOnScreen(mWebView, rect, true));
        assertThat(mOnUiThread.getScrollX(), greaterThan(origX));
        assertThat(mOnUiThread.getScrollY(), greaterThan(origY));
    }

    @Test
    public void testSetDownloadListener() throws Throwable {
        final SettableFuture<Void> downloadStartFuture = SettableFuture.create();
        final class MyDownloadListener implements DownloadListener {
            public String url;
            public String mimeType;
            public long contentLength;
            public String contentDisposition;

            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimetype,
                    long contentLength) {
                this.url = url;
                this.mimeType = mimetype;
                this.contentLength = contentLength;
                this.contentDisposition = contentDisposition;
                downloadStartFuture.set(null);
            }
        }

        final String mimeType = "application/octet-stream";
        final int length = 100;
        final MyDownloadListener listener = new MyDownloadListener();

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String url = mWebServer.getBinaryUrl(mimeType, length);

        // By default, WebView sends an intent to ask the system to
        // handle loading a new URL. We set WebViewClient as
        // WebViewClient.shouldOverrideUrlLoading() returns false, so
        // the WebView will load the new URL.
        mOnUiThread.setDownloadListener(listener);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body onload=\"window.location = \'" + url + "\'\"></body></html>",
                "text/html",
                null);
        // Wait for layout to complete before setting focus.
        getTestEnvironment().waitForIdleSync();

        WebkitUtils.waitForFuture(downloadStartFuture);
        assertEquals(url, listener.url);
        assertTrue(listener.contentDisposition.contains("test.bin"));
        assertEquals(length, listener.contentLength);
        assertEquals(mimeType, listener.mimeType);
    }

    @Test
    public void testSetLayoutParams() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(600, 800);
                    mWebView.setLayoutParams(params);
                    assertSame(params, mWebView.getLayoutParams());
                });
    }

    @Test
    public void testSetMapTrackballToArrowKeys() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    mWebView.setMapTrackballToArrowKeys(true);
                });
    }

    @Test
    public void testSetNetworkAvailable() throws Exception {
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        String url = mWebServer.getAssetUrl(TestHtmlConstants.NETWORK_STATE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("ONLINE", mOnUiThread.getTitle());

        mOnUiThread.setNetworkAvailable(false);

        // Wait for the DOM to receive notification of the network state change.
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals("OFFLINE");
            }
        }.run();

        mOnUiThread.setNetworkAvailable(true);

        // Wait for the DOM to receive notification of the network state change.
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals("ONLINE");
            }
        }.run();
    }

    @Test
    public void testSetWebChromeClient() throws Throwable {
        final SettableFuture<Void> future = SettableFuture.create();
        mOnUiThread.setWebChromeClient(
                new WaitForProgressClient(mOnUiThread) {
                    @Override
                    public void onProgressChanged(WebView view, int newProgress) {
                        super.onProgressChanged(view, newProgress);
                        future.set(null);
                    }
                });
        getTestEnvironment().waitForIdleSync();
        assertFalse(future.isDone());

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        getTestEnvironment().waitForIdleSync();

        WebkitUtils.waitForFuture(future);
    }

    @Test
    public void testPauseResumeTimers() throws Throwable {
        class Monitor {
            private boolean mIsUpdated;

            @JavascriptInterface
            public synchronized void update() {
                mIsUpdated = true;
                notify();
            }

            public synchronized boolean waitForUpdate() {
                while (!mIsUpdated) {
                    try {
                        // This is slightly flaky, as we can't guarantee that
                        // this is a sufficient time limit, but there's no way
                        // around this.
                        wait(1000);
                        if (!mIsUpdated) {
                            return false;
                        }
                    } catch (InterruptedException e) {
                    }
                }
                mIsUpdated = false;
                return true;
            }
        }

        final Monitor monitor = new Monitor();
        final String updateMonitorHtml =
                "<html>" + "<body onload=\"monitor.update();\"></body></html>";

        // Test that JavaScript is executed even with timers paused.
        WebkitUtils.onMainThreadSync(
                () -> {
                    mWebView.getSettings().setJavaScriptEnabled(true);
                    mWebView.addJavascriptInterface(monitor, "monitor");
                    mWebView.pauseTimers();
                    mOnUiThread.loadDataAndWaitForCompletion(updateMonitorHtml, "text/html", null);
                });
        assertTrue(monitor.waitForUpdate());

        // Start a timer and test that it does not fire.
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body onload='setTimeout(function(){monitor.update();},100)'>"
                        + "</body></html>",
                "text/html",
                null);
        assertFalse(monitor.waitForUpdate());

        // Resume timers and test that the timer fires.
        mOnUiThread.resumeTimers();
        assertTrue(monitor.waitForUpdate());
    }

    // verify query parameters can be passed correctly to android asset files
    @Test
    public void testAndroidAssetQueryParam() {
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        // test passing a parameter
        String fileUrl =
                TestHtmlConstants.getFileUrl(TestHtmlConstants.PARAM_ASSET_URL + "?val=SUCCESS");
        mOnUiThread.loadUrlAndWaitForCompletion(fileUrl);
        assertEquals("SUCCESS", mOnUiThread.getTitle());
    }

    // verify anchors work correctly for android asset files
    @Test
    public void testAndroidAssetAnchor() {
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        // test using an anchor
        String fileUrl =
                TestHtmlConstants.getFileUrl(TestHtmlConstants.ANCHOR_ASSET_URL + "#anchor");
        mOnUiThread.loadUrlAndWaitForCompletion(fileUrl);
        assertEquals("anchor", mOnUiThread.getTitle());
    }

    @Test
    public void testEvaluateJavascript() {
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        assertEquals("2", mOnUiThread.evaluateJavascriptSync("1+1"));

        assertEquals("9", mOnUiThread.evaluateJavascriptSync("1+1; 4+5"));

        final String EXPECTED_TITLE = "test";
        mOnUiThread.evaluateJavascript("document.title='" + EXPECTED_TITLE + "';", null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals(EXPECTED_TITLE);
            }
        }.run();
    }

    // Verify Print feature can create a PDF file with a correct preamble.
    @Test
    public void testPrinting() throws Throwable {
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head></head>" + "<body>foo</body></html>", "text/html", null);
        final PrintDocumentAdapter adapter = mOnUiThread.createPrintDocumentAdapter();
        printDocumentStart(adapter);
        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("foo", "bar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();
        final File file = mContext.getFileStreamPath(PRINTER_TEST_FILE);
        final ParcelFileDescriptor descriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("w"));
        final SettableFuture<Void> result = SettableFuture.create();
        printDocumentLayout(
                adapter,
                null,
                attributes,
                new LayoutResultCallback() {
                    // Called on UI thread
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        PageRange[] pageRanges = new PageRange[] {PageRange.ALL_PAGES};
                        savePrintedPage(adapter, descriptor, pageRanges, result);
                    }
                });
        try {
            WebkitUtils.waitForFuture(result);
            assertThat(file.length(), greaterThan(0L));
            FileInputStream in = new FileInputStream(file);
            byte[] b = new byte[PDF_PREAMBLE.length()];
            in.read(b);
            String preamble = new String(b);
            assertEquals(PDF_PREAMBLE, preamble);
        } finally {
            // close the descriptor, if not closed already.
            descriptor.close();
            file.delete();
        }
    }

    // Verify Print feature can create a PDF file with correct number of pages.
    @Test
    public void testPrintingPagesCount() throws Throwable {
        String content = "<html><head></head><body>";
        for (int i = 0; i < 500; ++i) {
            content += "<br />abcdefghijk<br />";
        }
        content += "</body></html>";
        mOnUiThread.loadDataAndWaitForCompletion(content, "text/html", null);
        final PrintDocumentAdapter adapter = mOnUiThread.createPrintDocumentAdapter();
        printDocumentStart(adapter);
        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("foo", "bar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();
        final File file = mContext.getFileStreamPath(PRINTER_TEST_FILE);
        final ParcelFileDescriptor descriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("w"));
        final SettableFuture<Void> result = SettableFuture.create();
        printDocumentLayout(
                adapter,
                null,
                attributes,
                new LayoutResultCallback() {
                    // Called on UI thread
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        PageRange[] pageRanges =
                                new PageRange[] {new PageRange(1, 1), new PageRange(4, 7)};
                        savePrintedPage(adapter, descriptor, pageRanges, result);
                    }
                });
        try {
            WebkitUtils.waitForFuture(result);
            assertThat(file.length(), greaterThan(0L));
            PdfRenderer renderer =
                    new PdfRenderer(
                            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY));
            assertEquals(5, renderer.getPageCount());
        } finally {
            descriptor.close();
            file.delete();
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testVisualStateCallbackCalled. Modifications to this test
     * should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testVisualStateCallbackCalled() throws Exception {
        // Check that the visual state callback is called correctly.
        final long kRequest = 100;

        mOnUiThread.loadUrl("about:blank");

        final SettableFuture<Long> visualStateFuture = SettableFuture.create();
        mOnUiThread.postVisualStateCallback(
                kRequest,
                new VisualStateCallback() {
                    public void onComplete(long requestId) {
                        visualStateFuture.set(requestId);
                    }
                });

        assertEquals(kRequest, (long) WebkitUtils.waitForFuture(visualStateFuture));
    }

    private static boolean setSafeBrowsingAllowlistSync(List<String> allowlist) {
        final SettableFuture<Boolean> safeBrowsingAllowlistFuture = SettableFuture.create();
        WebView.setSafeBrowsingWhitelist(
                allowlist,
                new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean success) {
                        safeBrowsingAllowlistFuture.set(success);
                    }
                });
        return WebkitUtils.waitForFuture(safeBrowsingAllowlistFuture);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testSetSafeBrowsingAllowlistWithMalformedList.
     * Modifications to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    @Test
    public void testSetSafeBrowsingAllowlistWithMalformedList() throws Exception {
        List allowlist = new ArrayList<String>();
        // Protocols are not supported in the allowlist
        allowlist.add("http://google.com");
        assertFalse("Malformed list entry should fail", setSafeBrowsingAllowlistSync(allowlist));
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testSetSafeBrowsingAllowlistWithValidList. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testSetSafeBrowsingAllowlistWithValidList() throws Exception {
        List allowlist = new ArrayList<String>();
        allowlist.add("safe-browsing");
        assertTrue("Valid allowlist should be successful", setSafeBrowsingAllowlistSync(allowlist));

        final SettableFuture<Void> pageFinishedFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        pageFinishedFuture.set(null);
                    }

                    @Override
                    public void onSafeBrowsingHit(
                            WebView view,
                            WebResourceRequest request,
                            int threatType,
                            SafeBrowsingResponse callback) {
                        pageFinishedFuture.setException(
                                new IllegalStateException(
                                        "Should not invoke onSafeBrowsingHit for "
                                                + request.getUrl()));
                    }
                });

        mOnUiThread.loadUrl("chrome://safe-browsing/match?type=malware");

        // Wait until page load has completed
        WebkitUtils.waitForFuture(pageFinishedFuture);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testGetWebViewClient. Modifications to this test should be
     * reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testGetWebViewClient() throws Exception {
        WebkitUtils.onMainThreadSync(
                () -> {
                    // getWebViewClient should return a default WebViewClient if it hasn't been set
                    // yet
                    WebView webView = new WebView(mContext);
                    WebViewClient client = webView.getWebViewClient();
                    assertNotNull(client);
                    assertTrue(client instanceof WebViewClient);

                    // getWebViewClient should return the client after it has been set
                    WebViewClient client2 = new WebViewClient();
                    assertNotSame(client, client2);
                    webView.setWebViewClient(client2);
                    assertSame(client2, webView.getWebViewClient());
                    webView.destroy();
                });
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testGetWebChromeClient. Modifications to this test should
     * be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testGetWebChromeClient() throws Exception {
        WebkitUtils.onMainThreadSync(
                () -> {
                    // getWebChromeClient should return null if the client hasn't been set yet
                    WebView webView = new WebView(mContext);
                    WebChromeClient client = webView.getWebChromeClient();
                    assertNull(client);

                    // getWebChromeClient should return the client after it has been set
                    WebChromeClient client2 = new WebChromeClient();
                    assertNotSame(client, client2);
                    webView.setWebChromeClient(client2);
                    assertSame(client2, webView.getWebChromeClient());
                    webView.destroy();
                });
    }

    @Test
    public void testSetCustomTextClassifier() throws Exception {
        class CustomTextClassifier implements TextClassifier {
            @Override
            public TextSelection suggestSelection(
                    CharSequence text, int startIndex, int endIndex, LocaleList defaultLocales) {
                return new TextSelection.Builder(0, 1).build();
            }

            @Override
            public TextClassification classifyText(
                    CharSequence text, int startIndex, int endIndex, LocaleList defaultLocales) {
                return new TextClassification.Builder().build();
            }
        }

        WebkitUtils.onMainThreadSync(
                () -> {
                    TextClassifier classifier = new CustomTextClassifier();
                    WebView webView = new WebView(mContext);
                    webView.setTextClassifier(classifier);
                    assertSame(webView.getTextClassifier(), classifier);
                    webView.destroy();
                });
    }

    private static class MockContext extends ContextWrapper {
        private boolean mGetApplicationContextWasCalled;

        public MockContext(Context context) {
            super(context);
        }

        public Context getApplicationContext() {
            mGetApplicationContextWasCalled = true;
            return super.getApplicationContext();
        }

        public boolean wasGetApplicationContextCalled() {
            return mGetApplicationContextWasCalled;
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testStartSafeBrowsingUseApplicationContext. Modifications
     * to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    @Test
    public void testStartSafeBrowsingUseApplicationContext() throws Exception {
        final MockContext ctx =
                new MockContext(
                        ApplicationProvider.getApplicationContext().getApplicationContext());
        final SettableFuture<Boolean> startSafeBrowsingFuture = SettableFuture.create();
        WebView.startSafeBrowsing(
                ctx,
                new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean value) {
                        startSafeBrowsingFuture.set(ctx.wasGetApplicationContextCalled());
                        return;
                    }
                });
        assertTrue(WebkitUtils.waitForFuture(startSafeBrowsingFuture));
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testStartSafeBrowsingWithNullCallbackDoesntCrash.
     * Modifications to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    @Test
    public void testStartSafeBrowsingWithNullCallbackDoesntCrash() throws Exception {
        WebView.startSafeBrowsing(
                ApplicationProvider.getApplicationContext().getApplicationContext(), null);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testStartSafeBrowsingInvokesCallback. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testStartSafeBrowsingInvokesCallback() throws Exception {
        final SettableFuture<Boolean> startSafeBrowsingFuture = SettableFuture.create();
        WebView.startSafeBrowsing(
                ApplicationProvider.getApplicationContext().getApplicationContext(),
                new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean value) {
                        startSafeBrowsingFuture.set(Looper.getMainLooper().isCurrentThread());
                        return;
                    }
                });
        assertTrue(WebkitUtils.waitForFuture(startSafeBrowsingFuture));
    }

    private void savePrintedPage(
            final PrintDocumentAdapter adapter,
            final ParcelFileDescriptor descriptor,
            final PageRange[] pageRanges,
            final SettableFuture<Void> result) {
        adapter.onWrite(
                pageRanges,
                descriptor,
                new CancellationSignal(),
                new WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        try {
                            descriptor.close();
                            result.set(null);
                        } catch (IOException ex) {
                            result.setException(ex);
                        }
                    }
                });
    }

    private void printDocumentStart(final PrintDocumentAdapter adapter) {
        WebkitUtils.onMainThreadSync(
                () -> {
                    adapter.onStart();
                });
    }

    private void printDocumentLayout(
            final PrintDocumentAdapter adapter,
            final PrintAttributes oldAttributes,
            final PrintAttributes newAttributes,
            final LayoutResultCallback layoutResultCallback) {
        WebkitUtils.onMainThreadSync(
                () -> {
                    adapter.onLayout(
                            oldAttributes,
                            newAttributes,
                            new CancellationSignal(),
                            layoutResultCallback,
                            null);
                });
    }

    private static class HrefCheckHandler extends Handler {
        private boolean mHadRecieved;

        private String mResultUrl;

        public HrefCheckHandler(Looper looper) {
            super(looper);
        }

        public boolean hasCalledHandleMessage() {
            return mHadRecieved;
        }

        public String getResultUrl() {
            return mResultUrl;
        }

        public void reset() {
            mResultUrl = null;
            mHadRecieved = false;
        }

        @Override
        public void handleMessage(Message msg) {
            mResultUrl = msg.getData().getString("url");
            mHadRecieved = true;
        }
    }

    private void moveFocusDown() throws Throwable {
        // send down key and wait for idle
        getTestEnvironment().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        // waiting for idle isn't always sufficient for the key to be fully processed
        Thread.sleep(500);
    }

    private void pollingCheckWebBackForwardList(
            final String currUrl, final int currIndex, final int size) {
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                WebBackForwardList list = mWebView.copyBackForwardList();
                return checkWebBackForwardList(list, currUrl, currIndex, size);
            }
        }.run();
    }

    private boolean checkWebBackForwardList(
            WebBackForwardList list, String currUrl, int currIndex, int size) {
        return (list != null)
                && (list.getSize() == size)
                && (list.getCurrentIndex() == currIndex)
                && list.getItemAtIndex(currIndex).getUrl().equals(currUrl);
    }

    private void assertGoBackOrForwardBySteps(boolean expected, int steps) {
        // skip if steps equals to 0
        if (steps == 0) return;

        int start = steps > 0 ? 1 : steps;
        int end = steps > 0 ? steps : -1;

        // check all the steps in the history
        for (int i = start; i <= end; i++) {
            assertEquals(expected, mWebView.canGoBackOrForward(i));

            // shortcut methods for one step
            if (i == 1) {
                assertEquals(expected, mWebView.canGoForward());
            } else if (i == -1) {
                assertEquals(expected, mWebView.canGoBack());
            }
        }
    }

    private boolean isPictureFilledWithColor(Picture picture, int color) {
        if (picture.getWidth() == 0 || picture.getHeight() == 0) return false;

        Bitmap bitmap =
                Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Config.ARGB_8888);
        picture.draw(new Canvas(bitmap));

        for (int i = 0; i < bitmap.getWidth(); i++) {
            for (int j = 0; j < bitmap.getHeight(); j++) {
                if (color != bitmap.getPixel(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Waits at least MIN_SCROLL_WAIT_MS for scrolling to start. Once started, scrolling is checked
     * every SCROLL_WAIT_INTERVAL_MS for changes. Once changes have stopped, the function exits. If
     * no scrolling has happened then the function exits after MIN_SCROLL_WAIT milliseconds.
     *
     * @param previousScrollY The Y scroll position prior to waiting.
     */
    private void waitForScrollingComplete(int previousScrollY) throws InterruptedException {
        int scrollY = previousScrollY;
        // wait at least MIN_SCROLL_WAIT for something to happen.
        long noChangeMinWait = SystemClock.uptimeMillis() + MIN_SCROLL_WAIT_MS;
        boolean scrollChanging = false;
        boolean scrollChanged = false;
        boolean minWaitExpired = false;
        while (scrollChanging || (!scrollChanged && !minWaitExpired)) {
            Thread.sleep(SCROLL_WAIT_INTERVAL_MS);
            int oldScrollY = scrollY;
            scrollY = mOnUiThread.getScrollY();
            scrollChanging = (scrollY != oldScrollY);
            scrollChanged = (scrollY != previousScrollY);
            minWaitExpired = (SystemClock.uptimeMillis() > noChangeMinWait);
        }
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewCompatTest#testGetSafeBrowsingPrivacyPolicyUrl. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testGetSafeBrowsingPrivacyPolicyUrl() throws Exception {
        assertNotNull(WebView.getSafeBrowsingPrivacyPolicyUrl());
        try {
            new URL(WebView.getSafeBrowsingPrivacyPolicyUrl().toString());
        } catch (MalformedURLException e) {
            fail("The privacy policy URL should be a well-formed URL");
        }
    }

    @Test
    public void testWebViewClassLoaderReturnsNonNull() {
        assertNotNull(WebView.getWebViewClassLoader());
    }
}
