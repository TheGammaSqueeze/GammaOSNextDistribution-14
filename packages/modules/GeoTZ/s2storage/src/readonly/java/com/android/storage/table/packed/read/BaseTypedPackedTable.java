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
import com.android.storage.table.reader.Table;

import java.util.Objects;

/**
 * The base class for tables that are stored as fixed length records where the key and value are
 * variable width and therefore may have restricted range. This class supports both signed and
 * unsigned types for the value type.
 *
 * @param <E> the type of {@link TableEntry} used
 */
abstract class BaseTypedPackedTable<E extends Table.TableEntry<E>> implements Table<E> {

    protected final PackedTableReader mTableReader;

    BaseTypedPackedTable(BlockData blockData, int maxValueTypeBits) {
        mTableReader = new PackedTableReader(blockData);
        if (mTableReader.getValueSizeBits() > maxValueTypeBits) {
            throw new IllegalArgumentException(
                    "The supplied packed table block can hold values greater than "
                            + maxValueTypeBits + " bits. Use a wider value type.");
        }
    }

    @Override
    public byte[] getSharedData() {
        return mTableReader.getSharedData();
    }

    @Override
    public E getEntryByIndex(int i) {
        return createEntry(mTableReader.getEntryByIndex(i));
    }

    /** Returns the number of entries in the table. */
    public int getEntryCount() {
        return mTableReader.getEntryCount();
    }

    /**
     * Implemented by subclasses to create an entry of the correct type.
     */
    protected abstract E createEntry(PackedTableReader.Entry entry);

    @Override
    public E getEntry(int key) {
        PackedTableReader.Entry entry = mTableReader.getEntry(key);
        return wrapEntryOrNull(entry);
    }

    /**
     * If {@code entry} is {@code null}, returns null, otherwise returns the result from {@link
     * #createEntry(PackedTableReader.Entry)}.
     */
    E wrapEntryOrNull(PackedTableReader.Entry entry) {
        if (entry == null) {
            return null;
        }
        return createEntry(entry);
    }

    /** A base class for {@link TableEntry} implementations in {@link BaseTypedPackedTable} */
    abstract class BasePackedTableEntry implements TableEntry<E> {

        protected final PackedTableReader.Entry mEntry;

        BasePackedTableEntry(PackedTableReader.Entry entry) {
            mEntry = Objects.requireNonNull(entry);
        }

        @Override
        public int getKey() {
            return mEntry.getKey();
        }

        @Override
        public int getIndex() {
            return mEntry.getIndex();
        }

        @Override
        public E getPrevious() {
            PackedTableReader.Entry previousEntry = mEntry.getPrevious();
            return wrapEntryOrNull(previousEntry);
        }

        @Override
        public E getNext() {
            PackedTableReader.Entry nextEntry = mEntry.getNext();
            return wrapEntryOrNull(nextEntry);
        }

        @Override
        public int hashCode() {
            return getKey();
        }

        @Override
        public abstract boolean equals(Object other);

        @Override
        public abstract String toString();
    }
}
