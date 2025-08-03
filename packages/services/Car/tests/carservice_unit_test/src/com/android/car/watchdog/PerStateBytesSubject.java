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
import android.car.watchdog.PerStateBytes;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

public final class PerStateBytesSubject extends Subject {
    // Boiler-plate Subject.Factory for PerStateBytesSubject
    private static final Subject.Factory<com.android.car.watchdog.PerStateBytesSubject,
            PerStateBytes> PER_STATE_BYTES_SUBJECT_FACTORY =
            com.android.car.watchdog.PerStateBytesSubject::new;

    private final PerStateBytes mActual;

    // User-defined entry point
    public static PerStateBytesSubject assertThat(@Nullable PerStateBytes perStateBytes) {
        return assertAbout(PER_STATE_BYTES_SUBJECT_FACTORY).that(perStateBytes);
    }

    // Static method for getting the subject factory (for use with assertAbout())
    public static Subject.Factory<PerStateBytesSubject, PerStateBytes> perStateBytes() {
        return PER_STATE_BYTES_SUBJECT_FACTORY;
    }

    private PerStateBytesSubject(FailureMetadata failureMetadata,
            @Nullable PerStateBytes subject) {
        super(failureMetadata, subject);
        this.mActual = subject;
    }

    // User-defined test assertion SPI below this point
    public void isEqualTo(PerStateBytes expected) {
        if (mActual == expected) {
            return;
        }
        check("getForegroundModeBytes()").that(mActual.getForegroundModeBytes())
                .isEqualTo(expected.getForegroundModeBytes());
        check("getBackgroundModeBytes()").that(mActual.getBackgroundModeBytes())
                .isEqualTo(expected.getBackgroundModeBytes());
        check("getGarageModeBytes()").that(mActual.getGarageModeBytes())
                .isEqualTo(expected.getGarageModeBytes());
    }

    public static boolean isEquals(PerStateBytes actual, PerStateBytes expected) {
        if (actual == expected) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.getForegroundModeBytes() == expected.getForegroundModeBytes()
                && actual.getBackgroundModeBytes() == expected.getBackgroundModeBytes()
                && actual.getGarageModeBytes() == expected.getGarageModeBytes();
    }
}
