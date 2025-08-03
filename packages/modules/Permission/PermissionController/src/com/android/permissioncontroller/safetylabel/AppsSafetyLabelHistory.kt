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

package com.android.permissioncontroller.safetylabel

import com.android.permission.safetylabel.DataCategory as AppMetadataDataCategory
import com.android.permission.safetylabel.DataCategoryConstants
import com.android.permission.safetylabel.DataLabel as AppMetadataDataLabel
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import java.time.Instant

/** Data class representing safety label history of installed apps. */
data class AppsSafetyLabelHistory(val appSafetyLabelHistories: List<AppSafetyLabelHistory>) {

    /** Data class representing the safety label history of an app. */
    data class AppSafetyLabelHistory(
        /** Information about the app. */
        val appInfo: AppInfo,
        /**
         * A list of [SafetyLabel]s that this app has had in the past, ordered by
         * [SafetyLabel.receivedAt].
         *
         * The last [SafetyLabel] in this list can be considered the last known [SafetyLabel] of the
         * app.
         */
        val safetyLabelHistory: List<SafetyLabel>
    ) {
        init {
            require(safetyLabelHistory.sortedBy { it.receivedAt } == safetyLabelHistory)
            require(safetyLabelHistory.all { it.appInfo == appInfo })
        }

        /**
         * Returns an [AppSafetyLabelHistory] with the original history as well the provided safety
         * label, ensuring that the maximum number of safety labels stored for this app does not
         * exceed [maxToPersist].
         *
         * If the storage already has [maxToPersist] labels or more, the oldest will be discarded to
         * make space for the newly added safety label.
         */
        fun withSafetyLabel(safetyLabel: SafetyLabel, maxToPersist: Int): AppSafetyLabelHistory =
            AppSafetyLabelHistory(
                appInfo,
                safetyLabelHistory
                    .toMutableList()
                    .apply { add(safetyLabel) }
                    .sortedBy { it.receivedAt }
                    .takeLast(maxToPersist)
            )
    }

    /** Data class representing the information about an app. */
    data class AppInfo(
        val packageName: String,
    )

    /** Data class representing an app's safety label. */
    data class SafetyLabel(
        /** Information about the app. */
        val appInfo: AppInfo,
        /** Earliest time at which the safety label was known to be accurate. */
        val receivedAt: Instant,
        /** Information about data use policies for an app. */
        val dataLabel: DataLabel
    ) {
        /** Companion object for [SafetyLabel]. */
        companion object {
            /**
             * Creates a safety label for persistence from the safety label parsed from
             * PackageManager app metadata.
             */
            fun extractLocationSharingSafetyLabel(
                packageName: String,
                receivedAt: Instant,
                appMetadataSafetyLabel: AppMetadataSafetyLabel
            ): SafetyLabel =
                SafetyLabel(
                    AppInfo(packageName),
                    receivedAt,
                    DataLabel.extractLocationSharingDataLabel(appMetadataSafetyLabel.dataLabel)
                )
        }
    }

    /** Data class representing an app's data use policies. */
    data class DataLabel(
        /** Map of category to [DataCategory] */
        val dataShared: Map<String, DataCategory>
    ) {
        /** Companion object for [DataCategory]. */
        companion object {
            /**
             * Creates a data label for persistence from a data label parsed from PackageManager app
             * metadata.
             */
            fun extractLocationSharingDataLabel(
                appMetadataDataLabel: AppMetadataDataLabel
            ): DataLabel =
                DataLabel(
                    appMetadataDataLabel.dataShared
                        .filter { it.key == DataCategoryConstants.CATEGORY_LOCATION }
                        .mapValues { categoryEntry ->
                            DataCategory.fromAppMetadataDataCategory(categoryEntry.value)
                        }
                )
        }
    }

    /** Data class representing an app's data use for a particular category of data. */
    data class DataCategory(
        /** Whether any data in this category has been used for Advertising. */
        val containsAdvertisingPurpose: Boolean
    ) {
        /** Companion object for [DataCategory]. */
        companion object {
            /**
             * Creates a data category for persistence from a data category parsed from
             * PackageManager app metadata.
             */
            fun fromAppMetadataDataCategory(
                appMetadataDataCategory: AppMetadataDataCategory
            ): DataCategory =
                DataCategory(
                    appMetadataDataCategory.dataTypes.values.any {
                        it.purposeSet.contains(PURPOSE_ADVERTISING)
                    }
                )
        }
    }

    /** Data class representing a change of an app's safety label over time. */
    data class AppSafetyLabelDiff(
        val safetyLabelBefore: SafetyLabel,
        val safetyLabelAfter: SafetyLabel
    ) {
        init {
            require(safetyLabelBefore.appInfo == safetyLabelAfter.appInfo)
        }
    }
}
