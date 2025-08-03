/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.networkstack.apishim.api31;

import android.net.CaptivePortalData;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.CaptivePortalDataShim;

/**
 * Compatibility implementation of {@link CaptivePortalDataShim}.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class CaptivePortalDataShimImpl
        extends com.android.networkstack.apishim.api30.CaptivePortalDataShimImpl {
    public CaptivePortalDataShimImpl(@NonNull CaptivePortalData data) {
        super(data);
    }

    @Override
    public CharSequence getVenueFriendlyName() {
        return mData.getVenueFriendlyName();
    }

    /**
     * Get the information source of the User portal
     * @return The source that the User portal was obtained from
     */
    @Override
    public int getUserPortalUrlSource() {
        return mData.getUserPortalUrlSource();
    }

    /**
     * Generate a {@link CaptivePortalDataShim} object with a friendly name set
     *
     * @param friendlyName The friendly name to set
     * @return a {@link CaptivePortalDataShim} object with a friendly name set
     */
    @Override
    public CaptivePortalDataShim withVenueFriendlyName(String friendlyName) {
        return new CaptivePortalDataShimImpl(new CaptivePortalData.Builder(mData)
                .setVenueFriendlyName(friendlyName)
                .build());
    }

    /**
     * Generate a {@link CaptivePortalDataShim} object with a friendly name and Passpoint external
     * URLs set
     *
     * @param friendlyName The friendly name to set
     * @param venueInfoUrl Venue information URL
     * @param termsAndConditionsUrl Terms and conditions URL
     *
     * @return a {@link CaptivePortalDataShim} object with friendly name, venue info URL and terms
     * and conditions URL set
     */
    @Override
    public CaptivePortalDataShim withPasspointInfo(@NonNull String friendlyName,
            @NonNull Uri venueInfoUrl, @NonNull Uri termsAndConditionsUrl) {
        return new CaptivePortalDataShimImpl(new CaptivePortalData.Builder(mData)
                .setVenueFriendlyName(friendlyName)
                .setVenueInfoUrl(venueInfoUrl, ConstantsShim.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .setUserPortalUrl(termsAndConditionsUrl,
                        ConstantsShim.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT)
                .build());
    }
}
