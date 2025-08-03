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
import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.Nullable;
import android.car.watchdog.ResourceOveruseStats;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;

public final class ResourceOveruseStatsSubject extends Subject {
    // Boiler-plate Subject.Factory for ResourceOveruseStatsSubject
    private static final Subject.Factory<com.android.car.watchdog.ResourceOveruseStatsSubject,
            Iterable<ResourceOveruseStats>> RESOURCE_OVERUSE_STATS_SUBJECT_FACTORY =
            com.android.car.watchdog.ResourceOveruseStatsSubject::new;

    private final Iterable<ResourceOveruseStats> mActual;

    // User-defined entry point
    public static ResourceOveruseStatsSubject assertThat(
            @Nullable Iterable<ResourceOveruseStats> stats) {
        return assertAbout(RESOURCE_OVERUSE_STATS_SUBJECT_FACTORY).that(stats);
    }

    public static void assertEquals(ResourceOveruseStats actual, ResourceOveruseStats expected) {
        assertWithMessage("Expected stats (%s) equals to actual stats (%s)", expected, actual)
                .that(isEquals(actual, expected)).isTrue();
    }

    public static Subject.Factory<ResourceOveruseStatsSubject, Iterable<ResourceOveruseStats>>
            resourceOveruseStats() {
        return RESOURCE_OVERUSE_STATS_SUBJECT_FACTORY;
    }

    private ResourceOveruseStatsSubject(FailureMetadata failureMetadata,
            @Nullable Iterable<ResourceOveruseStats> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }

    public void containsExactly(ResourceOveruseStats... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<ResourceOveruseStats> expected) {
        Truth.assertThat(mActual)
                .comparingElementsUsing(Correspondence.from(ResourceOveruseStatsSubject::isEquals,
                        "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(ResourceOveruseStats actual, ResourceOveruseStats expected) {
        if (actual == expected) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.getPackageName().equals(expected.getPackageName())
                && actual.getUserHandle().equals(expected.getUserHandle())
                && IoOveruseStatsSubject.isEquals(actual.getIoOveruseStats(),
                    expected.getIoOveruseStats());
    }
}
