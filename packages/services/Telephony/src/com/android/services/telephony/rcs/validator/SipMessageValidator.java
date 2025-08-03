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

import android.telephony.ims.SipMessage;

/**
 * Validates a SipMessage and returns the result via an instance of {@link ValidationResult}.
 */
public interface SipMessageValidator {
    /**
     * Validate that the SipMessage is allowed to be sent to the remote.
     * @param message The SipMessage being validated.
     * @return A {@link ValidationResult} that represents whether or not the message was validated.
     * If not validated, it also returns a reason why the SIP message was not validated.
     */
    ValidationResult validate(SipMessage message);

    /**
     * Compose a SipMessageValidator out of two validators, this validator running before the next
     * validator.
     * @param next The next validator that will be run if this validator validates the message
     *             successfully.
     * @return A new SipMessageValidator composed of this validator and the next one.
     */
    default SipMessageValidator andThen(SipMessageValidator next) {
        return (SipMessage m) -> {
            ValidationResult result = validate(m);
            if (!result.isValidated) return result;
            return next.validate(m);
        };
    }
}
