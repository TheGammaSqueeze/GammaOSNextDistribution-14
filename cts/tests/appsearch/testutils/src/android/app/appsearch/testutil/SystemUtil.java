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

package android.app.appsearch.testutil;

import android.annotation.NonNull;
import android.app.UiAutomation;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Class to hold utilities to run {@link Runnable} with shell permission identity.
 *
 * <p>This is basically same as {@link com.android.compatibility.common.util.SystemUtil}, but we
 * have the same functionality here, so it can be used by AppSearch in g3 as well.
 */
public final class SystemUtil {
    private SystemUtil() {}

    /** Runs a {@link ThrowingRunnable} adopting a subset of Shell's permissions. */
    public static void runWithShellPermissionIdentity(
            @NonNull ThrowingRunnable runnable, String... permissions) {
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        runWithShellPermissionIdentity(automan, runnable, permissions);
    }

    /**
     * Runs a {@link ThrowingRunnable} adopting Shell's permissions, where you can specify the
     * uiAutomation used.
     *
     * @param automan UIAutomation to use.
     * @param runnable The code to run with Shell's identity.
     * @param permissions A subset of Shell's permissions.
     *                    Passing {@code null} will use all available permissions.
     */
    public static void runWithShellPermissionIdentity(
            @NonNull UiAutomation automan,
            @NonNull ThrowingRunnable runnable,
            String... permissions) {
        automan.adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }
}
