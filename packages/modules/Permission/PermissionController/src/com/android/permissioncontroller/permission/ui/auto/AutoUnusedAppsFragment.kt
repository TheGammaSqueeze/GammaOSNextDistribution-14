/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.auto

import android.app.Application
import android.os.Bundle
import android.os.UserHandle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.car.ui.utils.ViewUtils
import com.android.car.ui.utils.ViewUtils.LazyLayoutView
import com.android.permissioncontroller.R
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment.Companion.INFO_MSG_CATEGORY

/** Auto wrapper, with customizations, around [UnusedAppsFragment]. */
class AutoUnusedAppsFragment :
    AutoSettingsFrameFragment(), UnusedAppsFragment.Parent<AutoUnusedAppsPreference> {

    companion object {
        private const val UNUSED_PREFERENCE_KEY = "unused_pref_row_key"

        /** Create a new instance of this fragment. */
        @JvmStatic
        fun newInstance(): AutoUnusedAppsFragment {
            return AutoUnusedAppsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Preferences will be added via shared logic in [UnusedAppsFragment].
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment: UnusedAppsFragment<AutoUnusedAppsFragment, AutoUnusedAppsPreference> =
                UnusedAppsFragment.newInstance()
            fragment.arguments = arguments
            // child fragment does not have its own UI - it will add to the preferences of this
            // parent fragment
            childFragmentManager.beginTransaction().add(fragment, null).commit()
        }

        // initially focus on focus parking view and then shift focus to recyclerview once it has
        // loaded
        ViewUtils.hideFocus(getCarUiRecyclerView().getView().getRootView())
        val lazyLayoutView = getCarUiRecyclerView() as LazyLayoutView
        ViewUtils.initFocus(lazyLayoutView)
    }

    override fun createFooterPreference(): Preference {
        val preference = Preference(context!!)
        if (isHibernationEnabled()) {
            preference.summary = getString(R.string.unused_apps_page_summary)
        } else {
            preference.summary =
                """
            ${getString(R.string.auto_revoked_apps_page_summary)}
            ${getString(R.string.auto_revoke_open_app_message)}
            """
                    .trimIndent()
        }
        preference.setIcon(R.drawable.ic_info_outline)
        preference.isSelectable = false
        return preference
    }

    override fun setLoadingState(loading: Boolean, animate: Boolean) {
        setLoading(false)
    }

    override fun createUnusedAppPref(
        app: Application,
        packageName: String,
        user: UserHandle
    ): AutoUnusedAppsPreference {
        return AutoUnusedAppsPreference(app, packageName, user, requireContext())
    }

    override fun setTitle(title: CharSequence) {
        headerLabel = title
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
        val preference = Preference(context!!)
        preference.title = getString(R.string.zero_unused_apps)
        preference.key = UNUSED_PREFERENCE_KEY
        preference.isSelectable = false
        preference.order = 0
        return preference
    }
}
