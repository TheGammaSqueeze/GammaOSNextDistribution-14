/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.data;

import com.google.cobalt.AggregateValue;

/**
 * Aggregates values against existing {@link AggregateValue}s.
 *
 * @param <ToAggregate> the type of the value being aggregated against an {@link AggregateValue} in
 *     the {@link DataService}
 */
interface LogAggregator<ToAggregate> {
    /** Returns an initial {@link AggregateValue} for when no aggregate exists. */
    AggregateValue initialValue(ToAggregate toAggregate);

    /**
     * Returns an {@link AggregateValue} that is an aggregation of new and existing values.
     *
     * @param toAggregate the value being aggregated
     * @param existing the existing {@link AggregateValue}
     * @return an {@link AggregateValue} that is a combination of the new and existing values.
     */
    AggregateValue aggregateValues(ToAggregate toAggregate, AggregateValue existing);
}
