/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack.apishim.common;

import android.net.INetworkMonitorCallbacks;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;

/**
 * Compatibility interface for {@link android.net.CaptivePortalData}.
 */
public interface CaptivePortalDataShim {
    /**
     * @see android.net.CaptivePortalData#isCaptive()
     */
    boolean isCaptive();

    /**
     * @see android.net.CaptivePortalData#getByteLimit()
     */
    long getByteLimit();

    /**
     * @see android.net.CaptivePortalData#getExpiryTimeMillis()
     */
    long getExpiryTimeMillis();

    /**
     * @see android.net.CaptivePortalData#getUserPortalUrl()
     */
    Uri getUserPortalUrl();

    /**
     * @see android.net.CaptivePortalData#getVenueInfoUrl()
     */
    Uri getVenueInfoUrl();

    /**
     * @see CaptivePortalData#getVenueFriendlyName()
     */
    CharSequence getVenueFriendlyName();

    /**
     * @see CaptivePortalData#getUserPortalUrlSource()
     */
    int getUserPortalUrlSource();

    /**
     * @see INetworkMonitorCallbacks#notifyCaptivePortalDataChanged(android.net.CaptivePortalData)
     */
    void notifyChanged(INetworkMonitorCallbacks cb) throws RemoteException;

    /**
     * Generate a {@link CaptivePortalData} object with a friendly name set
     *
     * @param friendlyName The friendly name to set
     * @throws UnsupportedApiLevelException when used with API level lower than 31
     * @return a {@link CaptivePortalData} object with a friendly name set
     */
    CaptivePortalDataShim withVenueFriendlyName(@NonNull String friendlyName)
            throws UnsupportedApiLevelException;

    /**
     * Generate a {@link CaptivePortalData} object with a friendly name and Passpoint external URLs
     * set
     *
     * @param friendlyName The friendly name to set
     * @param venueInfoUrl Venue information URL
     * @param termsAndConditionsUrl Terms and conditions URL
     *
     * @throws UnsupportedApiLevelException when used with API level lower than 31
     * @return a {@link CaptivePortalData} object with friendly name, venue info URL and terms
     * and conditions URL set
     */
    CaptivePortalDataShim withPasspointInfo(@NonNull String friendlyName,
            @NonNull Uri venueInfoUrl, @NonNull Uri termsAndConditionsUrl)
            throws UnsupportedApiLevelException;
}
