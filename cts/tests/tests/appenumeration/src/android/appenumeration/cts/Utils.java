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

package android.appenumeration.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.users.UserReference;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;

import org.junit.Assert;

import java.io.File;
import java.util.List;

public class Utils {

    private static Context sContext;
    private static PackageManager sPm;

    interface Result {
        Bundle await() throws Exception;
    }

    interface ThrowingBiFunction<T, U, R> {
        R apply(T arg1, U arg2) throws Exception;
    }

    interface ThrowingFunction<T, R> {
        R apply(T arg1) throws Exception;
    }

    private static Context getContext() {
        if (sContext == null) {
            sContext = InstrumentationRegistry.getInstrumentation().getContext();
        }
        return sContext;
    }

    private static PackageManager getPackageManager() {
        if (sPm == null) {
            sPm = getContext().getPackageManager();
        }
        return sPm;
    }

    /**
     * Install the APK on all users.
     **/
    static void installPackage(String apkPath) {
        installPackageForUser(apkPath, null /* user */, null /* installerPackageName */);
    }

    /**
     * Install the APK on all users with specific installer.
     **/
    static void installPackage(String apkPath, String installerPackageName) {
        installPackageForUser(apkPath, null /* user */, installerPackageName);
    }

    /**
     * Install the APK on the given user.
     **/
    static void installPackageForUser(String apkPath, UserReference user) {
        installPackageForUser(apkPath, user, getContext().getPackageName());
    }

    /**
     * Install the APK on the given user with specific installer.
     **/
    private static void installPackageForUser(String apkPath, UserReference user,
            String installerPackageName) {
        assertWithMessage(apkPath).that(new File(apkPath).exists()).isTrue();
        final StringBuilder cmd = new StringBuilder("pm install ");
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        if (installerPackageName != null) {
            cmd.append("-i ").append(installerPackageName).append(" ");
        }
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString()).trim();
        assertWithMessage(result).that(result).contains("Success");
    }

    /**
     * Install the existing package for the APK on the given user.
     **/
    static void installExistPackageForUser(String apkPath, UserReference user) {
        final StringBuilder cmd = new StringBuilder("pm install-existing ");
        // If the user reference is null, it only effects on the current user.
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString());
        assertThat(result.trim()).contains("installed");
    }

    /**
     * Uninstall the package on all users.
     **/
    static void uninstallPackage(String packageName) {
        uninstallPackageForUser(packageName, null /* user */);
    }

    /**
     * Uninstall the package on the given user.
     **/
    static void uninstallPackageForUser(String packageName, UserReference user) {
        final StringBuilder cmd = new StringBuilder("pm uninstall ");
        // If the user reference is null, it effects on all users.
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append(packageName);
        runShellCommand(cmd.toString());
    }

    /**
     * Force stopping the processes of the package on all users.
     **/
    static void forceStopPackage(String packageName) {
        forceStopPackageForUser(packageName, null /* user */);
    }

    /**
     * Force stopping the processes of the package on the given user.
     **/
    static void forceStopPackageForUser(String packageName, UserReference user) {
        final StringBuilder cmd = new StringBuilder("am force-stop ");
        // If the user reference is null, it effects on all users.
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append(packageName);
        runShellCommand(cmd.toString());
    }

    /**
     * Clear app data of the given package on the given user.
     **/
    static void clearAppDataForUser(String packageName, UserReference user) {
        final StringBuilder cmd = new StringBuilder("pm clear ");
        // If the user reference is null, it only effects on the system user.
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append(packageName);
        runShellCommand(cmd.toString());
    }

    /**
     * Suspend/Unsuspend the packages on the current user.
     **/
    static void suspendPackages(boolean suspend, List<String> packages) {
        suspendPackagesForUser(suspend, packages, UserReference.of(getContext().getUser()),
                false /* extraPersistableBundle */);
    }

    /**
     * Suspend/Unsuspend the packages on the given user.
     **/
    static void suspendPackagesForUser(boolean suspend, List<String> packages,
            UserReference user, boolean extraPersistableBundle) {
        final StringBuilder cmd = new StringBuilder("pm ");
        if (suspend) {
            cmd.append("suspend").append(" ");
        } else {
            cmd.append("unsuspend").append(" ");
        }
        // If the user reference is null, it only effects on the system user.
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        if (extraPersistableBundle) {
            cmd.append("--les foo bar").append(" ");
        }
        packages.stream().forEach(p -> cmd.append(p).append(" "));
        runShellCommand(cmd.toString());
    }

    /**
     * Ensure the given package is installed; otherwise install via the APK.
     */
    static void ensurePackageIsInstalled(String packageName, String apkPath) {
        runShellCommand("pm install -R " + apkPath);
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0));
        } catch (NameNotFoundException e) {
            // Ignore
        }
        Assert.assertNotNull(packageName + " should be installed", info);
    }

    /**
     * Ensure the given package isn't install; otherwise uninstall it.
     */
    static void ensurePackageIsNotInstalled(String packageName) {
        uninstallPackage(packageName);
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0));
        } catch (NameNotFoundException e) {
            // Expected
        }
        Assert.assertNull(packageName + " shouldn't be installed", info);
    }

    /**
     * Adopt the permission identity of the shell UID only for the provided permissions.
     * @param permissions The permissions to adopt or <code>null</code> to adopt all.
     */
    static void adoptShellPermissions(@Nullable String... permissions) {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(permissions);
    }

    /**
     * Drop the shell permission identity adopted.
     */
    static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Attempt to commit everything staged in this session.
     */
    static void commitSession(int sessionId) throws Exception {
        final PackageInstaller.Session session =
                InstallUtils.openPackageInstallerSession(sessionId);
        final LocalIntentSender sender = new LocalIntentSender();
        session.commit(sender.getIntentSender());
        InstallUtils.assertStatusSuccess(sender.getResult());
    }

    /**
     * Abandon all the sessions owned by you, destroying all staged data and rendering them invalid.
     */
    static void cleanUpMySessions() {
        InstallUtils.getPackageInstaller().getMySessions().forEach(info -> {
            try {
                InstallUtils.getPackageInstaller().abandonSession(info.getSessionId());
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    /**
     * Grants access to APIs marked as {@code @TestApi}.
     */
    static void allowTestApiAccess(String pgkName)  {
        final StringBuilder cmd = new StringBuilder("am compat enable ALLOW_TEST_API_ACCESS ");
        cmd.append(pgkName);
        final String result = runShellCommand(cmd.toString()).trim();
        assertWithMessage(result).that(result).startsWith("Enabled change");
    }

    /**
     * Resets access to APIs marked as {@code @TestApi}.
     */
    static void resetTestApiAccess(String pgkName)  {
        final StringBuilder cmd = new StringBuilder("am compat reset ALLOW_TEST_API_ACCESS ");
        cmd.append(pgkName);
        final String result = runShellCommand(cmd.toString()).trim();
        assertWithMessage(result).that(result).startsWith("Reset change");
    }
}
