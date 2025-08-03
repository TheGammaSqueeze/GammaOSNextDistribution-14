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
package com.android.bedstead.testapp;

import com.android.bedstead.nene.appops.AppOpsMode;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.permissions.PermissionsController;
import com.android.bedstead.nene.utils.Versions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Permissions management for a test app instance.
 */
public final class TestAppInstancePermissions implements PermissionsController {

    private final List<TestAppPermissionContext> mPermissionContexts =
            Collections.synchronizedList(new ArrayList<>());
    private final TestAppInstance mTestAppInstance;

    TestAppInstancePermissions(TestAppInstance testAppInstance) {
        mTestAppInstance = testAppInstance;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted to the test app.
     *
     * <p>The permission will only be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Note that only runtime and development permissions can be granted to test apps.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions().withPermission(PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withPermission(String... permissions) {
        TestAppPermissionContext context =
                new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withPermission(permissions);

        return context;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted to the test app.
     *
     * <p>The permission will only be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Note that only runtime and development permissions can be granted to test apps.
     *
     * <p>The permission will only be granted on the given version.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withPermissionOnVersion(R, PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersion(int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, sdkVersion, permissions);
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted to the test app.
     *
     * <p>The permission will only be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Note that only runtime and development permissions can be granted to test apps.
     *
     * <p>The permission will only be granted on the given version or higher.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withPermissionOnVersionAtLest(R, PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionAtLeast(
            int minSdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(minSdkVersion, Versions.ANY, permissions);
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted to the test app.
     *
     * <p>The permission will only be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Note that only runtime and development permissions can be granted to test apps.
     *
     * <p>The permission will only be granted on the given version or lower
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withPermissionOnVersionAtMost(R, PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionAtMost(
            int maxSdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(Versions.ANY, maxSdkVersion, permissions);
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted to the test app.
     *
     * <p>The permission will only be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Note that only runtime and development permissions can be granted to test apps.
     *
     * <p>The permission will only be granted on versions between those given (inclusive).
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withPermissionOnVersionBetween(R, T, PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions) {
        TestAppPermissionContext context =
                new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withPermissionOnVersionBetween(minSdkVersion, maxSdkVersion, permissions);

        return context;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are not granted to the test
     * app.
     *
     * <p>The permission will only guarantee to not be granted for calls made by the test app.
     *
     * <p>If the permissions cannot be denied an exception will be thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withoutPermission(PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withoutPermission(String... permissions) {
        TestAppPermissionContext context =
                new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withoutPermission(permissions);

        return context;
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are granted to the test
     * app.
     *
     * <p>The app op will only guarantee to be granted for calls made by the test app.
     *
     * <p>If the app op cannot be granted an exception will be thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withAppOp(APP_OP1, APP_OP2) {
     * // Code which needs the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withAppOp(String... appOps) {
        TestAppPermissionContext context =
                new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withAppOp(appOps);

        return context;
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are granted to the test
     * app.
     *
     * <p>The app op will only guarantee to be granted for calls made by the test app.
     *
     * <p>If the app op cannot be granted an exception will be thrown.
     *
     * <p>The app op will only be granted on the version given
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withAppOpOnVersion(R, APP_OP1, APP_OP2) {
     * // Code which needs the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withAppOpOnVersion(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, sdkVersion, appOps);
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are granted to the test
     * app.
     *
     * <p>The app op will only guarantee to be granted for calls made by the test app.
     *
     * <p>If the app op cannot be granted an exception will be thrown.
     *
     * <p>The app op will only be granted on the version given and above
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withAppOpOnVersionAtLeast(R, APP_OP1, APP_OP2) {
     * // Code which needs the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withAppOpOnVersionAtLeast(int minSdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(minSdkVersion, Versions.ANY, appOps);
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are granted to the test
     * app.
     *
     * <p>The app op will only guarantee to be granted for calls made by the test app.
     *
     * <p>If the app op cannot be granted an exception will be thrown.
     *
     * <p>The app op will only be granted on the version given and below
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withAppOpOnVersionAtMost(S, APP_OP1, APP_OP2) {
     * // Code which needs the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withAppOpOnVersionAtMost(int maxSdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(Versions.ANY, maxSdkVersion, appOps);
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are granted to the test
     * app.
     *
     * <p>The app op will only guarantee to be granted for calls made by the test app.
     *
     * <p>If the app op cannot be granted an exception will be thrown.
     *
     * <p>The app op will only be granted on versions between the min and max (inclusive)
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withAppOpOnVersionBetween(R, S, APP_OP1, APP_OP2) {
     * // Code which needs the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps) {
        TestAppPermissionContext context =
                new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withAppOpOnVersionBetween(minSdkVersion, maxSdkVersion, appOps);

        return context;
    }

    /**
     * Enter a {@link PermissionContext} where the given app op are not granted to the test
     * app.
     *
     * <p>The app op will only guarantee to not be granted for calls made by the test app.
     *
     * <p>If the app op cannot be denied an exception will be thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = testApp.permissions()
     *     .withoutAppOp(APP_OP1, APP_OP2) {
     * // Code which needs to not have the app op goes here
     * }
     * }
     */
    @Override
    public TestAppPermissionContext withoutAppOp(String... appOps) {
        TestAppPermissionContext context = new TestAppPermissionContext(this);
        mPermissionContexts.add(context);
        context.withoutAppOp(appOps);

        return context;
    }

    void applyPermissions() {
        Set<String> grantedPermissions = new HashSet<>();
        Set<String> deniedPermissions = new HashSet<>();
        Set<String> grantedAppOps = new HashSet<>();
        Set<String> deniedAppOps = new HashSet<>();
        synchronized (mPermissionContexts) {
            for (TestAppPermissionContext permissionContext : mPermissionContexts) {
                for (String permission : permissionContext.grantedPermissions()) {
                    grantedPermissions.add(permission);
                    deniedPermissions.remove(permission);
                }

                for (String permission : permissionContext.deniedPermissions()) {
                    grantedPermissions.remove(permission);
                    deniedPermissions.add(permission);
                }

                for (String appOp : permissionContext.grantedAppOps()) {
                    grantedAppOps.add(appOp);
                    deniedAppOps.remove(appOp);
                }

                for (String appOp : permissionContext.deniedAppOps()) {
                    grantedAppOps.remove(appOp);
                    deniedAppOps.add(appOp);
                }
            }
        }

        for (String permission : grantedPermissions) {
            mTestAppInstance.testApp().pkg().grantPermission(mTestAppInstance.user(), permission);
        }
        for (String permission : deniedPermissions) {
            mTestAppInstance.testApp().pkg().denyPermission(mTestAppInstance.user(), permission);
        }
        for (String appOp : grantedAppOps) {
            mTestAppInstance.testApp().pkg().appOps(mTestAppInstance.user()).set(
                    appOp, AppOpsMode.ALLOWED);
        }
        for (String appOp : deniedAppOps) {
            mTestAppInstance.testApp().pkg().appOps().set(appOp, AppOpsMode.IGNORED);
        }
    }

    void undoPermission(TestAppPermissionContext permissionContext) {
        mPermissionContexts.remove(permissionContext);
        applyPermissions();
    }

    void clearPermissions() {
        mPermissionContexts.clear();
        applyPermissions();
    }
}
