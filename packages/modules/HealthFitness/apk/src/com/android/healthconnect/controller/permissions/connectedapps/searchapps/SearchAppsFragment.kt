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
package com.android.healthconnect.controller.permissions.connectedapps.searchapps

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel
import com.android.healthconnect.controller.permissions.connectedapps.HealthAppPreference
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.ALLOWED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.DENIED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.INACTIVE
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.settingslib.widget.TopIntroPreference
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for search apps screen. */
@AndroidEntryPoint(PreferenceFragmentCompat::class)
class SearchAppsFragment : Hilt_SearchAppsFragment() {

    companion object {
        const val ALLOWED_APPS_CATEGORY = "allowed_apps"
        private const val NOT_ALLOWED_APPS = "not_allowed_apps"
        private const val INACTIVE_APPS = "inactive_apps"
        private const val EMPTY_SEARCH_RESULT = "no_search_result_preference"
        private const val TOP_INTRO_PREF = "search_apps_top_intro"
    }

    @Inject lateinit var logger: HealthConnectLogger
    private val pageName = PageName.SEARCH_APPS_PAGE

    private var searchView: SearchView? = null
    private val viewModel: ConnectedAppsViewModel by viewModels()

    private val allowedAppsCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(ALLOWED_APPS_CATEGORY)!!
    }
    private val notAllowedAppsCategory: PreferenceGroup by lazy {
        preferenceScreen.findPreference(NOT_ALLOWED_APPS)!!
    }
    private val inactiveAppsPreference: PreferenceGroup by lazy {
        preferenceScreen.findPreference(INACTIVE_APPS)!!
    }
    private val emptySearchResultsPreference: NoSearchResultPreference by lazy {
        preferenceScreen.findPreference(EMPTY_SEARCH_RESULT)!!
    }
    private val topIntroPreference: TopIntroPreference by lazy {
        preferenceScreen.findPreference(TOP_INTRO_PREF)!!
    }

    private val menuProvider =
        object : MenuProvider, SearchView.OnQueryTextListener {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.search_apps, menu)
                val searchMenuItem = menu.findItem(R.id.menu_search_apps)
                searchMenuItem.expandActionView()
                searchMenuItem.setOnActionExpandListener(
                    object : MenuItem.OnActionExpandListener {

                        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                            return true
                        }

                        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                            showTitleFromCollapsingToolbarLayout()
                            findNavController().popBackStack()
                            return true
                        }
                    })
                searchView = searchMenuItem.actionView as SearchView
                searchView!!.queryHint = getText(R.string.search_connected_apps)
                searchView!!.setOnQueryTextListener(this)
                searchView!!.maxWidth = Int.MAX_VALUE
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }

            override fun onQueryTextSubmit(p0: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.searchConnectedApps(newText)
                return true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.setPageId(pageName)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.search_apps_screen, rootKey)
        preferenceScreen.addPreference(NoSearchResultPreference(requireContext()))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        hideTitleFromCollapsingToolbarLayout()
        viewModel.connectedApps.observe(viewLifecycleOwner) { connectedApps ->
            emptySearchResultsPreference?.isVisible = connectedApps.isEmpty()
            topIntroPreference?.isVisible = connectedApps.isNotEmpty()

            val connectedAppsGroup = connectedApps.groupBy { it.status }
            updateAllowedApps(connectedAppsGroup[ALLOWED].orEmpty())
            updateDeniedApps(connectedAppsGroup[DENIED].orEmpty())
            updateInactiveApps(connectedAppsGroup[INACTIVE].orEmpty())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showTitleFromCollapsingToolbarLayout()
    }

    override fun onResume() {
        super.onResume()
        hideTitleFromCollapsingToolbarLayout()
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

    private fun updateInactiveApps(appsList: List<ConnectedAppMetadata>) {
        inactiveAppsPreference?.removeAll()
        if (appsList.isEmpty()) {
            preferenceScreen.removePreference(inactiveAppsPreference)
        } else {
            preferenceScreen.addPreference(inactiveAppsPreference)
            appsList.forEach { app -> inactiveAppsPreference?.addPreference(getAppPreference(app)) }
        }
    }

    private fun updateAllowedApps(appsList: List<ConnectedAppMetadata>) {
        allowedAppsCategory?.removeAll()
        if (appsList.isEmpty()) {
            preferenceScreen.removePreference(allowedAppsCategory)
        } else {
            preferenceScreen.addPreference(allowedAppsCategory)
            appsList.forEach { app ->
                allowedAppsCategory?.addPreference(
                    getAppPreference(app) { navigateToAppInfoScreen(app) })
            }
        }
    }

    private fun updateDeniedApps(appsList: List<ConnectedAppMetadata>) {
        notAllowedAppsCategory?.removeAll()
        if (appsList.isEmpty()) {
            preferenceScreen.removePreference(notAllowedAppsCategory)
        } else {
            preferenceScreen.addPreference(notAllowedAppsCategory)
            appsList.forEach { app ->
                notAllowedAppsCategory?.addPreference(
                    getAppPreference(app) { navigateToAppInfoScreen(app) })
            }
        }
    }

    private fun navigateToAppInfoScreen(app: ConnectedAppMetadata) {
        findNavController()
            .navigate(
                R.id.action_searchApps_to_connectedApp,
                bundleOf(
                    EXTRA_PACKAGE_NAME to app.appMetadata.packageName,
                    EXTRA_APP_NAME to app.appMetadata.appName))
    }

    private fun getAppPreference(
        app: ConnectedAppMetadata,
        onClick: (() -> Unit)? = null
    ): Preference {
        return HealthAppPreference(requireContext(), app.appMetadata).also {
            if (app.status == ALLOWED) {
                it.logName = AppPermissionsElement.CONNECTED_APP_BUTTON
            } else if (app.status == DENIED) {
                it.logName = AppPermissionsElement.NOT_CONNECTED_APP_BUTTON
            } else if (app.status == INACTIVE) {
                it.logName = AppPermissionsElement.INACTIVE_APP_BUTTON
            }
            it.setOnPreferenceClickListener {
                onClick?.invoke()
                true
            }
        }
    }

    private fun setupMenu() {
        (activity as MenuHost).addMenuProvider(
            menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun hideTitleFromCollapsingToolbarLayout() {
        activity?.findViewById<AppBarLayout>(
            com.android.settingslib.collapsingtoolbar.R.id.app_bar)?.setExpanded(false)
    }

    private fun showTitleFromCollapsingToolbarLayout() {
        activity?.findViewById<AppBarLayout>(
            com.android.settingslib.collapsingtoolbar.R.id.app_bar)?.setExpanded(true)
    }
}
