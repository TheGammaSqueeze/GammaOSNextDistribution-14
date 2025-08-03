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

package android.multiuser.cts;

import android.app.Instrumentation;

public class PermissionHelper implements AutoCloseable {
    private final Instrumentation mInstrumentation;

    private PermissionHelper(Instrumentation instrumentation, String... permissions) {
        if (instrumentation == null) {
            throw new IllegalArgumentException("instrumentation must not be null");
        }
        mInstrumentation = instrumentation;
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(permissions);
    }

    @Override
    public void close() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    public static PermissionHelper adoptShellPermissionIdentity(
            Instrumentation instrumentation, String... permissions) {
        return new PermissionHelper(instrumentation, permissions);
    }
}
