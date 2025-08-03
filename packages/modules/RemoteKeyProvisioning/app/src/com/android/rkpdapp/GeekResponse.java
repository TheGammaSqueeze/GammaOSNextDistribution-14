/**
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.rkpdapp;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Convenience class for packaging up the values returned by the server when initially requesting
 * an Endpoint Encryption Key for remote provisioning. Those values are described by the following
 * CDDL Schema:
 *    GeekResponse = [
 *        [+CurveAndEek],
 *        challenge : bstr,
 *        ? Config,
 *    ]
 *    CurveAndEek = [
 *        curve: uint,
 *        EekChain
 *    ]
 *    Config = {
 *        ? "num_extra_attestation_keys": uint,
 *        ? "time_to_refresh_hours" : uint,
 *        ? "provisioning_url": tstr,
 *    }
 *
 * The CDDL that defines EekChain is defined in the RemoteProvisioning HAL, but this app does not
 * require any semantic understanding of the format to perform its function.
 */
public class GeekResponse {
    public static final int NO_EXTRA_KEY_UPDATE = -1;
    private byte[] mChallenge;
    private final Map<Integer, byte[]> mCurveToGeek;
    public int numExtraAttestationKeys;
    public Duration timeToRefresh;
    public String provisioningUrl;

    /**
     * Default initializer.
     */
    public GeekResponse() {
        mCurveToGeek = new HashMap<>();
        numExtraAttestationKeys = NO_EXTRA_KEY_UPDATE;
    }

    /**
     * Add a CBOR encoded array containing a GEEK and the corresponding certificate chain, keyed
     * on the EC {@code curve}.
     *
     * @param curve an integer which represents an EC curve.
     * @param geekChain the encoded CBOR array containing an ECDH key and corresponding certificate
     *                  chain.
     */
    public void addGeek(int curve, byte[] geekChain) {
        mCurveToGeek.put(curve, geekChain);
    }

    /**
     * Returns the encoded CBOR array with an ECDH key corresponding to the provided {@code curve}.
     *
     * @param curve an integer which represents an EC curve.
     * @return the corresponding encoded CBOR array.
     */
    public byte[] getGeekChain(int curve) {
        return mCurveToGeek.get(curve);
    }

    /**
     * Sets the {@code challenge}.
     */
    public void setChallenge(byte[] challenge) {
        mChallenge = challenge;
    }

    /**
     * Returns the {@code challenge}.
     *
     * @return the challenge that will be embedded in the CSR sent to the server.
     */
    public byte[] getChallenge() {
        return mChallenge;
    }
}
