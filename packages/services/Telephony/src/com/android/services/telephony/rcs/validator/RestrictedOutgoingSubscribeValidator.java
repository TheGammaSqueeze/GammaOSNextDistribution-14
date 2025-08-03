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
import android.util.Pair;

import com.android.internal.telephony.SipMessageParsingUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Ensure that if there is an outgoing SUBSCRIBE request, that it does not contain the "Event"
 * header "presence"
 */
public class RestrictedOutgoingSubscribeValidator implements SipMessageValidator {

    private static final String SUBSCRIBE_REQUEST = "subscribe";
    private static final String SUBSCRIBE_EVENT_HEADER = "event";
    private static final String[] RESTRICTED_EVENTS = new String[]{ "presence" };


    @Override
    public ValidationResult validate(SipMessage message) {
        if (!SipMessageParsingUtils.isSipRequest(message.getStartLine())) {
            return ValidationResult.SUCCESS;
        }
        String[] requestSegments = SipMessageParsingUtils.splitStartLineAndVerify(
                message.getStartLine());
        if (requestSegments == null) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE,
                    "malformed start line: " + message.getStartLine());
        }
        // Request-Line  =  Method SP Request-URI SP SIP-Version CRLF, verify Method
        if (!requestSegments[0].equalsIgnoreCase(SUBSCRIBE_REQUEST)) {
            return ValidationResult.SUCCESS;
        }

        List<Pair<String, String>> eventHeaders = SipMessageParsingUtils.parseHeaders(
                message.getHeaderSection(), true /*stopAtFirstMatch*/, SUBSCRIBE_EVENT_HEADER);
        if (eventHeaders.size() == 0) {
            return ValidationResult.SUCCESS;
        }
        boolean isRestricted = eventHeaders.stream().map(e -> e.second)
                .anyMatch(e -> Arrays.asList(RESTRICTED_EVENTS).contains(e.trim().toLowerCase(
                        Locale.ROOT)));

        return isRestricted ? new ValidationResult(
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_HEADER_FIELDS,
                "matched a restricted header field: " + eventHeaders.stream().map(e -> e.second)
                        .collect(Collectors.toSet())) :
                ValidationResult.SUCCESS;
    }
}
