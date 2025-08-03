/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar.element;

import android.app.StatusBarManager;

/** Generic interface for CarSystemBar UI elements */
public interface CarSystemBarElement {
    /** Returns the class to be instantiated to control this element */
    Class<?> getElementControllerClass();

    /** Return the system bar disable flag for this element */
    @StatusBarManager.DisableFlags
    int getSystemBarDisableFlags();

    /** Return the system bar disable2 flag for this element */
    @StatusBarManager.Disable2Flags
    int getSystemBarDisable2Flags();

    /**
     * Return if this element is disabled by the
     * {@link android.app.ActivityManager.LOCK_TASK_MODE_LOCKED} system flag.
     */
    boolean disableForLockTaskModeLocked();
}
