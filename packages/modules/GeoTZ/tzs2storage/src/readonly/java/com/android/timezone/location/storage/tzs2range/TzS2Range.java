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
import com.android.storage.s2.S2Support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link S2LevelRange} that additionally stores a set of time zone ID strings. This class
 * is intended for small, inclusive ranges and so requires that the start cell ID is lower according
 * to {@link S2CellOrdering} than the end cell ID. The only exception is when the range is at the
 * very top end of the S2 space: since the range end is exclusive and therefore can wrap around, the
 * end cell ID is allowed to be the first ID of face 0.
 */
public final class TzS2Range extends S2LevelRange {

    private final List<String> mTzIdSet;

    public TzS2Range(long startCellId, long endCellId, List<String> tzIdSet) {
        super(startCellId, endCellId);
        if (mStartCellIdNumeric > mEndCellIdNumeric
                && mEndCellIdNumeric != S2CellOrdering.getMinCellIdNumeric(mS2Level)) {
            throw new IllegalArgumentException("startCellId=" + cellIdToString(startCellId)
                    + " is higher than  endCellId=" + cellIdToString(endCellId));
        }
        mTzIdSet = Collections.unmodifiableList(new ArrayList<>(tzIdSet));
    }

    public List<String> getTzIdSet() {
        return mTzIdSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TzS2Range tzS2Range = (TzS2Range) o;
        return super.equals(o) && mTzIdSet.equals(tzS2Range.mTzIdSet);
    }

    @Override
    public String toString() {
        return "TzS2Range{"
                + "mStartCellId=" + S2Support.cellIdToString(mStartCellId)
                + ", mEndCellId=" + S2Support.cellIdToString(mEndCellId)
                + ", mS2Level=" + cellIdToString(mS2Level)
                + ", mTzIdSet=" + mTzIdSet
                + '}';
    }
}
