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

package com.android.phone;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telephony.TelephonyManager;
import android.telephony.UssdResponse;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IIntegerConsumer;

import java.util.HashMap;

/**
 * Handling the call waiting USSD result.
 */
public class CallWaitingUssdResultReceiver extends ResultReceiver {
    private static final String LOG_TAG = "CwUssdResultReceiver";

    private IIntegerConsumer mCallback;
    private CarrierXmlParser mCarrierXmlParser;
    private CarrierXmlParser.SsEntry.SSAction mSsAction;

    CallWaitingUssdResultReceiver(Handler handler, IIntegerConsumer callback,
            CarrierXmlParser carrierXmlParser, CarrierXmlParser.SsEntry.SSAction action) {
        super(handler);
        mCallback = callback;
        mCarrierXmlParser = carrierXmlParser;
        mSsAction = action;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle ussdResponse) {
        log("USSD:" + resultCode);
        checkNotNull(ussdResponse, "ussdResponse cannot be null.");
        UssdResponse response = ussdResponse.getParcelable(
                TelephonyManager.USSD_RESPONSE);

        if (resultCode == TelephonyManager.USSD_RETURN_SUCCESS) {
            int callWaitingStatus = getStatusFromResponse(response);
            try {
                mCallback.accept(callWaitingStatus);
            } catch (RemoteException e) {
                log("Fail to notify getCallWaitingStatus due to " + e);
            }
        } else {
            try {
                mCallback.accept(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
            } catch (RemoteException e) {
                log("Fail to notify getCallWaitingStatus due to " + e);
            }
        }
    }

    private int getStatusFromResponse(UssdResponse response) {
        int callWaitingStatus = TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR;

        CarrierXmlParser.SsFeature callWaitingFeature = mCarrierXmlParser.getFeature(
                CarrierXmlParser.FEATURE_CALL_WAITING);
        if (callWaitingFeature == null) {
            return callWaitingStatus;
        }

        HashMap<String, String> analysisResult = callWaitingFeature
                .getResponseSet(mSsAction, response.getReturnMessage().toString());
        if (analysisResult.get(CarrierXmlParser.TAG_RESPONSE_STATUS_ERROR) != null) {
            return callWaitingStatus;
        }

        if (analysisResult != null && analysisResult.size() != 0) {
            String tmpStatusStr = analysisResult.get(
                    CarrierXmlParser.TAG_RESPONSE_STATUS);

            if (!TextUtils.isEmpty(tmpStatusStr)) {
                if (tmpStatusStr.equals(
                        CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_ACTIVATE)) {
                    callWaitingStatus =
                            TelephonyManager.CALL_WAITING_STATUS_ENABLED;
                } else if (tmpStatusStr.equals(
                        CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_DEACTIVATE)) {
                    callWaitingStatus =
                            TelephonyManager.CALL_WAITING_STATUS_DISABLED;
                }
            }
        }
        return callWaitingStatus;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
