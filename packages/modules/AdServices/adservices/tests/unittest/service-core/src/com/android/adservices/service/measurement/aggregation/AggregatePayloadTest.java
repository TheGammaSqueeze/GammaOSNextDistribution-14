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
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Unit tests for {@link AggregatePayload} */
@SmallTest
public final class AggregatePayloadTest {

    private AggregatePayload.AggregationServicePayload createPayload(
            List<Integer> payload, String keyId) {
        return new AggregatePayload.AggregationServicePayload.Builder()
                .setPayload(payload)
                .setKeyId(keyId).build();
    }

    private AggregatePayload createAggregateReport() {
        return new AggregatePayload.Builder()
                .setAggregationServicePayload(
                        Arrays.asList(
                                createPayload(Arrays.asList(1, 2), "1"),
                                createPayload(Arrays.asList(3), "2"),
                                createPayload(Arrays.asList(1, 2), "1")))
                .setSharedInfo("share_info")
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregatePayload aggregateReport = createAggregateReport();
        assertEquals("share_info", aggregateReport.getSharedInfo());
        List<AggregatePayload.AggregationServicePayload> payloads =
                aggregateReport.getPayloads();
        assertEquals(payloads.size(), 3);
        assertEquals("1", payloads.get(0).getKeyId());
        assertEquals("2", payloads.get(1).getKeyId());
        assertEquals(2, payloads.get(0).getPayload().size());
        assertEquals(1, payloads.get(1).getPayload().size());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregatePayload aggregateReport = new AggregatePayload.Builder().build();
        assertEquals(0, aggregateReport.getPayloads().size());
        assertNull(aggregateReport.getSharedInfo());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregatePayload aggregatePayload1 = createAggregateReport();
        final AggregatePayload aggregatePayload2 = createAggregateReport();
        final Set<AggregatePayload> aggregatePayloadSet1 = Set.of(aggregatePayload1);
        final Set<AggregatePayload> aggregatePayloadSet2 = Set.of(aggregatePayload2);
        assertEquals(aggregatePayload1.hashCode(), aggregatePayload2.hashCode());
        assertEquals(aggregatePayload1, aggregatePayload2);
        assertEquals(aggregatePayloadSet1, aggregatePayloadSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregatePayload aggregatePayload1 = createAggregateReport();
        final AggregatePayload aggregatePayload2 =
                new AggregatePayload.Builder()
                        .setAggregationServicePayload(
                                Arrays.asList(createPayload(Arrays.asList(1, 2), "1")))
                        .setSharedInfo("share_info")
                        .build();
        final Set<AggregatePayload> aggregatePayloadSet1 = Set.of(aggregatePayload1);
        final Set<AggregatePayload> aggregatePayloadSet2 = Set.of(aggregatePayload2);
        assertNotEquals(aggregatePayload1.hashCode(), aggregatePayload2.hashCode());
        assertNotEquals(aggregatePayload1, aggregatePayload2);
        assertNotEquals(aggregatePayloadSet1, aggregatePayloadSet2);
    }

    @Test
    public void testHashCodeAggregationServicePayload_equals() throws Exception {
        final AggregatePayload.AggregationServicePayload aggregatePayload1 =
                createPayload(Arrays.asList(1, 2), "1");
        final AggregatePayload.AggregationServicePayload aggregatePayload2 =
                createPayload(Arrays.asList(1, 2), "1");
        final Set<AggregatePayload.AggregationServicePayload> aggregatePayloadSet1 =
                Set.of(aggregatePayload1);
        final Set<AggregatePayload.AggregationServicePayload> aggregatePayloadSet2 =
                Set.of(aggregatePayload2);
        assertEquals(aggregatePayload1.hashCode(), aggregatePayload2.hashCode());
        assertEquals(aggregatePayload1, aggregatePayload2);
        assertEquals(aggregatePayloadSet1, aggregatePayloadSet2);
    }

    @Test
    public void testHashCodeAggregationServicePayload_notEquals() throws Exception {
        final AggregatePayload.AggregationServicePayload aggregatePayload1 =
                createPayload(Arrays.asList(1, 2), "1");
        final AggregatePayload.AggregationServicePayload aggregatePayload2 =
                createPayload(Arrays.asList(1), "1");
        final Set<AggregatePayload.AggregationServicePayload> aggregatePayloadSet1 =
                Set.of(aggregatePayload1);
        final Set<AggregatePayload.AggregationServicePayload> aggregatePayloadSet2 =
                Set.of(aggregatePayload2);
        assertNotEquals(aggregatePayload1.hashCode(), aggregatePayload2.hashCode());
        assertNotEquals(aggregatePayload1, aggregatePayload2);
        assertNotEquals(aggregatePayloadSet1, aggregatePayloadSet2);
    }
}
