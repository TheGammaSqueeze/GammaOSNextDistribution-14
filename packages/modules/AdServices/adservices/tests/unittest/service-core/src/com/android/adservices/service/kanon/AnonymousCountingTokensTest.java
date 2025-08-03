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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ActJniUtility;
import com.android.adservices.common.SdkLevelSupportRule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPrivateParameters;
import private_join_and_compute.anonymous_counting_tokens.ClientPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.GeneratedTokensRequestProto;
import private_join_and_compute.anonymous_counting_tokens.MessagesSet;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPrivateParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.TokensRequest;
import private_join_and_compute.anonymous_counting_tokens.TokensRequestPrivateState;
import private_join_and_compute.anonymous_counting_tokens.TokensResponse;
import private_join_and_compute.anonymous_counting_tokens.TokensSet;
import private_join_and_compute.anonymous_counting_tokens.Transcript;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class AnonymousCountingTokensTest {
    private SchemeParameters mSchemeParameters;
    private ServerPublicParameters mServerPublicParameters;
    private static final String GOLDEN_TRANSCRIPT_PATH = "act/golden_transcript_1";
    private ServerPrivateParameters mServerPrivateParameters;
    private ClientParameters mClientParameters;
    private Transcript mTranscript;
    private AnonymousCountingTokens mAnonymousCountingTokens;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws IOException {
        Context sContext = ApplicationProvider.getApplicationContext();
        InputStream inputStream = sContext.getAssets().open(GOLDEN_TRANSCRIPT_PATH);
        mTranscript = Transcript.parseDelimitedFrom(inputStream);

        mAnonymousCountingTokens = new AnonymousCountingTokensImpl();
        mSchemeParameters = mTranscript.getSchemeParameters();
        mServerPublicParameters = mTranscript.getServerParameters().getPublicParameters();
        mServerPrivateParameters = mTranscript.getServerParameters().getPrivateParameters();
        mClientParameters = mTranscript.getClientParameters();
    }

    @Test
    public void testGenerateClientParams_generatesDifferentClientParams() throws IOException {
        ClientParameters clientParameters =
                mAnonymousCountingTokens.generateClientParameters(
                        mSchemeParameters, mServerPublicParameters);
        ClientParameters otherClientParameters =
                mAnonymousCountingTokens.generateClientParameters(
                        mSchemeParameters, mServerPublicParameters);

        Assert.assertNotEquals(
                clientParameters
                        .getPublicParameters()
                        .getClientPublicParametersV0()
                        .getDyVrfPublicKey()
                        .getCommitPrfKey(),
                otherClientParameters
                        .getPublicParameters()
                        .getClientPublicParametersV0()
                        .getDyVrfPublicKey()
                        .getCommitPrfKey());
    }

    @Test
    public void testGenerateClientParams_withInvalidParameters_throwIllegalArgumentException() {
        SchemeParameters invalidSchemeParameters = SchemeParameters.newBuilder().build();
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAnonymousCountingTokens.generateClientParameters(
                                invalidSchemeParameters, mServerPublicParameters));
    }

    @Test
    public void testGenerateClientParams__clientParametersCheckPasses()
            throws InvalidProtocolBufferException {

        ClientParameters clientParameters =
                mAnonymousCountingTokens.generateClientParameters(
                        mSchemeParameters, mServerPublicParameters);
        Assert.assertTrue(
                ActJniUtility.checkClientParameters(
                        mSchemeParameters,
                        clientParameters.getPublicParameters(),
                        mServerPublicParameters,
                        mServerPrivateParameters));
    }

    @Test
    public void test_generateTokensRequest_fingerprintsMatchOnlyForEqualMessages()
            throws InvalidProtocolBufferException {
        ClientPublicParameters clientPublicParameters = mClientParameters.getPublicParameters();
        ClientPrivateParameters clientPrivateParameters = mClientParameters.getPrivateParameters();
        MessagesSet messagesSet =
                MessagesSet.newBuilder()
                        .addMessage("message_0")
                        .addMessage("message_1")
                        .addMessage("message_2")
                        .build();
        MessagesSet messagesSet2 =
                MessagesSet.newBuilder()
                        .addMessage("message_2")
                        .addMessage("message_3")
                        .addMessage("message_4")
                        .build();

        GeneratedTokensRequestProto generatedTokenRequestResponse =
                mAnonymousCountingTokens.generateTokensRequest(
                        messagesSet,
                        mSchemeParameters,
                        clientPublicParameters,
                        clientPrivateParameters,
                        mServerPublicParameters);
        GeneratedTokensRequestProto generatedTokenRequestResponse2 =
                mAnonymousCountingTokens.generateTokensRequest(
                        messagesSet2,
                        mSchemeParameters,
                        clientPublicParameters,
                        clientPrivateParameters,
                        mServerPublicParameters);

        List<ByteString> fingerprintsByteList1 =
                generatedTokenRequestResponse.getFingerprintsBytesList();
        List<ByteString> fingerprintsByteList2 =
                generatedTokenRequestResponse2.getFingerprintsBytesList();
        Assert.assertEquals(fingerprintsByteList1.get(2), fingerprintsByteList2.get(0));
        for (int i = 0; i < fingerprintsByteList1.size(); i++) {
            for (int j = 0; j < fingerprintsByteList2.size(); j++) {
                if (i != 2 && j != 0) {
                    Assert.assertNotEquals(
                            fingerprintsByteList1.get(i), fingerprintsByteList2.get(j));
                }
            }
        }
    }

    @Test
    public void test_verifyTokensResponse_throwsErrorWithIncorrectTokenResponse()
            throws InvalidProtocolBufferException {
        ClientPublicParameters clientPublicParameters = mClientParameters.getPublicParameters();
        ClientPrivateParameters clientPrivateParameters = mClientParameters.getPrivateParameters();
        MessagesSet messagesSet =
                MessagesSet.newBuilder()
                        .addMessage("message_0")
                        .addMessage("message_1")
                        .addMessage("message_2")
                        .build();
        GeneratedTokensRequestProto generatedTokenRequestResponse =
                mAnonymousCountingTokens.generateTokensRequest(
                        messagesSet,
                        mSchemeParameters,
                        clientPublicParameters,
                        clientPrivateParameters,
                        mServerPublicParameters);
        TokensRequestPrivateState tokensRequestPrivateState =
                generatedTokenRequestResponse.getTokensRequestPrivateState();
        TokensRequest tokensRequest = generatedTokenRequestResponse.getTokenRequest();
        TokensResponse tokensResponse = TokensResponse.newBuilder().build();

        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        mAnonymousCountingTokens.verifyTokensResponse(
                                messagesSet,
                                tokensRequest,
                                tokensRequestPrivateState,
                                tokensResponse,
                                mSchemeParameters,
                                clientPublicParameters,
                                clientPrivateParameters,
                                mServerPublicParameters));
    }

    @Test
    public void test_verifyTokensResponse_returnsTrueWithCorrectTokenResponse() {
        MessagesSet messagesSet =
                MessagesSet.newBuilder().addAllMessage(mTranscript.getMessagesList()).build();

        Assert.assertTrue(
                mAnonymousCountingTokens.verifyTokensResponse(
                        messagesSet,
                        mTranscript.getTokensRequest(),
                        mTranscript.getTokensRequestPrivateState(),
                        mTranscript.getTokensResponse(),
                        mTranscript.getSchemeParameters(),
                        mTranscript.getClientParameters().getPublicParameters(),
                        mTranscript.getClientParameters().getPrivateParameters(),
                        mTranscript.getServerParameters().getPublicParameters()));
    }

    @Test
    public void test_recoverTokens_returnsTheCorrectTokens() throws InvalidProtocolBufferException {
        MessagesSet messagesSet =
                MessagesSet.newBuilder().addAllMessage(mTranscript.getMessagesList()).build();

        TokensSet tokensSet =
                mAnonymousCountingTokens.recoverTokens(
                        messagesSet,
                        mTranscript.getTokensRequest(),
                        mTranscript.getTokensRequestPrivateState(),
                        mTranscript.getTokensResponse(),
                        mTranscript.getSchemeParameters(),
                        mTranscript.getClientParameters().getPublicParameters(),
                        mTranscript.getClientParameters().getPrivateParameters(),
                        mTranscript.getServerParameters().getPublicParameters());

        Assert.assertEquals(tokensSet.getTokensList(), mTranscript.getTokensList());
    }
}
