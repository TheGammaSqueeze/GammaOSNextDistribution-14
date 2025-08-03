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

package com.android.adservices.service.kanon;

import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.SchemeParametersV0;

public class KAnonUtil {

    public static SchemeParameters getSchemeParameters() {
        long kDefaultSecurityParameter = 128;
        long kDefaultChallengeLength = 128;
        long kDefaultCamenischShoupS = 1;
        long kDefaultCurveId = 415;
        int kDefaultModulusLengthBits = 3072;
        int test_modulus_length = 1536;
        String random_oracle_prefix =
                "ActV0SchemeParametersPedersenBatchSize32ModulusLengthBits2048CamenischShoupVectorLength2";
        long pedersen_batch_size = 32;
        long modulus_length = 2048;
        long camensich_shoup_vector_encryption_length = 2;
        SchemeParametersV0 schemeParametersV0 =
                SchemeParametersV0.newBuilder()
                        .setSecurityParameter(kDefaultSecurityParameter)
                        .setChallengeLengthBits(kDefaultChallengeLength)
                        .setCamenischShoupS(kDefaultCamenischShoupS)
                        .setVectorEncryptionLength(camensich_shoup_vector_encryption_length)
                        .setPedersenBatchSize(pedersen_batch_size)
                        .setRandomOraclePrefix(random_oracle_prefix)
                        .setPrfEcGroup(kDefaultCurveId)
                        .setModulusLengthBits(modulus_length)
                        .build();
        return SchemeParameters.newBuilder().setSchemeParametersV0(schemeParametersV0).build();
    }
}
