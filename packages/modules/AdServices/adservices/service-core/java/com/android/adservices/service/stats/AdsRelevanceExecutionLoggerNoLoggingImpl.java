/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.stats;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;

/** Replacement for {@link AdsRelevanceExecutionLoggerImpl} if
 * Ads Relevance metrics is disabled. */
public class AdsRelevanceExecutionLoggerNoLoggingImpl
        implements AdsRelevanceExecutionLogger {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private String mApiName;

    public AdsRelevanceExecutionLoggerNoLoggingImpl(@NonNull String apiName) {
        this.mApiName = apiName;
    }

    /**
     * Identity function that do nothing.
     *
     * @param resultCode Api usage status code.
     */
    @Override
    public void endAdsRelevanceApi(int resultCode) {
        sLogger.v("Disabled end of Ads Relevance API logging process for API: " + mApiName);
    }
}
