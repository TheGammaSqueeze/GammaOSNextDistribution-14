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
import android.provider.MediaStore
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.platform.app.InstrumentationRegistry

/** Helper to launch the Camera app. */
class CameraAppHelper
@JvmOverloads
constructor(
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getCameraLauncherName(pkgManager),
        getCameraComponent(pkgManager)
    ) {

    override val openAppIntent =
        pkgManager.getLaunchIntentForPackage(packageName)
            ?: error("Unable to find intent for camera")

    companion object {
        private fun getCameraIntent(): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo =
            pkgManager.resolveActivity(getCameraIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve camera activity")

        private fun getCameraComponent(pkgManager: PackageManager): ComponentNameMatcher =
            ComponentNameMatcher(
                getResolveInfo(pkgManager).activityInfo.packageName,
                className = ""
            )

        private fun getCameraLauncherName(pkgManager: PackageManager): String =
            getResolveInfo(pkgManager).loadLabel(pkgManager).toString()
    }
}
