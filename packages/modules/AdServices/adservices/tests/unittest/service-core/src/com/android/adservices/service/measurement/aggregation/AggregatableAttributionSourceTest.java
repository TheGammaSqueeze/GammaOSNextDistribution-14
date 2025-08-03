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

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Set;
import java.util.TreeMap;

/** Unit tests for {@link AggregatableAttributionSource} */
@SmallTest
public final class AggregatableAttributionSourceTest {

    private AggregatableAttributionSource createExample() {
        TreeMap<String, BigInteger> aggregatableSource = new TreeMap<>();
        aggregatableSource.put("campaignCounts", BigInteger.valueOf(159L));
        aggregatableSource.put("geoValue", BigInteger.valueOf(5L));

        return new AggregatableAttributionSource.Builder()
                .setAggregatableSource(aggregatableSource)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregatableAttributionSource attributionSource = createExample();

        assertEquals(attributionSource.getAggregatableSource().size(), 2);
        assertEquals(attributionSource.getAggregatableSource().get("campaignCounts")
                .longValue(), 159L);
        assertEquals(attributionSource.getAggregatableSource().get("geoValue").longValue(), 5L);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder().build();
        assertEquals(attributionSource.getAggregatableSource().size(), 0);
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregatableAttributionSource attributionSource1 = createExample();
        final AggregatableAttributionSource attributionSource2 = createExample();
        final Set<AggregatableAttributionSource> attributionSourceSet1 = Set.of(attributionSource1);
        final Set<AggregatableAttributionSource> attributionSourceSet2 = Set.of(attributionSource2);
        assertEquals(attributionSource1.hashCode(), attributionSource2.hashCode());
        assertEquals(attributionSource1, attributionSource2);
        assertEquals(attributionSourceSet1, attributionSourceSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregatableAttributionSource attributionSource1 = createExample();

        TreeMap<String, BigInteger> aggregatableSource = new TreeMap<>();
        aggregatableSource.put("campaignCounts", BigInteger.valueOf(159L));
        aggregatableSource.put("geoValue", BigInteger.valueOf(1L));

        final AggregatableAttributionSource attributionSource2 =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource)
                        .build();
        final Set<AggregatableAttributionSource> attributionSourceSet1 = Set.of(attributionSource1);
        final Set<AggregatableAttributionSource> attributionSourceSet2 = Set.of(attributionSource2);
        assertNotEquals(attributionSource1.hashCode(), attributionSource2.hashCode());
        assertNotEquals(attributionSource1, attributionSource2);
        assertNotEquals(attributionSourceSet1, attributionSourceSet2);
    }
}

