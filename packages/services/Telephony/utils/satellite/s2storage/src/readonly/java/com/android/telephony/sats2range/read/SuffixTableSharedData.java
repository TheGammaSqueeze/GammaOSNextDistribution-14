/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.telephony.sats2range.read;

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

    /**
     * Creates a {@link SuffixTableSharedData}. See also {@link #fromBytes(byte[])}.
     */
    public SuffixTableSharedData(int tablePrefix) {
        mTablePrefix = tablePrefix;
    }

    /**
     * Returns the S2 cell ID prefix associated with the table. i.e. all S2 ranges in the table will
     * have this prefix.
     */
    public int getTablePrefix() {
        return mTablePrefix;
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
        return mTablePrefix == that.mTablePrefix;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTablePrefix);
    }

    @Override
    public String toString() {
        return "SuffixTableSharedData{"
                + "mTablePrefix=" + mTablePrefix
                + '}';
    }

    /**
     * Creates a {@link SuffixTableSharedData} using shared data from a {@link Table}.
     */
    public static SuffixTableSharedData fromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                TypedInputStream tis = new TypedInputStream(bis)) {
            int tablePrefixValue = tis.readInt();
            return new SuffixTableSharedData(tablePrefixValue);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
