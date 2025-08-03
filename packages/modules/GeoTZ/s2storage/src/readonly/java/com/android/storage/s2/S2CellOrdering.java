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

import static com.android.storage.s2.S2Support.getMaxCellId;
import static com.android.storage.s2.S2Support.getMinCellId;

/** A class that provides a natural ordering for S2 cell IDs. */
public final class S2CellOrdering {

    private static final long[] MIN_CELL_ID_NUMERIC;

    private static final long[] MAX_CELL_ID_NUMERIC;

    static {
        MIN_CELL_ID_NUMERIC = new long[S2Support.MAX_S2_LEVEL + 1];
        MAX_CELL_ID_NUMERIC = new long[S2Support.MAX_S2_LEVEL + 1];
        for (int i = 0; i < MIN_CELL_ID_NUMERIC.length; i++) {
            MIN_CELL_ID_NUMERIC[i] = asUnsignedNumeric(getMinCellId(i));
            MAX_CELL_ID_NUMERIC[i] = asUnsignedNumeric(getMaxCellId(i));
        }
    }


    private S2CellOrdering() {
    }

    /**
     * Turns a cell ID into a value that can be compared arithmetically to provide a natural order,
     * i.e. ordered by face ID then index of the cell on that face. Necessary because face ID is
     * stored in the top 3 MSB of a long and Java's longs are signed, leading to face 4 and 5 having
     * negative cell IDs.
     */
    public static long asUnsignedNumeric(long cellId) {
        // Convert to an unsigned integer. The LSB is only used for level 30 where it is always 1 so
        // its always safe to do this on a valid cell ID: the value won't be meaningful, and is only
        // good for comparing cell IDs of the same level, but ordering will be preserved.
        return cellId >>> 1;
    }

    /**
     * Returns the smallest value that could be returned by {@link #asUnsignedNumeric(long)} for
     * a range of this level.
     */
    public static long getMinCellIdNumeric(int s2Level) {
        return MIN_CELL_ID_NUMERIC[s2Level];
    }

    /**
     * Returns the largest value that could be returned by {@link #asUnsignedNumeric(long)} for
     * a range of this level.
     */
    public static long getMaxCellIdNumeric(int s2Level) {
        return MAX_CELL_ID_NUMERIC[s2Level];
    }
}
