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

import com.android.storage.util.BitwiseUtils;

/** A class that can perform basic S2 operations on cell IDs. */
public final class S2Support {

    /** The number of bits in an S2 long cell ID devoted to the face. */
    public static final int FACE_BIT_COUNT = 3;

    /** The max S2 face ID. */
    public static final int MAX_FACE_ID = 5;

    /** The maximum S2 level. */
    public static final int MAX_S2_LEVEL = 30;

    private static final long FACE_BIT_MASK = 0b11100000L << 56;

    private static final int BIT_COUNT_PER_LEVEL = 2;

    /** The minimum cell ID. The index is S2 level. */
    private static final long[] MIN_CELL_ID;

    /** The maximum cell ID The index is S2 level. */
    private static final long[] MAX_CELL_ID;

    static {
        MIN_CELL_ID = new long[MAX_S2_LEVEL + 1];
        MAX_CELL_ID = new long[MAX_S2_LEVEL + 1];
        for (int i = 0; i < MIN_CELL_ID.length; i++) {
            MIN_CELL_ID[i] = calcMinS2CellId(i);
            MAX_CELL_ID[i] = calcMaxS2CellId(i);
        }
    }

    private S2Support() {
    }

    /** Converts an S2 cell ID to a human readable form, e.g. for debugging. */
    public static String cellIdToString(long cellId) {
        int level = getS2Level(cellId);
        int faceId = getValidFaceId(cellId);
        long index = getValidIndex(cellId);
        return Long.toUnsignedString(cellId) + " (level=" + level + " faceId=" + faceId
                + " index=" + index + ")";
    }

    private static int getValidFaceId(long cellId) {
        int faceId = getFaceIdValue(cellId);
        checkValidFaceId(faceId);
        return faceId;
    }

    /** Extracts the face ID bits from the cellId. Does not guarantee they are valid. */
    private static int getFaceIdValue(long cellId) {
        return (int) ((BitwiseUtils.getHighBitsMask(FACE_BIT_COUNT) & cellId)
                >>> (Long.SIZE - FACE_BIT_COUNT));
    }

    /**
     * Extracts the cell index from the cellId. Throws IllegalArgumentException if the cell ID is
     * invalid.
     */
    private static long getValidIndex(long cellId) {
        // Unused bits is the final 1 followed by a variable number of zeros.
        int unusedBitCount = Long.numberOfTrailingZeros(cellId) + 1;
        if (unusedBitCount > Long.SIZE - FACE_BIT_COUNT) {
            throw new IllegalArgumentException(
                    "There must be at least one bit set in every S2 cell ID");
        } else if (unusedBitCount % BIT_COUNT_PER_LEVEL == 0) {
            throw new IllegalArgumentException("Invalid number of index bits in " + cellId);
        }
        long indexBits = cellId & ~FACE_BIT_MASK;
        return indexBits >>> unusedBitCount;
    }

    /**
     * Creates a cell ID of the specified level, face ID and index. e.g. index 0 is the first cell
     * ID with that level and face ID, index 1 is the second, and so on.
     *
     * <p>If index is greater than or equal to the number of cells at the specified level an
     * {@link IllegalArgumentException} is thrown. If level == 0, index must be zero, otherwise an
     * {@link IllegalArgumentException} is thrown.
     */
    public static long cellId(int level, int faceId, long index) {
        checkValidFaceId(faceId);

        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, index=" + index);
        }
        long maxIndexValue = getMaxIndex(level);
        if (index > maxIndexValue) {
            throw new IllegalArgumentException(
                    "index=" + index + " > the max index value at level=" + level
                            + " (" + maxIndexValue + ")");
        }

        // storageBitCountForLevel includes the faceId and index bits, but not the trailing 1 needed
        // to indicate level.
        int storageBitCountForLevel = storageBitCountForLevel(level);
        // indexBitCount is just the bits needed to store the index at the given level.
        int indexBitCount = storageBitCountForLevel - FACE_BIT_COUNT;

        // suffixBitCount excludes the face bits but includes trailing 1 needed to indicate level.
        int suffixBitCount = indexBitCount + 1;

        // Introduce the trailing 1 to the index to generate the suffix.
        long suffix = (index << 1) | 1L;

