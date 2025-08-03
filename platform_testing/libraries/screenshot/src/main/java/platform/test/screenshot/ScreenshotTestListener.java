/*
 * Copyright 2023 The Android Open Source Project
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

package platform.test.screenshot;

import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.internal.runner.listener.InstrumentationRunListener;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Result;

/** A test listener for cleaning up after all screenshot tests are done. */
public class ScreenshotTestListener extends InstrumentationRunListener {

    private static final String TAG = "ScreenshotTestListener";

    @Override
    public void testRunFinished(Result result) throws Exception {
        // Skip cleaning up if we run Robolectric tests.
        if (Build.FINGERPRINT.contains("robolectric")) {
            return;
        }

        // Reset the density and display size.
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        wm.clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, UserHandle.myUserId());
        wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);

        // Reset the dark/light theme.
        UiModeManager uiModeManager =
                (UiModeManager)
                        (InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getSystemService(Context.UI_MODE_SERVICE));
        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);

        InstrumentationRegistry.getInstrumentation().resetInTouchMode();

        // Unfreeze locked rotation
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .setRotation(UiAutomation.ROTATION_UNFREEZE);
    }
}
