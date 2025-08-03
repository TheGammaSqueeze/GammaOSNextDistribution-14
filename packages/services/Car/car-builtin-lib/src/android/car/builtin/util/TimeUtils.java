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

package android.car.builtin.util;

import android.annotation.SystemApi;

import java.io.PrintWriter;

/**
 * Wrapper class for {@code android.util.TimeUtils}. Check the class for API documentation.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class TimeUtils {

    /** Check {@code android.util.TimeUtils.dumpTime}. */
    public static void dumpTime(PrintWriter pw, long time) {
        android.util.TimeUtils.dumpTime(pw, time);
    }

    /** Check {@code android.util.TimeUtils.formatDuration}. */
    public static void formatDuration(long duration, PrintWriter pw) {
        android.util.TimeUtils.formatDuration(duration, pw);
    }
}
