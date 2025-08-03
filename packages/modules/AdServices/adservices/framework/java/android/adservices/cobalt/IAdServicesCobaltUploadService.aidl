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

package android.adservices.cobalt;

import android.adservices.cobalt.EncryptedCobaltEnvelopeParams;

/**
 * Service to upload AdServices' Cobalt data.
 *
 * {@hide}
 */
oneway interface IAdServicesCobaltUploadService {
    /**
     * Upload an encrypted Cobalt Envelope.

     * <p>Errors in this method execution, both because of problems within the binder
     * call and in the service execution, will cause a RuntimeException to be thrown.
     */
    void uploadEncryptedCobaltEnvelope(in EncryptedCobaltEnvelopeParams params);
}
