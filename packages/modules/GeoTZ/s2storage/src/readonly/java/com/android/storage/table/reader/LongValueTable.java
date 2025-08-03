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
 * A type for tables where the value can be expressed as a Java long or perhaps smaller.
 */
public interface LongValueTable extends Table<LongValueTable.TableEntry> {

    /**
     * Finds an entry using the supplied matcher via a binary search. If multiple entries match, an
     * arbitrary matching entry is returned. Returns {@code null} if no entries match.
     */
    TableEntry findEntry(LongValueEntryMatcher matcher);

    /**
     * A type for entries in {@link LongValueTable}.
     */
    interface TableEntry extends Table.TableEntry<TableEntry> {

        /**
         * Returns the entry's value.
         */
        long getValue();

        /**
         * Finds an entry using the supplied matcher via a binary search. Returns {@code null} if no
         * entry matches. Like {@link LongValueTable#findEntry(LongValueEntryMatcher)} but uses this
         * entry as a starting point.
         */
        TableEntry findNearbyEntry(LongValueEntryMatcher entryMatcher);

        /** A helper method for implementing {@link TableEntry#equals(Object)}. */
        static boolean equal(TableEntry one, Object two) {
            if (one == null && two == null) {
                return true;
            }
            if (!(two instanceof TableEntry) || one == null || one.getClass() != two.getClass()) {
                return false;
            }
            TableEntry other = (TableEntry) two;
            return (one.getKey() == other.getKey() && one.getValue() == other.getValue());
        }

        /** A helper method for implementing {@link TableEntry#toString()}. */
        static String toString(TableEntry entry) {
            return "TableEntry{key=" + entry.getKey() + ", value=" + entry.getValue() + "}";
        }
    }

    /**
     * A matcher that can be used with {@link LongValueTable}s to find entries in the table.
     */
    interface LongValueEntryMatcher {

        /**
         * Returns &lt; 0 if the entry sought is lower than one with the values provided, &gt; 0
         * if the entry sought is higher than one with the values provided, and exactly zero if it
         * is the entry sought.
         */
        int compare(int key, long value);
    }
}
