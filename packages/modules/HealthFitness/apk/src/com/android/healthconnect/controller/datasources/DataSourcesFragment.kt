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
package com.android.healthconnect.controller.datasources

import android.health.connect.HealthDataCategory
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment.Companion.CATEGORY_KEY
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.AggregationCardsState
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.PotentialAppSourcesState
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.PriorityListState
import com.android.healthconnect.controller.datasources.appsources.AppSourcesAdapter
import com.android.healthconnect.controller.datasources.appsources.AppSourcesPreference
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.lowercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.app.AppUtils
import com.android.healthconnect.controller.shared.preference.CardContainerPreference
import com.android.healthconnect.controller.shared.preference.HeaderPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.DeviceInfoUtilsImpl
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.DataSourcesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.setupMenu
import com.android.healthconnect.controller.utils.setupSharedMenu
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.SettingsSpinnerAdapter
import com.android.settingslib.widget.SettingsSpinnerPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(HealthPreferenceFragment::class)
class DataSourcesFragment :
    Hilt_DataSourcesFragment(), AppSourcesAdapter.OnAppRemovedFromPriorityListListener {

    companion object {
        private const val DATA_TYPE_SPINNER_PREFERENCE_GROUP = "data_type_spinner_group"
        private const val DATA_TOTALS_PREFERENCE_GROUP = "data_totals_group"
        private const val DATA_TOTALS_PREFERENCE_KEY = "data_totals_preference"
        private const val APP_SOURCES_PREFERENCE_GROUP = "app_sources_group"
        private const val APP_SOURCES_PREFERENCE_KEY = "app_sources"
        private const val ADD_AN_APP_PREFERENCE_KEY = "add_an_app"
        private const val NON_EMPTY_FOOTER_PREFERENCE_KEY = "data_sources_footer"
        private const val EMPTY_STATE_HEADER_PREFERENCE_KEY = "empty_state_header"
        private const val EMPTY_STATE_FOOTER_PREFERENCE_KEY = "empty_state_footer"
        private const val IS_EDIT_MODE = "is_edit_mode"

        private val dataSourcesCategories =
            arrayListOf(HealthDataCategory.ACTIVITY, HealthDataCategory.SLEEP)
    }

    init {
        this.setPageName(PageName.DATA_SOURCES_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var appUtils: AppUtils
    private var isEditMode = false

    private val dataSourcesViewModel: DataSourcesViewModel by activityViewModels()
    private lateinit var spinnerPreference: SettingsSpinnerPreference
    private lateinit var dataSourcesCategoriesStrings: List<String>
    private var currentCategorySelection: @HealthDataCategoryInt Int = HealthDataCategory.ACTIVITY
    @Inject lateinit var timeSource: TimeSource

    private val dataTypeSpinnerPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(DATA_TYPE_SPINNER_PREFERENCE_GROUP)
    }

    private val dataTotalsPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(DATA_TOTALS_PREFERENCE_GROUP)
    }

    private val appSourcesPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(APP_SOURCES_PREFERENCE_GROUP)
    }

    private val nonEmptyFooterPreference: FooterPreference? by lazy {
        preferenceScreen.findPreference(NON_EMPTY_FOOTER_PREFERENCE_KEY)
    }

    private val onEditMenuItemSelected: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_edit -> {
                editPriorityList()
                true
            }
            else -> false
        }
    }

    private var cardContainerPreference: CardContainerPreference? = null

    override fun onAppRemovedFromPriorityList() {
        exitEditMode()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.data_sources_and_priority_screen, rootKey)
        dataSourcesCategoriesStrings =
            dataSourcesCategories.map { category -> getString(category.uppercaseTitle()) }

        if (requireArguments().containsKey(CATEGORY_KEY) && savedInstanceState == null) {

            // Only require this from the HealthPermissionTypes screen
            // When navigating here from the Manage Data screen we pass Unknown
            // so that going back and forth to this screen does not restrict users to just one
            // category
            val argCategory = requireArguments().getInt(CATEGORY_KEY)
            if (argCategory != HealthDataCategory.UNKNOWN) {
                currentCategorySelection = argCategory
                dataSourcesViewModel.setCurrentSelection(currentCategorySelection)
            }
        }

        setupSpinnerPreference()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_EDIT_MODE, isEditMode)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let { bundle ->
            val savedIsEditMode = bundle.getBoolean(IS_EDIT_MODE, false)
            isEditMode = savedIsEditMode
        }

        setLoading(true)
        val currentStringSelection = spinnerPreference.selectedItem
        currentCategorySelection =
            dataSourcesCategories[dataSourcesCategoriesStrings.indexOf(currentStringSelection)]
        dataSourcesViewModel.loadData(currentCategorySelection)

        dataSourcesViewModel.dataSourcesAndAggregationsInfo.observe(viewLifecycleOwner) {
            dataSourcesInfo ->
            if (dataSourcesInfo.isLoading()) {
                setLoading(true)
            } else if (dataSourcesInfo.isLoadingFailed()) {
                setLoading(false)
                setError(true)
            } else if (dataSourcesInfo.isWithData()) {
                setLoading(false)

                val priorityList =
                    (dataSourcesInfo.priorityListState as PriorityListState.WithData).priorityList
                val potentialAppSources =
                    (dataSourcesInfo.potentialAppSourcesState as PotentialAppSourcesState.WithData)
                        .appSources
                val cardInfos =
                    (dataSourcesInfo.aggregationCardsState as AggregationCardsState.WithData)
                        .dataTotals

                if (priorityList.isEmpty() && potentialAppSources.isEmpty()) {
                    addEmptyState()
                } else {
                    updateMenu(priorityList.size > 1 && !isEditMode)
                    updateAppSourcesSection(priorityList, potentialAppSources)
                    updateDataTotalsSection(cardInfos)
                }
            }
        }

        dataSourcesViewModel.updatedAggregationCardsData.observe(viewLifecycleOwner) {
            aggregationCardsData ->
            when (aggregationCardsData) {
                is AggregationCardsState.Loading -> {
                    updateAggregations(listOf(), true)
                }
                is AggregationCardsState.LoadingFailed -> {
                    updateDataTotalsSection(listOf())
                }
                is AggregationCardsState.WithData -> {
                    updateAggregations(aggregationCardsData.dataTotals, false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataSourcesViewModel.loadData(currentCategorySelection)
    }

    private fun updateMenu(shouldShowEditButton: Boolean) {
        if (shouldShowEditButton) {
            setupMenu(R.menu.data_sources, viewLifecycleOwner, logger, onEditMenuItemSelected)
        } else {
            setupSharedMenu(viewLifecycleOwner, logger)
        }
    }

    @VisibleForTesting
    fun editPriorityList() {
        isEditMode = true
        updateMenu(shouldShowEditButton = false)
        appSourcesPreferenceGroup?.removePreferenceRecursively(ADD_AN_APP_PREFERENCE_KEY)
        val appSourcesPreference =
            preferenceScreen?.findPreference<AppSourcesPreference>(APP_SOURCES_PREFERENCE_KEY)
        appSourcesPreference?.toggleEditMode(true)
    }

    private fun exitEditMode() {
        appSourcesPreferenceGroup
            ?.findPreference<AppSourcesPreference>(APP_SOURCES_PREFERENCE_KEY)
            ?.toggleEditMode(false)
        updateMenu(dataSourcesViewModel.getEditedPriorityList().size > 1)
        updateAddApp(dataSourcesViewModel.getEditedPotentialAppSources().isNotEmpty())
        isEditMode = false
    }

    /** Updates the priority list preference. */
    private fun updateAppSourcesSection(
        priorityList: List<AppMetadata>,
        potentialAppSources: List<AppMetadata>
    ) {
        removeEmptyState()
        appSourcesPreferenceGroup?.isVisible = true
        appSourcesPreferenceGroup?.removePreferenceRecursively(APP_SOURCES_PREFERENCE_KEY)

        dataSourcesViewModel.setEditedPriorityList(priorityList)
        appSourcesPreferenceGroup?.addPreference(
            AppSourcesPreference(
                    requireContext(),
                    appUtils,
                    dataSourcesViewModel,
                    currentCategorySelection,
                    this)
                .also {
                    it.key = APP_SOURCES_PREFERENCE_KEY
                    it.setEditMode(isEditMode)
                })

        updateAddApp(potentialAppSources.isNotEmpty() && !isEditMode)
        nonEmptyFooterPreference?.isVisible = true
    }

    /**
     * Shows the "Add an app" button when there is at least one potential app for the priority list.
     *
     * <p> Hides the button in edit mode and when there are no other potential apps for the priority
     * list.
     */
    private fun updateAddApp(shouldShow: Boolean) {
        appSourcesPreferenceGroup?.removePreferenceRecursively(ADD_AN_APP_PREFERENCE_KEY)

        if (!shouldShow) {
            return
        }

        appSourcesPreferenceGroup?.addPreference(
            HealthPreference(requireContext()).also {
                it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.addIcon)
                it.title = getString(R.string.data_sources_add_app)
                it.logName = DataSourcesElement.ADD_AN_APP_BUTTON
                it.key = ADD_AN_APP_PREFERENCE_KEY
                it.order = 100 // Arbitrary number to ensure the button is added at the end of the
                // priority list
                it.setOnPreferenceClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_dataSourcesFragment_to_addAnAppFragment,
                            bundleOf(CATEGORY_KEY to currentCategorySelection))
                    true
                }
            })
    }

    /** Populates the data totals section with aggregation cards if needed. */
    private fun updateDataTotalsSection(cardInfos: List<AggregationCardInfo>) {
        dataTotalsPreferenceGroup?.removePreferenceRecursively(DATA_TOTALS_PREFERENCE_KEY)
        // Do not show data cards when there are no apps on the priority list
        if (appSourcesPreferenceGroup?.isVisible == false) {
            return
        }

        if (cardInfos.isEmpty()) {
            dataTotalsPreferenceGroup?.isVisible = false
        } else {
            dataTotalsPreferenceGroup?.isVisible = true
            cardContainerPreference =
                CardContainerPreference(requireContext(), timeSource).also {
                    it.setAggregationCardInfo(cardInfos)
                    it.key = DATA_TOTALS_PREFERENCE_KEY
                }
            dataTotalsPreferenceGroup?.addPreference(
                (cardContainerPreference as CardContainerPreference))
        }
    }

    /** Updates the aggregation cards after a priority list change. */
    private fun updateAggregations(cardInfos: List<AggregationCardInfo>, isLoading: Boolean) {
        if (isLoading) {
            cardContainerPreference?.setLoading(true)
        } else {
            if (cardInfos.isEmpty()) {
                dataTotalsPreferenceGroup?.isVisible = false
            } else {
                dataTotalsPreferenceGroup?.isVisible = true
                cardContainerPreference?.setAggregationCardInfo(cardInfos)
                cardContainerPreference?.setLoading(false)
            }
        }
    }

    /**
     * The empty state of this fragment is represented by:
     * - no apps with write permissions for this category
     * - no apps with data for this category
     */
    private fun addEmptyState() {
        removeNonEmptyState()
        removeEmptyState()

        preferenceScreen.addPreference(getEmptyStateHeaderPreference())
        preferenceScreen.addPreference(getEmptyStateFooterPreference())
    }

    private fun removeEmptyState() {
        preferenceScreen.removePreferenceRecursively(EMPTY_STATE_HEADER_PREFERENCE_KEY)
        preferenceScreen.removePreferenceRecursively(EMPTY_STATE_FOOTER_PREFERENCE_KEY)
    }

    private fun removeNonEmptyState() {
        preferenceScreen.removePreferenceRecursively(APP_SOURCES_PREFERENCE_KEY)
        preferenceScreen.removePreferenceRecursively(ADD_AN_APP_PREFERENCE_KEY)
        preferenceScreen.removePreferenceRecursively(DATA_TOTALS_PREFERENCE_KEY)

        // We hide the preference group headers and footer instead of removing them
        appSourcesPreferenceGroup?.isVisible = false
        dataTotalsPreferenceGroup?.isVisible = false
        nonEmptyFooterPreference?.isVisible = false
    }

    private fun getEmptyStateHeaderPreference(): HeaderPreference {
        return HeaderPreference(requireContext()).also {
            it.setHeaderText(getString(R.string.data_sources_empty_state))
            it.key = EMPTY_STATE_HEADER_PREFERENCE_KEY
        }
    }

    private fun getEmptyStateFooterPreference(): FooterPreference {
        return FooterPreference(context).also {
            it.title =
                getString(
                    R.string.data_sources_empty_state_footer,
                    getString(currentCategorySelection.lowercaseTitle()))
            it.setLearnMoreText(getString(R.string.data_sources_help_link))
            it.setLearnMoreAction { DeviceInfoUtilsImpl().openHCGetStartedLink(requireActivity()) }
            it.key = EMPTY_STATE_FOOTER_PREFERENCE_KEY
        }
    }

    private fun setupSpinnerPreference() {
        spinnerPreference = SettingsSpinnerPreference(context)
        spinnerPreference.setAdapter(
            SettingsSpinnerAdapter<String>(context).also {
                it.addAll(dataSourcesCategoriesStrings)
            })

        spinnerPreference.setOnItemSelectedListener(
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    logger.logInteraction(DataSourcesElement.DATA_TYPE_SPINNER)

                    val currentCategory = dataSourcesCategories[position]
                    currentCategorySelection = dataSourcesCategories[position]
                    exitEditMode()

                    // Reload the data sources information when a new category has been selected
                    dataSourcesViewModel.loadData(currentCategory)
                    dataSourcesViewModel.setCurrentSelection(currentCategory)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            })

        spinnerPreference.setSelection(
            dataSourcesCategories.indexOf(dataSourcesViewModel.getCurrentSelection()))

        dataTypeSpinnerPreferenceGroup?.isVisible = true
        dataTypeSpinnerPreferenceGroup?.addPreference(spinnerPreference)
        logger.logImpression(DataSourcesElement.DATA_TYPE_SPINNER)
    }
}
