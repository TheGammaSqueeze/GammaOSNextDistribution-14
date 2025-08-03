/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permissionui

import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject

private const val SUMMARY_TEXT = "apps allowed"

/**
 * Read the {@link UsageCount} of the group of the permission from the Ui.
 *
 * @param groupLabel label fo the group the count should be read for
 * @return usage counts for the group of the permission
 */
fun getUsageCountsFromUi(groupLabel: CharSequence): UsageCount {
    waitFindObject(By.text(groupLabel.toString()))

    return getEventually {
        val summaryText =
            waitFindObject(
                    By.hasChild(By.text(groupLabel.toString()))
                        .hasChild(By.textContains(SUMMARY_TEXT))
                )
                .findObject(By.textContains(SUMMARY_TEXT))
                .text

        // Matches two numbers out of the summary line, i.e. "...3...12..." -> "3", "12"
        val groups =
            Regex("^[^\\d]*(\\d+)[^\\d]*(\\d+)[^\\d]*\$").find(summaryText)?.groupValues
                ?: throw Exception("No usage counts found")

        UsageCount(groups[1].toInt(), groups[2].toInt())
    }
}

/** Usage counts as read via {@link #getUsageCountsFromUi}. */
data class UsageCount(
    /** Number of apps with permission granted */
    val granted: Int,
    /** Number of apps that request permissions */
    val total: Int
)
