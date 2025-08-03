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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdkSandboxWebViewZoomTest {

    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.WebViewZoomTest");

    @Test
    public void testZoomIn() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testZoomIn");
    }

    @Test
    public void testGetZoomControls() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testGetZoomControls");
    }

    @Test
    public void testInvokeZoomPicker() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testInvokeZoomPicker");
    }

    @Test
    public void testZoom_canNotZoomInPastMaximum() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testZoom_canNotZoomInPastMaximum");
    }

    @Test
    public void testZoom_canNotZoomOutPastMinimum() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testZoom_canNotZoomOutPastMinimum");
    }

    @Test
    public void testCanZoomWhileZoomSupportedFalse() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testCanZoomWhileZoomSupportedFalse");
    }

    @Test
    public void testZoomByPowerOfTwoIncrements() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testZoomByPowerOfTwoIncrements");
    }

    @Test
    public void testZoomByNonPowerOfTwoIncrements() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testZoomByNonPowerOfTwoIncrements");
    }

    @Test
    public void testScaleChangeCallbackMatchesGetScale() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testScaleChangeCallbackMatchesGetScale");
    }
}
