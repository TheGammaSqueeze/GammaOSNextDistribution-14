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

import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.telephony.SipMessageParsingUtils;
import com.android.services.telephony.rcs.SipDialog;
import com.android.services.telephony.rcs.SipSessionTracker;
import com.android.services.telephony.rcs.SipTransportController;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tracks the state of the outgoing SIP message transport from the remote IMS application to the
 * ImsService. Used to validate outgoing SIP messages based off of this state.
 */
public class OutgoingTransportStateValidator implements SipMessageValidator {

    /**
     * The message transport is closed, meaning there can be no more outgoing messages
     */
    private static final int STATE_CLOSED = 0;

    /**
     * The message transport is restricted to only in-dialog outgoing traffic
     */
    private static final int STATE_RESTRICTED = 1;

    /**
     * The message transport is open and outgoing traffic is not restricted.
     */
    private static final int STATE_OPEN = 2;

    private static final ArrayMap<Integer, String> ENUM_TO_STRING_MAP  = new ArrayMap<>(3);
    static {
        ENUM_TO_STRING_MAP.append(STATE_CLOSED, "CLOSED");
        ENUM_TO_STRING_MAP.append(STATE_RESTRICTED, "RESTRICTED");
        ENUM_TO_STRING_MAP.append(STATE_OPEN, "OPEN");
    }

    private final SipSessionTracker mSipSessionTracker;
    private int mState = STATE_CLOSED;
    private int mReason = SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED;
    private Set<String> mAllowedTags = Collections.emptySet();
    private Set<String> mDeniedTags = Collections.emptySet();
    private Set<String> mRestrictedFeatureTags;

    public OutgoingTransportStateValidator(SipSessionTracker sessionTracker) {
        mSipSessionTracker = sessionTracker;
    }

    /**
     * The SIP message transport is open and will successfully validate both in and out of dialog
     * SIP messages.
     */
    public void open(Set<String> allowedFeatureTags, Set<String> deniedFeatureTags) {
        mState = STATE_OPEN;
        mReason = -1;
        // This is for validation, so try to reduce matching errors due to upper/lower case.
        mAllowedTags = allowedFeatureTags.stream().map(String::trim).map(String::toLowerCase)
                .collect(Collectors.toSet());
        mDeniedTags = deniedFeatureTags.stream().map(String::trim).map(String::toLowerCase)
                .collect(Collectors.toSet());
        mRestrictedFeatureTags = null;
    }

    /**
     * Restrict the starting of dialogs for specific feature tags, excluding requests associated
     * with ongoing sessions.
     * @param restrictedFeatureTags The feature tags that are deregistering or deregistered and can
     *                              not have new dialogs associated with them.
     */
    public void restrictFeatureTags(Set<String> restrictedFeatureTags) {
        // This is for validation, so try to reduce matching errors due to upper/lower case.
        mRestrictedFeatureTags = restrictedFeatureTags.stream().map(String::trim)
                .map(String::toLowerCase).collect(Collectors.toSet());
    }

    /**
     * The SIP message transport is restricted and only allows in-dialog outgoing messages.
     * @param reason The reason that will be returned to outgoing out-of-dialog SIP messages that
     *               are denied.
     */
    public void restrict(int reason) {
        mState = STATE_RESTRICTED;
        mReason = reason;
    }

    /**
     * The SIP message transport is closed for outgoing SIP messages.
     * @param reason The error reason sent in response to any outgoing SIP messages requests.
     */
    public void close(int reason) {
        mState = STATE_CLOSED;
        mReason = reason;
        mAllowedTags = Collections.emptySet();
    }

    @Override
    public ValidationResult validate(SipMessage message) {
        switch (mState) {
            case STATE_CLOSED:
                return new ValidationResult(mReason, "outgoing transport closed.");
            case STATE_RESTRICTED:
                return verifyRestrictedMessage(message);
            case STATE_OPEN:
                return verifyOpenMessage(message);
            default:
                Log.w(SipTransportController.LOG_TAG, "OutgoingTSV - warning, unexpected state");
                return ValidationResult.SUCCESS;
        }
    }

