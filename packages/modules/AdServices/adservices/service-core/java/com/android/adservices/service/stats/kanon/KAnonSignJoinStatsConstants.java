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

package com.android.adservices.service.stats.kanon;

/** This class represents the constant values used for kanon logging. */
public class KAnonSignJoinStatsConstants {

    // Code for different KAnon actions (0-13).

    /** Unknown action or unset action */
    public static final int KANON_ACTION_UNSET = 0;

    /** KAnon get challenge action */
    public static final int KANON_ACTION_GET_CHALLENGE_HTTP_CALL = 1;

    /** Generating a kanon key attestation. */
    public static final int KANON_ACTION_KEY_ATTESTATION_CERT_GENERATION = 2;

    /** Making an http server call to fetch server parameters */
    public static final int KANON_ACTION_SERVER_PARAM_HTTP_CALL = 3;

    /** Parsing the server public params from the http response. */
    public static final int KANON_ACTION_SERVER_PUBLIC_PARAMS_PROTO_COMPOSITION = 4;

    /** Generating the client parameters using the ACT Library */
    public static final int KANON_ACTION_GENERATE_CLIENT_PARAM_ACT = 5;

    /** Making an http server call to register the client */
    public static final int KANON_ACTION_REGISTER_CLIENT_HTTP_CALL = 6;

    /** Parsing the register client proto from the http response */
    public static final int KANON_ACTION_REGISTER_CLIENT_RESPONSE_PROTO_COMPOSITION = 7;

    /** Generating the tokens request using the ACT library */
    public static final int KANON_ACTION_GENERATE_TOKENS_REQUEST_ACT = 8;

    /** Making an http server call to get the tokens */
    public static final int KANON_ACTION_GET_TOKENS_REQUEST_HTTP_CALL = 9;

    /** Parsing the get tokens response into a proto from the http response */
    public static final int KANON_ACTION_GET_TOKENS_RESPONSE_PROTO_COMPOSITION = 10;

    /** Verifying the tokens using the ACT library */
    public static final int KANON_ACTION_VERIFY_TOKENS_RESPONSE_ACT = 11;

    /** Recovering the tokens using ACT library */
    public static final int KANON_ACTION_RECOVER_TOKENS_ACT = 12;

    /** Making an http call to the server to make the join request */
    public static final int KANON_ACTION_JOIN_HTTP_CALL = 13;

    // The following are the result for the failure reason for the kanon action. It is set to 0 if
    // there was no failure.

    public static final int KANON_ACTION_FAILURE_REASON_UNSET = 0;

    /** Represents a network exception. Eg. 404 error. */
    public static final int KANON_ACTION_FAILURE_REASON_NETWORK_EXCEPTION = 1;

    /** Exception from the server. Eg. Server failed our request. */
    public static final int KANON_ACTION_FAILURE_REASON_SERVER_EXCEPTION = 2;

    /**
     * Not able to parse the protobuf. This will most likely happen when the server returned a bad
     * response
     */
    public static final int KANON_ACTION_FAILURE_REASON_PROTO_PARSE_EXCEPTION = 3;

    /** Internal error in our implementation. */
    public static final int KANON_ACTION_FAILURE_REASON_INTERNAL_ERROR = 4;

    /** Unknown error */
    public static final int KANON_ACTION_FAILURE_REASON_UNKNOWN_ERROR = 5;

    // The result code of a K-Anon Job (triggered immediately after auction or via background job)

    public static final int KANON_JOB_RESULT_UNSET = 0;

    /** Failure during KAnon initialize method */
    public static final int KANON_JOB_RESULT_INITIALIZE_FAILED = 1;

    /** Failure during sign process for this job */
    public static final int KANON_JOB_RESULT_SOME_OR_ALL_SIGN_FAILED = 2;

    /** Failure during join process for this job */
    public static final int KANON_JOB_RESULT_SOME_OR_ALL_JOIN_FAILED = 3;

    /** No failure in the job, everything is successful */
    public static final int KANON_JOB_RESULT_SUCCESS = 4;

    // The following result codes are related to the KAnon get challenge path.

    /** Unset result code, used for unknown scenarios */
    public static final int KEY_ATTESTATION_RESULT_UNSET = 0;

    /** Result code indicating the {@link java.security.KeyStoreException} error thrown. */
    public static final int KEY_ATTESTATION_RESULT_KEYSTORE_EXCEPTION = 1;

    /** Result code indicating the {@link IllegalStateException} error thrown. */
    public static final int KEY_ATTESTATION_RESULT_ILLEGAL_STATE_EXCEPTION = 2;

    /** Result code indicating the {@link java.security.cert.CertificateException} error thrown. */
    public static final int KEY_ATTESTATION_RESULT_CERTIFICATE_EXCEPTION = 3;

    /** Result code indicating the {@link java.security.NoSuchAlgorithmException} error thrown. */
    public static final int KEY_ATTESTATION_RESULT_IO_EXCEPTION = 4;

    /** Result code indicating the {@link java.security.NoSuchAlgorithmException} error thrown. */
    public static final int KEY_ATTESTATION_RESULT_NO_SUCH_ALGORITHM_EXCEPTION = 5;

    /** Result code indicating get challenge was successful */
    public static final int KEY_ATTESTATION_RESULT_SUCCESS = 6;
}
