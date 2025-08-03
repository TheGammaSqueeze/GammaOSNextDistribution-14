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

import static com.android.storage.testing.MoreAsserts.assertThrows;
import static com.android.storage.testing.TestSupport.listOf;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** Tests for {@link BankedTzIdSets}. */
public class BankedTzIdSetsTest {

    @Test
    public void test() {
        List<String> stringsByIndex = listOf("One", "Two", "Three", "Four", "Five");
        BankedTzIdSets.Builder builder = new BankedTzIdSets.Builder()
                .setStringsByIndex(stringsByIndex);
        BankedTzIdSets.Builder.BankBuilder bank1Builder = builder.newBank();
        bank1Builder.addTzIdSet(listOf(0))
                .addTzIdSet(listOf(0, 1))
                .addTzIdSet(listOf(0, 1, 2));

        BankedTzIdSets.Builder.BankBuilder bank2Builder = builder.newBank();
        bank2Builder.addTzIdSet(listOf(1))
                .addTzIdSet(listOf(1, 2))
                .addTzIdSet(listOf(1, 2, 3))
                .addTzIdSet(listOf(1, 2, 3, 4));

        BankedTzIdSets bankedTzIdSets = builder.build();

        assertEquals(2, bankedTzIdSets.getBankCount());
        assertEquals(stringsByIndex, bankedTzIdSets.getStringsByIndex());

        assertThrows(IndexOutOfBoundsException.class, () -> bankedTzIdSets.getBank(-1));

        BankedTzIdSets.Bank bank1 = bankedTzIdSets.getBank(0);
        assertEquals(3, bank1.getTzIdSetCount());
        assertEquals(listOf("One"), bank1.getTzIdSet(0).getTzIds());
        assertEquals(listOf(0), bank1.getTzIdSet(0).getStringIds());
        assertEquals(listOf("One", "Two"), bank1.getTzIdSet(1).getTzIds());
        assertEquals(listOf(0, 1), bank1.getTzIdSet(1).getStringIds());
        assertEquals(listOf("One", "Two", "Three"), bank1.getTzIdSet(2).getTzIds());
        assertEquals(listOf(0, 1, 2), bank1.getTzIdSet(2).getStringIds());

        BankedTzIdSets.Bank bank2 = bankedTzIdSets.getBank(1);
        assertEquals(4, bank2.getTzIdSetCount());
        assertEquals(listOf("Two"), bank2.getTzIdSet(0).getTzIds());
        assertEquals(listOf(1), bank2.getTzIdSet(0).getStringIds());
        assertEquals(listOf("Two", "Three"), bank2.getTzIdSet(1).getTzIds());
        assertEquals(listOf(1, 2), bank2.getTzIdSet(1).getStringIds());
        assertEquals(listOf("Two", "Three", "Four"), bank2.getTzIdSet(2).getTzIds());
        assertEquals(listOf(1, 2, 3), bank2.getTzIdSet(2).getStringIds());
        assertEquals(listOf("Two", "Three", "Four", "Five"), bank2.getTzIdSet(3).getTzIds());
        assertEquals(listOf(1, 2, 3, 4), bank2.getTzIdSet(3).getStringIds());

        assertThrows(IndexOutOfBoundsException.class, () -> bankedTzIdSets.getBank(2));
    }
}
