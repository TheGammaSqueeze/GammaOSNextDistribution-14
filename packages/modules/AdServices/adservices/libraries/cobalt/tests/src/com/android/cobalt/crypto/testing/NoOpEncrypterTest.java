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

package com.android.cobalt.crypto.testing;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationToEncrypt;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class NoOpEncrypterTest {
    private static final ByteString CONTRIBUTION_ID =
            ByteString.copyFromUtf8("test_contribution_id");
    private final NoOpEncrypter mNoOpEncrypter = new NoOpEncrypter();

    @Test
    public void encryptEnvelope() throws Exception {
        Envelope envelope =
                Envelope.newBuilder().setApiKey(ByteString.copyFromUtf8("test_api_key")).build();

        // NoOp Encrypt envelope.
        Optional<EncryptedMessage> encryptionResult = mNoOpEncrypter.encryptEnvelope(envelope);
        assertTrue(encryptionResult.isPresent());

        Envelope noOpEncryptedEnvelope = Envelope.parseFrom(encryptionResult.get().getCiphertext());
        assertThat(noOpEncryptedEnvelope).isEqualTo(envelope);
    }

    @Test
    public void encryptObservation() throws Exception {
        Observation observation =
                Observation.newBuilder()
                        .setRandomId(ByteString.copyFromUtf8("test_random_id"))
                        .build();

        // NoOp Encrypt observation.
        Optional<EncryptedMessage> encryptionResult =
                mNoOpEncrypter.encryptObservation(
                        ObservationToEncrypt.newBuilder()
                                .setObservation(observation)
                                .setContributionId(CONTRIBUTION_ID)
                                .build());
        assertTrue(encryptionResult.isPresent());

        Observation noOpEncryptedObservation =
                Observation.parseFrom(encryptionResult.get().getCiphertext());
        assertThat(noOpEncryptedObservation).isEqualTo(observation);
        assertThat(encryptionResult.get().getContributionId()).isEqualTo(CONTRIBUTION_ID);
    }

    @Test
    public void encryptEmptyEnvelope() {
        Envelope emptyEnvelope = Envelope.newBuilder().build();

        // NoOp Encrypt empty envelope.
        Optional<EncryptedMessage> encryptionResult = mNoOpEncrypter.encryptEnvelope(emptyEnvelope);
        assertFalse(encryptionResult.isPresent());
    }

    @Test
    public void encryptEmptyObservation() {
        Observation emptyObservation = Observation.newBuilder().build();

        // NoOp Encrypt empty observation.
        Optional<EncryptedMessage> encryptionResult =
                mNoOpEncrypter.encryptObservation(
                        ObservationToEncrypt.newBuilder()
                                .setObservation(emptyObservation)
                                .setContributionId(CONTRIBUTION_ID)
                                .build());
        assertFalse(encryptionResult.isPresent());

        // NoOp Encrypt unset observation.
        encryptionResult =
                mNoOpEncrypter.encryptObservation(
                        ObservationToEncrypt.newBuilder()
                                .setContributionId(CONTRIBUTION_ID)
                                .build());
        assertFalse(encryptionResult.isPresent());
    }
}
