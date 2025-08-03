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

import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;

import org.junit.Test;

public class GaUxEnrollmentChannelCollectionTest {
    @Test
    public void gaUxEnrollmentChannelCollectionTest_cardinalityCheck() {
        assertEquals(9, GaUxEnrollmentChannelCollection.values().length);
    }

    @Test
    public void gaUxEnrollmentChannelCollectionTest_enrollmentChannelOrderCheck() {
        GaUxEnrollmentChannelCollection[] enrollmentChannelCollection =
                GaUxEnrollmentChannelCollection.values();

        assertEquals(
                GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL,
                enrollmentChannelCollection[0]);
        assertEquals(
                GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL,
                enrollmentChannelCollection[1]);
        assertEquals(
                GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL,
                enrollmentChannelCollection[2]);
        assertEquals(
                GaUxEnrollmentChannelCollection.PAS_FIRST_CONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[3]);
        assertEquals(
                GaUxEnrollmentChannelCollection.PAS_RECONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[4]);
        assertEquals(
                GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[5]);
        assertEquals(
                GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL,
                enrollmentChannelCollection[6]);
        assertEquals(
                GaUxEnrollmentChannelCollection.GA_GRADUATION_CHANNEL,
                enrollmentChannelCollection[7]);
        assertEquals(
                GaUxEnrollmentChannelCollection.RVC_POST_OTA_CHANNEL,
                enrollmentChannelCollection[8]);
    }

    @Test
    public void gaUxEnrollmentChannelCollectionTest_priorityCheck() {
        assertThat(
                        GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                                        .getPriority()
                                < GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                        .getPriority()
                        < GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                        .getPriority())
                .isTrue();
        assertThat(
                        GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL.getPriority()
                                < GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                        GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                                        .getPriority()
                                < GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                        GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL.getPriority()
                                < GaUxEnrollmentChannelCollection.GA_GRADUATION_CHANNEL
                                        .getPriority())
                .isTrue();
        assertThat(
                        GaUxEnrollmentChannelCollection.GA_GRADUATION_CHANNEL.getPriority()
                                < GaUxEnrollmentChannelCollection.RVC_POST_OTA_CHANNEL
                                        .getPriority())
                .isTrue();
    }

    @Test
    public void gaUxEnrollmentChannelCollectionTest_enrollmentChannelCheck() {
        assertThat(
                        GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                                .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL
                        .getEnrollmentChannel())
                .isNotNull();
        assertThat(GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL.getEnrollmentChannel())
                .isNotNull();
        assertThat(
                        GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                                .getEnrollmentChannel())
                .isNotNull();
        assertThat(
                        GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL
                                .getEnrollmentChannel())
                .isNotNull();
        assertThat(GaUxEnrollmentChannelCollection.GA_GRADUATION_CHANNEL.getEnrollmentChannel())
                .isNotNull();
        assertThat(GaUxEnrollmentChannelCollection.RVC_POST_OTA_CHANNEL.getEnrollmentChannel())
                .isNotNull();
    }
}
