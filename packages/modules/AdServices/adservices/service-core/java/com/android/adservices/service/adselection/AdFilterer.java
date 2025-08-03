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

import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.List;

/** Interface for filtering ads out of an ad selection auction. */
public interface AdFilterer {
    /**
     * Takes a list of CAs and returns an identical list with any ads that should be filtered
     * removed.
     *
     * <p>Note that some of the copying to the new list is shallow, so the original list should not
     * be re-used after the method is called.
     *
     * @param cas A list of CAs to filter ads for.
     * @return A list of cas identical to the cas input, but with any ads that should be filtered
     *     removed.
     */
    List<DBCustomAudience> filterCustomAudiences(List<DBCustomAudience> cas);
    /**
     * Takes in a {@link SignedContextualAds} object and filters out ads from it that should not be
     * in the auction
     *
     * @param contextualAds An object containing contextual ads corresponding to a buyer
     * @return A list of object identical to the input, but without any ads that should be filtered
     */
    SignedContextualAds filterContextualAds(SignedContextualAds contextualAds);
}
