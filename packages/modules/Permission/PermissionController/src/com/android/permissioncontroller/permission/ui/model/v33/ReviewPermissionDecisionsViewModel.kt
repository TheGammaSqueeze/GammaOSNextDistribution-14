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

package com.android.permissioncontroller.permission.ui.model.v33

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.icu.lang.UCharacter
import android.os.Build
import android.os.UserHandle
import android.text.BidiFormatter
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.permission.data.v33.PermissionDecision
import com.android.permissioncontroller.permission.data.v33.RecentPermissionDecisionsLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.auto.AutoReviewPermissionDecisionsFragment
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.StringUtils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job

/** Viewmodel for [ReviewPermissionDecisionsFragment] */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ReviewPermissionDecisionsViewModel(val app: Application, val user: UserHandle) : ViewModel() {

    val LOG_TAG = "ReviewPermissionDecisionsViewModel"

    private val recentPermissionsLiveData = RecentPermissionDecisionsLiveData()
    private val userPackageInfosLiveData = UserPackageInfosLiveData[user]

    val recentPermissionDecisionsLiveData =
        object :
            SmartAsyncMediatorLiveData<List<PermissionDecision>>(alwaysUpdateOnActive = false) {

            init {
                addSource(recentPermissionsLiveData) { onUpdate() }

                addSource(userPackageInfosLiveData) { onUpdate() }
            }

            override suspend fun loadDataAndPostValue(job: Job) {
                if (
                    !recentPermissionsLiveData.isInitialized ||
                        !userPackageInfosLiveData.isInitialized
                ) {
                    return
                }

                // create package info lookup map for performance
                val packageToLightPackageInfo: MutableMap<String, LightPackageInfo> = mutableMapOf()
                for (lightPackageInfo in userPackageInfosLiveData.value!!) {
                    packageToLightPackageInfo[lightPackageInfo.packageName] = lightPackageInfo
                }

                // verify that permission state is still correct. Will also filter out any apps that
                // were uninstalled
                val decisionsToReview: MutableList<PermissionDecision> = mutableListOf()
                for (recentDecision in recentPermissionsLiveData.value!!) {
                    val lightPackageInfo = packageToLightPackageInfo[recentDecision.packageName]
                    if (lightPackageInfo == null) {
                        DumpableLog.e(
                            LOG_TAG,
                            "Package $recentDecision.packageName " + "is no longer installed"
                        )
                        continue
                    }
                    val grantedGroups: List<String?> =
                        lightPackageInfo.grantedPermissions.map {
                            PermissionMapping.getGroupOfPermission(
                                app.packageManager.getPermissionInfo(it, /* flags= */ 0)
                            )
                        }
                    val currentlyGranted =
                        grantedGroups.contains(recentDecision.permissionGroupName)
                    if (currentlyGranted && recentDecision.isGranted) {
                        decisionsToReview.add(recentDecision)
                    } else if (!currentlyGranted && !recentDecision.isGranted) {
                        decisionsToReview.add(recentDecision)
                    } else {
                        // It's okay for this to happen - the state could change due to role
                        // changes,
                        // app hibernation, or other non-user-driven actions.
                        DumpableLog.d(
                            LOG_TAG,
                            "Permission decision grant state (${recentDecision.isGranted}) " +
                                "for ${recentDecision.packageName} access to " +
                                "${recentDecision.permissionGroupName} does not match current " +
                                "grant state $currentlyGranted"
                        )
                    }
                }

                postValue(decisionsToReview)
            }
        }

    fun getAppIcon(packageName: String): Drawable? {
        return KotlinUtils.getBadgedPackageIcon(app, packageName, user)
    }

    fun createPreferenceTitle(permissionDecision: PermissionDecision): String {
        val packageLabel =
            BidiFormatter.getInstance()
                .unicodeWrap(KotlinUtils.getPackageLabel(app, permissionDecision.packageName, user))
        val permissionGroupLabel =
            KotlinUtils.getPermGroupLabel(app, permissionDecision.permissionGroupName).toString()
        return if (permissionDecision.isGranted) {
            app.getString(
                R.string.granted_permission_decision,
                packageLabel,
                UCharacter.toLowerCase(permissionGroupLabel)
            )
        } else {
            app.getString(
                R.string.denied_permission_decision,
                packageLabel,
                UCharacter.toLowerCase(permissionGroupLabel)
            )
        }
    }

    fun createManageAppPermissionIntent(permissionDecision: PermissionDecision): Intent {
        return Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, permissionDecision.packageName)
            putExtra(Intent.EXTRA_PERMISSION_NAME, permissionDecision.permissionGroupName)
            putExtra(Intent.EXTRA_USER, user)
            putExtra(
                ManagePermissionsActivity.EXTRA_CALLER_NAME,
                AutoReviewPermissionDecisionsFragment::class.java.name
            )
        }
    }

    fun createSummaryText(permissionDecision: PermissionDecision): String {
        val diff = System.currentTimeMillis() - permissionDecision.eventTime
        val daysAgo = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS).toInt()
        return StringUtils.getIcuPluralsString(app, R.string.days_ago, daysAgo)
    }
}

/** Factory for a [ReviewPermissionDecisionsViewModel] */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ReviewPermissionDecisionsViewModelFactory(val app: Application, val user: UserHandle) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return ReviewPermissionDecisionsViewModel(app, user) as T
    }
}
