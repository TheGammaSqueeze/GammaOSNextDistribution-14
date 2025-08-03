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

package com.android.permissioncontroller.permission.service

import android.permission.PermissionManager
import com.android.permissioncontroller.permission.utils.PermissionMapping

/**
 * Takes a list of split permissions, and provides methods that return which split-permissions will
 * be active given an app's targetSdk.
 */
class SplitPermissionIndex() {
    private lateinit var permToGroupSplits: Set<SplitPermissionIndexEntry>
    private lateinit var groupToGroupSplits: Set<SplitPermissionIndexEntry>

    constructor(groupToGroupSplits: Set<SplitPermissionIndexEntry>) : this() {
        this.groupToGroupSplits = groupToGroupSplits
    }

    constructor(splitPermissionInfos: List<PermissionManager.SplitPermissionInfo>) : this() {
        val permToGroupSplits: MutableSet<SplitPermissionIndexEntry> = mutableSetOf()
        val groupToGroupSplits: MutableSet<SplitPermissionIndexEntry> = mutableSetOf()
        for (splitPerm in splitPermissionInfos) {
            val oldPerm = splitPerm.splitPermission
            for (newPerm in splitPerm.newPermissions) {
                val oldPermGroup = PermissionMapping.getGroupOfPlatformPermission(oldPerm)
                val newPermGroup = PermissionMapping.getGroupOfPlatformPermission(newPerm)
                if (newPermGroup != null) {
                    permToGroupSplits.add(
                        SplitPermissionIndexEntry(oldPerm, splitPerm.targetSdk, newPermGroup)
                    )
                }
                if (oldPermGroup != null && newPermGroup != null) {
                    groupToGroupSplits.add(
                        SplitPermissionIndexEntry(oldPermGroup, splitPerm.targetSdk, newPermGroup)
                    )
                }
            }
        }
        this.permToGroupSplits = permToGroupSplits
        this.groupToGroupSplits = groupToGroupSplits
    }

    /** Given a permission, return which groups split *from* it for the given targetSdk. */
    fun getPermToGroupSplitsFrom(oldPermission: String, targetSdk: Int): List<String> {
        return permToGroupSplits
            .filter { it.oldPerm == oldPermission && it.targetSdk < targetSdk }
            .map { it.newPerm }
            .toList()
    }

    /** Given a permission group, return which groups split *from* it for the given targetSdk. */
    fun getGroupToGroupSplitsFrom(oldPermissionGroup: String, targetSdk: Int): List<String> {
        return groupToGroupSplits
            .filter { it.oldPerm == oldPermissionGroup && it.targetSdk < targetSdk }
            .map { it.newPerm }
            .toList()
    }

    /** Given a permission group, return which permissions split *to* it for the given targetSdk. */
    fun getGroupToGroupSplitsTo(newPermissionGroup: String, targetSdk: Int): List<String> {
        return groupToGroupSplits
            .filter { it.newPerm == newPermissionGroup && it.targetSdk < targetSdk }
            .map { it.oldPerm }
            .toList()
    }

    data class SplitPermissionIndexEntry(
        val oldPerm: String,
        val targetSdk: Int,
        val newPerm: String
    )
}
