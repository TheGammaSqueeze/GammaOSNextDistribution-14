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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;

/** Unit tests for {@link AggregateAttributionData} */
@SmallTest
public final class AggregateAttributionDataTest {

    private AggregateAttributionData createExample() {
        return new AggregateAttributionData.Builder()
                .setContributions(new ArrayList<>())
                .setId(1L).build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateAttributionData data = createExample();
        assertNotNull(data.getContributions());
        assertEquals(1L, data.getId().longValue());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateAttributionData data = new AggregateAttributionData.Builder().build();
        assertEquals(0, data.getContributions().size());
        assertNull(data.getId());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregateAttributionData data1 = createExample();
        final AggregateAttributionData data2 = createExample();
        final Set<AggregateAttributionData> dataSet1 = Set.of(data1);
        final Set<AggregateAttributionData> dataSet2 = Set.of(data2);
        assertEquals(data1.hashCode(), data2.hashCode());
        assertEquals(data1, data2);
        assertEquals(dataSet1, dataSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregateAttributionData data1 = createExample();
        final AggregateAttributionData data2 =
                new AggregateAttributionData.Builder()
                        .setContributions(new ArrayList<>())
                        .setId(2L)
                        .build();
        final Set<AggregateAttributionData> dataSet1 = Set.of(data1);
        final Set<AggregateAttributionData> dataSet2 = Set.of(data2);
        assertNotEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1, data2);
        assertNotEquals(dataSet1, dataSet2);
    }
}

