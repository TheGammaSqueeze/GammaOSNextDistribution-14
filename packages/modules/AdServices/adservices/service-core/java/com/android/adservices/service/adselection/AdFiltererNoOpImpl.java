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

import android.adservices.adselection.SignedContextualAds;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.List;

/** Replacement for {@link AdFiltererImpl} if filtering is turned off. */
public final class AdFiltererNoOpImpl implements AdFilterer {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    /**
     * Identity function that returns its input.
     *
     * @param cas A list of CAs.
     * @return cas
     */
    @Override
    public List<DBCustomAudience> filterCustomAudiences(List<DBCustomAudience> cas) {
        logSkip();
        return cas;
    }

    /**
     * Identity function that returns its input.
     *
     * @param contextualAds An object containing ads.
     * @return contextual ads
     */
    @Override
    public SignedContextualAds filterContextualAds(SignedContextualAds contextualAds) {
        logSkip();
        return contextualAds;
    }

    private static void logSkip() {
        sLogger.v("Ad filtering is disabled, skipping");
    }
}
