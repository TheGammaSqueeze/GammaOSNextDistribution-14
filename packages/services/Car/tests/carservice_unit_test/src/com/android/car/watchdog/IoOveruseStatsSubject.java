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

import static com.google.common.truth.Truth.assertAbout;

import android.annotation.Nullable;
import android.car.watchdog.IoOveruseStats;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.SimpleSubjectBuilder;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

public final class IoOveruseStatsSubject extends Subject {
    /* Boiler-plate Subject.Factory for IoOveruseStatsSubject. */
    private static final Subject.Factory<com.android.car.watchdog.IoOveruseStatsSubject,
            IoOveruseStats> IO_OVERUSE_STATS_SUBJECT_FACTORY =
            com.android.car.watchdog.IoOveruseStatsSubject::new;

    private final IoOveruseStats mActual;

    /* User-defined entry point. */
    public static IoOveruseStatsSubject assertThat(@Nullable IoOveruseStats stats) {
        return assertAbout(IO_OVERUSE_STATS_SUBJECT_FACTORY).that(stats);
    }

    public static SimpleSubjectBuilder<IoOveruseStatsSubject, IoOveruseStats> assertWithMessage(
            String format, Object... args) {
        return Truth.assertWithMessage(format, args).about(IO_OVERUSE_STATS_SUBJECT_FACTORY);
    }

    /* Static method for getting the subject factory (for use with assertAbout()). */
    public static Subject.Factory<IoOveruseStatsSubject, IoOveruseStats> ioOveruseStats() {
        return IO_OVERUSE_STATS_SUBJECT_FACTORY;
    }

    private IoOveruseStatsSubject(FailureMetadata failureMetadata,
            @Nullable IoOveruseStats subject) {
        super(failureMetadata, subject);
        this.mActual = subject;
    }

    /* User-defined test assertion SPI below this point. */
    public void isEqualTo(IoOveruseStats expected) {
        if (mActual == expected) {
            return;
        }
        check("getStartTime()").that(mActual.getStartTime()).isEqualTo(expected.getStartTime());
        check("getDurationInSeconds()").that(mActual.getDurationInSeconds())
                .isEqualTo(expected.getDurationInSeconds());
        check("getTotalOveruses()").that(mActual.getTotalOveruses())
                .isEqualTo(expected.getTotalOveruses());
        check("getTotalTimesKilled()").that(mActual.getTotalTimesKilled())
                .isEqualTo(expected.getTotalTimesKilled());
        check("getTotalBytesWritten()").that(mActual.getTotalBytesWritten())
                .isEqualTo(expected.getTotalBytesWritten());
        check("isKillableOnOveruse()").that(mActual.isKillableOnOveruse())
                .isEqualTo(expected.isKillableOnOveruse());
        Truth.assertWithMessage("getRemainingWriteBytes()")
                .about(PerStateBytesSubject.perStateBytes()).that(mActual.getRemainingWriteBytes())
                .isEqualTo(expected.getRemainingWriteBytes());
    }

    public static boolean isEquals(IoOveruseStats actual, IoOveruseStats expected) {
        if (actual == expected) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.getStartTime() == expected.getStartTime()
                && actual.getDurationInSeconds() == expected.getDurationInSeconds()
                && actual.getTotalOveruses() == expected.getTotalOveruses()
                && actual.getTotalTimesKilled() == expected.getTotalTimesKilled()
                && actual.getTotalBytesWritten() == expected.getTotalBytesWritten()
                && actual.isKillableOnOveruse() == expected.isKillableOnOveruse()
                && PerStateBytesSubject.isEquals(actual.getRemainingWriteBytes(),
                    expected.getRemainingWriteBytes());
    }
}
