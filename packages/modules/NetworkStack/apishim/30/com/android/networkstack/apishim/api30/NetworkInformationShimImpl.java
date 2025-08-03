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

import static com.android.modules.utils.build.SdkLevel.isAtLeastR;

import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.CaptivePortalDataShim;
import com.android.networkstack.apishim.common.NetworkInformationShim;

import java.net.Inet4Address;

/**
 * Compatibility implementation of {@link NetworkInformationShim}.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class NetworkInformationShimImpl extends
        com.android.networkstack.apishim.api29.NetworkInformationShimImpl {
    private static final String TAG = "api30.NetworkInformationShimImpl";

    protected NetworkInformationShimImpl() {}

    /**
     * Get a new instance of {@link NetworkInformationShim}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static NetworkInformationShim newInstance() {
        if (!isAtLeastR()) {
            return com.android.networkstack.apishim.api29.NetworkInformationShimImpl.newInstance();
        }
        return new NetworkInformationShimImpl();
    }

    @Nullable
    @Override
    public Uri getCaptivePortalApiUrl(@Nullable LinkProperties lp) {
        if (lp == null) return null;
        return lp.getCaptivePortalApiUrl();
    }

    @Override
    public void setCaptivePortalApiUrl(@NonNull LinkProperties lp, @Nullable Uri url) {
        lp.setCaptivePortalApiUrl(url);
    }

    @Nullable
    @Override
    public CaptivePortalDataShim getCaptivePortalData(@Nullable LinkProperties lp) {
        if (lp == null || lp.getCaptivePortalData() == null) return null;
        return new CaptivePortalDataShimImpl(lp.getCaptivePortalData());
    }

    @Nullable
    @Override
    public IpPrefix getNat64Prefix(@NonNull LinkProperties lp) {
        return lp.getNat64Prefix();
    }

    @Override
    public void setNat64Prefix(@NonNull LinkProperties lp, @Nullable IpPrefix prefix) {
        lp.setNat64Prefix(prefix);
    }

    @Nullable
    @Override
    public String getSsid(@Nullable NetworkCapabilities nc) {
        if (nc == null) return null;
        return nc.getSsid();
    }

    @NonNull
    @Override
    public LinkProperties makeSensitiveFieldsParcelingCopy(@NonNull final LinkProperties lp) {
        return new LinkProperties(lp, true);
    }

    @Override
    public void setDhcpServerAddress(@NonNull LinkProperties lp,
            @NonNull Inet4Address serverAddress) {
        lp.setDhcpServerAddress(serverAddress);
    }

    @Override
    public void setCaptivePortalData(@NonNull LinkProperties lp,
            @Nullable CaptivePortalDataShim captivePortalData) {
        if (lp == null) {
            return;
        }
        if (!(captivePortalData instanceof CaptivePortalDataShimImpl)) {
            // The caller passed in a subclass that is not a CaptivePortalDataShimImpl.
            // This is a programming error, but don't crash with ClassCastException.
            Log.wtf(TAG, "Expected CaptivePortalDataShimImpl, but got "
                    + captivePortalData.getClass().getName());
            return;
        }
        lp.setCaptivePortalData(((CaptivePortalDataShimImpl) captivePortalData).getData());
    }
}
