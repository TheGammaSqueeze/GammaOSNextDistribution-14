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

package com.android.eventlib.truth;

import static com.google.common.truth.Truth.assertAbout;

import androidx.annotation.Nullable;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogs;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.time.Duration;

/** {@link Subject} for queries about {@link EventLogs}. */
public final class EventLogsSubject<E extends Event> extends Subject {

    /**
     * Assertions about {@link EventLogs}.
     */
    public static <F extends Event> Factory<EventLogsSubject<F>, EventLogs<F>> eventLogs() {
        return EventLogsSubject::new;
    }

    /**
     * Assertions about {@link EventLogs}.
     */
    public static <F extends Event> EventLogsSubject<F> assertThat(@Nullable EventLogs<F> actual) {
        return assertAbout(EventLogsSubject.<F>eventLogs()).that(actual);
    }

    @Nullable private final EventLogs<E> mActual;

    private EventLogsSubject(FailureMetadata metadata, @Nullable EventLogs<E> actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    /**
     * Asserts that an event occurred (that {@link EventLogs#poll()} returns non-null).
     */
    public E eventOccurred() {
        E event = mActual.poll();

        if (event == null) {
            fail();
        }

        return event;
    }

    /**
     * Asserts that an event occurred (that {@link EventLogs#poll(Duration)} returns non-null).
     */
    public E eventOccurredWithin(Duration timeout) {
        E event = mActual.poll(timeout);

        if (event == null) {
            fail();
        }

        return event;
    }

    private void fail() {

        failWithoutActual(Fact.simpleFact("Expected event to have occurred matching: "
                + mActual + " but it did not occur."));
    }
}
