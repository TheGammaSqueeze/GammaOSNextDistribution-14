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

package com.android.sts.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tools for parsing uname version strings */
public final class UnameVersion {

    // assuming KBUILD_BUILD_TIMESTAMP is $(shell date)
    // "date" default format from the source code:
    //     format = "%a %b %e %H:%M:%S %Z %Y";
    // weekday, month, monthday, hms, tz, year
    private static final Map<Pattern, DateTimeFormatter[]> PATTERN_TO_FORMATTER = new HashMap<>();

    static {
        // matches:
        // #1 SMP PREEMPT Fri May 13 12:22:46 UTC 2022
        // #1 SMP PREEMPT Tue Jun 11 08:31:19 -03 2019
        // #1 SMP PREEMPT Thu Feb 7 12:05:45 GMT+2 2019
        // #1 SMP PREEMPT Mon Feb 19 14:33:34 2018
        PATTERN_TO_FORMATTER.put(
                Pattern.compile(
                        // weekday
                        "(Sun|Mon|Tue|Wed|Thu|Fri|Sat) "
                                // month
                                + "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) *"
                                // day of month
                                + "\\d{1,2} "
                                // HH:MM:SS
                                + "\\d{2}:\\d{2}:\\d{2} "
                                // (optional) timezone
                                + "(\\S+ )?"
                                // year
                                + "\\d{4,}"),
                new DateTimeFormatter[] {
                    DateTimeFormatter.ofPattern("E MMM d H:m:s z u"),
                    DateTimeFormatter.ofPattern("E MMM d H:m:s X u"),
                    DateTimeFormatter.ofPattern("E MMM d H:m:s O u"),
                    DateTimeFormatter.ofPattern("E MMM d H:m:s u"),
                    DateTimeFormatter.ofPattern("E MMM  d H:m:s z u"),
                    DateTimeFormatter.ofPattern("E MMM  d H:m:s X u"),
                    DateTimeFormatter.ofPattern("E MMM  d H:m:s O u"),
                    DateTimeFormatter.ofPattern("E MMM  d H:m:s u"),
                });

        // matches:
        // #1 SMP PREEMPT 2021-08-28 09:42:03
        PATTERN_TO_FORMATTER.put(
                Pattern.compile("\\d{4,}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}") // YYYY-MM-DD HH:MM:SS
                ,
                new DateTimeFormatter[] {
                    DateTimeFormatter.ofPattern("u-M-d H:m:s"),
                });
    }

    /**
     * @param unameVersion The output of `uname -v` or android.system.Os.uname().version. See {@link
     *     com.android.sts.common.util.UnameVersionHost UnameVersionHost} for a host-side helper.
     * @return An Optional with the LocalDate representation of the timestamp in the uname version
     *     string.
     */
    public static final Optional<LocalDate> parseBuildTimestamp(String unameVersion) {
        for (Map.Entry<Pattern, DateTimeFormatter[]> entry : PATTERN_TO_FORMATTER.entrySet()) {
            Matcher m = entry.getKey().matcher(unameVersion);
            if (m.find()) {
                DateTimeFormatter[] formatters = entry.getValue();
                for (DateTimeFormatter formatter : formatters) {
                    try {
                        return Optional.of(LocalDate.parse(m.group(), formatter));
                    } catch (DateTimeParseException e) {
                        // do nothing; try next formatter
                    }
                }
            }
        }
        // must be a non-standard kernel build version
        return Optional.empty();
    }
}
