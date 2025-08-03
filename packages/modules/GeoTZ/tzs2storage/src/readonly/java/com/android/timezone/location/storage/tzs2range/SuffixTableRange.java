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

package com.android.timezone.location.storage.tzs2range;

import static com.android.storage.s2.S2Support.cellIdToString;

import com.android.storage.s2.S2CellOrdering;
import com.android.storage.s2.S2LevelRange;

import java.util.Objects;

/**
 * An {@link S2LevelRange} that additionally stores an ID for the (banked) TZ ID set. This class
 * is intended for small, inclusive ranges and so requires that the start cell ID is lower than the
 * end cell ID according to {@link S2CellOrdering} . The only exception is when the range is at the
 * very top end of the S2 space: since the range end is exclusive and therefore can wrap around, the
 * end cell ID is allowed to be the first ID of face 0.
 */
public final class SuffixTableRange extends S2LevelRange {

    private final int mTzIdSetId;

    public SuffixTableRange(long startCellId, long endCellId, int tzIdSetId) {
        super(startCellId, endCellId);
        if (mStartCellIdNumeric > mEndCellIdNumeric
                && mEndCellIdNumeric != S2CellOrdering.getMinCellIdNumeric(mS2Level)) {
            throw new IllegalArgumentException("startCellId=" + cellIdToString(startCellId)
                    + " is higher than  endCellId=" + cellIdToString(endCellId));
        }
        if (tzIdSetId < 0) {
            throw new IllegalArgumentException(
                    "tzIdSetId=" + tzIdSetId + " must not be negative");
        }
        mTzIdSetId = tzIdSetId;
    }

    /** Returns the TZ IDs set ID associated with this range. */
    public int getTzIdSetId() {
        return mTzIdSetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SuffixTableRange that = (SuffixTableRange) o;
        return mTzIdSetId == that.mTzIdSetId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mTzIdSetId);
    }

    @Override
    public String toString() {
        return "SuffixTableRange{"
                + "mStartCellId=" + cellIdToString(mStartCellId)
                + ", mEndCellId=" + cellIdToString(mEndCellId)
                + ", mS2Level=" + mS2Level
                + ", mTzIdSetId=" + mTzIdSetId
                + '}';
    }
}
