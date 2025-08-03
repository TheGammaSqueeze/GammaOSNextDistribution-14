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
#include "act_jni.h"
#include "jni_util.h"
#include <act/act_v0/act_v0.h>
#include <act/act_v0/parameters.h>
#include <act/util.pb.h>
#include <vector>
#include <google/protobuf/message_lite.h>


using namespace private_join_and_compute::anonymous_counting_tokens;


const char* IllegalArgumentExceptionClass = "java/lang/IllegalArgumentException";
const char* IllegalStateExceptionClass = "java/lang/IllegalStateException";


JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_ActJniWrapper_generateClientParameters(
    JNIEnv *env,
    jclass,
    jbyteArray scheme_parameter_bytes,
    jbyteArray server_public_parameters_bytes
) {
    SchemeParameters scheme_parameter_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &scheme_parameter_proto, scheme_parameter_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env, IllegalArgumentExceptionClass, "Error while parsing SchemeParameters Proto");
        return nullptr;
    }
    ServerPublicParameters server_public_parameters_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &server_public_parameters_proto,
                                                                 server_public_parameters_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env,
                IllegalArgumentExceptionClass,
                "Error while parsing ServerPublicParameters Proto");
        return nullptr;
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();

    ClientParameters client_parameters;
    auto status_or
        = (act -> GenerateClientParameters(scheme_parameter_proto, server_public_parameters_proto));
    if(status_or.ok()) {
        client_parameters = std::move(status_or).value();
    } else {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalArgumentExceptionClass, status_or.status().ToString().c_str());
        return nullptr;
    }
    jbyteArray client_parameters_in_bytes =
                            jni_util::JniUtil::SerializeProtoToJniByteArray(env, client_parameters);
    return client_parameters_in_bytes;
}


JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_ActJniWrapper_generateTokensRequest(
    JNIEnv *env,
    jclass,
    jbyteArray messagesInBytes,
    jbyteArray schemeParametersInBytes,
    jbyteArray clientPublicParametersInBytes,
    jbyteArray clientPrivateParametersInBytes,
    jbyteArray serverPublicParametersInBytes
) {
    MessagesSet messagesProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &messagesProto, messagesInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env, IllegalArgumentExceptionClass, "Error parsing MessagesSet Proto");
        return nullptr;
    }
    std::vector<std::string> messagesVector(messagesProto.message_size());
    for(int i = 0; i < messagesProto.message_size(); i++) {
        messagesVector[i] = messagesProto.message(i);
    }

    SchemeParameters schemeParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &schemeParametersProto, schemeParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env, IllegalArgumentExceptionClass, "Error parsing SchemeParameters Proto");
        return nullptr;
    }
    ClientPublicParameters clientPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &clientPublicParametersProto,
                                                                clientPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
        return nullptr;
    }
    ClientPrivateParameters clientPrivateParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &clientPrivateParametersProto,
                                                               clientPrivateParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPrivateParameters Proto");

        return nullptr;
    }
    ServerPublicParameters serverPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &serverPublicParametersProto,
                                                                serverPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ServerPublicParameters Proto");
        return nullptr;
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();
    auto status_or = (act -> GenerateTokensRequest(
                                              messagesVector,
                                              schemeParametersProto,
                                              clientPublicParametersProto,
                                              clientPrivateParametersProto,
                                              serverPublicParametersProto));
    if (!status_or.ok()) {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalStateExceptionClass, status_or.status().ToString().c_str());
        return nullptr;
    }

    std::tuple<std::vector<std::string>, TokensRequest, TokensRequestPrivateState> response
                                                                    = std::move(status_or).value();

    std::vector<std::string> fingerprint_bytes = std::get<0>(response);
    TokensRequest token_request_temp = std::get<1>(response);
    TokensRequestPrivateState tokens_request_private_state_temp = std::get<2>(response);

    GeneratedTokensRequestProto generated_tokens_request_proto;
    for(auto& fingerprint: fingerprint_bytes) {
        generated_tokens_request_proto.add_fingerprints_bytes(fingerprint);
    }
    TokensRequest* token_request = new TokensRequest(token_request_temp);
    generated_tokens_request_proto.set_allocated_token_request(token_request);
    TokensRequestPrivateState* tokens_request_private_state
                            = new TokensRequestPrivateState(tokens_request_private_state_temp);
    generated_tokens_request_proto
                        .set_allocated_tokens_request_private_state(tokens_request_private_state);

    return jni_util::JniUtil::SerializeProtoToJniByteArray(env, generated_tokens_request_proto);
}

