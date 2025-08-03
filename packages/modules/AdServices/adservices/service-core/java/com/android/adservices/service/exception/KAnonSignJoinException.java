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

package com.android.adservices.service.exception;

public class KAnonSignJoinException extends RuntimeException {

    public enum KAnonAction {
        UNSET,
        // 1 - 7 are actions required to initialize a K-Anon Caller
        GET_CHALLENGE_HTTP_CALL,
        KEY_ATTESTATION_CERT_GENERATION,
        SERVER_PARAM_HTTP_CALL,
        SERVER_PUBLIC_PARAMS_PROTO_COMPOSITION,
        GENERATE_CLIENT_PARAM_ACT,
        REGISTER_CLIENT_HTTP_CALL,
        REGISTER_CLIENT_RESPONSE_PROTO_COMPOSITION,

        // Following are various actions required to perform the sign and join calls
        GENERATE_TOKENS_REQUEST_ACT,
        GET_TOKENS_REQUEST_HTTP_CALL,
        GET_TOKENS_RESPONSE_PROTO_COMPOSITION,
        VERIFY_TOKENS_RESPONSE_ACT,
        RECOVER_TOKENS_ACT,
        JOIN_HTTP_CALL,
        BINARY_HTTP_RESPONSE
    }

    private final KAnonAction mAction;

    public KAnonSignJoinException(String message, Throwable cause) {
        super(message, cause);
        mAction = KAnonAction.UNSET;
    }

    public KAnonSignJoinException(String message) {
        super(message);
        mAction = KAnonAction.UNSET;
    }

    public KAnonSignJoinException(String message, KAnonAction kAnonAction) {
        super(message);
        mAction = kAnonAction;
    }

    public KAnonSignJoinException(String message, Throwable cause, KAnonAction kAnonAction) {
        super(message, cause);
        mAction = kAnonAction;
    }

    public KAnonAction getAction() {
        return mAction;
    }
}
