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
package com.android.healthconnect.controller.data.access

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.permissions.connectedapps.HealthAppPreference
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.inactiveapp.InactiveAppPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.logging.DataAccessElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setTitle
import com.android.healthconnect.controller.utils.setupMenu
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment displaying health data access information. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class AccessFragment : Hilt_AccessFragment() {

    companion object {
        private const val CAN_READ_SECTION = "can_read"
        private const val CAN_WRITE_SECTION = "can_write"
        private const val INACTIVE_SECTION = "inactive"
    }

    init {
        this.setPageName(PageName.DATA_ACCESS_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: AccessViewModel by viewModels()

    private lateinit var permissionType: HealthPermissionType

    private val mCanReadSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(CAN_READ_SECTION)
    }

    private val mCanWriteSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(CAN_WRITE_SECTION)
    }

    private val mInactiveSection: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(INACTIVE_SECTION)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.access_screen, rootKey)
        if (requireArguments().containsKey(PERMISSION_TYPE_KEY)) {
            permissionType =
                arguments?.getSerializable(PERMISSION_TYPE_KEY, HealthPermissionType::class.java)
                    ?: throw IllegalArgumentException("PERMISSION_TYPE_KEY can't be null!")
        }

        mCanReadSection?.isVisible = false
        mCanWriteSection?.isVisible = false
        mInactiveSection?.isVisible = false
        mCanReadSection?.title =
            getString(
                R.string.can_read, getString(fromPermissionType(permissionType).lowercaseLabel))
        mCanWriteSection?.title =
            getString(
                R.string.can_write, getString(fromPermissionType(permissionType).lowercaseLabel))
    }

    override fun onResume() {
        super.onResume()
        setTitle(fromPermissionType(permissionType).uppercaseLabel)
        viewModel.loadAppMetaDataMap(permissionType)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadAppMetaDataMap(permissionType)
        viewModel.appMetadataMap.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AccessViewModel.AccessScreenState.Loading -> {
                    setLoading(isLoading = true)
                }
                is AccessViewModel.AccessScreenState.Error -> {
                    setError(hasError = true)
                }
                is AccessViewModel.AccessScreenState.WithData -> {
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
                    // TODO(b/291249677): Enable in an upcoming CL.
                    //                    findNavController()
                    //
                    // .navigate(R.id.action_entriesAndAccessFragment_to_unitFragment)
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
                                // TODO(b/291249677): Update when new deletion flows are ready.
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
                findNavController()
                        .navigate(
                                R.id.action_entriesAndAccessFragment_to_appAccess,
                                bundleOf(EXTRA_PACKAGE_NAME to appMetadata.packageName,
                                        EXTRA_APP_NAME to appMetadata.appName))
                true
            }
        }
    }
}
