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

package com.android.storage.table.packed.read;

import com.android.storage.block.read.BlockData;
import com.android.storage.table.reader.IntValueTable;

/**
 * A key-ordered table implementation that holds values using a fixed-length entry. The combined
 * size of the key and the value must be exactly the length of the entry, so increasing the size of
 * the key decreases the range of values that can be stored. In a table using N key bits, valid keys
 * are 0 to (2^N)-1. Not all keys have an entry but a key can have multiple entries. This class can
 * support signed values up to 31-bits in size, unsigned values up to 32-bits in size.
 */
public final class IntValueTypedPackedTable extends BaseTypedPackedTable<IntValueTable.TableEntry>
        implements IntValueTable {

    public IntValueTypedPackedTable(BlockData blockData) {
        super(blockData, Integer.SIZE);
        boolean signedValue = mTableReader.isValueSigned();
        if (mTableReader.getValueSizeBits() == Integer.SIZE && !signedValue) {
            throw new IllegalArgumentException(
                    "Cannot use unsigned with " + Integer.SIZE + " bits");
        }
    }

    @Override
    public IntValueTable.TableEntry findEntry(IntValueEntryMatcher entryMatcher) {
        PackedTableReader.Entry entry = mTableReader.findIntValueEntry(entryMatcher);
        if (entry == null) {
            return null;
        }
        return createEntry(entry);
    }

    @Override
    protected IntValueTable.TableEntry createEntry(PackedTableReader.Entry entry) {
        return new IntValuePackedTableEntry(entry);
    }

    private class IntValuePackedTableEntry extends BasePackedTableEntry
            implements IntValueTable.TableEntry {

        IntValuePackedTableEntry(PackedTableReader.Entry entry) {
            super(entry);
        }

        @Override
        public int getValue() {
            return mEntry.getIntValue();
        }

        @Override
        public IntValueTable.TableEntry findNearbyEntry(IntValueEntryMatcher entryMatcher) {
            PackedTableReader.Entry nearbyEntry = mEntry.findNearbyEntry(entryMatcher);
            return wrapEntryOrNull(nearbyEntry);
        }

        @Override
        public boolean equals(Object other) {
            return IntValueTable.TableEntry.equal(this, other);
        }

        @Override
        public String toString() {
            return IntValueTable.TableEntry.toString(this);
        }
    }
}
