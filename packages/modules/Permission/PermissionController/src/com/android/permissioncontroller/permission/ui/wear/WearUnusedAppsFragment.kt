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

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPackageInfo
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod.Companion.allPeriods
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModelFactory
import com.android.permissioncontroller.permission.ui.wear.model.WearUnusedAppsViewModel
import com.android.permissioncontroller.permission.ui.wear.model.WearUnusedAppsViewModel.UnusedAppChip
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.settingslib.utils.applications.AppUtils
import java.text.Collator

/**
 * This is a condensed version of
 * [com.android.permissioncontroller.permission.ui.UnusedAppsFragment.kt], tailored for Wear.
 *
 * A fragment displaying all applications that are unused as well as the option to remove them and
 * to open them.
 */
class WearUnusedAppsFragment : Fragment() {
    private lateinit var activity: FragmentActivity
    private lateinit var context: Context
    private lateinit var viewModel: UnusedAppsViewModel
    private lateinit var wearViewModel: WearUnusedAppsViewModel
    private lateinit var collator: Collator
    private var sessionId: Long = 0L
    private var isFirstLoad = false
    private var categoryVisibilities: MutableList<Boolean> =
        MutableList(UnusedPeriod.values().size) { false }
    private var unusedAppsMap: MutableMap<UnusedPeriod, MutableMap<String, UnusedAppChip>> =
        initUnusedAppsMap()

    companion object {
        private const val SHOW_LOAD_DELAY_MS = 200L
        private val LOG_TAG = WearUnusedAppsFragment::class.java.simpleName

        /**
         * Create the args needed for this fragment
         *
         * @param sessionId The current session Id
         * @return A bundle containing the session Id
         */
        @JvmStatic
        fun createArgs(sessionId: Long): Bundle {
            val bundle = Bundle()
            bundle.putLong(EXTRA_SESSION_ID, sessionId)
            return bundle
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isFirstLoad = true
        context = requireContext()
        collator =
            Collator.getInstance(context.getResources().getConfiguration().getLocales().get(0))
        activity = requireActivity()
        val application = activity.getApplication()
        sessionId = arguments!!.getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)
        val factory = UnusedAppsViewModelFactory(activity.application, sessionId)
        viewModel = ViewModelProvider(this, factory).get(UnusedAppsViewModel::class.java)
        wearViewModel =
            ViewModelProvider(
                    this,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                .get(WearUnusedAppsViewModel::class.java)
        viewModel.unusedPackageCategoriesLiveData.observe(
            this,
            Observer {
                it?.let { pkgs ->
                    updatePackages(pkgs)
                    updateWearViewModel(false)
                }
            }
        )

        if (!viewModel.unusedPackageCategoriesLiveData.isInitialized) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(
                {
                    if (!viewModel.unusedPackageCategoriesLiveData.isInitialized) {
                        wearViewModel.loadingLiveData.value = true
                    } else {
                        updatePackages(viewModel.unusedPackageCategoriesLiveData.value!!)
                        updateWearViewModel(false)
                    }
                },
                SHOW_LOAD_DELAY_MS
            )
        } else {
            updatePackages(viewModel.unusedPackageCategoriesLiveData.value!!)
            updateWearViewModel(false)
        }

        return ComposeView(activity).apply {
            setContent { WearPermissionTheme { WearUnusedAppsScreen(wearViewModel) } }
        }
    }

    private fun initUnusedAppsMap(): MutableMap<UnusedPeriod, MutableMap<String, UnusedAppChip>> {
        val res = mutableMapOf<UnusedPeriod, MutableMap<String, UnusedAppChip>>()
        for (period in allPeriods) {
            res.put(period, mutableMapOf())
        }
        return res
    }

