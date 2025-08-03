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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SortedS2RangesTest {

    @Test
    public void testCreateFromSorted_detectsUnsorted() {
        int s2Level = 15;
        S2LevelRange range0 = new S2LevelRange(cellId(s2Level, 1, 1000),
                cellId(s2Level, 1, 1001));
        S2LevelRange range1 = new S2LevelRange(cellId(s2Level, 1, 1001),
                cellId(s2Level, 1, 1002));
        assertThrows(IllegalArgumentException.class,
                () -> SortedS2Ranges.createFromSorted(s2Level, range1, range0));
    }

    @Test
    public void testCreateFromSorted() {
        int s2Level = 15;
        S2LevelRange range0 = new S2LevelRange(cellId(s2Level, 1, 1000),
                cellId(s2Level, 1, 1001));
        S2LevelRange range1 = new S2LevelRange(cellId(s2Level, 1, 1001),
                cellId(s2Level, 1, 1002));
        S2LevelRange range2 = new S2LevelRange(cellId(s2Level, 1, 1002),
                cellId(s2Level, 1, 1003));
        S2LevelRange range3 = new S2LevelRange(cellId(s2Level, 1, 1003),
                cellId(s2Level, 1, 1004));
        List<S2LevelRange> sortedRanges = Arrays.asList(range0, range1, range2, range3);
        SortedS2Ranges<S2LevelRange> ranges =
                SortedS2Ranges.createFromSorted(s2Level, sortedRanges);
        assertEquals(sortedRanges, ranges.getRanges());
        assertEquals(s2Level, ranges.getS2Level());
    }

    @Test
    public void testOverlapsIllegal() {
        int s2Level = 15;
        assertThrows(IllegalArgumentException.class, () -> SortedS2Ranges.createFromSorted(s2Level,
                new S2LevelRange(cellId(s2Level, 1, 1000), cellId(s2Level, 1, 1002)),
                new S2LevelRange(cellId(s2Level, 1, 1001), cellId(s2Level, 1, 1002))
        ));
    }

    @Test
    public void testMixedLevelsIllegal() {
        S2LevelRange level16Range = new S2LevelRange(cellId(16, 1, 1000), cellId(16, 1, 1001));
        int s2Level = 15;
        assertThrows(IllegalArgumentException.class,
                () -> SortedS2Ranges.createFromSorted(s2Level, level16Range));
    }
}
