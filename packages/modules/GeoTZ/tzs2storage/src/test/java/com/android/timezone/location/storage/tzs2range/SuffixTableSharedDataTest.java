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

import static org.junit.Assert.assertEquals;

import com.android.timezone.location.storage.tzs2range.write.SuffixTableSharedDataWriter;

import org.junit.Test;

/** Tests for {@link SuffixTableSharedData} and {@link SuffixTableSharedDataWriter}. */
public class SuffixTableSharedDataTest {

    @Test
    public void test() {
        int prefix = 321;
        int bank = 123;
        SuffixTableSharedData sharedData = new SuffixTableSharedData(prefix, bank);
        byte[] bytes = SuffixTableSharedDataWriter.toBytes(sharedData);

        assertEquals(sharedData, SuffixTableSharedData.fromBytes(bytes));
    }
}
