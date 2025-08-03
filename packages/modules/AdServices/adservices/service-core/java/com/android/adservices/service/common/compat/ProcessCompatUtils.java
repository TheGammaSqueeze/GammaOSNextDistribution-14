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

package com.android.adservices.service.common.compat;

import android.os.Process;

import com.android.modules.utils.build.SdkLevel;

/**
 * Utility class that contains methods associated with {@link Process} that need to be handled in a
 * backward-compatible manner.
 */
public final class ProcessCompatUtils {

    private ProcessCompatUtils() {
        /* cannot be instantiated */
    }

    /**
     * Returns whether the provided UID belongs to SDK sandbox process on T+. For S-, defaults to
     * false as SDK sandbox is not supported.
     */
    public static boolean isSdkSandboxUid(int uid) {
        return SdkLevel.isAtLeastT() && Process.isSdkSandboxUid(uid);
    }
}
