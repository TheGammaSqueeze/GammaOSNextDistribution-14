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
package com.android.healthconnect.controller.data.alldata

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.NoDataPreference
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for health permission types. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class AllDataFragment : Hilt_AllDataFragment() {

    companion object {
        private const val TAG = "AllDataFragmentTag"
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: AllDataViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.app_data_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadAllData()

        viewModel.allData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AllDataViewModel.AllDataState.Loading -> {
                    setLoading(isLoading = true)
                }
                is AllDataViewModel.AllDataState.Error -> {
                    setError(hasError = true)
                }
                is AllDataViewModel.AllDataState.WithData -> {
                    setLoading(isLoading = false)
                    setError(hasError = false)
                    updatePreferenceScreen(state.dataMap)
                }
            }
        }
    }

    private fun updatePreferenceScreen(
        permissionTypesPerCategoryList: List<PermissionTypesPerCategory>
    ) {
        preferenceScreen?.removeAll()

        val populatedCategories =
            permissionTypesPerCategoryList
                .filter { it.data.isNotEmpty() }
                .sortedBy { getString(it.category.uppercaseTitle()) }

        if (populatedCategories.isEmpty()) {
            preferenceScreen.addPreference(NoDataPreference(requireContext()))
            preferenceScreen.addPreference(
                FooterPreference(requireContext()).also { it.setTitle(R.string.no_data_footer) })
            return
        }

        populatedCategories.forEach { permissionTypesPerCategory ->
            val category = permissionTypesPerCategory.category
            val categoryIcon = category.icon(requireContext())

            val preferenceCategory =
                PreferenceCategory(requireContext()).also { it.setTitle(category.uppercaseTitle()) }
            preferenceScreen.addPreference(preferenceCategory)

            permissionTypesPerCategory.data
                .sortedBy {
                    getString(HealthPermissionStrings.fromPermissionType(it).uppercaseLabel)
                }
                .forEach { permissionType ->
                    preferenceCategory.addPreference(
                        HealthPreference(requireContext()).also {
                            it.icon = categoryIcon
                            it.setTitle(
                                HealthPermissionStrings.fromPermissionType(permissionType)
                                    .uppercaseLabel)
                            // TODO(b/291249677): Add in upcoming CL.
                            // it.logName = AllDataElement.PERMISSION_TYPE_BUTTON
                            it.setOnPreferenceClickListener {
                                findNavController()
                                    .navigate(
                                        R.id.action_allData_to_entriesAndAccess,
                                        bundleOf(PERMISSION_TYPE_KEY to permissionType))
                                true
                            }
                        })
                }
        }
    }
}
