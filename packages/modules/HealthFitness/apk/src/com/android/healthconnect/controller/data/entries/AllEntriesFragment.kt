/*
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
package com.android.healthconnect.controller.data.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Empty
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Loading
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.With
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationView
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.recyclerview.RecyclerViewAdapter
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setTitle
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.AppHeaderPreference
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/** Fragment to show health data entries. */
@AndroidEntryPoint(Fragment::class)
class AllEntriesFragment : Hilt_AllEntriesFragment() {

    @Inject lateinit var logger: HealthConnectLogger
    // TODO(b/291249677): Add logging.

    private lateinit var permissionType: HealthPermissionType
    private val entriesViewModel: EntriesViewModel by viewModels()

    private lateinit var header: AppHeaderPreference
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
                        R.id.action_entriesAndAccessFragment_to_dataEntryDetailsFragment,
                        DataEntryDetailsFragment.createBundle(
                            permissionType, id, showDataOrigin = true))
            }
        }
    }
    private val aggregationViewBinder by lazy {
        com.android.healthconnect.controller.data.entries.AggregationViewBinder()
    }
    private val entryViewBinder by lazy { EntryItemViewBinder() }
    private val sectionTitleViewBinder by lazy { SectionTitleViewBinder() }
    private val sleepSessionViewBinder by lazy {
        SleepSessionItemViewBinder(onItemClickedListener = onClickEntryListener)
    }
    private val exerciseSessionItemViewBinder by lazy {
        ExerciseSessionItemViewBinder(onItemClickedListener = onClickEntryListener)
    }
    private val seriesDataItemViewBinder by lazy {
        SeriesDataItemViewBinder(onItemClickedListener = onClickEntryListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_entries, container, false)
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
                    findNavController()
                        .navigate(R.id.action_entriesAndAccessFragment_to_unitFragment)
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
                .setViewBinder(FormattedEntry.FormattedDataEntry::class.java, entryViewBinder)
                .setViewBinder(FormattedEntry.SleepSessionEntry::class.java, sleepSessionViewBinder)
                .setViewBinder(
                    FormattedEntry.ExerciseSessionEntry::class.java, exerciseSessionItemViewBinder)
                .setViewBinder(FormattedEntry.SeriesDataEntry::class.java, seriesDataItemViewBinder)
                .setViewBinder(
                    FormattedEntry.FormattedAggregation::class.java, aggregationViewBinder)
                .setViewBinder(
                    FormattedEntry.EntryDateSectionHeader::class.java, sectionTitleViewBinder)
                .build()
        entriesRecyclerView =
            view.findViewById<RecyclerView?>(R.id.data_entries_list).also {
                it.adapter = adapter
                it.layoutManager = LinearLayoutManager(context, VERTICAL, false)
            }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dateNavigationView.setDateChangedListener(
            object : DateNavigationView.OnDateChangedListener {
                override fun onDateChanged(
                    displayedStartDate: Instant,
                    period: DateNavigationPeriod
                ) {
                    entriesViewModel.loadEntries(permissionType, displayedStartDate, period)
                }
            })

        header = AppHeaderPreference(requireContext())
        observeEntriesUpdates()
    }

    override fun onResume() {
        super.onResume()
        setTitle(fromPermissionType(permissionType).uppercaseLabel)
        if (entriesViewModel.currentSelectedDate.value != null &&
            entriesViewModel.period.value != null) {
            val date = entriesViewModel.currentSelectedDate.value!!
            val selectedPeriod = entriesViewModel.period.value!!
            dateNavigationView.setDate(date)
            dateNavigationView.setPeriod(selectedPeriod)
            entriesViewModel.loadEntries(permissionType, date, selectedPeriod)
        } else {
            entriesViewModel.loadEntries(
                permissionType, dateNavigationView.getDate(), dateNavigationView.getPeriod())
        }
        //
        //        logger.setPageId(pageName)
        //        logger.logPageImpression()
    }

    private fun observeEntriesUpdates() {
        entriesViewModel.entries.observe(viewLifecycleOwner) { state ->
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
                is With -> {
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
}
