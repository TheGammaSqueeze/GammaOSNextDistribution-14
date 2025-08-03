/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.virt.vm_attestation.testservice;

/** {@hide} */
interface IAttestationService {
    const int PORT = 5679;

    /**
     * Requests attestation for testing.
     *
     * A fake key pair should be provisioned with the call to
     * {@link VirtualMachine#enableTestAttestation()} before calling this method.
     *
     * The attestation result will be cached in the VM and can be validated with
     * {@link #validateAttestationResult}.
     */
    void requestAttestationForTesting();

    /**
     * Validates the attestation result returned by the last call to
     * {@link #requestAttestationForTesting}.
     */
    void validateAttestationResult();
}
