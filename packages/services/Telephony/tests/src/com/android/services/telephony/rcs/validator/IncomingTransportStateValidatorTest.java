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
public class IncomingTransportStateValidatorTest {
    private static final SipMessage TEST_MESSAGE = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142",
            new byte[0]);

    @Test
    public void testVerifyMessageAndUpdateState() {
        IncomingTransportStateValidator validator = new IncomingTransportStateValidator();
        ValidationResult result = validator.validate(TEST_MESSAGE);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);

        validator.open();
        result = validator.validate(TEST_MESSAGE);
        assertTrue(result.isValidated);

        validator.close(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        result = validator.validate(TEST_MESSAGE);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);
    }
}
