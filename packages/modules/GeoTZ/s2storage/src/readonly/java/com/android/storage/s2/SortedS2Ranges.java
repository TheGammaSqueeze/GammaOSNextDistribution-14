/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.storage.s2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Wraps a collection of {@link S2LevelRange} objects, sorted by {@link
 * S2LevelRange#getStartCellId()} that must all be at the same level and must not overlap. Gaps
 * are permitted.
 *
 * @param <T> the exact subtype of {@link S2LevelRange}
 */
public final class SortedS2Ranges<T extends S2LevelRange> implements Iterable<T> {

    private final int mS2Level;

    private final List<T> mRanges;

    private SortedS2Ranges(int s2Level, List<T> ranges) {
        mS2Level = s2Level;
        mRanges = Collections.unmodifiableList(new ArrayList<>(ranges));
    }

    /**
     * Creates a {@link SortedS2Ranges} from a pre-sorted set of ranges with the same S2 level.
     *
     * @thorws IllegalArgumentException if the ranges are at different levels, ranges are not
     *     sorted, or ranges overlap
     */
    public static <T extends S2LevelRange> SortedS2Ranges<T> createFromSorted(
            int s2Level, T... ranges) {
        return createFromSorted(s2Level, Arrays.asList(ranges));
    }

    /**
     * Creates a {@link SortedS2Ranges} from a pre-sorted set of ranges with the same S2 level.
     *
     * @thorws IllegalArgumentException if the ranges are at different levels, ranges are not
     *     sorted, or ranges overlap
     */
    public static <T extends S2LevelRange> SortedS2Ranges<T> createFromSorted(
            int s2Level, List<T> ranges) {
        // Validate - look for overlapping ranges or ranges of the wrong level.
        S2LevelRange previousRange = null;
        for (S2LevelRange currentRange : ranges) {
            int currentRangeLevel = currentRange.getS2Level();
            // Validate all the levels are the same.
            if (s2Level != currentRangeLevel) {
                throw new IllegalArgumentException(
                        "currentRange=" + currentRange + " is not at level " + s2Level);
            }

            if (previousRange != null) {
                if (previousRange.overlaps(currentRange)) {
                    throw new IllegalArgumentException("previous range " + previousRange
                            + " overlaps next range " + currentRange);
                }
                if (!previousRange.startsBefore(currentRange)) {
                    throw new IllegalArgumentException("previous range " + previousRange
                            + " must start before next range " + currentRange);
                }
            }

            previousRange = currentRange;
        }
        return new SortedS2Ranges<>(s2Level, ranges);
    }

    /** Returns the S2 level of the ranges. */
    public int getS2Level() {
        return mS2Level;
    }

    /** Returns an unmodifiable list of all of the ranges. */
    public List<T> getRanges() {
        return mRanges;
    }

    @Override
    public Iterator<T> iterator() {
        return mRanges.iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        mRanges.forEach(action);
    }

    @Override
    public Spliterator<T> spliterator() {
        return mRanges.spliterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SortedS2Ranges<?> that = (SortedS2Ranges<?>) o;
        return mS2Level == that.mS2Level
                && mRanges.equals(that.mRanges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mS2Level, mRanges);
    }

    @Override
    public String toString() {
        return "SortedS2Ranges{"
                + "mS2Level=" + mS2Level
                + ", mRanges=" + mRanges
                + '}';
    }
}
