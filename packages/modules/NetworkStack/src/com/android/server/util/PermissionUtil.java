/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.util;

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Binder.getCallingPid;
import static android.os.Binder.getCallingUid;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to check calling permissions on the network stack.
 */
public final class PermissionUtil {
    private static final AtomicInteger sSystemPid = new AtomicInteger(-1);

    private static volatile int sTestUid = Process.INVALID_UID;

    /**
     * Check that the caller is allowed to communicate with the network stack.
     * @throws SecurityException The caller is not allowed to communicate with the network stack.
     */
    public static void enforceNetworkStackCallingPermission() {
        final int caller = getCallingUid();
        if (caller == Process.SYSTEM_UID) {
            checkConsistentSystemPid();
            return;
        }

        if (caller != Process.myUid() // apps with NETWORK_STACK_UID
                && UserHandle.getAppId(caller) != Process.BLUETOOTH_UID
                && !isTestUid(caller)) {
            throw new SecurityException("Invalid caller: " + caller);
        }
    }

    private static void checkConsistentSystemPid() {
        // Apart from the system server process, no process with a system UID should try to
        // communicate with the network stack. This is to ensure that the network stack does not
        // need to maintain behavior for clients it was not designed to work with.
        // Checking that all calls from a system UID originate from the same PID loosely enforces
        // this restriction as if another system process calls the network stack first, the system
        // server would lose access to the network stack and cause obvious failures. If the system
        // server calls the network stack first, other clients would lose access as expected.
        final int systemPid = getCallingPid();
        if (sSystemPid.compareAndSet(-1, systemPid)) {
            // sSystemPid was unset (-1): this was the first call
            return;
        }

        if (sSystemPid.get() != systemPid) {
            throw new SecurityException("Invalid PID for the system server, expected "
                    + sSystemPid.get() + " but was called from " + systemPid);
        }
    }

    private static boolean isTestUid(int uid) {
        return uid == sTestUid;
    }

    /**
     * Set a test uid that is allowed to call the NetworkStack. Pass in -1 to reset.
     *
     * <p>The UID must have a package with NETWORK_SETTINGS permissions when it is allowed.
     */
    public static void setTestUid(Context context, int uid) {
        if (!isDebuggableBuild()) {
            throw new SecurityException("Cannot set test UID on non-debuggable builds");
        }
        if (getCallingUid() != Process.ROOT_UID) {
            throw new SecurityException("Only root can set the test UID");
        }

        if (uid == Process.INVALID_UID) {
            sTestUid = uid;
            return;
        }

        final PackageManager pm = context.getPackageManager();
        final String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) {
            throw new SecurityException("No package in uid " + uid);
        }
        final boolean hasPermission = Arrays.stream(packages).anyMatch(
                p -> pm.checkPermission(NETWORK_SETTINGS, p) == PERMISSION_GRANTED);
        if (!hasPermission) {
            throw new SecurityException(
                    "The uid must have a package with NETWORK_SETTINGS permissions");
        }
        sTestUid = uid;
    }

    /**
     * Check that the caller is allowed to dump the network stack, e.g. dumpsys.
     * @throws SecurityException The caller is not allowed to dump the network stack.
     */
    public static void checkDumpPermission() {
        final int caller = getCallingUid();
        if (caller != Process.myUid() && caller != Process.SYSTEM_UID && caller != Process.ROOT_UID
                && caller != Process.SHELL_UID) {
            throw new SecurityException("No dump permissions for caller: " + caller);
        }
    }

    /**
     * @see android.os.Build.IS_DEBUGGABLE
     */
    public static boolean isDebuggableBuild() {
        // TODO: consider adding Build.IS_DEBUGGABLE to @SystemApi
        return SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    private PermissionUtil() {
        throw new UnsupportedOperationException("This class is not to be instantiated");
    }
}
