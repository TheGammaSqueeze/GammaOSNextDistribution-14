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

import android.annotation.NonNull;
import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

import com.android.libraries.entitlement.CarrierConfig;
import com.android.libraries.entitlement.ServiceEntitlement;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.ServiceEntitlementRequest;

/**
 * Class that sends an HTTP request to the entitlement server and processes the response to check
 * whether satellite service can be activated.
 * @hide
 */
public class SatelliteEntitlementApi {
    @NonNull
    private final ServiceEntitlement mServiceEntitlement;
    private final Context mContext;

    public SatelliteEntitlementApi(@NonNull Context context,
            @NonNull PersistableBundle carrierConfig, @NonNull int subId) {
        mContext = context;
        mServiceEntitlement = new ServiceEntitlement(mContext,
                getCarrierConfigFromEntitlementServerUrl(carrierConfig), subId);
    }

    /**
     * Returns satellite entitlement result from the entitlement server.
     * @return The SatelliteEntitlementResult
     */
    public SatelliteEntitlementResult checkEntitlementStatus() throws ServiceEntitlementException {
        ServiceEntitlementRequest.Builder requestBuilder = ServiceEntitlementRequest.builder();
        requestBuilder.setAcceptContentType(ServiceEntitlementRequest.ACCEPT_CONTENT_TYPE_JSON);
        ServiceEntitlementRequest request = requestBuilder.build();

        String response = mServiceEntitlement.queryEntitlementStatus(
                ServiceEntitlement.APP_SATELLITE_ENTITLEMENT, request);
        SatelliteEntitlementResponse satelliteEntitlementResponse =
                new SatelliteEntitlementResponse(response);
        return new SatelliteEntitlementResult(satelliteEntitlementResponse.getEntitlementStatus(),
                satelliteEntitlementResponse.getPlmnAllowed());
    }

    @NonNull
    private CarrierConfig getCarrierConfigFromEntitlementServerUrl(
            @NonNull PersistableBundle carrierConfig) {
        String entitlementServiceUrl = carrierConfig.getString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                "");
        return CarrierConfig.builder().setServerUrl(entitlementServiceUrl).build();
    }
}
