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

package com.android.imsserviceentitlement;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.android.imsserviceentitlement.R;

import androidx.fragment.app.Fragment;

/** A fragment of WebView to render emergency address web portal */
public class WfcQnsWebPortalFragment extends Fragment {
  private static final String TAG = "IMSSE-WfcQnsWebPortalFragment";

  private static final String URL_KEY = "URL_KEY";

  /** Public static constructor */
  public static WfcQnsWebPortalFragment newInstance(String url) {
    WfcQnsWebPortalFragment frag = new WfcQnsWebPortalFragment();

    Bundle args = new Bundle();
    args.putString(URL_KEY, url);
    frag.setArguments(args);

    return frag;
  }

  @SuppressLint("SetJavaScriptEnabled") // only trusted URLs are loaded
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_webview, container, false);

    Bundle arguments = getArguments();
    String url = arguments.getString(URL_KEY);

    ProgressBar spinner = (ProgressBar) v.findViewById(R.id.loadingbar);
    WebView webView = (WebView) v.findViewById(R.id.webview);
    webView.setWebViewClient(
        new WebViewClient() {
          private boolean hideLoader = false;

          @Override
          public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "shouldOverrideUrlLoading()");
            return false; // Let WebView handle redirected URL
          }

          @Override
          public void onPageStarted(WebView webview, String url, Bitmap favicon) {
            // Webview will be invisible for first time only
            Log.d(TAG, "onPageStarted()");
            if (!hideLoader) {
              webview.setVisibility(WebView.INVISIBLE);
              Log.d(TAG, "onPageStarted() setVisibility(WebView.INVISIBLE)");
            }
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "onPageFinished()");
            hideLoader = true;
            spinner.setVisibility(View.GONE);
            view.setVisibility(WebView.VISIBLE);
            super.onPageFinished(view, url);
          }
        });

    webView.addJavascriptInterface(new JsInterface(), JsInterface.OBJECT_NAME);

    WebSettings settings = webView.getSettings();
    settings.setDomStorageEnabled(true);
    settings.setJavaScriptEnabled(true);

    webView.loadUrl(url);

    return v;
  }

  // JS interface required by Rogers web portal.
  private class JsInterface {
    /**
     * Name of the JS controller object.
     *
     * <p>It's hard-coded in the JS; DO NOT change unless requested by Rogers.
     */
    public static final String OBJECT_NAME = "WiFiCallingWebViewController";

    /**
     * Finish the activity when the cancel button clicked.
     *
     * <p>The method name is hard-coded in the JS; DO NOT change unless requested by Rogers.
     */
    @JavascriptInterface
    public void cancelButtonClicked() {
      Log.d(TAG, "cancelButtonClicked()");
      final Activity activity = WfcQnsWebPortalFragment.this.getActivity();
      if (activity != null) {
        activity.setResult(Activity.RESULT_CANCELED);
        activity.finish();
      }
    }

    /**
     * This method is invoked in JS but no implementation required. So define it to avoid JS
     * failure.
     *
     * <p>The method name is hard-coded in the JS; DO NOT change unless requested by Rogers.
     */
    @JavascriptInterface
    public void cancelButtonPressed() {
      Log.d(TAG, "cancelButtonPressed()");
    }

    /**
     * This method is invoked in JS but no implementation required. So define it to avoid JS
     * failure.
     *
     * <p>The method name is hard-coded in the JS; DO NOT change unless requested by Rogers.
     */
    @JavascriptInterface
    public void phoneServicesAccountStatusChanged() {
      Log.d(TAG, "phoneServicesAccountStatusChanged()");
      // No-op
    }

    /**
     * Finish the activity when onCloseWebView() is called.
     *
     * <p>The method name is hard-coded in the JS; DO NOT change unless requested by Bell.
     */
    @JavascriptInterface
    @SuppressWarnings("checkstyle:MethodName")
    public void CloseWebView() {
      Log.d(TAG, "CloseWebView()");
      final Activity activity = WfcQnsWebPortalFragment.this.getActivity();
      if (activity != null) {
        activity.setResult(Activity.RESULT_CANCELED);
        activity.finish();
      }
    }
  }
}
