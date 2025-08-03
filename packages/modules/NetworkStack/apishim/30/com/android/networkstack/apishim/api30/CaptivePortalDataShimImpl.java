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

package com.android.networkstack.apishim.api30;

import android.net.CaptivePortalData;
import android.net.INetworkMonitorCallbacks;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.CaptivePortalDataShim;
import com.android.networkstack.apishim.common.ShimUtils;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Compatibility implementation of {@link CaptivePortalDataShim}.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class CaptivePortalDataShimImpl
        extends com.android.networkstack.apishim.api29.CaptivePortalDataShimImpl {
    @NonNull
    protected final CaptivePortalData mData;

    public CaptivePortalDataShimImpl(@NonNull CaptivePortalData data) {
        mData = data;
    }

    public CaptivePortalData getData() {
        return mData;
    }

    /**
     * Parse a {@link CaptivePortalDataShim} from a JSON object.
     * @throws JSONException The JSON is not a representation of correct captive portal data.
     * @throws UnsupportedApiLevelException CaptivePortalData is not available on this API level.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    public static CaptivePortalDataShim fromJson(JSONObject obj) throws JSONException,
            UnsupportedApiLevelException {
        if (!isSupported()) {
            return com.android.networkstack.apishim.api29.CaptivePortalDataShimImpl.fromJson(obj);
        }

        final long refreshTimeMs = System.currentTimeMillis();
        final long secondsRemaining = getLongOrDefault(obj, "seconds-remaining", -1L);
        final long millisRemaining = secondsRemaining <= Long.MAX_VALUE / 1000
                ? secondsRemaining * 1000
                : Long.MAX_VALUE;
        final long expiryTimeMs = secondsRemaining == -1L ? -1L :
                refreshTimeMs + Math.min(Long.MAX_VALUE - refreshTimeMs, millisRemaining);
        return new CaptivePortalDataShimImpl(new CaptivePortalData.Builder()
                .setRefreshTime(refreshTimeMs)
                // captive is mandatory; throws JSONException if absent
                .setCaptive(obj.getBoolean("captive"))
                .setUserPortalUrl(getUriOrNull(obj, "user-portal-url"))
                .setVenueInfoUrl(getUriOrNull(obj, "venue-info-url"))
                .setBytesRemaining(getLongOrDefault(obj, "bytes-remaining", -1L))
                .setExpiryTime(expiryTimeMs)
                .build());
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    public static boolean isSupported() {
        return ShimUtils.isReleaseOrDevelopmentApiAbove(Build.VERSION_CODES.Q);
    }

    private static long getLongOrDefault(JSONObject o, String key, long def) throws JSONException {
        if (!o.has(key)) return def;
        return o.getLong(key);
    }

    private static Uri getUriOrNull(JSONObject o, String key) throws JSONException {
        if (!o.has(key)) return null;
        return Uri.parse(o.getString(key));
    }

    @Override
    public boolean isCaptive() {
        return mData.isCaptive();
    }

    @Override
    public long getByteLimit() {
        return mData.getByteLimit();
    }

    @Override
    public long getExpiryTimeMillis() {
        return mData.getExpiryTimeMillis();
    }

    @Override
    public Uri getUserPortalUrl() {
        return mData.getUserPortalUrl();
    }

    @Override
    public Uri getVenueInfoUrl() {
        return mData.getVenueInfoUrl();
    }

    @Override
    public void notifyChanged(INetworkMonitorCallbacks cb) throws RemoteException {
        cb.notifyCaptivePortalDataChanged(mData);
    }

    /**
     * Generate a {@link CaptivePortalDataShim} object with a friendly name set
     *
     * @param friendlyName The friendly name to set
     * @return a {@link CaptivePortalDataShim} object with a friendly name set
     */
    @Override
    public CaptivePortalDataShim withVenueFriendlyName(String friendlyName)
            throws UnsupportedApiLevelException {
        // Not supported in API level 30
        throw new UnsupportedApiLevelException("FriendlyName not supported on API 30");
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
            @NonNull Uri venueInfoUrl, @NonNull Uri termsAndConditionsUrl)
            throws UnsupportedApiLevelException {
        // Not supported in API level 30
        throw new UnsupportedApiLevelException("PasspointInfo not supported on API 30");
    }
}
