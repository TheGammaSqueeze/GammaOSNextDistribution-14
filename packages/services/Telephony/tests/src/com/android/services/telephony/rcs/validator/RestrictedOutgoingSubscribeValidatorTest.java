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

package com.android.services.telephony.rcs.validator;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RestrictedOutgoingSubscribeValidatorTest {

    @Test
    public void testValidUnrelatedRequest() {
        SipMessage msg = new SipMessage(
                "INVITE sip:bob@biloxi.com SIP/2.0",
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
        ValidationResult result = new MalformedSipMessageValidator().validate(msg);
        assertTrue(result.isValidated);
    }

    @Test
    public void testValidUnrelatedResponse() {
        SipMessage msg = new SipMessage(
                "SIP/2.0 200 OK",
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
        ValidationResult result = new MalformedSipMessageValidator().validate(msg);
        assertTrue(result.isValidated);
    }

    @Test
    public void testValidSubscribeRequest() {
        SipMessage msg = new SipMessage(
                "SUBSCRIBE sip:joe@example.com SIP/2.0",
                "Via: SIP/2.0/UDP app.example.com;branch=z9hG4bKnashds7\n"
                        + "From: sip:app.example.com;tag=123aa9\n"
                        + "To: sip:joe@example.com\n"
                        + "Call-ID: 9987@app.example.com\n"
                        + "CSeq: 9887 SUBSCRIBE\n"
                        + "Contact: sip:app.example.com\n"
                        + "Event:    conference \n"
                        + "Max-Forwards: 70\n",
                new byte[0]);
        ValidationResult result = new RestrictedOutgoingSubscribeValidator().validate(msg);
        assertTrue(result.isValidated);
    }

    @Test
    public void testInvalidSubscribeRequest() {
        SipMessage msg = new SipMessage(
                "SUBSCRIBE sip:joe@example.com SIP/2.0",
                "Via: SIP/2.0/UDP app.example.com;branch=z9hG4bKnashds7\n"
                        + "From: sip:app.example.com;tag=123aa9\n"
                        + "To: sip:joe@example.com\n"
                        + "Call-ID: 9987@app.example.com\n"
                        + "CSeq: 9887 SUBSCRIBE\n"
                        + "Contact: sip:app.example.com\n"
                        + "Event:  presence  \n"
                        + "Max-Forwards: 70\n",
                new byte[0]);
        ValidationResult result = new RestrictedOutgoingSubscribeValidator().validate(msg);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_HEADER_FIELDS,
                result.restrictedReason);

    }
}
