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
package com.android.healthconnect.controller.datasources.appsources

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.datasources.DataSourcesViewModel
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.app.AppUtils
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.logging.DataSourcesElement
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.text.NumberFormat

/** RecyclerView adapter that holds the list of app sources for this [HealthDataCategory]. */
class AppSourcesAdapter(
    private val context: Context,
    private val appUtils: AppUtils,
    priorityList: List<AppMetadata>,
    potentialAppSourcesList: List<AppMetadata>,
    private val dataSourcesViewModel: DataSourcesViewModel,
    private val category: @HealthDataCategoryInt Int,
    private val onAppRemovedListener: OnAppRemovedFromPriorityListListener,
    private val itemMoveAttachCallbackListener: ItemMoveAttachCallbackListener,
) : RecyclerView.Adapter<AppSourcesAdapter.AppSourcesItemViewHolder?>() {

    private var listener: ItemTouchHelper? = null
    private var priorityList = priorityList.toMutableList()
    private var potentialAppSourcesList = potentialAppSourcesList.toMutableList()
    private var isEditMode = false

    private val POSITION_CHANGED_PAYLOAD = Any()

    private var logger: HealthConnectLogger
    var logName: ElementName = DataSourcesElement.DATA_TOTALS_CARD

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    interface OnAppRemovedFromPriorityListListener {
        fun onAppRemovedFromPriorityList()
    }

    /**
     * Used for re-attaching the onItemMovedCallback to the RecyclerView when we exit the edit mode
     */
    interface ItemMoveAttachCallbackListener {
        fun attachCallback()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AppSourcesItemViewHolder {
        return AppSourcesItemViewHolder(
            LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.widget_app_source_layout, viewGroup, false),
            listener)
    }

    override fun onBindViewHolder(viewHolder: AppSourcesItemViewHolder, position: Int) {
        viewHolder.bind(position, priorityList[position], isOnlyApp = priorityList.size == 1)
    }

    override fun getItemCount(): Int {
        return priorityList.size
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val movedAppInfo: AppMetadata = priorityList.removeAt(fromPosition)
        priorityList.add(
            if (toPosition > fromPosition + 1) toPosition - 1 else toPosition, movedAppInfo)
        notifyItemMoved(fromPosition, toPosition)
        if (toPosition < fromPosition) {
            notifyItemRangeChanged(
                toPosition, fromPosition - toPosition + 1, POSITION_CHANGED_PAYLOAD)
        } else {
            notifyItemRangeChanged(
                fromPosition, toPosition - fromPosition + 1, POSITION_CHANGED_PAYLOAD)
        }
        dataSourcesViewModel.updatePriorityList(priorityList.map { it.packageName }, category)
        return true
    }

    fun setOnItemDragStartedListener(listener: ItemTouchHelper) {
        this.listener = listener
    }

    private fun removeOnItemDragStartedListener() {
        listener = null
    }

    fun toggleEditMode(isEditMode: Boolean) {
        this.isEditMode = isEditMode
        if (!isEditMode) {
            itemMoveAttachCallbackListener.attachCallback()
        } else {
            removeOnItemDragStartedListener()
        }
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        priorityList.removeAt(position)
        notifyItemRemoved(position)
    }

    /** Shows a single item of the priority list. */
    inner class AppSourcesItemViewHolder(
        itemView: View,
        private val onItemDragStartedListener: ItemTouchHelper?
    ) : RecyclerView.ViewHolder(itemView) {
        private val EDIT_MODE_TAG = "edit_mode"
        private val DRAG_MODE_TAG = "drag_mode"
        private val appPositionView: TextView
        private val appNameView: TextView
        private val appSourceSummary: TextView
        private val actionView: View
        private val actionIconBackground: ImageView

        init {
            appPositionView = itemView.findViewById(R.id.app_position)
            appNameView = itemView.findViewById(R.id.app_name)
            actionView = itemView.findViewById(R.id.action_icon)
            actionIconBackground = itemView.findViewById(R.id.action_icon_background)
            appSourceSummary = itemView.findViewById(R.id.app_source_summary)
        }

        fun bind(appPosition: Int, appMetadata: AppMetadata, isOnlyApp: Boolean) {
            // Adding 1 to position as position starts from 0 but should show to the user starting
            // from 1.
            val positionString: String = NumberFormat.getIntegerInstance().format(appPosition + 1)
            appPositionView.text = positionString
            appNameView.text = appMetadata.appName
            actionIconBackground.contentDescription =
                context.getString(R.string.reorder_button_content_description, appNameView.text)

            if (appUtils.isDefaultApp(context, appMetadata.packageName)) {
                appSourceSummary.visibility = View.VISIBLE
                appSourceSummary.text = context.getString(R.string.default_app_summary)
            } else {
                appSourceSummary.visibility = View.GONE
            }

            if (isEditMode) {
                setupItemForEditMode(appPosition)
            } else {
                setupItemForDragMode(isOnlyApp)
            }

            logger.logImpression(DataSourcesElement.APP_SOURCE_BUTTON)
        }

        private fun setupItemForEditMode(appPosition: Int) {
            actionView.isClickable = true
            actionView.visibility = View.VISIBLE
            actionIconBackground.background =
                AttributeResolver.getDrawable(itemView.context, R.attr.closeIcon)
            actionIconBackground.contentDescription =
                context.getString(R.string.remove_button_content_description, appNameView.text)
            actionIconBackground.tag = EDIT_MODE_TAG
            actionView.setOnTouchListener(null)
            actionView.setOnClickListener {
                logger.logInteraction(DataSourcesElement.REMOVE_APP_SOURCE_BUTTON)

                val currentPriority = priorityList.toMutableList()
                val removedItem = currentPriority.removeAt(appPosition)
                dataSourcesViewModel.setEditedPriorityList(currentPriority)
                dataSourcesViewModel.updatePriorityList(
                    currentPriority.map { it.packageName }, category)

                potentialAppSourcesList.add(removedItem)
                dataSourcesViewModel.loadPotentialAppSources(category, false)
                dataSourcesViewModel.setEditedPotentialAppSources(potentialAppSourcesList)

                removeItem(appPosition)
                onAppRemovedListener.onAppRemovedFromPriorityList()
            }
            logger.logImpression(DataSourcesElement.REMOVE_APP_SOURCE_BUTTON)
        }

        // These items are not clickable and so onTouch does not need to reimplement click
        // conditions.
        // Drag&drop in accessibility mode (talk back) is implemented as custom actions.
        @SuppressLint("ClickableViewAccessibility")
        private fun setupItemForDragMode(isOnlyApp: Boolean) {
            // Hide drag icon if this is the only app in the list
            if (isOnlyApp) {
                actionView.visibility = View.INVISIBLE
                actionView.isClickable = false
            } else {
                actionIconBackground.background =
                    AttributeResolver.getDrawable(itemView.context, R.attr.priorityItemDragIcon)
                actionIconBackground.tag = DRAG_MODE_TAG
                actionView.setOnClickListener(null)
                actionView.setOnTouchListener { _, event ->
                    logger.logInteraction(DataSourcesElement.REORDER_APP_SOURCE_BUTTON)
                    if (event.action == MotionEvent.ACTION_DOWN ||
                        event.action == MotionEvent.ACTION_UP) {
                        onItemDragStartedListener?.startDrag(this)
                    }
                    false
                }
                logger.logImpression(DataSourcesElement.REORDER_APP_SOURCE_BUTTON)
            }
        }
    }
}
