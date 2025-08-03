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

package android.gamemanager.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

public final class TestUtil {

    // When an app is installed, some propagation work of the configuration will
    // be set up asynchronously, hence it is recommended to put the thread into sleep
    // to wait for the propagation finishes for a few hundred milliseconds.
    public static void installPackage(@NonNull String apkPath) {
        assertEquals("Success", runShellCommand("pm install --force-queryable -t " + apkPath));
    }

    public static void uninstallPackage(@NonNull String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }
}
