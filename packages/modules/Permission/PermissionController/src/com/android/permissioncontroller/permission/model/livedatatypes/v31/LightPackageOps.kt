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

package com.android.permissioncontroller.permission.model.livedatatypes.v31

import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.AppOpsManager.PackageOps
import android.os.UserHandle
import com.android.permissioncontroller.permission.utils.PermissionMapping.getPlatformPermissionGroupForOp

/**
 * Light version of [PackageOps] class, tracking the last permission access for system permission
 * groups.
 */
data class LightPackageOps(
    /** Name of the package. */
    val packageName: String,
    /** [UserHandle] running the package. */
    val userHandle: UserHandle,
    /**
     * Mapping of permission group name to the last access time of any op backing a permission in
     * the group.
     */
    val lastPermissionGroupAccessTimesMs: Map<String, Long>
) {
    constructor(
        ops: Set<String>,
        packageOps: PackageOps
    ) : this(
        packageOps.packageName,
        UserHandle.getUserHandleForUid(packageOps.uid),
        createLastPermissionGroupAccessTimesMap(ops, packageOps)
    )

    /** Companion object for [LightPackageOps]. */
    companion object {
        /** Flags to use for querying an op's last access time. */
        private const val OPS_LAST_ACCESS_FLAGS =
            OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED or OP_FLAG_TRUSTED_PROXY

        /** Creates a mapping from permission group to the last time it was accessed. */
        private fun createLastPermissionGroupAccessTimesMap(
            opNames: Set<String>,
            packageOps: PackageOps
        ): Map<String, Long> {
            val lastAccessTimeMs = mutableMapOf<String, Long>()
            // Add keys for all permissions groups covered by the provided ops, regardless of
            // whether they have been observed recently.
            for (permissionGroup in
                opNames.mapNotNull { getPlatformPermissionGroupForOp(it) }.toSet()) {
                lastAccessTimeMs[permissionGroup] = -1
            }

            for (opEntry in packageOps.ops) {
                val permissionGroupOfOp = getPlatformPermissionGroupForOp(opEntry.opStr) ?: continue
                lastAccessTimeMs[permissionGroupOfOp] =
                    maxOf(
                        lastAccessTimeMs[permissionGroupOfOp] ?: -1,
                        opEntry.getLastAccessTime(OPS_LAST_ACCESS_FLAGS)
                    )
            }

            return lastAccessTimeMs
        }
    }
}