    private fun updatePackages(categorizedPackages: Map<UnusedPeriod, List<UnusedPackageInfo>>) {
        // Remove stale unused app chips
        for (period in allPeriods) {
            val unUsedAppsInAPeriod = unusedAppsMap[period] ?: continue
            val categorizedPackagesOfAPeriod = categorizedPackages[period]
            if (categorizedPackagesOfAPeriod == null) {
                unUsedAppsInAPeriod.clear()
                continue
            }
            val categorizedPackageKeys =
                categorizedPackagesOfAPeriod.map { createKey(it.packageName, it.user) }
            // Do not remove apps that are still in the unused category
            val keysToRemove = unUsedAppsInAPeriod.keys.filterNot { it in categorizedPackageKeys }
            for (key in keysToRemove) {
                unUsedAppsInAPeriod.remove(key)
            }
        }

        var allCategoriesEmpty = true
        for ((period, packages) in categorizedPackages) {
            categoryVisibilities.set(periodToIndex(period), packages.isNotEmpty())
            if (packages.isNotEmpty()) {
                allCategoriesEmpty = false
            }

            for ((pkgName, user, _, permSet) in packages) {
                val revokedPerms = permSet.toList()
                val key = createKey(pkgName, user)

                if (!unusedAppsMap[period]!!.containsKey(key)) {
                    val mostImportant = getMostImportantGroup(revokedPerms)
                    val importantLabel = KotlinUtils.getPermGroupLabel(context, mostImportant)
                    val summary =
                        when {
                            revokedPerms.isEmpty() -> null
                            revokedPerms.size == 1 ->
                                getString(R.string.auto_revoked_app_summary_one, importantLabel)
                            revokedPerms.size == 2 -> {
                                val otherLabel =
                                    if (revokedPerms[0] == mostImportant) {
                                        KotlinUtils.getPermGroupLabel(context, revokedPerms[1])
                                    } else {
                                        KotlinUtils.getPermGroupLabel(context, revokedPerms[0])
                                    }
                                getString(
                                    R.string.auto_revoked_app_summary_two,
                                    importantLabel,
                                    otherLabel
                                )
                            }
                            else ->
                                getString(
                                    R.string.auto_revoked_app_summary_many,
                                    importantLabel,
                                    "${revokedPerms.size - 1}"
                                )
                        }

                    val onChipClicked: () -> Unit = {
                        run { viewModel.navigateToAppInfo(pkgName, user, sessionId) }
                    }

                    val chip =
                        UnusedAppChip(
                            KotlinUtils.getPackageLabel(activity.application, pkgName, user),
                            summary,
                            KotlinUtils.getBadgedPackageIcon(activity.application, pkgName, user),
                            AppUtils.getAppContentDescription(
                                context,
                                pkgName,
                                user.getIdentifier()
                            ),
                            onChipClicked
                        )
                    unusedAppsMap[period]!!.put(key, chip)
                }
            }

            // Sort the chips
            unusedAppsMap[period] =
                unusedAppsMap[period]!!
                    .toList()
                    .sortedWith(Comparator { lhs, rhs -> compareUnusedApps(lhs, rhs) })
                    .toMap()
                    .toMutableMap()
        }

        wearViewModel.infoMsgCategoryVisibilityLiveData.value = !allCategoriesEmpty

        if (isFirstLoad) {
            if (categorizedPackages.any { (_, packages) -> packages.isNotEmpty() }) {
                isFirstLoad = false
            }
            Log.i(LOG_TAG, "sessionId: $sessionId Showed Auto Revoke Page")
            for (period in allPeriods) {
                Log.i(
                    LOG_TAG,
                    "sessionId: $sessionId $period unused: " + "${categorizedPackages[period]}"
                )
                for (revokedPackageInfo in categorizedPackages[period]!!) {
                    for (groupName in revokedPackageInfo.revokedGroups) {
                        val isNewlyRevoked = period.isNewlyUnused()
                        viewModel.logAppView(
                            revokedPackageInfo.packageName,
                            revokedPackageInfo.user,
                            groupName,
                            isNewlyRevoked
                        )
                    }
                }
            }
        }
    }

    private fun createKey(packageName: String, user: UserHandle): String {
        return "$packageName:${user.identifier}"
    }

    private fun periodToIndex(period: UnusedPeriod): Int {
        when (period) {
            UnusedPeriod.ONE_MONTH -> return 0
            UnusedPeriod.THREE_MONTHS -> return 1
            UnusedPeriod.SIX_MONTHS -> return 2
        }
    }

    private fun getMostImportantGroup(groupNames: List<String>): String {
        return when {
            groupNames.contains(Manifest.permission_group.LOCATION) ->
                Manifest.permission_group.LOCATION
            groupNames.contains(Manifest.permission_group.MICROPHONE) ->
                Manifest.permission_group.MICROPHONE
            groupNames.contains(Manifest.permission_group.CAMERA) ->
                Manifest.permission_group.CAMERA
            groupNames.contains(Manifest.permission_group.CONTACTS) ->
                Manifest.permission_group.CONTACTS
            groupNames.contains(Manifest.permission_group.STORAGE) ->
                Manifest.permission_group.STORAGE
            groupNames.contains(Manifest.permission_group.CALENDAR) ->
                Manifest.permission_group.CALENDAR
            groupNames.isNotEmpty() -> groupNames[0]
            else -> ""
        }
    }

    private fun compareUnusedApps(
        lhs: Pair<String, UnusedAppChip>,
        rhs: Pair<String, UnusedAppChip>
    ): Int {
        var result = collator.compare(lhs.second.label, rhs.second.label)
        if (result == 0) {
            result = collator.compare(lhs.first, rhs.first)
        }
        return result
    }

    private fun updateWearViewModel(isLoading: Boolean) {
        wearViewModel.loadingLiveData.value = isLoading
        wearViewModel.unusedPeriodCategoryVisibilitiesLiveData.setValue(categoryVisibilities)

        // Need to copy to non mutable maps or compose will not update correctly
        val map = mutableMapOf<UnusedPeriod, Map<String, UnusedAppChip>>()
        for (period in allPeriods) {
            map.put(period, unusedAppsMap[period]!!.toMap())
        }

        wearViewModel.unusedAppChipsLiveData.setValue(map.toMap())
    }
}
