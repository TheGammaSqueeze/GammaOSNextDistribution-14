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

package com.android.bedstead.testapp;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.permissions.PermissionContextModifier;
import com.android.bedstead.nene.utils.Versions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Collection of required permissions to be granted or denied to a test app.
 *
 * <p>Once the permissions are no longer required, {@link #close()} should be called.
 *
 * <p>It is recommended that this be used as part of a try-with-resource block
 */
public class TestAppPermissionContext implements PermissionContextModifier {
    private final TestAppInstancePermissions mTestAppInstancePermissions;
    private final Set<String> mGrantedPermissions = new HashSet<>();
    private final Set<String> mDeniedPermissions = new HashSet<>();
    private final Set<String> mGrantedAppOps = new HashSet<>();
    private final Set<String> mDeniedAppOps = new HashSet<>();

    TestAppPermissionContext(TestAppInstancePermissions testAppInstancePermissions) {
        mTestAppInstancePermissions = testAppInstancePermissions;
    }

    /**
     * See {@link TestAppInstancePermissions#withPermission(String...)}
     */
    @Override
    public TestAppPermissionContext withPermission(String... permissions) {
        for (String permission : permissions) {
            if (mDeniedPermissions.contains(permission)) {
                mTestAppInstancePermissions.clearPermissions();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        mGrantedPermissions.addAll(Arrays.asList(permissions));

        mTestAppInstancePermissions.applyPermissions();
        return this;
    }

    /**
     * See {@link TestAppInstancePermissions#withPermissionOnVersion(int, String...)}
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersion(int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, sdkVersion, permissions);
    }

    /**
     * See {@link TestAppInstancePermissions#withPermissionOnVersionAtLeast(int, String...)}
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionAtLeast(
            int minSdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(minSdkVersion, Versions.ANY, permissions);
    }

    /**
     * See {@link TestAppInstancePermissions#withPermissionOnVersionAtMost(int, String...)}
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionAtMost(
            int maxSdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(Versions.ANY, maxSdkVersion, permissions);
    }

    /**
     * See {@link TestAppInstancePermissions#withPermissionOnVersionBetween(int, int, String...)}
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withPermission(permissions);
        }

        return this;
    }

    /**
     * See {@link TestAppInstancePermissions#withoutPermission(String...)}
     */
    @Override
    public TestAppPermissionContext withoutPermission(String... permissions) {
        for (String permission : permissions) {
            if (mGrantedPermissions.contains(permission)) {
                mTestAppInstancePermissions.clearPermissions();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        mDeniedPermissions.addAll(Arrays.asList(permissions));

        mTestAppInstancePermissions.applyPermissions();
        return this;
    }

    /**
     * See {@link TestAppInstancePermissions#withAppOp(String...)}
     */
    @Override
    public TestAppPermissionContext withAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mDeniedAppOps.contains(appOp)) {
                mTestAppInstancePermissions.clearPermissions();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }
        mGrantedAppOps.addAll(Arrays.asList(appOps));

        mTestAppInstancePermissions.applyPermissions();
        return this;
    }

    /**
     * See {@link TestAppInstancePermissions#withAppOpOnVersion(int, String...)}
     */
    @Override
    public PermissionContext withAppOpOnVersion(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, sdkVersion, appOps);
    }

    /**
     * See {@link TestAppInstancePermissions#withAppOpOnVersionAtLeast(int, String...)}
     */
    @Override
    public PermissionContext withAppOpOnVersionAtLeast(int minSdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(minSdkVersion, Versions.ANY, appOps);
    }

    /**
     * See {@link TestAppInstancePermissions#withAppOpOnVersionAtMost(int, String...)}
     */
    @Override
    public PermissionContext withAppOpOnVersionAtMost(int maxSdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(Versions.ANY, maxSdkVersion, appOps);
    }

    /**
     * See {@link TestAppInstancePermissions#withAppOpOnVersionBetween(int, int, String...)}
     */
    @Override
    public PermissionContext withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withAppOp(appOps);
        }

        return this;
    }

    /**
     * See {@link TestAppInstancePermissions#withoutAppOp(String...)}
     */
    @Override
    public TestAppPermissionContext withoutAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mGrantedAppOps.contains(appOp)) {
                mTestAppInstancePermissions.clearPermissions();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }

        mDeniedAppOps.addAll(Arrays.asList(appOps));

        mTestAppInstancePermissions.applyPermissions();
        return this;
    }

    /**
     * Returns the set of permissions granted in this context.
     */
    public Set<String> grantedPermissions() {
        return mGrantedPermissions;
    }

    /**
     * Returns the set of permissions denied in this context.
     */
    public Set<String> deniedPermissions() {
        return mDeniedPermissions;
    }

    /**
     * Returns the set of appOps granted in this context.
     */
    public Set<String> grantedAppOps() {
        return mGrantedAppOps;
    }

    /**
     * Returns the set of appOps denied in this context.
     */
    public Set<String> deniedAppOps() {
        return mDeniedAppOps;
    }

    @Override
    public void close() {
        mTestAppInstancePermissions.undoPermission(this);
    }
}
