/**
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
package com.android.healthconnect.controller.dataentries

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.ExerciseSessionEntry
import com.android.healthconnect.controller.shared.RoundView
import com.android.healthconnect.controller.shared.map.MapView
import com.android.healthconnect.controller.shared.recyclerview.ViewBinder
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** ViewBinder for ExerciseSessionEntry. */
class ExerciseSessionItemViewBinder(
    private val showSecondAction: Boolean = true,
    private val onItemClickedListener: OnClickEntryListener?,
    private val onDeleteEntryClicked: OnDeleteEntryListener?,
) : ViewBinder<ExerciseSessionEntry, View> {

    private lateinit var logger: HealthConnectLogger

    override fun newView(parent: ViewGroup): View {
        val context = parent.context.applicationContext
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_session_entry, parent, false)
    }

    override fun bind(view: View, data: ExerciseSessionEntry, index: Int) {
        val container = view.findViewById<LinearLayout>(R.id.item_data_entry_container)
        val divider = view.findViewById<LinearLayout>(R.id.item_data_entry_divider)
        val header = view.findViewById<TextView>(R.id.item_data_entry_header)
        val title = view.findViewById<TextView>(R.id.item_data_entry_title)
        val notes = view.findViewById<TextView>(R.id.item_data_entry_notes)
        val deleteButton = view.findViewById<ImageButton>(R.id.item_data_entry_delete)
        val mapView = view.findViewById<MapView>(R.id.map_view)
        val mapContainer = view.findViewById<RoundView>(R.id.map_round_view)
        logger.logImpression(DataEntriesElement.EXERCISE_SESSION_ENTRY_BUTTON)
        if (showSecondAction) {
            logger.logImpression(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
        }
        title.text = data.title
        title.contentDescription = data.titleA11y
        header.text = data.header
        header.contentDescription = data.headerA11y
        notes.isVisible = !data.notes.isNullOrBlank()
        notes.text = data.notes
        deleteButton.isVisible = showSecondAction
        divider.isVisible = showSecondAction
        mapContainer.isVisible = (data.route != null)
        if (data.route != null) {
            mapView.setRoute(data.route)
            logger.logImpression(DataEntriesElement.EXERCISE_SESSION_MAP_VIEW)
        }

        deleteButton.contentDescription =
            view.resources.getString(
                R.string.data_point_action_content_description, data.headerA11y)
        deleteButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
            onDeleteEntryClicked?.onDeleteEntry(data.uuid, data.dataType, index)
        }
        if (showSecondAction) {
            container.setOnClickListener {
                logger.logInteraction(DataEntriesElement.EXERCISE_SESSION_ENTRY_BUTTON)
                onItemClickedListener?.onItemClicked(data.uuid, index)
            }
        } else {
            container.isClickable = false
        }
    }
}
