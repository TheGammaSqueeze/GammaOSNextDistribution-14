/**
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.dataentries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.ExerciseSessionEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SeriesDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SleepSessionEntry
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.Empty
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.Loading
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.WithData
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.END_TIME
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionConstants.START_TIME
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionState
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.DeletionViewModel
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.recyclerview.RecyclerViewAdapter
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setTitle
import com.android.healthconnect.controller.utils.setupMenu
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/** Fragment to show health data entries by date. */
@AndroidEntryPoint(Fragment::class)
class DataEntriesFragment : Hilt_DataEntriesFragment() {

    @Inject lateinit var logger: HealthConnectLogger
    private val pageName = PageName.DATA_ENTRIES_PAGE

    private lateinit var permissionType: HealthPermissionType
    private val entriesViewModel: DataEntriesFragmentViewModel by viewModels()
    private val deletionViewModel: DeletionViewModel by activityViewModels()

    private lateinit var dateNavigationView: DateNavigationView
    private lateinit var entriesRecyclerView: RecyclerView
    private lateinit var noDataView: TextView
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var adapter: RecyclerViewAdapter

    private val onClickEntryListener by lazy {
        object : OnClickEntryListener {
            override fun onItemClicked(id: String, index: Int) {
                findNavController()
                    .navigate(
                        R.id.action_dataEntriesFragment_to_dataEntryDetailsFragment,
                        DataEntryDetailsFragment.createBundle(
                            permissionType, id, showDataOrigin = true))
            }
        }
    }
    private val onDeleteEntryListener by lazy {
        object : OnDeleteEntryListener {
            override fun onDeleteEntry(
                id: String,
                dataType: DataType,
                index: Int,
                startTime: Instant?,
                endTime: Instant?
            ) {
                deleteEntry(id, dataType, index, startTime, endTime)
            }
        }
    }
    private val aggregationViewBinder by lazy { AggregationViewBinder() }
    private val entryViewBinder by lazy { EntryItemViewBinder(onDeleteEntryListener) }
    private val sleepSessionViewBinder by lazy {
        SleepSessionItemViewBinder(
            onDeleteEntryListenerClicked = onDeleteEntryListener,
            onItemClickedListener = onClickEntryListener)
    }
    private val exerciseSessionItemViewBinder by lazy {
        ExerciseSessionItemViewBinder(
            onDeleteEntryClicked = onDeleteEntryListener,
            onItemClickedListener = onClickEntryListener)
    }
    private val seriesDataItemViewBinder by lazy {
        SeriesDataItemViewBinder(
            onDeleteEntryClicked = onDeleteEntryListener,
            onItemClickedListener = onClickEntryListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        logger.setPageId(pageName)

        val view = inflater.inflate(R.layout.fragment_data_entries, container, false)
        if (requireArguments().containsKey(PERMISSION_TYPE_KEY)) {
            permissionType =
                arguments?.getSerializable(PERMISSION_TYPE_KEY, HealthPermissionType::class.java)
                    ?: throw IllegalArgumentException("PERMISSION_TYPE_KEY can't be null!")
        }
        setTitle(fromPermissionType(permissionType).uppercaseLabel)
        setupMenu(R.menu.set_data_units_with_send_feedback_and_help, viewLifecycleOwner, logger) {
            menuItem ->
            when (menuItem.itemId) {
                R.id.menu_open_units -> {
                    logger.logImpression(ToolbarElement.TOOLBAR_UNITS_BUTTON)
                    findNavController().navigate(R.id.action_dataEntriesFragment_to_unitsFragment)
                    true
                }
                else -> false
            }
        }
        logger.logImpression(ToolbarElement.TOOLBAR_SETTINGS_BUTTON)

        dateNavigationView = view.findViewById(R.id.date_navigation_view)
        noDataView = view.findViewById(R.id.no_data_view)
        errorView = view.findViewById(R.id.error_view)
        loadingView = view.findViewById(R.id.loading)
        adapter =
            RecyclerViewAdapter.Builder()
                .setViewBinder(FormattedDataEntry::class.java, entryViewBinder)
                .setViewBinder(SleepSessionEntry::class.java, sleepSessionViewBinder)
                .setViewBinder(ExerciseSessionEntry::class.java, exerciseSessionItemViewBinder)
                .setViewBinder(SeriesDataEntry::class.java, seriesDataItemViewBinder)
                .setViewBinder(FormattedAggregation::class.java, aggregationViewBinder)
                .build()
        entriesRecyclerView =
            view.findViewById<RecyclerView?>(R.id.data_entries_list).also {
                it.adapter = adapter
                it.layoutManager = LinearLayoutManager(context, VERTICAL, false)
            }

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dateNavigationView.setDateChangedListener(
            object : DateNavigationView.OnDateChangedListener {
                override fun onDateChanged(selectedDate: Instant) {
                    entriesViewModel.loadData(permissionType, selectedDate)
                }
            })
        observeDeleteState()
        observeEntriesUpdates()
    }

    override fun onResume() {
        super.onResume()
        setTitle(fromPermissionType(permissionType).uppercaseLabel)
        if (entriesViewModel.currentSelectedDate.value != null) {
            val date = entriesViewModel.currentSelectedDate.value!!
            dateNavigationView.setDate(date)
            entriesViewModel.loadData(permissionType, date)
        } else {
            entriesViewModel.loadData(permissionType, dateNavigationView.getDate())
        }

        logger.setPageId(pageName)
        logger.logPageImpression()
    }

    private fun observeDeleteState() {
        deletionViewModel.deletionParameters.observe(viewLifecycleOwner) { params ->
            when (params.deletionState) {
                DeletionState.STATE_DELETION_SUCCESSFUL -> {
                    val index = (params.deletionType as DeletionType.DeleteDataEntry).index
                    adapter.notifyItemRemoved(index)
                    entriesViewModel.loadData(permissionType, dateNavigationView.getDate())
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun observeEntriesUpdates() {
        entriesViewModel.dataEntries.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Loading -> {
                    loadingView.isVisible = true
                    noDataView.isVisible = false
                    errorView.isVisible = false
                    entriesRecyclerView.isVisible = false
                }
                is Empty -> {
                    noDataView.isVisible = true
                    loadingView.isVisible = false
                    errorView.isVisible = false
                    entriesRecyclerView.isVisible = false
                }
                is WithData -> {
                    entriesRecyclerView.isVisible = true
                    adapter.updateData(state.entries)
                    entriesRecyclerView.scrollToPosition(0)
                    errorView.isVisible = false
                    noDataView.isVisible = false
                    loadingView.isVisible = false
                }
                is LoadingFailed -> {
                    errorView.isVisible = true
                    loadingView.isVisible = false
                    noDataView.isVisible = false
                    entriesRecyclerView.isVisible = false
                }
            }
        }
    }

    private fun deleteEntry(
        uuid: String,
        dataType: DataType,
        index: Int,
        startTime: Instant?,
        endTime: Instant?
    ) {
        val deletionType = DeletionType.DeleteDataEntry(uuid, dataType, index)

        if (deletionType.dataType == DataType.MENSTRUATION_PERIOD) {
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT,
                bundleOf(
                    DELETION_TYPE to deletionType, START_TIME to startTime, END_TIME to endTime))
        } else {
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
        }
    }
}
