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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

public class DBEncoderEndpointFixture {

    private static final String PATH = "/download/encoder";

    public static final DBEncoderEndpoint anEndpoint() {
        return anEndpointBuilder().build();
    }

    public static final DBEncoderEndpoint.Builder anEndpointBuilder() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        return anEndpointBuilder(buyer);
    }

    public static final DBEncoderEndpoint.Builder anEndpointBuilder(AdTechIdentifier buyer) {
        return DBEncoderEndpoint.builder()
                .setBuyer(buyer)
                .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                .setCreationTime(CommonFixture.FIXED_NOW);
    }
}
