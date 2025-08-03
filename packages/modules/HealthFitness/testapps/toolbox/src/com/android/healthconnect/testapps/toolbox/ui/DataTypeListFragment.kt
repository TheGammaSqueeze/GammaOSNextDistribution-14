/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.testapps.toolbox.Constants.HealthDataCategory
import com.android.healthconnect.testapps.toolbox.Constants.HealthPermissionType
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.adapters.TextViewListAdapter
import com.android.healthconnect.testapps.toolbox.adapters.TextViewListViewHolder

class DataTypeListFragment : Fragment() {

    private lateinit var mDataSet: List<HealthPermissionType>
    private lateinit var mNavigationController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_list_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val category =
            arguments?.getSerializable("category", HealthDataCategory::class.java)
                ?: throw IllegalArgumentException("Please pass category.")

        mDataSet = category.healthPermissionTypes

        val recyclerView: RecyclerView = view.findViewById(R.id.list_recycler_view)
        recyclerView.adapter =
            TextViewListAdapter(mDataSet) { viewHolder: TextViewListViewHolder, position: Int ->
                onBindViewHolderCallback(viewHolder, position)
            }
        mNavigationController = findNavController()
    }

    private fun onBindViewHolderCallback(viewHolder: TextViewListViewHolder, position: Int) {
        val textView = viewHolder.textView
        textView.text = viewHolder.itemView.context.getString(mDataSet[position].title)

        textView.setOnClickListener {
            if (mDataSet[position].recordClass == null) {
                Toast.makeText(context, R.string.not_implemented, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bundle = bundleOf("permissionType" to mDataSet[position])
            mNavigationController.navigate(R.id.action_dataTypeList_to_insertRecord, bundle)
        }
    }
}
