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

package com.android.phone.satellite.entitlement;

import android.annotation.IntDef;

import com.android.internal.telephony.satellite.SatelliteNetworkInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class stores the result of the satellite entitlement query and passes them to
 * SatelliteEntitlementController.
 */
public class SatelliteEntitlementResult {
    /** SatMode allowed, but not yet provisioned and activated on the network. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_DISABLED = 0;
    /** SatMode service allowed, provisioned and activated on the network. User can access the
     * satellite service. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_ENABLED = 1;
    /** SatMode cannot be offered for network or device. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE = 2;
    /** SatMode is being provisioned on the network. Not yet activated. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_PROVISIONING = 3;

    @IntDef(prefix = {"SATELLITE_ENTITLEMENT_STATUS_"}, value = {
            SATELLITE_ENTITLEMENT_STATUS_DISABLED,
            SATELLITE_ENTITLEMENT_STATUS_ENABLED,
            SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE,
            SATELLITE_ENTITLEMENT_STATUS_PROVISIONING
    })
    public @interface SatelliteEntitlementStatus {}

    private @SatelliteEntitlementStatus int mEntitlementStatus;
    /**
     * An SatelliteNetworkInfo list consisting of the PLMN and the DataPlanType in the PLMNAlowed
     * item of the satellite configuration received from the entitlement server.
     */
    private List<SatelliteNetworkInfo> mAllowedSatelliteNetworkInfoList;

    /**
     * Store the result of the satellite entitlement response.
     *
     * @param entitlementStatus The entitlement status.
     * @param allowedSatelliteNetworkInfoList The allowedSatelliteNetworkInfoList
     */
    public SatelliteEntitlementResult(@SatelliteEntitlementStatus int entitlementStatus,
            List<SatelliteNetworkInfo> allowedSatelliteNetworkInfoList) {
        mEntitlementStatus = entitlementStatus;
        mAllowedSatelliteNetworkInfoList = allowedSatelliteNetworkInfoList;
    }

    /**
     * Get the entitlement status.
     *
     * @return The entitlement status.
     */
    public @SatelliteEntitlementStatus int getEntitlementStatus() {
        return mEntitlementStatus;
    }

    /**
     * Get the plmn allowed list
     *
     * @return The plmn allowed list.
     */
    public List<String> getAllowedPLMNList() {
        return mAllowedSatelliteNetworkInfoList.stream().map(info -> info.mPlmn).collect(
                Collectors.toList());
    }

    /**
     * Get the default SatelliteEntitlementResult. EntitlementStatus set to
     * `SATELLITE_ENTITLEMENT_STATUS_DISABLED` and SatelliteNetworkInfo list set to empty.
     *
     * @return If there is no response, return default SatelliteEntitlementResult
     */
    public static SatelliteEntitlementResult getDefaultResult() {
        return new SatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_DISABLED,
                new ArrayList<>());
    }
}
