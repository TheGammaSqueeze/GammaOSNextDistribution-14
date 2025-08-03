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

package com.android.sdksandboxcode_webview;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.app.sdksandbox.interfaces.IWebViewSdkApi;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;


public class SandboxedSdkWebViewProvider extends SandboxedSdkProvider {

    private WebView mWebView = null;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        IWebViewSdkApi.Stub webviewProxy =
                new IWebViewSdkApi.Stub() {
                    public void loadUrl(String url) {
                        sHandler.post(() -> mWebView.loadUrl(url));
                    }

                    public void destroy() {
                        sHandler.post(() -> mWebView.destroy());
                    }
                };
        return new SandboxedSdk(webviewProxy);
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        mWebView = new WebView(windowContext);
        initializeSettings(mWebView.getSettings());
        mWebView.loadUrl("https://www.google.com/");
        return mWebView;
    }

    private void initializeSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);

        settings.setGeolocationEnabled(true);
        settings.setSupportZoom(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Default layout behavior for chrome on android.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
    }

}
