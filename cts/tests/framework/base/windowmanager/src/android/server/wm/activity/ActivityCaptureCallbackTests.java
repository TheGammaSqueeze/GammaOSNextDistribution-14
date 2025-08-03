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

package android.server.wm.activity;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Ensure Activity screencapture callback is invoked as expected.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceWindow:ActivityCaptureCallbackTests
 */
@Presubmit
@ApiTest(apis = {"android.app.Activity#registerScreenCaptureCallback",
        "android.app.Activity#unregisterScreenCaptureCallback"})
public class ActivityCaptureCallbackTests extends WindowManagerTestBase {
    private PrimaryActivity mPrimaryActivity;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPrimaryActivity = startActivity(PrimaryActivity.class, DEFAULT_DISPLAY);
    }

    @After
    public void tearDown() throws Exception {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    /** Test a registered Activity callback invocation. */
    @Test
    public void testScreencaptureInvokeCallback() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        mPrimaryActivity.waitAndAssertCallbackInvokedOnActivity();
    }

    /** Test multi-window activities, both callbacks are invoked. */
    @Test
    public void testScreencaptureInvokeCallbackOnAllVisibleActivities() {
        assumeTrue(supportsMultiDisplay());

        final WindowManagerState.DisplayContent newDisplay =
                createManagedExternalDisplaySession().createVirtualDisplay();
        final SecondaryActivity secondaryActivity =
                startActivity(SecondaryActivity.class, newDisplay.mId, /* hasFocus */ false);
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        mWm.notifyScreenshotListeners(newDisplay.mId);
        mPrimaryActivity.waitAndAssertCallbackInvokedOnActivity();
        secondaryActivity.waitAndAssertCallbackInvokedOnActivity();
    }

    /** Test screenshotting only one display. */
    @Test
    public void testScreencaptureInvokeCallbackOnOneDisplay() {
        assumeTrue(supportsMultiDisplay());

        final WindowManagerState.DisplayContent newDisplay =
                createManagedExternalDisplaySession().createVirtualDisplay();
        final SecondaryActivity secondaryActivity =
                startActivity(SecondaryActivity.class, newDisplay.mId, /* hasFocus */ false);
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        mPrimaryActivity.waitAndAssertCallbackInvokedOnActivity();
        secondaryActivity.waitAndAssertCallbackNotInvoked();
    }
    /** Test multi-window activities, only registered callback is invoked. */
    @Test
    public void testScreencaptureInvokeCallbackOnRegisteredVisibleActivities() {
        assumeTrue(supportsMultiDisplay());

        mPrimaryActivity.unregisterScreencaptureCallback();
        final WindowManagerState.DisplayContent newDisplay =
                createManagedExternalDisplaySession().createVirtualDisplay();
        final SecondaryActivity secondaryActivity =
                startActivity(SecondaryActivity.class, newDisplay.mId, /* hasFocus */ false);
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        mWm.notifyScreenshotListeners(newDisplay.mId);
        mPrimaryActivity.waitAndAssertCallbackNotInvoked();
        secondaryActivity.waitAndAssertCallbackInvokedOnActivity();
    }

    /** Test only the top activity callback is invoked */
    @Test
    public void testScreencaptureInvokeCallbackOnVisibleOnly() {
        final SecondaryActivity topActivity =
                startActivity(SecondaryActivity.class, DEFAULT_DISPLAY);
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        topActivity.waitAndAssertCallbackInvokedOnActivity();
        mPrimaryActivity.waitAndAssertCallbackNotInvoked();
    }

    /** Test unregister callback */
    @Test
    public void testScreencaptureUnregisterCallback() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mPrimaryActivity.unregisterScreencaptureCallback();
        mWm.notifyScreenshotListeners(DEFAULT_DISPLAY);
        mPrimaryActivity.waitAndAssertCallbackNotInvoked();
    }

    public static class PrimaryActivity extends ScreencaptureCallbackActivity {}

    public static class SecondaryActivity extends ScreencaptureCallbackActivity {}

    private static class ScreencaptureCallbackActivity extends FocusableActivity {
        private static final long TIMEOUT_SCREENCAPTURE_CALLBACK_INVOKED = 1000; // milliseconds

        private CountDownLatch mCountDownLatch = new CountDownLatch(1);

        final ScreenCaptureCallback mCallback =
                new ScreenCaptureCallback() {

                    @Override
                    public void onScreenCaptured() {
                        mCountDownLatch.countDown();
                    }
                };

        void waitAndAssertCallbackInvokedOnActivity() {
            try {
                boolean invoked = mCountDownLatch.await(
                        TIMEOUT_SCREENCAPTURE_CALLBACK_INVOKED, TimeUnit.MILLISECONDS);
                assertTrue(invoked);
            } catch (InterruptedException e) {
                // This shouldn't happen
            }
        }

        void waitAndAssertCallbackNotInvoked() {
            try {
                boolean invoked = mCountDownLatch.await(
                        TIMEOUT_SCREENCAPTURE_CALLBACK_INVOKED, TimeUnit.MILLISECONDS);
                assertFalse(invoked);
            } catch (InterruptedException e) {
                // This shouldn't happen
            }
        }

        public void unregisterScreencaptureCallback() {
            unregisterScreenCaptureCallback(mCallback);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            registerScreenCaptureCallback(Executors.newSingleThreadExecutor(), mCallback);
        }
    }
}
