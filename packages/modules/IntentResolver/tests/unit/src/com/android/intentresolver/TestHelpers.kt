/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager.ShareShortcutInfo
import android.os.Bundle
import android.service.chooser.ChooserTarget
import org.mockito.Mockito.`when` as whenever

internal fun createShareShortcutInfo(
    id: String,
    componentName: ComponentName,
    rank: Int
): ShareShortcutInfo =
    ShareShortcutInfo(
        createShortcutInfo(id, componentName, rank),
        componentName
    )

internal fun createShortcutInfo(
    id: String,
    componentName: ComponentName,
    rank: Int
): ShortcutInfo {
    val context = mock<Context>()
    whenever(context.packageName).thenReturn(componentName.packageName)
    return ShortcutInfo.Builder(context, id)
        .setShortLabel("Short Label $id")
        .setLongLabel("Long Label $id")
        .setActivity(componentName)
        .setRank(rank)
        .build()
}

internal fun createAppTarget(shortcutInfo: ShortcutInfo) =
    AppTarget(
        AppTargetId(shortcutInfo.id),
        shortcutInfo,
        shortcutInfo.activity?.className ?: error("missing activity info")
    )

fun createChooserTarget(
    title: String, score: Float, componentName: ComponentName, shortcutId: String
): ChooserTarget =
    ChooserTarget(
        title,
        null,
        score,
        componentName,
        Bundle().apply { putString(Intent.EXTRA_SHORTCUT_ID, shortcutId) }
    )
