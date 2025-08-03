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

package com.android.healthconnect.controller.recentaccess

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.connectedapps.ComparablePreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.RecentAccessElement
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Custom preference for displaying Recent access apps, including dash lines for timeline views. */
class RecentAccessPreference
constructor(
    context: Context,
    private val recentAccessApp: RecentAccessEntry,
    private val timeSource: TimeSource,
    private val showCategories: Boolean
) : HealthPreference(context), ComparablePreference {

    private lateinit var appIcon: ImageView
    private lateinit var appTitle: TextView
    private lateinit var dataTypesWritten: TextView
    private lateinit var dataTypesRead: TextView
    private lateinit var accessTime: TextView
    private val separator: String = context.getString(R.string.data_type_separator)

    private val appName = recentAccessApp.metadata.appName
    private val writtenText: String =
        context.getString(
            R.string.write_data_access_label,
            recentAccessApp.dataTypesWritten.sorted().joinToString(separator) {
                context.getString(it)
            })
    private val readText: String =
        context.getString(
            R.string.read_data_access_label,
            recentAccessApp.dataTypesRead.sorted().joinToString(separator) {
                context.getString(it)
            })
    private val formattedTime = formatTime(recentAccessApp.instantTime)
    // Used to compare this preference to other RecentAccessPreferences
    val comparableTitle = "${formattedTime}_${appName}_${writtenText}_${readText}"

    init {
        layoutResource = R.layout.widget_recent_access_timeline
        isSelectable = true
        this.logName = RecentAccessElement.RECENT_ACCESS_ENTRY_BUTTON
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        appIcon = holder.findViewById(R.id.recent_access_app_icon) as ImageView
        appIcon.setImageDrawable(recentAccessApp.metadata.icon)

        appTitle = holder.findViewById(R.id.title) as TextView
        appTitle.text = appName

        dataTypesWritten = holder.findViewById(R.id.data_types_written) as TextView
        dataTypesRead = holder.findViewById(R.id.data_types_read) as TextView

        if (showCategories) {
            if (recentAccessApp.dataTypesWritten.isNotEmpty()) {
                dataTypesWritten.text = writtenText
                dataTypesWritten.isVisible = true
            }

            if (recentAccessApp.dataTypesRead.isNotEmpty()) {
                dataTypesRead.text = readText
                dataTypesRead.isVisible = true
            }
        }

        accessTime = holder.findViewById(R.id.time) as TextView
        accessTime.text = formattedTime
        accessTime.contentDescription =
            context.getString(R.string.recent_access_time_content_descritption, formattedTime)
    }

    override fun isSameItem(preference: Preference): Boolean {
        return preference is RecentAccessPreference && this == preference
    }

    override fun hasSameContents(preference: Preference): Boolean {
        return preference is RecentAccessPreference &&
            this.recentAccessApp == preference.recentAccessApp
    }

    private fun formatTime(instant: Instant): String {
        val localTime: LocalTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        return if (timeSource.is24Hour(context)) {
            localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            localTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        }
    }
}
