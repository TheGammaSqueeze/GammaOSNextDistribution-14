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
import android.util.Log
import com.android.internal.logging.InstanceId
import javax.inject.Inject

private const val TAG = "EventLog"
private const val LOG = true

/** A fake EventLog. */
class FakeEventLog @Inject constructor(private val instanceId: InstanceId) : EventLog {

    var chooserActivityShown: ChooserActivityShown? = null
    var actionSelected: ActionSelected? = null
    var customActionSelected: CustomActionSelected? = null
    var actionShareWithPreview: ActionShareWithPreview? = null
    val shareTargetSelected: MutableList<ShareTargetSelected> = mutableListOf()

    private fun log(message: () -> Any?) {
        if (LOG) {
            Log.d(TAG, "[%04x] ".format(instanceId.id) + message())
        }
    }

    override fun logChooserActivityShown(
        isWorkProfile: Boolean,
        targetMimeType: String?,
        systemCost: Long
    ) {
        chooserActivityShown = ChooserActivityShown(isWorkProfile, targetMimeType, systemCost)
        log { chooserActivityShown }
    }

    override fun logShareStarted(
        packageName: String?,
        mimeType: String?,
        appProvidedDirect: Int,
        appProvidedApp: Int,
        isWorkprofile: Boolean,
        previewType: Int,
        intent: String?,
        customActionCount: Int,
        modifyShareActionProvided: Boolean
    ) {
        log {
            ShareStarted(
                packageName,
                mimeType,
                appProvidedDirect,
                appProvidedApp,
                isWorkprofile,
                previewType,
                intent,
                customActionCount,
                modifyShareActionProvided
            )
        }
    }

    override fun logCustomActionSelected(positionPicked: Int) {
        customActionSelected = CustomActionSelected(positionPicked)
        log { "logCustomActionSelected(positionPicked=$positionPicked)" }
    }

    override fun logShareTargetSelected(
        targetType: Int,
        packageName: String?,
        positionPicked: Int,
        directTargetAlsoRanked: Int,
        numCallerProvided: Int,
        directTargetHashed: HashedStringCache.HashResult?,
        isPinned: Boolean,
        successfullySelected: Boolean,
        selectionCost: Long
    ) {
        shareTargetSelected.add(
            ShareTargetSelected(
                targetType,
                packageName,
                positionPicked,
                directTargetAlsoRanked,
                numCallerProvided,
                directTargetHashed,
                isPinned,
                successfullySelected,
                selectionCost
            )
        )
        log { shareTargetSelected.last() }
        shareTargetSelected.limitSize(10)
    }

    private fun MutableList<*>.limitSize(n: Int) {
        while (size > n) {
            removeFirst()
        }
    }

    override fun logDirectShareTargetReceived(category: Int, latency: Int) {
        log { "logDirectShareTargetReceived(category=$category, latency=$latency)" }
    }

    override fun logActionShareWithPreview(previewType: Int) {
        actionShareWithPreview = ActionShareWithPreview(previewType)
        log { actionShareWithPreview }
    }

    override fun logActionSelected(targetType: Int) {
        actionSelected = ActionSelected(targetType)
        log { actionSelected }
    }

    override fun logContentPreviewWarning(uri: Uri?) {
        log { "logContentPreviewWarning(uri=$uri)" }
    }

    override fun logSharesheetTriggered() {
        log { "logSharesheetTriggered()" }
    }

    override fun logSharesheetAppLoadComplete() {
        log { "logSharesheetAppLoadComplete()" }
    }

    override fun logSharesheetDirectLoadComplete() {
        log { "logSharesheetAppLoadComplete()" }
    }

    override fun logSharesheetDirectLoadTimeout() {
        log { "logSharesheetDirectLoadTimeout()" }
    }

    override fun logSharesheetProfileChanged() {
        log { "logSharesheetProfileChanged()" }
    }

    override fun logSharesheetExpansionChanged(isCollapsed: Boolean) {
        log { "logSharesheetExpansionChanged(isCollapsed=$isCollapsed)" }
    }

    override fun logSharesheetAppShareRankingTimeout() {
        log { "logSharesheetAppShareRankingTimeout()" }
    }

    override fun logSharesheetEmptyDirectShareRow() {
        log { "logSharesheetEmptyDirectShareRow()" }
    }

    data class ActionSelected(val targetType: Int)
    data class CustomActionSelected(val positionPicked: Int)
    data class ActionShareWithPreview(val previewType: Int)
    data class ChooserActivityShown(
        val isWorkProfile: Boolean,
        val targetMimeType: String?,
        val systemCost: Long
    )
    data class ShareStarted(
        val packageName: String?,
        val mimeType: String?,
        val appProvidedDirect: Int,
        val appProvidedApp: Int,
        val isWorkprofile: Boolean,
        val previewType: Int,
        val intent: String?,
        val customActionCount: Int,
        val modifyShareActionProvided: Boolean
    )
    data class ShareTargetSelected(
        val targetType: Int,
        val packageName: String?,
        val positionPicked: Int,
        val directTargetAlsoRanked: Int,
        val numCallerProvided: Int,
        val directTargetHashed: HashedStringCache.HashResult?,
        val pinned: Boolean,
        val successfullySelected: Boolean,
        val selectionCost: Long
    )
}
