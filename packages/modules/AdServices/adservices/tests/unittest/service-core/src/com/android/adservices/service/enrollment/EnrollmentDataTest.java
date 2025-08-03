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

package com.android.adservices.service.enrollment;

import static com.android.adservices.service.enrollment.EnrollmentData.SEPARATOR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


import androidx.test.filters.SmallTest;

import com.android.adservices.service.proto.PrivacySandboxApi;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link EnrollmentData} */
@SmallTest
public final class EnrollmentDataTest {
    private static final String ENROLLMENT_ID = "1";
    private static final String ENROLLED_API_NAMES =
            "PRIVACY_SANDBOX_API_TOPICS PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE TEST1";
    private static final ImmutableList<PrivacySandboxApi> ENROLLED_API_ENUMS =
            ImmutableList.of(
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS,
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE,
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_UNKNOWN);
    private static final ImmutableList<String> SDK_NAMES = ImmutableList.of("Admob");
    private static final ImmutableList<String> ATTRIBUTION_SOURCE_REGISTRATION_URLS =
            ImmutableList.of("source1.example.com", "source2.example.com");
    private static final ImmutableList<String> ATTRIBUTION_TRIGGER_REGISTRATION_URLS =
            ImmutableList.of("trigger1.example.com", "trigger2.example.com");
    private static final ImmutableList<String> ATTRIBUTION_REPORTING_REGISTRATION_URLS =
            ImmutableList.of("reporting1.example.com", "reporting2.example.com");
    private static final ImmutableList<String> REMARKETING_RESPONSE_BASED_REGISTRATION_URLS =
            ImmutableList.of("remarketing1.example.com", "remarketing2.example.com");
    private static final String ENCRYPTION_KEY_URL = "encryption1.example.com";

    private EnrollmentData createEnrollmentData() {
        return new EnrollmentData.Builder()
                .setEnrollmentId(ENROLLMENT_ID)
                .setEnrolledAPIs(ENROLLED_API_NAMES)
                .setSdkNames(SDK_NAMES)
                .setAttributionSourceRegistrationUrl(ATTRIBUTION_SOURCE_REGISTRATION_URLS)
                .setAttributionTriggerRegistrationUrl(ATTRIBUTION_TRIGGER_REGISTRATION_URLS)
                .setAttributionReportingUrl(ATTRIBUTION_REPORTING_REGISTRATION_URLS)
                .setRemarketingResponseBasedRegistrationUrl(
                        REMARKETING_RESPONSE_BASED_REGISTRATION_URLS)
                .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        EnrollmentData enrollmentData = createEnrollmentData();

        assertEquals(ENROLLMENT_ID, enrollmentData.getEnrollmentId());
        assertThat(enrollmentData.getEnrolledAPIs()).containsExactlyElementsIn(ENROLLED_API_ENUMS);
        assertEquals(ENROLLED_API_NAMES, enrollmentData.getEnrolledAPIsString());
        assertThat(enrollmentData.getSdkNames()).containsExactlyElementsIn(SDK_NAMES);
        assertThat(enrollmentData.getAttributionSourceRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_SOURCE_REGISTRATION_URLS);
        assertThat(enrollmentData.getAttributionTriggerRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_TRIGGER_REGISTRATION_URLS);
        assertThat(enrollmentData.getAttributionReportingUrl())
                .containsExactlyElementsIn(ATTRIBUTION_REPORTING_REGISTRATION_URLS);
        assertThat(enrollmentData.getRemarketingResponseBasedRegistrationUrl())
                .containsExactlyElementsIn(REMARKETING_RESPONSE_BASED_REGISTRATION_URLS);
        assertEquals(ENCRYPTION_KEY_URL, enrollmentData.getEncryptionKeyUrl());
    }

