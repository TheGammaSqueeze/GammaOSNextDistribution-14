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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import android.telephony.ims.SipMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SipDialogTest {

    private static final String SIP_URI_ALICE = "sip:alice@client.example.com";
    private static final String BASE_CONTACT_URI_ALICE = "Alice <" + SIP_URI_ALICE + ">";
    private static final String SIP_URI_BOB = "sip:bob@client.example.com";
    private static final String BASE_CONTACT_URI_BOB = "Bob <" + SIP_URI_BOB + ">";

    @Test
    public void testCreateEarlyDialog() {
        String branchId = "testBranchId";
        String fromTag = "abcd";
        String callId = "testCallId";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);
        assertEquals(SipDialog.STATE_EARLY, dialog.getState());
        assertEquals(callId, dialog.getCallId());
        assertNull(dialog.getToTag());
        // receive an earlyResponse
        String toTag = "testToTag";
        dialog.earlyResponse(toTag);
        assertEquals(toTag, dialog.getToTag());
    }

    @Test
    public void testIsResponseAssociated() {
        String branchId = "testBranchId";
        String fromTag = "abcd";
        String callId = "testCallId";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);

        // A response with no to tag should match
        SipMessage inviteTrying = SipMessageUtils.generateSipResponse("100", "Trying",
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, branchId, callId, fromTag,
                null /*toTag*/);
        assertTrue(dialog.isResponseAssociatedWithDialog(inviteTrying));
        // A response with a different to tag should match
        inviteTrying = SipMessageUtils.generateSipResponse("100", "Trying",
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, branchId, callId, fromTag,
                "testToTag");
        assertTrue(dialog.isResponseAssociatedWithDialog(inviteTrying));
        // A response with a different from tag shouldn't match.
        String fromTag2 = "testFromTag2";
        inviteTrying = SipMessageUtils.generateSipResponse("100", "Trying",
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, branchId, callId, fromTag2,
                null /*toTag*/);
        assertFalse(dialog.isResponseAssociatedWithDialog(inviteTrying));
        // A response with a different branch ID shouldn't match.
        String branchId2 = "testBranchId2";
        inviteTrying = SipMessageUtils.generateSipResponse("100", "Trying",
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, branchId2, callId, fromTag,
                null /*toTag*/);
        assertFalse(dialog.isResponseAssociatedWithDialog(inviteTrying));
        // A response with a different call id shouldn't match.
        String callId2 = "testCallId2";
        inviteTrying = SipMessageUtils.generateSipResponse("100", "Trying",
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, branchId, callId2, fromTag,
                null /*toTag*/);
        assertFalse(dialog.isResponseAssociatedWithDialog(inviteTrying));
    }

    @Test
    public void testFork() {
        String branchId = "testBranchId";
        String fromTag = "abcd";
        String callId = "testCallId";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);
        assertEquals(SipDialog.STATE_EARLY, dialog.getState());
        assertEquals(callId, dialog.getCallId());
        // receive an earlyResponse
        dialog.earlyResponse("testToTag");
        assertEquals(SipDialog.STATE_EARLY, dialog.getState());
        // fork dialog
        SipDialog dialogFork = dialog.forkDialog();
        assertEquals(SipDialog.STATE_EARLY, dialogFork.getState());
        assertEquals(callId, dialogFork.getCallId());
        assertNull(dialogFork.getToTag());
    }

    @Test
    public void testConfirmDialog() {
        String branchId = "testBranchId";
        String fromTag = "abcd";
        String callId = "testCallId";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);
        assertEquals(SipDialog.STATE_EARLY, dialog.getState());
        assertEquals(callId, dialog.getCallId());
        // receive a confirm response
        dialog.confirm("testToTag");
        assertEquals(SipDialog.STATE_CONFIRMED, dialog.getState());
        assertEquals(callId, dialog.getCallId());
    }

    @Test
    public void testIsRequestAssociated() {
        String branchId = "testBranchId";
        String fromTag = "testFromTag";
        String callId = "testCallId";
        String toTag = "testToTag";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);
        dialog.earlyResponse(toTag);

        SipMessage cancelRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.CANCEL_SIP_METHOD, BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB,
                SIP_URI_BOB, branchId, callId, fromTag, toTag);
        assertTrue(dialog.isRequestAssociatedWithDialog(cancelRequest));
        // cancel request with no toTag should fail
        cancelRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD, BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB,
                SIP_URI_BOB, branchId, callId, fromTag, null /*toTag*/);
        assertFalse(dialog.isRequestAssociatedWithDialog(cancelRequest));
        // cancel request to a different dialog in the same session should fail
        String toTag2 = "testToTag2";
        cancelRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD, BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB,
                SIP_URI_BOB, branchId, callId, fromTag, toTag2);
        assertFalse(dialog.isRequestAssociatedWithDialog(cancelRequest));
        // cancel request to a different session should fail (even with the same from/to)
        String callId2 = "testCallId2";
        cancelRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD, BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB,
                SIP_URI_BOB, branchId, callId2, fromTag, toTag);
        assertFalse(dialog.isRequestAssociatedWithDialog(cancelRequest));
        // Same call id but different from and to (although not really possible) should fail.
        String fromTag3 = "testFromTag3";
        String toTag3 = "testToTag3";
        cancelRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD, BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB,
                SIP_URI_BOB, branchId, callId, fromTag3, toTag3);
        assertFalse(dialog.isRequestAssociatedWithDialog(cancelRequest));
    }

    @Test
    public void testCloseDialog() {
        String branchId = "testBranchId";
        String fromTag = "abcd";
        String callId = "testCallId";
        SipMessage inviteRequest = SipMessageUtils.generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                BASE_CONTACT_URI_ALICE, BASE_CONTACT_URI_BOB, SIP_URI_BOB, branchId, callId,
                fromTag, null /*toTag*/);
        SipDialog dialog = SipDialog.fromSipMessage(inviteRequest);
        assertNotNull(dialog);
        assertEquals(SipDialog.STATE_EARLY, dialog.getState());
        assertEquals(callId, dialog.getCallId());

        // receive a confirm response
        dialog.confirm("testToTag");
        assertEquals(SipDialog.STATE_CONFIRMED, dialog.getState());

        dialog.close();
        assertEquals(SipDialog.STATE_CLOSED, dialog.getState());
    }
}
