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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import com.android.adservices.common.WebUtil;

import org.junit.Test;

import java.util.UUID;

public class AttributionTest {
    private static final String ID = "AR1";
    private static final String DESTINATION_ORIGIN = "https://destination.test/origin";
    private static final String DESTINATION_SITE = "https://destination.test";
    private static final String PUBLISHER_ORIGIN = "android-app://com.publisher/abc";
    private static final String PUBLISHER_SITE = "android-app://com.publisher";
    private static final String REGISTRANT = "android-app://com.registrant";
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final long TRIGGER_TIME = 10000L;
    private static final String SOME_OTHER_STRING = "some_other";
    private static final long SOME_OTHER_LONG = 1L;
    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();
    private static final Uri REGISTRATION_ORIGIN =
            WebUtil.validUri("https://subdomain.example.test");
    private static final Uri OTHER_REGISTRATION_ORIGIN =
            WebUtil.validUri("https://other.example.test");

    @Test
    public void equals_pass() {
        assertEquals(
                createExampleAttributionBuilder().build(),
                createExampleAttributionBuilder().build());
        assertEquals(
                createExampleAttributionBuilder().setId(SOME_OTHER_STRING).build(),
                createExampleAttributionBuilder().build());
    }

    @Test
    public void equals_fail() {
        assertNotEquals(
                createExampleAttributionBuilder().setScope(Attribution.Scope.EVENT).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder().setRegistrant(SOME_OTHER_STRING).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setEnrollmentId(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setDestinationSite(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setDestinationOrigin(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder().setSourceSite(SOME_OTHER_STRING).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setSourceOrigin(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder().setTriggerTime(SOME_OTHER_LONG).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder().setSourceId(SOME_OTHER_STRING).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder().setTriggerId(SOME_OTHER_STRING).build(),
                createExampleAttributionBuilder().build());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setRegistrationOrigin(OTHER_REGISTRATION_ORIGIN)
                        .build(),
                createExampleAttributionBuilder().build());
    }

    @Test
    public void hashCode_pass() {
        assertEquals(
                createExampleAttributionBuilder().build().hashCode(),
                createExampleAttributionBuilder().build().hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setScope(Attribution.Scope.EVENT)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setRegistrant(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setEnrollmentId(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setDestinationSite(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setDestinationOrigin(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setSourceSite(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setSourceOrigin(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setTriggerTime(SOME_OTHER_LONG)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder().setSourceId(SOME_OTHER_STRING).build().hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setTriggerId(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionBuilder()
                        .setRegistrationOrigin(OTHER_REGISTRATION_ORIGIN)
                        .build()
                        .hashCode(),
                createExampleAttributionBuilder().build().hashCode());
    }

    @Test
    public void getters() {
        assertEquals(
                Attribution.Scope.AGGREGATE,
                createExampleAttributionBuilder().build().getScope());
        assertEquals(
                REGISTRANT, createExampleAttributionBuilder().build().getRegistrant());
        assertEquals(
                DESTINATION_SITE,
                createExampleAttributionBuilder().build().getDestinationSite());
        assertEquals(
                DESTINATION_ORIGIN,
                createExampleAttributionBuilder().build().getDestinationOrigin());
        assertEquals(
                PUBLISHER_ORIGIN,
                createExampleAttributionBuilder().build().getSourceOrigin());
        assertEquals(
                PUBLISHER_SITE, createExampleAttributionBuilder().build().getSourceSite());
        assertEquals(
                TRIGGER_TIME, createExampleAttributionBuilder().build().getTriggerTime());
        assertEquals(
                ENROLLMENT_ID,
                createExampleAttributionBuilder().build().getEnrollmentId());
        assertEquals(SOURCE_ID, createExampleAttributionBuilder().build().getSourceId());
        assertEquals(TRIGGER_ID, createExampleAttributionBuilder().build().getTriggerId());
        assertEquals(
                REGISTRATION_ORIGIN,
                createExampleAttributionBuilder().build().getRegistrationOrigin());
    }

    @Test
    public void validate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionBuilder().setSourceSite(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionBuilder().setRegistrant(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionBuilder().setEnrollmentId(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionBuilder().setDestinationSite(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        createExampleAttributionBuilder()
                                .setDestinationOrigin(null)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionBuilder().setSourceOrigin(null).build());
    }

    private static Attribution.Builder createExampleAttributionBuilder() {
        return new Attribution.Builder()
                .setId(ID)
                .setScope(Attribution.Scope.AGGREGATE)
                .setRegistrant(REGISTRANT)
                .setTriggerTime(TRIGGER_TIME)
                .setEnrollmentId(ENROLLMENT_ID)
                .setDestinationOrigin(DESTINATION_ORIGIN)
                .setDestinationSite(DESTINATION_SITE)
                .setSourceOrigin(PUBLISHER_ORIGIN)
                .setSourceSite(PUBLISHER_SITE)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(REGISTRATION_ORIGIN);
    }
}
