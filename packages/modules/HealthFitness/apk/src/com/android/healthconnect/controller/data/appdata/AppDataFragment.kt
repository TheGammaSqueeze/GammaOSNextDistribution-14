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
package com.android.healthconnect.controller.data.appdata

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.NoDataPreference
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint

/** Fragment to display data in Health Connect written by a given app. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class AppDataFragment : Hilt_AppDataFragment() {

    companion object {
        private const val TAG = "AppDataFragmentTag"
        const val PERMISSION_TYPE_KEY = "permission_type_key"
    }

    init {
        // TODO(b/281811925):
        // this.setPageName(PageName.APP_DATA_PAGE)
    }

    private var packageName: String = ""
    private var appName: String = ""

    private val viewModel: AppDataViewModel by viewModels()

    private lateinit var header: AppHeaderPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.app_data_screen, rootKey)

        if (requireArguments().containsKey(EXTRA_PACKAGE_NAME) &&
            requireArguments().getString(EXTRA_PACKAGE_NAME) != null) {
            packageName = requireArguments().getString(EXTRA_PACKAGE_NAME)!!
        }
        if (requireArguments().containsKey(Constants.EXTRA_APP_NAME) &&
            requireArguments().getString(Constants.EXTRA_APP_NAME) != null) {
            appName = requireArguments().getString(Constants.EXTRA_APP_NAME)!!
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadAppInfo(packageName)
        viewModel.loadAppData(packageName)

        header = AppHeaderPreference(requireContext())
        viewModel.appInfo.observe(viewLifecycleOwner) { appMetadata ->
            header.apply {
                icon = appMetadata.icon
                title = appMetadata.appName
            }
        }

        viewModel.appData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AppDataViewModel.AppDataState.Loading -> {
                    setLoading(isLoading = true)
                }
                is AppDataViewModel.AppDataState.Error -> {
                    setError(hasError = true)
                }
                is AppDataViewModel.AppDataState.WithData -> {
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
        preferenceScreen.addPreference(header)

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
                            it.setOnPreferenceClickListener {
                                // TODO(b/281811925): Add in upcoming cl.
                                // it.logName = AppDataElement.PERMISSION_TYPE_BUTTON
                                findNavController()
                                    .navigate(
                                        R.id.action_appData_to_appEntries,
                                        bundleOf(
                                            EXTRA_PACKAGE_NAME to packageName,
                                            Constants.EXTRA_APP_NAME to appName,
                                            PERMISSION_TYPE_KEY to permissionType))
                                true
                            }
                        })
                }
        }
    }
}
