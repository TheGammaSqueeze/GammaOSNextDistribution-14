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

import android.app.AppOpsManager.AttributedHistoricalOps
import android.app.AppOpsManager.AttributedOpEntry
import android.app.AppOpsManager.HistoricalOp
import android.app.AppOpsManager.HistoricalPackageOps
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.AppOpsManager.OpEventProxyInfo
import android.os.UserHandle
import com.android.permissioncontroller.permission.utils.PermissionMapping.getPlatformPermissionGroupForOp

/**
 * Light version of [HistoricalPackageOps] class, tracking the last permission access for system
 * permission groups.
 */
data class LightHistoricalPackageOps(
    /** Name of the package. */
    val packageName: String,
    /** [UserHandle] running the package. */
    val userHandle: UserHandle,
    /**
     * Data about permission accesses, one [AppPermissionDiscreteAccesses] for each permission
     * group.
     */
    // TODO(b/262042582): Consider removing this field and using attributed accesses aggregated over
    // attribution tags instead.
    val appPermissionDiscreteAccesses: List<AppPermissionDiscreteAccesses>,
    /**
     * Attributed data about permission accesses, one [AttributedAppPermissionDiscreteAccesses] for
     * each permission group.
     */
    val attributedAppPermissionDiscreteAccesses: List<AttributedAppPermissionDiscreteAccesses>
) {
    constructor(
        historicalPackageOps: HistoricalPackageOps,
        userHandle: UserHandle,
        opNames: Set<String>
    ) : this(
        historicalPackageOps.packageName,
        userHandle,
        historicalPackageOps.getAppPermissionDiscreteAccesses(userHandle, opNames),
        historicalPackageOps.getAttributedAppPermissionDiscreteAccesses(userHandle, opNames),
    )

    /** Companion object for [LightHistoricalPackageOps]. */
    companion object {
        /** String to represent the absence of an attribution tag. */
        const val NO_ATTRIBUTION_TAG = "no_attribution_tag"
        /** String to represent the absence of a permission group. */
        private const val NO_PERM_GROUP = "no_perm_group"
        private const val DISCRETE_ACCESS_OP_FLAGS =
            OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED or OP_FLAG_TRUSTED_PROXY

        /**
         * Creates a list of [AppPermissionDiscreteAccesses] for the provided package, user and ops.
         */
        private fun HistoricalPackageOps.getAppPermissionDiscreteAccesses(
            userHandle: UserHandle,
            opNames: Set<String>
        ): List<AppPermissionDiscreteAccesses> {
            val permissionsToOpNames = partitionOpsByPermission(opNames)
            val appPermissionDiscreteAccesses = mutableListOf<AppPermissionDiscreteAccesses>()
            for (permissionToOpNames in permissionsToOpNames.entries) {
                this.getDiscreteAccesses(permissionToOpNames.value)?.let {
                    appPermissionDiscreteAccesses.add(
                        AppPermissionDiscreteAccesses(
                            AppPermissionId(packageName, userHandle, permissionToOpNames.key),
                            it
                        )
                    )
                }
            }

            return appPermissionDiscreteAccesses
        }

        /**
         * Creates a list of [AttributedAppPermissionDiscreteAccesses] for the provided package,
         * user and ops.
         */
        private fun HistoricalPackageOps.getAttributedAppPermissionDiscreteAccesses(
            userHandle: UserHandle,
            opNames: Set<String>
        ): List<AttributedAppPermissionDiscreteAccesses> {
            val permissionsToOpNames = partitionOpsByPermission(opNames)
            val attributedAppPermissionDiscreteAccesses =
                mutableMapOf<AppPermissionId, MutableMap<String, List<DiscreteAccess>>>()

            val attributedHistoricalOpsList = mutableListOf<AttributedHistoricalOps>()
            for (i in 0 until attributedOpsCount) {
                attributedHistoricalOpsList.add(getAttributedOpsAt(i))
            }

            for (permissionToOpNames in permissionsToOpNames.entries) {
                attributedHistoricalOpsList.forEach { attributedHistoricalOps ->
                    attributedHistoricalOps.getDiscreteAccesses(permissionToOpNames.value)?.let {
                        discAccessData ->
                        val appPermissionId =
                            AppPermissionId(packageName, userHandle, permissionToOpNames.key)
                        if (!attributedAppPermissionDiscreteAccesses.containsKey(appPermissionId)) {
                            attributedAppPermissionDiscreteAccesses[appPermissionId] =
                                mutableMapOf()
                        }
                        attributedAppPermissionDiscreteAccesses[appPermissionId]?.put(
                            attributedHistoricalOps.tag ?: NO_ATTRIBUTION_TAG,
                            discAccessData
                        )
                    }
                }
            }

            return attributedAppPermissionDiscreteAccesses.map {
                AttributedAppPermissionDiscreteAccesses(it.key, it.value)
            }
        }

        /**
         * Retrieves all discrete accesses for the provided op names, if any.
         *
         * Returns null if there are no accesses.
         */
        private fun HistoricalPackageOps.getDiscreteAccesses(
            opNames: List<String>
        ): List<DiscreteAccess>? {
            if (opCount == 0) {
                return null
            }

            val historicalOps = mutableListOf<HistoricalOp>()
            for (opName in opNames) {
                getOp(opName)?.let { historicalOps.add(it) }
            }

            val discreteAccessList = mutableListOf<DiscreteAccess>()
            historicalOps.forEach {
                for (i in 0 until it.discreteAccessCount) {
                    val opEntry: AttributedOpEntry = it.getDiscreteAccessAt(i)
                    discreteAccessList.add(
                        DiscreteAccess(
                            opEntry.getLastAccessTime(DISCRETE_ACCESS_OP_FLAGS),
                            opEntry.getLastDuration(DISCRETE_ACCESS_OP_FLAGS),
                            opEntry.getLastProxyInfo(DISCRETE_ACCESS_OP_FLAGS)
                        )
                    )
                }
            }

            if (discreteAccessList.isEmpty()) {
                return null
            }
            return discreteAccessList.sortedWith(compareBy { -it.accessTimeMs })
        }

        /**
         * Retrieves all discrete accesses for the provided op names, if any.
         *
         * Returns null if there are no accesses.
         */
        private fun AttributedHistoricalOps.getDiscreteAccesses(
            opNames: List<String>
        ): List<DiscreteAccess>? {
            if (opCount == 0) {
                return null
            }

            val historicalOps = mutableListOf<HistoricalOp>()
            for (opName in opNames) {
                getOp(opName)?.let { historicalOps.add(it) }
            }

            val discreteAccessList = mutableListOf<DiscreteAccess>()
            historicalOps.forEach {
                for (i in 0 until it.discreteAccessCount) {
                    val attributedOpEntry: AttributedOpEntry = it.getDiscreteAccessAt(i)
                    discreteAccessList.add(
                        DiscreteAccess(
                            attributedOpEntry.getLastAccessTime(DISCRETE_ACCESS_OP_FLAGS),
                            attributedOpEntry.getLastDuration(DISCRETE_ACCESS_OP_FLAGS),
                            attributedOpEntry.getLastProxyInfo(DISCRETE_ACCESS_OP_FLAGS)
                        )
                    )
                }
            }

            if (discreteAccessList.isEmpty()) {
                return null
            }
            return discreteAccessList.sortedWith(compareBy { -it.accessTimeMs })
        }

        private fun partitionOpsByPermission(ops: Set<String>): Map<String, List<String>> =
            ops.groupBy { getPlatformPermissionGroupForOp(it) ?: NO_PERM_GROUP }
                .filter { it.key != NO_PERM_GROUP }
    }

    /**
     * Data class representing permissions accesses for a particular permission group by a
     * particular package and user.
     */
    data class AppPermissionDiscreteAccesses(
        val appPermissionId: AppPermissionId,
        val discreteAccesses: List<DiscreteAccess>
    )

    /**
     * Data class representing permissions accesses for a particular permission group by a
     * particular package and user, partitioned by attribution tag.
     */
    data class AttributedAppPermissionDiscreteAccesses(
        val appPermissionId: AppPermissionId,
        val attributedDiscreteAccesses: Map<String, List<DiscreteAccess>>
    )

    /** Data class representing a discrete permission access. */
    data class DiscreteAccess(
        val accessTimeMs: Long,
        val accessDurationMs: Long,
        val proxy: OpEventProxyInfo?
    )
}
