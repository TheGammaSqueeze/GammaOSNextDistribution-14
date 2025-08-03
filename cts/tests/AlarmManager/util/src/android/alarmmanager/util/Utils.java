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

package android.alarmmanager.util;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

public class Utils {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private Utils() {
        // Empty to ensure no one can instantiate it.
    }

    private static boolean isChangeEnabled(long changeId, String packageName, UserHandle user) {
        try {
            return SystemUtil.callWithShellPermissionIdentity(
                    () -> CompatChanges.isChangeEnabled(changeId, packageName, user),
                    Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                    Manifest.permission.LOG_COMPAT_CHANGE);
        } catch (Exception e) {
            throw new RuntimeException("Exception while reading compat config", e);
        }
    }

    public static void enableChange(long changeId, String packageName, int userId) {
        if (!isChangeEnabled(changeId, packageName, UserHandle.of(userId))) {
            SystemUtil.runShellCommand("am compat enable --no-kill " + changeId + " "
                    + packageName, output -> output.contains("Enabled"));
        }
    }

    public static void enableChangeForSelf(long changeId) {
        if (!CompatChanges.isChangeEnabled(changeId)) {
            SystemUtil.runShellCommand("am compat enable --no-kill " + changeId + " "
                    + sContext.getOpPackageName(), output -> output.contains("Enabled"));
        }
    }

    public static void resetChange(long changeId, String packageName) {
        SystemUtil.runShellCommand("am compat reset --no-kill " + changeId + " " + packageName);
    }

    public static int getPackageUid(String packageName) {
        try {
            return sContext.getPackageManager().getPackageUid(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
