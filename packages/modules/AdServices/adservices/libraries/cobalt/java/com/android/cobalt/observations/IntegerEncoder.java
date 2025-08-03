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

package com.android.cobalt.observations;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.cobalt.data.EventRecordAndSystemProfile;

import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.common.collect.ImmutableList;

import java.security.SecureRandom;

/**
 * Encodes events for the same day and report as a single {@link Observation} that wraps an {@link
 * IntegerObservation}.
 *
 * <p>Note, this encoder expects input {@link AggregateValue} objects to have an inner integer value
 * set and will use 0 if not.
 */
final class IntegerEncoder implements NonPrivateObservationGenerator.Encoder {
    private final SecureRandom mSecureRandom;

    IntegerEncoder(@NonNull SecureRandom secureRandom) {
        this.mSecureRandom = requireNonNull(secureRandom);
    }

    /**
     * Encodes integer events for the same day and report as a single {@link IntegerObservation}.
     *
     * @param events the events to encode
     * @return an observation which wraps an {@link IntegerObservation} holding the input events
     */
    @Override
    public Observation encode(ImmutableList<EventRecordAndSystemProfile> events) {
        IntegerObservation.Builder integerObservation = IntegerObservation.newBuilder();
        for (EventRecordAndSystemProfile event : events) {
            IntegerObservation.Value value =
                    IntegerObservation.Value.newBuilder()
                            .addAllEventCodes(event.eventVector().eventCodes())
                            .setValue(event.aggregateValue().getIntegerValue())
                            .build();
            integerObservation.addValues(value);
        }
        return Observation.newBuilder()
                .setInteger(integerObservation)
                .setRandomId(RandomId.generate(mSecureRandom))
                .build();
    }
}
