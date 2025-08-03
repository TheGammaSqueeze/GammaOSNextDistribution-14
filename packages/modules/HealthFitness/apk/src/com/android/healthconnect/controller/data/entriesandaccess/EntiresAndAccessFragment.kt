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
package com.android.healthconnect.controller.data.entriesandaccess

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.access.AccessFragment
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.data.entries.AllEntriesFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment with [AllEntriesFragment] tab and [AccessFragment] tab. */
@AndroidEntryPoint(Fragment::class)
class EntriesAndAccessFragment : Hilt_EntriesAndAccessFragment() {

    @Inject lateinit var logger: HealthConnectLogger

    private lateinit var permissionType: HealthPermissionType
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO(b/291249677): Add logging.
        // logger.setPageId(pageName)

        if (requireArguments().containsKey(PERMISSION_TYPE_KEY)) {
            permissionType =
                arguments?.getSerializable(PERMISSION_TYPE_KEY, HealthPermissionType::class.java)
                    ?: throw IllegalArgumentException("PERMISSION_TYPE_KEY can't be null!")
        }
        return inflater.inflate(R.layout.fragment_entries_access, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.view_pager)
        viewPager.adapter = ViewPagerAdapter(this, permissionType)
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                if (position == 0) {
                    tab.text = getString(R.string.tab_entries)
                } else {
                    tab.text = getString(R.string.tab_access)
                }
            }
            .attach()
    }

    override fun onResume() {
        super.onResume()
    }

    class ViewPagerAdapter(
        fragment: EntriesAndAccessFragment,
        private val permissionType: HealthPermissionType
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            val fragment: Fragment = if (position == 0) AllEntriesFragment() else AccessFragment()
            fragment.arguments = bundleOf(PERMISSION_TYPE_KEY to permissionType)
            return fragment
        }
    }
}
