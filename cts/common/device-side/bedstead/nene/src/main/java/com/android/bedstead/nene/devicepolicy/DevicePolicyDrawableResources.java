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

package com.android.bedstead.nene.devicepolicy;

import static android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyDrawableResource;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.os.Build;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * APIs related to device policy drawable resource overrides.
 */
@TargetApi(TIRAMISU)
public final class DevicePolicyDrawableResources {

    public static final DevicePolicyDrawableResources sInstance =
            new DevicePolicyDrawableResources();

    private static final DevicePolicyResourcesManager sResourcesManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class)
                    .getResources();

    private DevicePolicyDrawableResources() {

    }

    /**
     * Set a drawable override with a resource from the instrumented package.
     */
    public void set(String id, String style, int resource) {
        set(new DevicePolicyDrawableResource(TestApis.context().instrumentedContext(), id, style, resource));
    }

    /**
     * Set drawable overrides.
     */
    public void set(DevicePolicyDrawableResource... resources) {
        set(new HashSet<>(Arrays.asList(resources)));
    }

    /**
     * Set drawable overrides.
     */
    public void set(Set<DevicePolicyDrawableResource> resources) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sResourcesManager.setDrawables(resources);
        }
    }

    /**
     * Reset drawable overrides.
     */
    public void reset(String... resourceIds) {
        reset(new HashSet<>(Arrays.asList(resourceIds)));
    }

    /**
     * Reset drawable overrides.
     */
    public void reset(Set<String> resourceIds) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sResourcesManager.resetDrawables(resourceIds);
        }
    }

}
