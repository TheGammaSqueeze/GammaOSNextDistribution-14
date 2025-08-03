/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.cobalt.data.StringListEntry;
import com.android.cobalt.observations.testing.FakeSecureRandom;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.IndexHistogram;
import com.google.cobalt.LocalIndexHistogram;
import com.google.cobalt.Observation;
import com.google.cobalt.StringHistogramObservation;
import com.google.cobalt.SystemProfile;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.SecureRandom;
import java.util.List;

@RunWith(JUnit4.class)
public final class StringHistogramEncoderTest {
    private static final SystemProfile SYSTEM_PROFILE = SystemProfile.getDefaultInstance();

    private static final HashCode HASH_CODE_1 = HashCode.fromBytes(new byte[] {0x0a});
    private static final HashCode HASH_CODE_2 = HashCode.fromBytes(new byte[] {0x0b});
    private static final HashCode HASH_CODE_3 = HashCode.fromBytes(new byte[] {0x0c});
    private static final HashCode HASH_CODE_4 = HashCode.fromBytes(new byte[] {0x0d});

    private final SecureRandom mSecureRandom = new FakeSecureRandom();

    private EventRecordAndSystemProfile createEvent(
            EventVector eventVector, List<LocalIndexHistogram.Bucket> buckets) {
        return EventRecordAndSystemProfile.create(
                SYSTEM_PROFILE,
                eventVector,
                AggregateValue.newBuilder()
                        .setIndexHistogram(LocalIndexHistogram.newBuilder().addAllBuckets(buckets))
                        .build());
    }

    @Test
    public void stringsHashesOrderedAndCompacted() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(
                                StringListEntry.create(/* listIndex= */ 1, HASH_CODE_2),
                                StringListEntry.create(/* listIndex= */ 2, HASH_CODE_3),
                                StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1),
                                StringListEntry.create(/* listIndex= */ 5, HASH_CODE_4)),
                        mSecureRandom);

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_2.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_3.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_4.asBytes()))
                        .build();
        assertThat(encoder.encode(ImmutableList.of()))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }

    @Test
    public void multipleEventsDisjointStrings() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(
                                StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1),
                                StringListEntry.create(/* listIndex= */ 1, HASH_CODE_2),
                                StringListEntry.create(/* listIndex= */ 2, HASH_CODE_3),
                                StringListEntry.create(/* listIndex= */ 3, HASH_CODE_4)),
                        mSecureRandom);

        EventRecordAndSystemProfile event1 =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(10)
                                        .build(),
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(1)
                                        .setCount(11)
                                        .build()));
        EventRecordAndSystemProfile event2 =
                createEvent(
                        EventVector.create(2, 6),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(2)
                                        .setCount(12)
                                        .build()));
        EventRecordAndSystemProfile event3 =
                createEvent(
                        EventVector.create(3, 7),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(3)
                                        .setCount(13)
                                        .build()));

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_2.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_3.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_4.asBytes()))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .addBucketIndices(0)
                                        .addBucketCounts(10)
                                        .addBucketIndices(1)
                                        .addBucketCounts(11))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(2, 6))
                                        .addBucketIndices(2)
                                        .addBucketCounts(12))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(3, 7))
                                        .addBucketIndices(3)
                                        .addBucketCounts(13))
                        .build();

        assertThat(encoder.encode(ImmutableList.of(event1, event2, event3)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }

    @Test
    public void multipleEventsSameStrings() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(
                                StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1),
                                StringListEntry.create(/* listIndex= */ 1, HASH_CODE_2)),
                        mSecureRandom);

        EventRecordAndSystemProfile event1 =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(10)
                                        .build(),
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(1)
                                        .setCount(11)
                                        .build()));
        EventRecordAndSystemProfile event2 =
                createEvent(
                        EventVector.create(2, 6),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(12)
                                        .build()));
        EventRecordAndSystemProfile event3 =
                createEvent(
                        EventVector.create(3, 7),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(1)
                                        .setCount(13)
                                        .build()));

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_2.asBytes()))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .addBucketIndices(0)
                                        .addBucketCounts(10)
                                        .addBucketIndices(1)
                                        .addBucketCounts(11))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(2, 6))
                                        .addBucketIndices(0)
                                        .addBucketCounts(12))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(3, 7))
                                        .addBucketIndices(1)
                                        .addBucketCounts(13))
                        .build();

        assertThat(encoder.encode(ImmutableList.of(event1, event2, event3)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }

    @Test
    public void bucketWithInvalidIndexExcluded() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1)),
                        mSecureRandom);

        EventRecordAndSystemProfile event =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(10)
                                        .build()));

        // An event with a non-sensical index.
        EventRecordAndSystemProfile badEvent =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(10000)
                                        .setCount(1)
                                        .build()));

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .addBucketIndices(0)
                                        .addBucketCounts(10))
                        .build();
        assertThat(encoder.encode(ImmutableList.of(event, badEvent)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }

    @Test
    public void bucketWithZeroCountExcluded() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(
                                StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1),
                                StringListEntry.create(/* listIndex= */ 1, HASH_CODE_2)),
                        mSecureRandom);

        EventRecordAndSystemProfile event =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(10)
                                        .build()));

        // An event with a count of zero.
        EventRecordAndSystemProfile badEvent =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(1)
                                        .setCount(0)
                                        .build()));

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_2.asBytes()))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .addBucketIndices(0)
                                        .addBucketCounts(10))
                        .build();
        assertThat(encoder.encode(ImmutableList.of(event, badEvent)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }

    @Test
    public void bucketCountsSummedForSameIndex() throws Exception {
        StringHistogramEncoder encoder =
                new StringHistogramEncoder(
                        ImmutableList.of(StringListEntry.create(/* listIndex= */ 0, HASH_CODE_1)),
                        mSecureRandom);

        // An event with the same index referenced twice.
        EventRecordAndSystemProfile event =
                createEvent(
                        EventVector.create(1, 5),
                        List.of(
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(10)
                                        .build(),
                                LocalIndexHistogram.Bucket.newBuilder()
                                        .setIndex(0)
                                        .setCount(11)
                                        .build()));

        StringHistogramObservation expectedStringObservation =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(ByteString.copyFrom(HASH_CODE_1.asBytes()))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(List.of(1, 5))
                                        .addBucketIndices(0)
                                        .addBucketCounts(21))
                        .build();
        assertThat(encoder.encode(ImmutableList.of(event)))
                .isEqualTo(
                        Observation.newBuilder()
                                .setStringHistogram(expectedStringObservation)
                                .setRandomId(
                                        ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}))
                                .build());
    }
}
