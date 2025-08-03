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

import static org.junit.Assert.assertEquals;

import com.android.telephony.sats2range.read.SuffixTableSharedData;
import com.android.telephony.sats2range.write.SuffixTableSharedDataWriter;

import org.junit.Test;

/** Tests for {@link SuffixTableSharedData} and {@link SuffixTableSharedDataWriter}. */
public class SuffixTableSharedDataTest {
    @Test
    public void testSuffixTableSharedData() {
        int prefix = 321;
        SuffixTableSharedData sharedData = new SuffixTableSharedData(prefix);
        byte[] bytes = SuffixTableSharedDataWriter.toBytes(sharedData);

        assertEquals(sharedData, SuffixTableSharedData.fromBytes(bytes));
    }
}

