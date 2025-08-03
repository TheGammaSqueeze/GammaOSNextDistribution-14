/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.internal.accessibility.util.AccessibilityUtils;

import java.util.List;

/**
 * Hosts utility methods and constants for screen reader related settings.
 */
public class ScreenReaderUtils {

    /**
     * Returns whether the system screen reader is installed on the device.
     */
    static boolean isScreenReaderInstalled(Context context) {
        return getScreenReaderServiceInfo(context) != null;
    }

    /**
     * Returns whether the default screen reader is currently enabled on the device.
     */
    static boolean isScreenReaderEnabled(Context context) {
        ComponentName screenReaderComponent = getScreenReaderComponentName(context);
        if (screenReaderComponent == null) {
            return false;
        }
        return AccessibilityUtils.getEnabledServicesFromSettings(context,
                UserHandle.myUserId()).contains(screenReaderComponent);
    }

    /**
     * Returns the name of the system default screen reader if it is installed. Returns an empty
     * string if not.
     */
    @Nullable
    static CharSequence getScreenReaderName(Context context) {
        AccessibilityServiceInfo serviceInfo = getScreenReaderServiceInfo(context);
        if (serviceInfo == null) {
            return "";
        }

        return serviceInfo.getResolveInfo().loadLabel(context.getPackageManager());
    }

    /**
     * Returns the description of the system default screen reader if it is installed. Returns an
     * empty string if not.
     */
    static CharSequence getScreenReaderDescription(Context context) {
        AccessibilityServiceInfo serviceInfo = getScreenReaderServiceInfo(context);
        if (serviceInfo == null) {
            return "";
        }

        String description = serviceInfo.loadDescription(context.getPackageManager());
        return description != null ? description : "";
    }

    /**
     * Returns the settings activity ComponentName of the system default screen reader if the screen
     * reader is installed and a settings activity exists. Returns {@code null} otherwise.
     */
    @Nullable
    static ComponentName getScreenReaderSettingsActivity(Context context) {
        AccessibilityServiceInfo serviceInfo = getScreenReaderServiceInfo(context);
        if (serviceInfo == null) {
            return null;
        }

        String settingsActivity = serviceInfo.getSettingsActivityName();
        if (settingsActivity == null || settingsActivity.isEmpty()) {
            return null;
        }

        return new ComponentName(getScreenReaderComponentName(context).getPackageName(),
                settingsActivity);
    }

    /**
     * Sets the screen reader enabled state. This should only be called if the screen reader is
     * installed.
     */
    static void setScreenReaderEnabled(Context context, boolean enabled) {
        ComponentName screenReaderComponent = getScreenReaderComponentName(context);
        if (screenReaderComponent == null) {
            return;
        }

        AccessibilityUtils.setAccessibilityServiceState(context,
                getScreenReaderComponentName(context), enabled,
                UserHandle.myUserId());
    }

    private static ComponentName getScreenReaderComponentName(Context context) {
        return ComponentName.unflattenFromString(
                context.getString(R.string.config_default_screen_reader));
    }

    @Nullable
    private static AccessibilityServiceInfo getScreenReaderServiceInfo(Context context) {
        AccessibilityManager accessibilityManager = context.getSystemService(
                AccessibilityManager.class);
        if (accessibilityManager == null) {
            return null;
        }

        List<AccessibilityServiceInfo> accessibilityServices =
                accessibilityManager.getInstalledAccessibilityServiceList();
        if (accessibilityServices == null) {
            return null;
        }

        ComponentName screenReaderComponent = getScreenReaderComponentName(context);
        if (screenReaderComponent == null) {
            return null;
        }

        for (AccessibilityServiceInfo info : accessibilityServices) {
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (screenReaderComponent.getPackageName().equals(serviceInfo.packageName)
                    && screenReaderComponent.getClassName().equals(serviceInfo.name)) {
                return info;
            }
        }

        return null;
    }

}
