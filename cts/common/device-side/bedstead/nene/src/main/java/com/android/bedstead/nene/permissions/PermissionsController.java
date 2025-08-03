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

package com.android.bedstead.nene.permissions;

/** Methods available to classes which maintain permission contexts. */
public interface PermissionsController {
    /** Enter a permission context with the given permissions. */
    PermissionContext withPermission(String... permissions);
    /** Enter a permission context with the given permissions only when on a matching version. */
    PermissionContext withPermissionOnVersion(int sdkVersion, String... permissions);
    /** Enter a permission context with the given permissions only when on a matching version. */
    PermissionContext withPermissionOnVersionAtLeast(int minSdkVersion, String... permissions);
    /** Enter a permission context with the given permissions only when on a matching version. */
    PermissionContext withPermissionOnVersionAtMost(int maxSdkVersion, String... permissions);
    /** Enter a permission context with the given permissions only when on a matching version. */
    PermissionContext withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions);
    /** Enter a permission context without the given permissions. */
    PermissionContext withoutPermission(String... permissions);

    /** Enter a permission context with the given app op. */
    PermissionContext withAppOp(String... appOps);
    /** Enter a permission context with the given app op only when on a matching version. */
    PermissionContext withAppOpOnVersion(int sdkVersion, String... appOps);
    /** Enter a permission context with the given app op only when on a matching version. */
    PermissionContext withAppOpOnVersionAtLeast(int minSdkVersion, String... appOps);
    /** Enter a permission context with the given app op only when on a matching version. */
    PermissionContext withAppOpOnVersionAtMost(int maxSdkVersion, String... appOps);
    /** Enter a permission context with the given app op only when on a matching version. */
    PermissionContext withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps);
    /** Enter a permission context without the given app op. */
    PermissionContext withoutAppOp(String... appOps);
}



