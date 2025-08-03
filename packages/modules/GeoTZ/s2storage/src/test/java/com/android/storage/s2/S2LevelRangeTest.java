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

import static com.android.storage.s2.S2Support.cellId;
import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class S2LevelRangeTest {

    @Test
    public void constructor() {
        long lowCellId = cellId(12, 1, 100);
        long nextLowCellId = cellId(12, 3, 101);
        long highCellId = cellId(12, 5, 110);
        new S2LevelRange(highCellId, lowCellId);
        assertThrows(IllegalArgumentException.class, () -> new S2LevelRange(lowCellId, lowCellId));
        new S2LevelRange(lowCellId, nextLowCellId);
        new S2LevelRange(lowCellId, highCellId);

        long highCellIdDifferentLevel = cellId(15, 2, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new S2LevelRange(lowCellId, highCellIdDifferentLevel));
    }

    @Test
    public void contains() {
        S2LevelRange range1 = new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 3));
        assertFalse(range1.contains(cellId(12, 1, 0)));
        assertTrue(range1.contains(cellId(12, 1, 1)));
        assertTrue(range1.contains(cellId(12, 1, 2)));
        assertFalse(range1.contains(cellId(12, 1, 3)));
        assertFalse(range1.contains(cellId(12, 1, 4)));

        // A range that wraps around (end < start)
        S2LevelRange range2 = new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 1));
        assertTrue(range2.contains(cellId(12, 1, 0)));
        assertFalse(range2.contains(cellId(12, 1, 1)));
        assertFalse(range2.contains(cellId(12, 1, 2)));
        assertTrue(range2.contains(cellId(12, 1, 3)));
        assertTrue(range2.contains(cellId(12, 1, 4)));
    }

    @Test
    public void overlaps() {
        {
            S2LevelRange range1 = new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 3));
            assertTrue(range1.overlaps(range1));

            assertFalse(range1.overlaps(new S2LevelRange(cellId(12, 1, 0), cellId(12, 1, 1))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 0), cellId(12, 1, 2))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 2))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 2), cellId(12, 1, 3))));
            assertFalse(range1.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 4))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 2), cellId(12, 1, 4))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 3))));

            // Test a range argument that wraps around.
            assertFalse(range1.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 1))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 2))));
            assertTrue(range1.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 2))));
            assertFalse(range1.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 1))));
            assertFalse(range1.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 0))));
        }

        {
            // A range that wraps around (end < start)
            S2LevelRange range2 = new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 1));
            assertTrue(range2.overlaps(range2));

            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 0), cellId(12, 1, 1))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 0), cellId(12, 1, 2))));
            assertFalse(range2.overlaps(new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 2))));
            assertFalse(range2.overlaps(new S2LevelRange(cellId(12, 1, 2), cellId(12, 1, 3))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 4))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 2), cellId(12, 1, 4))));
            assertFalse(range2.overlaps(new S2LevelRange(cellId(12, 1, 1), cellId(12, 1, 3))));

            // Test a range argument that wraps around.
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 1))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 3), cellId(12, 1, 2))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 2))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 1))));
            assertTrue(range2.overlaps(new S2LevelRange(cellId(12, 1, 4), cellId(12, 1, 0))));
        }
    }

    @Test
    public void equals() {
        long lowCellId = cellId(12, 1, 100);
        long midCellId = cellId(12, 1, 105);
        long highCellId = cellId(12, 1, 110);
        assertEquals(new S2LevelRange(lowCellId, highCellId),
                new S2LevelRange(lowCellId, highCellId));
        assertNotEquals(new S2LevelRange(lowCellId, highCellId),
                new S2LevelRange(midCellId, highCellId));
    }

    @Test
    public void startCellIdComparator() {
        S2LevelRange lowRange = new S2LevelRange(
                cellId(12, 0, 0), cellId(12, S2Support.MAX_FACE_ID, 500));
        S2LevelRange midRange = new S2LevelRange(
                cellId(12, 2, 0), cellId(12, S2Support.MAX_FACE_ID, 500));
        S2LevelRange highRange = new S2LevelRange(cellId(12, S2Support.MAX_FACE_ID, 0),
                cellId(12, S2Support.MAX_FACE_ID, 500));
        assertEquals(0, S2LevelRange.COMPARATOR.compare(lowRange, lowRange));
        assertEquals(-1, S2LevelRange.COMPARATOR.compare(lowRange, midRange));
        assertEquals(-1, S2LevelRange.COMPARATOR.compare(lowRange, highRange));
        assertEquals(1, S2LevelRange.COMPARATOR.compare(midRange, lowRange));
        assertEquals(0, S2LevelRange.COMPARATOR.compare(midRange, midRange));
        assertEquals(-1, S2LevelRange.COMPARATOR.compare(midRange, highRange));
        assertEquals(1, S2LevelRange.COMPARATOR.compare(highRange, lowRange));
        assertEquals(1, S2LevelRange.COMPARATOR.compare(highRange, midRange));
        assertEquals(0, S2LevelRange.COMPARATOR.compare(highRange, highRange));
    }
}
