/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.uwb.secure.csml;

import android.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.ResponseApdu;

import java.util.Optional;

/**
 * Response of get Data Object APDU, see CSML 1.0 - 7.2.1.5
 */
public class GetDoResponse extends FiRaResponse {
    @NonNull
    public final Optional<byte[]> data;

    private GetDoResponse(@NonNull ResponseApdu responseApdu) {
        super(responseApdu.getStatusWord());

        if (!isSuccess()) {
            data = Optional.empty();
            return;
        }
        // Don't parse the data. make it opaque data.
        data = Optional.of(responseApdu.getResponseData());
    }

    /**
     * Parse the response of GetDoCommand.
     */
    public static GetDoResponse fromResponseApdu(@NonNull ResponseApdu responseApdu) {
        return new GetDoResponse(responseApdu);
    }
}
