/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.Instant;

public class DBKAnonMessageTest {

    private static final String KANON_HASH_SET = "somehashset";
    private static final long AD_SELECTION_ID_1 = 1;
    private static final Instant INSTANT_1 = Instant.now();
    private static final Instant INSTANT_2 = Instant.now().plusSeconds(1234);
    private static final Instant INSTANT_3 = Instant.now().plusSeconds(14);
    private static final Long MESSAGE_ID_1 = 1L;

    @Test
    public void testBuildKAnonMessage_validMessage_buildsSuccessfully() {
        DBKAnonMessage dbkAnonMessage =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                        .setExpiryInstant(INSTANT_3)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                        .setMessageId(MESSAGE_ID_1)
                        .build();

        assertThat(dbkAnonMessage.getStatus())
                .isEqualTo(KAnonMessageConstants.MessageStatus.SIGNED);
        assertThat(dbkAnonMessage.getKanonHashSet()).isEqualTo(KANON_HASH_SET);
        assertThat(dbkAnonMessage.getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
        assertThat(dbkAnonMessage.getExpiryInstant()).isEqualTo(INSTANT_3);
        assertThat(dbkAnonMessage.getCreatedAt()).isEqualTo(INSTANT_2);
        assertThat(dbkAnonMessage.getCorrespondingClientParametersExpiryInstant())
                .isEqualTo(INSTANT_1);
        assertThat(dbkAnonMessage.getMessageId()).isEqualTo(MESSAGE_ID_1);
        assertThat(dbkAnonMessage.getMessageId()).isNotNull();
    }

    @Test
    public void testBuildKAnonMessage_unsetMessageId_buildsSuccessfully() {
        DBKAnonMessage dbkAnonMessage =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                        .setExpiryInstant(INSTANT_3)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                        .build();

        assertThat(dbkAnonMessage.getStatus())
                .isEqualTo(KAnonMessageConstants.MessageStatus.SIGNED);
        assertThat(dbkAnonMessage.getKanonHashSet()).isEqualTo(KANON_HASH_SET);
        assertThat(dbkAnonMessage.getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
        assertThat(dbkAnonMessage.getExpiryInstant()).isEqualTo(INSTANT_3);
        assertThat(dbkAnonMessage.getCreatedAt()).isEqualTo(INSTANT_2);
        assertThat(dbkAnonMessage.getCorrespondingClientParametersExpiryInstant())
                .isEqualTo(INSTANT_1);
    }

    @Test
    public void testBuildKAnonMessage_unsetCreatedAt_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBKAnonMessage.builder()
                                .setKanonHashSet(KANON_HASH_SET)
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                                .setExpiryInstant(INSTANT_3)
                                .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                                .build());
    }

    @Test
    public void testBuildKAnonMessage_unsetKAnonHashSet_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBKAnonMessage.builder()
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                                .setExpiryInstant(INSTANT_3)
                                .setCreatedAt(INSTANT_2)
                                .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                                .build());
    }

    @Test
    public void testBuildKAnonMessage_unsetExpiryInstant_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBKAnonMessage.builder()
                                .setKanonHashSet(KANON_HASH_SET)
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                                .setCreatedAt(INSTANT_2)
                                .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                                .build());
    }

    @Test
    public void testBuildKAnonMessage_unsetStatus_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBKAnonMessage.builder()
                                .setKanonHashSet(KANON_HASH_SET)
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                                .setCreatedAt(INSTANT_2)
                                .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                                .build());
    }
}
