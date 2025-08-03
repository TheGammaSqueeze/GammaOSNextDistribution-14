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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import com.android.car.ui.preference.CarUiPreference
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.R
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment
import com.android.permissioncontroller.permission.data.v33.PermissionDecision
import com.android.permissioncontroller.permission.ui.model.v33.ReviewPermissionDecisionsViewModel
import com.android.permissioncontroller.permission.ui.model.v33.ReviewPermissionDecisionsViewModelFactory

/** Shows all recent permission decisions. */
@SuppressLint("NewApi")
class AutoReviewPermissionDecisionsViewAllFragment : AutoSettingsFrameFragment() {

    companion object {
        private const val LOG_TAG = "AutoReviewPermissionDecisionsViewAllFragment"

        /** Creates a new instance of [AutoReviewPermissionDecisionsViewAllFragment]. */
        fun newInstance(
            sessionId: Long,
            userHandle: UserHandle
        ): AutoReviewPermissionDecisionsViewAllFragment {
            return AutoReviewPermissionDecisionsViewAllFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong(Constants.EXTRA_SESSION_ID, sessionId)
                        putParcelable(Intent.EXTRA_USER, userHandle)
                    }
            }
        }
    }

    private lateinit var user: UserHandle
    private lateinit var viewModel: ReviewPermissionDecisionsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments == null) {
            DumpableLog.e(LOG_TAG, "Missing arguments")
            activity?.finish()
            return
        }
        if (!requireArguments().containsKey(Intent.EXTRA_USER)) {
            DumpableLog.e(LOG_TAG, "Missing argument ${Intent.EXTRA_USER}")
            activity?.finish()
            return
        }
        user = requireArguments().getParcelable<UserHandle>(Intent.EXTRA_USER)!!
        val factory =
            ReviewPermissionDecisionsViewModelFactory(requireActivity().getApplication()!!, user)
        viewModel = ViewModelProvider(this, factory)[ReviewPermissionDecisionsViewModel::class.java]
        viewModel.recentPermissionDecisionsLiveData.observe(this) { recentDecisions ->
            onRecentDecisionsChanged(recentDecisions)
        }
        headerLabel = getString(R.string.review_permission_decisions)
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context!!)
    }

    private fun onRecentDecisionsChanged(recentDecisions: List<PermissionDecision>) {
        preferenceScreen.removeAll()
        for (recentDecision in recentDecisions) {
            val decisionPreference =
                CarUiPreference(context).apply {
                    icon = viewModel.getAppIcon(recentDecision.packageName)
                    title = viewModel.createPreferenceTitle(recentDecision)
                    summary = viewModel.createSummaryText(recentDecision)
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            viewModel.createManageAppPermissionIntent(recentDecision).also {
                                startActivity(it)
                            }
                            false
                        }
                }
            preferenceScreen.addPreference(decisionPreference)
        }
    }
}
