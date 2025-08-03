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

package android.view.inputmethod.cts;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.isInputMethodPickerShown;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SecurityTest;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.view.Display;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot rely on ACTION_CLOSE_SYSTEM_DIALOGS")
public class InputMethodPickerTest extends MultiDisplayTestBase {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private InputMethodManager mImManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS));

        mImManager = mContext.getSystemService(InputMethodManager.class);
        closeSystemDialogsAndWait();
    }

    @After
    public void tearDown() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS)) {
            return;
        }
        closeSystemDialogsAndWait();
    }

    @SecurityTest(minPatchLevel = "unknown")
    @Test
    public void testInputMethodPicker_hidesUntrustedOverlays() throws Exception {
        TestActivity testActivity = TestActivity.startSync(activity -> {
            final View view = new View(activity);
            view.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            return view;
        });

        // Test setup: Show overlay and verify that it worked.
        getInstrumentation().runOnMainSync(testActivity::showOverlayWindow);
        mWmState.waitAndAssertWindowSurfaceShown(TestActivity.OVERLAY_WINDOW_NAME, true);

        // Test setup: Show the IME picker and verify that it worked.
        getInstrumentation().runOnMainSync(mImManager::showInputMethodPicker);
        waitOnMainUntil(() -> isInputMethodPickerShown(mImManager), TIMEOUT,
                "Test setup failed: InputMethod picker should be shown");

        // Actual Test: Make sure the IME picker hides app overlays.
        mWmState.waitAndAssertWindowSurfaceShown(TestActivity.OVERLAY_WINDOW_NAME, false);
    }

    /**
     * Test if the IME picker dialog remain to be visible when popup the non-focusable overlay
     * window with {@link android.view.WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM} flag.
     *
     * <p>Regression test for Bug 236101545.</p>
     */
    @Test
    public void testShowInputMethodPicker_noDismissWhenOverlayPopup() throws Exception {
        TestActivity testActivity = TestActivity.startSync(activity -> {
            final View view = new View(activity);
            view.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            return view;
        });

        // Test the IME picker dialog won't be dismissed when the overlay popup in parallel.
        mImManager.showInputMethodPicker();
        waitOnMainUntil(() -> isInputMethodPickerShown(mImManager), TIMEOUT,
                "InputMethod picker should be shown");
        getInstrumentation().runOnMainSync(() ->
                testActivity.showOverlayWindow(true /* imeFocusable */));
        SystemClock.sleep(500);
        assertTrue(getOnMainSync(() -> isInputMethodPickerShown(mImManager)));
    }

    @Test
    public void testShowImePickerOnExternalDisplay() throws Exception {
        assumeTrue(supportsMultiDisplay());

        try (MultiDisplayTestBase.VirtualDisplaySession session =
                new MultiDisplayTestBase.VirtualDisplaySession()) {
            // Setup a simulated display.
            WindowManagerState.DisplayContent dc = session.setSimulateDisplay(true).createDisplay();
            Display simulatedDisplay = mContext.getSystemService(DisplayManager.class)
                    .getDisplay(dc.mId);

            // Launch a test activity on the simulated display.
            TestActivity testActivity = new TestActivity.Starter().withDisplayId(dc.mId)
                    .startSync(activity -> {
                        final View view = new View(activity);
                        view.setLayoutParams(
                                new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
                        return view;
                    }, TestActivity.class);
            waitAndAssertActivityStateOnDisplay(testActivity.getComponentName(),
                    STATE_RESUMED, dc.mId, "Activity launched on external display must be resumed");

            // Verify IME picker will be shown when using InputMethodManager instance from
            // the display context.
            final Context displayContext = getInstrumentation().getTargetContext()
                    .createDisplayContext(simulatedDisplay);
            final InputMethodManager im = displayContext.getSystemService(InputMethodManager.class);
            im.showInputMethodPicker();
            waitOnMainUntil(() -> isInputMethodPickerShown(im), TIMEOUT,
                    "Test failed: InputMethod picker should be shown");
            mWmState.waitAndAssertImePickerShownOnDisplay(dc.mId,
                    "Test failed: InputMethod picker should be shown on display #" + dc.mId);
        }
    }

    private void closeSystemDialogsAndWait() throws Exception {
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !isInputMethodPickerShown(mImManager), TIMEOUT,
                "Test assertion failed: InputMethod picker should be closed but isn't");
    }
}
