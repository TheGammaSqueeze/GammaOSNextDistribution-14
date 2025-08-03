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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.secure.iso7816.TlvParser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * See CSML 1.0 - 8.2.2.14.1.5 SWAP ADF
 */
public class SwapInAdfResponse extends FiRaResponse {
    //TODO: the tag is not defined in CSML, use 0x06.
    @VisibleForTesting
    static final Tag SLOT_IDENTIFIER_TAG = new Tag((byte) 0x06);

    @NonNull
    public final Optional<byte[]> slotIdentifier;

    private SwapInAdfResponse(ResponseApdu responseApdu) {
        super(responseApdu.getStatusWord());

        if (!isSuccess()) {
            slotIdentifier = Optional.empty();
            return;
        }

        Map<Tag, List<TlvDatum>> tlvsMap = TlvParser.parseTlvs(responseApdu);
        List<TlvDatum> tlvs = tlvsMap.get(SLOT_IDENTIFIER_TAG);
        if (tlvs != null && tlvs.size() > 0) {
            slotIdentifier = Optional.of(tlvs.get(0).value);
        } else {
            slotIdentifier = Optional.empty();
        }
    }

    /**
     * Parse the response of SwapInCommand.
     */
    public static SwapInAdfResponse fromResponseApdu(@NonNull ResponseApdu responseApdu) {
        return new SwapInAdfResponse(responseApdu);
    }
}
