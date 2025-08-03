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

package com.android.devicelockcontroller.activities;

import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Activity to show help articles. The url for the article is provided as an extra param.
 */
public final class HelpActivity extends AppCompatActivity {
    private static final String TAG = HelpActivity.class.getSimpleName();
    public static final String EXTRA_URL_PARAM = "URL";

    private WebView mWebView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help_activity);

        mWebView = findViewById(R.id.webview);
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            LogUtil.e(TAG, "No extras present in the launch intent");
            finish();
            return;
        }

        String url = extras.getString(EXTRA_URL_PARAM);
        if (url == null) {
            LogUtil.e(TAG, "URL param missing in intent extras");
            finish();
            return;
        }
        mWebView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view,
                            WebResourceRequest request) {
                        return false;
                    }
                });
        mWebView.loadUrl(url);
    }

    @VisibleForTesting
    WebView getWebView() {
        return mWebView;
    }
}
