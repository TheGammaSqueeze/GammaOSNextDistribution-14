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

package com.android.adservices.service.ui.ux;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import org.junit.Test;

public class PrivacySandboxUxCollectionTest {

    @Test
    public void uxCollectionTest_cardinalityCheck() {
        assertEquals(5, PrivacySandboxUxCollection.values().length);
    }

    @Test
    public void uxCollectionTest_uxOrderCheck() {
        PrivacySandboxUxCollection[] uxCollection = PrivacySandboxUxCollection.values();

        assertEquals(PrivacySandboxUxCollection.UNSUPPORTED_UX, uxCollection[0]);
        assertEquals(PrivacySandboxUxCollection.U18_UX, uxCollection[1]);
        assertEquals(PrivacySandboxUxCollection.RVC_UX, uxCollection[2]);
        assertEquals(PrivacySandboxUxCollection.GA_UX, uxCollection[3]);
        assertEquals(PrivacySandboxUxCollection.BETA_UX, uxCollection[4]);
    }

    @Test
    public void uxCollectionTest_priorityCheck() {
        assertThat(
                PrivacySandboxUxCollection.UNSUPPORTED_UX.getPriority()
                        < PrivacySandboxUxCollection.U18_UX.getPriority())
                .isTrue();
        assertThat(
                        PrivacySandboxUxCollection.U18_UX.getPriority()
                                < PrivacySandboxUxCollection.RVC_UX.getPriority())
                .isTrue();
        assertThat(
                        PrivacySandboxUxCollection.RVC_UX.getPriority()
                                < PrivacySandboxUxCollection.GA_UX.getPriority())
                .isTrue();
        assertThat(
                PrivacySandboxUxCollection.GA_UX.getPriority()
                        < PrivacySandboxUxCollection.BETA_UX.getPriority())
                .isTrue();
    }

    @Test
    public void uxCollectionTest_uxCheck() {
        assertThat(PrivacySandboxUxCollection.UNSUPPORTED_UX.getUx()).isNotNull();
        assertThat(PrivacySandboxUxCollection.U18_UX.getUx()).isNotNull();
        assertThat(PrivacySandboxUxCollection.GA_UX.getUx()).isNotNull();
        assertThat(PrivacySandboxUxCollection.BETA_UX.getUx()).isNotNull();
    }

    @Test
    public void uxCollectionTest_enrollmentChannelCollectionCheck() {
        assertEquals(
                0,
                PrivacySandboxUxCollection.UNSUPPORTED_UX.getEnrollmentChannelCollection().length);
        assertEquals(5, PrivacySandboxUxCollection.U18_UX.getEnrollmentChannelCollection().length);
        assertEquals(9, PrivacySandboxUxCollection.GA_UX.getEnrollmentChannelCollection().length);
        assertEquals(4, PrivacySandboxUxCollection.BETA_UX.getEnrollmentChannelCollection().length);
    }
}
