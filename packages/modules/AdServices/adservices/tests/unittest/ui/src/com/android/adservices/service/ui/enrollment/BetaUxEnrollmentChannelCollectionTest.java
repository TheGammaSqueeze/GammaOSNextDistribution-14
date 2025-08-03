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

import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;

import org.junit.Test;

public class BetaUxEnrollmentChannelCollectionTest {
    @Test
    public void betaUxEnrollmentChannelCollectionTest_cardinalityCheck() {
        assertEquals(4, BetaUxEnrollmentChannelCollection.values().length);
    }

    @Test
    public void betaUxEnrollmentChannelCollectionTest_enrollmentChannelOrderCheck() {
        BetaUxEnrollmentChannelCollection[] enrollmentChannelCollection =
                BetaUxEnrollmentChannelCollection.values();

        assertEquals(
                BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL,
                enrollmentChannelCollection[0]);
        assertEquals(
                BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL,
                enrollmentChannelCollection[1]);
        assertEquals(
                BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL,
                enrollmentChannelCollection[2]);
        assertEquals(
                BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[3]);
    }

    @Test
    public void betaUxEnrollmentChannelCollectionTest_priorityCheck() {
        assertThat(
                        BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                                        .getPriority()
                                < BetaUxEnrollmentChannelCollection
                                        .CONSENT_NOTIFICATION_RESET_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                        BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                                        .getPriority()
                                < BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL.getPriority()
                        < BetaUxEnrollmentChannelCollection
                        .FIRST_CONSENT_NOTIFICATION_CHANNEL
                        .getPriority())
                .isTrue();
    }

    @Test
    public void betaUxEnrollmentChannelCollectionTest_enrollmentChannelCheck() {
        assertThat(
                BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                        BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                                .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
    }
}
