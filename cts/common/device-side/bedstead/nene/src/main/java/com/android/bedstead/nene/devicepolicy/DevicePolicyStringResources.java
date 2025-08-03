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
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.app.admin.DevicePolicyStringResource;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * APIs related to Device policy string resource overrides.
 */
@TargetApi(TIRAMISU)
public final class DevicePolicyStringResources {

    public static final DevicePolicyStringResources sInstance = new DevicePolicyStringResources();

    private static final DevicePolicyResourcesManager sResourcesManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class)
                    .getResources();

    private DevicePolicyStringResources() {

    }

    /**
     * Set a string override with a resource from the instrumented package.
     */
    public void set(String id, int resource) {
        set(new DevicePolicyStringResource(TestApis.context().instrumentedContext(), id, resource));
    }

    /**
     * Set string overrides.
     */
    public void set(Map<String, Integer> resources) {
        set(resources.keySet().stream().map(id -> new DevicePolicyStringResource(TestApis.context().instrumentedContext(), id, resources.get(id))).collect(
                Collectors.toSet()));
    }

    /**
     * Set string overrides.
     */
    public void set(DevicePolicyStringResource... resources) {
        set(new HashSet<>(Arrays.asList(resources)));
    }

    /**
     * Set string overrides.
     */
    public void set(Set<DevicePolicyStringResource> resources) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sResourcesManager.setStrings(resources);
        }
        TestApis.broadcasts().waitForBroadcastDispatch(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED,
                "Ensure updated strings have propagated before continuing test");
    }

    /**
     * Reset string overrides.
     */
    public void reset(String... resourceIds) {
        reset(new HashSet<>(Arrays.asList(resourceIds)));
    }

    /**
     * Reset string overrides.
     */
    public void reset(Set<String> resourceIds) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sResourcesManager.resetStrings(resourceIds);
        }
        TestApis.broadcasts().waitForBroadcastDispatch(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED,
                "Ensure updated strings have propagated before continuing test");
    }

}
