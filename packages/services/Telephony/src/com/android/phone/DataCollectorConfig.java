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

package com.android.phone;

import android.provider.DeviceConfig;

public final class DataCollectorConfig {
    public static final long LOGCAT_READ_TIMEOUT_MILLIS_VALUE = 500L;
    public static final long DUMPSYS_READ_TIMEOUT_MILLIS_VALUE = 100L;
    public static final long LOGCAT_PROC_TIMEOUT_MILLIS_VALUE = 500L;
    public static final long DUMPSYS_PROC_TIMEOUT_MILLIS_VALUE = 100L;
    public static final int MAX_LOGCAT_LINES_LOW_MEM_DEVICE_VALUE = 2000;
    public static final int MAX_LOGCAT_LINES_VALUE = 8000;
    private static String LOGCAT_READ_TIMEOUT_MILLIS = "logcat_read_timeout_millis";
    private static String DUMPSYS_READ_TIMEOUT_MILLIS = "dumpsys_read_timeout_millis";
    private static String LOGCAT_PROC_TIMEOUT_MILLIS = "logcat_proc_timeout_millis";
    private static String DUMPSYS_PROC_TIMEOUT_MILLIS = "dumpsys_proc_timeout_millis";
    private static String MAX_LOGCAT_LINES_LOW_MEM = "max_logcat_lines_low_mem";
    private static String MAX_LOGCAT_LINES = "max_logcat_lines";

    public static int getMaxLogcatLinesForLowMemDevice() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY,
                MAX_LOGCAT_LINES_LOW_MEM, MAX_LOGCAT_LINES_LOW_MEM_DEVICE_VALUE);
    }

    public static int getMaxLogcatLines() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY,
                MAX_LOGCAT_LINES, MAX_LOGCAT_LINES_VALUE);
    }

    public static long getLogcatReadTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                LOGCAT_READ_TIMEOUT_MILLIS, LOGCAT_READ_TIMEOUT_MILLIS_VALUE);
    }

    public static long getDumpsysReadTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                DUMPSYS_READ_TIMEOUT_MILLIS, DUMPSYS_READ_TIMEOUT_MILLIS_VALUE);
    }

    public static long getLogcatProcTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                LOGCAT_PROC_TIMEOUT_MILLIS, LOGCAT_PROC_TIMEOUT_MILLIS_VALUE);
    }

    public static long getDumpsysProcTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                DUMPSYS_PROC_TIMEOUT_MILLIS, DUMPSYS_PROC_TIMEOUT_MILLIS_VALUE);
    }

    public static class Adapter {
        public Adapter() {
        }

        public int getMaxLogcatLinesForLowMemDevice() {
            return DataCollectorConfig.getMaxLogcatLinesForLowMemDevice();
        }

        public int getMaxLogcatLines() {
            return DataCollectorConfig.getMaxLogcatLines();
        }

        public long getLogcatReadTimeoutMillis() {
            return DataCollectorConfig.getLogcatReadTimeoutMillis();
        }

        public long getDumpsysReadTimeoutMillis() {
            return DataCollectorConfig.getDumpsysReadTimeoutMillis();
        }

        public long getLogcatProcTimeoutMillis() {
            return DataCollectorConfig.getLogcatProcTimeoutMillis();
        }

        public long getDumpsysProcTimeoutMillis() {
            return DataCollectorConfig.getDumpsysProcTimeoutMillis();
        }
    }


}
