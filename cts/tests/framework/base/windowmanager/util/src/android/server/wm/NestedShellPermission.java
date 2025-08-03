/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import android.app.UiAutomation;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Helper to run code that might end up with nested permission requirements (eg. TaskOrganizer).
 */
public class NestedShellPermission {
    private static NestedShellPermission sInstance;

    private int mPermissionDepth = 0;

    private NestedShellPermission() {}

    synchronized private static NestedShellPermission getInstance() {
        if (sInstance == null) {
            sInstance = new NestedShellPermission();
        }
        return sInstance;
    }

    /**
     * Similar to {@link SystemUtil#runWithShellPermissionIdentity} except it supports nesting. Use
     * this with anything that interacts with TestTaskOrganizer since async operations are common.
     */
    public static void run(Runnable action) {
        run(action, null /* permissions */);
    }

    /** Similar to {@link #run(Runnable)}, but allow to specify {@code permissions} to hold. */
    public static void run(Runnable action, @Nullable String... permissions) {
        final NestedShellPermission self = getInstance();
        final UiAutomation automan =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        synchronized (self) {
            if (0 == self.mPermissionDepth++) {
                automan.adoptShellPermissionIdentity(permissions);
            }
        }
        try {
            action.run();
        } finally {
            synchronized (self) {
                if (0 == --self.mPermissionDepth) {
                    automan.dropShellPermissionIdentity();
                }
            }
        }
    }
}
