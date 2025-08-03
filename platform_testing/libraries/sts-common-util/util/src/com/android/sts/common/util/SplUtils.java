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

package com.android.sts.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Tools for Security Patch Levels and LocalDates representing them. */
public final class SplUtils {
    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum Type {
        PARTIAL(1), // platform
        COMPLETE(5); // device-specific (kernel, soc, etc)

        public final int day;

        Type(int day) {
            this.day = day;
        }
    }

    public static LocalDate localDateFromMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(UTC_ZONE_ID).toLocalDate();
    }

    public static LocalDate localDateFromSplString(String spl) {
        return LocalDate.parse(spl, formatter);
    }

    public static String format(LocalDate date) {
        return date.format(formatter);
    }
}
