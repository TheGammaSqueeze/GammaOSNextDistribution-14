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

package android.accessibilityservice.cts.utils;

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.DisplayUtils.getStatusBarHeight;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.TimeoutException;

/**
 * Utility class for launching and positioning new windows given an already-launched Activity.
 * This is useful when testing interactions where multiple AccessibilityWindowInfos are on screen,
 * and an activity has already been launched.
 */
public class WindowCreationUtils {
    public static final CharSequence TOP_WINDOW_TITLE =
            "android.accessibilityservice.cts.TOP_WINDOW_TITLE";

    /**
     * Returns the layout specifications for a test window of type TYPE_APPLICATION_PANEL that lies
     * at the top of the screen.
     * @param instrumentation the test instrumentation.
     * @param activity the activity to specify the layout params against.
     * @param windowTitle the title of the test window.
     * @return the layout params.
     */
    public static WindowManager.LayoutParams layoutParamsForWindowOnTop(
            Instrumentation instrumentation, Activity activity, CharSequence windowTitle) {
        return layoutParamsForWindowOnTop(instrumentation, activity, windowTitle,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Returns the layout specifications for a test window of type TYPE_APPLICATION_PANEL that lies
     * at the top of the screen.
     * @param instrumentation the test instrumentation.
     * @param activity the activity to specify the layout params against.
     * @param windowTitle the title of the test window.
     * @return the layout params.
     */
    public static WindowManager.LayoutParams layoutParamsForWindowOnTop(
            Instrumentation instrumentation, Activity activity, CharSequence windowTitle,
            int width, int height) {
        final WindowManager.LayoutParams params =
                layoutParamsForTestWindow(instrumentation, activity, width, height);
        params.gravity = Gravity.TOP;
        params.setTitle(windowTitle);
        instrumentation.runOnMainSync(() -> {
            params.y = getStatusBarHeight(activity);
        });
        return params;
    }

    /**
     * Returns the layout params for a test window of type TYPE_APPLICATION_PANEL.
     * @param instrumentation the test instrumentation.
     * @param activity the activity whose window token will be used for the test window.
     * @return the layout params.
     */
    public static WindowManager.LayoutParams layoutParamsForTestWindow(
            Instrumentation instrumentation, Activity activity) {
        return layoutParamsForTestWindow(instrumentation, activity,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Returns the layout params for a test window of type TYPE_APPLICATION_PANEL.
     * @param instrumentation the test instrumentation.
     * @param activity the activity whose window token will be used for the test window.
     * @param width the width of the window.
     * @param height the height of the window.
     * @return the layout params.
     */
    public static WindowManager.LayoutParams layoutParamsForTestWindow(
            Instrumentation instrumentation, Activity activity, int width, int height) {
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = width;
        params.height = height;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        instrumentation.runOnMainSync(() -> {
            params.token = activity.getWindow().getDecorView().getWindowToken();
        });
        return params;
    }

    /**
     * Adds a new window of type TYPE_APPLICATION_PANEL.
     *
     * <p> Note: Pair with removeWindowAndWaitForEvent to avoid a leaked window. </p>
     *
     * @param uiAutomation the test automation.
     * @param instrumentation the test instrumentation.
     * @param activity the activity whose window manager will add the window.
     * @param view the test window to add.
     * @param params the test window layout params.
     * @param filter the filter to determine when the test window has been added.
     * @throws TimeoutException
     */
    public static void addWindowAndWaitForEvent(UiAutomation uiAutomation,
            Instrumentation instrumentation, Activity activity, View view,
            WindowManager.LayoutParams params,
            UiAutomation.AccessibilityEventFilter filter)
            throws TimeoutException {
        uiAutomation.executeAndWaitForEvent(() -> instrumentation.runOnMainSync(
                () -> activity.getWindowManager().addView(view, params)), filter,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
    }

    /**
     * Removes a window.
     *
     * @param uiAutomation the test automation.
     * @param instrumentation the test instrumentation.
     * @param activity the activity whose window manager will remove the window.
     * @param view the test window to remove.
     * @throws TimeoutException
     */
    public static void removeWindow(UiAutomation uiAutomation, Instrumentation instrumentation,
            Activity activity, View view) throws TimeoutException {
        uiAutomation.executeAndWaitForEvent(() -> instrumentation.runOnMainSync(
                () -> activity.getWindowManager().removeView(view)),
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_REMOVED),
                AsyncUtils.DEFAULT_TIMEOUT_MS);
    }
}
