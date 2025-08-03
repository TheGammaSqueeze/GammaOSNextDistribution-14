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
package com.android.intentresolver.v2.data.repository

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_PERSONAL
import android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_WORK
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB_ACCESSIBILITY
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_PROFILE_NOT_SUPPORTED
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB_ACCESSIBILITY
import android.content.res.Resources
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicePolicyResources
@Inject
constructor(
    @ApplicationOwned private val resources: Resources,
    devicePolicyManager: DevicePolicyManager
) {
    private val policyResources = devicePolicyManager.resources

    val personalTabLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_PERSONAL_TAB) {
                resources.getString(R.string.resolver_personal_tab)
            }
        )
    }

    val workTabLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_WORK_TAB) {
                resources.getString(R.string.resolver_work_tab)
            }
        )
    }

    val personalTabAccessibilityLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_PERSONAL_TAB_ACCESSIBILITY) {
                resources.getString(R.string.resolver_personal_tab_accessibility)
            }
        )
    }

    val workTabAccessibilityLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_WORK_TAB_ACCESSIBILITY) {
                resources.getString(R.string.resolver_work_tab_accessibility)
            }
        )
    }

    val forwardToPersonalMessage: String? =
        devicePolicyManager.resources.getString(FORWARD_INTENT_TO_PERSONAL) {
            resources.getString(R.string.forward_intent_to_owner)
        }

    val forwardToWorkMessage by lazy {
        requireNotNull(
            policyResources.getString(FORWARD_INTENT_TO_WORK) {
                resources.getString(R.string.forward_intent_to_work)
            }
        )
    }

    fun getWorkProfileNotSupportedMessage(launcherName: String): String {
        return requireNotNull(
            policyResources.getString(
                RESOLVER_WORK_PROFILE_NOT_SUPPORTED,
                {
                    resources.getString(
                        R.string.activity_resolver_work_profiles_support,
                        launcherName
                    )
                },
                launcherName
            )
        )
    }
}
