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

package com.android.permissioncontroller.permission.ui.handheld.v34

import android.content.Context
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R

/** A preference for a footer with an icon and a link. */
class AppDataSharingUpdatesFooterPreference : Preference {
    constructor(c: Context) : super(c)
    constructor(c: Context, a: AttributeSet) : super(c, a)
    constructor(c: Context, a: AttributeSet, attr: Int) : super(c, a, attr)
    constructor(c: Context, a: AttributeSet, attr: Int, res: Int) : super(c, a, attr, res)

    private var footerMessageView: TextView? = null
    private var footerLinkView: TextView? = null

    init {
        layoutResource = R.layout.app_data_sharing_updates_footer_preference
    }

    /** Message for the footer. */
    var footerMessage: CharSequence = ""
        set(value) {
            field = value
            notifyChanged()
        }

    /** Clickable link for the footer. */
    var footerLink: CharSequence = ""
        set(value) {
            field = value
            notifyChanged()
        }

    /** [View.OnClickListener] for the footer link. */
    var onFooterLinkClick: View.OnClickListener? = null
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        footerMessageView = holder.findViewById(R.id.footer_message) as TextView?
        footerMessageView?.text = footerMessage

        footerLinkView = holder.findViewById(R.id.footer_link) as TextView?
        val footerLinkText = SpannableString(footerLink)
        footerLinkText.setSpan(
            object : ClickableSpan() {
                override fun onClick(v: View) {
                    onFooterLinkClick?.onClick(v)
                }
            },
            0,
            footerLink.length,
            0
        )
        footerLinkView?.let {
            it.visibility = if (onFooterLinkClick == null) View.GONE else View.VISIBLE
            it.text = footerLinkText
            it.movementMethod = LinkMovementMethodCompat.getInstance()
        }
        super.onBindViewHolder(holder)
    }
}
