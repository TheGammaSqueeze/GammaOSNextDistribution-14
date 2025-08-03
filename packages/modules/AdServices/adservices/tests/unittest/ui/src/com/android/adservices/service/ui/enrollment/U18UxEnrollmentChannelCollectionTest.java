/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.ui.enrollment;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;

import org.junit.Test;

public class U18UxEnrollmentChannelCollectionTest {
    @Test
    public void u18UxEnrollmentChannelCollectionTest_cardinalityCheck() {
        assertEquals(5, U18UxEnrollmentChannelCollection.values().length);
    }

    @Test
    public void u18UxEnrollmentChannelCollectionTest_enrollmentChannelOrderCheck() {
        U18UxEnrollmentChannelCollection[] enrollmentChannelCollection =
                U18UxEnrollmentChannelCollection.values();

        assertEquals(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL,
                enrollmentChannelCollection[0]);
        assertEquals(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL,
                enrollmentChannelCollection[1]);
        assertEquals(
                U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL,
                enrollmentChannelCollection[2]);
        assertEquals(
                U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[3]);
        assertEquals(
                U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL,
                enrollmentChannelCollection[4]);
    }

    @Test
    public void u18UxEnrollmentChannelCollectionTest_priorityCheck() {
        assertThat(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                        .getPriority()
                        < U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                        .getPriority())
                .isTrue();
        assertThat(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                        .getPriority()
                        < U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                        .getPriority())
                .isTrue();
        assertThat(
                U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL.getPriority()
                        < U18UxEnrollmentChannelCollection
                        .FIRST_CONSENT_NOTIFICATION_CHANNEL
                        .getPriority())
                .isTrue();
        assertThat(
                U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                        .getPriority()
                        < U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL
                        .getPriority())
                .isTrue();
    }

    @Test
    public void u18UxEnrollmentChannelCollectionTest_enrollmentChannelCheck() {
        assertThat(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL.getEnrollmentChannel())
                .isNotNull();
        assertThat(
                U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL.getEnrollmentChannel())
                .isNotNull();
    }
}
