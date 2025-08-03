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

package com.android.bedstead.nene.packages;

import static android.content.pm.ApplicationInfo.FLAG_STOPPED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.content.pm.PermissionInfo.PROTECTION_DANGEROUS;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Process.myUid;

import static com.android.bedstead.nene.permissions.CommonPermissions.CHANGE_COMPONENT_ENABLED_STATE;
import static com.android.bedstead.nene.permissions.CommonPermissions.FORCE_STOP_PACKAGES;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ALL_PACKAGES;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.UiAutomation;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.appops.AppOps;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.permissions.Permissions;
import com.android.bedstead.nene.roles.RoleContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Retry;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.BlockingCallback.DefaultBlockingCallback;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A representation of a package on device which may or may not exist.
 */
public final class Package {
    private static final String LOG_TAG = "PackageReference";
    private static final int PIDS_PER_USER_ID = 100000;
    private static final PackageManager sPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();
    private static final RoleManager sRoleManager = TestApis.context().instrumentedContext()
            .getSystemService(RoleManager.class);

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private final String mPackageName;

    /**
     * Constructs a new {@link Package} from the provided {@code packageName}.
     */
    public static Package of(String packageName) {
        return new Package(packageName);
    }

    Package(String packageName) {
        mPackageName = packageName;
    }

    /** Return the package's name. */
    public String packageName() {
        return mPackageName;
    }

    /**
     * Install the package on the given user.
     *
     * <p>If you wish to install a package which is not already installed on another user, see
     * {@link Packages#install(UserReference, File)}.
     */
    public Package installExisting(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        try {
            // Expected output "Package X installed for user: Y"
            ShellCommand.builderForUser(user, "cmd package install-existing")
                    .addOperand(mPackageName)
                    .validate(
                            (output) -> output.contains("installed for user"))
                    .execute();

            return this;
        } catch (AdbException e) {
            throw new NeneException("Could not install-existing package " + this, e);
        }
    }

    /**
     * Install this package on the given user, using {@link #installExisting(UserReference)} if
     * possible, otherwise installing fresh.
     */
    public Package install(UserReference user, File apkFile) {
        if (exists()) {
            return installExisting(user);
        }

        return TestApis.packages().install(user, apkFile);
    }

    /**
     * Install this package on the given user, using {@link #installExisting(UserReference)} if
     * possible, otherwise installing fresh.
     */
    public Package install(UserReference user, Supplier<File> apkFile) {
        if (exists()) {
            return installExisting(user);
        }

        return TestApis.packages().install(user, apkFile.get());
    }

    /**
     * Install this package on the given user, using {@link #installExisting(UserReference)} if
     * possible, otherwise installing fresh.
     */
    public Package installBytes(UserReference user, byte[] apkFile) {
        if (exists()) {
            return installExisting(user);
        }

        return TestApis.packages().install(user, apkFile);
    }

    /**
     * Install this package on the given user, using {@link #installExisting(UserReference)} if
     * possible, otherwise installing fresh.
     */
    public Package installBytes(UserReference user, Supplier<byte[]> apkFile) {
        if (exists()) {
            return installExisting(user);
        }

        return TestApis.packages().install(user, apkFile.get());
    }

    /**
     * Uninstall the package for all users.
     */
    public Package uninstallFromAllUsers() {
        for (UserReference user : installedOnUsers()) {
            uninstall(user);
        }

        return this;
    }

    /**
     * Uninstall the package for the given user.
     *
     * <p>If the package is not installed for the given user, nothing will happen.
     */
    public Package uninstall(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        IntentFilter packageRemovedIntentFilter =
                new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        packageRemovedIntentFilter.addDataScheme("package");

        // This is outside of the try because we don't want to await if the package isn't installed
        BlockingBroadcastReceiver broadcastReceiver = BlockingBroadcastReceiver.create(
                TestApis.context().androidContextAsUser(user),
                packageRemovedIntentFilter);

        try {

            boolean canWaitForBroadcast = false;
            if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.R)) {
                try (PermissionContext p = TestApis.permissions().withPermission(
                        INTERACT_ACROSS_USERS_FULL)) {
                    broadcastReceiver.register();
                }
                canWaitForBroadcast = true;
            } else if (user.equals(TestApis.users().instrumented())) {
                broadcastReceiver.register();
                canWaitForBroadcast = true;
            }

