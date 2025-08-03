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

package com.android.modules.utils.build;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

/** Stub class to compile the linter for host execution. */
public final class SdkLevel {
    private SdkLevel() {}

    private static volatile int sSdkInt = TIRAMISU;

    /**
     * Linter only method to set the mocked SDK level for the Safety Center config code.
     *
     * <p>You must hold the class lock before calling this method. You should hold the class lock
     * for the whole duration of the lint check.
     */
    public static void setSdkInt(int sdkInt) {
        if (!Thread.holdsLock(SdkLevel.class)) {
            throw new IllegalStateException("Lock not held.");
        }
        sSdkInt = sdkInt;
    }

    /** Method used in the Safety Center config code. */
    public static boolean isAtLeastU() {
        return sSdkInt >= UPSIDE_DOWN_CAKE;
    }

    /** Method used in the Safety Center config code. */
    public static boolean isAtLeastV() {
        return sSdkInt >= VANILLA_ICE_CREAM;
    }
}
