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

/**
 * Validates that the SipMessage is not malformed before sending the message to the vendor
 * ImsService by ensuring:
 * <ul>The SipMessage is a valid SIP request or SIP response.</ul>
 */
public class MalformedSipMessageValidator implements SipMessageValidator {

    @Override
    public ValidationResult validate(SipMessage message) {
        // Verify the request and response start lines are valid.
        if (!SipMessageParsingUtils.isSipRequest(message.getStartLine())
                && !SipMessageParsingUtils.isSipResponse(message.getStartLine())) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE,
                    "malformed start line: " + message.getStartLine());
        }
        return ValidationResult.SUCCESS;
    }
}
