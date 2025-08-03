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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.permissioncontroller.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseFragment

/** Wrapper over HandheldUnusedAppsFragment */
class HandheldUnusedAppsWrapperFragment : CollapsingToolbarBaseFragment() {
    companion object {
        /** Create a new instance of this fragment. */
        @JvmStatic
        fun newInstance(): HandheldUnusedAppsWrapperFragment {
            return HandheldUnusedAppsWrapperFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        inflater.inflate(R.layout.settings_fragment_include, contentFrameLayout)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var preferenceFragment =
            childFragmentManager.findFragmentById(R.id.preference_fragment_container)
                as HandheldUnusedAppsFragment?
        if (preferenceFragment == null) {
            preferenceFragment = HandheldUnusedAppsFragment.newInstance()
            preferenceFragment.arguments = arguments
            childFragmentManager
                .beginTransaction()
                .add(R.id.preference_fragment_container, preferenceFragment)
                .commit()
        }
    }
}
