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

package com.android.adservices.service.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.kanon.KAnonMessageEntity.KanonMessageEntityStatus;

import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

public class KAnonMessageEntityTest {
    private static final String MESSAGE_HASH_SET = "somerandomstring";
    private static final long AD_SELECTION_ID_1 = 1;
    private static final long MESSAGE_ID_1 = 12;
    private static final Instant CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT = Instant.now();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuilderKAnonMessageEntity_buildsSuccessfully() {
        KAnonMessageEntity kAnonMessageEntity =
                KAnonMessageEntity.builder()
                        .setMessageId(MESSAGE_ID_1)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setHashSet(MESSAGE_HASH_SET)
                        .setStatus(KanonMessageEntityStatus.NOT_PROCESSED)
                        .setCorrespondingClientParametersExpiryInstant(
                                CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT)
                        .build();

        assertThat(kAnonMessageEntity).isNotNull();
        assertThat(kAnonMessageEntity.getMessageId()).isEqualTo(MESSAGE_ID_1);
        assertThat(kAnonMessageEntity.getHashSet()).isEqualTo(MESSAGE_HASH_SET);
        assertThat(kAnonMessageEntity.getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
        assertThat(kAnonMessageEntity.getStatus())
                .isEqualTo(KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntity.getCorrespondingClientParametersExpiryInstant())
                .isEqualTo(CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT);
    }

    @Test
    public void testBuilderKAnonMessageEntity_withoutMessageId_buildsSuccessfully() {
        KAnonMessageEntity kAnonMessageEntity =
                KAnonMessageEntity.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setHashSet(MESSAGE_HASH_SET)
                        .setStatus(KanonMessageEntityStatus.NOT_PROCESSED)
                        .setCorrespondingClientParametersExpiryInstant(
                                CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT)
                        .build();

        assertThat(kAnonMessageEntity).isNotNull();
        assertThat(kAnonMessageEntity.getHashSet()).isEqualTo(MESSAGE_HASH_SET);
        assertThat(kAnonMessageEntity.getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
    }

    @Test
    public void testBuilderKAnonMessageEntity_withoutClientExpiryInstant_buildsSuccessfully() {
        KAnonMessageEntity kAnonMessageEntity =
                KAnonMessageEntity.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setHashSet(MESSAGE_HASH_SET)
                        .setStatus(KanonMessageEntityStatus.NOT_PROCESSED)
                        .build();

        assertThat(kAnonMessageEntity).isNotNull();
        assertThat(kAnonMessageEntity.getHashSet()).isEqualTo(MESSAGE_HASH_SET);
        assertThat(kAnonMessageEntity.getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
    }

    @Test
    public void testBuilderKAnonMessageEntity_withoutHashSet_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        KAnonMessageEntity.builder()
                                .setMessageId(MESSAGE_ID_1)
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setStatus(KanonMessageEntityStatus.NOT_PROCESSED)
                                .setCorrespondingClientParametersExpiryInstant(
                                        CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT)
                                .build());
    }

    @Test
    public void testBuilderKAnonMessageEntity_withoutStatus_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        KAnonMessageEntity.builder()
                                .setMessageId(MESSAGE_ID_1)
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setHashSet(MESSAGE_HASH_SET)
                                .setCorrespondingClientParametersExpiryInstant(
                                        CORRESPONDING_CLIENT_PARAMS_EXPIRY_INSTANT)
                                .build());
    }
}
