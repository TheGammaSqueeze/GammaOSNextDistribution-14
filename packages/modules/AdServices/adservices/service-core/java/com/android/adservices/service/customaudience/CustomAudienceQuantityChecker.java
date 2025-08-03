/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Checks custom audience storage usage. */
public class CustomAudienceQuantityChecker {
    @VisibleForTesting
    static final String CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED =
            "Custom audience quantity check failed. %s";

    @VisibleForTesting
    static final String THE_MAX_NUMBER_OF_OWNER_ALLOWED_FOR_THE_DEVICE_HAD_REACHED =
            "The max number of owner allowed for the device had reached.";

    @VisibleForTesting
    static final String THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED =
            "The max number of custom audience for the device had reached.";

    @VisibleForTesting
    static final String THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED =
            "The max number of custom audience for the owner had reached.";

    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final Flags mFlags;

    public CustomAudienceQuantityChecker(
            @NonNull CustomAudienceDao customAudienceDao, @NonNull Flags flags) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);

        mCustomAudienceDao = customAudienceDao;
        mFlags = flags;
    }

    /**
     * Validates custom audience quantity:
     *
     * <ol>
     *   <li>The total number of custom audience does not exceed max allowed.
     *   <li>The total number of custom audience of an owner does not exceed max allowed.
     *   <li>The total number of custom audience owner does not exceed max allowed.
     * </ol>
     *
     * @param customAudience The custom audience really to be validated against.
     * @param callerPackageName the package name for the calling application, used as the owner
     *     application identifier
     */
    public void check(@NonNull CustomAudience customAudience, @NonNull String callerPackageName) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(callerPackageName);

        long mCustomAudienceMaxOwnerCount = mFlags.getFledgeCustomAudienceMaxOwnerCount();
        long mCustomAudienceMaxCount = mFlags.getFledgeCustomAudienceMaxCount();
        long mCustomAudiencePerAppMaxCount = mFlags.getFledgeCustomAudiencePerAppMaxCount();

        List<String> violations = new ArrayList<>();
        CustomAudienceStats customAudienceStats =
                mCustomAudienceDao.getCustomAudienceStats(callerPackageName);
        if (customAudienceStats.getPerOwnerCustomAudienceCount() == 0
                && customAudienceStats.getTotalOwnerCount() >= mCustomAudienceMaxOwnerCount) {
            violations.add(THE_MAX_NUMBER_OF_OWNER_ALLOWED_FOR_THE_DEVICE_HAD_REACHED);
        }
        if (customAudienceStats.getTotalCustomAudienceCount() >= mCustomAudienceMaxCount) {
            violations.add(THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED);
        }
        if (customAudienceStats.getPerOwnerCustomAudienceCount() >= mCustomAudiencePerAppMaxCount) {
            violations.add(THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED);
        }
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED, violations));
        }
    }
}
