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
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.primitives.Bytes;

import org.junit.Test;

public class DispatchResponseTest {
    @Test
    public void validResponseWithTransactionSuccess() {
        TlvDatum statusTlv = new TlvDatum(DispatchResponse.STATUS_TAG, new byte[] {(byte) 0x00});
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                statusTlv);
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.getOutboundData().isPresent()).isFalse();
    }

    @Test
    public void validResponseWithTransactionError() {
        TlvDatum statusTlv = new TlvDatum(DispatchResponse.STATUS_TAG, new byte[] {(byte) 0xFF});
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                statusTlv);
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(1);
        assertThat(dispatchResponse.notifications.get(0).notificationEventId)
                .isEqualTo(DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED);
        assertThat(dispatchResponse.getOutboundData().isPresent()).isFalse();
    }

    @Test
    public void validResponseWithOutboundDataToRemote() {
        TlvDatum statusTlv = new TlvDatum(DispatchResponse.STATUS_TAG, new byte[] {(byte) 0x80});
        TlvDatum dataTlv = new TlvDatum(DispatchResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(0);
        assertThat(dispatchResponse.getOutboundData().isPresent()).isTrue();
        assertThat(dispatchResponse.getOutboundData().get().target)
                .isEqualTo(DispatchResponse.OUTBOUND_TARGET_REMOTE);
        assertThat(dispatchResponse.getOutboundData().get().data)
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0A0B"));
    }

    @Test
    public void validResponseWithOutboundDataToHost() {
        TlvDatum statusTlv = new TlvDatum(DispatchResponse.STATUS_TAG, new byte[] {(byte) 0x81});
        TlvDatum dataTlv = new TlvDatum(DispatchResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(0);
        assertThat(dispatchResponse.getOutboundData().isPresent()).isTrue();
        assertThat(dispatchResponse.getOutboundData().get().target)
                .isEqualTo(DispatchResponse.OUTBOUND_TARGET_HOST);
        assertThat(dispatchResponse.getOutboundData().get().data)
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0A0B"));
    }

    @Test
    public void validResponseWithAdfSelectedNotification() {
        TlvDatum notiFormat = new TlvDatum(DispatchResponse.NOTIFICATION_FORMAT_TAG,
                new byte[] {(byte) 0x00});
        TlvDatum notiId = new TlvDatum(DispatchResponse.NOTIFICATION_EVENT_ID_TAG,
                new byte[] { (byte) 0x00});
        TlvDatum notiData = new TlvDatum(DispatchResponse.NOTIFICATION_DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0000000100000002"));
        TlvDatum notiTlv = new TlvDatum(DispatchResponse.NOTIFICATION_TAG,
                Bytes.concat(notiFormat.toBytes(), notiId.toBytes(), notiData.toBytes()));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                notiTlv.toBytes());
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);


        assertThat(dispatchResponse.notifications).hasSize(1);
        assertThat(dispatchResponse.notifications.get(0).notificationEventId)
                .isEqualTo(DispatchResponse.NOTIFICATION_EVENT_ID_ADF_SELECTED);
        assertThat(((DispatchResponse.AdfSelectedNotification)
                dispatchResponse.notifications.get(0)).adfOid)
                .isEqualTo(ObjectIdentifier.fromBytes(
                        DataTypeConversionUtil.hexStringToByteArray("0000000100000002")));

    }

    @Test
    public void validResponseWithSecureSessionEstablishedNotification() {
        TlvDatum notiFormat = new TlvDatum(DispatchResponse.NOTIFICATION_FORMAT_TAG,
                new byte[] {(byte) 0x00});
        TlvDatum notiId = new TlvDatum(DispatchResponse.NOTIFICATION_EVENT_ID_TAG,
                new byte[] { (byte) 0x01});
        TlvDatum notiTlv = new TlvDatum(DispatchResponse.NOTIFICATION_TAG,
                Bytes.concat(notiFormat.toBytes(), notiId.toBytes()));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                notiTlv.toBytes());
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(1);
        DispatchResponse.SecureChannelEstablishedNotification eNotification =
                (DispatchResponse.SecureChannelEstablishedNotification)
                        dispatchResponse.notifications.get(0);
        assertThat(eNotification.notificationEventId)
                .isEqualTo(DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED);
        assertThat(eNotification.defaultSessionId.isEmpty()).isTrue();
    }

    @Test
    public void validResponseWithSecureSessionEstablishedNotificationAndValidSessionId() {
        TlvDatum notiFormat = new TlvDatum(DispatchResponse.NOTIFICATION_FORMAT_TAG,
                new byte[] {(byte) 0x00});
        TlvDatum notiId = new TlvDatum(DispatchResponse.NOTIFICATION_EVENT_ID_TAG,
                new byte[] { (byte) 0x01});
        // sessionIdLen | sessionId
        TlvDatum notiData = new TlvDatum(DispatchResponse.NOTIFICATION_DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0101"));
        TlvDatum notiTlv = new TlvDatum(DispatchResponse.NOTIFICATION_TAG,
                Bytes.concat(notiFormat.toBytes(), notiId.toBytes(), notiData.toBytes()));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                notiTlv.toBytes());
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(1);
        DispatchResponse.SecureChannelEstablishedNotification eNotification =
                (DispatchResponse.SecureChannelEstablishedNotification)
                        dispatchResponse.notifications.get(0);
        assertThat(eNotification.notificationEventId)
                .isEqualTo(DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED);
        assertThat(eNotification.defaultSessionId.get()).isEqualTo(1);
    }

    @Test
    public void validResponseWithRdsAvailableNotification() {
        TlvDatum notiFormat = new TlvDatum(DispatchResponse.NOTIFICATION_FORMAT_TAG,
                new byte[] {(byte) 0x00});
        TlvDatum notiId = new TlvDatum(DispatchResponse.NOTIFICATION_EVENT_ID_TAG,
                new byte[] { (byte) 0x02});
        // sessionIdLen | sessionId | arbitrary_data_len | arbitrary data
        TlvDatum notiData = new TlvDatum(DispatchResponse.NOTIFICATION_DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("020102020A0B"));
        TlvDatum notiTlv = new TlvDatum(DispatchResponse.NOTIFICATION_TAG,
                Bytes.concat(notiFormat.toBytes(), notiId.toBytes(), notiData.toBytes()));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                notiTlv.toBytes());
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.notifications).hasSize(1);
        assertThat(dispatchResponse.notifications.get(0).notificationEventId)
                .isEqualTo(DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE);
        assertThat(((DispatchResponse.RdsAvailableNotification)
                dispatchResponse.notifications.get(0)).sessionId).isEqualTo(0x0102);
        assertThat(((DispatchResponse.RdsAvailableNotification)
                dispatchResponse.notifications.get(0)).arbitraryData.get())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0A0B"));
    }

    @Test
    public void wrongStatusWord() {
        ResponseApdu responseApdu =
                ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        assertThat(dispatchResponse.isSuccess()).isFalse();
        assertThat(dispatchResponse.notifications).hasSize(0);
    }
}
