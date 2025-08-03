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

package com.android.cts.webkit;

import android.app.admin.DeviceAdminReceiver;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WebViewDeviceSideMultipleProfileTest {
    // Profile owner component.
    public static class BasicAdminReceiver extends DeviceAdminReceiver {}

    @Rule
    public ActivityScenarioRule<WebViewStartupCtsActivity> mActivityRule =
            new ActivityScenarioRule<>(WebViewStartupCtsActivity.class);

    private WebViewStartupCtsActivity mActivity;

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
        });
    }

    @Test
    @UiThreadTest
    public void testCreateWebViewAndNavigate() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        mActivity.createAndAttachWebView();
        WebView webView = mActivity.getWebView();
        Assert.assertNotNull(webView);

        WebViewSyncLoader syncLoader = new WebViewSyncLoader(webView);
        syncLoader.loadUrlAndWaitForCompletion("about:blank");
        syncLoader.detach();
    }


}
