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

package android.photopicker.cts;

import static android.photopicker.cts.PhotoPickerBaseTest.INVALID_CLOUD_PROVIDER;
import static android.photopicker.cts.PhotoPickerCloudUtils.NAMESPACE_MEDIAPROVIDER;
import static android.photopicker.cts.PhotoPickerCloudUtils.NAMESPACE_STORAGE_NATIVE_BOOT;
import static android.photopicker.cts.PhotoPickerCloudUtils.disableCloudMediaAndClearAllowedCloudProviders;
import static android.photopicker.cts.PhotoPickerCloudUtils.enableCloudMediaAndSetAllowedCloudProviders;
import static android.photopicker.cts.PhotoPickerCloudUtils.getAllowedProvidersDeviceConfig;
import static android.photopicker.cts.PhotoPickerCloudUtils.getCurrentCloudProvider;
import static android.photopicker.cts.PhotoPickerCloudUtils.isCloudMediaEnabled;
import static android.photopicker.cts.PhotoPickerCloudUtils.setCloudProvider;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/**
 * Provides helper functions to save current device state and restore the state after running tests.
 */
public class DeviceStatePreserver {
    private static final String TAG = DeviceStatePreserver.class.getSimpleName();

    private UiDevice mUiDevice;

    // Previous state in storage_native_boot namespace
    private static boolean sIsCloudEnabledStorageNativeBoot;
    @Nullable
    private static String sAllowedCloudProvidersStorageNativeBoot;

    // Previous state in mediaprovider namespace
    private static boolean sIsCloudEnabledMediaProvider;
    @Nullable
    private static String sAllowedCloudProvidersMediaProvider;

    @Nullable
    private static String sPreviouslySetCloudProvider;

    public DeviceStatePreserver(@NonNull UiDevice device) {
        mUiDevice = device;
    }

    /**
     * Saves current cloud provider related device config flags from
     * {@link PhotoPickerCloudUtils#NAMESPACE_STORAGE_NATIVE_BOOT} and
     * {@link PhotoPickerCloudUtils#NAMESPACE_MEDIAPROVIDER}.
     * Also saves the current cloud provider.
     */
    public void saveCurrentCloudProviderState() throws IOException {
        // Save flags in storage_native_boot namespace
        sIsCloudEnabledStorageNativeBoot = isCloudMediaEnabled(NAMESPACE_STORAGE_NATIVE_BOOT);
        if (sIsCloudEnabledStorageNativeBoot) {
            sAllowedCloudProvidersStorageNativeBoot =
                    getAllowedProvidersDeviceConfig(NAMESPACE_STORAGE_NATIVE_BOOT);
        }

        // Save flags in mediaprovider namespace
        sIsCloudEnabledMediaProvider = isCloudMediaEnabled(NAMESPACE_MEDIAPROVIDER);
        if (sIsCloudEnabledMediaProvider) {
            sAllowedCloudProvidersMediaProvider =
                    getAllowedProvidersDeviceConfig(NAMESPACE_MEDIAPROVIDER);
        }

        // Save current cloud provider.
        try {
            sPreviouslySetCloudProvider = getCurrentCloudProvider(mUiDevice);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not get previously set cloud provider", e);
            sPreviouslySetCloudProvider = INVALID_CLOUD_PROVIDER;
        }
    }

    /**
     * Restores the current cloud provider state previously saved using
     * {@link DeviceStatePreserver#saveCurrentCloudProviderState()}.
     */
    public void restoreCloudProviderState() throws Exception {
        // Restore flags in storage_native_boot namespace
        if (sIsCloudEnabledStorageNativeBoot) {
            enableCloudMediaAndSetAllowedCloudProviders(
                    NAMESPACE_STORAGE_NATIVE_BOOT, sAllowedCloudProvidersStorageNativeBoot);
        } else {
            disableCloudMediaAndClearAllowedCloudProviders(NAMESPACE_STORAGE_NATIVE_BOOT);
        }

        // Restore flags in mediaprovider namespace
        if (sIsCloudEnabledMediaProvider) {
            enableCloudMediaAndSetAllowedCloudProviders(
                    NAMESPACE_MEDIAPROVIDER, sAllowedCloudProvidersMediaProvider);
        } else {
            disableCloudMediaAndClearAllowedCloudProviders(NAMESPACE_MEDIAPROVIDER);
        }

        // Restore previously set cloud provider.
        setCloudProvider(mUiDevice, sPreviouslySetCloudProvider);
    }
}
