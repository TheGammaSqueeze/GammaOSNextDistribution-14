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
package com.android.healthconnect.controller.dataaccess

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.access.AccessViewModel
import com.android.healthconnect.controller.data.access.AccessViewModel.AccessScreenState
import com.android.healthconnect.controller.data.access.AppAccessState
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.permissions.connectedapps.HealthAppPreference
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromHealthPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.inactiveapp.InactiveAppPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.logging.DataAccessElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setTitle
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.TopIntroPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment displaying health data access information. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class HealthDataAccessFragment : Hilt_HealthDataAccessFragment() {

    companion object {
        private const val DATA_ACCESS_HEADER = "data_access_header"
        private const val PERMISSION_TYPE_DESCRIPTION = "permission_type_description"
        private const val CAN_READ_SECTION = "can_read"
        private const val CAN_WRITE_SECTION = "can_write"
        private const val INACTIVE_SECTION = "inactive"
        private const val ALL_ENTRIES_BUTTON = "all_entries_button"
        private const val DELETE_PERMISSION_TYPE_DATA_BUTTON = "delete_permission_type_data"
    }

    init {
        this.setPageName(PageName.DATA_ACCESS_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: AccessViewModel by viewModels()

    private lateinit var permissionType: HealthPermissionType

    private val mDataAccessHeader: AppHeaderPreference? by lazy {
        preferenceScreen.findPreference(DATA_ACCESS_HEADER)
    }

    private val mPermissionTypeDescription: TopIntroPreference? by lazy {
        preferenceScreen.findPreference(PERMISSION_TYPE_DESCRIPTION)
    }

    private val mCanReadSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(CAN_READ_SECTION)
    }

    private val mCanWriteSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(CAN_WRITE_SECTION)
    }

    private val mInactiveSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(INACTIVE_SECTION)
    }

    private val mAllEntriesButton: HealthPreference? by lazy {
        preferenceScreen.findPreference(ALL_ENTRIES_BUTTON)
    }

    private val mDeletePermissionTypeData: HealthPreference? by lazy {
        preferenceScreen.findPreference(DELETE_PERMISSION_TYPE_DATA_BUTTON)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.health_data_access_screen, rootKey)
        if (requireArguments().containsKey(PERMISSION_TYPE_KEY)) {
            permissionType =
                arguments?.getSerializable(PERMISSION_TYPE_KEY, HealthPermissionType::class.java)
                    ?: throw IllegalArgumentException("PERMISSION_TYPE_KEY can't be null!")
        }

        mCanReadSection?.isVisible = false
        mCanWriteSection?.isVisible = false
        mInactiveSection?.isVisible = false
        maybeShowPermissionTypeDescription()
        mCanReadSection?.title =
            getString(
                R.string.can_read, getString(fromPermissionType(permissionType).lowercaseLabel))
        mCanWriteSection?.title =
            getString(
                R.string.can_write, getString(fromPermissionType(permissionType).lowercaseLabel))
        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }

        mAllEntriesButton?.logName = DataAccessElement.SEE_ALL_ENTRIES_BUTTON
        mAllEntriesButton?.setOnPreferenceClickListener {
            findNavController()
                .navigate(
                    R.id.action_healthDataAccess_to_dataEntries,
                    bundleOf(PERMISSION_TYPE_KEY to permissionType))
            true
        }
        mDeletePermissionTypeData?.logName = DataAccessElement.DELETE_THIS_DATA_BUTTON
        mDeletePermissionTypeData?.setOnPreferenceClickListener {
            val deletionType =
                DeletionType.DeletionTypeHealthPermissionTypeData(
                    healthPermissionType = permissionType)
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
            true
        }
    }

    private fun maybeShowPermissionTypeDescription() {
        mPermissionTypeDescription?.isVisible = false
        if (permissionType == HealthPermissionType.EXERCISE) {
            mPermissionTypeDescription?.isVisible = true
            mPermissionTypeDescription?.setTitle(R.string.data_access_exercise_description)
        }
        if (permissionType == HealthPermissionType.SLEEP) {
            mPermissionTypeDescription?.isVisible = true
            mPermissionTypeDescription?.setTitle(R.string.data_access_sleep_description)
        }
    }

    override fun onResume() {
        super.onResume()
        setTitle(fromPermissionType(permissionType).uppercaseLabel)
        viewModel.loadAppMetaDataMap(permissionType)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mDataAccessHeader?.icon = fromHealthPermissionType(permissionType).icon(requireContext())
        mDataAccessHeader?.title = getString(fromPermissionType(permissionType).uppercaseLabel)
        viewModel.loadAppMetaDataMap(permissionType)
        viewModel.appMetadataMap.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AccessScreenState.Loading -> {
                    setLoading(isLoading = true)
                }
                is AccessScreenState.Error -> {
                    setError(hasError = true)
                }
                is AccessScreenState.WithData -> {
                    setLoading(isLoading = false, animate = false)
                    updateDataAccess(state.appMetadata)
                }
            }
        }

        setupMenu(R.menu.set_data_units_with_send_feedback_and_help, viewLifecycleOwner, logger) {
            menuItem ->
            when (menuItem.itemId) {
                R.id.menu_open_units -> {
                    logger.logImpression(ToolbarElement.TOOLBAR_UNITS_BUTTON)
                    findNavController()
                        .navigate(R.id.action_healthDataAccessFragment_to_unitsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateDataAccess(appMetadataMap: Map<AppAccessState, List<AppMetadata>>) {
        mCanReadSection?.removeAll()
        mCanWriteSection?.removeAll()
        mInactiveSection?.removeAll()

        if (appMetadataMap.containsKey(AppAccessState.Read)) {
            if (appMetadataMap[AppAccessState.Read]!!.isEmpty()) {
                mCanReadSection?.isVisible = false
            } else {
                mCanReadSection?.isVisible = true
                appMetadataMap[AppAccessState.Read]!!.forEach { _appMetadata ->
                    mCanReadSection?.addPreference(createAppPreference(_appMetadata))
                }
            }
        }
        if (appMetadataMap.containsKey(AppAccessState.Write)) {
            if (appMetadataMap[AppAccessState.Write]!!.isEmpty()) {
                mCanWriteSection?.isVisible = false
            } else {
                mCanWriteSection?.isVisible = true
                appMetadataMap[AppAccessState.Write]!!.forEach { _appMetadata ->
                    mCanWriteSection?.addPreference(createAppPreference(_appMetadata))
                }
            }
        }
        if (appMetadataMap.containsKey(AppAccessState.Inactive)) {
            if (appMetadataMap[AppAccessState.Inactive]!!.isEmpty()) {
                mInactiveSection?.isVisible = false
            } else {
                mInactiveSection?.isVisible = true
                mInactiveSection?.addPreference(
                    Preference(requireContext()).also {
                        it.summary =
                            getString(
                                R.string.inactive_apps_message,
                                getString(fromPermissionType(permissionType).lowercaseLabel))
                    })
                appMetadataMap[AppAccessState.Inactive]?.forEach { _appMetadata ->
                    mInactiveSection?.addPreference(
                        InactiveAppPreference(requireContext()).also {
                            it.title = _appMetadata.appName
                            it.icon = _appMetadata.icon
                            it.logName = DataAccessElement.DATA_ACCESS_INACTIVE_APP_BUTTON
                            it.setOnDeleteButtonClickListener {
                                val deletionType =
                                    DeletionType.DeletionTypeAppData(
                                        _appMetadata.packageName, _appMetadata.appName)
                                childFragmentManager.setFragmentResult(
                                    START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
                            }
                        })
                }
            }
        }
    }

    private fun createAppPreference(appMetadata: AppMetadata): HealthAppPreference {
        return HealthAppPreference(requireContext(), appMetadata).also {
            it.logName = DataAccessElement.DATA_ACCESS_APP_BUTTON
            it.setOnPreferenceClickListener {
                // TODO (b/270859815) might need to navigate to appAccess instead
                findNavController()
                    .navigate(
                        R.id.action_healthDataAccessFragment_to_appAccess,
                        bundleOf(EXTRA_PACKAGE_NAME to appMetadata.packageName))
                true
            }
        }
    }
}
