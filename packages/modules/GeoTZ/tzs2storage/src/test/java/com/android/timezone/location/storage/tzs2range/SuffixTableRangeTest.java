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

import static com.android.storage.s2.S2Support.cellId;
import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.storage.s2.S2Support;

import org.junit.Test;

/** Tests for {@link SuffixTableRange}. */
public class SuffixTableRangeTest {

    @Test
    public void constructorInvariants() {
        long startCellId = cellId(12, 5, 1);
        long endCellId = cellId(12, 5, 2);
        int tzIdSetId = 100;
        assertThrows(IllegalArgumentException.class,
                () -> new SuffixTableRange(startCellId, startCellId, tzIdSetId));
        assertThrows(IllegalArgumentException.class,
                () -> new SuffixTableRange(endCellId, startCellId, tzIdSetId));
    }

    @Test
    public void equalsAndHashcode() {
        int s2Level = 12;
        long startCellId = cellId(s2Level, 5, 1);
        long endCellId = cellId(s2Level, 5, 2);
        int tzIdSetId = 100;
        SuffixTableRange tzS2Range = new SuffixTableRange(startCellId, endCellId, tzIdSetId);
        SuffixTableRange tzS2Range2 = new SuffixTableRange(startCellId, endCellId, tzIdSetId);

        assertEquals(tzS2Range, tzS2Range2);
        assertEquals(tzS2Range.hashCode(), tzS2Range2.hashCode());
        assertEquals(startCellId, tzS2Range.getStartCellId());
        assertEquals(endCellId, tzS2Range.getEndCellId());
        assertEquals(tzIdSetId, tzS2Range.getTzIdSetId());

        SuffixTableRange tzS2Range3 = new SuffixTableRange(startCellId, endCellId, tzIdSetId + 1);
        assertNotEquals(tzS2Range, tzS2Range3);

        SuffixTableRange tzS2Range4 = new SuffixTableRange(
                startCellId, S2Support.offsetCellId(endCellId, 1), tzIdSetId);
        assertNotEquals(tzS2Range, tzS2Range4);
    }
}
