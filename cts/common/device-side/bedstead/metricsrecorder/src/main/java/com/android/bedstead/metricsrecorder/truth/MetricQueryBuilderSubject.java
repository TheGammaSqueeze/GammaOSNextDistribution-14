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

package com.android.bedstead.metricsrecorder.truth;

import static com.google.common.truth.Truth.assertAbout;

import androidx.annotation.Nullable;

import com.android.bedstead.metricsrecorder.MetricQueryBuilder;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.time.Duration;

public final class MetricQueryBuilderSubject extends Subject {

    /**
     * Assertions about {@link MetricQueryBuilder}.
     */
    public static Factory<MetricQueryBuilderSubject, MetricQueryBuilder> MetricQueryBuilder() {
        return MetricQueryBuilderSubject::new;
    }

    /**
     * Assertions about {@link MetricQueryBuilder}.
     */
    public static MetricQueryBuilderSubject assertThat(@Nullable MetricQueryBuilder actual) {
        return assertAbout(MetricQueryBuilder()).that(actual);
    }

    @Nullable private final MetricQueryBuilder mActual;

    private MetricQueryBuilderSubject(FailureMetadata metadata, @Nullable MetricQueryBuilder actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    /**
     * Asserts that an event occurred (that {@link MetricQueryBuilder#poll()} returns non-null).
     */
    public void wasLogged() {
        if (mActual.poll() == null) {
            // TODO(b/197315353): Add non-matching metrics
            failWithoutActual(Fact.simpleFact("Expected metric to have been logged matching: "
                    + mActual + " but it was not logged. Did see: " + mActual.nonMatchingMetrics()));
        }
    }

    /**
     * Asserts that an event occurred (that {@link MetricQueryBuilder#poll(Duration)} returns non-null).
     */
    public void wasLoggedWithin(Duration timeout) {
        if (mActual.poll(timeout) == null) {
            // TODO(b/197315353): Add non-matching events
            failWithoutActual(Fact.simpleFact("Expected metric to have been logged matching: "
                    + mActual + " but it was not logged."));
        }
    }
}
