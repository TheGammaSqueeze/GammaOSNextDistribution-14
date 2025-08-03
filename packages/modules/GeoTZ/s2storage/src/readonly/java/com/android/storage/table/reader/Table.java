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

package com.android.storage.table.reader;

/**
 * A table containing entries with a signed, int key. A table can also have an array of shared data
 * that can be used, for example, to hold information shared by all entries in the table.
 *
 * @param <E> The subtype of the {@link TableEntry} used for entries.
 */
public interface Table<E extends Table.TableEntry> {

    /**
     * Returns the table's unstructured shared data that can be used, for example, to hold
     * information shared by all entries in the table.
     */
    byte[] getSharedData();

    /**
     * Returns a table entry associated with the key, or {@code null} if there isn't one. If
     * multiple entries have the key, then an arbitrary entry with the key is returned.
     */
    E getEntry(int key);

    /**
     * Returns the entry with the specified index, index starts at zero. Throws
     * {@link IndexOutOfBoundsException} if the index is invalid.
     */
    E getEntryByIndex(int i);

    /** Returns the number of entries in the table. */
    int getEntryCount();

    /**
     * An entry in a {@link Table}.
     *
     * @param <T> the type of the entry
     */
    interface TableEntry<T extends TableEntry<T>> {

        /** Returns the entry's index in the table. */
        int getIndex();

        /** Returns the entry's key. */
        int getKey();

        /** Returns the previous entry, or {@code null} if there isn't one. */
        T getPrevious();

        /** Returns the next entry, or {@code null} if there isn't one. */
        T getNext();
    }
}
