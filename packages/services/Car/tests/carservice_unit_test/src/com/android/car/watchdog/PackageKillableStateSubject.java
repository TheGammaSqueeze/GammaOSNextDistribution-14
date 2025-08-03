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
import android.car.watchdog.PackageKillableState;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;

public final class PackageKillableStateSubject extends Subject {
    // Boiler-plate Subject.Factory for PackageKillableStateSubject
    private static final Subject.Factory<
            com.android.car.watchdog.PackageKillableStateSubject,
            Iterable<PackageKillableState>> PACKAGE_KILLABLE_STATE_SUBJECT_FACTORY =
            com.android.car.watchdog.PackageKillableStateSubject::new;

    private final Iterable<PackageKillableState> mActual;

    // User-defined entry point
    public static PackageKillableStateSubject assertThat(
            @Nullable Iterable<PackageKillableState> stats) {
        return assertAbout(PACKAGE_KILLABLE_STATE_SUBJECT_FACTORY).that(stats);
    }

    public static Subject.Factory<PackageKillableStateSubject,
            Iterable<PackageKillableState>> packageKillableStates() {
        return PACKAGE_KILLABLE_STATE_SUBJECT_FACTORY;
    }

    public void containsExactly(PackageKillableState... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<PackageKillableState> expected) {
        Truth.assertThat(mActual)
                .comparingElementsUsing(Correspondence.from(
                        PackageKillableStateSubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(PackageKillableState actual,
            PackageKillableState expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.getPackageName().equals(expected.getPackageName())
                && actual.getUserId() == expected.getUserId()
                && actual.getKillableState() == expected.getKillableState();
    }

    private PackageKillableStateSubject(FailureMetadata failureMetadata,
            @Nullable Iterable<PackageKillableState> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }
}
