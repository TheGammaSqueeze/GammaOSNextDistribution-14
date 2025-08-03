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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.cobalt.data.EventRecordAndSystemProfile;
import com.android.cobalt.data.StringListEntry;

import com.google.cobalt.IndexHistogram;
import com.google.cobalt.LocalIndexHistogram;
import com.google.cobalt.Observation;
import com.google.cobalt.StringHistogramObservation;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes events for the same day and report as a single {@link Observation} that wraps a {@link
 * StringHistogramObservation}.
 *
 * <p>Note, this encoder expects input {@link AggregateValue} objects to have an inner index
 * histogram value set and will skip any values without one.
 */
final class StringHistogramEncoder implements NonPrivateObservationGenerator.Encoder {
    private final ImmutableList<StringListEntry> mStringListEntries;
    private final SecureRandom mSecureRandom;

    StringHistogramEncoder(
            @NonNull ImmutableList<StringListEntry> stringListEntries,
            @NonNull SecureRandom secureRandom) {
        this.mStringListEntries =
                ImmutableList.sortedCopyOf(
                        Comparator.comparingInt(StringListEntry::listIndex),
                        requireNonNull(stringListEntries));
        this.mSecureRandom = requireNonNull(secureRandom);
    }

    /**
     * Encodes string events for the same day and report as a single {@link
     * StringHistogramObservation}.
     *
     * @param events the events to encode
     * @return an observation which wraps a {@link StringHistogramObservation} holding the input
     *     events
     */
    @Override
    public Observation encode(ImmutableList<EventRecordAndSystemProfile> events) {
        StringHistogramObservation.Builder stringObservation =
                StringHistogramObservation.newBuilder();

        // Add all the string hashes.
        for (StringListEntry entry : mStringListEntries) {
            stringObservation.addStringHashesFf64(
                    ByteString.copyFrom(entry.stringHash().asBytes()));
        }

        for (EventRecordAndSystemProfile event : events) {
            IndexHistogram.Builder indexHistogram = IndexHistogram.newBuilder();

            // Accumulate the (list index, counts) the buckets hold into a map.
            //
            // Note, the list index is the persisted index, *not* the actual index in
            // mStringListEntries.
            Map<Integer, Long> bucketCounts = new HashMap();
            for (LocalIndexHistogram.Bucket bucket :
                    event.aggregateValue().getIndexHistogram().getBucketsList()) {
                bucketCounts.merge(bucket.getIndex(), bucket.getCount(), (c1, c2) -> c1 + c2);
            }

            if (bucketCounts.isEmpty()) {
                continue;
            }

            // Add an (index, count) pair to the index histogram for each persisted index.
            boolean atLeastOne = false;
            for (int observationIndex = 0;
                    observationIndex < mStringListEntries.size();
                    ++observationIndex) {
                StringListEntry entry = mStringListEntries.get(observationIndex);
                long count = bucketCounts.getOrDefault(entry.listIndex(), 0L);
                if (count != 0) {
                    indexHistogram.addBucketIndices(observationIndex).addBucketCounts(count);
                    atLeastOne = true;
                }
            }

            if (atLeastOne) {
                indexHistogram.addAllEventCodes(event.eventVector().eventCodes());
                stringObservation.addStringHistograms(indexHistogram);
            }
        }

        return Observation.newBuilder()
                .setStringHistogram(stringObservation)
                .setRandomId(RandomId.generate(mSecureRandom))
                .build();
    }
}
