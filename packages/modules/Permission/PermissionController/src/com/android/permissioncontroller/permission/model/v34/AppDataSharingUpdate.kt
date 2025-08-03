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

package com.android.permissioncontroller.permission.model.v34

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType.ADDS_ADVERTISING_PURPOSE
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType.ADDS_SHARING_WITHOUT_ADVERTISING_PURPOSE
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType.ADDS_SHARING_WITH_ADVERTISING_PURPOSE
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelDiff
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel

/**
 * Class representing a significant update in an app's data sharing policy from its safety label.
 *
 * Note that safety labels are part of package information, and therefore the safety label
 * information applies to apps for all users that have the app installed.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
data class AppDataSharingUpdate(
    /** Package name for the app with the data sharing update. */
    val packageName: String,
    /** How data sharing for each category has changed. */
    val categorySharingUpdates: Map<String, DataSharingUpdateType>
) {

    /** Companion object for [AppDataSharingUpdate]. */
    companion object {
        /**
         * Builds and returns an [AppDataSharingUpdate] from an [AppSafetyLabelDiff], if the change
         * in safety labels is significant, else returns null.
         */
        fun AppSafetyLabelDiff.buildUpdateIfSignificantChange(): AppDataSharingUpdate? {
            // In Android U, only updates for the location data category will be displayed in
            // the UI.
            val updates = getUpdatesForCategories(listOf(CATEGORY_LOCATION))

            return if (updates.isEmpty()) null
            else AppDataSharingUpdate(safetyLabelBefore.appInfo.packageName, updates)
        }

        private fun AppSafetyLabelDiff.getUpdatesForCategories(
            categories: List<String>
        ): Map<String, DataSharingUpdateType> {
            val categoryUpdateMap = mutableMapOf<String, DataSharingUpdateType>()

            for (category in categories) {
                var categoryUpdateType: DataSharingUpdateType?

                val beforeSharesData = safetyLabelBefore.sharesData(category)
                val beforeSharesDataForAds = safetyLabelBefore.sharesDataForAdsPurpose(category)
                val afterSharesData = safetyLabelAfter.sharesData(category)
                val afterSharesDataForAds = safetyLabelAfter.sharesDataForAdsPurpose(category)

                categoryUpdateType =
                    when {
                        !beforeSharesData && afterSharesDataForAds ->
                            ADDS_SHARING_WITH_ADVERTISING_PURPOSE
                        !beforeSharesData && afterSharesData && !afterSharesDataForAds ->
                            ADDS_SHARING_WITHOUT_ADVERTISING_PURPOSE
                        beforeSharesData && !beforeSharesDataForAds && afterSharesDataForAds ->
                            ADDS_ADVERTISING_PURPOSE
                        else -> null
                    }

                if (categoryUpdateType == null) {
                    continue
                }

                categoryUpdateMap[category] = categoryUpdateType
            }

            return categoryUpdateMap
        }

        private fun SafetyLabel.sharesData(category: String) =
            dataLabel.dataShared.containsKey(category)

        private fun SafetyLabel.sharesDataForAdsPurpose(category: String) =
            dataLabel.dataShared[category]?.containsAdvertisingPurpose ?: false
    }
}

/**
 * Different ways in which data sharing can be significantly updated for a particular data category.
 */
enum class DataSharingUpdateType {
    ADDS_ADVERTISING_PURPOSE,
    ADDS_SHARING_WITHOUT_ADVERTISING_PURPOSE,
    ADDS_SHARING_WITH_ADVERTISING_PURPOSE
}
