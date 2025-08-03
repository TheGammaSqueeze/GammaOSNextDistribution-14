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
package com.android.intentresolver.logging

import android.net.Uri
import android.util.HashedStringCache

/** Logs notable events during ShareSheet usage. */
interface EventLog {

    companion object {
        const val SELECTION_TYPE_SERVICE = 1
        const val SELECTION_TYPE_APP = 2
        const val SELECTION_TYPE_STANDARD = 3
        const val SELECTION_TYPE_COPY = 4
        const val SELECTION_TYPE_NEARBY = 5
        const val SELECTION_TYPE_EDIT = 6
        const val SELECTION_TYPE_MODIFY_SHARE = 7
        const val SELECTION_TYPE_CUSTOM_ACTION = 8
    }

    fun logChooserActivityShown(isWorkProfile: Boolean, targetMimeType: String?, systemCost: Long)

    fun logShareStarted(
        packageName: String?,
        mimeType: String?,
        appProvidedDirect: Int,
        appProvidedApp: Int,
        isWorkprofile: Boolean,
        previewType: Int,
        intent: String?,
        customActionCount: Int,
        modifyShareActionProvided: Boolean
    )

    fun logCustomActionSelected(positionPicked: Int)
    fun logShareTargetSelected(
        targetType: Int,
        packageName: String?,
        positionPicked: Int,
        directTargetAlsoRanked: Int,
        numCallerProvided: Int,
        directTargetHashed: HashedStringCache.HashResult?,
        isPinned: Boolean,
        successfullySelected: Boolean,
        selectionCost: Long
    )

    fun logDirectShareTargetReceived(category: Int, latency: Int)
    fun logActionShareWithPreview(previewType: Int)
    fun logActionSelected(targetType: Int)
    fun logContentPreviewWarning(uri: Uri?)
    fun logSharesheetTriggered()
    fun logSharesheetAppLoadComplete()
    fun logSharesheetDirectLoadComplete()
    fun logSharesheetDirectLoadTimeout()
    fun logSharesheetProfileChanged()
    fun logSharesheetExpansionChanged(isCollapsed: Boolean)
    fun logSharesheetAppShareRankingTimeout()
    fun logSharesheetEmptyDirectShareRow()
}
