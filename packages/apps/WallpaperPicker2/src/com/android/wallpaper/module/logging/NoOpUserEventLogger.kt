/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module.logging

import android.content.Intent
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.module.logging.UserEventLogger.WallpaperDestination

/** [UserEventLogger] implementation that does nothing. */
open class NoOpUserEventLogger : UserEventLogger {

    override fun logSnapshot() {}

    override fun logAppLaunched(launchSource: Intent) {}

    override fun logWallpaperApplied(
        collectionId: String?,
        wallpaperId: String?,
        effects: String?,
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        @WallpaperDestination destination: Int,
    ) {}

    override fun logEffectApply(
        effect: String,
        status: Int,
        timeElapsedMillis: Long,
        resultCode: Int,
    ) {}

    override fun logEffectProbe(effect: String, status: Int) {}

    override fun logEffectForegroundDownload(
        effect: String,
        status: Int,
        timeElapsedMillis: Long,
    ) {}

    override fun logResetApplied() {}

    override fun logWallpaperExploreButtonClicked() {}
}
