/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.model.livedatatypes

import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_ELIGIBLE
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_EXEMPT_BY_SYSTEM

/**
 * Tracks the setting state of hibernation and auto revoke for a package
 *
 * @param hibernationEligibility state saying whether the package is eligible for hibernation. See
 *   [HIBERNATION_ELIGIBILITY_ELIGIBLE].
 * @param revocableGroupNames A list of which permission groups of this package are eligible for
 *   auto-revoke. A permission group is auto-revocable if it does not contain a default granted
 *   permission.
 */
data class HibernationSettingState(
    val hibernationEligibility: Int,
    val revocableGroupNames: List<String>
) {
    /** Whether package will hibernate if it is unused. */
    fun isEligibleForHibernation(): Boolean {
        return hibernationEligibility == HIBERNATION_ELIGIBILITY_ELIGIBLE
    }

    /**
     * Whether the package is exempt from hibernation by the system. This means the app can never be
     * hibernated, and the user setting to exempt it is disabled.
     */
    fun isExemptBySystem(): Boolean {
        return hibernationEligibility == HIBERNATION_ELIGIBILITY_EXEMPT_BY_SYSTEM
    }
}
