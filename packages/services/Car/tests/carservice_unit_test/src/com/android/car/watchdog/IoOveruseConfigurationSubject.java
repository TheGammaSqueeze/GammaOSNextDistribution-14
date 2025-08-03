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
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.PerStateBytes;

import com.google.common.base.Equivalence;
import com.google.common.collect.Maps;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class IoOveruseConfigurationSubject extends Subject {
    // Boiler-plate Subject.Factory for IoOveruseConfigurationSubject
    private static final Subject.Factory<
            com.android.car.watchdog.IoOveruseConfigurationSubject,
            Iterable<IoOveruseConfiguration>> Io_OVERUSE_CONFIG_SUBJECT_FACTORY =
            com.android.car.watchdog.IoOveruseConfigurationSubject::new;

    private static final Equivalence<PerStateBytes> PER_STATE_BYTES_EQUIVALENCE =
            new Equivalence<PerStateBytes>() {
                @Override
                protected boolean doEquivalent(PerStateBytes actual, PerStateBytes expected) {
                    return isPerStateBytesEquals(actual, expected);
                }

                @Override
                protected int doHash(PerStateBytes perStateBytes) {
                    return (int) ((perStateBytes.getForegroundModeBytes() * 10 ^ 4)
                            + (perStateBytes.getBackgroundModeBytes() * 10 ^ 3)
                            + perStateBytes.getGarageModeBytes());
                }
            };

    private final Iterable<IoOveruseConfiguration> mActual;

    // User-defined entry point
    public static IoOveruseConfigurationSubject assertThat(
            @Nullable Iterable<IoOveruseConfiguration> stats) {
        return assertAbout(Io_OVERUSE_CONFIG_SUBJECT_FACTORY).that(stats);
    }

    public static Subject.Factory<IoOveruseConfigurationSubject,
            Iterable<IoOveruseConfiguration>> resourceOveruseStats() {
        return Io_OVERUSE_CONFIG_SUBJECT_FACTORY;
    }

    public void containsExactly(IoOveruseConfiguration... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<IoOveruseConfiguration> expected) {
        Truth.assertThat(mActual)
                .comparingElementsUsing(Correspondence.from(
                        IoOveruseConfigurationSubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(IoOveruseConfiguration actual,
            IoOveruseConfiguration expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }

        return isPerStateBytesEquals(actual.getComponentLevelThresholds(),
                expected.getComponentLevelThresholds())
                && Maps.difference(actual.getPackageSpecificThresholds(),
                expected.getPackageSpecificThresholds(), PER_STATE_BYTES_EQUIVALENCE).areEqual()
                && Maps.difference(actual.getAppCategorySpecificThresholds(),
                expected.getAppCategorySpecificThresholds(), PER_STATE_BYTES_EQUIVALENCE).areEqual()
                && isAlertThresholdEquals(actual.getSystemWideThresholds(),
                expected.getSystemWideThresholds());
    }

    private IoOveruseConfigurationSubject(FailureMetadata failureMetadata,
            @Nullable Iterable<IoOveruseConfiguration> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }

    private static boolean isPerStateBytesEquals(PerStateBytes actual, PerStateBytes expected) {
        return actual.getForegroundModeBytes() == expected.getForegroundModeBytes()
                && actual.getBackgroundModeBytes() == expected.getBackgroundModeBytes()
                && actual.getGarageModeBytes() == expected.getGarageModeBytes();
    }

    private static boolean isAlertThresholdEquals(List<IoOveruseAlertThreshold> actual,
            List<IoOveruseAlertThreshold> expected) {
        Set<String> actualStr = toAlertThresholdStrings(actual);
        Set<String> expectedStr = toAlertThresholdStrings(expected);
        return actualStr.equals(expectedStr);
    }

    private static Set<String> toAlertThresholdStrings(List<IoOveruseAlertThreshold> thresholds) {
        return thresholds.stream().map(x -> String.format("%d:%d", x.getDurationInSeconds(),
                x.getWrittenBytesPerSecond())).collect(Collectors.toSet());
    }
}
