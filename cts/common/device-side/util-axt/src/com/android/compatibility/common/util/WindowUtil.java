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

package com.android.compatibility.common.util;

import android.app.Activity;
import android.view.Window;

public class WindowUtil {
    /**
     * The timeout in milliseconds to wait for window focus in
     * {@link #waitForFocus(Window)} and {@link #waitForFocus(Activity)}.
     */
    public static final long WINDOW_FOCUS_TIMEOUT_MILLIS = 10_000L;

    /**
     * Waits until {@code activity}'s main window has focus, timing out after
     * {@link #WINDOW_FOCUS_TIMEOUT_MILLIS}.
     * @param activity The Activity whose main window should gain focus.
     */
    public static void waitForFocus(Activity activity) {
        PollingCheck.waitFor(WINDOW_FOCUS_TIMEOUT_MILLIS,
                activity::hasWindowFocus);
    }

    /**
     * Waits until {@code window} has focus, timing out after {@link #WINDOW_FOCUS_TIMEOUT_MILLIS}.
     * @param window The Window that should gain focus.
     */
    public static void waitForFocus(Window window) {
        PollingCheck.waitFor(WINDOW_FOCUS_TIMEOUT_MILLIS,
                window.getDecorView()::hasWindowFocus);
    }
}
