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

/**
 * Communicates the result of validating whether a SIP message should be sent to a remote based on
 * the contents of the SIP message as well as if the transport is in an appropriate state for the
 * intended recipient of the message.
 */
public class ValidationResult {
    public static final ValidationResult SUCCESS = new ValidationResult();

    /**
     * If {@code true}, the requested SIP message has been validated and may be sent to the remote.
     * If {@code false}, the SIP message has failed validation and should not be sent to the
     * remote. The {@link #restrictedReason} field will contain the reason for the validation
     * failure.
     */
    public final boolean isValidated;

    /**
     * The reason associated with why the SIP message was not verified and generated a
     * {@code false} result for {@link #isValidated}.
     */
    public final int restrictedReason;

    /**
     * The human readable reason for why the validation failed for logging.
     */
    public final String logReason;

    /**
     * Communicates a validated result of success. Use {@link #SUCCESS} instead.
     */
    private ValidationResult() {
        isValidated = true;
        restrictedReason = SipDelegateManager.MESSAGE_FAILURE_REASON_UNKNOWN;
        logReason = "";
    }

    /**
     * The result of validating that the SIP Message should be sent.
     *
     * @param reason The reason associated with why the SIP message was not validated and
     *               generated a {@code false} result for {@link #isValidated}.
     */
    public ValidationResult(@SipDelegateManager.MessageFailureReason int reason, String log) {
        isValidated = false;
        restrictedReason = reason;
        logReason = log;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("ValidationResult{");
        b.append("validated=");
        b.append(isValidated);
        if (!isValidated) {
            b.append(", restrictedReason=");
            b.append(restrictedReason);
            b.append(", logReason=");
            b.append(logReason);
        }
        b.append('}');
        return b.toString();
    }
}
