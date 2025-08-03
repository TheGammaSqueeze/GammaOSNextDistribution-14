/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.display;

import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_DRAWN_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION;
import static android.view.WindowManager.LayoutParams.TYPE_SEARCH_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import android.content.Context;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import org.junit.Test;

/**
 * Tests that verify the behavior of window context policy
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:WindowContextPolicyTests
 */
@Presubmit
public class WindowContextPolicyTests extends WindowContextTestBase {

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateWindowContextWithNoDisplayContext() {
        mContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWindowContextWithNullDisplay() {
        final Context displayContext = createDisplayContext(Display.DEFAULT_DISPLAY);
        displayContext.createWindowContext(
                null /* display */, TYPE_APPLICATION_OVERLAY, null /* options */);
    }

    @Test
    public void testWindowContextWithDisplayOnNonUiContext() {
        createAllowSystemAlertWindowAppOpSession();
        final Display display = mDm.getDisplay(Display.DEFAULT_DISPLAY);
        mContext.createWindowContext(display, TYPE_APPLICATION_OVERLAY, null /* options */);
    }

    @Test
    public void testCreateMultipleWindowContextsWithoutView() {
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        for (int i = 0; i < 10; i++) {
            createWindowContext(display.mId);
        }
    }

    @Test
    public void testWindowContextWithAllPublicTypes() {
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        final int[] allPublicWindowTypes = new int[] {
                TYPE_BASE_APPLICATION, TYPE_APPLICATION, TYPE_APPLICATION_STARTING,
                TYPE_DRAWN_APPLICATION, TYPE_APPLICATION_PANEL, TYPE_APPLICATION_MEDIA,
                TYPE_APPLICATION_SUB_PANEL, TYPE_APPLICATION_ATTACHED_DIALOG,
                TYPE_STATUS_BAR, TYPE_SEARCH_BAR, TYPE_PHONE, TYPE_SYSTEM_ALERT,
                TYPE_TOAST, TYPE_SYSTEM_OVERLAY, TYPE_PRIORITY_PHONE,
                TYPE_SYSTEM_DIALOG, TYPE_KEYGUARD_DIALOG, TYPE_SYSTEM_ERROR, TYPE_INPUT_METHOD,
                TYPE_INPUT_METHOD_DIALOG, TYPE_WALLPAPER, TYPE_PRIVATE_PRESENTATION,
                TYPE_ACCESSIBILITY_OVERLAY, TYPE_APPLICATION_OVERLAY
        };

        for (int windowType : allPublicWindowTypes) {
            createWindowContext(display.mId, windowType);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWindowContextAddMismatchedWindowType() {
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        windowContext.getSystemService(WindowManager.class)
                .addView(new View(windowContext), new WindowManager.LayoutParams(TYPE_PHONE));
    }
}
