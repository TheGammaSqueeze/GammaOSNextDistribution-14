/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.homeScreenOrBust;

import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test invoking the various {@link AccessibilityService#performGlobalAction(int)}} actions.
 */
@Presubmit
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
public class AccessibilityGlobalActionsTest {

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private static UiDevice sUiDevice;

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        sUiDevice = UiDevice.getInstance(sInstrumentation);
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
        // Start on a clean home screen with any system dialogs removed.
        homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @After
    public void tearDown() throws Exception {
        // The majority of system actions involve System UI requests that both:
        //   - Can take a few seconds to take effect on certain device types.
        //   - Perform behavior that depends on the specific SystemUI implementation of the device,
        //     making it untestable to a device-agnostic CTS test like this.
        // So instead of waiting for any specific condition, we repeatedly try to get to the home
        // screen to clean up before starting the next test.
        sUiDevice.pressHome();
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionBack() {
        assertTrue(sUiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionHome() {
        assertTrue(sUiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
    }

    @LargeTest
    @Test
    public void testPerformGlobalActionRecents() {
        // Not all devices support GLOBAL_ACTION_RECENTS, but there is no current feature flag for
        // this. Our best hope is to test that this does throw a runtime error.
        sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_RECENTS);
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionNotifications() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionQuickSettings() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionPowerDialog() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_POWER_DIALOG));
    }

    @LargeTest
    @Test
    public void testPerformActionScreenshot() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT));
        // Ideally should verify that we actually have a screenshot, but it's also possible
        // for the screenshot to fail.
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionDpadUp() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DPAD_UP));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionDpadDown() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DPAD_DOWN));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionDpadLeft() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DPAD_LEFT));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionDpadRight() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT));
    }

    @MediumTest
    @Test
    public void testPerformGlobalActionDpadCenter() {
        assertTrue(sUiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DPAD_CENTER));
    }
}
