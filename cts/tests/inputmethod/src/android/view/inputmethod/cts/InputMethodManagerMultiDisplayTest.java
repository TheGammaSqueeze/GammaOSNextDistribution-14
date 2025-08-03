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

package android.view.inputmethod.cts;

import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.view.Display;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot query the installed IMEs")
public class InputMethodManagerMultiDisplayTest extends MultiDisplayTestBase {
    private static final String MOCK_IME_ID =
            "com.android.cts.mockimewithsubtypes/.MockImeWithSubtypes";
    private static final String MOCK_IME_SUBTYPE_LABEL = "CTS Subtype 1 Test String";
    private static final String SETTINGS_ACTIVITY_PACKAGE = "com.android.settings";

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private InputMethodManager mImManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS));

        mImManager = mContext.getSystemService(InputMethodManager.class);

        enableAndSetIme();
    }

    @After
    public void tearDown() {
        runShellCommandOrThrow("ime reset");
        launchHomeActivity();
    }

    @Test
    public void testShowInputMethodAndSubtypeEnablerOnSingleDisplay() throws RemoteException {
        assumeFalse(isCar());
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        uiDevice.setOrientationNatural();

        mImManager.showInputMethodAndSubtypeEnabler(MOCK_IME_ID);
        // Check if new activity was started with subtype settings
        assertThat(uiDevice.wait(Until.hasObject(By.text(MOCK_IME_SUBTYPE_LABEL)),
                TIMEOUT)).isTrue();
    }

    @Test
    public void testShowInputMethodAndSubtypeEnablerOnNonDefaultDisplay() {
        assumeFalse(isCar());
        assumeTrue(supportsMultiDisplay());

        try (MultiDisplayTestBase.VirtualDisplaySession session =
                     new MultiDisplayTestBase.VirtualDisplaySession()) {

            // Set up a simulated display.
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
            waitAndAssertActivityStateOnDisplay(testActivity.getComponentName(), STATE_RESUMED,
                    dc.mId, "Test Activity launched on external display must be resumed");

            // Focus on virtual display, otherwise UI automator cannot detect objects
            tapOnDisplayCenter(dc.mId);

            waitAndAssertTopResumedActivity(testActivity.getComponentName(),
                    dc.mId, "Test Activity launched on external display must be on top");

            // Open settings screen for subtypes from the non-default / currently active screen
            final InputMethodManager im = testActivity.getSystemService(InputMethodManager.class);
            im.showInputMethodAndSubtypeEnabler(MOCK_IME_ID);
            // Verify that settings screen is opened on the second/non-default display
            // TODO(b/274604301): Use UiAutomator to verify the given subtype settings
            // started on the simulated display.
            mWmState.waitForWithAmState(state -> ComponentName.unflattenFromString(
                            state.getResumedActivityOnDisplay(dc.mId)).getPackageName().equals(
                            SETTINGS_ACTIVITY_PACKAGE),
                    "Settings screen must be launched on display where it was started from");
        }
    }

    private void enableAndSetIme() {
        runShellCommandOrThrow("ime enable " + InputMethodManagerMultiDisplayTest.MOCK_IME_ID);
        runShellCommandOrThrow("ime set " + InputMethodManagerMultiDisplayTest.MOCK_IME_ID);
    }

}
