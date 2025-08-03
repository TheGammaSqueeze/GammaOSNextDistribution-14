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

import static com.android.storage.s2.S2Support.cellIdToString;

import java.util.Comparator;
import java.util.Objects;

/**
 * A range of S2 cell IDs consisting of a startCellId (inclusive) and an endCellId (exclusive). The
 * start and end cell ID must have the same S2 level. This class can cope with ranges that wrap
 * around (i.e. from face 5 to face 0), but the cell IDs provided must be valid. Subclasses may
 * apply additional constraints.
 */
public class S2LevelRange {

    /**
     * Orders S2LevelRanges by startCellId then by endCellId.
     */
    public static Comparator<? super S2LevelRange> COMPARATOR = (o1, o2) -> {
        long diff = o2.mStartCellIdNumeric - o1.mStartCellIdNumeric;
        if (diff < 0) {
            return 1;
        } else if (diff > 0) {
            return -1;
        }
        long endDiff = o2.mEndCellIdNumeric - o1.mEndCellIdNumeric;
        return endDiff < 0 ? 1 : (endDiff > 0 ? -1 : 0);
    };

    // Inclusive
    protected final long mStartCellId;

    /**
     * A modified value for mStartCellId that can be compared numerically against other cells of the
     * same level to determine a natural ordering. See {@link
     * S2CellOrdering#asUnsignedNumeric(long)}.
     */
    protected final long mStartCellIdNumeric;

    // Exclusive
    protected final long mEndCellId;

    /**
     * A modified value for mEndCellId that can be compared numerically against other cells of the
     * same level to determine ordering. See {@link S2CellOrdering#asUnsignedNumeric(long)}.
     */
    protected final long mEndCellIdNumeric;

    protected final int mS2Level;

    /**
     * Creates a range of S2 cell IDs from {@code startCellId} (inclusive) to {@code endCellId}
     * (exclusive). Both values must have the same S2 level otherwise {@link
     * IllegalArgumentException} will be thrown.
     */
    public S2LevelRange(long startCellId, long endCellId) {
        S2Support.validateCellId(startCellId);
        S2Support.validateCellId(endCellId);
        int startS2Level = S2Support.getS2Level(startCellId);
        int endS2Level = S2Support.getS2Level(endCellId);
        // Validate the levels are the same.
        if (startS2Level != endS2Level) {
            throw new IllegalArgumentException("startCellId " + cellIdToString(startCellId)
                    + " level=" + startS2Level + " != endCellId " + cellIdToString(endCellId)
                    + " level=" + endS2Level);
        }

        // Rule out the empty range / complete range ambiguity case.
        if (startCellId == endCellId) {
            throw new IllegalArgumentException("A range cannot be zero length,"
                    + " startCellId=" + startCellId + ", endCellId=" + endCellId);
        }
        long startCellIdNumeric = S2CellOrdering.asUnsignedNumeric(startCellId);
        long endCellIdNumeric = S2CellOrdering.asUnsignedNumeric(endCellId);

        mStartCellId = startCellId;
        mStartCellIdNumeric = startCellIdNumeric;
        mEndCellId = endCellId;
        mEndCellIdNumeric = endCellIdNumeric;
        mS2Level = startS2Level;
    }

    /** Returns the S2 level for this range. */
    public int getS2Level() {
        return mS2Level;
    }

    /** Returns the (inclusive) start cell ID. */
    public long getStartCellId() {
        return mStartCellId;
    }

    /** Returns the (exclusive) end cell ID. */
    public long getEndCellId() {
        return mEndCellId;
    }

    /**
     * Returns {@code true} if the range contains the supplied cell ID. The cell ID must have the
     * same S2 level as the range.
     */
    public boolean contains(long s2CellId) {
        long level = S2Support.getS2Level(s2CellId);
        if (level != mS2Level) {
            throw new IllegalArgumentException(s2CellId + "("
                    + S2Support.cellIdToString(s2CellId) + " is not at level " + mS2Level);
        }
        long s2CellIdNumeric = S2CellOrdering.asUnsignedNumeric(s2CellId);
        if (mStartCellIdNumeric < mEndCellIdNumeric) {
            return s2CellIdNumeric >= mStartCellIdNumeric && s2CellIdNumeric < mEndCellIdNumeric;
        } else {
            return s2CellIdNumeric >= mStartCellIdNumeric || s2CellIdNumeric < mEndCellIdNumeric;
        }
    }

    /**
     * Returns {@code true} if this range has a "lower" cell ID than the supplied range. The
     * supplied range must be at the same level as this range otherwise an
     * {@link IllegalArgumentException} is thrown.
     */
    public boolean startsBefore(S2LevelRange other) {
        checkSameLevel(other);
        return this.mStartCellIdNumeric < other.mStartCellIdNumeric;
    }

    /**
     * Returns {@code true} if the range overlaps with the supplied range. The supplied range must
     * be at the same level as this range otherwise an {@link IllegalArgumentException} is thrown.
     */
    public boolean overlaps(S2LevelRange other) {
        checkSameLevel(other);

        // This could probably be reduced but it wouldn't make it clearer.
        if (this.mStartCellIdNumeric < this.mEndCellIdNumeric) {
            // "this" is a range that does not wrap around.
            if (other.mStartCellIdNumeric < other.mEndCellIdNumeric) {
                // "other"" is a range that does not wrap around.
                return other.mStartCellIdNumeric < this.mEndCellIdNumeric
                        && other.mEndCellIdNumeric > this.mStartCellIdNumeric;
            } else {
                // "other" is a range that wraps around.
                return other.mStartCellIdNumeric < this.mEndCellIdNumeric
                        || other.mEndCellIdNumeric > this.mStartCellIdNumeric;
            }
        } else {
            // "this" is a range that wraps around.
            if (other.mStartCellIdNumeric < other.mEndCellIdNumeric) {
                // "other" is a range that does not wrap around.
                return this.mStartCellIdNumeric < other.mEndCellIdNumeric
                        || this.mEndCellIdNumeric > other.mStartCellIdNumeric;
            } else {
                // "other" is a range that wraps around.
                return true;
            }
        }
    }

    private void checkSameLevel(S2LevelRange other) {
        if (this.mS2Level != other.mS2Level) {
            throw new IllegalArgumentException(
                    "Cannot compare ranges of different levels. this=" + this + ", other=" + other);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        S2LevelRange tzS2Range = (S2LevelRange) o;
        return mStartCellId == tzS2Range.mStartCellId
                && mEndCellId == tzS2Range.mEndCellId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartCellId, mEndCellId);
    }

    @Override
    public String toString() {
        return "S2LevelRange{"
                + "mS2Level=" + mS2Level
                + ", mStartCellId=" + cellIdToString(mStartCellId)
                + ", mEndCellId=" + cellIdToString(mEndCellId)
                + '}';
    }
}