JNIEXPORT jboolean JNICALL Java_com_android_adservices_ActJniWrapper_verifyTokensResponse(
    JNIEnv *env,
    jclass,
    jbyteArray messagesInBytes,
    jbyteArray tokenRequestInBytes,
    jbyteArray tokensRequestPrivateStateInBytes,
    jbyteArray tokensResponseInBytes,
    jbyteArray schemeParametersInBytes,
    jbyteArray clientPublicParametersInBytes,
    jbyteArray clientPrivateParametersInBytes,
    jbyteArray serverPublicParametersInBytes
) {
    MessagesSet messagesProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &messagesProto, messagesInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalArgumentExceptionClass, "Error parsing MessagesSet Proto");
        return false;
    }
    std::vector<std::string> messagesVector(messagesProto.message_size());
    for(int i = 0; i < messagesProto.message_size(); i++) {
        messagesVector[i] = messagesProto.message(i);
    }

    TokensRequest tokenRequestProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &tokenRequestProto, tokenRequestInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalArgumentExceptionClass, "Error parsing TokensRequest Proto");
        return false;
    }

    TokensRequestPrivateState tokensRequestPrivateStateProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &tokensRequestPrivateStateProto,
                                                              tokensRequestPrivateStateInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing TokensRequestPrivateState Proto");
        return false;
    }

    TokensResponse tokensResponseProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &tokensResponseProto, tokensResponseInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing TokensResponse Proto");
        return false;
    }

    SchemeParameters schemeParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &schemeParametersProto, schemeParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalArgumentExceptionClass, "Error parsing SchemeParameters Proto");
        return false;
    }
    ClientPublicParameters clientPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &clientPublicParametersProto,
                                                                clientPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
        return false;
    }
    ClientPrivateParameters clientPrivateParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &clientPrivateParametersProto,
                                                                clientPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
        return false;
    }
    ServerPublicParameters serverPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &serverPublicParametersProto,
                                                                serverPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
        return false;
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();

    auto status = (act -> VerifyTokensResponse(
                                              messagesVector,
                                              tokenRequestProto,
                                              tokensRequestPrivateStateProto,
                                              tokensResponseProto,
                                              schemeParametersProto,
                                              clientPublicParametersProto,
                                              clientPrivateParametersProto,
                                              serverPublicParametersProto));

    if(status.ok()) {
        return true;
    } else {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalStateExceptionClass, status.ToString().c_str());
        return false;
    }
}


JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_ActJniWrapper_recoverTokens(
    JNIEnv *env,
    jclass,
    jbyteArray messagesInBytes,
    jbyteArray tokenRequestInBytes,
    jbyteArray tokensRequestPrivateStateInBytes,
    jbyteArray tokensResponseInBytes,
    jbyteArray schemeParametersInBytes,
    jbyteArray clientPublicParametersInBytes,
    jbyteArray clientPrivateParametersInBytes,
    jbyteArray serverPublicParametersInBytes
) {
    MessagesSet messagesProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &messagesProto, messagesInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing MessagesSet Proto");
        return nullptr;
    }
    std::vector<std::string> messagesVector(messagesProto.message_size());
    for(int i = 0; i < messagesProto.message_size(); i++) {
        messagesVector[i] = messagesProto.message(i);
    }

    TokensRequest tokenRequestProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &tokenRequestProto, tokenRequestInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing TokensRequest Proto");
        return nullptr;
    }

    TokensRequestPrivateState tokensRequestPrivateStateProto;
    if(!jni_util::JniUtil::BytesToCppProto(
                env, &tokensRequestPrivateStateProto, tokensRequestPrivateStateInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing TokensRequestPrivateState Proto");
        return nullptr;
    }

    TokensResponse tokensResponseProto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &tokensResponseProto, tokensResponseInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing TokensResponse Proto");
        return nullptr;
    }

    SchemeParameters schemeParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(
            env, &schemeParametersProto, schemeParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing SchemeParameters Proto");
        return nullptr;
    }
    ClientPublicParameters clientPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(
                env, &clientPublicParametersProto, clientPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
        return nullptr;
    }
    ClientPrivateParameters clientPrivateParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(
                env, &clientPrivateParametersProto, clientPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing SchemeParameters Proto");
        return nullptr;
    }
    ServerPublicParameters serverPublicParametersProto;
    if(!jni_util::JniUtil::BytesToCppProto(
                    env, &serverPublicParametersProto, serverPublicParametersInBytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ServerPublicParameters Proto");
        return nullptr;
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();

    auto status_or = (act -> RecoverTokens(
                                              messagesVector,
                                              tokenRequestProto,
                                              tokensRequestPrivateStateProto,
                                              tokensResponseProto,
                                              schemeParametersProto,
                                              clientPublicParametersProto,
                                              clientPrivateParametersProto,
                                              serverPublicParametersProto));



    if (!status_or.ok()) {
        jni_util::JniUtil::ThrowJavaException(
                    env, IllegalArgumentExceptionClass, status_or.status().ToString().c_str());
        return nullptr;
    }
    std::vector<Token> tokens = std::move(status_or).value();
    TokensSet tokens_set_proto;
    *tokens_set_proto.mutable_tokens() = {tokens.begin(), tokens.end()};
    return jni_util::JniUtil::SerializeProtoToJniByteArray(env, tokens_set_proto);
}