        long cellId = ((long) faceId) << suffixBitCount;
        cellId |= suffix;
        cellId <<= Long.SIZE - (storageBitCountForLevel + 1);
        return cellId;
    }

    /**
     * Returns the max index value permitted for the specified S2 level. For level 0, this method
     * returns 0.
     */
    public static long getMaxIndex(int s2Level) {
        checkValidLevel(s2Level);
        return BitwiseUtils.getLowBitsMask(s2Level * BIT_COUNT_PER_LEVEL);
    }

    /**
     * Returns the number of bits used to store cell IDs at the specified level. i.e. excluding the
     * non-storage bits that consist of the trailing 1 used to indicate level and any zeros after
     * it.
     */
    public static int storageBitCountForLevel(int level) {
        checkValidLevel(level);
        return FACE_BIT_COUNT + (BIT_COUNT_PER_LEVEL * level);
    }

    /**
     * Throws an exception if the cell ID is invalid.
     */
    public static void validateCellId(long cellId) {
        getValidFaceId(cellId);
        getValidIndex(cellId);
    }

    /**
     * Offset the supplied cellId by {@code offset} cell IDs. offset can be negative. If the
     * absolute value of {@code offset} is &gt; the number of cell IDs at the S2 level of
     * {@code cellId} then an {@link IllegalArgumentException} is thrown.
     */
    public static long offsetCellId(long cellId, int offset) {
        // Validate the cell ID and capture values for use later.
        int level = getS2Level(cellId);
        int faceId = getValidFaceId(cellId);

        // Special case offset 0 because it's trivial.
        if (offset == 0) {
            return cellId;
        }

        // Validate the offset and calculate index values.
        final int faceCount = MAX_FACE_ID + 1;

        long cellsPerFace = 0;
        long cellsAtLevel = faceCount;
        if (level > 0) {
            cellsPerFace = 1L << (level * BIT_COUNT_PER_LEVEL);
            cellsAtLevel *= cellsPerFace;
        }

        // Validate the offset: any offset >= the number at the cells at the level will wrap more
        // than once and is probably a coding error.
        if (Math.abs(offset) >= cellsAtLevel) {
            throw new IllegalArgumentException(
                    "abs(offset) (offset=" + offset + ") > cellsAtLevel (" + cellsAtLevel + ")"
                            + " for cellId=" + cellIdToString(cellId));
        }

        // Special case level 0 because it's easy.
        if (level == 0) {
            faceId += offset;
            if (faceId < 0) {
                faceId += faceCount;
            } else if (faceId > MAX_FACE_ID) {
                faceId -= faceCount;
            }
            long facePlusTrailingBit = faceId << 1 | 1;
            return (facePlusTrailingBit) << (Long.SIZE - FACE_BIT_COUNT - 1);
        }

        // Handle the common level > 0 case.

        // Handle cell ID wrapping, etc.
        long cellIndex = getValidIndex(cellId);
        long newCellIndex = cellIndex + offset;
        int faceAdjustment = 0;
        while (newCellIndex < 0) {
            faceAdjustment--;
            newCellIndex += cellsPerFace;
        }
        while (newCellIndex >= cellsPerFace) {
            faceAdjustment++;
            newCellIndex -= cellsPerFace;
        }

        int newFaceId = faceId + faceAdjustment;
        if (newFaceId < 0) {
            newFaceId += faceCount;
        } else if (newFaceId >= faceCount) {
            newFaceId -= faceCount;
        }
        return cellId(level, newFaceId, newCellIndex);
    }

    /**
     * Returns the S2 level of the specified cell ID. If the cell ID is invalid it throws
     * {@link IllegalArgumentException}.
     */
    public static int getS2Level(long cellId) {
        int trailingZeroCount = Long.numberOfTrailingZeros(cellId);
        if (trailingZeroCount == Long.SIZE) {
            throw new IllegalArgumentException("No trailing bit in " + cellId);
        }
        int cellIdBitCount = Long.SIZE - trailingZeroCount;
        int cellIdBitCountWithoutFaceBitCount = cellIdBitCount - FACE_BIT_COUNT;
        if (cellIdBitCountWithoutFaceBitCount % BIT_COUNT_PER_LEVEL != 1) {
            throw new IllegalArgumentException(
                    cellId + " is not a valid cell Id, bitCount=" + cellIdBitCount);
        }
        return (cellIdBitCountWithoutFaceBitCount - 1) / BIT_COUNT_PER_LEVEL;
    }

    /** Returns the cell ID for the first cell for face 0 at the given level. */
    public static long getMinCellId(int s2Level) {
        checkValidLevel(s2Level);
        return MIN_CELL_ID[s2Level];
    }

    /** Returns the cell ID for the last cell for face {@link #MAX_FACE_ID} at the given level. */
    public static long getMaxCellId(int s2Level) {
        checkValidLevel(s2Level);
        return MAX_CELL_ID[s2Level];
    }

    private static void checkValidLevel(int s2Level) {
        if (s2Level < 0 || s2Level > MAX_S2_LEVEL) {
            throw new IllegalArgumentException("s2Level " + s2Level + " is invalid");
        }
    }

    private static void checkValidFaceId(int faceId) {
        if (faceId < 0 || faceId > MAX_FACE_ID) {
            throw new IllegalArgumentException("faceId=" + faceId + " is invalid");
        }
    }

    private static long calcMinS2CellId(int s2Level) {
        return cellId(s2Level, 0, 0);
    }

    private static long calcMaxS2CellId(int s2Level) {
        long maxIndex = getMaxIndex(s2Level);
        return cellId(s2Level, MAX_FACE_ID, maxIndex);
    }
}
