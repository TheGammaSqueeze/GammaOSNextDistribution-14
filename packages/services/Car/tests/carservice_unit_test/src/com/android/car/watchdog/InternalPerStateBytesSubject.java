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
import android.automotive.watchdog.PerStateBytes;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

public final class InternalPerStateBytesSubject extends Subject {
    // Boiler-plate Subject.Factory for PerStateBytesSubject
    private static final Subject.Factory<com.android.car.watchdog.InternalPerStateBytesSubject,
            PerStateBytes> PER_STATE_BYTES_SUBJECT_FACTORY =
            com.android.car.watchdog.InternalPerStateBytesSubject::new;
    private static final String NULL_ENTRY_STRING = "{NULL}";

    private final PerStateBytes mActual;

    // User-defined entry point
    public static InternalPerStateBytesSubject assertThat(@Nullable PerStateBytes perStateBytes) {
        return assertAbout(PER_STATE_BYTES_SUBJECT_FACTORY).that(perStateBytes);
    }

    // Static method for getting the subject factory (for use with assertAbout())
    public static Subject.Factory<InternalPerStateBytesSubject, PerStateBytes> perStateBytes() {
        return PER_STATE_BYTES_SUBJECT_FACTORY;
    }

    public static InternalPerStateBytesSubject assertWithMessage(
            @Nullable PerStateBytes perStateBytes, String format, Object... args) {
        return Truth.assertWithMessage(format, args).about(PER_STATE_BYTES_SUBJECT_FACTORY)
                .that(perStateBytes);
    }

    private InternalPerStateBytesSubject(FailureMetadata failureMetadata,
            @Nullable PerStateBytes subject) {
        super(failureMetadata, subject);
        this.mActual = subject;
    }

    // User-defined test assertion SPI below this point
    public void isEqualTo(PerStateBytes expected) {
        if (mActual == expected) {
            return;
        }
        check("foregroundBytes").that(mActual.foregroundBytes).isEqualTo(expected.foregroundBytes);
        check("backgroundBytes").that(mActual.backgroundBytes).isEqualTo(expected.backgroundBytes);
        check("garageModeBytes").that(mActual.garageModeBytes).isEqualTo(expected.garageModeBytes);
    }

    public static boolean isEquals(PerStateBytes actual, PerStateBytes expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.foregroundBytes == expected.foregroundBytes
                && actual.backgroundBytes == expected.backgroundBytes
                && actual.garageModeBytes == expected.garageModeBytes;
    }

    public static StringBuilder toStringBuilder(
            StringBuilder builder, PerStateBytes perStateBytes) {
        if (perStateBytes == null) {
            return builder.append(NULL_ENTRY_STRING);
        }
        return builder.append("{Foreground bytes: ").append(perStateBytes.foregroundBytes)
                .append(", Background bytes: ").append(perStateBytes.backgroundBytes)
                .append(", Garage mode bytes: ").append(perStateBytes.garageModeBytes)
                .append('}');
    }
}
