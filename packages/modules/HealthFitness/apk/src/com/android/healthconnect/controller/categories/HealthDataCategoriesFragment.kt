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
package com.android.healthconnect.controller.categories

import android.icu.text.MessageFormat
import android.os.Bundle
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.Error
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.Loading
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.WithData
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.DeletionViewModel
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.CategoriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setupMenu
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for health data categories. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class HealthDataCategoriesFragment : Hilt_HealthDataCategoriesFragment() {

    companion object {
        const val CATEGORY_KEY = "category_key"
        private const val BROWSE_DATA_CATEGORY = "browse_data_category"
        private const val AUTO_DELETE_BUTTON = "auto_delete_button"
        private const val DELETE_ALL_DATA_BUTTON = "delete_all_data"
    }

    init {
        this.setPageName(PageName.CATEGORIES_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var featureUtils: FeatureUtils

    private val categoriesViewModel: HealthDataCategoryViewModel by viewModels()
    private val autoDeleteViewModel: AutoDeleteViewModel by activityViewModels()
    private val deletionViewModel: DeletionViewModel by activityViewModels()

    private val mBrowseDataCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(BROWSE_DATA_CATEGORY)
    }
    private val mAutoDelete: HealthPreference? by lazy {
        preferenceScreen.findPreference(AUTO_DELETE_BUTTON)
    }
    private val mDeleteAllData: HealthPreference? by lazy {
        preferenceScreen.findPreference(DELETE_ALL_DATA_BUTTON)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.health_data_categories_screen, rootKey)

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }

        if (!featureUtils.isNewAppPriorityEnabled()) {
            mAutoDelete?.isVisible = true
            mAutoDelete?.logName = CategoriesElement.AUTO_DELETE_BUTTON
            mAutoDelete?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_healthDataCategories_to_autoDelete)
                true
            }
        }

        mDeleteAllData?.logName = CategoriesElement.DELETE_ALL_DATA_BUTTON
        mDeleteAllData?.setOnPreferenceClickListener {
            val deletionType = DeletionType.DeletionTypeAllData()
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
            true
        }
        mDeleteAllData?.isEnabled = false
    }

    private fun buildSummary(autoDeleteRange: AutoDeleteRange): String {
        return when (autoDeleteRange) {
            AutoDeleteRange.AUTO_DELETE_RANGE_NEVER -> getString(R.string.range_off)
            AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS -> {
                val count = AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS.numberOfMonths
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
            AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS -> {
                val count = AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.numberOfMonths
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
        }
    }

    @AttrRes
    private fun iconAttribute(autoDeleteRange: AutoDeleteRange): Int {
        return when (autoDeleteRange) {
            AutoDeleteRange.AUTO_DELETE_RANGE_NEVER -> R.attr.autoDeleteOffIcon
            else -> R.attr.autoDeleteIcon
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoriesViewModel.loadCategories()

        categoriesViewModel.categoriesData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Loading -> {
                    setLoading(true)
                }
                is WithData -> {
                    setLoading(false)
                    updateDataList(state.categories)
                }
                Error -> {
                    setError(true)
                }
            }
        }

        setupMenu(R.menu.set_data_units_with_send_feedback_and_help, viewLifecycleOwner, logger) {
            menuItem ->
            when (menuItem.itemId) {
                R.id.menu_open_units -> {
                    logger.logImpression(ToolbarElement.TOOLBAR_UNITS_BUTTON)
                    findNavController()
                        .navigate(R.id.action_dataCategoriesFragment_to_unitsFragment)
                    true
                }
                else -> false
            }
        }

        if (!featureUtils.isNewAppPriorityEnabled()) {
            autoDeleteViewModel.storedAutoDeleteRange.observe(viewLifecycleOwner) { state ->
                when (state) {
                    AutoDeleteViewModel.AutoDeleteState.Loading -> {
                        mAutoDelete?.summary = ""
                        mAutoDelete?.icon = null
                    }
                    is AutoDeleteViewModel.AutoDeleteState.LoadingFailed -> {
                        mAutoDelete?.summary = ""
                        mAutoDelete?.icon = null
                    }
                    is AutoDeleteViewModel.AutoDeleteState.WithData -> {
                        mAutoDelete?.summary = buildSummary(state.autoDeleteRange)
                        mAutoDelete?.setIcon(
                            AttributeResolver.getResource(
                                requireContext(), iconAttribute(state.autoDeleteRange)))
                    }
                }
            }
        }

        deletionViewModel.categoriesReloadNeeded.observe(viewLifecycleOwner) { isReloadNeeded ->
            if (isReloadNeeded) {
                categoriesViewModel.loadCategories()
            }
        }
    }

    private fun updateDataList(categoriesList: List<HealthCategoryUiState>) {
        val sortedCategoriesList: List<HealthCategoryUiState> =
            categoriesList
                .filter { it.hasData }
                .sortedBy { getString(it.category.uppercaseTitle()) }
        mBrowseDataCategory?.removeAll()
        if (sortedCategoriesList.isEmpty()) {
            mBrowseDataCategory?.addPreference(
                Preference(requireContext()).also { it.setSummary(R.string.no_categories) })
        } else {
            sortedCategoriesList.forEach { categoryState ->
                val newCategoryPreference =
                    HealthPreference(requireContext()).also {
                        it.setTitle(categoryState.category.uppercaseTitle())
                        it.icon = categoryState.category.icon(requireContext())
                        it.logName = CategoriesElement.CATEGORY_BUTTON
                        it.setOnPreferenceClickListener {
                            findNavController()
                                .navigate(
                                    R.id.action_healthDataCategories_to_healthPermissionTypes,
                                    bundleOf(CATEGORY_KEY to categoryState.category))
                            true
                        }
                    }
                mBrowseDataCategory?.addPreference(newCategoryPreference)
            }
        }
        if (sortedCategoriesList.isEmpty() || categoriesList.any { !it.hasData }) {
            addSeeAllCategoriesPreference()
        }
        mDeleteAllData?.isEnabled = sortedCategoriesList.isNotEmpty()
    }

    private fun addSeeAllCategoriesPreference() {
        mBrowseDataCategory?.addPreference(
            HealthPreference(requireContext()).also {
                it.setTitle(R.string.see_all_categories)
                it.setIcon(AttributeResolver.getResource(requireContext(), R.attr.seeAllIcon))
                it.logName = CategoriesElement.SEE_ALL_CATEGORIES_BUTTON
                it.setOnPreferenceClickListener {
                    findNavController()
                        .navigate(R.id.action_healthDataCategories_to_healthDataAllCategories)
                    true
                }
            })
    }
}
