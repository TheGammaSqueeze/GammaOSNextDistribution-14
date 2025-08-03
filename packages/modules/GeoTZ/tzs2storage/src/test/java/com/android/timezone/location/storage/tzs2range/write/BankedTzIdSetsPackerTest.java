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

package com.android.timezone.location.storage.tzs2range.write;

import static com.android.storage.s2.S2Support.cellId;
import static com.android.storage.testing.MoreAsserts.assertThrows;
import static com.android.storage.testing.TestSupport.listOf;
import static com.android.storage.testing.TestSupport.setOf;

import static org.junit.Assert.assertEquals;

import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.TzS2Range;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for {@link BankedTzIdSetsPacker}. */
public class BankedTzIdSetsPackerTest {

    @Test
    public void simplePacking() {
        BankedTzIdSetsPacker packer = new BankedTzIdSetsPacker(5);
        List<List<String>> idSet1 = listOf(
                listOf("One"),
                listOf("One", "Two"),
                listOf("One", "Two", "Three"),
                listOf("One", "Two", "Three", "Four"),
                listOf("One", "Two", "Three", "Four", "Five"));
        BankedTzIdSetsPacker.BankHelper bank1Helper = packer.addTzIdSets(idSet1);
        assertEquals(0, bank1Helper.getId());

        List<List<String>> idSet2 = listOf(
                listOf("Two"),
                listOf("Two", "Three"),
                listOf("Two", "Three", "Four"),
                listOf("Two", "Three", "Four", "Five"),
                listOf("Two", "Three", "Four", "Five", "Six"));
        BankedTzIdSetsPacker.BankHelper bank2Helper = packer.addTzIdSets(idSet2);
        assertEquals(1, bank2Helper.getId());

        BankedTzIdSets bankedTzIdSets = packer.create();
        assertEquals(setOf("One", "Two", "Three", "Four", "Five", "Six"),
                new HashSet<>(bankedTzIdSets.getStringsByIndex()));
        assertEquals(2, bankedTzIdSets.getBankCount());
        BankedTzIdSets.Bank bank1 = bankedTzIdSets.getBank(0);
        assertEquals(listOf("One"), bank1.getTzIdSet(0).getTzIds());
        assertEquals(listOf("One", "Two"), bank1.getTzIdSet(1).getTzIds());

        BankedTzIdSets.Bank bank2 = bankedTzIdSets.getBank(1);
        assertEquals(listOf("Two"), bank2.getTzIdSet(0).getTzIds());
        assertEquals(listOf("Two", "Three"), bank2.getTzIdSet(1).getTzIds());
    }

    @Test
    public void overflowPacking() {
        BankedTzIdSetsPacker packer = new BankedTzIdSetsPacker(5);
        List<List<String>> idSetA1 = listOf(
                listOf("One"),
                listOf("One", "Two"),
                listOf("One", "Two", "Three"),
                listOf("One", "Two", "Three", "Four"));
        BankedTzIdSetsPacker.BankHelper bankA1Helper = packer.addTzIdSets(idSetA1);
        assertEquals(0, bankA1Helper.getId());

        List<List<String>> idSetA2 = listOf(listOf("One", "Two", "Three", "Four", "Five"));
        BankedTzIdSetsPacker.BankHelper bankA2Helper = packer.addTzIdSets(idSetA2);
        assertEquals(0, bankA2Helper.getId());

        BankedTzIdSetsPacker.BankHelper bankA1Helper2 = packer.addTzIdSets(idSetA1);
        assertEquals(0, bankA1Helper2.getId());

        BankedTzIdSetsPacker.BankHelper bankA2Helper2 = packer.addTzIdSets(idSetA2);
        assertEquals(0, bankA2Helper2.getId());

        List<List<String>> idSetB = listOf(listOf("Two"));
        BankedTzIdSetsPacker.BankHelper bankBHelper = packer.addTzIdSets(idSetB);
        assertEquals(1, bankBHelper.getId());
    }

    @Test
    public void exceedMaxBankSize() {
        BankedTzIdSetsPacker packer = new BankedTzIdSetsPacker(3);
        // If the bank max size is 3, four different sets cannot be stored.
        List<List<String>> idSet = listOf(
                listOf("One"),
                listOf("One", "Two"),
                listOf("One", "Two", "Three"),
                listOf("One", "Two", "Three", "Four"));
        assertThrows(IllegalArgumentException.class, () -> packer.addTzIdSets(idSet));
    }

    @Test
    public void extractUniqueTzIdSets() {
        List<TzS2Range> ranges = listOf(new TzS2Range(cellId(12, 1, 1), cellId(12, 1, 2),
                        listOf("One", "Two", "Three")),
                new TzS2Range(cellId(12, 1, 2), cellId(12, 1, 3), listOf("One")),
                new TzS2Range(cellId(12, 1, 3), cellId(12, 1, 4), listOf("Two")),
                new TzS2Range(cellId(12, 1, 4), cellId(12, 1, 5), listOf("One")),
                new TzS2Range(cellId(12, 1, 5), cellId(12, 1, 6),
                        listOf("One", "Two", "Three")),
                new TzS2Range(cellId(12, 1, 6), cellId(12, 1, 7), listOf("Two", "Three")),
                new TzS2Range(cellId(12, 1, 7), cellId(12, 1, 8), listOf("Three")));
        List<List<String>> uniqueTzIdSetsList = BankedTzIdSetsPacker.extractUniqueTzIdSets(ranges);
        Set<List<String>> uniqueTzIdSetsSet = new HashSet<>(uniqueTzIdSetsList);
        assertEquals(uniqueTzIdSetsList.size(), uniqueTzIdSetsSet.size());

        Set<List<String>> expectedValuesSet = setOf(
                listOf("One"),
                listOf("Two"),
                listOf("Three"),
                listOf("Two", "Three"),
                listOf("One", "Two", "Three")
        );
        assertEquals(expectedValuesSet, uniqueTzIdSetsSet);
    }
}
