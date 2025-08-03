/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.example.odpsamplenetwork;

import android.util.Base64;

import com.google.common.hash.Funnels;
import com.google.setfilters.cuckoofilter.CuckooFilter;
import com.google.setfilters.cuckoofilter.CuckooFilterHashFunctions;
import com.google.setfilters.cuckoofilter.CuckooFilterStrategies;
import com.google.setfilters.cuckoofilter.SerializedCuckooFilterTable;

import java.nio.charset.StandardCharsets;

class CuckooFilterUtil {

    static CuckooFilter<String> createCuckooFilter(String serializedFilterBase64) {
        byte[] serializedFilter = Base64.decode(serializedFilterBase64, 0);

        CuckooFilter<String> result = CuckooFilter.createFromSerializedTable(
                SerializedCuckooFilterTable.createFromByteArray(serializedFilter),
                CuckooFilterHashFunctions.MURMUR3_128,
                CuckooFilterStrategies.SIMPLE_MOD,
                Funnels.stringFunnel(StandardCharsets.UTF_8)
        );

        return result;
    }

    private CuckooFilterUtil() {
    }
}
