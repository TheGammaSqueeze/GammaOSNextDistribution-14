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

import android.annotation.IntDef;
import android.annotation.NonNull;

/**
 * Response class containing the entitlement status, provisioning status, and service flow URL
 * for premium network entitlement checks.
 *
 * The relationship between entitlement status (left column) and provision status (top row)
 * is defined in the table below:
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * |              | Not Provisioned |    Provisioned    |   Not Available   |   In Progress   |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * |   Disabled   |   Check failed  |    Check failed   |    Check failed   |   Check failed  |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * |    Enabled   | Display webview | Already purchased | Already purchased |   In progress   |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * | Incompatible |   Check failed  |    Check failed   |    Check failed   |   Check failed  |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * | Provisioning |  Carrier error  |   Carrier error   |    In progress    |   In progress   |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 * |   Included   |  Carrier error  | Already purchased | Already purchased |  Carrier error  |
 * +--------------+-----------------+-------------------+-------------------+-----------------+
 */
public class PremiumNetworkEntitlementResponse {
    public static final int PREMIUM_NETWORK_ENTITLEMENT_STATUS_DISABLED = 0;
    public static final int PREMIUM_NETWORK_ENTITLEMENT_STATUS_ENABLED = 1;
    public static final int PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCOMPATIBLE = 2;
    public static final int PREMIUM_NETWORK_ENTITLEMENT_STATUS_PROVISIONING = 3;
    public static final int PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCLUDED = 4;

    @IntDef(prefix = {"PREMIUM_NETWORK_ENTITLEMENT_STATUS_"}, value = {
            PREMIUM_NETWORK_ENTITLEMENT_STATUS_DISABLED,
            PREMIUM_NETWORK_ENTITLEMENT_STATUS_ENABLED,
            PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCOMPATIBLE,
            PREMIUM_NETWORK_ENTITLEMENT_STATUS_PROVISIONING,
            PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCLUDED
    })
    public @interface PremiumNetworkEntitlementStatus {}

    public static final int PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED = 0;
    public static final int PREMIUM_NETWORK_PROVISION_STATUS_PROVISIONED = 1;
    public static final int PREMIUM_NETWORK_PROVISION_STATUS_NOT_AVAILABLE = 2;
    public static final int PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS = 3;

    @IntDef(prefix = {"PREMIUM_NETWORK_PROVISION_STATUS_"}, value = {
            PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED,
            PREMIUM_NETWORK_PROVISION_STATUS_PROVISIONED,
            PREMIUM_NETWORK_PROVISION_STATUS_NOT_AVAILABLE,
            PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS
    })
    public @interface PremiumNetworkProvisionStatus {}

    @PremiumNetworkEntitlementStatus public int mEntitlementStatus;
    @PremiumNetworkProvisionStatus public int mProvisionStatus;
    @NonNull public String mServiceFlowURL;
    @NonNull public String mServiceFlowUserData;
    @NonNull public String mServiceFlowContentsType;

    /**
     * @return {@code true} if the premium network is already purchased and {@code false} otherwise.
     */
    public boolean isAlreadyPurchased() {
        switch (mEntitlementStatus) {
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_ENABLED:
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCLUDED:
                return mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_PROVISIONED
                        || mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_NOT_AVAILABLE;
        }
        return false;
    }

    /**
     * @return {@code true} if provisioning the premium network is in progress and
     *         {@code false} otherwise.
     */
    public boolean isProvisioningInProgress() {
        switch (mEntitlementStatus) {
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_ENABLED:
                return mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS;
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_PROVISIONING:
                return mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS
                        || mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_NOT_AVAILABLE;
        }
        return false;
    }

    /**
     * @return {@code true} if the premium network capability is allowed and
     *         {@code false} otherwise.
     */
    public boolean isPremiumNetworkCapabilityAllowed() {
        switch (mEntitlementStatus) {
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_DISABLED:
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCOMPATIBLE:
                return false;
        }
        return !isInvalidResponse();
    }

    /**
     * @return {@code true} if the response is invalid and {@code false} if it is valid.
     */
    public boolean isInvalidResponse() {
        switch (mEntitlementStatus) {
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCLUDED:
                return mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED
                        || mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS;
            case PREMIUM_NETWORK_ENTITLEMENT_STATUS_PROVISIONING:
                return mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED
                    || mProvisionStatus == PREMIUM_NETWORK_PROVISION_STATUS_PROVISIONED;
        }
        return false;
    }

    @Override
    @NonNull public String toString() {
        return "PremiumNetworkEntitlementResponse{mEntitlementStatus=" + mEntitlementStatus
                + ", mProvisionStatus=" + mProvisionStatus + ", mServiceFlowURL=" + mServiceFlowURL
                + ", mServiceFlowUserData=" + mServiceFlowUserData + ", mServiceFlowContentsType="
                + mServiceFlowContentsType + "}";
    }
}
