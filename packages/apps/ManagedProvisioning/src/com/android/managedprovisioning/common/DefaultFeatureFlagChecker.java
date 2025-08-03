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

package com.android.managedprovisioning.common;

import static android.provider.Settings.Global.MANAGED_PROVISIONING_DEFER_PROVISIONING_TO_ROLE_HOLDER;

import static java.util.Objects.requireNonNull;

import android.content.ContentResolver;
import android.provider.Settings;

/**
 * Utility methods related to reading feature flags.
 */
public class DefaultFeatureFlagChecker implements FeatureFlagChecker {

    /**
     * Default value for {@link
     * Settings.Global#MANAGED_PROVISIONING_DEFER_PROVISIONING_TO_ROLE_HOLDER}.
     */
    private static final int DEFAULT_DEFER_PROVISIONING_TO_ROLE_HOLDER = 1;

    private final ContentResolver mContentResolver;

    public DefaultFeatureFlagChecker(ContentResolver contentResolver) {
        mContentResolver = requireNonNull(contentResolver);
    }

    @Override
    public boolean canDelegateProvisioningToRoleHolder() {
        return Settings.Global.getInt(
                mContentResolver,
                MANAGED_PROVISIONING_DEFER_PROVISIONING_TO_ROLE_HOLDER,
                DEFAULT_DEFER_PROVISIONING_TO_ROLE_HOLDER) == 1;
    }
}
