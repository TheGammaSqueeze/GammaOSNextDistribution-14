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
package com.android.healthconnect.controller.permissions.connectedapps

import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.deletion.DeletionConstants
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel.DisconnectAllState
import com.android.healthconnect.controller.permissions.shared.HelpAndFeedbackFragment.Companion.APP_INTEGRATION_REQUEST_BUCKET_ID
import com.android.healthconnect.controller.permissions.shared.HelpAndFeedbackFragment.Companion.FEEDBACK_INTENT_RESULT_CODE
import com.android.healthconnect.controller.shared.Constants.APP_UPDATE_NEEDED_BANNER_SEEN
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.ALLOWED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.DENIED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.INACTIVE
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.NEEDS_UPDATE
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.shared.inactiveapp.InactiveAppPreference
import com.android.healthconnect.controller.shared.preference.BannerPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AppStoreUtils
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.dismissLoadingDialog
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.DisconnectAllAppsDialogElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.setupMenu
import com.android.healthconnect.controller.utils.setupSharedMenu
import com.android.healthconnect.controller.utils.showLoadingDialog
import com.android.settingslib.widget.AppPreference
import com.android.settingslib.widget.TopIntroPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for connected apps screen. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class ConnectedAppsFragment : Hilt_ConnectedAppsFragment() {

    companion object {
        private const val TOP_INTRO = "connected_apps_top_intro"
        const val ALLOWED_APPS_CATEGORY = "allowed_apps"
        private const val NOT_ALLOWED_APPS = "not_allowed_apps"
        private const val INACTIVE_APPS = "inactive_apps"
        private const val NEED_UPDATE_APPS = "need_update_apps"
        private const val THINGS_TO_TRY = "things_to_try_app_permissions_screen"
        private const val SETTINGS_AND_HELP = "settings_and_help"
        private const val BANNER_PREFERENCE_KEY = "banner_preference"
    }

    init {
        this.setPageName(PageName.APP_PERMISSIONS_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var appStoreUtils: AppStoreUtils
    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils
    @Inject lateinit var navigationUtils: NavigationUtils

    private val viewModel: ConnectedAppsViewModel by viewModels()
    private lateinit var searchMenuItem: MenuItem
    private lateinit var removeAllAppsDialog: AlertDialog

    private val mTopIntro: TopIntroPreference by lazy {
        preferenceScreen.findPreference(TOP_INTRO)!!
    }

    private val mAllowedAppsCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(ALLOWED_APPS_CATEGORY)!!
    }

    private val mNotAllowedAppsCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(NOT_ALLOWED_APPS)!!
    }

    private val mInactiveAppsCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(INACTIVE_APPS)!!
    }

    private val mNeedUpdateAppsCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(NEED_UPDATE_APPS)!!
    }

    private val mThingsToTryCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(THINGS_TO_TRY)!!
    }

    private val mSettingsAndHelpCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(SETTINGS_AND_HELP)!!
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.connected_apps_screen, rootKey)

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }
    }

    private fun createRemoveAllAppsAccessDialog(apps: List<ConnectedAppMetadata>) {
        removeAllAppsDialog =
            AlertDialogBuilder(
                    this, DisconnectAllAppsDialogElement.DISCONNECT_ALL_APPS_DIALOG_CONTAINER)
                .setIcon(R.attr.disconnectAllIcon)
                .setTitle(R.string.permissions_disconnect_all_dialog_title)
                .setMessage(R.string.permissions_disconnect_all_dialog_message)
                .setNeutralButton(
                    android.R.string.cancel,
                    DisconnectAllAppsDialogElement.DISCONNECT_ALL_APPS_DIALOG_CANCEL_BUTTON) { _, _
                        ->
                        viewModel.setAlertDialogStatus(false)
                    }
                .setPositiveButton(
                    R.string.permissions_disconnect_all_dialog_disconnect,
                    DisconnectAllAppsDialogElement.DISCONNECT_ALL_APPS_DIALOG_REMOVE_ALL_BUTTON) {
                        _,
                        _ ->
                        if (!viewModel.disconnectAllApps(apps)) {
                            Toast.makeText(
                                    requireContext(), R.string.default_error, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                .create()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadConnectedApps()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeConnectedApps()
        observeRevokeAllAppsPermissions()
    }

    private fun observeRevokeAllAppsPermissions() {
        viewModel.disconnectAllState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DisconnectAllState.Loading -> {
                    showLoadingDialog()
                }
                else -> {
                    dismissLoadingDialog()
                }
            }
        }
    }

    private fun observeConnectedApps() {
        viewModel.connectedApps.observe(viewLifecycleOwner) { connectedApps ->
            clearAllCategories()
            if (connectedApps.isEmpty()) {
                setupSharedMenu(viewLifecycleOwner, logger)
                setUpEmptyState()
            } else {
                setupMenu(R.menu.connected_apps, viewLifecycleOwner, logger) { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_search -> {
                            searchMenuItem = menuItem
                            logger.logInteraction(AppPermissionsElement.SEARCH_BUTTON)
                            findNavController().navigate(R.id.action_connectedApps_to_searchApps)
                            true
                        }
                        else -> false
                    }
                }
                logger.logImpression(AppPermissionsElement.SEARCH_BUTTON)

                mTopIntro?.title = getString(R.string.connected_apps_text)
                mThingsToTryCategory?.isVisible = false
                setAppAndSettingsCategoriesVisibility(true)

                val connectedAppsGroup = connectedApps.groupBy { it.status }
                val allowedApps = connectedAppsGroup[ALLOWED].orEmpty()
                val notAllowedApps = connectedAppsGroup[DENIED].orEmpty()
                val needUpdateApps = connectedAppsGroup[NEEDS_UPDATE].orEmpty()
                val activeApps: MutableList<ConnectedAppMetadata> = allowedApps.toMutableList()
                activeApps.addAll(notAllowedApps)
                createRemoveAllAppsAccessDialog(activeApps)

                mSettingsAndHelpCategory?.addPreference(
                    getRemoveAccessForAllAppsPreference().apply {
                        isEnabled = allowedApps.isNotEmpty()
                        setOnPreferenceClickListener {
                            viewModel.setAlertDialogStatus(true)
                            true
                        }
                    })

                if (deviceInfoUtils.isPlayStoreAvailable(requireContext()) ||
                    deviceInfoUtils.isSendFeedbackAvailable(requireContext())) {
                    mSettingsAndHelpCategory?.addPreference(getHelpAndFeedbackPreference())
                }

                updateAllowedApps(allowedApps)
                updateDeniedApps(notAllowedApps)
                updateInactiveApps(connectedAppsGroup[INACTIVE].orEmpty())
                updateNeedUpdateApps(needUpdateApps)

                viewModel.alertDialogActive.observe(viewLifecycleOwner) { state ->
                    if (state) {
                        removeAllAppsDialog.show()
                    } else {
                        if (removeAllAppsDialog.isShowing) {
                            removeAllAppsDialog.dismiss()
                        }
                    }
                }
            }
        }
    }

    private fun updateInactiveApps(appsList: List<ConnectedAppMetadata>) {
        if (appsList.isEmpty()) {
            preferenceScreen.removePreference(mInactiveAppsCategory)
        } else {
            appsList.forEach { app ->
                val inactiveAppPreference =
                    InactiveAppPreference(requireContext()).also {
                        it.title = app.appMetadata.appName
                        it.icon = app.appMetadata.icon
                        it.logName = AppPermissionsElement.INACTIVE_APP_BUTTON
                        it.setOnDeleteButtonClickListener {
                            val appDeletionType =
                                DeletionType.DeletionTypeAppData(
                                    app.appMetadata.packageName, app.appMetadata.appName)
                            childFragmentManager.setFragmentResult(
                                DeletionConstants.START_INACTIVE_APP_DELETION_EVENT,
                                bundleOf(DELETION_TYPE to appDeletionType))
                        }
                    }
                mInactiveAppsCategory?.addPreference(inactiveAppPreference)
            }
        }
    }

    private fun updateNeedUpdateApps(appsList: List<ConnectedAppMetadata>) {
        if (appsList.isEmpty()) {
            mNeedUpdateAppsCategory?.isVisible = false
        } else {
            mNeedUpdateAppsCategory?.isVisible = true
            appsList.forEach { app ->
                val intent = appStoreUtils.getAppStoreLink(app.appMetadata.packageName)
                if (intent == null) {
                    mNeedUpdateAppsCategory?.addPreference(
                        getAppPreference(app).also { it.isSelectable = false })
                } else {
                    mNeedUpdateAppsCategory?.addPreference(
                        getAppPreference(app) { navigationUtils.startActivity(this, intent) })
                }
            }

            val sharedPreference =
                requireActivity().getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
            val bannerSeen = sharedPreference.getBoolean(APP_UPDATE_NEEDED_BANNER_SEEN, false)

            if (!bannerSeen) {
                val banner = getAppUpdateNeededBanner(appsList)
                preferenceScreen.removePreferenceRecursively(BANNER_PREFERENCE_KEY)
                preferenceScreen.addPreference(banner)
            }
        }
    }

    private fun updateAllowedApps(appsList: List<ConnectedAppMetadata>) {
        if (appsList.isEmpty()) {
            mAllowedAppsCategory?.addPreference(getNoAppsPreference(R.string.no_apps_allowed))
        } else {
            appsList.forEach { app ->
                mAllowedAppsCategory?.addPreference(
                    getAppPreference(app) { navigateToAppInfoScreen(app) })
            }
        }
    }

    private fun updateDeniedApps(appsList: List<ConnectedAppMetadata>) {
        if (appsList.isEmpty()) {
            mNotAllowedAppsCategory?.addPreference(getNoAppsPreference(R.string.no_apps_denied))
        } else {
            appsList.forEach { app ->
                mNotAllowedAppsCategory?.addPreference(
                    getAppPreference(app) { navigateToAppInfoScreen(app) })
            }
        }
    }

    private fun navigateToAppInfoScreen(app: ConnectedAppMetadata) {
        findNavController()
            .navigate(
                R.id.action_connectedApps_to_connectedApp,
                bundleOf(
                    EXTRA_PACKAGE_NAME to app.appMetadata.packageName,
                    EXTRA_APP_NAME to app.appMetadata.appName))
    }

    private fun getNoAppsPreference(@StringRes res: Int): Preference {
        return Preference(requireContext()).also {
            it.setTitle(res)
            it.isSelectable = false
        }
    }

    private fun getAppPreference(
        app: ConnectedAppMetadata,
        onClick: (() -> Unit)? = null
    ): AppPreference {
        return HealthAppPreference(requireContext(), app.appMetadata).also {
            if (app.status == ALLOWED) {
                it.logName = AppPermissionsElement.CONNECTED_APP_BUTTON
            } else if (app.status == DENIED) {
                it.logName = AppPermissionsElement.NOT_CONNECTED_APP_BUTTON
            } else if (app.status == NEEDS_UPDATE) {
                it.logName = AppPermissionsElement.NEEDS_UPDATE_APP_BUTTON
            }
            it.setOnPreferenceClickListener {
                onClick?.invoke()
                true
            }
        }
    }

    private fun getRemoveAccessForAllAppsPreference(): HealthPreference {
        return HealthPreference(requireContext()).also {
            it.title = resources.getString(R.string.disconnect_all_apps)
            it.icon =
                AttributeResolver.getDrawable(requireContext(), R.attr.removeAccessForAllAppsIcon)
            it.logName = AppPermissionsElement.REMOVE_ALL_APPS_PERMISSIONS_BUTTON
        }
    }

    private fun getHelpAndFeedbackPreference(): HealthPreference {
        return HealthPreference(requireContext()).also {
            it.title = resources.getString(R.string.help_and_feedback)
            it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.helpAndFeedbackIcon)
            it.logName = AppPermissionsElement.HELP_AND_FEEDBACK_BUTTON
            it.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_connectedApps_to_helpAndFeedback)
                true
            }
        }
    }

    private fun getCheckForUpdatesPreference(): HealthPreference {
        return HealthPreference(requireContext()).also {
            it.title = resources.getString(R.string.check_for_updates)
            it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.checkForUpdatesIcon)
            it.summary = resources.getString(R.string.check_for_updates_description)
            it.logName = AppPermissionsElement.CHECK_FOR_UPDATES_BUTTON
            it.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_connected_apps_to_updated_apps)
                true
            }
        }
    }

    private fun getSeeAllCompatibleAppsPreference(): HealthPreference {
        return HealthPreference(requireContext()).also {
            it.title = resources.getString(R.string.see_all_compatible_apps)
            it.icon =
                AttributeResolver.getDrawable(requireContext(), R.attr.seeAllCompatibleAppsIcon)
            it.summary = resources.getString(R.string.see_all_compatible_apps_description)
            it.logName = AppPermissionsElement.SEE_ALL_COMPATIBLE_APPS_BUTTON
            it.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_connected_apps_to_play_store)
                true
            }
        }
    }

    private fun getSendFeedbackPreference(): Preference {
        return HealthPreference(requireContext()).also {
            it.title = resources.getString(R.string.send_feedback)
            it.icon = AttributeResolver.getDrawable(requireContext(), R.attr.sendFeedbackIcon)
            it.summary = resources.getString(R.string.send_feedback_description)
            it.logName = AppPermissionsElement.SEND_FEEDBACK_BUTTON
            it.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_BUG_REPORT)
                intent.putExtra("category_tag", APP_INTEGRATION_REQUEST_BUCKET_ID)
                activity?.startActivityForResult(intent, FEEDBACK_INTENT_RESULT_CODE)
                true
            }
        }
    }

    private fun getAppUpdateNeededBanner(appsList: List<ConnectedAppMetadata>): BannerPreference {
        return BannerPreference(requireContext(), MigrationElement.MIGRATION_APP_UPDATE_BANNER)
            .also { banner ->
                banner.setPrimaryButton(
                    resources.getString(R.string.app_update_needed_banner_button),
                    MigrationElement.MIGRATION_APP_UPDATE_BUTTON)
                banner.setSecondaryButton(
                    resources.getString(R.string.app_update_needed_banner_learn_more_button),
                    MigrationElement.MIGRATION_APP_UPDATE_LEARN_MORE_BUTTON)
                banner.title = resources.getString(R.string.app_update_needed_banner_title)

                if (appsList.size > 1) {
                    banner.summary =
                        resources.getString(R.string.app_update_needed_banner_description_multiple)
                } else {
                    banner.summary =
                        resources.getString(
                            R.string.app_update_needed_banner_description_single,
                            appsList[0].appMetadata.appName)
                }

                banner.key = BANNER_PREFERENCE_KEY
                banner.setIcon(R.drawable.ic_apps_outage)
                banner.order = 1
                if (deviceInfoUtils.isPlayStoreAvailable(requireContext())) {
                    banner.setPrimaryButtonOnClickListener {
                        findNavController().navigate(R.id.action_connected_apps_to_updated_apps)
                        true
                    }
                } else {
                    banner.setPrimaryButtonVisibility(GONE)
                }

                banner.setSecondaryButtonOnClickListener {
                    deviceInfoUtils.openHCGetStartedLink(requireActivity())
                }
                banner.setIsDismissable(true)
                banner.setDismissAction(
                    MigrationElement.MIGRATION_APP_UPDATE_BANNER_DISMISS_BUTTON) {
                        val sharedPreference =
                            requireActivity()
                                .getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
                        sharedPreference.edit().apply {
                            putBoolean(APP_UPDATE_NEEDED_BANNER_SEEN, true)
                            apply()
                        }
                        preferenceScreen.removePreference(banner)
                    }
            }
    }

    private fun setUpEmptyState() {
        mTopIntro?.title = getString(R.string.connected_apps_empty_list_section_title)
        if (deviceInfoUtils.isPlayStoreAvailable(requireContext()) ||
            deviceInfoUtils.isSendFeedbackAvailable(requireContext())) {
            mThingsToTryCategory?.isVisible = true
        }
        if (deviceInfoUtils.isPlayStoreAvailable(requireContext())) {
            mThingsToTryCategory?.addPreference(getCheckForUpdatesPreference())
            mThingsToTryCategory?.addPreference(getSeeAllCompatibleAppsPreference())
        }
        if (deviceInfoUtils.isSendFeedbackAvailable(requireContext())) {
            mThingsToTryCategory?.addPreference(getSendFeedbackPreference())
        }
        setAppAndSettingsCategoriesVisibility(false)
    }

    private fun setAppAndSettingsCategoriesVisibility(isVisible: Boolean) {
        mInactiveAppsCategory?.isVisible = isVisible
        mAllowedAppsCategory?.isVisible = isVisible
        mNeedUpdateAppsCategory?.isVisible = isVisible
        mNotAllowedAppsCategory?.isVisible = isVisible
        mSettingsAndHelpCategory?.isVisible = isVisible
    }

    private fun clearAllCategories() {
        mThingsToTryCategory?.removeAll()
        mAllowedAppsCategory?.removeAll()
        mNotAllowedAppsCategory?.removeAll()
        mNeedUpdateAppsCategory?.removeAll()
        mInactiveAppsCategory?.removeAll()
        mSettingsAndHelpCategory?.removeAll()
    }
}
