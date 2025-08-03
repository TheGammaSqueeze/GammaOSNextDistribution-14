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

import static com.google.common.truth.Truth.assertThat;

import com.android.cobalt.data.EventRecordAndSystemProfile;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.observations.testing.FakeSecureRandom;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.IntegerObservation;
import com.google.cobalt.Observation;
import com.google.cobalt.SystemProfile;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.SecureRandom;
import java.util.List;

@RunWith(JUnit4.class)
public final class IntegerEncoderTest {
    private static final SecureRandom SECURE_RANDOM = new FakeSecureRandom();
    private static final SystemProfile SYSTEM_PROFILE = SystemProfile.getDefaultInstance();

    @Test
    public void encodesEventsIntoOneObservation() throws Exception {
        EventRecordAndSystemProfile event1 =
                EventRecordAndSystemProfile.create(
                        SYSTEM_PROFILE,
                        EventVector.create(ImmutableList.of(1, 5)),
                        AggregateValue.newBuilder().setIntegerValue(100).build());
        EventRecordAndSystemProfile event2 =
                EventRecordAndSystemProfile.create(
                        SYSTEM_PROFILE,
                        EventVector.create(ImmutableList.of(2, 6)),
                        AggregateValue.newBuilder().setIntegerValue(200).build());

        IntegerObservation observation =
                IntegerObservation.newBuilder()
                        .addValues(
                                IntegerObservation.Value.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .setValue(100))
                        .addValues(
                                IntegerObservation.Value.newBuilder()
                                        .addAllEventCodes(List.of(2, 6))
                                        .setValue(200))
                        .build();

        IntegerEncoder encoder = new IntegerEncoder(SECURE_RANDOM);
        assertThat(encoder.encode(ImmutableList.of(event1, event2)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setInteger(observation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }
}
