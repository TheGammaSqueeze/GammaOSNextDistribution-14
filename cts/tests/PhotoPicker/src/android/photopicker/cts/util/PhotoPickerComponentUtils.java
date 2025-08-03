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

package android.photopicker.cts.util;

import static android.photopicker.cts.util.PhotoPickerPackageUtils.getPhotoPickerPackageName;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Util methods for Photo Picker related components.
 */
public class PhotoPickerComponentUtils {

    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long POLLING_SLEEP_MILLIS = 100;

    public static final ComponentName GET_CONTENT_ACTIVITY_COMPONENT = new ComponentName(
            getPhotoPickerPackageName(),
            "com.android.providers.media.photopicker.PhotoPickerGetContentActivity");

    public static final ComponentName PICKER_SETTINGS_ACTIVITY_COMPONENT = new ComponentName(
            getPhotoPickerPackageName(),
            "com.android.providers.media.photopicker.PhotoPickerSettingsActivity");

    /**
     * Returns the current state of the given component and enables it.
     */
    public static int enableAndGetOldState(@NonNull ComponentName componentName) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final PackageManager packageManager = inst.getContext().getPackageManager();
        if (isComponentEnabledSetAsExpected(packageManager,
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED)) {
            return PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        }

        final int currentState = packageManager.getComponentEnabledSetting(componentName);

        updateComponentEnabledSetting(packageManager,
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        return currentState;
    }

    /**
     * Sets state of the given component to the given state.
     */
    public static void setState(@NonNull ComponentName componentName, int oldState)
            throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        updateComponentEnabledSetting(inst.getContext().getPackageManager(),
                componentName, oldState);
    }

    private static void updateComponentEnabledSetting(
            @NonNull PackageManager packageManager,
            @NonNull ComponentName componentName,
            int state) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        inst.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        try {
            packageManager.setComponentEnabledSetting(componentName, state,
                    PackageManager.DONT_KILL_APP);
        } finally {
            inst.getUiAutomation().dropShellPermissionIdentity();
        }
        waitForComponentToBeInExpectedState(packageManager, componentName, state);
    }

    private static void waitForComponentToBeInExpectedState(
            @NonNull PackageManager packageManager,
            @NonNull ComponentName componentName,
            int state) throws Exception {
        pollForCondition(() ->
                        isComponentEnabledSetAsExpected(packageManager, componentName, state),
                "Timed out while waiting for component to be enabled");
    }

    private static void pollForCondition(Supplier<Boolean> condition, String errorMessage)
            throws Exception {
        for (int i = 0; i < POLLING_TIMEOUT_MILLIS / POLLING_SLEEP_MILLIS; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(POLLING_SLEEP_MILLIS);
        }
        throw new TimeoutException(errorMessage);
    }

    private static boolean isComponentEnabledSetAsExpected(@NonNull PackageManager packageManager,
            @NonNull ComponentName componentName,
            int state) {
        return packageManager.getComponentEnabledSetting(componentName) == state;
    }
}
