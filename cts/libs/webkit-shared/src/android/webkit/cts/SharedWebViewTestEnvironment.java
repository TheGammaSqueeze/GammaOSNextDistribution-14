/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import org.apache.http.util.EncodingUtils;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.X509TrustManager;

/**
 * This class contains all the environmental variables that need to be configured for WebView tests
 * to either run inside the SDK Runtime or within an Activity.
 */
public final class SharedWebViewTestEnvironment {
    @Nullable private final Context mContext;
    @Nullable private final WebView mWebView;
    @Nullable private final FrameLayout mRootLayout;
    private final IHostAppInvoker mHostAppInvoker;

    private SharedWebViewTestEnvironment(
            Context context,
            WebView webView,
            IHostAppInvoker hostAppInvoker,
            FrameLayout rootLayout) {
        mContext = context;
        mWebView = webView;
        mHostAppInvoker = hostAppInvoker;
        mRootLayout = rootLayout;
    }

    @Nullable
    public Context getContext() {
        return mContext;
    }

    @Nullable
    public WebView getWebView() {
        return mWebView;
    }

    /**
     * Some tests require adding a content view to the root view at runtime. This method mimics the
     * behaviour of Activity.addContentView()
     */
    @Nullable
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        view.setLayoutParams(params);
        mRootLayout.addView(view);
    }

    /**
     * Apache Utils can't be statically linked so we can't use them directly inside the SDK Runtime.
     * Use this method instead of EncodingUtils.getBytes.
     */
    public byte[] getEncodingBytes(String data, String charset) {
        return ExceptionWrapper.unwrap(() -> {
            return mHostAppInvoker.getEncodingBytes(data, charset);
        });
    }

    /** Invokes waitForIdleSync on the {@link Instrumentation} in the activity. */
    public void waitForIdleSync() {
        ExceptionWrapper.unwrap(() -> {
            mHostAppInvoker.waitForIdleSync();
        });
    }

    /** Invokes sendKeyDownUpSync on the {@link Instrumentation} in the activity. */
    public void sendKeyDownUpSync(int keyCode) {
        ExceptionWrapper.unwrap(() -> {
            mHostAppInvoker.sendKeyDownUpSync(keyCode);
        });
    }

    /** Invokes sendPointerSync on the {@link Instrumentation} in the activity. */
    public void sendPointerSync(MotionEvent event) {
        ExceptionWrapper.unwrap(() -> {
            mHostAppInvoker.sendPointerSync(event);
        });
    }

    /** Returns a web server that can be used for web based testing. */
    public SharedSdkWebServer getWebServer() {
        return ExceptionWrapper.unwrap(() -> {
            return new SharedSdkWebServer(mHostAppInvoker.getWebServer());
        });
    }

    /** Returns a web server that has been started and can be used for web based testing. */
    public SharedSdkWebServer getSetupWebServer(@SslMode int sslMode) {
        return getSetupWebServer(sslMode, null, 0, 0);
    }

    /** Returns a web server that has been started and can be used for web based testing. */
    public SharedSdkWebServer getSetupWebServer(
            @SslMode int sslMode, @Nullable byte[] acceptedIssuerDer, int keyResId, int certResId) {
        SharedSdkWebServer webServer = getWebServer();
        webServer.start(sslMode, acceptedIssuerDer, keyResId, certResId);
        return webServer;
    }

    /**
     * Use this builder to create a {@link SharedWebViewTestEnvironment}. The {@link
     * SharedWebViewTestEnvironment} can not be built directly.
     */
    public static final class Builder {
        private Context mContext;
        private WebView mWebView;

        private FrameLayout mRootLayout;
        private IHostAppInvoker mHostAppInvoker;

        /** Provide a {@link Context} the tests should use for your environment. */
        public Builder setContext(@NonNull Context context) {
            mContext = context;
            return this;
        }

        /** Provide a {@link WebView} the tests should use for your environment. */
        public Builder setWebView(@NonNull WebView webView) {
            mWebView = webView;
            return this;
        }

        /**
         * Provide a {@link IHostAppInvoker} the tests should use for your environment.
         *
         * <p>This can be created with {@link createHostAppInvoker}.
         *
         * <p>Note: This is required.
         */
        public Builder setHostAppInvoker(@NonNull IHostAppInvoker hostAppInvoker) {
            mHostAppInvoker = hostAppInvoker;
            return this;
        }

        /** Provide a {@link FrameLayout} the tests should use for your environment. */
        public Builder setRootLayout(@NonNull FrameLayout rootLayout) {
            mRootLayout = rootLayout;
            return this;
        }

        /** Build a new SharedWebViewTestEnvironment. */
        public SharedWebViewTestEnvironment build() {
            if (mHostAppInvoker == null) {
                throw new NullPointerException("The host app invoker is required");
            }
            return new SharedWebViewTestEnvironment(
                    mContext, mWebView, mHostAppInvoker, mRootLayout);
        }
    }

    /**
     * UiAutomation sends events at device level which lets us get around issues with sending
     * instrumented events to the SDK Runtime but we don't want this for the regular tests. If
     * something like a dialog pops up while an input event is being sent, the instrumentation would
     * treat that as an issue while the UiAutomation input event would just send it through.
     *
     * <p>So by default, we disable this use and only use it in the SDK Sandbox.
     *
     * <p>This API is used for regular activity based tests.
     */
    public static IHostAppInvoker.Stub createHostAppInvoker(Context applicationContext) {
        return createHostAppInvoker(applicationContext, false);
    }
    /**
     * This will generate a new {@link IHostAppInvoker} binder node. This should be called from
     * wherever the activity exists for test cases.
     */
    public static IHostAppInvoker.Stub createHostAppInvoker(
            Context applicationContext, boolean allowUiAutomation) {
        return new IHostAppInvoker.Stub() {
            private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
            private UiAutomation mUiAutomation;

            public void waitForIdleSync() {
                ExceptionWrapper.wrap(() -> {
                    mInstrumentation.waitForIdleSync();
                });
            }

            public void sendKeyDownUpSync(int keyCode) {
                ExceptionWrapper.wrap(() -> {
                    mInstrumentation.sendKeyDownUpSync(keyCode);
                });
            }

            public void sendPointerSync(MotionEvent event) {
                ExceptionWrapper.wrap(() -> {
                    if (allowUiAutomation) {
                        sendPointerSyncWithUiAutomation(event);
                    } else {
                        sendPointerSyncWithInstrumentation(event);
                    }
                });
            }

            public byte[] getEncodingBytes(String data, String charset) {
                return ExceptionWrapper.wrap(() -> {
                    return EncodingUtils.getBytes(data, charset);
                });
            }

            public IWebServer getWebServer() {
                return new IWebServer.Stub() {
                    private CtsTestServer mWebServer;

                    public void start(
                            @SslMode int sslMode,
                            @Nullable byte[] acceptedIssuerDer,
                            int keyResId,
                            int certResId) {
                        ExceptionWrapper.wrap(() -> {
                            assertNull(mWebServer);
                            final X509Certificate[] acceptedIssuerCerts;
                            if (acceptedIssuerDer != null) {
                                CertificateFactory certFactory =
                                        CertificateFactory.getInstance("X.509");
                                acceptedIssuerCerts = new X509Certificate[] {
                                    (X509Certificate) certFactory.generateCertificate(
                                            new ByteArrayInputStream(acceptedIssuerDer))
                                };
                            } else {
                                acceptedIssuerCerts = null;
                            }
                            X509TrustManager trustManager =
                                    new CtsTestServer.CtsTrustManager() {
                                        @Override
                                        public X509Certificate[] getAcceptedIssuers() {
                                            return acceptedIssuerCerts;
                                        }
                                    };
                            mWebServer = new CtsTestServer(
                                    applicationContext, sslMode, trustManager, keyResId, certResId);
                        });
                    }

                    public void shutdown() {
                        if (mWebServer == null) {
                            return;
                        }
                        ExceptionWrapper.wrap(() -> {
                            ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
                            ThreadPolicy tmpPolicy =
                                    new ThreadPolicy.Builder(oldPolicy)
                                            .permitNetwork()
                                            .build();
                            StrictMode.setThreadPolicy(tmpPolicy);
                            mWebServer.shutdown();
                            mWebServer = null;
                            StrictMode.setThreadPolicy(oldPolicy);
                        });
                    }

                    public void resetRequestState() {
                        ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            mWebServer.resetRequestState();
                        });
                    }

                    public String setResponse(
                            String path, String responseString, List<HttpHeader> responseHeaders) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.setResponse(
                                    path, responseString, HttpHeader.asPairList(responseHeaders));
                        });
                    }

                    public String getAbsoluteUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getAbsoluteUrl(path);
                        });
                    }

                    public String getUserAgentUrl() {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getUserAgentUrl();
                        });
                    }

                    public String getDelayedAssetUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getDelayedAssetUrl(path);
                        });
                    }

                    public String getRedirectingAssetUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getRedirectingAssetUrl(path);
                        });
                    }

                    public String getAssetUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getAssetUrl(path);
                        });
                    }

                    public String getAuthAssetUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getAuthAssetUrl(path);
                        });
                    }

                    public String getBinaryUrl(String mimeType, int contentLength) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getBinaryUrl(mimeType, contentLength);
                        });
                    }

                    public String getAppCacheUrl() {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getAppCacheUrl();
                        });
                    }

                    public int getRequestCount() {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getRequestCount();
                        });
                    }

                    public int getRequestCountWithPath(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getRequestCount(path);
                        });
                    }

                    public boolean wasResourceRequested(String url) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.wasResourceRequested(url);
                        });
                    }

                    public HttpRequest getLastRequest(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return toHttpRequest(path, mWebServer.getLastRequest(path));
                        });
                    }

                    public HttpRequest getLastAssetRequest(String url) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return toHttpRequest(url, mWebServer.getLastAssetRequest(url));
                        });
                    }

                    public String getCookieUrl(String path) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getCookieUrl(path);
                        });
                    }

                    public String getSetCookieUrl(
                            String path, String key, String value, String attributes) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getSetCookieUrl(path, key, value, attributes);
                        });
                    }

                    public String getLinkedScriptUrl(String path, String url) {
                        return ExceptionWrapper.wrap(() -> {
                            assertNotNull("The WebServer needs to be started", mWebServer);
                            return mWebServer.getLinkedScriptUrl(path, url);
                        });
                    }

                    private HttpRequest toHttpRequest(
                            String url, org.apache.http.HttpRequest apacheRequest) {
                        if (apacheRequest == null) {
                            return null;
                        }

                        return new HttpRequest(url, apacheRequest);
                    }
                };
            }

            private void sendPointerSyncWithInstrumentation(MotionEvent event) {
                mInstrumentation.sendPointerSync(event);
            }

            private void sendPointerSyncWithUiAutomation(MotionEvent event) {
                if (mUiAutomation == null) {
                    mUiAutomation = mInstrumentation.getUiAutomation();

                    if (mUiAutomation == null) {
                        fail("Could not retrieve UI automation");
                    }
                }

                if (!mUiAutomation.injectInputEvent(event, true)) {
                    fail("Could not inject motion event");
                }
            }
        };
    }
}
