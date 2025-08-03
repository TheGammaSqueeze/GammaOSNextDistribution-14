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

package android.photopicker.cts;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;
import static android.photopicker.cts.PhotoPickerBaseTest.INVALID_CLOUD_PROVIDER;
import static android.photopicker.cts.PickerProviderMediaGenerator.syncCloudProvider;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.content.ClipData;
import android.content.Context;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;

import com.android.modules.utils.build.SdkLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhotoPickerCloudUtils {
    public static final String NAMESPACE_MEDIAPROVIDER = "mediaprovider";
    public static final String NAMESPACE_STORAGE_NATIVE_BOOT = "storage_native_boot";
    private static final String KEY_ALLOWED_CLOUD_PROVIDERS = "allowed_cloud_providers";
    private static final String KEY_CLOUD_MEDIA_FEATURE_ENABLED = "cloud_media_feature_enabled";
    private static final String DISABLE_DEVICE_CONFIG_SYNC =
            "cmd device_config set_sync_disabled_for_tests %s";
    private static final String DISABLE_DEVICE_CONFIG_SYNC_MODE = "until_reboot";
    private static final String TAG = "PickerCloudUtils";

    /**
     * Device config is reset from the server periodically. When tests override device config, it is
     * not a sticky change. The device config may be reset to server values at any point - even
     * while a test is running. In order to prevent unwanted device config resets, this method
     * disables device config syncs until device reboot.
     */
    static void disableDeviceConfigSync() {
        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand(String.format(DISABLE_DEVICE_CONFIG_SYNC,
                            DISABLE_DEVICE_CONFIG_SYNC_MODE));
        } catch (IOException e) {
            Log.e(TAG, "Could not disable device_config sync. "
                    + "Device config may reset to server values at any point during test runs.", e);
        }
    }
    public static List<String> extractMediaIds(ClipData clipData, int minCount) {
        final int count = clipData.getItemCount();
        assertThat(count).isAtLeast(minCount);

        final List<String> mediaIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            mediaIds.add(clipData.getItemAt(i).getUri().getLastPathSegment());
        }

        return mediaIds;
    }

    public static void selectAndAddPickerMedia(UiDevice uiDevice, int maxCount)
            throws Exception {
        final List<UiObject> itemList = findItemList(maxCount);
        for (int i = 0; i < itemList.size(); i++) {
            final UiObject item = itemList.get(i);
            item.click();
            uiDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        uiDevice.waitForIdle();
    }

    public static ClipData fetchPickerMedia(GetResultActivity activity, UiDevice uiDevice,
            int maxCount) throws Exception {
        selectAndAddPickerMedia(uiDevice, maxCount);

        return activity.getResult().data.getClipData();
    }

    public static void initCloudProviderWithImage(
            Context context, PickerProviderMediaGenerator.MediaGenerator mediaGenerator,
            String authority, Pair<String, String>... mediaPairs) throws Exception {
        PhotoPickerBaseTest.setCloudProvider(authority);
        assertThat(MediaStore.isCurrentCloudMediaProviderAuthority(context.getContentResolver(),
                authority)).isTrue();

        for (Pair<String, String> pair : mediaPairs) {
            addImage(mediaGenerator, pair.first, pair.second);
        }

        syncCloudProvider(context);
    }

    public static void addImage(PickerProviderMediaGenerator.MediaGenerator generator,
            String localId, String cloudId)
            throws Exception {
        final long imageSizeBytes = 107684;
        generator.addMedia(localId, cloudId, /* albumId */ null, "image/jpeg",
                /* mimeTypeExtension */ 0, imageSizeBytes, /* isFavorite */ false,
                R.raw.lg_g4_iso_800_jpg);
    }

    public static void containsExcept(List<String> mediaIds, String contained,
            String notContained) {
        assertThat(mediaIds).contains(contained);
        assertThat(mediaIds).containsNoneIn(Collections.singletonList(notContained));
    }

    /**
     * @return true if cloud is enabled in the device config of the given namespace,
     * otherwise false.
     */
    public static boolean isCloudMediaEnabled(@NonNull String namespace) {
        return Boolean.parseBoolean(readDeviceConfigProp(namespace,
                KEY_CLOUD_MEDIA_FEATURE_ENABLED));
    }

    /**
     * @return the allowed providers in the device config of the given namespace.
     */
    @Nullable
    static String getAllowedProvidersDeviceConfig(@NonNull String namespace) {
        return readDeviceConfigProp(namespace, KEY_ALLOWED_CLOUD_PROVIDERS);
    }

    /**
     * Enables cloud media and sets the allowed cloud provider in the namespace storage_native_boot
     * and mediaprovider.
     */
    static void enableCloudMediaAndSetAllowedCloudProviders(@NonNull String allowedPackagesJoined) {
        enableCloudMediaAndSetAllowedCloudProviders(NAMESPACE_MEDIAPROVIDER, allowedPackagesJoined);
        enableCloudMediaAndSetAllowedCloudProviders(
                NAMESPACE_STORAGE_NATIVE_BOOT, allowedPackagesJoined);
    }

    /**
     * Enables cloud media and sets the allowed cloud provider in the given namespace.
     */
    static void enableCloudMediaAndSetAllowedCloudProviders(@NonNull String namespace,
            @NonNull String allowedPackagesJoined) {
        writeDeviceConfigProp(namespace, KEY_ALLOWED_CLOUD_PROVIDERS, allowedPackagesJoined);
        assertWithMessage("Failed to update the allowed cloud providers device config")
                .that(getAllowedProvidersDeviceConfig(namespace))
                .isEqualTo(allowedPackagesJoined);

        writeDeviceConfigProp(namespace, KEY_CLOUD_MEDIA_FEATURE_ENABLED, true);
        assertWithMessage("Failed to update the cloud media feature device config")
                .that(isCloudMediaEnabled(namespace))
                .isTrue();
    }

    /**
     * Disable cloud media in the given namespace.
     */
    static void disableCloudMediaAndClearAllowedCloudProviders(@NonNull String namespace) {
        writeDeviceConfigProp(namespace, KEY_CLOUD_MEDIA_FEATURE_ENABLED, false);
        assertWithMessage("Failed to update the cloud media feature device config")
                .that(isCloudMediaEnabled(namespace))
                .isFalse();

        deleteDeviceConfigProp(namespace, KEY_ALLOWED_CLOUD_PROVIDERS);
        assertWithMessage("Failed to delete the allowed cloud providers device config")
                .that(getAllowedProvidersDeviceConfig(namespace))
                .isNull();
    }

    @NonNull
    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Nullable
    private static String readDeviceConfigProp(@NonNull String namespace, @NonNull String name) {
        getUiAutomation().adoptShellPermissionIdentity(READ_DEVICE_CONFIG);
        try {
            return DeviceConfig.getProperty(namespace, name);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static void writeDeviceConfigProp(@NonNull String namespace,
            @NonNull String key, boolean value) {
        writeDeviceConfigProp(namespace, key, Boolean.toString(value));
    }

    private static void writeDeviceConfigProp(@NonNull String namespace, @NonNull String name,
            @NonNull String value) {
        getUiAutomation().adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG);

        try {
            DeviceConfig.setProperty(namespace, name, value,
                    /* makeDefault*/ false);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static void deleteDeviceConfigProp(@NonNull String namespace, @NonNull String name) {
        try {
            getUiAutomation().adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG);
            if (SdkLevel.isAtLeastU()) {
                DeviceConfig.deleteProperty(namespace, name);
            } else {
                // DeviceConfig.deleteProperty API is only available from T onwards.
                try {
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            .executeShellCommand(
                                    String.format("device_config delete %s %s",
                                            namespace, name));
                } catch (IOException e) {
                    Log.e(TAG, String.format("Could not delete device_config %s / %s",
                            namespace, name), e);
                }
            }
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Set cloud provider in the device to the provided authority.
     * If the provided cloud authority equals {@link PhotoPickerBaseTest#INVALID_CLOUD_PROVIDER},
     * the cloud provider will not be updated.
     */
    public static void setCloudProvider(@Nullable UiDevice sDevice,
            @Nullable String authority) throws IOException {
        if (INVALID_CLOUD_PROVIDER.equals(authority)) {
            Log.w(TAG, "Cloud provider is invalid. "
                    + "Ignoring the request to set the cloud provider to invalid");
            return;
        }
        if (authority == null) {
            sDevice.executeShellCommand(
                    "content call"
                            + " --user " + UserHandle.myUserId()
                            + " --uri content://media/ --method set_cloud_provider --extra"
                            + " cloud_provider:n:null");
        } else {
            sDevice.executeShellCommand(
                    "content call"
                            + " --user " + UserHandle.myUserId()
                            + " --uri content://media/ --method set_cloud_provider --extra"
                            + " cloud_provider:s:"
                            + authority);
        }
    }

    /**
     * @return the current cloud provider.
     */
    public static String getCurrentCloudProvider(UiDevice sDevice) throws IOException {
        final String out =
                sDevice.executeShellCommand(
                        "content call"
                                + " --user " + UserHandle.myUserId()
                                + " --uri content://media/ --method get_cloud_provider");
        return extractCloudProvider(out);
    }

    private static String extractCloudProvider(String out) {
        String[] splitOutput;
        if (TextUtils.isEmpty(out) || ((splitOutput = out.split("=")).length < 2)) {
            throw new RuntimeException("Could not get current cloud provider. Output: " + out);
        }
        String cloudprovider = splitOutput[1];
        cloudprovider = cloudprovider.substring(0, cloudprovider.length() - 3);
        if (cloudprovider.equals("null")) {
            return null;
        }
        return cloudprovider;
    }
}
