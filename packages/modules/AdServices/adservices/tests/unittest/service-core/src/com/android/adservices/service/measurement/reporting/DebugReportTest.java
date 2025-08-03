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
package com.android.adservices.service.measurement.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.net.Uri;

import com.android.adservices.common.WebUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

/** Unit tests for {@link DebugReport} */
public final class DebugReportTest {

    private final String mType = "trigger-event-deduplicated";
    private final JSONObject mBodyJson = new JSONObject();

    private static final Uri REGISTRATION_ORIGIN_1 =
            WebUtil.validUri("https://subdomain.example1.test");
    private static final Uri REGISTRATION_ORIGIN_2 =
            WebUtil.validUri("https://subdomain.example2.test");

    private static final Uri SOURCE_REGISTRANT = Uri.parse("android-app://com.example.abc");
    private static final long INSERTION_TIME = 1617297798;

    @Before
    public void setup() throws JSONException {
        mBodyJson.put("attribution_destination", "https://destination.example");
        mBodyJson.put("source_event_id", "733458");
    }

    @Test
    public void creation_success() {
        DebugReport debugReport = createExample1();
        assertEquals("1", debugReport.getId());
        assertEquals(mType, debugReport.getType());
        assertEquals(mBodyJson, debugReport.getBody());
        assertEquals("2", debugReport.getEnrollmentId());
        assertEquals(REGISTRATION_ORIGIN_1, debugReport.getRegistrationOrigin());
    }

    @Test
    public void testHashCode_equals() {
        final DebugReport debugReport1 = createExample1();
        final DebugReport debugReport2 = createExample1();
        final Set<DebugReport> debugReportSet1 = Set.of(debugReport1);
        final Set<DebugReport> debugReportSet2 = Set.of(debugReport2);
        assertEquals(debugReport1.hashCode(), debugReport2.hashCode());
        assertEquals(debugReport1, debugReport2);
        assertEquals(debugReportSet1, debugReportSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final DebugReport debugReport1 = createExample1();
        final DebugReport debugReport2 = createExample2();
        final Set<DebugReport> debugReportSet1 = Set.of(debugReport1);
        final Set<DebugReport> debugReportSet2 = Set.of(debugReport2);
        assertNotEquals(debugReport1.hashCode(), debugReport2.hashCode());
        assertNotEquals(debugReport1, debugReport2);
        assertNotEquals(debugReportSet1, debugReportSet2);
    }

    @Test
    public void testDebugReportPayloadJsonSerialization() throws JSONException {
        DebugReport debugReport = createExample1();
        JSONObject debugReportJson = debugReport.toPayloadJson();
        assertEquals(mType, debugReportJson.get("type"));
        assertEquals(mBodyJson, debugReportJson.get("body"));
    }

    @Test
    public void testEqualsPass() {
        assertEquals(
                createExample1(),
                new DebugReport.Builder()
                        .setId("1")
                        .setType(mType)
                        .setBody(mBodyJson)
                        .setEnrollmentId("2")
                        .setRegistrationOrigin(REGISTRATION_ORIGIN_1)
                        .setReferenceId("6")
                        .setRegistrant(SOURCE_REGISTRANT)
                        .setInsertionTime(INSERTION_TIME)
                        .build());
    }

    @Test
    public void testEqualsFail() {
        assertNotEquals(createExample1(), createExample2());
    }

    private DebugReport createExample1() {
        return new DebugReport.Builder()
                .setId("1")
                .setType(mType)
                .setBody(mBodyJson)
                .setEnrollmentId("2")
                .setRegistrationOrigin(REGISTRATION_ORIGIN_1)
                .setReferenceId("6")
                .setRegistrant(SOURCE_REGISTRANT)
                .setInsertionTime(INSERTION_TIME)
                .build();
    }

    private DebugReport createExample2() {
        return new DebugReport.Builder()
                .setId("3")
                .setType(mType)
                .setBody(mBodyJson)
                .setEnrollmentId("4")
                .setRegistrationOrigin(REGISTRATION_ORIGIN_2)
                .setReferenceId("5")
                .setRegistrant(SOURCE_REGISTRANT)
                .setInsertionTime(INSERTION_TIME)
                .build();
    }
}
