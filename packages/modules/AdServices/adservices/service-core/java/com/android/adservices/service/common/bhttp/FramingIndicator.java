/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.common.bhttp;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The start of each binary message is a framing indicator that is a single integer that describes
 * the structure of the subsequent sections. The framing indicator can take just four values:
 *
 * <ul>
 *   <li>A value of 0 describes a request of known length.
 *   <li>A value of 1 describes a response of known length.
 * </ul>
 *
 * <p>We only support known length encryption for now. So, only 0 and 1 are included here.
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-framing-indicator">Binary
 *     HTTP Framing Indicator</a>
 */
@IntDef(
        prefix = "FRAMING_INDICATOR_",
        value = {
            FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH,
            FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH,
        })
@Retention(RetentionPolicy.SOURCE)
public @interface FramingIndicator {
    byte FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH = 0;
    byte FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH = 1;
}
