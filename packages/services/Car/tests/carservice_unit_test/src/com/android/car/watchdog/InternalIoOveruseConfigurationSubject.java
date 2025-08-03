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
import android.automotive.watchdog.internal.IoOveruseAlertThreshold;
import android.automotive.watchdog.internal.IoOveruseConfiguration;
import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class InternalIoOveruseConfigurationSubject extends Subject {
    // Boiler-plate Subject.Factory for InternalIoOveruseConfigurationSubject
    private static final Subject.Factory<
            com.android.car.watchdog.InternalIoOveruseConfigurationSubject,
            Iterable<IoOveruseConfiguration>> IO_OVERUSE_CONFIG_SUBJECT_FACTORY =
            com.android.car.watchdog.InternalIoOveruseConfigurationSubject::new;

    private final Iterable<IoOveruseConfiguration> mActual;

    // User-defined entry point
    public static InternalIoOveruseConfigurationSubject assertThat(
            @Nullable Iterable<IoOveruseConfiguration> stats) {
        return assertAbout(IO_OVERUSE_CONFIG_SUBJECT_FACTORY).that(stats);
    }

    public static Subject.Factory<InternalIoOveruseConfigurationSubject,
            Iterable<IoOveruseConfiguration>> ioOveruseConfigurations() {
        return IO_OVERUSE_CONFIG_SUBJECT_FACTORY;
    }

    public void containsExactly(IoOveruseConfiguration... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<IoOveruseConfiguration> expected) {
        Truth.assertWithMessage("Expected: " + expected.toString() + "\nActual: "
                + mActual.toString()).that(mActual)
                .comparingElementsUsing(Correspondence.from(
                        InternalIoOveruseConfigurationSubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(IoOveruseConfiguration actual,
            IoOveruseConfiguration expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.componentLevelThresholds.name.equals(expected.componentLevelThresholds.name)
                && InternalPerStateBytesSubject.isEquals(
                        actual.componentLevelThresholds.perStateWriteBytes,
                        expected.componentLevelThresholds.perStateWriteBytes)
                && isPerStateThresholdEquals(actual.packageSpecificThresholds,
                    expected.packageSpecificThresholds)
                && isPerStateThresholdEquals(actual.categorySpecificThresholds,
                    expected.categorySpecificThresholds)
                && isAlertThresholdEquals(actual.systemWideThresholds,
                    expected.systemWideThresholds);
    }

    public static StringBuilder toStringBuilder(StringBuilder builder,
            IoOveruseConfiguration config) {
        builder.append("{Component-level thresholds: ")
                .append(toString(config.componentLevelThresholds))
                .append(", Package specific thresholds: [")
                .append(config.packageSpecificThresholds.stream()
                        .map(InternalIoOveruseConfigurationSubject::toString)
                        .collect(Collectors.joining(", ")))
                .append("], Category specific thresholds: [")
                .append(config.categorySpecificThresholds.stream()
                        .map(InternalIoOveruseConfigurationSubject::toString)
                        .collect(Collectors.joining(", ")))
                .append("], System wide thresholds: [")
                .append(config.systemWideThresholds.stream()
                        .map(InternalIoOveruseConfigurationSubject::toString)
                        .collect(Collectors.joining(", ")))
                .append("]}");
        return builder;
    }

    public static String toString(PerStateIoOveruseThreshold threshold) {
        StringBuilder builder = new StringBuilder();
        builder.append("{Name: ").append(threshold.name).append(", WriteBytes: ");
        InternalPerStateBytesSubject.toStringBuilder(builder, threshold.perStateWriteBytes);
        builder.append("}");
        return builder.toString();
    }

    public static String toString(IoOveruseAlertThreshold threshold) {
        StringBuilder builder = new StringBuilder();
        builder.append("{durationInSeconds: ").append(threshold.durationInSeconds)
                .append(", writtenBytesPerSecond: ").append(threshold.writtenBytesPerSecond)
                .append("}");
        return builder.toString();
    }

    private InternalIoOveruseConfigurationSubject(FailureMetadata failureMetadata,
            @Nullable Iterable<IoOveruseConfiguration> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }

    private static boolean isPerStateThresholdEquals(List<PerStateIoOveruseThreshold> actual,
            List<PerStateIoOveruseThreshold> expected) {
        Set<String> actualStr = toPerStateThresholdStrings(actual);
        Set<String> expectedStr = toPerStateThresholdStrings(expected);
        return actualStr.equals(expectedStr);
    }

    private static boolean isAlertThresholdEquals(List<IoOveruseAlertThreshold> actual,
            List<IoOveruseAlertThreshold> expected) {
        Set<String> actualStr = toAlertThresholdStrings(actual);
        Set<String> expectedStr = toAlertThresholdStrings(expected);
        return actualStr.equals(expectedStr);
    }

    private static Set<String> toPerStateThresholdStrings(
            List<PerStateIoOveruseThreshold> thresholds) {
        return thresholds.stream().map(x -> String.format("%s:{%d,%d,%d}", x.name,
                x.perStateWriteBytes.foregroundBytes, x.perStateWriteBytes.backgroundBytes,
                x.perStateWriteBytes.garageModeBytes))
                .collect(Collectors.toSet());
    }

    private static Set<String> toAlertThresholdStrings(
            List<IoOveruseAlertThreshold> thresholds) {
        return thresholds.stream().map(x -> String.format("%d:%d", x.durationInSeconds,
                x.writtenBytesPerSecond)).collect(Collectors.toSet());
    }
}
