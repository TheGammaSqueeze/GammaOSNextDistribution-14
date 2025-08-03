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

package com.android.safetycenter;

import static java.util.Objects.requireNonNull;

import android.annotation.StringRes;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;
import android.os.Binder;

import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.util.function.Supplier;

/** A class that handles dynamically updating enterprise-related resources. */
final class DevicePolicyResources {

    private static final String SAFETY_CENTER_PREFIX = "SafetyCenter.";
    private static final String WORK_PROFILE_PAUSED_TITLE = "WORK_PROFILE_PAUSED";

    private DevicePolicyResources() {}

    /**
     * Returns the updated string for the given {@code safetySourceId} by calling {@link
     * DevicePolicyResourcesManager#getString}.
     */
    static String getSafetySourceWorkString(
            SafetyCenterResourcesApk safetyCenterResourcesApk,
            String safetySourceId,
            @StringRes int workResId) {
        return getEnterpriseString(
                safetyCenterResourcesApk.getContext(),
                safetySourceId,
                () -> safetyCenterResourcesApk.getString(workResId));
    }

    /**
     * Returns the updated string for the {@code work_profile_paused} string by calling {@link
     * DevicePolicyResourcesManager#getString}.
     */
    static String getWorkProfilePausedString(SafetyCenterResourcesApk safetyCenterResourcesApk) {
        return getEnterpriseString(
                safetyCenterResourcesApk.getContext(),
                WORK_PROFILE_PAUSED_TITLE,
                () -> safetyCenterResourcesApk.getStringByName("work_profile_paused"));
    }

    private static String getEnterpriseString(
            Context context, String devicePolicyIdentifier, Supplier<String> defaultValueLoader) {
        // This call requires the caller’s identity to match the package name of the given context.
        // However, the SafetyCenterResourceApk Context's has package name "android", which does not
        // necessarily match the caller’s package when making Binder calls, so the calling identity
        // has to be cleared.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return requireNonNull(context.getSystemService(DevicePolicyManager.class))
                    .getResources()
                    .getString(SAFETY_CENTER_PREFIX + devicePolicyIdentifier, defaultValueLoader);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
