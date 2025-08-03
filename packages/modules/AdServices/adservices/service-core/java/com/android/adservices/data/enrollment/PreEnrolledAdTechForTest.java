/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.adservices.data.enrollment;

import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.Arrays;
import java.util.List;

/** Container class for pre-enrolled Adtech enrollment data to aid testing. */
final class PreEnrolledAdTechForTest {

    static List<EnrollmentData> getList() {
        return Arrays.asList(
                SIMPLE_ENROLLMENT,
                ONE_SDK_MULTIPLE_URLS,
                SECOND_ENROLLMENT,
                TOPICS_SAMPLE_APPS,
                MSMT_SYS_HEALTH_TEST_ENROLLMENT,
                LOCAL_SERVER_FOR_MSMT_REMARKETING,
                LOCAL_SERVER_FOR_PAS);
    }

    private static final EnrollmentData SIMPLE_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E1")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk1")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl("https://test.com/keys")
                    .build();

    private static final EnrollmentData ONE_SDK_MULTIPLE_URLS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E2")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk2")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://test2.com/source", "https://testtest.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test2.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test2.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/keys")
                    .build();

    private static final EnrollmentData SECOND_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E3")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk3")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test3.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test3.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test3.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test3.com"))
                    .setEncryptionKeyUrl("https://test3.com/keys")
                    .build();

    private static final EnrollmentData TOPICS_SAMPLE_APPS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E4")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_TOPICS")
                    .setSdkNames(
                            Arrays.asList(
                                    "SdkName1", "SdkName2", "SdkName3", "SdkName4", "SdkName5"))
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl("https://test.com/keys")
                    .build();

    private static final EnrollmentData MSMT_SYS_HEALTH_TEST_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E5")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://rb-measurement.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://rb-measurement.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://rb-measurement.com:38383"))
                    .build();

    private static final EnrollmentData LOCAL_SERVER_FOR_MSMT_REMARKETING =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E6")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://localhost:8080/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://localhost:8080/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://localhost:8080"))
                    .setRemarketingResponseBasedRegistrationUrl(
                            Arrays.asList("https://localhost:8080"))
                    .build();

    private static final EnrollmentData LOCAL_SERVER_FOR_PAS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E7")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setEncryptionKeyUrl("https://localhost")
                    .build();
}
