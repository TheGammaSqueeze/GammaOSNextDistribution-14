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
import android.automotive.watchdog.IoOveruseStats;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;

public final class IoUsageStatsEntrySubject extends Subject {
    /* Boiler-plate Subject.Factory for IoUsageStatsEntrySubject. */
    private static final Subject.Factory<
            com.android.car.watchdog.IoUsageStatsEntrySubject,
            Iterable<WatchdogStorage.IoUsageStatsEntry>> IO_OVERUSE_STATS_ENTRY_SUBJECT_FACTORY =
            com.android.car.watchdog.IoUsageStatsEntrySubject::new;
    private static final String NULL_ENTRY_STRING = "{NULL}";

    private final Iterable<WatchdogStorage.IoUsageStatsEntry> mActual;
    private String mMessagePrefix;

    /* User-defined entry point. */
    public static IoUsageStatsEntrySubject assertThat(
            @Nullable Iterable<WatchdogStorage.IoUsageStatsEntry> stats) {
        return assertAbout(IO_OVERUSE_STATS_ENTRY_SUBJECT_FACTORY).that(stats);
    }

    public static IoUsageStatsEntrySubject assertWithMessage(
            @Nullable Iterable<WatchdogStorage.IoUsageStatsEntry> stats,
            String format, Object... args) {
        IoUsageStatsEntrySubject subject =
                Truth.assertAbout(IO_OVERUSE_STATS_ENTRY_SUBJECT_FACTORY).that(stats);

        subject.mMessagePrefix = String.format(format + ": ", args);
        return subject;
    }

    public static Subject.Factory<IoUsageStatsEntrySubject,
            Iterable<WatchdogStorage.IoUsageStatsEntry>> ioUsageStatsEntries() {
        return IO_OVERUSE_STATS_ENTRY_SUBJECT_FACTORY;
    }

    public void containsExactly(WatchdogStorage.IoUsageStatsEntry... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<WatchdogStorage.IoUsageStatsEntry> expected) {
        Truth.assertWithMessage("%sExpected stats(%s) equals to actual stats(%s)",
                mMessagePrefix != null ? mMessagePrefix : "", toString(expected),
                toString(mActual)).that(mActual).comparingElementsUsing(Correspondence.from(
                        IoUsageStatsEntrySubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(WatchdogStorage.IoUsageStatsEntry actual,
            WatchdogStorage.IoUsageStatsEntry expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.userId == expected.userId && actual.packageName.equals(expected.packageName)
                && actual.ioUsage.getTotalTimesKilled() == expected.ioUsage.getTotalTimesKilled()
                && actual.ioUsage.getForgivenOveruses() == expected.ioUsage.getForgivenOveruses()
                && InternalPerStateBytesSubject.isEquals(actual.ioUsage.getForgivenWriteBytes(),
                expected.ioUsage.getForgivenWriteBytes())
                && isEqualsIoOveruseStats(actual.ioUsage.getInternalIoOveruseStats(),
                expected.ioUsage.getInternalIoOveruseStats());
    }

    private static boolean isEqualsIoOveruseStats(android.automotive.watchdog.IoOveruseStats actual,
            android.automotive.watchdog.IoOveruseStats expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.killableOnOveruse == expected.killableOnOveruse
                && InternalPerStateBytesSubject.isEquals(
                        actual.remainingWriteBytes, expected.remainingWriteBytes)
                && actual.startTime == expected.startTime
                && actual.durationInSeconds == expected.durationInSeconds
                && InternalPerStateBytesSubject.isEquals(actual.writtenBytes, expected.writtenBytes)
                && actual.totalOveruses == expected.totalOveruses;
    }

    private static String toString(Iterable<WatchdogStorage.IoUsageStatsEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (WatchdogStorage.IoUsageStatsEntry entry : entries) {
            toStringBuilder(builder, entry).append(", ");
        }
        if (builder.length() > 1) {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append(']');
        return builder.toString();
    }

    private static StringBuilder toStringBuilder(StringBuilder builder,
            WatchdogStorage.IoUsageStatsEntry entry) {
        builder.append("{UserId: ").append(entry.userId)
                .append(", Package name: ").append(entry.packageName)
                .append(", IoUsage: ");
        toStringBuilder(builder, entry.ioUsage);
        return builder.append('}');
    }

    private static StringBuilder toStringBuilder(StringBuilder builder,
            WatchdogPerfHandler.PackageIoUsage ioUsage) {
        builder.append("{IoOveruseStats: ");
        toStringBuilder(builder, ioUsage.getInternalIoOveruseStats());
        builder.append(", ForgivenWriteBytes: ");
        InternalPerStateBytesSubject.toStringBuilder(builder, ioUsage.getForgivenWriteBytes());
        builder.append(", Forgiven overuses: ").append(ioUsage.getForgivenOveruses());
        return builder.append(", Total times killed: ").append(ioUsage.getTotalTimesKilled())
                .append('}');
    }

    private static StringBuilder toStringBuilder(StringBuilder builder,
            IoOveruseStats stats) {
        if (stats == null) {
            return builder.append(NULL_ENTRY_STRING);
        }
        builder.append("{Killable on overuse: ").append(stats.killableOnOveruse)
                .append(", Remaining write bytes: ");
        InternalPerStateBytesSubject.toStringBuilder(builder, stats.remainingWriteBytes);
        builder.append(", Start time: ").append(stats.startTime)
                .append(", Duration: ").append(stats.durationInSeconds).append(" seconds")
                .append(", Total overuses: ").append(stats.totalOveruses)
                .append(", Written bytes: ");
        InternalPerStateBytesSubject.toStringBuilder(builder, stats.writtenBytes);
        return builder.append('}');
    }

    private IoUsageStatsEntrySubject(FailureMetadata failureMetadata,
            @Nullable Iterable<WatchdogStorage.IoUsageStatsEntry> iterableSubject) {
        super(failureMetadata, iterableSubject);

        mActual = iterableSubject;
    }
}
