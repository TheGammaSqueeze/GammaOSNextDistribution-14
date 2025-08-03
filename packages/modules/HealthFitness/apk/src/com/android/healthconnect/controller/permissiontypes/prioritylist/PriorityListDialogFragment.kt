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
package com.android.healthconnect.controller.permissiontypes.prioritylist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.PermissionTypesElement
import dagger.hilt.android.AndroidEntryPoint

/** A {@link DialogFragment} that displays the apps in priority order. */
@AndroidEntryPoint(DialogFragment::class)
class PriorityListDialogFragment() : Hilt_PriorityListDialogFragment() {

    private val viewModel: HealthPermissionTypesViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val priorityList: List<AppMetadata> = viewModel.editedPriorityList.value ?: emptyList()
        val dataCategoryName: String? = viewModel.categoryLabel.value
        val parentView: View =
            LayoutInflater.from(context).inflate(R.layout.dialog_layout_priority, null)
        val priorityListView: RecyclerView =
            parentView.findViewById(R.id.priority_list_recycle_view)
        val adapter = PriorityListAdapter(priorityList, viewModel)

        priorityListView.layoutManager = PriorityListLinearLayoutManager(context, adapter)
        priorityListView.adapter = adapter
        val messageView = parentView.findViewById<TextView>(R.id.priority_list_message)
        dataCategoryName?.let { messageView.text = getString(R.string.priority_dialog_message, it) }

        val callback = PriorityListItemMoveCallback(adapter)
        val priorityListMover = ItemTouchHelper(callback)
        adapter.setOnItemDragStartedListener(priorityListMover)
        priorityListMover.attachToRecyclerView(priorityListView)

        return AlertDialogBuilder(this, PermissionTypesElement.SET_APP_PRIORITY_DIALOG_CONTAINER)
            .setView(parentView)
            .setNeutralButton(
                android.R.string.cancel,
                PermissionTypesElement.SET_APP_PRIORITY_DIALOG_CANCEL_BUTTON)
            .setPositiveButton(
                R.string.priority_dialog_positive_button,
                PermissionTypesElement.SET_APP_PRIORITY_DIALOG_SAVE_BUTTON) { _, _ ->
                    setFragmentResult(PRIORITY_UPDATED_EVENT, bundle(adapter.getPackageNameList()))
                }
            .create()
    }

    fun bundle(list: List<String>): Bundle {
        val resultBundle = Bundle()
        resultBundle.putStringArrayList(
            PRIORITY_UPDATED_EVENT, list.toMutableList() as ArrayList<String>)
        return resultBundle
    }

    companion object {
        const val TAG = "PriorityDialogFragment"
        const val PRIORITY_UPDATED_EVENT = "PRIORITY_UPDATED_EVENT"
    }
}
