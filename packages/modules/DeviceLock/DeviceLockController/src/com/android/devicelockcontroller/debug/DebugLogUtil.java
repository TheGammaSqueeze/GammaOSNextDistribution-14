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

package com.android.devicelockcontroller.debug;

import com.android.devicelockcontroller.util.LogUtil;

/**
 * Utility class to log stack trace for debugging purpose
 */
public final class DebugLogUtil {
    private DebugLogUtil() {
    }

    static <T> T logAndReturn(String tag, T result) {
        String methodName = new Throwable().getStackTrace()[1].getMethodName();
        LogUtil.d(tag, methodName + ": " + result);
        return result;
    }
}
