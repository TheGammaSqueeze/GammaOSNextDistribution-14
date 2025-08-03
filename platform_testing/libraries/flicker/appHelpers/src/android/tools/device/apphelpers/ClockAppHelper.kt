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
import android.provider.AlarmClock
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.platform.app.InstrumentationRegistry

/** Helper to launch the Camera app. */
class ClockAppHelper
@JvmOverloads
constructor(
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getClockLauncherName(pkgManager),
        getClockComponent(pkgManager)
    ) {

    override val openAppIntent =
        pkgManager.getLaunchIntentForPackage(packageName)
            ?: error("Unable to find intent for camera")

    companion object {
        private fun getClockIntent(): Intent = Intent(AlarmClock.ACTION_SET_ALARM)

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo =
            pkgManager.resolveActivity(getClockIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve camera activity")

        private fun getClockComponent(pkgManager: PackageManager): ComponentNameMatcher =
            ComponentNameMatcher(
                getResolveInfo(pkgManager).activityInfo.packageName,
                className = ""
            )

        private fun getClockLauncherName(pkgManager: PackageManager): String =
            getResolveInfo(pkgManager).loadLabel(pkgManager).toString()
    }
}
