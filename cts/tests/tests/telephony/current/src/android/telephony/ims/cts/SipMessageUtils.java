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

package android.telephony.ims.cts;

import android.telephony.ims.SipMessage;

import java.util.Set;


public class SipMessageUtils {

    public static final String INVITE_SIP_METHOD = "INVITE";
    public static final String CANCEL_SIP_METHOD = "CANCEL";
    public static final String BYE_SIP_METHOD = "BYE";
    public static final String ACK_SIP_METHOD = "ACK";
    public static final String MESSAGE_SIP_METHOD = "MESSAGE";
    public static final String BASE_ADDRESS = "client.example.com";

    private static final String PARAM_SEPARATOR = ";";
    private static final String PARAM_KEY_VALUE_SEPARATOR = "=";
    private static final String BASE_VIA_HEADER_VALUE = "SIP/2.0/TCP " + BASE_ADDRESS + ":5060";

    public static final SipMessage TEST_SIP_MESSAGE = generateSipRequest(MESSAGE_SIP_METHOD,
            new SipDialogAttributes());

    // Example from RFC 3665
    public static final SipMessage TEST_INVALID_SIP_REGISTER = new SipMessage(
            "REGISTER sips:ss2.biloxi.example.com SIP/2.0",
            "Via: SIP/2.0/TLS client.biloxi.example.com:5061;branch=z9hG4bKnashds7\n"
                    + "Max-Forwards: 70\n"
                    + "From: Bob <sips:bob@biloxi.example.com>;tag=a73kszlfl\n"
                    + "To: Bob <sips:bob@biloxi.example.com>\n"
                    + "Call-ID: 1j9FpLxk3uxtm8tn@biloxi.example.com\n"
                    + "CSeq: 1 REGISTER\n"
                    + "Contact: <sips:bob@client.biloxi.example.com>\n"
                    + "Content-Length: 0",
            new byte[0]);

    //Example from RFC3903, but does not include PIDF document. PUBLISH is not allowed.
    public static final SipMessage TEST_INVALID_SIP_PUBLISH = new SipMessage(
            "PUBLISH sip:presentity@example.com SIP/2.0",
            "Via: SIP/2.0/UDP pua.example.com;branch=z9hG4bKcdad2\n"
                    + "To: <sip:presentity@example.com>\n"
                    + "From: <sip:presentity@example.com>;tag=54321mm\n"
                    + "Call-ID: 5566778@pua.example.com\n"
                    + "CSeq: 1 PUBLISH\n"
                    + "Max-Forwards: 70\n"
                    + "Expires: 3600\n"
                    + "Event: presence\n"
                    + "Content-Type: application/pidf+xml",
            new byte[0]);

    //Example from RFC3856 - SUBSCRIBE with event presence is not allowed
    public static final SipMessage TEST_INVALID_SIP_SUBSCRIBE_PRESENCE = new SipMessage(
            "SUBSCRIBE sip:resource@example.com SIP/2.0",
            "Via: SIP/2.0/TCP watcherhost.example.com;branch=z9hG4bKnashds7\n"
                    + "To: <sip:resource@example.com>\n"
                    + "From: <sip:user@example.com>;tag=xfg9\n"
                    + "Call-ID: 2010@watcherhost.example.com\n"
                    + "CSeq: 17766 SUBSCRIBE\n"
                    + "Max-Forwards: 70\n"
                    + "Event: presence\n"
                    + "Accept: application/pidf+xml\n"
                    + "Contact: <sip:user@watcherhost.example.com>\n"
                    + "Expires: 600\n"
                    + "Content-Length: 0",
            new byte[0]);

    //Example from RFC3261, OPTIONS not allowed from app
    public static final SipMessage TEST_INVALID_SIP_OPTIONS = new SipMessage(
            "OPTIONS sip:carol@chicago.com SIP/2.0",
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKhjhs8ass877\n"
                    + "Max-Forwards: 70\n"
                    + "To: <sip:carol@chicago.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710\n"
                    + "CSeq: 63104 OPTIONS\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Accept: application/sdp\n"
                    + "Content-Length: 0",
            new byte[0]);

    // Sample message from RFC 3261, malformed request start line
    public static final SipMessage TEST_SIP_MESSAGE_INVALID_REQUEST = new SipMessage(
            "INVITE sip:bob@biloxi.comSIP/2.0",
            // Typical Via
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK.TeSt\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142",
            new byte[0]);

    // Sample message from RFC 3261, malformed response start line
    public static final SipMessage TEST_SIP_MESSAGE_INVALID_RESPONSE = new SipMessage(
            "SIP/2.0 200OK",
            "Via: SIP/2.0/TCP terminal.vancouver.example.com;"
                    + "branch=z9hG4bKwYb6QREiCL\n"
                    + "To: <sip:adam-buddies@pres.vancouver.example.com>;tag=zpNctbZq\n"
                    + "From: <sip:adam@vancouver.example.com>;tag=ie4hbb8t\n"
                    + "Call-ID: cdB34qLToC@terminal.vancouver.example.com\n"
                    + "CSeq: 322723822 SUBSCRIBE\n"
                    + "Contact: <sip:pres.vancouver.example.com>\n"
                    + "Expires: 7200\n"
                    + "Require: eventlist\n"
                    + "Content-Length: 0",
            new byte[0]);

    /**
     * @return A new SIP request from the given parameters.
     */
    public static SipMessage generateSipRequest(String requestMethod, String fromContact,
            String toContact, String toUri, String branchId, String callId, String fromTag,
            String toTag, Set<String> acceptContactTags) {
        String header = "Via: " + addParamToHeader(BASE_VIA_HEADER_VALUE, "branch", branchId);
        header += "\n";
        header += "From: " + addParamToHeader(fromContact, "tag", fromTag);
        header += "\n";
        // To tag is optional
        header += "To: " + ((toTag != null)
                ? addParamToHeader(toContact, "tag", toTag) : toContact);
        header += "\n";
        header += "Call-ID: " + callId;
        if (!acceptContactTags.isEmpty()) {
            header += "\n";
            header += "Accept-Contact: *";
            for (String tag : acceptContactTags) {
                header += ";" + tag;
            }
        }
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

    public static SipMessage generateSipRequest(String requestMethod, SipDialogAttributes attr) {
        return generateSipRequest(requestMethod, attr.fromHeader, attr.toHeader,
                attr.toUri, attr.branchId, attr.callId, attr.fromTag, attr.getToTag(),
                attr.getAcceptContactTags());
    }

    public static SipMessage generateSipResponse(String statusCode, String statusString,
            SipDialogAttributes attr) {
        return generateSipResponse(statusCode, statusString, attr.fromHeader,
                attr.toHeader, attr.branchId, attr.callId, attr.fromTag, attr.getToTag());
    }

    private static String addParamToHeader(String headerValue, String paramKey, String paramValue) {
        headerValue += PARAM_SEPARATOR + paramKey.trim() + PARAM_KEY_VALUE_SEPARATOR
                + paramValue.trim();
        return headerValue;
    }
}
