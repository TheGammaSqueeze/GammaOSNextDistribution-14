/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.connectedapps

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R

class PermissionHeaderPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView

    init {
        layoutResource = R.layout.widget_permission_header
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        iconView = holder.findViewById(R.id.permission_header_icon) as ImageView
        iconView.setImageDrawable(getIcon())
        titleView = holder.findViewById(R.id.permission_header_title) as TextView
        titleView.setText(getTitle())
        subtitleView = holder.findViewById(R.id.permission_header_summary) as TextView
        subtitleView.isVisible = (summary != null)
        if (summary != null) {
            subtitleView.setText(summary)
        }
    }
}
