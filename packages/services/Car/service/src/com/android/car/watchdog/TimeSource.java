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

package com.android.car.watchdog;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Provider for the current value of "now" for users of {@code java.time}.
 */
public abstract class TimeSource {
    public static final ZoneOffset ZONE_OFFSET = ZoneOffset.UTC;

    /** Returns the current instant from the time source implementation. */
    public abstract Instant now();

    /** Returns the current date time for the UTC timezone. */
    public ZonedDateTime getCurrentDateTime() {
        return now().atZone(ZONE_OFFSET);
    }

    /** Returns the current date for the UTC timezone without time. */
    public ZonedDateTime getCurrentDate() {
        return now().atZone(ZONE_OFFSET).truncatedTo(ChronoUnit.DAYS);
    }
}
