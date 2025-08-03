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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui.television

import android.app.Application
import android.os.Bundle
import android.os.UserHandle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment.Companion.INFO_MSG_CATEGORY

/** TV wrapper, with customizations, around [UnusedAppsFragment]. */
class TvUnusedAppsFragment :
    SettingsWithHeader(), UnusedAppsFragment.Parent<TvUnusedAppsPreference> {

    companion object {
        private const val UNUSED_PREFERENCE_KEY = "unused_pref_row_key"

        /** Create a new instance of this fragment. */
        @JvmStatic
        fun newInstance(): TvUnusedAppsFragment {
            return TvUnusedAppsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Preferences will be added via shared logic in [UnusedAppsFragment].
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment: UnusedAppsFragment<TvUnusedAppsFragment, TvUnusedAppsPreference> =
                UnusedAppsFragment.newInstance()
            fragment.arguments = arguments
            // child fragment does not have its own UI - it will add to the preferences of this
            // parent fragment
            childFragmentManager.beginTransaction().add(fragment, null).commit()
        }
    }

    override fun createFooterPreference(): Preference {
        val preference = com.android.settingslib.widget.FooterPreference(requireContext())
        if (isHibernationEnabled()) {
            preference.summary = getString(R.string.unused_apps_page_tv_summary)
        } else {
            preference.summary = getString(R.string.auto_revoked_apps_page_summary)
        }
        preference.setIcon(R.drawable.ic_info_outline)
        preference.isSelectable = false
        return preference
    }

    override fun setLoadingState(loading: Boolean, animate: Boolean) {
        setLoading(loading, animate)
    }

    override fun createUnusedAppPref(
        app: Application,
        packageName: String,
        user: UserHandle
    ): TvUnusedAppsPreference {
        return TvUnusedAppsPreference(app, packageName, user, requireContext())
    }

    override fun setTitle(title: CharSequence) {
        setHeader(null, null, null, title)
    }

    override fun setEmptyState(empty: Boolean) {
        val infoMsgCategory =
            preferenceScreen.findPreference<PreferenceCategory>(INFO_MSG_CATEGORY)!!
        val noUnusedAppsPreference: Preference? =
            infoMsgCategory.findPreference<Preference>(UNUSED_PREFERENCE_KEY)
        if (empty && noUnusedAppsPreference == null) {
            infoMsgCategory.addPreference(createNoUnusedAppsPreference())
        } else if (noUnusedAppsPreference != null) {
            noUnusedAppsPreference.setVisible(empty)
        }
    }

    private fun createNoUnusedAppsPreference(): Preference {
        val preference = Preference(requireContext())
        preference.title = getString(R.string.zero_unused_apps)
        preference.key = UNUSED_PREFERENCE_KEY
        preference.isSelectable = false
        preference.order = 0
        return preference
    }
}
