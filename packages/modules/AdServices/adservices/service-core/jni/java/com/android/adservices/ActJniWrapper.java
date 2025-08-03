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

package com.android.adservices;

/** Contains JNI wrappers for the ACT(Anonymous counting tokens). */
public class ActJniWrapper {

    static {
        System.loadLibrary("hpke_jni");
    }

    /**
     * Returns a fresh set of Client parameters corresponding to these SchemeParameters and
     * ServerPublicParameters.
     */
    public static native byte[] generateClientParameters(
            byte[] schemeParameters, byte[] serverPublicParameters);

    /**
     * Returns a tuple of client_fingerprints, TokensRequest and TokensRequestPrivateState for the
     * given set of messages.
     */
    public static native byte[] generateTokensRequest(
            byte[] messages,
            byte[] schemeParameters,
            byte[] clientPublicParameters,
            byte[] clientPrivateParameters,
            byte[] serverPublicParameters);

    /**
     * Returns {@code true} on a valid response. Returns {@code false} if the parameters don't
     * correspond to ACT v0.
     */
    public static native boolean verifyTokensResponse(
            byte[] messages,
            byte[] tokensRequest,
            byte[] tokensRequestPrivateState,
            byte[] tokensResponse,
            byte[] schemeParameters,
            byte[] clientPublicParameters,
            byte[] clientPrivateParameters,
            byte[] serverPublicParameters);

    /** Returns a vector of tokens corresponding to the supplied messages. */
    public static native byte[] recoverTokens(
            byte[] messages,
            byte[] tokensRequest,
            byte[] tokensRequestPrivateState,
            byte[] tokensResponse,
            byte[] schemeParameters,
            byte[] clientPublicParameters,
            byte[] clientPrivateParameters,
            byte[] serverPublicParameters);
}
