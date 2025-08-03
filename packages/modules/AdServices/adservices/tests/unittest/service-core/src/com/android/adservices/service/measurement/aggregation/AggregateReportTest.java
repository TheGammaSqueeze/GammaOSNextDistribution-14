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
import static org.junit.Assert.assertFalse;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Unit tests for {@link AggregateReport} */
@SmallTest
public final class AggregateReportTest {

    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();
    private static final String DEBUG_CLEARTEXT_PAYLOAD =
            "{"
                    + "\"operation\": \"histogram\","
                    + "\"data\": [{"
                    + "\"bucket\": \"1369\","
                    + "\"value\": 32768"
                    + "},"
                    + "{"
                    + "\"bucket\": \"3461\","
                    + "\"value\": 1664"
                    + "}]"
                    + "}";

    private AggregateReport createAttributionReport() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.test/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion("1452")
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(
                        AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN)
                .build();
    }

    private AggregateReport createAttributionReportSingleTriggerDebugKey() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.test/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion("1452")
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(
                        AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN)
                .build();
    }

    private AggregateReport createAttributionReportSingleSourceDebugKey() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.test/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                .setApiVersion("1452")
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(
                        AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateReport attributionReport = createAttributionReport();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(Uri.parse("https://example.test/aS"),
                attributionReport.getAttributionDestination());
        assertEquals(Long.valueOf(5L), attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(DEBUG_CLEARTEXT_PAYLOAD, attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals(
                AggregateReport.DebugReportStatus.PENDING,
                attributionReport.getDebugReportStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertEquals(SOURCE_DEBUG_KEY, attributionReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
        assertEquals(
                AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN,
                attributionReport.getRegistrationOrigin());
        assertFalse(attributionReport.isFakeReport());
    }

    @Test
    public void testCreationSingleSourceDebugKey() {
        AggregateReport attributionReport = createAttributionReportSingleSourceDebugKey();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(
                Uri.parse("https://example.test/aS"),
                attributionReport.getAttributionDestination());
        assertEquals(Long.valueOf(5L), attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(DEBUG_CLEARTEXT_PAYLOAD, attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals(
                AggregateReport.DebugReportStatus.PENDING,
                attributionReport.getDebugReportStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertEquals(SOURCE_DEBUG_KEY, attributionReport.getSourceDebugKey());
        assertNull(attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
        assertEquals(
                AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN,
                attributionReport.getRegistrationOrigin());
        assertFalse(attributionReport.isFakeReport());
    }

    @Test
    public void testCreationSingleTriggerDebugKey() {
        AggregateReport attributionReport = createAttributionReportSingleTriggerDebugKey();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(Uri.parse("https://example.test/aS"),
                attributionReport.getAttributionDestination());
        assertEquals(Long.valueOf(5L), attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(DEBUG_CLEARTEXT_PAYLOAD, attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals(
                AggregateReport.DebugReportStatus.PENDING,
                attributionReport.getDebugReportStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertNull(attributionReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
        assertEquals(
                AggregateReportFixture.ValidAggregateReportParams.REGISTRATION_ORIGIN,
                attributionReport.getRegistrationOrigin());
        assertFalse(attributionReport.isFakeReport());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateReport attributionReport =
                new AggregateReport.Builder().build();
        assertNull(attributionReport.getId());
        assertNull(attributionReport.getPublisher());
        assertNull(attributionReport.getAttributionDestination());
        assertNull(attributionReport.getSourceRegistrationTime());
        assertEquals(0L, attributionReport.getScheduledReportTime());
        assertNull(attributionReport.getEnrollmentId());
        assertNull(attributionReport.getDebugCleartextPayload());
        assertNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals(
                AggregateReport.DebugReportStatus.NONE, attributionReport.getDebugReportStatus());
        assertNull(attributionReport.getApiVersion());
        assertNull(attributionReport.getSourceDebugKey());
        assertNull(attributionReport.getTriggerDebugKey());
        assertNull(attributionReport.getSourceId());
        assertNull(attributionReport.getTriggerId());
        assertNull(attributionReport.getRegistrationOrigin());
        assertFalse(attributionReport.isFakeReport());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        AggregateReport attributionReport1 = createAttributionReport();
        AggregateReport attributionReport2 = createAttributionReport();
        Set<AggregateReport> attributionReportSet1 = Set.of(attributionReport1);
        Set<AggregateReport> attributionReportSet2 = Set.of(attributionReport2);
        assertEquals(attributionReport1.hashCode(), attributionReport2.hashCode());
        assertEquals(attributionReport1, attributionReport2);
        assertEquals(attributionReportSet1, attributionReportSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        AggregateReport attributionReport1 = createAttributionReport();
        AggregateReport attributionReport2 =
                new AggregateReport.Builder()
                        .setId("1")
                        .setPublisher(Uri.parse("android-app://com.example.abc"))
                        .setAttributionDestination(Uri.parse("https://example.test/aS"))
                        .setSourceRegistrationTime(1L)
                        .setScheduledReportTime(1L)
                        .setEnrollmentId("another-enrollment-id")
                        .setDebugCleartextPayload(
                                " key: 1369, value: 32768; key: 3461, value: 1664;")
                        .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                        .setStatus(AggregateReport.Status.PENDING)
                        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                        .setApiVersion("1452")
                        .setSourceId(SOURCE_ID)
                        .setTriggerId(TRIGGER_ID)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .build();
        Set<AggregateReport> attributionReportSet1 = Set.of(attributionReport1);
        Set<AggregateReport> attributionReportSet2 = Set.of(attributionReport2);
        assertNotEquals(attributionReport1.hashCode(), attributionReport2.hashCode());
        assertNotEquals(attributionReport1, attributionReport2);
        assertNotEquals(attributionReportSet1, attributionReportSet2);
    }

    @Test
    public void extractAggregateHistogramContributions_withDebugPayload_canReverseConvert() {
        // Setup
        AggregateReport aggregateReport = createAttributionReport();
        AggregateHistogramContribution expectedContribution1 =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("1369"))
                        .setValue(32768)
                        .build();
        AggregateHistogramContribution expectedContribution2 =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("3461"))
                        .setValue(1664)
                        .build();

        // Execution
        List<AggregateHistogramContribution> contributions =
                aggregateReport.extractAggregateHistogramContributions();

        // Assertion
        assertEquals(2, contributions.size());
        assertEquals(expectedContribution1, contributions.get(0));
        assertEquals(expectedContribution2, contributions.get(1));
    }

    @Test
    public void extractAggregateHistogramContributions_withInvalidDebugPayload_returnsEmptyList() {
        // Setup
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("1")
                        .setPublisher(Uri.parse("android-app://com.example.abc"))
                        .setAttributionDestination(Uri.parse("https://example.test/aS"))
                        .setSourceRegistrationTime(5L)
                        .setScheduledReportTime(1L)
                        .setEnrollmentId("enrollment-id")
                        // invalid debug cleartext payload
                        .setDebugCleartextPayload("some_invalid_debug_cleartext_payload")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                        .setApiVersion("1452")
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setSourceId(SOURCE_ID)
                        .setTriggerId(TRIGGER_ID)
                        .setRegistrationOrigin(
                                AggregateReportFixture.ValidAggregateReportParams
                                        .REGISTRATION_ORIGIN)
                        .build();

        // Execution
        List<AggregateHistogramContribution> contributions =
                aggregateReport.extractAggregateHistogramContributions();

        // Assertion
        assertEquals(0, contributions.size());
    }
}
