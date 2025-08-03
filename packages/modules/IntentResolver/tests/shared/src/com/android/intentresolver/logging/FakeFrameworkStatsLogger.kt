package com.android.intentresolver.logging
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

import com.android.internal.util.FrameworkStatsLog

internal data class ShareSheetStarted(
    val frameworkEventId: Int = FrameworkStatsLog.SHARESHEET_STARTED,
    val appEventId: Int,
    val packageName: String?,
    val instanceId: Int,
    val mimeType: String?,
    val numAppProvidedDirectTargets: Int,
    val numAppProvidedAppTargets: Int,
    val isWorkProfile: Boolean,
    val previewType: Int,
    val intentType: Int,
    val numCustomActions: Int,
    val modifyShareActionProvided: Boolean
)

internal data class RankingSelected(
    val frameworkEventId: Int = FrameworkStatsLog.RANKING_SELECTED,
    val appEventId: Int,
    val packageName: String?,
    val instanceId: Int,
    val positionPicked: Int,
    val isPinned: Boolean
)

internal class FakeFrameworkStatsLogger : FrameworkStatsLogger {
    var shareSheetStarted: ShareSheetStarted? = null
    var rankingSelected: RankingSelected? = null
    override fun write(
        frameworkEventId: Int,
        appEventId: Int,
        packageName: String?,
        instanceId: Int,
        mimeType: String?,
        numAppProvidedDirectTargets: Int,
        numAppProvidedAppTargets: Int,
        isWorkProfile: Boolean,
        previewType: Int,
        intentType: Int,
        numCustomActions: Int,
        modifyShareActionProvided: Boolean
    ) {
        shareSheetStarted =
            ShareSheetStarted(
                frameworkEventId,
                appEventId,
                packageName,
                instanceId,
                mimeType,
                numAppProvidedDirectTargets,
                numAppProvidedAppTargets,
                isWorkProfile,
                previewType,
                intentType,
                numCustomActions,
                modifyShareActionProvided
            )
    }
    override fun write(
        frameworkEventId: Int,
        appEventId: Int,
        packageName: String?,
        instanceId: Int,
        positionPicked: Int,
        isPinned: Boolean
    ) {
        rankingSelected =
            RankingSelected(
                frameworkEventId,
                appEventId,
                packageName,
                instanceId,
                positionPicked,
                isPinned
            )
    }
}
