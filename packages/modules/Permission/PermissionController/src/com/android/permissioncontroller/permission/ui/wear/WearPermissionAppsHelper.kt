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

import android.app.Application
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.permission.flags.Flags
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel
import com.android.permissioncontroller.permission.ui.wear.model.WearAppPermissionUsagesViewModel
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupDescription
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.settingslib.utils.applications.AppUtils
import java.text.Collator
import java.util.Random

/** Helper class for WearPermissionsAppScreen. */
class WearPermissionAppsHelper(
    val application: Application,
    val permGroupName: String,
    val viewModel: PermissionAppsViewModel,
    val wearViewModel: WearAppPermissionUsagesViewModel,
    private val isStorageAndLessThanT: Boolean,
    private val onAppClick: (String, UserHandle, String) -> Unit,
    val onShowSystemClick: (Boolean) -> Unit,
    val logFragmentCreated: (String, UserHandle, Long, Boolean, Boolean, Boolean) -> Unit
) {
    fun categorizedAppsLiveData() = viewModel.categorizedAppsLiveData
    fun hasSystemAppsLiveData() = viewModel.hasSystemAppsLiveData
    fun shouldShowSystemLiveData() = viewModel.shouldShowSystemLiveData
    fun showAlways() = viewModel.showAllowAlwaysStringLiveData.value ?: false
    fun getTitle() = getPermGroupLabel(application, permGroupName).toString()
    fun getSubTitle() = getPermGroupDescription(application, permGroupName).toString()
    fun getChipsByCategory(
        categorizedApps: Map<Category, List<Pair<String, UserHandle>>>,
        appPermissionUsages: List<AppPermissionUsage>
    ): Map<String, List<ChipInfo>> {
        val chipsByCategory: MutableMap<String, MutableList<ChipInfo>> = HashMap()

        // A mapping of user + packageName to their last access timestamps for the permission group.
        val groupUsageLastAccessTime: Map<String, Long> =
            viewModel.extractGroupUsageLastAccessTime(appPermissionUsages)

        val context = application
        val collator = Collator.getInstance(context.resources.configuration.locales.get(0))
        val comparator = ChipComparator(collator)

        val viewIdForLogging = Random().nextLong()
        for (category in Category.values()) {
            if (category == Category.ALLOWED && isStorageAndLessThanT) {
                val allowedList = categorizedApps[Category.ALLOWED]
                if (!allowedList.isNullOrEmpty()) {
                    allowedList
                        .partition { p -> viewModel.packageHasFullStorage(p.first, p.second) }
                        .let {
                            if (it.first.isNotEmpty()) {
                                chipsByCategory[STORAGE_ALLOWED_FULL] =
                                    convertToChips(
                                        category,
                                        it.first,
                                        viewIdForLogging,
                                        comparator,
                                        groupUsageLastAccessTime
                                    )
                            }
                            if (it.second.isNotEmpty()) {
                                chipsByCategory[STORAGE_ALLOWED_SCOPED] =
                                    convertToChips(
                                        category,
                                        it.second,
                                        viewIdForLogging,
                                        comparator,
                                        groupUsageLastAccessTime
                                    )
                            }
                        }
                }
                continue
            }
            val list = categorizedApps[category]
            if (!list.isNullOrEmpty()) {
                chipsByCategory[category.categoryName] =
                    convertToChips(
                        category,
                        list,
                        viewIdForLogging,
                        comparator,
                        groupUsageLastAccessTime
                    )
            }
        }

        // Add no_apps chips to allowed and denied if it doesn't have an app.
        addNoAppsIfNeeded(chipsByCategory)
        return chipsByCategory
    }

    private fun convertToChips(
        category: Category,
        list: List<Pair<String, UserHandle>>,
        viewIdForLogging: Long,
        comparator: Comparator<ChipInfo>,
        groupUsageLastAccessTime: Map<String, Long>
    ) =
        list
            .map { p ->
                val lastAccessTime = groupUsageLastAccessTime[(p.second.toString() + p.first)]
                createAppChipInfo(
                    application,
                    p.first,
                    p.second,
                    category,
                    onAppClick,
                    viewIdForLogging,
                    lastAccessTime
                )
            }
            .sortedWith(comparator)
            .toMutableList()

    fun setCreationLogged(isLogged: Boolean) {
        viewModel.creationLogged = isLogged
    }

    private fun createAppChipInfo(
        application: Application,
        packageName: String,
        user: UserHandle,
        category: Category,
        onClick: (packageName: String, user: UserHandle, category: String) -> Unit,
        viewIdForLogging: Long,
        lastAccessTime: Long?
    ): ChipInfo {
        if (!viewModel.creationLogged) {
            logFragmentCreated(
                packageName,
                user,
                viewIdForLogging,
                category == Category.ALLOWED,
                category == Category.ALLOWED_FOREGROUND,
                category == Category.DENIED
            )
        }
        val summary =
            if (Flags.wearPrivacyDashboardEnabledReadOnly()) {
                lastAccessTime?.let { WearUtils.getPreferenceSummary(application, lastAccessTime) }
            } else {
                null
            }
        return ChipInfo(
            title = KotlinUtils.getPackageLabel(application, packageName, user),
            summary = summary,
            contentDescription =
                AppUtils.getAppContentDescription(application, packageName, user.getIdentifier()),
            icon = KotlinUtils.getBadgedPackageIcon(application, packageName, user),
            onClick = { onClick(packageName, user, category.categoryName) }
        )
    }

    private fun addNoAppsIfNeeded(chipsByCategory: MutableMap<String, MutableList<ChipInfo>>) {
        addNoAppsToAllowed(chipsByCategory)
        addNoAppsToDenied(chipsByCategory)
    }

    private fun addNoAppsToAllowed(chipsByCategory: MutableMap<String, MutableList<ChipInfo>>) {
        if (isStorageAndLessThanT) {
            // For the storage permission,
            // allowed category is split into allowed_full and allowed_scoped categories,
            // add no_apps chip to the categories.
            addNoAppsTo(chipsByCategory, STORAGE_ALLOWED_FULL, R.string.no_apps_allowed_full)
            addNoAppsTo(chipsByCategory, STORAGE_ALLOWED_SCOPED, R.string.no_apps_allowed_scoped)
            return
        }
        addNoAppsTo(chipsByCategory, Category.ALLOWED.categoryName, R.string.no_apps_allowed)
    }

    private fun addNoAppsToDenied(chipsByCategory: MutableMap<String, MutableList<ChipInfo>>) {
        addNoAppsTo(chipsByCategory, Category.DENIED.categoryName, R.string.no_apps_denied)
    }

    private fun addNoAppsTo(
        chipsByCategory: MutableMap<String, MutableList<ChipInfo>>,
        categoryName: String,
        titleResId: Int
    ) {
        if (chipsByCategory[categoryName].isNullOrEmpty()) {
            chipsByCategory[categoryName] =
                mutableListOf(
                    ChipInfo(title = application.resources.getString(titleResId), enabled = false)
                )
        }
    }

    companion object {
        private const val STORAGE_ALLOWED_FULL = "allowed_storage_full"
        private const val STORAGE_ALLOWED_SCOPED = "allowed_storage_scoped"
    }
}

class ChipInfo(
    val title: String,
    val summary: String? = null,
    val contentDescription: String? = null,
    val onClick: () -> Unit = {},
    val icon: Drawable? = null,
    val enabled: Boolean = true
)

internal class ChipComparator(val collator: Collator) : Comparator<ChipInfo> {
    override fun compare(lhs: ChipInfo, rhs: ChipInfo): Int {
        var result = collator.compare(lhs.title, rhs.title)
        if (result == 0) {
            result = lhs.title.compareTo(rhs.title)
        }
        return result
    }
}
