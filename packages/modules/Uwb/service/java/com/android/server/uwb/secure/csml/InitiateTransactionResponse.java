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

import static com.android.server.uwb.secure.csml.FiRaResponse.PROPRIETARY_RESPONSE_TAG;

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
 * Response of Initiate Traction APDU, see CSML 1.0 - 8.2.2.14.2.8
 */
public class InitiateTransactionResponse extends FiRaResponse {

    @VisibleForTesting
    public static final Tag STATUS_TAG = new Tag((byte) 0x80);
    @VisibleForTesting
    public static final Tag DATA_TAG = new Tag((byte) 0x81);

    /**
     * The data should be sent to the peer device.
     */
    @NonNull
    public final Optional<byte[]> outboundDataToRemoteApplet;

    /**
     * The status from the response data.
     */
    @NonNull
    private byte mStatus = (byte) 0;

    private boolean hasOutboundData() {
        return mStatus == (byte) 0x80;
    }

    private InitiateTransactionResponse(ResponseApdu responseApdu) {
        super(responseApdu.getStatusWord());

        if (!isSuccess()) {
            outboundDataToRemoteApplet = Optional.empty();
            return;
        }
        Map<Tag, List<TlvDatum>> tlvsMap = TlvParser.parseTlvs(responseApdu);
        List<TlvDatum> proprietaryTlv = tlvsMap.get(PROPRIETARY_RESPONSE_TAG);
        if (proprietaryTlv == null || proprietaryTlv.size() == 0) {
            outboundDataToRemoteApplet = Optional.empty();
            return;
        }

        tlvsMap = TlvParser.parseTlvs(proprietaryTlv.get(0).value);
        List<TlvDatum> statusTlvs = tlvsMap.get(STATUS_TAG);
        if (statusTlvs != null && statusTlvs.size() > 0) {
            mStatus = statusTlvs.get(0).value[0];
        }
        if (!hasOutboundData()) {
            outboundDataToRemoteApplet = Optional.empty();
            return;
        }
        List<TlvDatum> dataTlvs = tlvsMap.get(DATA_TAG);
        if (dataTlvs != null && dataTlvs.size() > 0) {
            outboundDataToRemoteApplet = Optional.of(dataTlvs.get(0).value);
        } else {
            outboundDataToRemoteApplet = Optional.empty();
        }
    }

    /**
     * Parse the ResponseApdu of InitiateTractionCommand.
     */
    @NonNull
    public static InitiateTransactionResponse fromResponseApdu(
            @NonNull ResponseApdu responseApdu) {
        return new InitiateTransactionResponse(responseApdu);
    }
}
