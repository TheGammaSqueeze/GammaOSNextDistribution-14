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

import android.graphics.Bitmap;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for the dark mode related tests.
 */
public class WebViewDarkModeTestBase {

    private WebViewOnUiThread mOnUiThread;
    private WebSettings mSettings;

    public void init(WebViewCtsActivity activity) {
        WebView webview = activity.getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
            mSettings = mOnUiThread.getSettings();
            mSettings.setJavaScriptEnabled(true);
        }
    }

    public void cleanup() {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    public void setWebViewSize(int width, int height) {
        // Set the webview size to 64x64
        WebkitUtils.onMainThreadSync(() -> {
            WebView webView = mOnUiThread.getWebView();
            ViewGroup.LayoutParams params = webView.getLayoutParams();
            params.height = height;
            params.width = width;
            webView.setLayoutParams(params);
        });
    }

    public WebViewOnUiThread getOnUiThread() {
        return mOnUiThread;
    }

    public WebSettings getSettings() {
        return mSettings;
    }

    private static int[] getBitmapPixels(Bitmap bitmap, int x, int y, int width, int height) {
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, x, y, width, height);
        return pixels;
    }

    public static Map<Integer, Integer> getBitmapHistogram(
            Bitmap bitmap, int x, int y, int width, int height) {
        HashMap<Integer, Integer> histogram = new HashMap();
        for (int pixel : getBitmapPixels(bitmap, x, y, width, height)) {
            histogram.put(pixel, histogram.getOrDefault(pixel, 0) + 1);
        }
        return histogram;
    }
}
