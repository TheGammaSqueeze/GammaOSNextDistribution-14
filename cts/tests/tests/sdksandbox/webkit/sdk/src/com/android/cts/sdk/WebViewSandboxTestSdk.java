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

package com.android.cts.sdk;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.testutils.testscenario.ISdkSandboxTestExecutor;
import android.app.sdksandbox.testutils.testscenario.SdkSandboxTestScenarioRunner;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.webkit.cts.IHostAppInvoker;
import android.webkit.cts.SharedWebViewTest;
import android.webkit.cts.SharedWebViewTestEnvironment;
import android.webkit.cts.WebkitUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;

public class WebViewSandboxTestSdk extends SdkSandboxTestScenarioRunner {
    private static final String TAG = WebViewSandboxTestSdk.class.getName();

    private @Nullable SharedWebViewTest mTestInstance;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        try {
            Bundle setupParams = params.getBundle(ISdkSandboxTestExecutor.TEST_SETUP_PARAMS);
            if (setupParams != null) {
                String webViewTestClassName =
                        setupParams.getString(SharedWebViewTest.WEB_VIEW_TEST_CLASS_NAME);
                mTestInstance = (SharedWebViewTest) Class.forName(webViewTestClassName)
                        .newInstance();
                setTestInstance(mTestInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return super.onLoadSdk(params);
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        WebView webView = new WebView(getContext());
        FrameLayout rootLayout = wrapWebViewInLayout(webView);

        if (mTestInstance != null) {
            SharedWebViewTestEnvironment testEnvironment =
                    new SharedWebViewTestEnvironment.Builder()
                            .setContext(getContext())
                            .setWebView(webView)
                            .setHostAppInvoker(
                                    IHostAppInvoker.Stub.asInterface(getCustomInterface()))
                            .setRootLayout(rootLayout)
                            .build();

            mTestInstance.setTestEnvironment(testEnvironment);
        }

        return rootLayout;
    }

    // TODO(b/327028536): temporarily removed @Override as this method is
    // not available on this branch.
    // @Override
    public void cleanUpOnTestFinish() {
        // Enforcing UI thread cleanup after Binder thread execution to match test origin
        // and prevent threading issues.
        WebkitUtils.onMainThreadSync(() -> {
            WebView webView = mTestInstance.getTestEnvironment().getWebView();
            ViewParent webviewParent = webView.getParent();
            if (webviewParent instanceof ViewGroup) {
                ((ViewGroup) webviewParent).removeView(webView);
            }
        webView.destroy();
        });
    }

    private FrameLayout wrapWebViewInLayout(WebView webView) {
        // Some tests add content views the root view of the activity which
        // is a FrameLayout, hence adding a FrameLayout as a root here as well
        FrameLayout rootLayout = new FrameLayout(getContext());
        rootLayout.setLayoutParams(
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Some tests expect the WebView to have a parent so making the parent
        // a linear layout the same as the regular webkit tests.
        LinearLayout webviewParent = new LinearLayout(getContext());
        webviewParent.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        webviewParent.setOrientation(LinearLayout.VERTICAL);

        webView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        webviewParent.addView(webView);
        rootLayout.addView(webviewParent);

        return rootLayout;
    }
}
