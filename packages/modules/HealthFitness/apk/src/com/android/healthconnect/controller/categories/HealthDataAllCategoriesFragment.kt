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

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.WithData
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.logging.CategoriesElement
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint

/** Fragment for all health data categories. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class HealthDataAllCategoriesFragment : Hilt_HealthDataAllCategoriesFragment() {

    companion object {
        private const val ALL_DATA_CATEGORY = "all_data_categories"
    }

    init {
        this.setPageName(PageName.ALL_CATEGORIES_PAGE)
    }

    private val viewModel: HealthDataCategoryViewModel by viewModels()

    private val mAllDataCategories: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(ALL_DATA_CATEGORY)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.health_data_all_categories_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadCategories()

        viewModel.categoriesData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is WithData -> {
                    setLoading(false)
                    updateAllDataList(state.categories)
                }
                HealthDataCategoryViewModel.CategoriesFragmentState.Error -> {
                    setError(true)
                }
                HealthDataCategoryViewModel.CategoriesFragmentState.Loading -> {
                    setLoading(true)
                }
            }
        }
    }

    private fun updateAllDataList(categoriesList: List<HealthCategoryUiState>) {
        val sortedAllCategoriesList: List<HealthCategoryUiState> =
            categoriesList.sortedBy { getString(it.category.uppercaseTitle()) }
        mAllDataCategories?.removeAll()
        if (sortedAllCategoriesList.isEmpty()) {
            mAllDataCategories?.addPreference(
                Preference(requireContext()).also { it.setSummary(R.string.no_categories) })
        } else {
            sortedAllCategoriesList.forEach { categoryInfo ->
                val newPreference =
                    HealthPreference(requireContext()).also {
                        it.setTitle(categoryInfo.category.uppercaseTitle())
                        it.icon = categoryInfo.category.icon(requireContext())
                        it.logName = CategoriesElement.CATEGORY_BUTTON
                        if (!categoryInfo.hasData) {
                            it.setSummary(R.string.no_data)
                            it.isEnabled = false
                        } else {
                            it.setOnPreferenceClickListener {
                                findNavController()
                                    .navigate(
                                        R.id
                                            .action_healthDataAllCategories_to_healthPermissionTypes,
                                        bundleOf(
                                            HealthDataCategoriesFragment.CATEGORY_KEY to
                                                categoryInfo.category))
                                true
                            }
                        }
                    }
                mAllDataCategories?.addPreference(newPreference)
            }
        }
    }
}
