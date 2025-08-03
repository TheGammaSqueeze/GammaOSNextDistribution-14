/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import static android.autofillservice.cts.testcore.Timeouts.WEBVIEW_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.R;
import android.autofillservice.cts.testcore.UiBot;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.autofill.VirtualViewFillInfo;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.RetryableException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebViewActivity extends AbstractWebViewActivity {

    private static final String TAG = "WebViewActivity";
    private static final String FAKE_URL = "https://" + FAKE_DOMAIN + ":666/login.html";
    static final String ID_WEBVIEW = "webview";

    public static final String ID_OUTSIDE1 = "outside1";
    public static final String ID_OUTSIDE2 = "outside2";

    public EditText mOutside1;
    public EditText mOutside2;

    private LinearLayout mParent;
    private LinearLayout mOutsideContainer1;
    private LinearLayout mOutsideContainer2;

    private UiObject2 mUsernameLabel;
    private UiObject2 mUsernameInput;
    private UiObject2 mPasswordLabel;
    private UiObject2 mPasswordInput;
    private UiObject2 mLoginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview_activity);

        mParent = findViewById(R.id.parent);
        mOutsideContainer1 = findViewById(R.id.outsideContainer1);
        mOutsideContainer2 = findViewById(R.id.outsideContainer2);
        mOutside1 = findViewById(R.id.outside1);
        mOutside2 = findViewById(R.id.outside2);
    }

    public MyWebView loadWebView(UiBot uiBot) throws Exception {
        return loadWebView(uiBot, false);
    }

    public MyWebView loadWebView(UiBot uiBot, boolean usingAppContext) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        syncRunOnUiThread(() -> {
            final Context context = usingAppContext ? getApplicationContext() : this;
            mWebView = new MyWebView(context);
            mParent.addView(mWebView);
            mWebView.setWebViewClient(new WebViewClient() {
                // WebView does not set the WebDomain on file:// requests, so we need to use an
                // https:// request and intercept it to provide the real data.
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view,
                        WebResourceRequest request) {
                    final String url = request.getUrl().toString();
                    if (!url.equals(FAKE_URL)) {
                        Log.d(TAG, "Ignoring " + url);
                        return super.shouldInterceptRequest(view, request);
                    }

                    final String rawPath = request.getUrl().getPath()
                            .substring(1); // Remove leading /
                    Log.d(TAG, "Converting " + url + " to " + rawPath);
                    // NOTE: cannot use try-with-resources because it would close the stream before
                    // WebView uses it.
                    try {
                        return new WebResourceResponse("text/html", "utf-8",
                                getAssets().open(rawPath));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Error opening " + rawPath, e);
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    Log.v(TAG, "onPageFinished(): " + url);
                    latch.countDown();
                }
            });
            mWebView.loadUrl(FAKE_URL);
        });

        // Wait until it's loaded.
        if (!latch.await(WEBVIEW_TIMEOUT.ms(), TimeUnit.MILLISECONDS)) {
            throw new RetryableException(WEBVIEW_TIMEOUT, "WebView not loaded");
        }

        // Validation check to make sure autofill was enabled when the WebView was created
        assertThat(mWebView.isAutofillEnabled()).isTrue();

        // WebView builds its accessibility tree asynchronously and only after being queried the
        // first time, so we should first find the WebView and query some of its properties,
        // wait for its accessibility tree to be populated (by blocking until a known element
        // appears), then cache the objects for further use.

        // NOTE: we cannot search by resourceId because WebView does not set them...

        // Wait for known element...
        mUsernameLabel = uiBot.assertShownByText("Username: ", WEBVIEW_TIMEOUT);
        // ...then cache the others
        mUsernameInput = getInput(uiBot, mUsernameLabel);
        mPasswordLabel = uiBot.findRightAwayByText("Password: ");
        mPasswordInput = getInput(uiBot, mPasswordLabel);
        mLoginButton = uiBot.findRightAwayByText("Login");

        return mWebView;
    }

    public void loadOutsideViews() {
        syncRunOnUiThread(() -> {
            mOutsideContainer1.setVisibility(View.VISIBLE);
            mOutsideContainer2.setVisibility(View.VISIBLE);
        });
    }

    public UiObject2 getUsernameLabel() throws Exception {
        return mUsernameLabel;
    }

    public UiObject2 getPasswordLabel() throws Exception {
        return mPasswordLabel;
    }

    public UiObject2 getUsernameInput() throws Exception {
        return mUsernameInput;
    }

    public UiObject2 getPasswordInput() throws Exception {
        return mPasswordInput;
    }

    public UiObject2 getLoginButton() throws Exception {
        return mLoginButton;
    }

    @Override
    public void clearFocus() {
        syncRunOnUiThread(() -> mParent.requestFocus());
    }

    public void notifyViewReady(String[] hints) throws Exception {
        VirtualViewFillInfo info = null;
        if (hints != null) {
            info = new VirtualViewFillInfo.Builder()
                    .setAutofillHints(hints)
                    .build();
        }
        SparseArray<VirtualViewFillInfo> viewInfo = new SparseArray<>();
        viewInfo.put(65536, info);
        getAutofillManager().notifyVirtualViewsReady(mWebView, viewInfo);
    }

    /*
        To notify two views are ready.
     */
    public void notifyViewReady(String[] hints1, String[] hints2) throws Exception {
        VirtualViewFillInfo info1 = null;
        if (hints1 != null) {
            info1 = new VirtualViewFillInfo.Builder()
                .setAutofillHints(hints1)
                .build();
        }
        VirtualViewFillInfo info2 = null;
        if (hints2 != null) {
            info2 = new VirtualViewFillInfo.Builder()
                .setAutofillHints(hints2)
                .build();
        }
        SparseArray<VirtualViewFillInfo> viewInfo = new SparseArray<>();
        viewInfo.put(65536, info1);
        viewInfo.put(65537, info2);
        getAutofillManager().notifyVirtualViewsReady(mWebView, viewInfo);
    }

    public void notifyViewReadyWithEmptyInfo() throws Exception {
        getAutofillManager().notifyVirtualViewsReady(mWebView, new SparseArray<>());
    }

    public void notifyViewReadyWithNullInfo() throws Exception {
        getAutofillManager().notifyVirtualViewsReady(mWebView, null);
    }
}
