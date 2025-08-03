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

import com.android.internal.telephony.SipMessageParsingUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Validate that any outgoing SIP request message does not contain methods that are only generated
 * internally by the ImsService implementation.
 */
public class RestrictedOutgoingSipRequestValidator implements SipMessageValidator {

    /**
     * These SIP requests are always handled by the ImsService and are restricted to being
     * generated internally. Messages with these request methods should fail validation.
     */
    private static final String[] IMS_SERVICE_HANDLED_REQUEST_METHODS = new String[]{
            "register", "options", "publish"};

    @Override
    public ValidationResult validate(SipMessage message) {
        String startLine = message.getStartLine();
        if (SipMessageParsingUtils.isSipRequest(startLine)) {
            String[] segments = SipMessageParsingUtils.splitStartLineAndVerify(startLine);
            if (segments == null) {
                return new ValidationResult(
                        SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE,
                        "malformed start line: " + message.getStartLine());
            }
            if (Arrays.stream(IMS_SERVICE_HANDLED_REQUEST_METHODS).anyMatch(
                    s -> segments[0].toLowerCase(Locale.ROOT).contains(s))) {
                return new ValidationResult(
                        SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE,
                        "restricted method: " + segments[0]);
            }
        }
        return ValidationResult.SUCCESS;
    }
}
