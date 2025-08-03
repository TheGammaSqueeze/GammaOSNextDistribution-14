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

package com.android.permissioncontroller.tests.mocking.safetylabel

import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import java.time.Instant
import java.time.ZonedDateTime

/** Safety labels to be used in tests. */
object TestSafetyLabels {
    const val PACKAGE_NAME_1 = "package_name_1"
    const val PACKAGE_NAME_2 = "package_name_2"
    const val PACKAGE_NAME_3 = "package_name_3"
    const val LOCATION_CATEGORY = "location"
    const val FINANCIAL_CATEGORY = "financial"
    val DATE_2022_09_01: Instant = ZonedDateTime.parse("2022-09-01T00:00:00.000Z").toInstant()
    val DATE_2022_10_10: Instant = ZonedDateTime.parse("2022-10-10T00:00:00.000Z").toInstant()
    val DATE_2022_10_12: Instant = ZonedDateTime.parse("2022-10-12T00:00:00.000Z").toInstant()
    val DATE_2022_10_14: Instant = ZonedDateTime.parse("2022-10-14T00:00:00.000Z").toInstant()
    val DATE_2022_12_10: Instant = ZonedDateTime.parse("2022-12-10T00:00:00.000Z").toInstant()
    val DATE_2022_12_30: Instant = ZonedDateTime.parse("2022-12-30T00:00:00.000Z").toInstant()

    /** A Safety label for [PACKAGE_NAME_1]. */
    val SAFETY_LABEL_PKG_1_V1: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_1),
            DATE_2022_09_01,
            DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true)))
        )

    /** A Safety label for [PACKAGE_NAME_1]. */
    val SAFETY_LABEL_PKG_1_V2: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_1),
            DATE_2022_10_14,
            DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(false)))
        )

    /** A Safety label for [PACKAGE_NAME_1]. */
    val SAFETY_LABEL_PKG_1_V3: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_1),
            DATE_2022_12_10,
            DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(false)))
        )

    /** A Safety label for [PACKAGE_NAME_2]. */
    val SAFETY_LABEL_PKG_2_V1: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_2),
            DATE_2022_10_10,
            DataLabel(
                mapOf(
                    LOCATION_CATEGORY to DataCategory(true),
                    FINANCIAL_CATEGORY to DataCategory(false)
                )
            )
        )

    /** A Safety label for [PACKAGE_NAME_2]. */
    val SAFETY_LABEL_PKG_2_V2: SafetyLabel =
        SafetyLabel(AppInfo(PACKAGE_NAME_2), DATE_2022_12_10, DataLabel(mapOf()))

    /** A Safety label for [PACKAGE_NAME_2]. */
    val SAFETY_LABEL_PKG_2_V3: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_2),
            DATE_2022_12_30,
            DataLabel(mapOf(FINANCIAL_CATEGORY to DataCategory(true)))
        )

    /** A Safety label for [PACKAGE_NAME_3]. */
    val SAFETY_LABEL_PKG_3_V1: SafetyLabel =
        SafetyLabel(
            AppInfo(PACKAGE_NAME_3),
            DATE_2022_10_10,
            DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true)))
        )
}
