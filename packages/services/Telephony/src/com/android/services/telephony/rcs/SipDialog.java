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
import android.text.TextUtils;

import com.android.internal.telephony.SipMessageParsingUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Track the state of a SIP Dialog.
 * <p>
 * SIP Dialogs follow the following initialization flow:
 * <pre>
 * (INVITE) ---> EARLY -(2XX)-> CONFIRMED -(BYE)-----> CLOSED
 *          ^     |   \                           ^
 *          |--(1XX)   -(3XX-7XX) ----------------|
 * </pre>
 * <p> A special note on forking INVITE requests:
 * During the EARLY phase, a 1XX or 2XX response can carry a To header tag param. This To header
 * tag param will be set once a INVITE reaches the remote and responds. If the proxy has multiple
 * endpoints available for the same contact, the INVITE may fork and multiple responses may be
 * received for the same INVITE request, each with a different To header tag parameter (but the
 * same call-id). This will generate another SIP dialog within the same SIP session.
 */
public class SipDialog {

    /**
     * The device has sent out a dialog starting event and is awaiting a confirmation.
     */
    public static final int STATE_EARLY = 0;

    /**
     * The device has received a 2XX response to the early dialog.
     */
    public static final int STATE_CONFIRMED = 1;

    /**
     * The device has received either a 3XX+ response to a pending dialog request or a BYE
     * request has been sent on this dialog.
     */
    public static final int STATE_CLOSED = 2;

    private final String mBranchId;
    private final String mCallId;
    private final String mFromTag;
    private final Set<String> mAcceptContactFeatureTags;
    private String mToTag;
    private int mState = STATE_EARLY;
    private Instant mLastInteraction;

    /**
     * @return A SipDialog instance representing the SIP request.
     */
    public static SipDialog fromSipMessage(SipMessage m) {
        if (!SipMessageParsingUtils.isSipRequest(m.getStartLine())) return null;
        String fromTag = SipMessageParsingUtils.getFromTag(m.getHeaderSection());
        Set<String> acceptContactTags = SipMessageParsingUtils.getAcceptContactFeatureTags(
                m.getHeaderSection());
        return new SipDialog(m.getViaBranchParameter(), m.getCallIdParameter(), fromTag,
                acceptContactTags);
    }

    /**
     * Track a new SIP dialog, which will be starting in {@link #STATE_EARLY}.
     *
     * @param branchId The via branch param of the INVITE request, which is used to match
     *                 responses.
     * @param callId   The callId of the SIP dialog.
     * @param fromTag  The from header's tag parameter.
     */
    private SipDialog(String branchId, String callId, String fromTag, Set<String> featureTags) {
        mBranchId = branchId;
        mCallId = callId;
        mFromTag = fromTag;
        mAcceptContactFeatureTags = featureTags;
        mLastInteraction = Instant.now();
    }

    /**
     * @return The call id associated with the SIP dialog.
     */
    public String getCallId() {
        return mCallId;
    }

    /**
     * @return the state of the SIP dialog, either {@link #STATE_EARLY},
     * {@link #STATE_CONFIRMED}, {@link #STATE_CLOSED}.
     */
    public int getState() {
        return mState;
    }

    /**
     * @return The to header's tag parameter if this dialog has gotten a response from the remote
     * party or {@code null} if it has not.
     */
    public String getToTag() {
        return mToTag;
    }

    /**
     * @return The feature tags contained in the "Accept-Contact" header.
     */
    public Set<String> getAcceptContactFeatureTags() {
        return mAcceptContactFeatureTags;
    }

    /**
     * @return A new instance with branch param, call-id value, and from tag param populated.
     */
    public SipDialog forkDialog() {
        return new SipDialog(mBranchId, mCallId, mFromTag, mAcceptContactFeatureTags);
    }

    /**
     * A early response has been received (101-199) and contains a to tag, which will create a
     * dialog.
     * @param toTag The to tag of the SIP response.
     */
    public void earlyResponse(String toTag) {
        if (TextUtils.isEmpty(toTag) || mState != STATE_EARLY) {
            // invalid state
            return;
        }
        mLastInteraction = Instant.now();
        // Only accept To tag if one has not been assigned yet.
        if (mToTag == null) {
            mToTag = toTag;
        }
    }

    /**
     * The early dialog has been confirmed and
     * @param toTag The To header's tag parameter.
     */
    public void confirm(String toTag) {
        if (mState != STATE_EARLY) {
            // Invalid state
            return;
        }
        mLastInteraction = Instant.now();
        mState = STATE_CONFIRMED;
        // Only accept a To tag if one has not been assigned yet.
        if (mToTag == null) {
            mToTag = toTag;
        }
    }

    /**
     * Close the SIP dialog
     */
    public void close() {
        mLastInteraction = Instant.now();
        mState = STATE_CLOSED;
    }

    /**
     * @return {@code true} if a SIP response's branch, call-id, and from tags match this dialog,
     * {@code false} if it does not. This may match multiple Dialogs in the case of SIP INVITE
     * forking.
     */
    public boolean isResponseAssociatedWithDialog(SipMessage m) {
        if (!mBranchId.equals(m.getViaBranchParameter())) return false;
        if (!mCallId.equals(m.getCallIdParameter())) return false;
        String fromTag = SipMessageParsingUtils.getFromTag(m.getHeaderSection());
        return mFromTag.equals(fromTag);
    }

    /**
     * @return {@code true} if the SIP request is part of the SIP Dialog, {@code false} if it is
     * not.
     */
    public boolean isRequestAssociatedWithDialog(SipMessage m) {
        if (!mCallId.equals(m.getCallIdParameter())) return false;
        String fromTag = SipMessageParsingUtils.getFromTag(m.getHeaderSection());
        String toTag = SipMessageParsingUtils.getToTag(m.getHeaderSection());
        // Requests can only be associated if both to and from tag of message are populated. The
        // dialog's to tag must also be non-null meaning we got a response from the remote.
        if (fromTag == null || toTag == null || mToTag == null) return false;
        // For outgoing requests, recorded from tag will match their from tag and for incoming
        // requests recorded from tag will match their to tag. Same with our recorded to tag.
        return (mFromTag.equals(fromTag) || mFromTag.equals(toTag))
                && (mToTag.equals(toTag) || mToTag.equals(fromTag));
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("SipDialog[");
        switch (mState) {
            case STATE_EARLY:
                b.append("early");
                break;
            case STATE_CONFIRMED:
                b.append("confirmed");
                break;
            case STATE_CLOSED:
                b.append("closed");
                break;
            default:
                b.append(mState);
        }
        b.append("] bId=");
        b.append(mBranchId);
        b.append(", cId=");
        b.append(mCallId);
        b.append(", f=");
        b.append(mFromTag);
        b.append(", t=");
        b.append(mToTag);
        b.append(", Last Interaction: ");
        b.append(mLastInteraction);
        return b.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SipDialog sipDialog = (SipDialog) o;
        // Does not include mState and last interaction time, as a dialog is only the same if
        // its branch, callId, and to/from tags are equal.
        return mBranchId.equals(sipDialog.mBranchId)
                && Objects.equals(mCallId, sipDialog.mCallId)
                && Objects.equals(mFromTag, sipDialog.mFromTag)
                && Objects.equals(mToTag, sipDialog.mToTag);
    }

    @Override
    public int hashCode() {
        // Does not include mState and last interaction time, as a dialog is only the same if
        // its branch, callId, and to/from tags are equal.
        return Objects.hash(mBranchId, mCallId, mFromTag, mToTag);
    }
}
