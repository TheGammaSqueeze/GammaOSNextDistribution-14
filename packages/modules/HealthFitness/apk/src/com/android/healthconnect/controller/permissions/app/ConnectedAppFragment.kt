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

/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.app

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.DeletionViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.DisableExerciseRoutePermissionDialog
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel.RevokeAllState
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.permissions.shared.DisconnectDialogFragment
import com.android.healthconnect.controller.permissions.shared.DisconnectDialogFragment.Companion.DISCONNECT_ALL_EVENT
import com.android.healthconnect.controller.permissions.shared.DisconnectDialogFragment.Companion.DISCONNECT_CANCELED_EVENT
import com.android.healthconnect.controller.permissions.shared.DisconnectDialogFragment.Companion.KEY_DELETE_DATA
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromHealthPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.dismissLoadingDialog
import com.android.healthconnect.controller.utils.logging.AppAccessElement
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.pref
import com.android.healthconnect.controller.utils.showLoadingDialog
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for connected app screen. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class ConnectedAppFragment : Hilt_ConnectedAppFragment() {

    companion object {
        private const val PERMISSION_HEADER = "manage_app_permission_header"
        private const val ALLOW_ALL_PREFERENCE = "allow_all_preference"
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        private const val MANAGE_DATA_PREFERENCE_KEY = "manage_app"
        private const val FOOTER_KEY = "connected_app_footer"
        private const val KEY_ADDITIONAL_ACCESS = "additional_access"
        private const val DISABLE_EXERCISE_ROUTE_DIALOG_TAG = "disable_exercise_route"
        private const val PARAGRAPH_SEPARATOR = "\n\n"
    }

    init {
        this.setPageName(PageName.APP_ACCESS_PAGE)
    }

    @Inject lateinit var featureUtils: FeatureUtils
    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    private var packageName: String = ""
    private var appName: String = ""
    private val appPermissionViewModel: AppPermissionViewModel by activityViewModels()
    private val deletionViewModel: DeletionViewModel by activityViewModels()
    private val additionalAccessViewModel: AdditionalAccessViewModel by activityViewModels()
    private val permissionMap: MutableMap<HealthPermission, HealthSwitchPreference> = mutableMapOf()

    private val header: AppHeaderPreference by pref(PERMISSION_HEADER)
    private val allowAllPreference: HealthMainSwitchPreference by pref(ALLOW_ALL_PREFERENCE)
    private val readPermissionCategory: PreferenceGroup by pref(READ_CATEGORY)
    private val writePermissionCategory: PreferenceGroup by pref(WRITE_CATEGORY)
    private val manageDataCategory: PreferenceGroup by pref(MANAGE_DATA_PREFERENCE_KEY)
    private val connectedAppFooter: FooterPreference by pref(FOOTER_KEY)
    private val dateFormatter by lazy { LocalDateTimeFormatter(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.connected_app_screen, rootKey)

        allowAllPreference.logNameActive = AppAccessElement.ALLOW_ALL_PERMISSIONS_SWITCH_ACTIVE
        allowAllPreference.logNameInactive = AppAccessElement.ALLOW_ALL_PERMISSIONS_SWITCH_INACTIVE

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (requireArguments().containsKey(EXTRA_PACKAGE_NAME) &&
            requireArguments().getString(EXTRA_PACKAGE_NAME) != null) {
            packageName = requireArguments().getString(EXTRA_PACKAGE_NAME)!!
        }
        if (requireArguments().containsKey(EXTRA_APP_NAME) &&
            requireArguments().getString(EXTRA_APP_NAME) != null) {
            appName = requireArguments().getString(EXTRA_APP_NAME)!!
        }

        appPermissionViewModel.loadPermissionsForPackage(packageName)

        appPermissionViewModel.appPermissions.observe(viewLifecycleOwner) { permissions ->
            updatePermissions(permissions)
        }
        appPermissionViewModel.grantedPermissions.observe(viewLifecycleOwner) { granted ->
            permissionMap.forEach { (healthPermission, switchPreference) ->
                switchPreference.isChecked = healthPermission in granted
            }
        }

        deletionViewModel.appPermissionReloadNeeded.observe(viewLifecycleOwner) { isReloadNeeded ->
            if (isReloadNeeded) appPermissionViewModel.loadPermissionsForPackage(packageName)
        }

        appPermissionViewModel.revokeAllPermissionsState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RevokeAllState.Loading -> {
                    showLoadingDialog()
                }
                else -> {
                    dismissLoadingDialog()
                }
            }
        }

        appPermissionViewModel.showDisableExerciseRouteEvent.observe(viewLifecycleOwner) { event ->
            if (event.shouldShowDialog) {
                DisableExerciseRoutePermissionDialog.createDialog(packageName, event.appName)
                    .show(childFragmentManager, DISABLE_EXERCISE_ROUTE_DIALOG_TAG)
            }
        }

        setupAllowAllPreference()
        setupManageDataPreferenceCategory()
        setupHeader()
        setupFooter()
    }

    private fun setupHeader() {
        appPermissionViewModel.appInfo.observe(viewLifecycleOwner) { appMetadata ->
            header.apply {
                icon = appMetadata.icon
                title = appMetadata.appName
            }
        }
    }

    private fun setupManageDataPreferenceCategory() {
        manageDataCategory.removeAll()
        if (featureUtils.isNewInformationArchitectureEnabled()) {
            manageDataCategory.addPreference(
                HealthPreference(requireContext()).also {
                    it.title = getString(R.string.see_app_data)
                    it.setOnPreferenceClickListener {
                        findNavController()
                            .navigate(
                                R.id.action_connectedApp_to_appData,
                                bundleOf(
                                    EXTRA_PACKAGE_NAME to packageName, EXTRA_APP_NAME to appName))
                        true
                    }
                })
        } else {
            manageDataCategory.addPreference(
                HealthPreference(requireContext()).also {
                    it.logName = AppAccessElement.DELETE_APP_DATA_BUTTON
                    it.title = getString(R.string.delete_app_data)
                    it.setOnPreferenceClickListener {
                        val deletionType = DeletionType.DeletionTypeAppData(packageName, appName)
                        childFragmentManager.setFragmentResult(
                            START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
                        true
                    }
                })
        }
        additionalAccessViewModel.loadAdditionalAccessPreferences(packageName)
        additionalAccessViewModel.additionalAccessState.observe(viewLifecycleOwner) { state ->
            if (state.isValid() && shouldAddAdditionalAccessPref()) {
                val additionalAccessPref =
                    HealthPreference(requireContext()).also {
                        it.key = KEY_ADDITIONAL_ACCESS
                        it.logName = AppPermissionsElement.ADDITIONAL_ACCESS_BUTTON
                        it.setTitle(R.string.additional_access_label)
                        it.setOnPreferenceClickListener { _ ->
                            val extras = bundleOf(EXTRA_PACKAGE_NAME to packageName)
                            findNavController()
                                .navigate(
                                    R.id.action_connectedAppFragment_to_additionalAccessFragment,
                                    extras)
                            true
                        }
                    }
                manageDataCategory.addPreference(additionalAccessPref)
            }
        }
    }

    private fun shouldAddAdditionalAccessPref(): Boolean {
        return manageDataCategory.children.none { it.key == KEY_ADDITIONAL_ACCESS }
    }

    private val onSwitchChangeListener = OnCheckedChangeListener { buttonView, isChecked ->
        if (isChecked) {
            val permissionsUpdated = appPermissionViewModel.grantAllPermissions(packageName)
            if (!permissionsUpdated) {
                buttonView.isChecked = false
                Toast.makeText(requireContext(), R.string.default_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            showRevokeAllPermissions()
        }
    }

    private fun setupAllowAllPreference() {
        allowAllPreference.addOnSwitchChangeListener(onSwitchChangeListener)
        appPermissionViewModel.allAppPermissionsGranted.observe(viewLifecycleOwner) { isAllGranted
            ->
            allowAllPreference.removeOnSwitchChangeListener(onSwitchChangeListener)
            allowAllPreference.isChecked = isAllGranted
            allowAllPreference.addOnSwitchChangeListener(onSwitchChangeListener)
        }
    }

    private fun showRevokeAllPermissions() {
        childFragmentManager.setFragmentResultListener(DISCONNECT_CANCELED_EVENT, this) { _, _ ->
            allowAllPreference.isChecked = true
        }

        childFragmentManager.setFragmentResultListener(DISCONNECT_ALL_EVENT, this) { _, bundle ->
            val permissionsUpdated = appPermissionViewModel.revokeAllPermissions(packageName)
            if (!permissionsUpdated) {
                Toast.makeText(requireContext(), R.string.default_error, Toast.LENGTH_SHORT).show()
            }
            if (bundle.containsKey(KEY_DELETE_DATA) && bundle.getBoolean(KEY_DELETE_DATA)) {
                appPermissionViewModel.deleteAppData(packageName, appName)
            }
        }

        DisconnectDialogFragment(appName).show(childFragmentManager, DisconnectDialogFragment.TAG)
    }

    private fun updatePermissions(permissions: List<HealthPermission>) {
        readPermissionCategory.removeAll()
        writePermissionCategory.removeAll()
        permissionMap.clear()

        permissions
            .sortedBy {
                requireContext()
                    .getString(fromPermissionType(it.healthPermissionType).uppercaseLabel)
            }
            .forEach { permission ->
                val category =
                    if (permission.permissionsAccessType == PermissionsAccessType.READ) {
                        readPermissionCategory
                    } else {
                        writePermissionCategory
                    }

                val preference =
                    HealthSwitchPreference(requireContext()).also { it ->
                        val healthCategory =
                            fromHealthPermissionType(permission.healthPermissionType)
                        it.icon = healthCategory.icon(requireContext())
                        it.setTitle(
                            fromPermissionType(permission.healthPermissionType).uppercaseLabel)
                        it.logNameActive = AppAccessElement.PERMISSION_SWITCH_ACTIVE
                        it.logNameInactive = AppAccessElement.PERMISSION_SWITCH_INACTIVE
                        it.setOnPreferenceChangeListener { _, newValue ->
                            allowAllPreference?.removeOnSwitchChangeListener(onSwitchChangeListener)
                            val checked = newValue as Boolean
                            val permissionUpdated =
                                appPermissionViewModel.updatePermission(
                                    packageName, permission, checked)
                            if (!permissionUpdated) {
                                Toast.makeText(
                                        requireContext(),
                                        R.string.default_error,
                                        Toast.LENGTH_SHORT)
                                    .show()
                            }
                            allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
                            permissionUpdated
                        }
                    }
                permissionMap[permission] = preference
                category.addPreference(preference)
            }

        readPermissionCategory.apply { isVisible = (preferenceCount != 0) }
        writePermissionCategory.apply { isVisible = (preferenceCount != 0) }
    }

    private fun setupFooter() {
        appPermissionViewModel.atLeastOnePermissionGranted.observe(viewLifecycleOwner) {
            isAtLeastOneGranted ->
            updateFooter(isAtLeastOneGranted)
        }
    }

    private fun updateFooter(isAtLeastOneGranted: Boolean) {
        var title =
            getString(R.string.other_android_permissions) +
                PARAGRAPH_SEPARATOR +
                getString(R.string.manage_permissions_rationale, appName)
        var contentDescription =
            getString(R.string.other_android_permissions_content_description) +
                PARAGRAPH_SEPARATOR +
                getString(R.string.manage_permissions_rationale, appName)

        if (isAtLeastOneGranted) {
            val dataAccessDate = appPermissionViewModel.loadAccessDate(packageName)
            dataAccessDate?.let {
                val formattedDate = dateFormatter.formatLongDate(dataAccessDate)
                val paragraph =
                    getString(R.string.manage_permissions_time_frame, appName, formattedDate)
                title = paragraph + PARAGRAPH_SEPARATOR + title
                contentDescription = paragraph + PARAGRAPH_SEPARATOR + contentDescription
            }
        }

        connectedAppFooter.title = title
        connectedAppFooter.setContentDescription(contentDescription)
        if (healthPermissionReader.isRationalIntentDeclared(packageName)) {
            connectedAppFooter.setLearnMoreText(getString(R.string.manage_permissions_learn_more))
            logger.logImpression(AppAccessElement.PRIVACY_POLICY_LINK)
            connectedAppFooter.setLearnMoreAction {
                logger.logInteraction(AppAccessElement.PRIVACY_POLICY_LINK)
                val startRationaleIntent =
                    healthPermissionReader.getApplicationRationaleIntent(packageName)
                startActivity(startRationaleIntent)
            }
        }
    }
}
