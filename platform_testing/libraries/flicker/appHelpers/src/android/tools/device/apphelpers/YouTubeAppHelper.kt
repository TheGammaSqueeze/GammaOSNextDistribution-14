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

package android.tools.device.apphelpers

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.net.Uri
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Helper to launch Youtube (not compatible with AOSP)
 *
 * This helper has no other functionality but the app launch.
 */
class YouTubeAppHelper(
    instrumentation: Instrumentation,
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getYoutubeLauncherName(pkgManager),
        getYoutubeComponent(pkgManager),
    ) {

    fun waitForVideoPlaying() {
        displayControls()
        getPauseButton()
    }

    /**
     * This re-displays the controls if they are already displayed so that the timer until they're
     * off again, is reset giving the tests more time to target them. If the controls are already
     * off, then this simply displays them by clicking off to the left side of the player.
     */
    private fun displayControls() {
        val player: UiObject2 = uiDevice.findObject(By.res(PACKAGE_NAME, UI_WATCH_PLAYER_ID))
        val playerRect: Rect = player.getVisibleBounds()
        // Always touch the screen when requested to display controls regardless of
        // the current state of the controls. This always starts the timer giving the test
        // 3 seconds to perform the operation.
        uiDevice.click(playerRect.centerX(), playerRect.centerY())

        val controls: UiObject2? =
            uiDevice.wait(Until.findObject(By.res(PACKAGE_NAME, UI_CONTROL_ID)), WAIT_DELAY)
        if (controls != null) {
            // nothing to do here. We return now since the controls are visible.
            return
        }

        // Since our first tap may have turned off the controls, we tap again and exit
        uiDevice.click(playerRect.centerX(), playerRect.centerY())
    }

    protected fun getPauseButton(): UiObject2? {
        return uiDevice.wait(Until.findObject(By.desc(UI_PAUSE_BUTTON_DESC)), WAIT_DELAY)
    }

    companion object {
        const val INTENT_WATCH_VIDEO_PATTERN = "vnd.youtube:%s"
        const val PACKAGE_NAME = "com.google.android.youtube"
        const val UI_WATCH_PLAYER_ID = "watch_player"
        const val UI_CONTROL_ID = "controls_layout"
        const val UI_PAUSE_BUTTON_DESC = "Pause video"
        const val WAIT_DELAY: Long = 2000

        fun getYoutubeVideoIntent(videoId: String?): Intent {
            val youTubeVideoIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(String.format(INTENT_WATCH_VIDEO_PATTERN, videoId))
                )
            youTubeVideoIntent.setPackage(PACKAGE_NAME)
            youTubeVideoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return youTubeVideoIntent
        }

        private fun getYoutubeIntent(pkgManager: PackageManager): Intent {
            return pkgManager.getLaunchIntentForPackage(PACKAGE_NAME)
                ?: error("Youtube launch intent not found")
        }

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo {
            val intent = getYoutubeIntent(pkgManager)
            return pkgManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve calendar activity")
        }

        private fun getYoutubeComponent(pkgManager: PackageManager): ComponentNameMatcher {
            val resolveInfo = getResolveInfo(pkgManager)
            return ComponentNameMatcher(
                resolveInfo.activityInfo.packageName,
                className = resolveInfo.activityInfo.name
            )
        }

        private fun getYoutubeLauncherName(pkgManager: PackageManager): String {
            val resolveInfo = getResolveInfo(pkgManager)
            return resolveInfo.loadLabel(pkgManager).toString()
        }
    }
}
