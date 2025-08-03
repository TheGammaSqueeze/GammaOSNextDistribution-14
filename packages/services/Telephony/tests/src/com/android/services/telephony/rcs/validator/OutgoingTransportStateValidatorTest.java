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

import static org.mockito.Mockito.doReturn;

import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.services.telephony.rcs.SipDialog;
import com.android.services.telephony.rcs.SipSessionTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class OutgoingTransportStateValidatorTest  extends TelephonyTestBase {

    @Mock
    private SipSessionTracker mMockSessionTracker;
    private final Random mRandom = new Random();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testVerifyMessageInOpenCloseState() {
        SipMessage testMessage = generateSipRequestForCallId("callId1");
        OutgoingTransportStateValidator validator =
                new OutgoingTransportStateValidator(mMockSessionTracker);
        ValidationResult result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);

        validator.open(Collections.singleton("+tag"), Collections.emptySet());
        validator.restrictFeatureTags(Collections.emptySet());
        result = validator.validate(testMessage);
        assertTrue(result.isValidated);

        validator.close(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);
    }

    @Test
    public void testVerifyMessageRestricted() {
        String callId1 = "callId1";
        String callId2 = "callId2";
        String callId3 = "callId3";
        SipMessage testInDialogEarlyMessage = generateSipRequestForCallId(callId1);
        SipMessage testInDialogConfirmedMessage = generateSipRequestForCallId(callId2);
        SipMessage testOutOfDialogInvite = generateSipRequestForCallId(callId3);
        SipMessage testStatelessRequest = generateMessageRequest();
        ArraySet<SipDialog> inDialogEarlyCallIds = new ArraySet<>(1);
        inDialogEarlyCallIds.add(SipDialog.fromSipMessage(testInDialogEarlyMessage));
        ArraySet<String> inDialogConfirmedCallIds = new ArraySet<>();
        inDialogEarlyCallIds.add(SipDialog.fromSipMessage(testInDialogConfirmedMessage));
        // For the sake of testing, add the same call id to early and confirmed dialogs, since we
        // will accept requests for both right now.
        doReturn(inDialogEarlyCallIds).when(mMockSessionTracker).getEarlyDialogs();
        doReturn(inDialogConfirmedCallIds).when(mMockSessionTracker).getConfirmedDialogs();
        OutgoingTransportStateValidator validator =
                new OutgoingTransportStateValidator(mMockSessionTracker);
        validator.restrict(
                SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION);

        // ensure a response to a pending request is not restricted, even if it is not for a tracked
        // call ID.
        ValidationResult result = validator.validate(generate200OkResponse("callId4"));
        assertTrue(result.isValidated);

        // confirm in dialog messages are not restricted
        result = validator.validate(testInDialogEarlyMessage);
        assertTrue(result.isValidated);
        result = validator.validate(testInDialogConfirmedMessage);
        assertTrue(result.isValidated);

        // confirm out-of-dialog requests are restricted.
        result = validator.validate(testOutOfDialogInvite);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                result.restrictedReason);
        result = validator.validate(testStatelessRequest);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                result.restrictedReason);
    }

    @Test
    public void testDeniedFeatureTag() {
        SipMessage testMessage = generateSipRequestForCallId("callId1");
        OutgoingTransportStateValidator validator =
                new OutgoingTransportStateValidator(mMockSessionTracker);
        ValidationResult result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);

        // Assert that invites associated with denied tags are denied.
        validator.open(Collections.emptySet(), Collections.singleton("+tag"));
        validator.restrictFeatureTags(Collections.emptySet());
        result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                result.restrictedReason);
    }

    @Test
    public void testRestrictedFeatureTag() {
        SipMessage testMessage = generateSipRequestForCallId("callId1");
        OutgoingTransportStateValidator validator =
                new OutgoingTransportStateValidator(mMockSessionTracker);
        ValidationResult result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);

        validator.open(Collections.singleton("+tag"), Collections.emptySet());
        // Ensure that when supported tags are restricted, the message is not validated.
        validator.restrictFeatureTags(Collections.singleton("+tag"));
        result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                result.restrictedReason);
    }

    @Test
    public void testNoSupportedFeatureTag() {
        SipMessage testMessage = generateSipRequestForCallId("callId1");
        OutgoingTransportStateValidator validator =
                new OutgoingTransportStateValidator(mMockSessionTracker);
        ValidationResult result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                result.restrictedReason);

        // Assert if a message doesn't have any related supported tags, it should be denied
        validator.open(Collections.emptySet(), Collections.emptySet());
        validator.restrictFeatureTags(Collections.emptySet());
        result = validator.validate(testMessage);
        assertFalse(result.isValidated);
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                result.restrictedReason);

        // responses and non-dialog starting messages do not matter
        result = validator.validate(generate200OkResponse("callId2"));
        assertTrue(result.isValidated);
        result = validator.validate(generateMessageRequest());
        assertTrue(result.isValidated);
    }

    /**
     * @return A INVITE with the call ID specified. Note: this request is not technically valid, but
     * only contains the relevant headers for testing.
     */
    private SipMessage generateSipRequestForCallId(String callId) {
        return new SipMessage(
                "INVITE sip:b@client.example.com SIP/2.0",
                "Via: SIP/2.0/UDP client.example.com;branch=z9hG4bK776asdhds\n"
                        + "To: B <sip:b@example.com>\n"
                        + "From: A <sip:a@example.com>;tag=1928301774\n"
                        + "Accept-Contact: *;+tag\n"
                        + "Call-ID: " + callId,
                new byte[0]);
    }

    /**
     * @return A MESSAGE request. Note: this request is not technically valid, but only contains the
     * relevant headers for testing.
     */
    private SipMessage generateMessageRequest() {
        return new SipMessage(
                "MESSAGE sip:b@client.example.com SIP/2.0",
                "Via: SIP/2.0/UDP client.example.com;branch=z9hG4bK776asdhds\n"
                        + "To: B <sip:b@example.com>\n"
                        + "From: A <sip:a@example.com>;tag=1928301774\n",
                new byte[0]);
    }

    /**
     * @return A 200 OK associated with the supplied call ID.
     */
    private SipMessage generate200OkResponse(String callId) {
        return new SipMessage(
                "SIP/2.0 200 OK",
                "Via: SIP/2.0/UDP client.example.com;branch=z9hG4bK776asdhds\n"
                        + "To: B <sip:b@example.com>\n"
                        + "From: A <sip:a@example.com>;tag=1928301774\n"
                        + "Call-ID: " + callId,
                new byte[0]);
    }
}
