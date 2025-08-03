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

package com.android.services.telephony.rcs;

import android.telephony.ims.SipMessage;

public class SipMessageUtils {

    public static final String INVITE_SIP_METHOD = "INVITE";
    public static final String CANCEL_SIP_METHOD = "CANCEL";
    public static final String BYE_SIP_METHOD = "BYE";
    public static final String ACK_SIP_METHOD = "ACK";
    public static final String BASE_ADDRESS = "client.example.com";

    private static final String PARAM_SEPARATOR = ";";
    private static final String PARAM_KEY_VALUE_SEPARATOR = "=";
    private static final String BASE_VIA_HEADER_VALUE = "SIP/2.0/TCP " + BASE_ADDRESS + ":5060";

    /**
     * @return A new SIP request from the given parameters.
     */
    public static SipMessage generateSipRequest(String requestMethod, String fromContact,
            String toContact, String toUri, String branchId, String callId, String fromTag,
            String toTag) {
        String header = "Via: " + addParamToHeader(BASE_VIA_HEADER_VALUE, "branch", branchId);
        header += "\n";
        header += "From: " + addParamToHeader(fromContact, "tag", fromTag);
        header += "\n";
        // To tag is optional
        header += "To: " + ((toTag != null)
                ? addParamToHeader(toContact, "tag", toTag) : toContact);
        header += "\n";
        header += "Call-ID: " + callId;
        return new SipMessage(requestMethod + " " + toUri + " SIP/2.0", header, new byte[0]);
    }

    /**
     * @return Generates a SIP response.
     */
    public static SipMessage generateSipResponse(String statusCode, String statusString,
            String fromContact, String toContact, String branchId, String callId, String fromTag,
            String toTag) {
        String header = "Via: " + addParamToHeader(BASE_VIA_HEADER_VALUE, "branch", branchId);
        header += "\n";
        header += "From: " + addParamToHeader(fromContact, "tag", fromTag);
        header += "\n";
        // To tag is optional
        header += "To: " + ((toTag != null)
                ? addParamToHeader(toContact, "tag", toTag) : toContact);
        header += "\n";
        header += "Call-ID: " + callId;
        return new SipMessage("SIP/2.0 " + statusCode + " " + statusString, header,
                new byte[0]);
    }

    private static String addParamToHeader(String headerValue, String paramKey, String paramValue) {
        headerValue += PARAM_SEPARATOR + paramKey.trim() + PARAM_KEY_VALUE_SEPARATOR
                + paramValue.trim();
        return headerValue;
    }
}
