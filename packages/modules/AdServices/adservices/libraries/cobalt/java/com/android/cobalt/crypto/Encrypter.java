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

package com.android.cobalt.crypto;

import android.annotation.NonNull;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.ObservationToEncrypt;

import java.util.Optional;

/** Interface for encrypting data types that need to be encrypted before upload. */
public interface Encrypter {
    /**
     * Encrypt an envelope.
     *
     * @return empty Optional if the {@link Envelope} to encrypt is empty
     * @throws EncryptionFailedException if encryption fails
     */
    Optional<EncryptedMessage> encryptEnvelope(@NonNull Envelope envelope)
            throws EncryptionFailedException;

    /**
     * Encrypt an observation.
     *
     * @return empty Optional if the {@link Observation} wrapped in {@link ObservationToEncrypt} to
     *     encrypt is empty
     * @throws EncryptionFailedException if encryption fails
     */
    Optional<EncryptedMessage> encryptObservation(@NonNull ObservationToEncrypt observation)
            throws EncryptionFailedException;
}
