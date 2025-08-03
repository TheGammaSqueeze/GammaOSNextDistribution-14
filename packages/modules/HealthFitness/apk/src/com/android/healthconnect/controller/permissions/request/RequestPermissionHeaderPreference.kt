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
package com.android.healthconnect.controller.permissions.request

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.boldAppName
import com.android.healthconnect.controller.utils.convertTextViewIntoLink

internal class RequestPermissionHeaderPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var title: TextView
    private lateinit var privacyPolicy: TextView
    private var appName: String? = null
    private var onRationaleLinkClicked: (() -> Unit)? = null

    init {
        layoutResource = R.layout.widget_request_permission_header
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        title = holder.findViewById(R.id.title) as TextView
        updateTitle()
        privacyPolicy = holder.findViewById(R.id.privacy_policy) as TextView
        updatePrivacyString()
    }

    fun bind(appName: String, onRationaleLinkClicked: () -> Unit) {
        this.appName = appName
        this.onRationaleLinkClicked = onRationaleLinkClicked
        notifyChanged()
    }

    private fun updateTitle() {
        val text = context.getString(R.string.request_permissions_header_title, appName)
        title.text = boldAppName(appName, text)
    }

    private fun updatePrivacyString() {
        val policyString = context.getString(R.string.request_permissions_privacy_policy)
        val rationaleText =
            context.resources.getString(
                R.string.request_permissions_rationale, appName, policyString)
        convertTextViewIntoLink(
            privacyPolicy,
            rationaleText,
            rationaleText.indexOf(policyString),
            rationaleText.indexOf(policyString) + policyString.length) {
                onRationaleLinkClicked?.invoke()
            }
    }
}
