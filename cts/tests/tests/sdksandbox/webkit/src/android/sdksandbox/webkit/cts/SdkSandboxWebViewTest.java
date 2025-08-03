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

package android.sdksandbox.webkit.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class SdkSandboxWebViewTest {

    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.WebViewTest");

    @Test
    public void testConstructor() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testConstructor");
    }

    @Test
    public void testCreatingWebViewWithDeviceEncrpytionFails() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testCreatingWebViewWithDeviceEncrpytionFails");
    }

    @Test
    public void testCreatingWebViewWithMultipleEncryptionContext() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testCreatingWebViewWithMultipleEncryptionContext");
    }

    @Test
    public void testCreatingWebViewCreatesCookieSyncManager() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testCreatingWebViewCreatesCookieSyncManager");
    }

    @Test
    public void testFindAddress() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testFindAddress");
    }

    @Test
    public void testAccessHttpAuthUsernamePassword() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessHttpAuthUsernamePassword");
    }

    @Test
    public void testWebViewDatabaseAccessHttpAuthUsernamePassword() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testWebViewDatabaseAccessHttpAuthUsernamePassword");
    }

    @Test
    public void testScrollBarOverlay() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testScrollBarOverlay");
    }

    @Test
    public void testFlingScroll() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testFlingScroll");
    }

    @Test
    @Presubmit
    public void testLoadUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadUrl");
    }

    @Test
    public void testPostUrlWithNetworkUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPostUrlWithNetworkUrl");
    }

    @Test
    public void testAppInjectedXRequestedWithHeaderIsNotOverwritten() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testAppInjectedXRequestedWithHeaderIsNotOverwritten");
    }

    @Test
    public void testAppCanInjectHeadersViaImmutableMap() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAppCanInjectHeadersViaImmutableMap");
    }

    @Test
    public void testCanInjectHeaders() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testCanInjectHeaders");
    }

    @Test
    public void testGetVisibleTitleHeight() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetVisibleTitleHeight");
    }

    @Test
    public void testGetOriginalUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetOriginalUrl");
    }

    @Test
    public void testStopLoading() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testStopLoading");
    }

    @Test
    public void testGoBackAndForward() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGoBackAndForward");
    }

    @Test
    public void testAddJavascriptInterface() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAddJavascriptInterface");
    }

    @Test
    public void testAddJavascriptInterfaceNullObject() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAddJavascriptInterfaceNullObject");
    }

    @Test
    public void testRemoveJavascriptInterface() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRemoveJavascriptInterface");
    }

    @Test
    public void testUseRemovedJavascriptInterface() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testUseRemovedJavascriptInterface");
    }

    @Test
    public void testAddJavascriptInterfaceExceptions() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAddJavascriptInterfaceExceptions");
    }

    @Test
    public void testJavascriptInterfaceCustomPropertiesClearedOnReload() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testJavascriptInterfaceCustomPropertiesClearedOnReload");
    }

    @Test
    @MediumTest
    public void testJavascriptInterfaceForClientPopup() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testJavascriptInterfaceForClientPopup");
    }

    @Test
    @MediumTest
    public void testLoadDataWithBaseUrl_historyUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadDataWithBaseUrl_historyUrl");
    }

    @Test
    public void testLoadDataWithBaseUrl_nullHistoryUrlShowsAsAboutBlank() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_nullHistoryUrlShowsAsAboutBlank");
    }

    @Test
    public void testLoadDataWithBaseUrl_dataBaseUrlIgnoresHistoryUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_dataBaseUrlIgnoresHistoryUrl");
    }

    @Test
    public void testLoadDataWithBaseUrl_unencodedContentHttpBaseUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_unencodedContentHttpBaseUrl");
    }

    @Test
    public void testLoadDataWithBaseUrl_urlEncodedContentDataBaseUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_urlEncodedContentDataBaseUrl");
    }

    @Test
    public void testLoadDataWithBaseUrl_nullSafe() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadDataWithBaseUrl_nullSafe");
    }

    @Test
    public void testSaveWebArchive() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSaveWebArchive");
    }

    @Test
    public void testFindAll() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testFindAll");
    }

    @Test
    public void testFindNext() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testFindNext");
    }

    @Test
    public void testPageScroll() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPageScroll");
    }

    @Test
    public void testGetContentHeight() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetContentHeight");
    }

    @Test
    public void testPlatformNotifications() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPlatformNotifications");
    }

    @Test
    public void testAccessPluginList() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testAccessPluginList");
    }

    @Test
    public void testDestroy() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDestroy");
    }

    @Test
    public void testDebugDump() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDebugDump");
    }

    @Test
    public void testSetInitialScale() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetInitialScale");
    }

    @Test
    public void testRequestChildRectangleOnScreen() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRequestChildRectangleOnScreen");
    }

    @Test
    public void testSetLayoutParams() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetLayoutParams");
    }

    @Test
    public void testSetMapTrackballToArrowKeys() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetMapTrackballToArrowKeys");
    }

    @Test
    public void testPauseResumeTimers() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPauseResumeTimers");
    }

    @Test
    public void testEvaluateJavascript() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testEvaluateJavascript");
    }

    @Test
    public void testPrinting() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPrinting");
    }

    @Test
    public void testPrintingPagesCount() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testPrintingPagesCount");
    }

    @Test
    public void testVisualStateCallbackCalled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testVisualStateCallbackCalled");
    }

    @Test
    public void testSetSafeBrowsingAllowlistWithMalformedList() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetSafeBrowsingAllowlistWithMalformedList");
    }

    @Test
    public void testSetSafeBrowsingAllowlistWithValidList() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetSafeBrowsingAllowlistWithValidList");
    }

    @Test
    public void testGetWebViewClient() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetWebViewClient");
    }

    @Test
    public void testGetWebChromeClient() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetWebChromeClient");
    }

    @Test
    public void testSetCustomTextClassifier() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetCustomTextClassifier");
    }

    @Test
    public void testGetSafeBrowsingPrivacyPolicyUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetSafeBrowsingPrivacyPolicyUrl");
    }

    @Test
    public void testWebViewClassLoaderReturnsNonNull() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testWebViewClassLoaderReturnsNonNull");
    }

    @Test
    public void testCapturePicture() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testCapturePicture");
    }

    @Test
    public void testSetPictureListener() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetPictureListener");
    }

    @Test
    public void testLoadData() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testLoadData");
    }

    @Test
    public void testLoadDataWithBaseUrl_resolvesRelativeToBaseUrl() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_resolvesRelativeToBaseUrl");
    }

    @Test
    public void testLoadDataWithBaseUrl_javascriptCanAccessOrigin() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses(
                "testLoadDataWithBaseUrl_javascriptCanAccessOrigin");
    }

    @Test
    public void testDocumentHasImages() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testDocumentHasImages");
    }

    @Test
    public void testClearHistory() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testClearHistory");
    }

    @Test
    public void testSaveAndRestoreState() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSaveAndRestoreState");
    }

    @Test
    public void testSetDownloadListener() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetDownloadListener");
    }

    @Test
    public void testSetNetworkAvailable() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetNetworkAvailable");
    }

    @Test
    public void testSetWebChromeClient() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testSetWebChromeClient");
    }

    @Test
    public void testRequestFocusNodeHref() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRequestFocusNodeHref");
    }

    @Test
    public void testRequestImageRef() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testRequestImageRef");
    }

    @Test
    public void testGetHitTestResult() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetHitTestResult");
    }
}
