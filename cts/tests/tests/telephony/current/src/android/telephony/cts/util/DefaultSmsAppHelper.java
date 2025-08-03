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

package android.telephony.cts.util;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Assume;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DefaultSmsAppHelper {
    private static final String TAG = "DefaultSmsAppHelper";
    public static void ensureDefaultSmsApp() {
        if (!hasTelephony() || !hasSms()) {
            return;
        }

        Context context = ApplicationProvider.getApplicationContext();
        String packageName = context.getPackageName();
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        Executor executor = context.getMainExecutor();
        UserHandle user = Process.myUserHandle();

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] success = new boolean[1];

        runWithShellPermissionIdentity(() -> {
            roleManager.addRoleHolderAsUser(
                    RoleManager.ROLE_SMS,
                    packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    user,
                    executor,
                    successful -> {
                        success[0] = successful;
                        latch.countDown();
                    });
        });

        try {
            latch.await();
            assertTrue(success[0]);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public static void stopBeingDefaultSmsApp() {
        if (!hasSms()) {
            return;
        }
        Context context = ApplicationProvider.getApplicationContext();
        String packageName = context.getPackageName();
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        Executor executor = context.getMainExecutor();
        UserHandle user = Process.myUserHandle();

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] success = new boolean[1];

        runWithShellPermissionIdentity(() -> {
            roleManager.removeRoleHolderAsUser(
                    RoleManager.ROLE_SMS,
                    packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    user,
                    executor,
                    successful -> {
                        success[0] = successful;
                        latch.countDown();
                    });
        });

        try {
            latch.await();
            assertTrue(success[0]);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * Get the default SMS application configured on the device.
     * @param context The context used for getting the default SMS application.
     */
    public static String getDefaultSmsApp(@NonNull Context context) throws Exception {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        List<String> result = ShellIdentityUtils.invokeMethodWithShellPermissions(roleManager,
                (m) -> m.getRoleHolders(RoleManager.ROLE_SMS));
        Log.d(TAG, "getDefaultSmsApp result: " + result);

        if (result.isEmpty()) {
            // No default SMS app.
            return null;
        }
        // There should only be one default sms app
        return result.get(0);
    }

    /**
     * Set the default SMS application for the device.
     * @param context The context used for setting the default SMS application.
     * @param packageName The package name of the default SMS application to be set.
     * @return {@code true} if success, {@code false} otherwise.
     */
    public static boolean setDefaultSmsApp(
            @NonNull Context context, String packageName) throws Exception {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        Boolean result;
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        if (TextUtils.isEmpty(packageName)) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(roleManager,
                    (m) -> m.clearRoleHoldersAsUser(RoleManager.ROLE_SMS,
                            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                            android.os.Process.myUserHandle(),
                            // Run on calling binder thread.
                            Runnable::run, queue::offer));
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(roleManager,
                    (m) -> m.addRoleHolderAsUser(RoleManager.ROLE_SMS, packageName, 0,
                            android.os.Process.myUserHandle(),
                            // Run on calling binder thread.
                            Runnable::run, queue::offer));
        }
        result = queue.poll(10, TimeUnit.SECONDS);
        Log.d(TAG, "setDefaultSmsApp result: " + result);
        return result;
    }

    public static void assumeTelephony() {
        Assume.assumeTrue(hasTelephony());
    }

    public static void assumeMessaging() {
        Assume.assumeTrue(hasSms());
    }

    public static boolean hasTelephony() {
        Context context = ApplicationProvider.getApplicationContext();
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public static boolean hasSms() {
        TelephonyManager telephonyManager = (TelephonyManager)
                ApplicationProvider.getApplicationContext().getSystemService(
                        Context.TELEPHONY_SERVICE);
        return telephonyManager.isSmsCapable();
    }
}
