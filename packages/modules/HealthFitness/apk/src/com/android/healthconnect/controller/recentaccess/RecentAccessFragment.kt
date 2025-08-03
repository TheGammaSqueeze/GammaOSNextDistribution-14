/*
 * Copyright (C) 2022 The Android Open Source Project
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
 */

package com.android.healthconnect.controller.recentaccess

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.RecentAccessElement
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Recent access fragment showing a timeline of apps that have recently accessed Health Connect. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class RecentAccessFragment : Hilt_RecentAccessFragment() {

    companion object {
        private const val RECENT_ACCESS_TODAY_KEY = "recent_access_today"
        private const val RECENT_ACCESS_YESTERDAY_KEY = "recent_access_yesterday"
        private const val RECENT_ACCESS_NO_DATA_KEY = "no_data"
    }

    init {
        this.setPageName(PageName.RECENT_ACCESS_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var timeSource: TimeSource

    private val viewModel: RecentAccessViewModel by viewModels()
    private lateinit var contentParent: FrameLayout
    private lateinit var fab: ExtendedFloatingActionButton
    private var recyclerView: RecyclerView? = null

    private val mRecentAccessTodayPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(RECENT_ACCESS_TODAY_KEY)
    }

    private val mRecentAccessYesterdayPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(RECENT_ACCESS_YESTERDAY_KEY)
    }

    private val mRecentAccessNoDataPreference: Preference? by lazy {
        preferenceScreen.findPreference(RECENT_ACCESS_NO_DATA_KEY)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.recent_access_preference_screen, rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        contentParent = requireActivity().findViewById(android.R.id.content)
        inflater.inflate(R.layout.widget_floating_action_button, contentParent)

        fab = contentParent.findViewById(R.id.extended_fab)
        fab.isVisible = true

        recyclerView = rootView?.findViewById(
            androidx.preference.R.id.recycler_view)
        val bottomPadding =
            resources.getDimensionPixelSize(R.dimen.recent_access_fab_bottom_padding)
        recyclerView?.setPadding(0, 0, 0, bottomPadding)

        return rootView
    }

    override fun onPause() {
        // Prevents FAB from being permanently attached to the activity layout
        contentParent.removeView(fab)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecentAccessApps()

        if (fab.parent == null) {
            contentParent.addView(fab)
        }
        logger.logImpression(RecentAccessElement.MANAGE_PERMISSIONS_FAB)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadRecentAccessApps()
        viewModel.recentAccessApps.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RecentAccessState.Loading -> {
                    setLoading(true)
                }
                is RecentAccessState.Error -> {
                    setError(true)
                }
                is RecentAccessState.WithData -> {
                    setLoading(false)
                    updateRecentApps(state.recentAccessEntries)
                }
            }
        }
    }

    private fun updateRecentApps(recentAppsList: List<RecentAccessEntry>) {
        mRecentAccessTodayPreferenceGroup?.removeAll()
        mRecentAccessYesterdayPreferenceGroup?.removeAll()
        mRecentAccessNoDataPreference?.isVisible = false

        if (recentAppsList.isEmpty()) {
            mRecentAccessYesterdayPreferenceGroup?.isVisible = false
            mRecentAccessTodayPreferenceGroup?.isVisible = false
            mRecentAccessNoDataPreference?.isVisible = true
            mRecentAccessNoDataPreference?.isSelectable = false
            fab.isVisible = false
        } else {
            // if the first entry is yesterday, we don't need the 'Today' section
            mRecentAccessTodayPreferenceGroup?.isVisible = recentAppsList[0].isToday

            // if the last entry is today, we don't need the 'Yesterday' section
            mRecentAccessYesterdayPreferenceGroup?.isVisible = !recentAppsList.last().isToday

            fab.setOnClickListener {
                logger.logInteraction(RecentAccessElement.MANAGE_PERMISSIONS_FAB)
                findNavController()
                    .navigate(R.id.action_recentAccessFragment_to_connectedAppsFragment)
            }

            recentAppsList.forEachIndexed { index, recentApp ->
                val isLastUsage =
                    (index == recentAppsList.size - 1) ||
                        (recentApp.isToday &&
                            index < recentAppsList.size - 1 &&
                            !recentAppsList[index + 1].isToday)
                val newPreference =
                    RecentAccessPreference(requireContext(), recentApp, timeSource, true).also {
                        if (!recentApp.isInactive) {
                            // Do not set click listeners for inactive apps
                            it.setOnPreferenceClickListener {
                                if (findNavController().currentDestination?.id ==
                                    R.id.recentAccessFragment) {
                                    findNavController()
                                        .navigate(
                                            R.id
                                                .action_recentAccessFragment_to_connectedAppFragment,
                                            bundleOf(
                                                Intent.EXTRA_PACKAGE_NAME to
                                                    recentApp.metadata.packageName,
                                                Constants.EXTRA_APP_NAME to
                                                    recentApp.metadata.appName))
                                }
                                true
                            }
                        }
                    }

                if (recentApp.isToday) {
                    mRecentAccessTodayPreferenceGroup?.addPreference(newPreference)
                    if (!isLastUsage) {
                        mRecentAccessTodayPreferenceGroup?.addPreference(
                            DividerPreference(requireContext()))
                    }
                } else {
                    mRecentAccessYesterdayPreferenceGroup?.addPreference(newPreference)
                    if (!isLastUsage) {
                        mRecentAccessYesterdayPreferenceGroup?.addPreference(
                            DividerPreference(requireContext()))
                    }
                }
            }
        }
    }
}
