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

package com.android.permissioncontroller.permission.ui.handheld

import android.app.Application
import android.os.Bundle
import android.os.UserHandle
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment.Companion.INFO_MSG_CATEGORY

/** Handheld wrapper, with customizations, around [UnusedAppsFragment]. */
class HandheldUnusedAppsFragment :
    PermissionsFrameFragment(), UnusedAppsFragment.Parent<UnusedAppPreference> {

    companion object {
        /** Create a new instance of this fragment. */
        @JvmStatic
        fun newInstance(): HandheldUnusedAppsFragment {
            return HandheldUnusedAppsFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        mUseShadowController = false
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment: UnusedAppsFragment<HandheldUnusedAppsFragment, UnusedAppPreference> =
                UnusedAppsFragment.newInstance()
            fragment.arguments = arguments
            // child fragment does not have its own UI - it will add to the preferences of this
            // parent fragment
            childFragmentManager.beginTransaction().add(fragment, null).commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getEmptyViewString(): Int {
        return if (isHibernationEnabled()) R.string.no_unused_apps else super.getEmptyViewString()
    }

    override fun createFooterPreference(): Preference {
        var preference: Preference
        if (isHibernationEnabled()) {
            preference = com.android.settingslib.widget.FooterPreference(requireContext())
            preference.summary = getString(R.string.unused_apps_page_summary)
        } else {
            preference = FooterPreference(requireContext())

            preference.summary = getString(R.string.auto_revoked_apps_page_summary)
            preference.secondSummary = getString(R.string.auto_revoke_open_app_message)
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
    ): UnusedAppPreference {
        return UnusedAppPreference(app, packageName, user, requireContext())
    }

    override fun setTitle(title: CharSequence) {
        requireActivity().setTitle(title)
    }

    override fun setEmptyState(empty: Boolean) {
        val infoMsgCategory =
            preferenceScreen.findPreference<PreferenceCategory>(INFO_MSG_CATEGORY)!!
        infoMsgCategory.isVisible = !empty
    }
}
