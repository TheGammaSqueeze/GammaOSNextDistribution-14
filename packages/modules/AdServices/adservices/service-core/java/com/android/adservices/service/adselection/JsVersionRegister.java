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

package com.android.adservices.service.adselection;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Register JS version and signature here to keep a copy of signature history. */
public class JsVersionRegister {

    /**
     * Version 3 signature: {@code runBidding(customAudience, auction_signals, per_buyer_signals,
     * trusted_bidding_signals, contextual_signals)}
     *
     * <p>return type is: {@code {bid, ad, render}}.
     */
    public static final long BUYER_BIDDING_LOGIC_VERSION_VERSION_3 = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "BUYER_BIDDING_LOGIC_VERSION_",
            value = {
                (int) BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
            })
    @interface BuyerBiddingLogicVersion {}
}
