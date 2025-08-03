/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.data.entries

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.EntryDateSectionHeader
import com.android.healthconnect.controller.shared.recyclerview.ViewBinder

/** View binder for a section title that looks like a PreferenceCategory. */
class SectionTitleViewBinder : ViewBinder<EntryDateSectionHeader, LinearLayout> {
    override fun newView(parent: ViewGroup): LinearLayout {
        return LayoutInflater.from(parent.context).inflate(R.layout.section_title, parent, false)
            as LinearLayout
    }

    override fun bind(view: View, data: EntryDateSectionHeader, index: Int) {
        val titleView = view.findViewById<TextView>(android.R.id.title)
        titleView.text = data.date
    }
}
