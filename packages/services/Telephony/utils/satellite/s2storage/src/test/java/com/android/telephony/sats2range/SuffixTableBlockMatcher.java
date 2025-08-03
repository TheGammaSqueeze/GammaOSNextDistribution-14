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

package com.android.telephony.sats2range;

import com.android.telephony.sats2range.read.SuffixTableBlock;

import org.mockito.ArgumentMatcher;

import java.util.Objects;

/** A matcher for {@link SuffixTableBlock} - checks all the various fields and content. */
public class SuffixTableBlockMatcher implements ArgumentMatcher<SuffixTableBlock> {

    private final SuffixTableBlock mSuffixTableBlock;

    public SuffixTableBlockMatcher(SuffixTableBlock suffixTableBlock) {
        mSuffixTableBlock = suffixTableBlock;
    }

    @Override
    public boolean matches(SuffixTableBlock block) {
        if (mSuffixTableBlock.getPrefix() != block.getPrefix()
                || mSuffixTableBlock.getEntryCount() != block.getEntryCount()) {
            return false;
        }
        for (int i = 0; i < mSuffixTableBlock.getEntryCount(); i++) {
            SuffixTableBlock.Entry expectedEntry = mSuffixTableBlock.getEntryByIndex(i);
            SuffixTableBlock.Entry actualEntry = block.getEntryByIndex(i);
            if (!Objects.equals(expectedEntry, actualEntry)) {
                return false;
            }
        }
        return true;
    }
}
