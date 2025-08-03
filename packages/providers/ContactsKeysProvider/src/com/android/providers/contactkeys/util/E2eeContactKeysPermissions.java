/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.providers.contactkeys.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

public class E2eeContactKeysPermissions {
    private static final String TAG = "E2eeContactKeysPermissions";

    private static final boolean DEBUG = false; // DO NOT submit with true

    private E2eeContactKeysPermissions() {
    }

    public static boolean hasCallerOrSelfPermission(Context context, String permission) {
        boolean ok;

        if (Binder.getCallingPid() == Process.myPid()) {
            ok = true; // Called by self; always allow.
        } else {
            ok = context.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (DEBUG) {
            Log.d(TAG, "hasCallerOrSelfPermission: "
                    + " perm=" + permission
                    + " caller=" + Binder.getCallingPid()
                    + " self=" + Process.myPid()
                    + " ok=" + ok);
        }
        return ok;
    }

    public static void enforceCallingOrSelfPermission(Context context, String permission) {
        final boolean ok = hasCallerOrSelfPermission(context, permission);
        if (!ok) {
            throw new SecurityException(String.format("The caller must have the %s permission.",
                    permission));
        }
    }

    public static void enforceVisibility(Context context, String callerPackageName,
            String targetPackageName) {
        final PackageManager packageManager = context.getPackageManager();
        boolean visible = false;
        try {
            visible = packageManager.canPackageQuery(callerPackageName, targetPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            // visible is false
        }
        if (!visible) {
            throw new SecurityException(String.format("Package %s does not have visibility into "
                            + "package %s", callerPackageName, targetPackageName));
        }
    }
}
