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
package com.android.healthconnect.controller.permissiontypes

import android.health.connect.HealthDataCategory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment.Companion.CATEGORY_KEY
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.filters.ChipPreference
import com.android.healthconnect.controller.filters.FilterChip
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.prioritylist.PriorityListDialogFragment
import com.android.healthconnect.controller.permissiontypes.prioritylist.PriorityListDialogFragment.Companion.PRIORITY_UPDATED_EVENT
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.lowercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionTypesElement
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.AppHeaderPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for health permission types. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class HealthPermissionTypesFragment : Hilt_HealthPermissionTypesFragment() {

    companion object {
        private const val TAG = "HealthPermissionTypesFT"
        private const val PERMISSION_TYPES_HEADER = "permission_types_header"
        private const val APP_FILTERS_PREFERENCE = "app_filters_preference"
        private const val PERMISSION_TYPES_CATEGORY = "permission_types"
        private const val MANAGE_DATA_CATEGORY = "manage_data_category"
        const val PERMISSION_TYPE_KEY = "permission_type_key"
        private const val APP_PRIORITY_BUTTON = "app_priority"
        private const val DELETE_CATEGORY_DATA_BUTTON = "delete_category_data"
    }

    init {
        this.setPageName(PageName.PERMISSION_TYPES_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var featureUtils: FeatureUtils

    @HealthDataCategoryInt private var category: Int = 0

    private val viewModel: HealthPermissionTypesViewModel by activityViewModels()

    private val mPermissionTypesHeader: AppHeaderPreference? by lazy {
        preferenceScreen.findPreference(PERMISSION_TYPES_HEADER)
    }

    private val mAppFiltersPreference: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(APP_FILTERS_PREFERENCE)
    }

    private val mPermissionTypes: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(PERMISSION_TYPES_CATEGORY)
    }

    private val mManageDataCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(MANAGE_DATA_CATEGORY)
    }

    private val mDeleteCategoryData: HealthPreference? by lazy {
        preferenceScreen.findPreference(DELETE_CATEGORY_DATA_BUTTON)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.health_permission_types_screen, rootKey)

        if (requireArguments().containsKey(CATEGORY_KEY)) {
            category = requireArguments().getInt(CATEGORY_KEY)
        }

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }

        mDeleteCategoryData?.logName = PermissionTypesElement.DELETE_CATEGORY_DATA_BUTTON
        mDeleteCategoryData?.title =
            getString(R.string.delete_category_data_button, getString(category.lowercaseTitle()))
        mDeleteCategoryData?.setOnPreferenceClickListener {
            val deletionType = DeletionType.DeletionTypeCategoryData(category = category)
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mPermissionTypesHeader?.icon = category.icon(requireContext())
        mPermissionTypesHeader?.title = getString(category.uppercaseTitle())
        viewModel.loadData(category)
        viewModel.loadAppsWithData(category)

        setupMenu(R.menu.set_data_units_with_send_feedback_and_help, viewLifecycleOwner, logger) {
            menuItem ->
            when (menuItem.itemId) {
                R.id.menu_open_units -> {
                    logger.logImpression(ToolbarElement.TOOLBAR_UNITS_BUTTON)
                    findNavController().navigate(R.id.action_healthPermissionTypes_to_unitsFragment)
                    true
                }
                else -> false
            }
        }

        viewModel.permissionTypesData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HealthPermissionTypesViewModel.PermissionTypesState.Loading -> {}
                is HealthPermissionTypesViewModel.PermissionTypesState.WithData -> {
                    updatePermissionTypesList(state.permissionTypes)
                }
            }
        }
        viewModel.appsWithData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HealthPermissionTypesViewModel.AppsWithDataFragmentState.Loading -> {}
                is HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData -> {
                    if (state.appsWithData.size > 1) {
                        addAppFilters(state.appsWithData)
                        mAppFiltersPreference?.isVisible = true
                    } else {
                        mAppFiltersPreference?.isVisible = false
                    }
                }
            }
        }
        childFragmentManager.setFragmentResultListener(PRIORITY_UPDATED_EVENT, this) { _, bundle ->
            bundle.getStringArrayList(PRIORITY_UPDATED_EVENT)?.let {
                try {
                    viewModel.updatePriorityList(category, it)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to update priorities!", ex)
                    Toast.makeText(requireContext(), R.string.default_error, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        if (!featureUtils.isNewAppPriorityEnabled()) {
            viewModel.priorityList.observe(viewLifecycleOwner) { state ->
                when (state) {
                    is HealthPermissionTypesViewModel.PriorityListState.Loading -> {
                        mManageDataCategory?.removePreferenceRecursively(APP_PRIORITY_BUTTON)
                    }
                    is HealthPermissionTypesViewModel.PriorityListState.LoadingFailed -> {
                        mManageDataCategory?.removePreferenceRecursively(APP_PRIORITY_BUTTON)
                    }
                    is HealthPermissionTypesViewModel.PriorityListState.WithData -> {
                        updateOldPriorityButton(state.priorityList)
                    }
                }
            }
        } else {
            // Add the new priority list button
            updateNewPriorityButton()
        }
    }

    private fun updateNewPriorityButton() {
        mManageDataCategory?.removePreferenceRecursively(APP_PRIORITY_BUTTON)

        // Only display the priority button for Activity and Sleep categories
        if (category !in setOf(HealthDataCategory.ACTIVITY, HealthDataCategory.SLEEP)) {
            return
        }

        val newPriorityButton =
            HealthPreference(requireContext()).also {
                it.title = resources.getString(R.string.data_sources_and_priority_title)
                it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.appPriorityIcon)
                it.logName = PermissionTypesElement.DATA_SOURCES_AND_PRIORITY_BUTTON
                it.key = APP_PRIORITY_BUTTON
                it.order = 4
                it.setOnPreferenceClickListener {
                    // Navigate to the data sources fragment
                    findNavController()
                        .navigate(
                            R.id.action_healthPermissionTypes_to_dataSourcesAndPriority,
                            bundleOf(CATEGORY_KEY to category))
                    true
                }
            }

        mManageDataCategory?.addPreference(newPriorityButton)
    }

    private fun updateOldPriorityButton(priorityList: List<AppMetadata>) {
        mManageDataCategory?.removePreferenceRecursively(APP_PRIORITY_BUTTON)

        // Only display the priority button for Activity and Sleep categories
        if (category !in setOf(HealthDataCategory.ACTIVITY, HealthDataCategory.SLEEP)) {
            return
        }

        if (priorityList.size < 2) {
            return
        }

        val appPriorityButton =
            HealthPreference(requireContext()).also {
                it.title = resources.getString(R.string.app_priority_button)
                it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.appPriorityIcon)
                it.logName = PermissionTypesElement.SET_APP_PRIORITY_BUTTON
                it.summary = priorityList.first().appName
                it.key = APP_PRIORITY_BUTTON
                it.order = 4
                it.setOnPreferenceClickListener {
                    viewModel.setEditedPriorityList(priorityList)
                    viewModel.setCategoryLabel(getString(category.lowercaseTitle()))
                    PriorityListDialogFragment()
                        .show(childFragmentManager, PriorityListDialogFragment.TAG)
                    true
                }
            }
        mManageDataCategory?.addPreference(appPriorityButton)
    }

    private fun updatePermissionTypesList(permissionTypeList: List<HealthPermissionType>) {
        mDeleteCategoryData?.isEnabled = permissionTypeList.isNotEmpty()
        mPermissionTypes?.removeAll()
        if (permissionTypeList.isEmpty()) {
            mPermissionTypes?.addPreference(
                Preference(requireContext()).also { it.setSummary(R.string.no_categories) })
            return
        }
        permissionTypeList.forEach { permissionType ->
            mPermissionTypes?.addPreference(
                HealthPreference(requireContext()).also {
                    it.setTitle(fromPermissionType(permissionType).uppercaseLabel)
                    it.logName = PermissionTypesElement.PERMISSION_TYPE_BUTTON
                    it.setOnPreferenceClickListener {
                        findNavController()
                            .navigate(
                                R.id.action_healthPermissionTypes_to_healthDataAccess,
                                bundleOf(PERMISSION_TYPE_KEY to permissionType))
                        true
                    }
                })
        }
    }

    private fun addAppFilters(appsWithHealthPermissions: List<AppMetadata>) {
        mAppFiltersPreference?.removeAll()
        mAppFiltersPreference?.addPreference(
            ChipPreference(
                requireContext(),
                appsWithHealthPermissions,
                addFilterChip = ::addFilterChip,
                addAllAppsFilterChip = ::addAllAppsFilterChip))
    }

    private fun addFilterChip(appMetadata: AppMetadata, chipGroup: RadioGroup) {
        val newFilterChip = FilterChip(requireContext())
        newFilterChip.id = View.generateViewId()
        newFilterChip.setUnselectedIcon(appMetadata.icon)
        newFilterChip.text = appMetadata.appName
        if (appMetadata.appName == viewModel.selectedAppFilter.value) {
            newFilterChip.isChecked = true
            viewModel.filterPermissionTypes(category, appMetadata.packageName)
        }
        newFilterChip.setOnClickListener {
            viewModel.setAppFilter(appMetadata.appName)
            viewModel.filterPermissionTypes(category, appMetadata.packageName)
        }
        chipGroup.addView(newFilterChip)
    }

    private fun addAllAppsFilterChip(chipGroup: RadioGroup) {
        val allAppsButton = FilterChip(requireContext())
        val selectAllAppsTitle = resources.getString(R.string.select_all_apps_title)
        allAppsButton.id = R.id.select_all_chip
        allAppsButton.setUnselectedIcon(null)
        allAppsButton.text = requireContext().resources.getString(R.string.select_all_apps_title)
        if (viewModel.selectedAppFilter.value == selectAllAppsTitle) {
            allAppsButton.isChecked = true
            viewModel.filterPermissionTypes(category, selectAllAppsTitle)
        }

        allAppsButton.setOnClickListener {
            viewModel.setAppFilter(selectAllAppsTitle)
            viewModel.filterPermissionTypes(category, selectAllAppsTitle)
        }
        chipGroup.addView(allAppsButton)
    }
}
