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

package android.tools

object PlatformConsts {
    /**
     * The default Display id, which is the id of the primary display assuming there is one.
     *
     * Duplicated from [Display.DEFAULT_DISPLAY] because this class is used by JVM and KotlinJS
     */
    const val DEFAULT_DISPLAY = 0

    /**
     * Window type: an application window that serves as the "base" window of the overall
     * application
     *
     * Duplicated from [WindowManager.LayoutParams.TYPE_BASE_APPLICATION] because this class is used
     * by JVM and KotlinJS
     */
    const val TYPE_BASE_APPLICATION = 1

    /**
     * Window type: special application window that is displayed while the application is starting
     *
     * Duplicated from [WindowManager.LayoutParams.TYPE_APPLICATION_STARTING] because this class is
     * used by JVM and KotlinJS
     */
    const val TYPE_APPLICATION_STARTING = 3

    /**
     * Rotation constant: 0 degrees rotation (natural orientation)
     *
     * Duplicated from [Surface.ROTATION_0] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_0 = 0

    /**
     * Rotation constant: 90 degrees rotation.
     *
     * Duplicated from [Surface.ROTATION_90] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_90 = 1

    /**
     * Rotation constant: 180 degrees rotation.
     *
     * Duplicated from [Surface.ROTATION_180] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_180 = 2

    /**
     * Rotation constant: 270 degrees rotation.
     *
     * Duplicated from [Surface.ROTATION_270] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_270 = 3

    /**
     * Navigation bar mode constant: 3 button navigation.
     *
     * Duplicated from [WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY] because this
     * class is used by JVM and KotlinJS
     */
    const val MODE_GESTURAL = "com.android.internal.systemui.navbar.gestural"

    /**
     * Navigation bar mode : gestural navigation.
     *
     * Duplicated from [WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY] because this
     * class is used by JVM and KotlinJS
     */
    const val MODE_3BUTTON = "com.android.internal.systemui.navbar.threebutton"

    internal const val STATE_INITIALIZING = "INITIALIZING"
    const val STATE_RESUMED = "RESUMED"
    const val STATE_PAUSED = "PAUSED"
    const val STATE_STOPPED = "STOPPED"
    const val STATE_DESTROYED = "DESTROYED"
    internal const val APP_STATE_IDLE = "APP_STATE_IDLE"
    internal const val ACTIVITY_TYPE_UNDEFINED = 0
    internal const val ACTIVITY_TYPE_STANDARD = 1
    internal const val DEFAULT_MINIMAL_SPLIT_SCREEN_DISPLAY_SIZE_DP = 440
    internal const val ACTIVITY_TYPE_HOME = 2
    internal const val ACTIVITY_TYPE_RECENTS = 3
    internal const val WINDOWING_MODE_UNDEFINED = 0

    /** @see android.app.WindowConfiguration.WINDOWING_MODE_PINNED */
    internal const val WINDOWING_MODE_PINNED = 2

    /** @see android.view.WindowManager.LayoutParams */
    internal const val TYPE_NAVIGATION_BAR_PANEL = 2024

    // Default minimal size of resizable task, used if none is set explicitly.
    // Must be kept in sync with 'default_minimal_size_resizable_task'
    // dimen from frameworks/base.
    internal const val DEFAULT_RESIZABLE_TASK_SIZE_DP = 220

    /** From [android.util.DisplayMetrics] */
    internal const val DENSITY_DEFAULT = 160f

    /** From [com.android.systemui.shared.recents.utilities.Utilities] */
    internal const val TABLET_MIN_DPS = 600f

    /**
     * From {@see android.view.WindowManager.FLAG_FULLSCREEN}.
     *
     * This class is shared between JVM and JS (Winscope) and cannot access Android internals
     */
    internal const val FLAG_FULLSCREEN = 0x00000400
    internal const val WINDOW_TYPE_STARTING = 1
    internal const val WINDOW_TYPE_EXITING = 2
    internal const val WINDOW_TYPE_DEBUGGER = 3

    internal const val STARTING_WINDOW_PREFIX = "Starting "
    internal const val DEBUGGER_WINDOW_PREFIX = "Waiting For Debugger: "

    const val SPLIT_SCREEN_TRANSITION_HANDLER = "com.android.wm.shell.splitscreen.StageCoordinator"
}
