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
import androidx.test.platform.app.InstrumentationRegistry

/** Helper to launch the Calendar app. */
class CalendarAppHelper
@JvmOverloads
constructor(
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getCalendarLauncherName(pkgManager),
        getCalendarComponent(pkgManager)
    ) {
    companion object {
        private fun getCalendarIntent(): Intent {
            val epochEventStartTime = 0
            val uri = Uri.parse("content://com.android.calendar/time/$epochEventStartTime")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri
            intent.putExtra("VIEW", "DAY")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
            return intent
        }

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo {
            val intent = getCalendarIntent()
            return pkgManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve calendar activity")
        }

        private fun getCalendarComponent(pkgManager: PackageManager): ComponentNameMatcher {
            val resolveInfo = getResolveInfo(pkgManager)
            return ComponentNameMatcher(
                resolveInfo.activityInfo.packageName,
                className = resolveInfo.activityInfo.name
            )
        }

        private fun getCalendarLauncherName(pkgManager: PackageManager): String {
            val resolveInfo = getResolveInfo(pkgManager)
            return resolveInfo.loadLabel(pkgManager).toString()
        }
    }
}
