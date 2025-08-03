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
 * See CSML 1.0 - 8.2.2.14.2.7 Tunnel
 */
public class TunnelResponse extends FiRaResponse {
    @VisibleForTesting
    static final Tag DATA_TAG = new Tag(new byte[] { (byte) 0x81 });

    /**
     * The data should be sent to the peer device.
     */
    @NonNull
    public final Optional<byte[]> outboundDataOrApdu;

    private TunnelResponse(ResponseApdu responseApdu) {
        super(responseApdu.getStatusWord());
        if (isSuccess()) {
            Map<Tag, List<TlvDatum>> tlvsMap = TlvParser.parseTlvs(responseApdu);
            List<TlvDatum> proprietaryTlv = tlvsMap.get(PROPRIETARY_RESPONSE_TAG);
            if (proprietaryTlv == null || proprietaryTlv.size() == 0) {
                outboundDataOrApdu = Optional.empty();
                return;
            }

            tlvsMap = TlvParser.parseTlvs(proprietaryTlv.get(0).value);
            List<TlvDatum> dataTlvs = tlvsMap.get(DATA_TAG);
            if (dataTlvs != null && dataTlvs.size() > 0) {
                outboundDataOrApdu = Optional.of(dataTlvs.get(0).value);
            } else {
                outboundDataOrApdu = Optional.empty();
            }
        } else {
            outboundDataOrApdu = Optional.empty();
        }
    }

    /**
     * Parse the response of InitiateTractionCommand.
     */
    public static TunnelResponse fromResponseApdu(@NonNull ResponseApdu responseApdu) {
        return new TunnelResponse(responseApdu);
    }
}
