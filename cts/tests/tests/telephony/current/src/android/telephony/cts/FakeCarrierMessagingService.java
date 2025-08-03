/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.telephony.cts;

import android.net.Uri;
import android.os.RemoteException;
import android.service.carrier.CarrierMessagingService;
import android.service.carrier.MessagePdu;

import java.util.List;

/**
 * The class that serves as a fake CarrierMessagingService. It allows
 * CarrierMessagingServiceWrapperTest to connect to and test against.
 */
public class FakeCarrierMessagingService extends CarrierMessagingService {
    public static int FAKE_MESSAGE_REF = 1;
    @Override
    public void onReceiveTextSms(MessagePdu pdu, String format, int destPort,
            int subId, ResultCallback<Integer> callback) {
        try {
            callback.onReceiveResult(CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT);
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onSendTextSms(String text, int subId, String destAddress,
            int sendSmsFlag, ResultCallback<SendSmsResult> callback) {
        try {
            callback.onReceiveResult(new SendSmsResult(SEND_STATUS_OK, FAKE_MESSAGE_REF));
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onSendDataSms(byte[] data, int subId, String destAddress,
            int destPort, int sendSmsFlag, ResultCallback<SendSmsResult> callback) {
        try {
            callback.onReceiveResult(new SendSmsResult(SEND_STATUS_OK, FAKE_MESSAGE_REF));
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onSendMultipartTextSms(List<String> parts, int subId,
            String destAddress, int sendSmsFlag,
            ResultCallback<SendMultipartSmsResult> callback) {
        try {
            callback.onReceiveResult(new SendMultipartSmsResult(
                    SEND_STATUS_OK, new int[] {FAKE_MESSAGE_REF}));
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onSendMms(Uri pduUri, int subId, Uri location,
            ResultCallback<SendMmsResult> callback) {
        try {
            callback.onReceiveResult(new SendMmsResult(
                    SEND_STATUS_OK, new byte[0]));
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onDownloadMms(Uri contentUri, int subId, Uri location,
            ResultCallback<Integer> callback) {
        try {
            callback.onReceiveResult(CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT);
        } catch (RemoteException ex) {
        }
    }
}
