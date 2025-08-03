/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.sdksandboxclient;

import android.app.Activity;
import android.app.sdksandbox.interfaces.IMediateeSdkApi;
import android.os.Bundle;
import android.os.RemoteException;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class AppWebViewActivity extends Activity {

    private JsInterface mInterface;
    private WebView mWebView;
    private static final String SANDBOXED_SDK_BINDER = "com.android.sdksandboxclient.SANDBOXED_SDK";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_app_webview);
        mInterface =
                new JsInterface(
                        IMediateeSdkApi.Stub.asInterface(
                                getIntent().getIBinderExtra(SANDBOXED_SDK_BINDER)));
        startWebview();
    }

    private void startWebview() {
        mWebView = findViewById(R.id.app_webview);
        initializeSettings(mWebView.getSettings());
        loadDataInWebview(
                "<body><h1 style=\"font-size:50px;\">"
                        + "Touch the screen to check Javascript bridge functionality!"
                        + "</h1></body>");
        mWebView.addJavascriptInterface(mInterface, "interface");
        mWebView.setOnTouchListener(
                (v, event) -> {
                    loadDataInWebview(
                            "<body onload=\"var res = window.interface.getString();"
                                    + "document.getElementById('Result').innerHTML=res\">"
                                    + "<h1 style=\"font-size:50px;\"id=\"Result\">Result here></h1>"
                                    + "</body>");
                    return true;
                });
    }

    private void initializeSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);
    }

    /** Loads some basic HTML in the Webview. */
    private void loadDataInWebview(String data) {
        mWebView.loadData("<html><head></head>" + data + "</html>", "text/html", null);
    }

    private static class JsInterface {
        IMediateeSdkApi mApi;

        private JsInterface(IMediateeSdkApi api) {
            mApi = api;
        }

        @JavascriptInterface
        @SuppressWarnings("UnusedMethod")
        public String getString() {
            try {
                return mApi.getMessage();
            } catch (RemoteException e) {
                return e.getMessage();
            }
        }
    }
}
