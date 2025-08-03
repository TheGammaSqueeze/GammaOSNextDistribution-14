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

package com.android.bedstead.nene.permissions;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Versions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link PermissionContext}
 */
public final class PermissionContextImpl implements PermissionContextModifier {
    private final Permissions mPermissions;
    private final Set<String> mGrantedPermissions = new HashSet<>();
    private final Set<String> mDeniedPermissions = new HashSet<>();
    private final Set<String> mGrantedAppOps = new HashSet<>();
    private final Set<String> mDeniedAppOps = new HashSet<>();

    PermissionContextImpl(Permissions permissions) {
        mPermissions = permissions;
    }

    Set<String> grantedPermissions() {
        return mGrantedPermissions;
    }

    Set<String> deniedPermissions() {
        return mDeniedPermissions;
    }

    Set<String> grantedAppOps() {
        return mGrantedAppOps;
    }

    Set<String> deniedAppOps() {
        return mDeniedAppOps;
    }

    /**
     * See {@link Permissions#withPermission(String...)}
     */
    @Override
    public PermissionContextImpl withPermission(String... permissions) {
        for (String permission : permissions) {
            if (mDeniedPermissions.contains(permission)) {
                mPermissions.clearPermissions();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        mGrantedPermissions.addAll(Arrays.asList(permissions));

        mPermissions.applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withPermissionOnVersion(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersion(int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, sdkVersion, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionAtLeast(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionAtLeast(
            int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, Versions.ANY, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionAtMost(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionAtMost(
            int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(Versions.ANY, sdkVersion, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionBetween(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withPermission(permissions);
        }

        return this;
    }

    /**
     * See {@link Permissions#withoutPermission(String...)}
     */
    @Override
    public PermissionContextImpl withoutPermission(String... permissions) {
        for (String permission : permissions) {
            if (mGrantedPermissions.contains(permission)) {
                mPermissions.clearPermissions();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        if (TestApis.packages().instrumented().isInstantApp()) {
            throw new NeneException(
                    "Tests which use withoutPermission must not run as instant apps. If you are"
                            + "using DeviceState, use the @RequireNotInstantApp annotation.");
        }

        mDeniedPermissions.addAll(Arrays.asList(permissions));

        mPermissions.applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withAppOp(String...)}
     */
    @Override
    public PermissionContextImpl withAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mDeniedAppOps.contains(appOp)) {
                mPermissions.clearPermissions();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }

        mGrantedAppOps.addAll(Arrays.asList(appOps));

        mPermissions.applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withAppOpOnVersion(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersion(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, sdkVersion, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionAtMost(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionAtMost(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(Versions.ANY, sdkVersion, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionAtLeast(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionAtLeast(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, Versions.ANY, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionBetween(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withAppOp(appOps);
        }

        return this;
    }

    /**
     * See {@link Permissions#withoutAppOp(String...)}.
     */
    @Override
    public PermissionContextImpl withoutAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mGrantedAppOps.contains(appOp)) {
                mPermissions.clearPermissions();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }

        mDeniedAppOps.addAll(Arrays.asList(appOps));

        mPermissions.applyPermissions();

        return this;
    }

    @Override
    public void close() {
        Permissions.sInstance.undoPermission(this);
    }
}
