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

package com.android.permissioncontroller.safetycenter.service

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.UserManager
import android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS
import android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS
import android.provider.SearchIndexablesContract.NonIndexableKey
import android.provider.SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION
import android.provider.SearchIndexablesContract.RawData.COLUMN_KEY
import android.provider.SearchIndexablesContract.RawData.COLUMN_KEYWORDS
import android.provider.SearchIndexablesContract.RawData.COLUMN_RANK
import android.provider.SearchIndexablesContract.RawData.COLUMN_SCREEN_TITLE
import android.provider.SearchIndexablesContract.RawData.COLUMN_TITLE
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterManager
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
import android.safetycenter.config.SafetySourcesGroup
import android.safetycenter.config.SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN
import android.safetycenter.config.SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL
import android.safetycenter.config.SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.service.BaseSearchIndexablesProvider
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PERSONAL_PROFILE_SUFFIX
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.WORK_PROFILE_SUFFIX
import com.android.permissioncontroller.safetycenter.ui.SafetyCenterUiFlags
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.Pref
import com.android.safetycenter.internaldata.SafetyCenterBundles
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.resources.SafetyCenterResourcesApk

/** [android.provider.SearchIndexablesProvider] for Safety Center. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SafetyCenterSearchIndexablesProvider : BaseSearchIndexablesProvider() {

    override fun queryRawData(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(INDEXABLES_RAW_COLUMNS)
        if (!SdkLevel.isAtLeastT()) {
            return cursor
        }

        val context = requireContext()
        val safetyCenterManager =
            context.getSystemService(SafetyCenterManager::class.java) ?: return cursor
        val safetyCenterResourcesApk = SafetyCenterResourcesApk(context)

        val screenTitle = context.getString(R.string.safety_center_dashboard_page_title)

        safetyCenterManager.safetySourcesGroupsWithEntries.forEach { safetySourcesGroup ->
            if (
                SdkLevel.isAtLeastU() &&
                    safetySourcesGroup.type == SAFETY_SOURCES_GROUP_TYPE_STATEFUL
            ) {
                cursor.addSafetySourcesGroupRow(
                    safetySourcesGroup,
                    safetyCenterResourcesApk,
                    screenTitle
                )
            }
            safetySourcesGroup.safetySources
                .asSequence()
                .filter { it.type != SAFETY_SOURCE_TYPE_ISSUE_ONLY }
                .forEach { safetySource ->
                    cursor.addSafetySourceRow(
                        context,
                        safetySource,
                        safetyCenterResourcesApk,
                        safetyCenterManager,
                        screenTitle
                    )
                }
        }

        if (SdkLevel.isAtLeastU()) {
            cursor.indexPrivacyControls(context, screenTitle)
        }
        return cursor
    }

    override fun queryNonIndexableKeys(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS)
        if (!SdkLevel.isAtLeastT()) {
            return cursor
        }

        val context = requireContext()
        val safetyCenterManager =
            context.getSystemService(SafetyCenterManager::class.java) ?: return cursor
        val userManager = context.getSystemService(UserManager::class.java) ?: return cursor
        val keysToRemove = mutableSetOf<String>()

        if (safetyCenterManager.isSafetyCenterEnabled) {
            // SafetyCenterStaticEntry doesn't provide an ID, so we never remove these entries from
            // search as we have no way to know if they're actually surfaced in the UI on T.
            // On U+, we implemented a workaround that provides an ID for these entries using
            // SafetyCenterData#getExtras().
            collectAllRemovableKeys(
                safetyCenterManager,
                keysToRemove,
                staticEntryGroupsAreRemovable = SdkLevel.isAtLeastU()
            )
            keepActiveEntriesFromRemoval(safetyCenterManager, userManager, keysToRemove)
        } else {
            collectAllRemovableKeys(
                safetyCenterManager,
                keysToRemove,
                staticEntryGroupsAreRemovable = true
            )
        }

        if (shouldRemovePrivacyControlKeys(safetyCenterManager)) {
            keysToRemove.addAll(privacyControlKeys)
        }

        keysToRemove.forEach { key -> cursor.newRow().add(NonIndexableKey.COLUMN_KEY_VALUE, key) }
        return cursor
    }

    private fun MatrixCursor.addSafetySourcesGroupRow(
        safetySourcesGroups: SafetySourcesGroup,
        safetyCenterResourcesApk: SafetyCenterResourcesApk,
        screenTitle: String,
    ) {
        val groupTitle =
            safetyCenterResourcesApk.getNotEmptyStringOrNull(safetySourcesGroups.titleResId)
                ?: return

        newRow()
            .add(COLUMN_RANK, 0)
            .add(COLUMN_TITLE, groupTitle)
            .add(COLUMN_KEYWORDS, groupTitle)
            .add(COLUMN_KEY, safetySourcesGroups.id)
            .add(COLUMN_INTENT_ACTION, Intent.ACTION_SAFETY_CENTER)
            .add(COLUMN_SCREEN_TITLE, screenTitle)
    }

    private fun MatrixCursor.addSafetySourceRow(
        context: Context,
        safetySource: SafetySource,
        safetyCenterResourcesApk: SafetyCenterResourcesApk,
        safetyCenterManager: SafetyCenterManager,
        screenTitle: String,
    ) {
        val searchTerms =
            safetyCenterResourcesApk.getNotEmptyStringOrNull(safetySource.searchTermsResId)
        var isPersonalEntryAdded = false
        var isWorkEntryAdded = false

        fun MatrixCursor.addIndexableRow(title: CharSequence, isWorkProfile: Boolean) =
            newRow()
                .add(COLUMN_RANK, 0)
                .add(COLUMN_TITLE, title)
                .add(COLUMN_KEYWORDS, searchTerms?.let { "$title, $it" } ?: title)
                .add(COLUMN_KEY, safetySource.id.addSuffix(isWorkProfile))
                .add(COLUMN_INTENT_ACTION, Intent.ACTION_SAFETY_CENTER)
                .add(COLUMN_SCREEN_TITLE, screenTitle)

        if (safetySource.id == BIOMETRIC_SOURCE_ID) {
            // correct Biometric Unlock title is only available when
            // Biometric SafetySource have sent the data to SafetyCenter
            context.getSystemService(UserManager::class.java)?.let { userManager ->
                safetyCenterManager.safetyEntries
                    .associateBy { it.entryId }
                    .filter { it.key.safetySourceId == BIOMETRIC_SOURCE_ID }
                    .forEach {
                        val isWorkProfile = userManager.isManagedProfile(it.key.userId)
                        if (isWorkProfile) {
                            isWorkEntryAdded = true
                        } else {
                            isPersonalEntryAdded = true
                        }
                        addIndexableRow(it.value.title, isWorkProfile)
                    }
            }
        }

        if (!isPersonalEntryAdded) {
            safetyCenterResourcesApk.getNotEmptyStringOrNull(safetySource.titleResId)?.let {
                addIndexableRow(title = it, isWorkProfile = false)
            }
        }

        if (!isWorkEntryAdded && safetySource.profile == SafetySource.PROFILE_ALL) {
            safetyCenterResourcesApk.getNotEmptyStringOrNull(safetySource.titleForWorkResId)?.let {
                addIndexableRow(title = it, isWorkProfile = true)
            }
        }
    }

    private fun SafetyCenterResourcesApk.getNotEmptyStringOrNull(resId: Int): String? =
        if (resId != Resources.ID_NULL) {
            getString(resId).takeIf { it.isNotEmpty() }
        } else {
            null
        }

    private fun String.addSuffix(isWorkProfile: Boolean): String =
        "${this}_${if (isWorkProfile) WORK_PROFILE_SUFFIX else PERSONAL_PROFILE_SUFFIX}"

    private val SafetyCenterManager.safetySourcesGroupsWithEntries: Sequence<SafetySourcesGroup>
        get() =
            safetyCenterConfig?.safetySourcesGroups?.asSequence()?.filter {
                it.type != SAFETY_SOURCES_GROUP_TYPE_HIDDEN
            }
                ?: emptySequence()

    private fun collectAllRemovableKeys(
        safetyCenterManager: SafetyCenterManager,
        keysToRemove: MutableSet<String>,
        staticEntryGroupsAreRemovable: Boolean
    ) {
        safetyCenterManager.safetySourcesGroupsWithEntries
            .filter {
                it.type != SAFETY_SOURCES_GROUP_TYPE_STATELESS || staticEntryGroupsAreRemovable
            }
            .forEach { safetySourcesGroup ->
                if (
                    SdkLevel.isAtLeastU() &&
                        safetySourcesGroup.type == SAFETY_SOURCES_GROUP_TYPE_STATEFUL
                ) {
                    keysToRemove.add(safetySourcesGroup.id)
                }
                safetySourcesGroup.safetySources
                    .asSequence()
                    .filter { it.type != SAFETY_SOURCE_TYPE_ISSUE_ONLY }
                    .forEach { safetySource ->
                        keysToRemove.add(safetySource.id.addSuffix(isWorkProfile = false))
                        if (safetySource.profile == SafetySource.PROFILE_ALL) {
                            keysToRemove.add(safetySource.id.addSuffix(isWorkProfile = true))
                        }
                    }
            }
    }

    private fun keepActiveEntriesFromRemoval(
        safetyCenterManager: SafetyCenterManager,
        userManager: UserManager,
        keysToRemove: MutableSet<String>
    ) {
        val safetyCenterData = safetyCenterManager.safetyCenterData
        safetyCenterData.entriesOrGroups.forEach { entryOrGroup ->
            val entryGroup = entryOrGroup.entryGroup
            if (entryGroup != null && SafetyCenterUiFlags.getShowSubpages()) {
                keysToRemove.remove(entryGroup.id)
            }
            entryOrGroup.entries.forEach {
                keepEntryFromRemoval(it.entryId, userManager, keysToRemove)
            }
        }
        if (!SdkLevel.isAtLeastU()) {
            return
        }
        safetyCenterData.staticEntryGroups
            .asSequence()
            .flatMap { it.staticEntries.asSequence() }
            .forEach { staticEntry ->
                val entryId = SafetyCenterBundles.getStaticEntryId(safetyCenterData, staticEntry)
                if (entryId != null) {
                    keepEntryFromRemoval(entryId, userManager, keysToRemove)
                }
            }
    }

    private fun keepEntryFromRemoval(
        entryId: SafetyCenterEntryId,
        userManager: UserManager,
        keysToRemove: MutableSet<String>
    ) {
        val isWorkProfile = userManager.isManagedProfile(entryId.userId)
        keysToRemove.remove(entryId.safetySourceId.addSuffix(isWorkProfile))
    }

    private val SafetyCenterManager.safetyEntriesOrGroups: Sequence<SafetyCenterEntryOrGroup>
        get() = safetyCenterData.entriesOrGroups.asSequence()

    private val SafetyCenterManager.safetyEntries: Sequence<SafetyCenterEntry>
        get() = safetyEntriesOrGroups.flatMap { it.entries }

    private val SafetyCenterEntryOrGroup.entries: Sequence<SafetyCenterEntry>
        get() =
            entryGroup?.entries?.asSequence() ?: entry?.let { sequenceOf(it) } ?: emptySequence()

    private val SafetyCenterEntry.entryId: SafetyCenterEntryId
        get() = SafetyCenterIds.entryIdFromString(id)

    companion object {
        private const val BIOMETRIC_SOURCE_ID = "AndroidBiometrics"

        private val privacyControlKeys: List<String>
            get() = Pref.values().map { it.key }

        private fun MatrixCursor.indexPrivacyControls(context: Context, screenTitle: String) {
            for (pref in Pref.values()) {
                val preferenceTitle = context.getString(pref.titleResId)
                newRow()
                    .add(COLUMN_RANK, 0)
                    .add(COLUMN_TITLE, preferenceTitle)
                    .add(COLUMN_KEY, pref.key)
                    .add(COLUMN_KEYWORDS, preferenceTitle)
                    .add(COLUMN_INTENT_ACTION, Intent.ACTION_SAFETY_CENTER)
                    .add(COLUMN_SCREEN_TITLE, screenTitle)
            }
        }

        private fun shouldRemovePrivacyControlKeys(
            safetyCenterManager: SafetyCenterManager
        ): Boolean {
            if (!SdkLevel.isAtLeastU()) {
                // The keys were never added in the first place, no need to remove.
                return false
            }
            val safetyCenterDisabled = !safetyCenterManager.isSafetyCenterEnabled
            val subpagesDisabled = !SafetyCenterUiFlags.getShowSubpages()
            return safetyCenterDisabled || subpagesDisabled
        }
    }
}