    @Test
    public void testCreationFromStrings() {
        EnrollmentData enrollmentData =
                new EnrollmentData.Builder()
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setEnrolledAPIs(ENROLLED_API_NAMES)
                        .setSdkNames(String.join(SEPARATOR, SDK_NAMES))
                        .setAttributionSourceRegistrationUrl(
                                String.join(SEPARATOR, ATTRIBUTION_SOURCE_REGISTRATION_URLS))
                        .setAttributionTriggerRegistrationUrl(
                                String.join(SEPARATOR, ATTRIBUTION_TRIGGER_REGISTRATION_URLS))
                        .setAttributionReportingUrl(
                                String.join(SEPARATOR, ATTRIBUTION_REPORTING_REGISTRATION_URLS))
                        .setRemarketingResponseBasedRegistrationUrl(
                                String.join(
                                        SEPARATOR, REMARKETING_RESPONSE_BASED_REGISTRATION_URLS))
                        .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                        .build();

        assertEquals(ENROLLMENT_ID, enrollmentData.getEnrollmentId());
        assertThat(enrollmentData.getEnrolledAPIs()).containsExactlyElementsIn(ENROLLED_API_ENUMS);
        assertEquals(ENROLLED_API_NAMES, enrollmentData.getEnrolledAPIsString());
        assertThat(enrollmentData.getSdkNames()).containsExactlyElementsIn(SDK_NAMES);
        assertThat(enrollmentData.getAttributionSourceRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_SOURCE_REGISTRATION_URLS);
        assertThat(enrollmentData.getAttributionTriggerRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_TRIGGER_REGISTRATION_URLS);
        assertThat(enrollmentData.getAttributionReportingUrl())
                .containsExactlyElementsIn(ATTRIBUTION_REPORTING_REGISTRATION_URLS);
        assertThat(enrollmentData.getRemarketingResponseBasedRegistrationUrl())
                .containsExactlyElementsIn(REMARKETING_RESPONSE_BASED_REGISTRATION_URLS);
        assertEquals(ENCRYPTION_KEY_URL, enrollmentData.getEncryptionKeyUrl());
    }

    @Test
    public void testSplitEnrollmentInputToList_emptyString() {
        assertThat(EnrollmentData.splitEnrollmentInputToList("")).isEmpty();
    }

    @Test
    public void testSplitEnrollmentInputToList_singleItem() {
        String item = "one.item";
        assertThat(EnrollmentData.splitEnrollmentInputToList(item)).containsExactly(item);
    }

    @Test
    public void testSplitEnrollmentInputToList_multipleItems() {
        List<String> items = Arrays.asList("first.item", "second.item");
        String itemListString = String.join(SEPARATOR, items);
        assertThat(EnrollmentData.splitEnrollmentInputToList(itemListString))
                .containsExactlyElementsIn(items);
    }

    @Test
    public void testDefaults() throws Exception {
        EnrollmentData enrollmentData = new EnrollmentData.Builder().build();
        assertNull(enrollmentData.getEnrollmentId());
        assertEquals(0, enrollmentData.getEnrolledAPIs().size());
        assertNull(enrollmentData.getEnrolledAPIsString());
        assertEquals(enrollmentData.getSdkNames().size(), 0);
        assertEquals(enrollmentData.getAttributionSourceRegistrationUrl().size(), 0);
        assertEquals(enrollmentData.getAttributionTriggerRegistrationUrl().size(), 0);
        assertEquals(enrollmentData.getAttributionReportingUrl().size(), 0);
        assertEquals(enrollmentData.getRemarketingResponseBasedRegistrationUrl().size(), 0);
        assertNull(enrollmentData.getEncryptionKeyUrl());
    }

    @Test
    public void testEquals() {
        EnrollmentData e1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();
        EnrollmentData e2 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();
        assertEquals(e1, e2);
    }

    @Test
    public void testSetEnrolledAPIs_returnsUnknownAPI() {
        EnrollmentData e1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("foo-inc")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();

        String enrolledAPIsString = e1.getEnrolledAPIsString();
        assertEquals("foo-inc", enrolledAPIsString);
        ImmutableList<PrivacySandboxApi> enrolledAPIUnknown =
                ImmutableList.of(PrivacySandboxApi.PRIVACY_SANDBOX_API_UNKNOWN);
        assertEquals(1, e1.getEnrolledAPIs().size());
        assertThat(e1.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIUnknown);
    }
}
