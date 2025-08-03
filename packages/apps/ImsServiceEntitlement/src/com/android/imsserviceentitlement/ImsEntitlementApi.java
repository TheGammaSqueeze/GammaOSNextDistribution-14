/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.imsserviceentitlement.debug.DebugUtils;
import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration;
import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior;
import com.android.imsserviceentitlement.entitlement.EntitlementResult;
import com.android.imsserviceentitlement.fcm.FcmTokenStore;
import com.android.imsserviceentitlement.fcm.FcmUtils;
import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlAttributes;
import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlNode;
import com.android.imsserviceentitlement.ts43.Ts43SmsOverIpStatus;
import com.android.imsserviceentitlement.ts43.Ts43VolteStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus;
import com.android.imsserviceentitlement.utils.TelephonyUtils;
import com.android.imsserviceentitlement.utils.XmlDoc;
import com.android.libraries.entitlement.CarrierConfig;
import com.android.libraries.entitlement.ServiceEntitlement;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.ServiceEntitlementRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Implementation of the entitlement API. */
public class ImsEntitlementApi {
    private static final String TAG = "IMSSE-ImsEntitlementApi";

    private static final int RESPONSE_RETRY_AFTER = 503;
    private static final int RESPONSE_TOKEN_EXPIRED = 511;

    private static final int AUTHENTICATION_RETRIES = 1;

    private final Context mContext;
    private final int mSubId;
    private final ServiceEntitlement mServiceEntitlement;
    private final EntitlementConfiguration mLastEntitlementConfiguration;

    private int mRetryFullAuthenticationCount = AUTHENTICATION_RETRIES;
    private boolean mNeedsImsProvisioning;

    @VisibleForTesting
    static Clock sClock = Clock.systemUTC();

    public ImsEntitlementApi(Context context, int subId) {
        this.mContext = context;
        this.mSubId = subId;
        CarrierConfig carrierConfig = getCarrierConfig(context);
        this.mNeedsImsProvisioning = TelephonyUtils.isImsProvisioningRequired(context, subId);
        this.mServiceEntitlement =
                new ServiceEntitlement(
                        context,
                        carrierConfig,
                        subId,
                        /* saveHttpHistory = */ false,
                        DebugUtils.getBypassEapAkaResponse());
        this.mLastEntitlementConfiguration = new EntitlementConfiguration(context, subId);
    }

    @VisibleForTesting
    ImsEntitlementApi(
            Context context,
            int subId,
            boolean needsImsProvisioning,
            ServiceEntitlement serviceEntitlement,
            EntitlementConfiguration lastEntitlementConfiguration) {
        this.mContext = context;
        this.mSubId = subId;
        this.mNeedsImsProvisioning = needsImsProvisioning;
        this.mServiceEntitlement = serviceEntitlement;
        this.mLastEntitlementConfiguration = lastEntitlementConfiguration;
    }

    /**
     * Returns WFC entitlement check result from carrier API (over network), or {@code null} on
     * unrecoverable network issue or malformed server response. This is blocking call so should
     * not be called on main thread.
     */
    @Nullable
    public EntitlementResult checkEntitlementStatus() {
        Log.d(TAG, "checkEntitlementStatus subId=" + mSubId);
        ServiceEntitlementRequest.Builder requestBuilder = ServiceEntitlementRequest.builder();
        mLastEntitlementConfiguration.getToken().ifPresent(
                token -> requestBuilder.setAuthenticationToken(token));
        FcmUtils.fetchFcmToken(mContext, mSubId);
        requestBuilder.setNotificationToken(FcmTokenStore.getToken(mContext, mSubId));
        requestBuilder.setAcceptContentType(ServiceEntitlementRequest.ACCEPT_CONTENT_TYPE_XML);
        if (mNeedsImsProvisioning) {
            requestBuilder.setConfigurationVersion(
                    Integer.parseInt(mLastEntitlementConfiguration.getVersion()));
        }
        ServiceEntitlementRequest request = requestBuilder.build();

        XmlDoc entitlementXmlDoc = null;

        try {
            String rawXml = mServiceEntitlement.queryEntitlementStatus(
                    mNeedsImsProvisioning
                            ? ImmutableList.of(
                            ServiceEntitlement.APP_VOWIFI,
                            ServiceEntitlement.APP_VOLTE,
                            ServiceEntitlement.APP_SMSOIP)
                            : ImmutableList.of(ServiceEntitlement.APP_VOWIFI),
                    request);
            entitlementXmlDoc = new XmlDoc(rawXml);
            mLastEntitlementConfiguration.update(rawXml);
            // Reset the retry count if no exception from queryEntitlementStatus()
            mRetryFullAuthenticationCount = AUTHENTICATION_RETRIES;
        } catch (ServiceEntitlementException e) {
            if (e.getErrorCode() == ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS) {
                if (e.getHttpStatus() == RESPONSE_TOKEN_EXPIRED) {
                    if (mRetryFullAuthenticationCount <= 0) {
                        Log.d(TAG, "Ran out of the retry count, stop query status.");
                        return null;
                    }
                    Log.d(TAG, "Server asking for full authentication, retry the query.");
                    // Clean up the cached data and perform full authentication next query.
                    mLastEntitlementConfiguration.reset();
                    mRetryFullAuthenticationCount--;
                    return checkEntitlementStatus();
                } else if (e.getHttpStatus() == RESPONSE_RETRY_AFTER && !TextUtils.isEmpty(
                        e.getRetryAfter())) {
                    // For handling the case of HTTP_UNAVAILABLE(503), client would perform the
                    // retry for the delay of Retry-After.
                    Log.d(TAG, "Server asking for retry. retryAfter = " + e.getRetryAfter());
                    return EntitlementResult
                            .builder()
                            .setRetryAfterSeconds(parseDelaySecondsByRetryAfter(e.getRetryAfter()))
                            .build();
                }
            }
            Log.e(TAG, "queryEntitlementStatus failed", e);
        }
        return entitlementXmlDoc == null ? null : toEntitlementResult(entitlementXmlDoc);
    }

