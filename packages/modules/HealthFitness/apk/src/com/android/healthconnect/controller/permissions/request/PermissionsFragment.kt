/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.permissions.request

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromHealthPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for displaying permission switches. */
@AndroidEntryPoint(PreferenceFragmentCompat::class)
class PermissionsFragment : Hilt_PermissionsFragment() {

    companion object {
        private const val ALLOW_ALL_PREFERENCE = "allow_all_preference"
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        private const val HEADER = "request_permissions_header"
    }
    private val pageName = PageName.REQUEST_PERMISSIONS_PAGE
    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: RequestPermissionViewModel by activityViewModels()
    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    private val header: RequestPermissionHeaderPreference? by lazy {
        preferenceScreen.findPreference(HEADER)
    }

    private val allowAllPreference: HealthMainSwitchPreference? by lazy {
        preferenceScreen.findPreference(ALLOW_ALL_PREFERENCE)
    }

    private val mReadPermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(READ_CATEGORY)
    }

    private val mWritePermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(WRITE_CATEGORY)
    }

    private val onSwitchChangeListener = OnCheckedChangeListener { _, grant ->
            mReadPermissionCategory?.children?.forEach { preference ->
                (preference as TwoStatePreference).isChecked = grant
            }
            mWritePermissionCategory?.children?.forEach { preference ->
                (preference as TwoStatePreference).isChecked = grant
            }
            viewModel.updatePermissions(grant)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.setPageId(pageName)
    }

    override fun onResume() {
        super.onResume()
        logger.setPageId(pageName)
        logger.logPageImpression()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logger.setPageId(pageName)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.permissions_screen, rootKey)
        allowAllPreference?.logNameActive = PermissionsElement.ALLOW_ALL_SWITCH
        allowAllPreference?.logNameInactive = PermissionsElement.ALLOW_ALL_SWITCH
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.appMetadata.observe(viewLifecycleOwner) { app ->
            logger.logImpression(PermissionsElement.APP_RATIONALE_LINK)
            header?.bind(app.appName) {
                val startRationaleIntent =
                    healthPermissionReader.getApplicationRationaleIntent(app.packageName)
                logger.logInteraction(PermissionsElement.APP_RATIONALE_LINK)
                startActivity(startRationaleIntent)
            }
            mReadPermissionCategory?.title =
                getString(R.string.read_permission_category, app.appName)
            mWritePermissionCategory?.title =
                getString(R.string.write_permission_category, app.appName)
        }
        viewModel.permissionsList.observe(viewLifecycleOwner) { permissions ->
            updateDataList(permissions)
            setupAllowAll()
        }
    }

    private fun setupAllowAll() {
        viewModel.allPermissionsGranted.observe(viewLifecycleOwner) { allPermissionsGranted ->
            // does not trigger removing/enabling all permissions
            allowAllPreference?.removeOnSwitchChangeListener(onSwitchChangeListener)
            allowAllPreference?.isChecked = allPermissionsGranted
            allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
        }
        allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
    }

    private fun updateDataList(permissionsList: List<HealthPermission>) {
        mReadPermissionCategory?.removeAll()
        mWritePermissionCategory?.removeAll()

        permissionsList
            .sortedBy {
                requireContext()
                    .getString(fromPermissionType(it.healthPermissionType).uppercaseLabel)
            }
            .forEach { permission ->
                val value = viewModel.isPermissionGranted(permission)
                if (PermissionsAccessType.READ == permission.permissionsAccessType) {
                    mReadPermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                } else if (PermissionsAccessType.WRITE == permission.permissionsAccessType) {
                    mWritePermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                }
            }

        mReadPermissionCategory?.apply { isVisible = (preferenceCount != 0) }
        mWritePermissionCategory?.apply { isVisible = (preferenceCount != 0) }
    }

    private fun getPermissionPreference(
        defaultValue: Boolean,
        permission: HealthPermission
    ): Preference {
        return HealthSwitchPreference(requireContext()).also {
            val healthCategory = fromHealthPermissionType(permission.healthPermissionType)
            it.icon = healthCategory.icon(requireContext())
            it.setDefaultValue(defaultValue)
            it.setTitle(fromPermissionType(permission.healthPermissionType).uppercaseLabel)
            it.logNameActive = PermissionsElement.PERMISSION_SWITCH
            it.logNameInactive = PermissionsElement.PERMISSION_SWITCH
            it.setOnPreferenceChangeListener { _, newValue ->
                viewModel.updatePermission(permission, newValue as Boolean)
                true
            }
        }
    }
}