    public Set<String> getAllowedCallIds() {
        return Stream.concat(mSipSessionTracker.getEarlyDialogs().stream(),
                mSipSessionTracker.getConfirmedDialogs().stream()).map(SipDialog::getCallId)
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "Outgoing Transport State: " + ENUM_TO_STRING_MAP.getOrDefault(mState,
                String.valueOf(mState)) + ", reason: "
                + SipDelegateManager.MESSAGE_FAILURE_REASON_STRING_MAP.getOrDefault(mReason,
                String.valueOf(mReason)) + ", allowed tags: " + mAllowedTags + ", restricted tags: "
                + mRestrictedFeatureTags + ", denied tags: " + mDeniedTags;
    }

    private ValidationResult verifyOpenMessage(SipMessage m) {
        // No need to validate responses to requests.
        if (!SipMessageParsingUtils.isSipRequest(m.getStartLine())) {
            return ValidationResult.SUCCESS;
        }
        if (mRestrictedFeatureTags == null) {
            return new ValidationResult(SipDelegateManager.MESSAGE_FAILURE_REASON_NOT_REGISTERED,
                    "no reg state from vendor");
        }
        String[] segments = SipMessageParsingUtils.splitStartLineAndVerify(m.getStartLine());
        if (segments == null) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE,
                    "couldn't parse start line: " + m.getStartLine());
        }
        // Only need to validate requests that start dialogs.
        boolean startsDialog = Arrays.stream(SipSessionTracker.SIP_REQUEST_DIALOG_START_METHODS)
                .anyMatch(req -> req.equals(segments[0].trim().toLowerCase(Locale.ROOT)));
        // If part of an existing dialog, then no need to validate.
        boolean needsFeatureValidation = startsDialog && !getAllowedCallIds()
                .contains(m.getCallIdParameter());
        if (needsFeatureValidation) {
            return validateMessageFeatureTag(m);
        }
        return ValidationResult.SUCCESS;
    }

    /**
     * Compares the "Accept-Contact" header against the supported/denied feature tags and ensures
     * that there are no restricted or denied tags included.
     */
    private ValidationResult validateMessageFeatureTag(SipMessage m) {
        Set<String> featureTags = SipMessageParsingUtils.getAcceptContactFeatureTags(
                m.getHeaderSection());
        // Get rid of potential formatting issues first.
        featureTags = featureTags.stream().map(String::toLowerCase).map(String::trim)
                .collect(Collectors.toSet());
        long acceptedFeatureTagCount = featureTags.stream()
                .filter(f -> mAllowedTags.contains(f)).count();
        long deniedFeatureTagCount = featureTags.stream()
                .filter(f -> mDeniedTags.contains(f)).count();
        long restrictedFeatureTagCount = featureTags.stream()
                .filter(f -> mRestrictedFeatureTags.contains(f)).count();
        // we should not have any feature tags that are denied/restricted and there should be at
        // least one accepted tag
        if (deniedFeatureTagCount > 0) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                    "contains denied tags in Accept-Contact: " + featureTags);
        }
        if (restrictedFeatureTagCount > 0) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                    "contains restricted tags in Accept-Contact: " + featureTags);
        }

        if (acceptedFeatureTagCount == 0) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG,
                    "No Accept-Contact feature tags are in accepted feature tag list: "
                            + featureTags);
        }

        return ValidationResult.SUCCESS;
    }

    private ValidationResult verifyRestrictedMessage(SipMessage m) {
        // The validator is in the restricted state, so only in dialog requests and all responses
        // are allowed.
        if (!SipMessageParsingUtils.isSipRequest(m.getStartLine())) {
            return ValidationResult.SUCCESS;
        }
        String callId = m.getCallIdParameter();
        if (TextUtils.isEmpty(callId)) {
            return new ValidationResult(mReason, "empty call id");
        }
        Set<String> mAllowedCallIds = getAllowedCallIds();
        if (!mAllowedCallIds.contains(callId)) {
            return new ValidationResult(mReason, "call id " + callId + " is not associated with"
                    + " any active sessions");
        }
        return ValidationResult.SUCCESS;
    }
}
