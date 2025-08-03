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

import com.google.protobuf.InvalidProtocolBufferException;

import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPrivateParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.GeneratedTokensRequestProto;
import private_join_and_compute.anonymous_counting_tokens.MessagesSet;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.TokensRequest;
import private_join_and_compute.anonymous_counting_tokens.TokensRequestPrivateState;
import private_join_and_compute.anonymous_counting_tokens.TokensResponse;
import private_join_and_compute.anonymous_counting_tokens.TokensSet;

public interface AnonymousCountingTokens {

    /**
     * Returns a fresh set of Client parameters corresponding to these SchemeParameters and
     * ServerPublicParameters.
     */
    ClientParameters generateClientParameters(
            SchemeParameters schemeParametersProto,
            ServerPublicParameters serverPublicParametersProto)
            throws InvalidProtocolBufferException;

    /**
     * Returns a tuple of client_fingerprints, TokensRequest and TokensRequestPrivateState for the
     * given set of messages.
     */
    GeneratedTokensRequestProto generateTokensRequest(
            MessagesSet messagesProto,
            SchemeParameters schemeParametersProto,
            ClientPublicParameters clientPublicParametersProto,
            ClientPrivateParameters clientPrivateParametersProto,
            ServerPublicParameters serverPublicParameters)
            throws InvalidProtocolBufferException;

    /**
     * Returns {@code true} on a valid response. Returns {@code false} if the parameters don't
     * correspond to ACT v0.
     */
    boolean verifyTokensResponse(
            MessagesSet messagesProto,
            TokensRequest tokensRequestProto,
            TokensRequestPrivateState tokensRequestPrivateStateProto,
            TokensResponse tokenResponseProto,
            SchemeParameters schemeParametersProto,
            ClientPublicParameters clientPublicParametersProto,
            ClientPrivateParameters clientPrivateParametersProto,
            ServerPublicParameters serverPublicParameters);

    /** Returns a vector of tokens corresponding to the supplied messages. */
    TokensSet recoverTokens(
            MessagesSet messagesProto,
            TokensRequest tokensRequestProto,
            TokensRequestPrivateState tokensRequestPrivateStateProto,
            TokensResponse tokenResponseProto,
            SchemeParameters schemeParametersProto,
            ClientPublicParameters clientPublicParametersProto,
            ClientPrivateParameters clientPrivateParametersProto,
            ServerPublicParameters serverPublicParameters)
            throws InvalidProtocolBufferException;
}
