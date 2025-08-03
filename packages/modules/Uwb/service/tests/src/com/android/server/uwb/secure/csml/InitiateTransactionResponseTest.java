/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import org.junit.Test;

public class InitiateTransactionResponseTest {
    @Test
    public void validResponse() {
        TlvDatum statusTlv = new TlvDatum(InitiateTransactionResponse.STATUS_TAG,
                DataTypeConversionUtil.hexStringToByteArray("80"));
        TlvDatum dataTlv = new TlvDatum(InitiateTransactionResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        InitiateTransactionResponse initiateTransactionResponse =
                InitiateTransactionResponse.fromResponseApdu(responseApdu);

        assertThat(initiateTransactionResponse.outboundDataToRemoteApplet.get()).isEqualTo(
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
    }

    @Test
    public void wrongStatusWord() {
        ResponseApdu responseApdu = ResponseApdu.fromStatusWord(
                StatusWord.SW_NO_SPECIFIC_DIAGNOSTIC);
        InitiateTransactionResponse initiateTransactionResponse =
                InitiateTransactionResponse.fromResponseApdu(responseApdu);

        assertThat(initiateTransactionResponse.outboundDataToRemoteApplet.isPresent()).isFalse();
    }

    @Test
    public void wrongTopTag() {
        TlvDatum statusTlv = new TlvDatum(InitiateTransactionResponse.STATUS_TAG,
                DataTypeConversionUtil.hexStringToByteArray("80"));
        TlvDatum dataTlv = new TlvDatum(InitiateTransactionResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(new TlvDatum.Tag((byte) 0x01),
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        InitiateTransactionResponse initiateTransactionResponse =
                InitiateTransactionResponse.fromResponseApdu(responseApdu);

        assertThat(initiateTransactionResponse.outboundDataToRemoteApplet.isPresent()).isFalse();
    }

    @Test
    public void wrongStatusValue() {
        TlvDatum statusTlv = new TlvDatum(InitiateTransactionResponse.STATUS_TAG,
                DataTypeConversionUtil.hexStringToByteArray("00"));
        TlvDatum dataTlv = new TlvDatum(InitiateTransactionResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        InitiateTransactionResponse initiateTransactionResponse =
                InitiateTransactionResponse.fromResponseApdu(responseApdu);

        assertThat(initiateTransactionResponse.outboundDataToRemoteApplet.isPresent()).isFalse();
    }

    @Test
    public void emptyOutboundData() {
        TlvDatum statusTlv = new TlvDatum(InitiateTransactionResponse.STATUS_TAG,
                DataTypeConversionUtil.hexStringToByteArray("80"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                statusTlv.toBytes());
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        InitiateTransactionResponse initiateTransactionResponse =
                InitiateTransactionResponse.fromResponseApdu(responseApdu);

        assertThat(initiateTransactionResponse.outboundDataToRemoteApplet.isPresent()).isFalse();
    }
}
