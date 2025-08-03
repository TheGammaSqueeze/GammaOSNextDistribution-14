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

package com.android.phone.slice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.telephony.AnomalyReporter;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.libraries.entitlement.CarrierConfig;
import com.android.libraries.entitlement.ServiceEntitlement;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.ServiceEntitlementRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Premium network entitlement API class to check the premium network slice entitlement result
 * from carrier API over the network.
 */
public class PremiumNetworkEntitlementApi {
    private static final String TAG = "PremiumNwEntitlementApi";
    private static final String ENTITLEMENT_STATUS_KEY = "EntitlementStatus";
    private static final String PROVISION_STATUS_KEY = "ProvStatus";
    private static final String SERVICE_FLOW_URL_KEY = "ServiceFlow_URL";
    private static final String SERVICE_FLOW_USERDATA_KEY = "ServiceFlow_UserData";
    private static final String SERVICE_FLOW_CONTENTS_TYPE_KEY = "ServiceFlow_ContentsType";
    private static final String DEFAULT_EAP_AKA_RESPONSE = "Default EAP AKA response";
    /**
     * UUID to report an anomaly if an unexpected error is received during entitlement check.
     */
    private static final String UUID_ENTITLEMENT_CHECK_UNEXPECTED_ERROR =
            "f2b0661a-9114-4b1b-9add-a8d338f9c054";

    /**
     * Experiment flag to enable bypassing EAP-AKA authentication for Slice Purchase activities.
     * The device will accept any challenge from the entitlement server and return a predefined
     * string as a response.
     *
     * This flag should be enabled for testing only.
     */
    public static final String BYPASS_EAP_AKA_AUTH_FOR_SLICE_PURCHASE_ENABLED =
            "bypass_eap_aka_auth_for_slice_purchase_enabled";

    @NonNull private final Phone mPhone;
    @NonNull private final ServiceEntitlement mServiceEntitlement;

    public PremiumNetworkEntitlementApi(@NonNull Phone phone,
            @NonNull PersistableBundle carrierConfig) {
        mPhone = phone;
        if (isBypassEapAkaAuthForSlicePurchaseEnabled()) {
            mServiceEntitlement =
                    new ServiceEntitlement(
                            mPhone.getContext(),
                            getEntitlementServerCarrierConfig(carrierConfig),
                            mPhone.getSubId(),
                            true,
                            DEFAULT_EAP_AKA_RESPONSE);
        } else {
            mServiceEntitlement =
                    new ServiceEntitlement(
                            mPhone.getContext(),
                            getEntitlementServerCarrierConfig(carrierConfig),
                            mPhone.getSubId());
        }
    }

