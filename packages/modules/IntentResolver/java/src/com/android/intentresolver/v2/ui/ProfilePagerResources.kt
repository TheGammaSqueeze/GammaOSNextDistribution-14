/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.ui

import android.content.res.Resources
import com.android.intentresolver.inject.ApplicationOwned
import com.android.intentresolver.v2.data.repository.DevicePolicyResources
import com.android.intentresolver.v2.shared.model.Profile
import javax.inject.Inject
import com.android.intentresolver.R

class ProfilePagerResources
@Inject
constructor(
    @ApplicationOwned private val resources: Resources,
    private val devicePolicyResources: DevicePolicyResources
) {
    private val privateTabLabel by lazy { resources.getString(R.string.resolver_private_tab) }

    private val privateTabAccessibilityLabel by lazy {
        resources.getString(R.string.resolver_private_tab_accessibility)
    }

    fun profileTabLabel(profile: Profile.Type): String {
        return when (profile) {
            Profile.Type.PERSONAL -> devicePolicyResources.personalTabLabel
            Profile.Type.WORK -> devicePolicyResources.workTabLabel
            Profile.Type.PRIVATE -> privateTabLabel
        }
    }

    fun profileTabAccessibilityLabel(type: Profile.Type): String {
        return when (type) {
            Profile.Type.PERSONAL -> devicePolicyResources.personalTabAccessibilityLabel
            Profile.Type.WORK -> devicePolicyResources.workTabAccessibilityLabel
            Profile.Type.PRIVATE -> privateTabAccessibilityLabel
        }
    }
}