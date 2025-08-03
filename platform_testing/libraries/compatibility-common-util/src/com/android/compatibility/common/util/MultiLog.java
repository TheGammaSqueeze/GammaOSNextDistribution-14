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

package com.android.compatibility.common.util;

/** Provide an interface for logging on host+device-common code. */
public interface MultiLog {

    /**
     * Log information with whichever logging mechanism is available to the instance. This varies
     * from host-side to device-side, so implementations are left to subclasses.
     * Host: MultiLogHost
     * Device: MultiLogDevice
     * See {@link String.format(String, Object...)} for parameter information.
     */
    public void logInfo(String logTag, String format, Object... args);

    /**
     * Log debugging information to the host or device logs (depending on implementation).
     * See {@link String.format(String, Object...)} for parameter information.
     * Host: MultiLogHost
     * Device: MultiLogDevice
     */
    public void logDebug(String logTag, String format, Object... args);

    /**
     * Log warnings to the host or device logs (depending on implementation).
     * See {@link String.format(String, Object...)} for parameter information.
     * Host: MultiLogHost
     * Device: MultiLogDevice
     */
    public void logWarn(String logTag, String format, Object... args);

    /**
     * Log errors to the host or device logs (depending on implementation).
     * See {@link String.format(String, Object...)} for parameter information.
     * Host: MultiLogHost
     * Device: MultiLogDevice
     */
    public void logError(String logTag, String format, Object... args);
}