    /**
     * Returns premium network slice entitlement check result from carrier API (over network),
     * or {@code null} on unrecoverable network issue or malformed server response.
     * This is blocking call sending HTTP request and should not be called on main thread.
     */
    @Nullable public PremiumNetworkEntitlementResponse checkEntitlementStatus(
            @TelephonyManager.PremiumCapability int capability) {
        Log.d(TAG, "checkEntitlementStatus subId=" + mPhone.getSubId());
        ServiceEntitlementRequest.Builder requestBuilder = ServiceEntitlementRequest.builder();
        // Set fake device info to avoid leaking
        requestBuilder.setTerminalVendor("vendorX");
        requestBuilder.setTerminalModel("modelY");
        requestBuilder.setTerminalSoftwareVersion("versionZ");
        requestBuilder.setAcceptContentType(ServiceEntitlementRequest.ACCEPT_CONTENT_TYPE_JSON);
        requestBuilder.setBoostType(getBoostTypeFromPremiumCapability(capability));
        ServiceEntitlementRequest request = requestBuilder.build();
        PremiumNetworkEntitlementResponse premiumNetworkEntitlementResponse =
                new PremiumNetworkEntitlementResponse();

        String response = null;
        try {
            response = mServiceEntitlement.queryEntitlementStatus(
                    ServiceEntitlement.APP_DATA_PLAN_BOOST,
                    request);
        } catch (ServiceEntitlementException e) {
            Log.e(TAG, "queryEntitlementStatus failed", e);
            reportAnomaly(UUID_ENTITLEMENT_CHECK_UNEXPECTED_ERROR,
                    "checkEntitlementStatus failed with ServiceEntitlementException");
        }
        if (response == null) {
            return null;
        }
        try {
            JSONObject jsonAuthResponse = new JSONObject(response);
            if (jsonAuthResponse.has(ServiceEntitlement.APP_DATA_PLAN_BOOST)) {
                JSONObject jsonToken = jsonAuthResponse.getJSONObject(
                        ServiceEntitlement.APP_DATA_PLAN_BOOST);
                if (jsonToken.has(ENTITLEMENT_STATUS_KEY)) {
                    String entitlementStatus = jsonToken.getString(ENTITLEMENT_STATUS_KEY);
                    if (entitlementStatus == null) {
                        return null;
                    }
                    premiumNetworkEntitlementResponse.mEntitlementStatus =
                            Integer.parseInt(entitlementStatus);
                }
                if (jsonToken.has(PROVISION_STATUS_KEY)) {
                    String provisionStatus = jsonToken.getString(PROVISION_STATUS_KEY);
                    if (provisionStatus != null) {
                        premiumNetworkEntitlementResponse.mProvisionStatus =
                                Integer.parseInt(provisionStatus);
                    }
                }
                if (jsonToken.has(SERVICE_FLOW_URL_KEY)) {
                    premiumNetworkEntitlementResponse.mServiceFlowURL =
                            jsonToken.getString(SERVICE_FLOW_URL_KEY);
                }
                if (jsonToken.has(SERVICE_FLOW_USERDATA_KEY)) {
                    premiumNetworkEntitlementResponse.mServiceFlowUserData =
                            jsonToken.getString(SERVICE_FLOW_USERDATA_KEY);
                }
                if (jsonToken.has(SERVICE_FLOW_CONTENTS_TYPE_KEY)) {
                    premiumNetworkEntitlementResponse.mServiceFlowContentsType =
                            jsonToken.getString(SERVICE_FLOW_CONTENTS_TYPE_KEY);
                }
            } else {
                Log.e(TAG, "queryEntitlementStatus failed with no app");
            }
        } catch (JSONException e) {
            Log.e(TAG, "queryEntitlementStatus failed", e);
            reportAnomaly(UUID_ENTITLEMENT_CHECK_UNEXPECTED_ERROR,
                    "checkEntitlementStatus failed with JSONException");
        } catch (NumberFormatException e) {
            Log.e(TAG, "queryEntitlementStatus failed", e);
            reportAnomaly(UUID_ENTITLEMENT_CHECK_UNEXPECTED_ERROR,
                    "checkEntitlementStatus failed with NumberFormatException");
        }
        Log.d(TAG, "queryEntitlementStatus succeeded with response: "
                + premiumNetworkEntitlementResponse);
        return premiumNetworkEntitlementResponse;
    }

    private void reportAnomaly(@NonNull String uuid, @NonNull String log) {
        AnomalyReporter.reportAnomaly(UUID.fromString(uuid), log);
    }

    /**
     * Returns entitlement server url from the given carrier configs or a default empty string
     * if it is not available.
     */
    @NonNull public static String getEntitlementServerUrl(
            @NonNull PersistableBundle carrierConfig) {
        return carrierConfig.getString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                "");
    }

    @NonNull private CarrierConfig getEntitlementServerCarrierConfig(
            @NonNull PersistableBundle carrierConfig) {
        String entitlementServiceUrl = getEntitlementServerUrl(carrierConfig);
        return CarrierConfig.builder().setServerUrl(entitlementServiceUrl).build();
    }

    private boolean isBypassEapAkaAuthForSlicePurchaseEnabled() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_TELEPHONY,
                BYPASS_EAP_AKA_AUTH_FOR_SLICE_PURCHASE_ENABLED, false);
    }

    @NonNull private String getBoostTypeFromPremiumCapability(
            @TelephonyManager.PremiumCapability int capability) {
        if (capability == TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY) {
            return "0" /* REALTIME_INTERACTIVE_TRAFFIC */;
        }
        return "";
    }
}
