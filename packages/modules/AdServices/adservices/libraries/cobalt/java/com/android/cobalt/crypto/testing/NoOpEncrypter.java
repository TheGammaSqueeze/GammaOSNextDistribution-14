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

import android.annotation.NonNull;

import androidx.annotation.VisibleForTesting;

import com.android.cobalt.crypto.Encrypter;
import com.android.cobalt.crypto.EncryptionFailedException;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.ObservationToEncrypt;

import java.util.Objects;
import java.util.Optional;

/** An encrypter that doesn't encrypt, just serializes the proto into the EncryptedMessage. */
@VisibleForTesting
public final class NoOpEncrypter implements Encrypter {
    /** Encrypt an envelope by serializing its bytes. */
    public Optional<EncryptedMessage> encryptEnvelope(@NonNull Envelope envelope)
            throws EncryptionFailedException {
        Objects.requireNonNull(envelope);

        if (envelope.toByteArray().length == 0) {
            return Optional.empty();
        }
        return Optional.of(
                EncryptedMessage.newBuilder().setCiphertext(envelope.toByteString()).build());
    }

    /** Encrypt an observation by serializing its bytes. */
    public Optional<EncryptedMessage> encryptObservation(@NonNull ObservationToEncrypt observation)
            throws EncryptionFailedException {
        Objects.requireNonNull(observation);

        if (observation.getObservation().toByteArray().length == 0) {
            return Optional.empty();
        }
        return Optional.of(
                EncryptedMessage.newBuilder()
                        .setContributionId(observation.getContributionId())
                        .setCiphertext(observation.getObservation().toByteString())
                        .build());
    }
}
