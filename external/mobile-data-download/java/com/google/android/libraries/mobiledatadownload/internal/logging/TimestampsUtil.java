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

package com.google.android.libraries.mobiledatadownload.internal.logging;

import static com.google.common.math.LongMath.checkedAdd;
import static com.google.common.math.LongMath.checkedMultiply;
import static com.google.common.math.LongMath.checkedSubtract;

import com.google.protobuf.Timestamp;

/**
 * Utilities to help create/manipulate {@code protobuf/timestamp.proto}.
 */
public class TimestampsUtil {

    // Timestamp for "0001-01-01T00:00:00Z"
    static final long TIMESTAMP_SECONDS_MIN = -62135596800L;

    // Timestamp for "9999-12-31T23:59:59Z"
    static final long TIMESTAMP_SECONDS_MAX = 253402300799L;

    static final int NANOS_PER_SECOND = 1000000000;
    static final int NANOS_PER_MILLISECOND = 1000000;
    static final int NANOS_PER_MICROSECOND = 1000;
    static final int MILLIS_PER_SECOND = 1000;
    static final int MICROS_PER_SECOND = 1000000;

    @SuppressWarnings("GoodTime") // this is a legacy conversion API
    public static long toMillis(Timestamp timestamp) {
        checkValid(timestamp);
        return checkedAdd(
                checkedMultiply(timestamp.getSeconds(), MILLIS_PER_SECOND),
                timestamp.getNanos() / NANOS_PER_MILLISECOND);
    }


    /** Create a Timestamp from the number of milliseconds elapsed from the epoch. */
    @SuppressWarnings("GoodTime") // this is a legacy conversion API
    public static Timestamp fromMillis(long milliseconds) {
        return normalizedTimestamp(
                milliseconds / MILLIS_PER_SECOND,
                (int) (milliseconds % MILLIS_PER_SECOND * NANOS_PER_MILLISECOND));
    }

    public static Timestamp checkValid(Timestamp timestamp) {
        long seconds = timestamp.getSeconds();
        int nanos = timestamp.getNanos();
        if (!isValid(seconds, nanos)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Timestamp is not valid. See proto definition for valid values. "
                                    + "Seconds (%s) must be in range [-62,135,596,800, +253,402,"
                                    + "300,799]. "
                                    + "Nanos (%s) must be in range [0, +999,999,999].",
                            seconds, nanos));
        }
        return timestamp;
    }

    /**
     * Returns true if the given number of seconds and nanos is a valid {@link Timestamp}. The
     * {@code
     * seconds} value must be in the range [-62,135,596,800, +253,402,300,799] (i.e., between
     * 0001-01-01T00:00:00Z and 9999-12-31T23:59:59Z). The {@code nanos} value must be in the range
     * [0, +999,999,999].
     *
     * <p><b>Note:</b> Negative second values with fractional seconds must still have non-negative
     * nanos values that count forward in time.
     */
    @SuppressWarnings("GoodTime") // this is a legacy conversion API
    public static boolean isValid(long seconds, int nanos) {
        if (seconds < TIMESTAMP_SECONDS_MIN || seconds > TIMESTAMP_SECONDS_MAX) {
            return false;
        }
        if (nanos < 0 || nanos >= NANOS_PER_SECOND) {
            return false;
        }
        return true;
    }

    static Timestamp normalizedTimestamp(long seconds, int nanos) {
        if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
            seconds = checkedAdd(seconds, nanos / NANOS_PER_SECOND);
            nanos = (int) (nanos % NANOS_PER_SECOND);
        }
        if (nanos < 0) {
            nanos =
                    (int)
                            (nanos
                                    + NANOS_PER_SECOND); // no overflow since nanos is negative
            // (and we're adding)
            seconds = checkedSubtract(seconds, 1);
        }
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        return checkValid(timestamp);
    }
}
