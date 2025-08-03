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
 */

package com.android.permissioncontroller.permission.ui.wear

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModel
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModel.PermissionUsageViewModelFactory

/**
 * This is a condensed version of
 * [com.android.permissioncontroller.permission.ui.handheld.v31.PermissionUsageFragment], tailored
 * for Wear.
 */
@RequiresApi(Build.VERSION_CODES.S)
class WearPermissionUsageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val sessionId: Long =
            arguments?.getLong(Constants.EXTRA_SESSION_ID) ?: Constants.INVALID_SESSION_ID
        val factory =
            PermissionUsageViewModelFactory(requireActivity().getApplication(), this, Bundle())
        val viewModel: PermissionUsageViewModel =
            ViewModelProvider(this, factory).get(PermissionUsageViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setContent { WearPermissionUsageScreen(sessionId, viewModel) }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(sessionId: Long): WearPermissionUsageFragment {
            return WearPermissionUsageFragment().apply {
                val arguments = Bundle().apply { putLong(Constants.EXTRA_SESSION_ID, sessionId) }
                setArguments(arguments)
            }
        }
    }
}
