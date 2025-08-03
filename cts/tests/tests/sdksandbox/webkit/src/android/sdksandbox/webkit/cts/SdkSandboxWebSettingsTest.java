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

package android.sdksandbox.webkit.cts;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdkSandboxWebSettingsTest {
    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.WebSettingsTest");

    @Test
    public void testUserAgentString_default() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testUserAgentString_default");
    }

    @Test
    public void testUserAgentStringTest() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testUserAgentStringTest");
    }

    @Test
    public void testAccessUserAgentString() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessUserAgentString");
    }

    @Test
    public void testAccessCacheMode_defaultValue() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessCacheMode_defaultValue");
    }

    @Test
    public void testAccessCacheMode_cacheElseNetwork() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessCacheMode_cacheElseNetwork");
    }

    @Test
    public void testAccessCacheMode_noCache() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessCacheMode_noCache");
    }

    @Test
    public void testAccessCacheMode_cacheOnly() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessCacheMode_cacheOnly");
    }

    @Test
    public void testAccessCursiveFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessCursiveFontFamily");
    }

    @Test
    public void testAccessFantasyFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessFantasyFontFamily");
    }

    @Test
    public void testAccessFixedFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessFixedFontFamily");
    }

    @Test
    public void testAccessSansSerifFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessSansSerifFontFamily");
    }

    @Test
    public void testAccessSerifFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessSerifFontFamily");
    }

    @Test
    public void testAccessStandardFontFamily() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessStandardFontFamily");
    }

    @Test
    public void testAccessDefaultFontSize() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessDefaultFontSize");
    }

    @Test
    public void testAccessDefaultFixedFontSize() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessDefaultFixedFontSize");
    }

    @Test
    public void testAccessDefaultTextEncodingName() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessDefaultTextEncodingName");
    }

    @Test
    public void testAccessJavaScriptCanOpenWindowsAutomatically() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testAccessJavaScriptCanOpenWindowsAutomatically");
    }

    @Test
    public void testAccessJavaScriptEnabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessJavaScriptEnabled");
    }

    @Test
    public void testAccessLayoutAlgorithm() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessLayoutAlgorithm");
    }

    @Test
    public void testAccessMinimumFontSize() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessMinimumFontSize");
    }

    @Test
    public void testAccessMinimumLogicalFontSize() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessMinimumLogicalFontSize");
    }

    @Test
    public void testAccessPluginsEnabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessPluginsEnabled");
    }

    @Test
    public void testOffscreenPreRaster() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOffscreenPreRaster");
    }

    @Test
    public void testAccessPluginsPath() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessPluginsPath");
    }

    @Test
    public void testAccessTextSize() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessTextSize");
    }

    @Test
    public void testAccessUseDoubleTree() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessUseDoubleTree");
    }

    @Test
    public void testAccessUseWideViewPort() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessUseWideViewPort");
    }

    @Test
    public void testSetNeedInitialFocus() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetNeedInitialFocus");
    }

    @Test
    public void testSetRenderPriority() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetRenderPriority");
    }

    @Test
    public void testAccessSupportMultipleWindows() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessSupportMultipleWindows");
    }

    @Test
    public void testAccessSupportZoom() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessSupportZoom");
    }

    @Test
    public void testAccessBuiltInZoomControls() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessBuiltInZoomControls");
    }

    @Test
    public void testAppCacheDisabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAppCacheDisabled");
    }

    @Test
    public void testAppCacheEnabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAppCacheEnabled");
    }

    @Test
    public void testDatabaseDisabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDatabaseDisabled");
    }

    @Test
    public void testDisabledActionModeMenuItems() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDisabledActionModeMenuItems");
    }

    @Test
    public void testLoadsImagesAutomatically_default() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadsImagesAutomatically_default");
    }

    @Test
    public void testLoadsImagesAutomatically_httpImagesLoaded() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadsImagesAutomatically_httpImagesLoaded");
    }

    @Test
    public void testLoadsImagesAutomatically_dataUriImagesLoaded() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadsImagesAutomatically_dataUriImagesLoaded");
    }

    @Test
    public void testLoadsImagesAutomatically_blockLoadingImages() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadsImagesAutomatically_blockLoadingImages");
    }

    @Test
    public void testLoadsImagesAutomatically_loadImagesWithoutReload() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadsImagesAutomatically_loadImagesWithoutReload");
    }

    @Test
    public void testBlockNetworkImage() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testBlockNetworkImage");
    }

    @Test
    public void testBlockNetworkLoads() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testBlockNetworkLoads");
    }

    @Test
    public void testIframesWhenAccessFromFileURLsDisabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testIframesWhenAccessFromFileURLsDisabled");
    }

    @Test
    public void testXHRWhenAccessFromFileURLsEnabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testXHRWhenAccessFromFileURLsEnabled");
    }

    @Test
    public void testXHRWhenAccessFromFileURLsDisabled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testXHRWhenAccessFromFileURLsDisabled");
    }

    @Test
    public void testAllowMixedMode() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAllowMixedMode");
    }

    @Test
    public void testEnableSafeBrowsing() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testEnableSafeBrowsing");
    }
}
