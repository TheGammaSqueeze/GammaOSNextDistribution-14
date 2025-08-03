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

package com.android.ondevicepersonalization.services.policyengine.policy

import com.android.libraries.pcc.chronicle.api.policy.StorageMedium
import com.android.libraries.pcc.chronicle.api.policy.UsageType
import com.android.libraries.pcc.chronicle.api.policy.builder.policy
import com.android.ondevicepersonalization.services.policyengine.data.USER_DATA_GENERATED_DTD
import com.android.ondevicepersonalization.services.policyengine.policy.rules.UserOptsInLimitedAdsTracking
import com.android.ondevicepersonalization.services.policyengine.policy.rules.UnicornAccount
import com.android.libraries.pcc.chronicle.api.policy.contextrules.and
import com.android.libraries.pcc.chronicle.api.policy.contextrules.not

import java.time.Duration

/** This module encapsulates all data ingress policies for ODA. */
class DataIngressPolicy {
    companion object {
        // NPA (No Personalized Ads) policy for user and vendor data in ODA
        @JvmField
        val NPA_DATA_POLICY = policy(
            name = "npaPolicy",
            egressType = "None",
        ) {
            description =
            """
                Policy that grant on-device data to ad vendors if no NPA flag is set.
                """
                .trimIndent()
            target(USER_DATA_GENERATED_DTD, Duration.ofDays(30)) {
                retention(medium = StorageMedium.RAM, encryptionRequired = false)
                "timestampSec" {rawUsage(UsageType.ANY)}
                "timezoneUtcOffsetMins" {rawUsage(UsageType.ANY)}
                "orientation" {rawUsage(UsageType.ANY)}
                "availableStorageMB" {rawUsage(UsageType.ANY)}
                "batteryPercentage" {rawUsage(UsageType.ANY)}
                "carrier" {rawUsage(UsageType.ANY)}
                "dataNetworkType" {rawUsage(UsageType.ANY)}
                "appInfos" {
                    "packageName" {rawUsage(UsageType.ANY)}
                    "installed" {rawUsage(UsageType.ANY)}
                }
            }

            allowedContext = not(UnicornAccount) and not(UserOptsInLimitedAdsTracking)
        }
    }
}
