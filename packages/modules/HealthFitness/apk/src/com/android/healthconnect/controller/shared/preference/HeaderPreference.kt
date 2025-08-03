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
package com.android.healthconnect.controller.shared.preference

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.convertTextViewIntoLink

/** Custom preference for the headers containing a link. */
class HeaderPreference constructor(context: Context) :
    Preference(context) {

    private lateinit var headerTitle: TextView
    private lateinit var headerLink: TextView

    private var headerText: String? = null
    private var headerLinkText: String? = null
    private var linkAction: OnClickListener? = null

    init {
        layoutResource = R.layout.widget_header_preference
        isSelectable = false
    }

    fun setHeaderText(headerText: String) {
        this.headerText = headerText
    }

    fun setHeaderLinkText(headerLinkText: String) {
        this.headerLinkText = headerLinkText
    }

    fun setHeaderLinkAction(onClickListener: OnClickListener) {
        this.linkAction = onClickListener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        headerTitle = holder.findViewById(R.id.header_title) as TextView
        headerTitle.text = headerText

        headerLink = holder.findViewById(R.id.header_link) as TextView
        if (headerLinkText != null) {
            headerLink.visibility = View.VISIBLE
            convertTextViewIntoLink(headerLink, headerLinkText, 0, headerLinkText!!.length, linkAction)
        } else {
            headerLink.visibility = View.GONE
        }
    }
}