    /**
     * Parses the value of {@link HttpHeaders#RETRY_AFTER}. The possible formats could be a numeric
     * value in second, or a HTTP-date in RFC-1123 date-time format.
     */
    private long parseDelaySecondsByRetryAfter(String retryAfter) {
        try {
            return Long.parseLong(retryAfter);
        } catch (NumberFormatException numberFormatException) {
        }

        try {
            return SECONDS.between(
                    Instant.now(sClock), RFC_1123_DATE_TIME.parse(retryAfter, Instant::from));
        } catch (DateTimeParseException dateTimeParseException) {
        }

        Log.w(TAG, "Unable to parse retry-after: " + retryAfter + ", ignore it.");
        return -1;
    }

    private EntitlementResult toEntitlementResult(XmlDoc doc) {
        EntitlementResult.Builder builder = EntitlementResult.builder();
        ClientBehavior clientBehavior = mLastEntitlementConfiguration.entitlementValidation();

        if (mNeedsImsProvisioning && isResetToDefault(clientBehavior)) {
            // keep the entitlement result in default value and reset the configs.
            if (clientBehavior == ClientBehavior.NEEDS_TO_RESET
                    || clientBehavior == ClientBehavior.UNKNOWN_BEHAVIOR) {
                mLastEntitlementConfiguration.reset();
            } else {
                mLastEntitlementConfiguration.resetConfigsExceptVers();
            }
        } else {
            builder.setVowifiStatus(Ts43VowifiStatus.builder(doc).build())
                    .setVolteStatus(Ts43VolteStatus.builder(doc).build())
                    .setSmsoveripStatus(Ts43SmsOverIpStatus.builder(doc).build());
            doc.get(
                    ResponseXmlNode.APPLICATION,
                    ResponseXmlAttributes.SERVER_FLOW_URL,
                    ServiceEntitlement.APP_VOWIFI)
                    .ifPresent(url -> builder.setEmergencyAddressWebUrl(url));
            doc.get(
                    ResponseXmlNode.APPLICATION,
                    ResponseXmlAttributes.SERVER_FLOW_USER_DATA,
                    ServiceEntitlement.APP_VOWIFI)
                    .ifPresent(userData -> builder.setEmergencyAddressWebData(userData));
        }
        return builder.build();
    }

    private boolean isResetToDefault(ClientBehavior clientBehavior) {
        return clientBehavior == ClientBehavior.UNKNOWN_BEHAVIOR
                || clientBehavior == ClientBehavior.NEEDS_TO_RESET
                || clientBehavior == ClientBehavior.NEEDS_TO_RESET_EXCEPT_VERS
                || clientBehavior == ClientBehavior.NEEDS_TO_RESET_EXCEPT_VERS_UNTIL_SETTING_ON;
    }

    private CarrierConfig getCarrierConfig(Context context) {
        String entitlementServiceUrl = TelephonyUtils.getEntitlementServerUrl(context, mSubId);
        return CarrierConfig.builder()
                .setClientTs43(CarrierConfig.CLIENT_TS_43_IMS_ENTITLEMENT)
                .setServerUrl(entitlementServiceUrl)
                .build();
    }
}
