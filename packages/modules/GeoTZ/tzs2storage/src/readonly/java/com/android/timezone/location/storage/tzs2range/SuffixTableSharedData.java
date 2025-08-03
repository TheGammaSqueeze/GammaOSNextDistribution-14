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

import com.android.storage.io.read.TypedInputStream;
import com.android.storage.table.reader.Table;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Shared data for a suffix table held in a suffix table block: the information applies to all
 * entries in the table and is required when interpreting the table's block data.
 */
public final class SuffixTableSharedData {

    private final int mTablePrefix;

    private final int mTzIdSetBank;

    /**
     * Creates a {@link SuffixTableSharedData}. See also {@link #fromBytes(byte[])}.
     */
    public SuffixTableSharedData(int tablePrefix, int tzIdSetBank) {
        mTablePrefix = tablePrefix;
        mTzIdSetBank = tzIdSetBank;
    }

    /**
     * Returns the S2 cell ID prefix associated with the table. i.e. all S2 ranges in the table will
     * have this prefix.
     */
    public int getTablePrefix() {
        return mTablePrefix;
    }

    /**
     * Returns the ID for the TZ Set ID bank associated with this table. All
     * {@link SuffixTableRange} entries in the table will reference sets from this bank.
     * See {@link BankedTzIdSets}.
     */
    public int getTzIdSetBank() {
        return mTzIdSetBank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SuffixTableSharedData that = (SuffixTableSharedData) o;
        return mTablePrefix == that.mTablePrefix
                && mTzIdSetBank == that.mTzIdSetBank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTablePrefix, mTzIdSetBank);
    }

    @Override
    public String toString() {
        return "SuffixTableSharedData{"
                + "mTablePrefix=" + mTablePrefix
                + ", mTzIdSetBank=" + mTzIdSetBank
                + '}';
    }

    /**
     * Creates a {@link SuffixTableSharedData} using shared data from a {@link Table}.
     */
    public static SuffixTableSharedData fromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                TypedInputStream tis = new TypedInputStream(bis)) {
            int tablePrefixValue = tis.readInt();
            int tzIdSetBank = tis.readInt();
            if (tzIdSetBank < 0) {
                throw new IllegalArgumentException(
                        "tzIdSetBank=" + tzIdSetBank + " must not be negative");
            }
            return new SuffixTableSharedData(tablePrefixValue, tzIdSetBank);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
