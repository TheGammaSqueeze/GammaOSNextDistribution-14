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
import static com.android.storage.testing.TestSupport.listOf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.storage.s2.S2Support;

import org.junit.Test;

import java.util.List;

/** Tests for {@link TzS2Range}. */
public class TzS2RangeTest {

    @Test
    public void constructorInvariants() {
        long startCellId = cellId(12, 5, 1);
        long endCellId = cellId(12, 5, 2);
        List<String> tzIds = listOf("One", "Two");
        assertThrows(IllegalArgumentException.class,
                () -> new TzS2Range(startCellId, startCellId, tzIds));
        assertThrows(IllegalArgumentException.class,
                () -> new TzS2Range(endCellId, startCellId, tzIds));
    }

    @Test
    public void equalsAndHashcode() {
        int s2Level = 12;
        long startCellId = cellId(s2Level, 5, 1);
        long endCellId = cellId(s2Level, 5, 2);
        List<String> tzIdSet = listOf("One", "Two");
        TzS2Range tzS2Range = new TzS2Range(startCellId, endCellId, tzIdSet);
        TzS2Range tzS2Range2 = new TzS2Range(startCellId, endCellId, tzIdSet);

        assertEquals(tzS2Range, tzS2Range2);
        assertEquals(tzS2Range.hashCode(), tzS2Range2.hashCode());
        assertEquals(startCellId, tzS2Range.getStartCellId());
        assertEquals(endCellId, tzS2Range.getEndCellId());
        assertEquals(tzIdSet, tzS2Range.getTzIdSet());

        TzS2Range tzS2Range3 = new TzS2Range(startCellId, endCellId, listOf("Not One", "Not Two"));
        assertNotEquals(tzS2Range, tzS2Range3);

        TzS2Range tzS2Range4 = new TzS2Range(startCellId, S2Support.offsetCellId(endCellId, 1),
                tzIdSet);
        assertNotEquals(tzS2Range, tzS2Range4);
    }
}
