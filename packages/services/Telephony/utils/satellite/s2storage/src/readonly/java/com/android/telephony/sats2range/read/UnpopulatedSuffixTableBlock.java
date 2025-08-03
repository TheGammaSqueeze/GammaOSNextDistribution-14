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

/**
 * An implementation of {@link SuffixTableBlock.SuffixTableBlockDelegate} for tables that are not
 * backed by real block data, i.e. have zero entries.
 */
final class UnpopulatedSuffixTableBlock implements SuffixTableBlock.SuffixTableBlockDelegate {

    private final int mPrefix;

    UnpopulatedSuffixTableBlock(int prefix) {
        mPrefix = prefix;
    }

    @Override
    public int getPrefix() {
        return mPrefix;
    }

    @Override
    public SuffixTableBlock.Entry findEntryByCellId(long cellId) {
        return null;
    }

    @Override
    public SuffixTableBlock.Entry findEntryByIndex(int i) {
        throw new IndexOutOfBoundsException("Unpopulated table");
    }

    @Override
    public int getEntryCount() {
        return 0;
    }
}
