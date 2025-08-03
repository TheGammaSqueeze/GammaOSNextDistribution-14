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
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.android.car.ui.preference.CarUiPreference
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_REMINDER_NOTIFICATION_INTERACTED__RESULT__NOTIFICATION_CLICKED
import com.android.permissioncontroller.PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__REVIEW_DECISION
import com.android.permissioncontroller.PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__SCREEN_VIEWED
import com.android.permissioncontroller.PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__VIEW_ALL_CLICKED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment
import com.android.permissioncontroller.auto.DrivingDecisionReminderService
import com.android.permissioncontroller.permission.data.v33.PermissionDecision
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.model.v33.ReviewPermissionDecisionsViewModel
import com.android.permissioncontroller.permission.ui.model.v33.ReviewPermissionDecisionsViewModelFactory
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageUid
import com.android.permissioncontroller.permission.utils.Utils
import kotlin.math.min

/** Shows summary of recent permission decisions. */
@SuppressLint("NewApi")
class AutoReviewPermissionDecisionsFragment : AutoSettingsFrameFragment() {

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SOURCE_NOTIFICATION = "notification"
        private const val LOG_TAG = "AutoReviewPermissionDecisionsFragment"
        private const val MAX_DECISIONS = 3

        /** Creates a new instance of [AutoReviewPermissionDecisionsFragment]. */
        fun newInstance(
            sessionId: Long,
            userHandle: UserHandle,
            source: String?
        ): AutoReviewPermissionDecisionsFragment {
            return AutoReviewPermissionDecisionsFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong(Constants.EXTRA_SESSION_ID, sessionId)
                        putParcelable(Intent.EXTRA_USER, userHandle)
                        putString(EXTRA_SOURCE, source)
                    }
            }
        }
    }

    private lateinit var user: UserHandle
    private lateinit var viewModel: ReviewPermissionDecisionsViewModel
    private lateinit var recentPermissionsGroup: PreferenceCategory
    private var sessionId: Long = Constants.INVALID_SESSION_ID

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
        if (!requireArguments().containsKey(Constants.EXTRA_SESSION_ID)) {
            DumpableLog.e(LOG_TAG, "Missing argument ${Constants.EXTRA_SESSION_ID}")
            activity?.finish()
            return
        }
        user = requireArguments().getParcelable<UserHandle>(Intent.EXTRA_USER)!!
        sessionId = requireArguments().getLong(Constants.EXTRA_SESSION_ID)
        if (
            requireArguments().containsKey(EXTRA_SOURCE) &&
                (requireArguments().getString(EXTRA_SOURCE) == EXTRA_SOURCE_NOTIFICATION)
        ) {
            DrivingDecisionReminderService.cancelNotification(requireActivity())
            logDecisionReminderNotificationClicked()
        }
        val factory =
            ReviewPermissionDecisionsViewModelFactory(requireActivity().getApplication()!!, user)
        viewModel = ViewModelProvider(this, factory)[ReviewPermissionDecisionsViewModel::class.java]

        addPrivacyDashboardPreference()
        addPermissionManagerPreference()
        preferenceScreen.addPreference(AutoDividerPreference(context))
        recentPermissionsGroup =
            PreferenceCategory(context!!).apply {
                title = getString(R.string.review_permission_decisions)
            }
        preferenceScreen.addPreference(recentPermissionsGroup)

        viewModel.recentPermissionDecisionsLiveData.observe(this) { recentDecisions ->
            onRecentDecisionsChanged(recentDecisions)
        }
        headerLabel = getString(R.string.app_permissions)

        logScreenViewed()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context!!)
    }

    private fun onRecentDecisionsChanged(recentDecisions: List<PermissionDecision>) {
        recentPermissionsGroup.removeAll()

        if (recentDecisions.isEmpty()) {
            addNoRecentDecisionsPreference(recentPermissionsGroup)
        } else {
            addRecentDecisionPreferences(recentPermissionsGroup, recentDecisions)
        }
        if (recentDecisions.size > MAX_DECISIONS) {
            addViewAllPreference(recentPermissionsGroup)
        }
    }

    private fun addPrivacyDashboardPreference() {
        val preference =
            CarUiPreference(context).apply {
                title = getString(R.string.permission_usage_title)
                summary = getString(R.string.auto_permission_usage_summary)
                onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { _ ->
                        val intent =
                            Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                            }
                        startActivity(intent)
                        true
                    }
            }
        preferenceScreen.addPreference(preference)
    }

    private fun addPermissionManagerPreference() {
        val preference =
            CarUiPreference(context).apply {
                title = getString(R.string.app_permission_manager)
                summary = getString(R.string.auto_permission_manager_summary)
                onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { _ ->
                        val intent =
                            Intent(Intent.ACTION_MANAGE_PERMISSIONS).apply {
                                putExtra(Intent.EXTRA_USER, user)
                                putExtra(
                                    ManagePermissionsActivity.EXTRA_CALLER_NAME,
                                    javaClass.name
                                )
                                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                            }
                        startActivity(intent)
                        true
                    }
            }
        preferenceScreen.addPreference(preference)
    }

    private fun addRecentDecisionPreferences(
        preferenceGroup: PreferenceGroup,
        recentDecisions: List<PermissionDecision>
    ) {
        for (i in 0 until min(recentDecisions.size, MAX_DECISIONS)) {
            val recentDecision = recentDecisions[i]
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
                            logPermissionDecisionClicked(
                                recentDecision.packageName,
                                recentDecision.permissionGroupName
                            )
                            true
                        }
                }
            preferenceGroup.addPreference(decisionPreference)
        }
    }

    private fun addViewAllPreference(preferenceGroup: PreferenceGroup) {
        val viewAllIcon = requireContext().getDrawable(R.drawable.car_ic_apps)
        val preference =
            CarUiPreference(context).apply {
                icon = Utils.applyTint(context, viewAllIcon, android.R.attr.colorControlNormal)
                title = getString(R.string.review_permission_decisions_view_all)
                onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val frag =
                            AutoReviewPermissionDecisionsViewAllFragment.newInstance(
                                sessionId,
                                user
                            )
                        getParentFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, frag)
                            .addToBackStack(null)
                            .commit()
                        logViewAllClicked()
                        true
                    }
            }
        preferenceGroup.addPreference(preference)
    }

    private fun addNoRecentDecisionsPreference(preferenceGroup: PreferenceGroup) {
        val preference =
            CarUiPreference(context).apply {
                title = getString(R.string.review_permission_decisions_empty)
            }
        preferenceGroup.addPreference(preference)
    }

    private fun logScreenViewed() {
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED,
            sessionId,
            RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__SCREEN_VIEWED,
            0,
            null
        )
    }

    private fun logViewAllClicked() {
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED,
            sessionId,
            RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__VIEW_ALL_CLICKED,
            0,
            null
        )
    }

    private fun logPermissionDecisionClicked(packageName: String, permissionGroupName: String) {
        val uid = getPackageUid(requireActivity().getApplication(), packageName, user) ?: return
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.RECENT_PERMISSION_DECISIONS_INTERACTED,
            sessionId,
            RECENT_PERMISSION_DECISIONS_INTERACTED__ACTION__REVIEW_DECISION,
            uid,
            permissionGroupName
        )
    }

    private fun logDecisionReminderNotificationClicked() {
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.PERMISSION_REMINDER_NOTIFICATION_INTERACTED,
            sessionId,
            PERMISSION_REMINDER_NOTIFICATION_INTERACTED__RESULT__NOTIFICATION_CLICKED
        )
    }
}
