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

package com.android.adservices.service.common;

import androidx.annotation.NonNull;

import com.android.adservices.service.Flags;

import com.google.common.annotations.VisibleForTesting;

import java.util.Locale;

/** Validate Ad Render Id. */
public interface AdRenderIdValidator extends Validator<String> {
    @VisibleForTesting
    String AD_RENDER_ID_TOO_LONG = "Ad Render Id exceeds max length %d, length is %d.";

    AdRenderIdValidator AD_RENDER_ID_VALIDATOR_NO_OP = (object, violations) -> {};

    /**
     * @return an instance of {@code AdRenderIdValidator} reading the configuration from {@code
     *     flags}.
     */
    static AdRenderIdValidator createInstance(Flags flags) {
        boolean adRenderIdEnabled = flags.getFledgeAuctionServerAdRenderIdEnabled();
        if (!adRenderIdEnabled) {
            return AD_RENDER_ID_VALIDATOR_NO_OP;
        } else {
            final long maxLength = flags.getFledgeAuctionServerAdRenderIdMaxLength();
            return createEnabledInstance(maxLength);
        }
    }

    /**
     * @return an instance of {@code AdRenderIdValidator} that will enforce the given {@code
     *     maxLength}
     */
    @NonNull
    static AdRenderIdValidator createEnabledInstance(long maxLength) {
        return (object, violations) -> {
            if (object != null && object.getBytes().length > maxLength) {
                violations.add(
                        String.format(
                                Locale.ENGLISH,
                                AD_RENDER_ID_TOO_LONG,
                                maxLength,
                                object.getBytes().length));
            }
        };
    }
}