            String commandOutput = Poll.forValue(() -> {
                // Expected output "Success"
                return ShellCommand.builderForUser(user, "pm uninstall")
                        .addOperand(mPackageName)
                        .execute();
            }).toMeet(output -> output.toUpperCase().startsWith("SUCCESS")
                    || output.toUpperCase().contains("NOT INSTALLED FOR"))
                    .terminalValue((output) -> {
                        if (output.contains("DELETE_FAILED_DEVICE_POLICY_MANAGER")) {
                            // A recently-removed device policy manager can't be removed - but won't
                            // show as DPC

                            DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                            if (deviceOwner != null && deviceOwner.pkg().equals(this)) {
                                // Terminal, can't remove actual DO
                                return true;
                            }
                            ProfileOwner profileOwner =
                                    TestApis.devicePolicy().getProfileOwner(user);
                            // Terminal, can't remove actual PO
                            return profileOwner != null && profileOwner.pkg().equals(this);

                            // Not PO or DO, likely temporary failure
                        }

                        return true;
                    })
                    .errorOnFail()
                    .await();

            if (commandOutput.toUpperCase().startsWith("SUCCESS")) {
                if (canWaitForBroadcast) {
                    broadcastReceiver.awaitForBroadcastOrFail();
                } else {
                    try {
                        // On versions prior to R - cross user installs can't block for broadcasts
                        // so we have an arbitrary sleep
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "Interrupted waiting for package uninstallation", e);
                    }
                }
            }
            return this;
        } catch (NeneException e) {
            throw new NeneException("Could not uninstall package " + this, e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }
    }

    /**
     * Enable this package for the given {@link UserReference}.
     */
    @Experimental
    public Package enable(UserReference user) {
        try {
            ShellCommand.builderForUser(user, "pm enable")
                    .addOperand(mPackageName)
                    .validate(o -> o.contains("new state"))
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error enabling package " + this + " for user " + user, e);
        }
        return this;
    }

    /**
     * Enable this package on the instrumented user.
     */
    @Experimental
    public Package enable() {
        return enable(TestApis.users().instrumented());
    }

    /**
     * Disable this package for the given {@link UserReference}.
     */
    @Experimental
    public Package disable(UserReference user) {
        try {
            // TODO(279387509): "pm disable" is currently broken for packages - restore to normal
            //  disable when fixed
            ShellCommand.builderForUser(user, "pm disable-user")
                    .addOperand(mPackageName)
                    .validate(o -> o.contains("new state"))
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error disabling package " + this + " for user " + user, e);
        }
        return this;
    }

    /**
     * Disable this package on the instrumented user.
     */
    @Experimental
    public Package disable() {
        return disable(TestApis.users().instrumented());
    }

    /**
     * Get a reference to the given {@code componentName} within this package.
     *
     * <p>This does not guarantee that the component exists.
     */
    @Experimental
    public ComponentReference component(String componentName) {
        return new ComponentReference(this, componentName);
    }

    /**
     * Grant a permission for the package on the given user.
     *
     * <p>The package must be installed on the user, must request the given permission, and the
     * permission must be a runtime permission.
     */
    public Package grantPermission(UserReference user, String permission) {
        // There is no readable output upon failure so we need to check ourselves
        checkCanGrantOrRevokePermission(user, permission);

        try {
            boolean shouldRunAsRoot = Tags.hasTag(Tags.ADB_ROOT);

            ShellCommand.builderForUser(user, "pm grant")
                    .asRoot(shouldRunAsRoot)
                    .addOperand(packageName())
                    .addOperand(permission)
                    .allowEmptyOutput(true)
                    .validate(String::isEmpty)
                    .execute();

            String message = "Error granting permission " + permission
                    + " to package " + this + " on user " + user
                    + ". Command appeared successful but not set."
                    + (shouldRunAsRoot ? "" : " If this test requires permissions that can only "
                    + "be granted on devices where adb has root. Add @RequireAdbRoot to the test.");
            assertWithMessage(message).that(hasPermission(user, permission)).isTrue();

            return this;
        } catch (AdbException e) {
            throw new NeneException("Error granting permission " + permission + " to package "
                    + this + " on user " + user, e);
        }
    }

    /** Grant the {@code permission} on the instrumented user. */
    public Package grantPermission(String permission) {
        return grantPermission(TestApis.users().instrumented(), permission);
    }

    /** Deny the {@code permission} on the instrumented user. */
    public Package denyPermission(String permission) {
        return denyPermission(TestApis.users().instrumented(), permission);
    }

    /**
     * Deny a permission for the package on the given user.
     *
     * <p>The package must be installed on the user, must request the given permission, and the
     * permission must be a runtime permission.
     *
     * <p>You can not deny permissions for the current package on the current user.
     */
    public Package denyPermission(UserReference user, String permission) {
        if (!hasPermission(user, permission)) {
            return this; // Already denied
        }

        // There is no readable output upon failure so we need to check ourselves
        checkCanGrantOrRevokePermission(user, permission);

        if (packageName().equals(TestApis.context().instrumentedContext().getPackageName())
                && user.equals(TestApis.users().instrumented())) {
            throw new NeneException("Cannot deny permission from current package");
        }

        boolean shouldRunAsRoot = Tags.hasTag(Tags.ADB_ROOT);

        sUiAutomation.revokeRuntimePermission(packageName(), permission);

        String message = "Error denying permission " + permission
                + " to package " + this + " on user " + user
                + ". Command appeared successful but not revoked."
                + (shouldRunAsRoot ? "" : " If this test requires permissions that can only be "
                + "granted on devices where adb has root. Add @RequireAdbRoot to the test.");
        assertWithMessage(message).that(hasPermission(user, permission)).isFalse();

        return this;
    }

    void checkCanGrantOrRevokePermission(UserReference user, String permission) {
        if (!installedOnUser(user)) {
            throw new NeneException("Attempting to grant " + permission + " to " + this
                    + " on user " + user + ". But it is not installed");
        }

        if (Tags.hasTag(Tags.ADB_ROOT)) {
            // If the test is being run as root, every permission can be granted or revoked.
            Log.i(LOG_TAG,
                    "checkCanGrantOrRevokePermission skipped as test is being run as root");
            return;
        }

        try {
            PermissionInfo permissionInfo =
                    sPackageManager.getPermissionInfo(permission, /* flags= */ 0);

            if (!protectionIsDangerous(permissionInfo.protectionLevel)
                    && !protectionIsDevelopment(permissionInfo.protectionLevel)) {
                throw new NeneException("Cannot grant non-runtime permission "
                        + permission + ", protection level is " + permissionInfo.protectionLevel +
                        ". To restrict this test to only running on debug devices where this "
                        + "permission is available, add @RequireAdbRoot to the test.");
            }

            if (!requestedPermissions().contains(permission)) {
                throw new NeneException("Cannot grant permission "
                        + permission + " which was not requested by package " + packageName());
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new NeneException("Permission does not exist: " + permission);
        }
    }

    private boolean protectionIsDangerous(int protectionLevel) {
        return (protectionLevel & PROTECTION_DANGEROUS) != 0;
    }

    private boolean protectionIsDevelopment(int protectionLevel) {
        return (protectionLevel & PROTECTION_FLAG_DEVELOPMENT) != 0;
    }

    /** Get running {@link ProcessReference} for this package on all users. */
    @Experimental
    public Set<ProcessReference> runningProcesses() {
        // TODO(scottjonathan): See if this can be remade using
        //  ActivityManager#getRunningappProcesses
        try {
            return ShellCommand.builder("ps")
                    .addOperand("-A")
                    .addOperand("-n")
                    .executeAndParseOutput(o -> parsePsOutput(o).stream()
                            .filter(p -> p.mPackageName.equals(mPackageName))
                            .map(p -> new ProcessReference(this, p.mPid, p.mUid,
                                    TestApis.users().find(p.mUserId))))
                    .collect(Collectors.toSet());
        } catch (AdbException e) {
            throw new NeneException("Error getting running processes ", e);
        }
    }

    private Set<ProcessInfo> parsePsOutput(String psOutput) {
        return Arrays.stream(psOutput.split("\n"))
                .skip(1) // Skip the title line
                .map(s -> s.split("\\s+"))
                .map(m -> new ProcessInfo(
                        m[8], Integer.parseInt(m[1]),
                        Integer.parseInt(m[0]),
                        Integer.parseInt(m[0]) / PIDS_PER_USER_ID))
                .collect(Collectors.toSet());
    }

    /** Get the running {@link ProcessReference} for this package on the given user. */
    @Experimental
    @Nullable
    public ProcessReference runningProcess(UserReference user) {
        ProcessReference p = runningProcesses().stream().filter(
                i -> i.user().equals(user))
                .findAny()
                .orElse(null);
        return p;
    }

    /** Get the running {@link ProcessReference} for this package on the given user. */
    @Experimental
    @Nullable
    public ProcessReference runningProcess(UserHandle user) {
        return runningProcess(TestApis.users().find(user));
    }

    /** Get the running {@link ProcessReference} for this package on the instrumented user. */
    @Experimental
    @Nullable
    public ProcessReference runningProcess() {
        return runningProcess(TestApis.users().instrumented());
    }

    /** {@code true} if the package is installed on the given user. */
    public boolean installedOnUser(UserHandle userHandle) {
        return installedOnUser(TestApis.users().find(userHandle));
    }

    /** {@code true} if the package is installed on the given user. */
    public boolean installedOnUser(UserReference user) {
        return packageInfoForUser(user, /* flags= */ 0) != null;
    }

    /** {@code true} if the package is installed on the instrumented user. */
    public boolean installedOnUser() {
        return installedOnUser(TestApis.users().instrumented());
    }

    /** {@code true} if the package on the given user has the given permission. */
    public boolean hasPermission(UserReference user, String permission) {
        return TestApis.context().androidContextAsUser(user).getPackageManager()
                .checkPermission(permission, mPackageName) == PERMISSION_GRANTED;
    }

    /** {@code true} if the package on the given user has the given permission. */
    public boolean hasPermission(UserHandle user, String permission) {
        return hasPermission(TestApis.users().find(user), permission);
    }

    /** {@code true} if the package on the instrumented user has the given permission. */
    public boolean hasPermission(String permission) {
        return hasPermission(TestApis.users().instrumented(), permission);
    }

    /** Get the permissions requested in the package's manifest. */
    public Set<String> requestedPermissions() {
        if (TestApis.packages().instrumented().isInstantApp()) {
            Log.i(LOG_TAG, "Tried to get requestedPermissions for "
                    + mPackageName + " but can't on instant apps");
            return new HashSet<>();
        }

        PackageInfo packageInfo = packageInfoFromAnyUser(GET_PERMISSIONS);
        if (packageInfo == null) {
            throw new NeneException("Error getting requestedPermissions, does not exist");
        }

        if (packageInfo.requestedPermissions == null) {
            return new HashSet<>();
        }

        return new HashSet<>(Arrays.asList(packageInfo.requestedPermissions));
    }

    @Nullable
    private PackageInfo packageInfoFromAnyUser(int flags) {
        return TestApis.users().all().stream()
                .map(i -> packageInfoForUser(i, flags))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private PackageInfo packageInfoForUser(UserReference user, int flags) {
        if (TestApis.packages().instrumented().isInstantApp()
                || !Versions.meetsMinimumSdkVersionRequirement(S)) {
            // Can't call API's directly
            return packageInfoForUserPreS(user, flags);
        }

        if (user.equals(TestApis.users().instrumented())) {
            try {
                return TestApis.context().instrumentedContext()
                        .getPackageManager()
                        .getPackageInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Could not find package " + this + " on user " + user, e);
                return null;
            }
        }

        if (Permissions.sIgnorePermissions.get()) {
            try {
                return TestApis.context().androidContextAsUser(user)
                        .getPackageManager()
                        .getPackageInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        } else {
            try (PermissionContext p = TestApis.permissions().withPermission(
                    INTERACT_ACROSS_USERS_FULL)) {
                return TestApis.context().androidContextAsUser(user)
                        .getPackageManager()
                        .getPackageInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }

    private PackageInfo packageInfoForUserPreS(UserReference user, int flags) {
        AdbPackage pkg = Packages.parseDumpsys().mPackages.get(mPackageName);

        if (pkg == null) {
            return null;
        }

        if (!pkg.installedOnUsers().contains(user)) {
            return null;
        }

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = mPackageName;
        packageInfo.requestedPermissions = pkg.requestedPermissions().toArray(new String[]{});

        return packageInfo;
    }

    @Nullable
    private ApplicationInfo applicationInfoFromAnyUser(int flags) {
        return TestApis.users().all().stream()
                .map(i -> applicationInfoForUser(i, flags))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private ApplicationInfo applicationInfoForUser(UserReference user, int flags) {
        if (user.equals(TestApis.users().instrumented())) {
            try {
                return TestApis.context().instrumentedContext()
                        .getPackageManager()
                        .getApplicationInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
            return applicationInfoForUserPreQ(user, flags);
        }

        if (Permissions.sIgnorePermissions.get()) {
            try {
                return TestApis.context().androidContextAsUser(user)
                        .getPackageManager()
                        .getApplicationInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        } else {
            try (PermissionContext p = TestApis.permissions().withPermission(
                    INTERACT_ACROSS_USERS_FULL)) {
                return TestApis.context().androidContextAsUser(user)
                        .getPackageManager()
                        .getApplicationInfo(mPackageName, /* flags= */ flags);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }

    private ApplicationInfo applicationInfoForUserPreQ(UserReference user, int flags) {
        try {
            String dumpsysOutput = ShellCommand.builder("dumpsys package").execute();

            AdbPackageParser.ParseResult r = Packages.sParser.parse(dumpsysOutput);
            AdbPackage pkg = r.mPackages.get(mPackageName);

            if (pkg == null) {
                return null;
            }

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = mPackageName;
            applicationInfo.uid = -1; // TODO: Get the actual uid...

            return applicationInfo;
        } catch (AdbException | AdbParseException e) {
            throw new NeneException("Error getting package info pre Q", e);
        }
    }

    /**
     * Get all users this package is installed on.
     *
     * <p>Note that this is an expensive operation - favor {@link #installedOnUser(UserReference)}
     * when possible.
     */
    public Set<UserReference> installedOnUsers() {
        return TestApis.users().all().stream()
                .filter(this::installedOnUser)
                .collect(Collectors.toSet());
    }

    /**
     * Force the running instance of the package to stop on the given user.
     *
     * <p>See {@link ActivityManager#forceStopPackage(String)}.
     */
    @Experimental
    public void forceStop(UserReference user) {
        try (PermissionContext p = TestApis.permissions().withPermission(FORCE_STOP_PACKAGES)) {
            ActivityManager userActivityManager =
                    TestApis.context().androidContextAsUser(user)
                            .getSystemService(ActivityManager.class);

            PackageManager userPackageManager =
                    TestApis.context().androidContextAsUser(user).getPackageManager();
            boolean shouldCheckPreviousProcess = runningProcess() != null;
            // In most cases this should work first time, however if a user restriction has been
            // recently removed we may need to retry

            int previousPid = shouldCheckPreviousProcess ? runningProcess().pid() : -1;

            Poll.forValue("Application flag", () -> {
                userActivityManager.forceStopPackage(mPackageName);

                return userPackageManager.getPackageInfo(mPackageName,
                            PackageManager.GET_META_DATA)
                            .applicationInfo.flags;
            }).toMeet(flag -> !shouldCheckPreviousProcess || (flag & FLAG_STOPPED) == FLAG_STOPPED
                            ||  previousPid != runningProcess().pid())
                    .errorOnFail("Expected application flags to contain FLAG_STOPPED ("
                            + FLAG_STOPPED + ")")
                    .await();
        }
    }

    /**
     * Force the running instance of the package to stop on the instrumented user.
     *
     * <p>See {@link ActivityManager#forceStopPackage(String)}.
     */
    @Experimental
    public void forceStop() {
        forceStop(TestApis.users().instrumented());
    }

    /**
     * Interact with AppOps on the instrumented user for the given package.
     */
    @Experimental
    public AppOps appOps() {
        return appOps(TestApis.users().instrumented());
    }

    /**
     * Interact with AppOps on the given user for the given package.
     */
    @Experimental
    public AppOps appOps(UserReference user) {
        return new AppOps(this, user);
    }

    /**
     * Get the UID of the package on the instrumented user.
     */
    @Experimental
    public int uid() {
        return uid(TestApis.users().instrumented());
    }

    /**
     * Get the UID of the package on the given {@code user}.
     */
    @Experimental
    public int uid(UserReference user) {
        if (user.equals(TestApis.users().instrumented())
                && this.equals(TestApis.packages().instrumented())) {
            return myUid();
        }

        ApplicationInfo applicationInfo = applicationInfoForUser(user, /* flags= */ 0);
        if (applicationInfo == null) {
            throw new IllegalStateException(
                    "Trying to get uid for not installed package " + this + " on user " + user);
        }

        return applicationInfo.uid;
    }

    @Override
    public int hashCode() {
        return mPackageName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Package)) {
            return false;
        }

        Package other = (Package) obj;
        return other.mPackageName.equals(mPackageName);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("PackageReference{");
        stringBuilder.append("packageName=" + mPackageName);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    /** {@code true} if the package exists on any user on the device as is not uninstalled. */
    @TargetApi(S)
    public boolean isInstalled() {
        Versions.requireMinimumVersion(S);

        try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
            return packageInfoFromAnyUser(0) != null;
        }
    }

    /** {@code true} if the package exists on the device. */
    public boolean exists() {
        if (Versions.meetsMinimumSdkVersionRequirement(S)) {
            try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                return packageInfoFromAnyUser(MATCH_UNINSTALLED_PACKAGES) != null;
            }
        }

        return Packages.parseDumpsys().mPackages.containsKey(mPackageName);
    }

    /** Get the targetSdkVersion for the package. */
    @Experimental
    public int targetSdkVersion() {
        return applicationInfoFromAnyUserOrError(/* flags= */ 0).targetSdkVersion;
    }

    /**
     * {@code true} if the package is installed in the device's system image.
     */
    @Experimental
    public boolean hasSystemFlag() {
        return (applicationInfoFromAnyUserOrError(/* flags= */ 0).flags & FLAG_SYSTEM) > 0;
    }

    @Experimental
    public boolean isInstantApp() {
        return sPackageManager.isInstantApp(mPackageName);
    }

    /** Get the AppComponentFactory for the package. */
    @Experimental
    @Nullable
    @TargetApi(P)
    public String appComponentFactory() {
        return applicationInfoFromAnyUserOrError(/* flags= */ 0).appComponentFactory;
    }

    private ApplicationInfo applicationInfoFromAnyUserOrError(int flags) {
        ApplicationInfo appInfo = applicationInfoFromAnyUser(flags);
        if (appInfo == null) {
            throw new NeneException("Package not installed: " + this);
        }
        return appInfo;
    }

    /**
     * Gets the shared user id of the package.
     */
    @Experimental
    public String sharedUserId() {
        PackageInfo packageInfo = packageInfoFromAnyUser(/* flags= */ 0);

        if (packageInfo == null) {
            throw new NeneException("Error getting sharedUserId, does not exist");
        }

        return packageInfo.sharedUserId;
    }

    /**
     * See {@link PackageManager#setSyntheticAppDetailsActivityEnabled(String, boolean)}.
     */
    @Experimental
    public void setSyntheticAppDetailsActivityEnabled(UserReference user, boolean enabled) {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(CHANGE_COMPONENT_ENABLED_STATE)) {
            TestApis.context().androidContextAsUser(user).getPackageManager()
                    .setSyntheticAppDetailsActivityEnabled(packageName(), enabled);
        }
    }

    /**
     * See {@link PackageManager#setSyntheticAppDetailsActivityEnabled(String, boolean)}.
     */
    @Experimental
    public void setSyntheticAppDetailsActivityEnabled(boolean enabled) {
        setSyntheticAppDetailsActivityEnabled(TestApis.users().instrumented(), enabled);
    }

    /**
     * See {@link PackageManager#getSyntheticAppDetailsActivityEnabled(String)}.
     */
    @Experimental
    public boolean syntheticAppDetailsActivityEnabled(UserReference user) {
        return TestApis.context().androidContextAsUser(user).getPackageManager()
                .getSyntheticAppDetailsActivityEnabled(packageName());
    }

    /**
     * See {@link PackageManager#getSyntheticAppDetailsActivityEnabled(String)}.
     */
    @Experimental
    public boolean syntheticAppDetailsActivityEnabled() {
        return syntheticAppDetailsActivityEnabled(TestApis.users().instrumented());
    }

    private static final class ProcessInfo {
        final String mPackageName;
        final int mPid;
        final int mUid;
        final int mUserId;

        ProcessInfo(String packageName, int pid, int uid, int userId) {
            if (packageName == null) {
                throw new NullPointerException();
            }
            mPackageName = packageName;
            mPid = pid;
            mUid = uid;
            mUserId = userId;
        }

        @Override
        public String toString() {
            return "ProcessInfo{packageName=" + mPackageName + ", pid="
                    + mPid + ", uid=" + mUid + ", userId=" + mUserId + "}";
        }
    }

    /**
     * Set this package as filling the given role on the instrumented user.
     */
    @Experimental
    public RoleContext setAsRoleHolder(String role) {
        return setAsRoleHolder(role, TestApis.users().instrumented());
    }

    /**
     * Set this package as filling the given role.
     */
    @Experimental
    public RoleContext setAsRoleHolder(String role, UserReference user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS, INTERACT_ACROSS_USERS_FULL)) {

            Retry.logic(() -> {
                TestApis.logcat().clear();
                DefaultBlockingCallback<Boolean> blockingCallback = new DefaultBlockingCallback<>();

                sRoleManager.addRoleHolderAsUser(
                        role,
                        mPackageName,
                        /* flags= */ 0,
                        user.userHandle(),
                        TestApis.context().instrumentedContext().getMainExecutor(),
                        blockingCallback::triggerCallback);

                boolean success = blockingCallback.await();
                if (!success) {
                    fail("Could not set role holder of " + role + "." + " Relevant logcat: "
                            + TestApis.logcat().dump((line) -> line.contains(role) || line.contains("Role")));
                }
                if (!TestApis.roles().getRoleHoldersAsUser(role, user).contains(packageName())) {
                    fail("addRoleHolderAsUser returned true but did not add role holder. "
                            + "Relevant logcat: " + TestApis.logcat().dump(
                                    (line) -> line.contains(role) || line.contains("Role")));
                }
            }).terminalException(e -> {
                // Terminal unless we see logcat output indicating it might be temporary
                var logcat = TestApis.logcat()
                        .dump(l -> l.contains("Error calling onAddRoleHolder()"));
                if (!logcat.isEmpty()) {
                    // On low end devices - this can happen when the broadcast queue is full
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException ex) {
                        return true;
                    }

                    return false;
                }

                return true;
            }).runAndWrapException();

            return new RoleContext(role, this, user);
        }
    }

    /**
     * Remove this package from the given role on the instrumented user.
     */
    @Experimental
    public void removeAsRoleHolder(String role) {
        removeAsRoleHolder(role, TestApis.users().instrumented());
    }

    /**
     * Remove this package from the given role.
     */
    @Experimental
    public void removeAsRoleHolder(String role, UserReference user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS)) {
            Retry.logic(() -> {
                TestApis.logcat().clear();
                DefaultBlockingCallback<Boolean> blockingCallback = new DefaultBlockingCallback<>();
                sRoleManager.removeRoleHolderAsUser(
                        role,
                        mPackageName,
                        /* flags= */ 0,
                        user.userHandle(),
                        TestApis.context().instrumentedContext().getMainExecutor(),
                        blockingCallback::triggerCallback);
                TestApis.roles().setBypassingRoleQualification(false);

                boolean success = blockingCallback.await();
                if (!success) {
                    fail("Failed to clear the role holder of "
                            + role + ".");
                }
                if (TestApis.roles().getRoleHoldersAsUser(role, user).contains(packageName())) {
                    fail("removeRoleHolderAsUser returned true but did not remove role holder. "
                            + "Relevant logcat: " + TestApis.logcat().dump(
                                    (line) -> line.contains(role)));
                }
            }).terminalException(e -> {
                // Terminal unless we see logcat output indicating it might be temporary
                var logcat = TestApis.logcat()
                        .dump(l -> l.contains("Error calling onRemoveRoleHolder()"));
                if (!logcat.isEmpty()) {
                    // On low end devices - this can happen when the broadcast queue is full
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException ex) {
                        return true;
                    }

                    return false;
                }

                return true;
            }).runAndWrapException();
        }
    }

    /**
     * True if the given package on the instrumented user can have its ability to interact across
     * profiles configured by the user.
     */
    @Experimental
    public boolean canConfigureInteractAcrossProfiles() {
        return canConfigureInteractAcrossProfiles(TestApis.users().instrumented());
    }

    /**
     * True if the given package can have its ability to interact across profiles configured
     * by the user.
     */
    @Experimental
    public boolean canConfigureInteractAcrossProfiles(UserReference user) {
        return TestApis.context().androidContextAsUser(user)
                .getSystemService(CrossProfileApps.class)
                .canConfigureInteractAcrossProfiles(packageName());
    }

    /**
     * Enable or disable this package from using @TestApis.
     */
    @Experimental
    public void setAllowTestApiAccess(boolean allowed) {
        ShellCommand.builder("am compat")
                .addOperand(allowed ? "enable" : "disable")
                .addOperand("ALLOW_TEST_API_ACCESS")
                .addOperand(packageName())
                .validate(s -> s.startsWith(allowed ? "Enabled change" : "Disabled change"))
                .executeOrThrowNeneException(
                        "Error allowing/disallowing test api access for " + this);
    }

    /**
     * True if the given package is suspended in the given user.
     */
    @Experimental
    public boolean isSuspended(UserReference user) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return TestApis.context().androidContextAsUser(user).getPackageManager()
                    .isPackageSuspended(mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            throw new NeneException("Package " + mPackageName + " not found for user " + user);
        }
    }

    /**
     * Get the app standby bucket of the package.
     */
    @Experimental
    public int getAppStandbyBucket() {
        return getAppStandbyBucket(TestApis.users().instrumented());
    }

    /**
     * Get the app standby bucket of the package.
     */
    @Experimental
    public int getAppStandbyBucket(UserReference user) {
        try {
            return ShellCommand.builderForUser(user, "am get-standby-bucket")
                .addOperand(mPackageName)
                .executeAndParseOutput(o -> Integer.parseInt(o.trim()));
        } catch (AdbException e) {
            throw new NeneException("Could not get app standby bucket " + this, e);
        }
    }

    /** Approves all links for an auto verifiable app */
    @Experimental
    public void setAppLinksToAllApproved() {
        try {
            ShellCommand.builder("pm set-app-links")
                    .addOption("--package", this.mPackageName)
                    .addOperand(2) // 2 = STATE_APPROVED
                    .addOperand("all")
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error verifying links ", e);
        }
    }

    /** Checks if the current package is a role holder for the given role*/
    @Experimental
    public boolean isRoleHolder(String role) {
        return TestApis.roles().getRoleHolders(role).contains(this.mPackageName);
    }

    @Experimental
    public void clearStorage() {
        ShellCommand.builder("pm clear")
                .addOperand(mPackageName)
                .validate(ShellCommandUtils::startsWithSuccess)
                .executeOrThrowNeneException("Error clearing storage for " + this);
    }
}
