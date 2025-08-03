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

package android.systemui.tv.cts

import android.content.ComponentName

private const val pkg = "android.systemui.cts.tv.pip"

object Components {

    @JvmStatic
    fun ComponentName.activityName(): String = flattenToShortString()

    @JvmStatic
    fun ComponentName.windowName(): String = flattenToString()

    @JvmField
    val PIP_ACTIVITY: ComponentName = ComponentName.createRelative(pkg, ".PipTestActivity")

    @JvmField
    val PIP_MENU_ACTIVITY: ComponentName = ComponentName.createRelative(
        ResourceNames.SYSTEM_UI_PACKAGE,
        ResourceNames.WM_SHELL_PACKAGE + ".pip.tv.PipMenuActivity"
    )
}

object PipActivity {
    /** Instruct the app to go into pip mode */
    const val ACTION_ENTER_PIP = "$pkg.PipTestActivity.enter_pip"
    const val ACTION_SET_MEDIA_TITLE = "$pkg.PipTestActivity.set_media_title"

    /**
     * A no-op action that the app's broadcast receiver listens to.
     *
     * This action can be used to apply changes passed in extras without having to
     * launch the activity thereby moving it out of pip.
     */
    const val ACTION_NO_OP = "$pkg.PipTestActivity.generic_update"

    const val ACTION_MEDIA_PLAY = "$pkg.PipTestActivity.media_play"
    const val ACTION_MEDIA_PAUSE = "$pkg.PipTestActivity.media_pause"

    /** Instruct the app to go into pip mode when set to true */
    const val EXTRA_ENTER_PIP = "enter_pip"

    /** Provide a rect hint for entering pip in the form "left top right bottom" */
    const val EXTRA_SOURCE_RECT_HINT = "source_rect_hint"

    /**
     * Boolean.
     * Make sure the app will turn on the screen (waking up the device) upon start.
     * This is accomplished by means of
     * https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean)
     */
    const val EXTRA_TURN_ON_SCREEN = "turn_on_screen"

    const val EXTRA_ASPECT_RATIO_DENOMINATOR = "aspect_ratio_denominator"
    const val EXTRA_ASPECT_RATIO_NUMERATOR = "aspect_ratio_numerator"

    /** Taken from [android.server.wm.PinnedStackTests] */
    object Ratios {
        // Corresponds to com.android.internal.R.dimen.config_pictureInPictureMinAspectRatio
        const val MIN_ASPECT_RATIO_NUMERATOR = 100
        const val MIN_ASPECT_RATIO_DENOMINATOR = 239

        // Corresponds to com.android.internal.R.dimen.config_pictureInPictureMaxAspectRatio
        const val MAX_ASPECT_RATIO_NUMERATOR = 239
        const val MAX_ASPECT_RATIO_DENOMINATOR = 100
    }

    /** URL encoded string. Sets the title of the media session. */
    const val EXTRA_MEDIA_SESSION_TITLE = "media_session_title"
    /** Boolean. Controls the active status of the media session. */
    const val EXTRA_MEDIA_SESSION_ACTIVE = "media_session_active"

    /**
     * Allows to set the [android.media.session.PlaybackState.Actions] that the media
     * session will react to. Defaults to (ACTION_PAUSE | ACTION_PLAY).
     */
    const val EXTRA_MEDIA_SESSION_ACTIONS = "media_session_actions"

    const val MEDIA_SESSION_TITLE = "PipTestActivity:MediaSession"

    /** Set the pip menu custom actions to this [ArrayList] of [android.app.RemoteAction]. */
    const val EXTRA_SET_CUSTOM_ACTIONS = "set_custom_actions"
}

object PipMenu {
    const val ACTION_MENU = "PipNotification.menu"
    const val ACTION_CLOSE = "PipNotification.close"
}

object ResourceNames {
    const val SYSTEM_UI_CTS_PACKAGE = "android.systemui.cts"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val WM_SHELL_PACKAGE = "com.android.wm.shell"

    const val STRING_PIP_PAUSE = "pip_pause"

    const val ID_PIP_MENU_CLOSE_BUTTON = "$SYSTEM_UI_PACKAGE:id/close_button"
    const val ID_PIP_MENU_FULLSCREEN_BUTTON = "$SYSTEM_UI_PACKAGE:id/full_button"
    const val ID_PIP_MENU_CUSTOM_BUTTON = "$SYSTEM_UI_PACKAGE:id/button"
}