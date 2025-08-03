/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.managedprovisioning.common.Globals;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Set;

/**
 * Constants used for communication between service and activity.
 */
public final class Constants {
    // Intents sent by the activity to the service
    /**
     * Intent action sent to the {@link ProvisioningService} to indicate that provisioning should be
     * started.
     */
    public static final String ACTION_START_PROVISIONING =
            "com.android.managedprovisioning.START_PROVISIONING";

    public static final int COLOR_TYPE_ACCENT = 1;
    public static final int COLOR_TYPE_PRIMARY_TEXT = 2;
    public static final int COLOR_TYPE_SECONDARY_TEXT = 3;
    public static final int COLOR_TYPE_BACKGROUND_SURFACE = 4;
    public static final int COLOR_TYPE_NOTIFICATION_BACKGROUND = 5;
    public static final int COLOR_TYPE_NAVIGATION_BAR_COLOR = 6;
    public static final int COLOR_TYPE_NAVIGATION_BAR_DIVIDER_COLOR = 7;
    public static final int COLOR_TYPE_IS_NIGHT_MODE_ACTIVE = 8;

    @IntDef(prefix = {"COLOR_PALETTE_"}, value = {
        COLOR_TYPE_ACCENT,
        COLOR_TYPE_PRIMARY_TEXT,
        COLOR_TYPE_SECONDARY_TEXT,
        COLOR_TYPE_BACKGROUND_SURFACE,
        COLOR_TYPE_NOTIFICATION_BACKGROUND,
        COLOR_TYPE_NAVIGATION_BAR_COLOR,
        COLOR_TYPE_NAVIGATION_BAR_DIVIDER_COLOR,
        COLOR_TYPE_IS_NIGHT_MODE_ACTIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ColorType {}

    /**
     * A {@link HashMap} extra that maps colors from {@link ColorType} to an {@link Integer} color
     */
    public static final String EXTRA_PROVISIONING_COLOR_PALETTE =
            "android.app.extra.PROVISIONING_COLOR_PALETTE";

    public static File getDeferredMetricsFile(Context context) {
        return new File(context.getFilesDir(), "deferred_metrics");
    }

    public static boolean FLAG_ENABLE_LIGHT_DARK_MODE = true;

    /**
     * A boolean flag to indicate whether to lock the orientation to portrait mode.
     * <p>This is a temporary feature flag until the setup wizard library support is implemented.
     */
    public static final boolean LOCK_TO_PORTRAIT_MODE = false;

    /**
     * A boolean flag to indicate whether to enable custom activity start gestures.
     */
    public static boolean ENABLE_CUSTOM_TRANSITIONS = true;

    public static final Intent PROVISIONING_SERVICE_INTENT = new Intent().setComponent(
            new ComponentName(
                    Globals.MANAGED_PROVISIONING_PACKAGE_NAME,
                    ProvisioningService.class.getName()));

    private static final Set<String> sManagedProvisioningProvisioningActions =
            Set.of(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE,
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);

    /**
     * Returns whether the supplied provisioning action is supported for role holder-driven
     * provisioning.
     */
    public static boolean isRoleHolderProvisioningAllowedForAction(
            @Nullable String managedProvisioningAction) {
        if (managedProvisioningAction == null) {
            return false;
        }
        return sManagedProvisioningProvisioningActions.contains(managedProvisioningAction);
    }

    private Constants() {
        // Do not instantiate
    }
}
