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
import android.net.Uri
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Helper to launch Netflix (not compatible with AOSP)
 *
 * This helper provides intent to launch directly into video playback.
 */
class NetflixAppHelper(
    instrumentation: Instrumentation,
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getNetflixLauncherName(pkgManager),
        getNetflixComponent(pkgManager),
    ) {

    fun waitForVideoPlaying() {
        getPauseButton()
    }

    protected fun getPauseButton(): UiObject2? {
        return uiDevice.wait(Until.findObject(By.desc(UI_PAUSE_BUTTON_DESC)), WAIT_DELAY)
    }

    companion object {
        const val PACKAGE_NAME = "com.netflix.mediaclient"
        const val WATCH_CLASS_NAME = "com.netflix.mediaclient.ui.launch.UIWebViewActivity"
        const val INTENT_WATCH_VIDEO_PATTERN = "http://www.netflix.com/watch/%s"
        const val WATCH_ACTIVITY = "com.netflix.mediaclient.ui.player.PlayerActivity"
        const val UI_PAUSE_BUTTON_DESC = "Pause"
        const val WAIT_DELAY: Long = 2000

        fun getNetflixWatchVideoIntent(videoId: String?): Intent {
            val netflixVideoIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(String.format(INTENT_WATCH_VIDEO_PATTERN, videoId))
                )
            netflixVideoIntent.setPackage(PACKAGE_NAME)
            netflixVideoIntent.setClassName(PACKAGE_NAME, WATCH_CLASS_NAME)
            netflixVideoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return netflixVideoIntent
        }

        private fun getNetflixIntent(pkgManager: PackageManager): Intent {
            return pkgManager.getLaunchIntentForPackage(PACKAGE_NAME)
                ?: error("Netflix launch intent not found")
        }

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo {
            val intent = getNetflixIntent(pkgManager)
            return pkgManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve calendar activity")
        }

        private fun getNetflixComponent(pkgManager: PackageManager): ComponentNameMatcher {
            val resolveInfo = getResolveInfo(pkgManager)
            return ComponentNameMatcher(
                resolveInfo.activityInfo.packageName,
                className = resolveInfo.activityInfo.name
            )
        }

        private fun getNetflixLauncherName(pkgManager: PackageManager): String {
            val resolveInfo = getResolveInfo(pkgManager)
            return resolveInfo.loadLabel(pkgManager).toString()
        }
    }
}
