/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi.entitlement;

import static com.android.libraries.entitlement.EapAkaHelper.EapAkaResponse;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.entitlement.EapAkaHelper;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.modules.utils.BackgroundThread;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpConstants.RequestMethod;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;
import com.android.server.wifi.entitlement.response.ChallengeResponse;
import com.android.server.wifi.entitlement.response.GetImsiPseudonymResponse;
import com.android.server.wifi.entitlement.response.Response;

import com.google.common.net.HttpHeaders;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * Implements the protocol to get IMSI pseudonym from service entitlement server.
 */
public class CarrierSpecificServiceEntitlement {
    public static final int REASON_HTTPS_CONNECTION_FAILURE = 0;
    public static final int REASON_TRANSIENT_FAILURE = 1;
    public static final int REASON_NON_TRANSIENT_FAILURE = 2;
    public static final String[] FAILURE_REASON_NAME = {
            "HTTP connection failure",
            "Transient failure",
            "Non-transient failure",
    };
    @IntDef(prefix = { "REASON_" }, value = {
            REASON_HTTPS_CONNECTION_FAILURE,
            REASON_TRANSIENT_FAILURE,
            REASON_NON_TRANSIENT_FAILURE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FailureReasonCode {}

    private static final String MIME_TYPE_JSON = "application/json";
    private static final String ENCODING_GZIP = "gzip";
    private static final int CONNECT_TIMEOUT_SECS = 30;

    private final RequestFactory mRequestFactory;
    private final HttpRequest.Builder mHttpRequestBuilder;
    private final EapAkaHelper mEapAkaHelper;
    private final String mImsi;
    private final Handler mBackgroundHandler;

    private String mAkaTokenCache;

    public CarrierSpecificServiceEntitlement(@NonNull Context context, int subId,
            @NonNull String serverUrl) throws MalformedURLException {
        this(context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId),
                EapAkaHelper.getInstance(context, subId), serverUrl);
    }

    private CarrierSpecificServiceEntitlement(@NonNull TelephonyManager telephonyManager,
            @NonNull EapAkaHelper eapAkaHelper, @NonNull String serverUrl)
            throws MalformedURLException {
        this(telephonyManager.getSubscriberId(), new RequestFactory(telephonyManager), eapAkaHelper,
                serverUrl, BackgroundThread.getHandler());
    }

    @VisibleForTesting
    CarrierSpecificServiceEntitlement(@NonNull String imsi,
            @NonNull RequestFactory requestFactory,
            @NonNull EapAkaHelper eapAkaHelper,
            @NonNull String serverUrl,
            @NonNull Handler backgroundHandler) throws MalformedURLException {
        URL url = new URL(serverUrl);
        if (!TextUtils.equals(url.getProtocol(), "https")) {
            throw new MalformedURLException("The server URL must use HTTPS protocol");
        }
        mImsi = imsi;
        mRequestFactory = requestFactory;
        mHttpRequestBuilder = HttpRequest.builder()
                .setUrl(serverUrl)
                .setRequestMethod(RequestMethod.POST)
                .addRequestProperty(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON)
                .addRequestProperty(HttpHeaders.CONTENT_ENCODING, ENCODING_GZIP)
                .addRequestProperty(HttpHeaders.ACCEPT, MIME_TYPE_JSON)
                .setTimeoutInSec(CONNECT_TIMEOUT_SECS);
        mEapAkaHelper = eapAkaHelper;
        mBackgroundHandler = backgroundHandler;
    }

    /**
     * Retrieve the OOB IMSI pseudonym from the entitlement server in the BackgroundThread.
     *
     * @param callbackHandler The handler used to run the callback.
     * @param callback The callback which will be called when the pseudonym is retrieved from
     *                server.
     */
    public void getImsiPseudonym(int carrierId, @NonNull Handler callbackHandler,
            @NonNull Callback callback) {
        mBackgroundHandler.post(() -> {
            try {
                Optional<PseudonymInfo> optionalPseudonymInfo = getImsiPseudonym();
                if (optionalPseudonymInfo.isPresent()) {
                    callbackHandler.post(() -> callback.onSuccess(carrierId,
                            optionalPseudonymInfo.get()));
                } else {
                    callbackHandler.post(() -> callback.onFailure(carrierId,
                            REASON_NON_TRANSIENT_FAILURE, "No valid pseudonym is received"));
                }
            } catch (ServiceEntitlementException e) {
                callbackHandler.post(() -> callback.onFailure(carrierId,
                        REASON_HTTPS_CONNECTION_FAILURE, e.toString()));
            } catch (TransientException e) {
                callbackHandler.post(() -> callback.onFailure(carrierId, REASON_TRANSIENT_FAILURE,
                        e.toString()));
            } catch (NonTransientException e) {
                callbackHandler.post(() -> callback.onFailure(carrierId,
                        REASON_NON_TRANSIENT_FAILURE, e.toString()));
            }
        });
    }

    /**
     * Retrieve the OOB IMSI pseudonym from the entitlement server.
     * @throws TransientException if a transient failure like failure to connect with server or
     * server's temporary problem etc.
     * @throws NonTransientException if a non-transient failure, like failure to get challenge
     * response or authentication failure from server etc.
     * @throws ServiceEntitlementException if there is any HTTPS connection failure.
     */
    private Optional<PseudonymInfo> getImsiPseudonym() throws
            TransientException, NonTransientException, ServiceEntitlementException {
        String eapAkaChallenge = null;
        if (TextUtils.isEmpty(mAkaTokenCache)) {
            eapAkaChallenge = getAuthenticationChallenge();
        }
        String eapAkaChallengeResponse = null;
        if (!TextUtils.isEmpty(eapAkaChallenge)) {
            EapAkaResponse eapAkaResponse = mEapAkaHelper.getEapAkaResponse(eapAkaChallenge);
            if (eapAkaResponse == null) {
                throw new NonTransientException("Can't get the AKA challenge response.");
            }
            eapAkaChallengeResponse = eapAkaResponse.response();
            if (eapAkaChallengeResponse == null) {
                throw new TransientException("EAP-AKA Challenge message not valid!");
            }
        }

        HttpResponse httpResponse = HttpClient.request(
                mHttpRequestBuilder.setPostDataJsonArray(
                        mRequestFactory.createGetImsiPseudonymRequest(
                                mAkaTokenCache, eapAkaChallengeResponse)).build());

        GetImsiPseudonymResponse imsiPseudonymResponse =
                new GetImsiPseudonymResponse(httpResponse.body());
        int authResponseCode = imsiPseudonymResponse.getAuthResponseCode();
        switch (authResponseCode) {
            case Response.RESPONSE_CODE_REQUEST_SUCCESSFUL:
                // only save AKA token for full authentication
                if (!TextUtils.isEmpty(eapAkaChallengeResponse)
                        && !TextUtils.isEmpty(imsiPseudonymResponse.getAkaToken())) {
                    mAkaTokenCache = imsiPseudonymResponse.getAkaToken();
                }
                break;
            case Response.RESPONSE_CODE_AKA_CHALLENGE:
                if (mAkaTokenCache == null) {
                    throw new TransientException("Something is wrong in the server side.");
                }
                // clear AKA token to trigger full authentication next time
                mAkaTokenCache = null;
                // TODO(b/274167498): Optimize the handling of expired AKA token
                return getImsiPseudonym();
            case Response.RESPONSE_CODE_AKA_AUTH_FAILED:
                throw new NonTransientException("Authentication failed!");
            case Response.RESPONSE_CODE_INVALID_REQUEST:
                throw new NonTransientException("Invalid request!");
            case Response.RESPONSE_CODE_SERVER_ERROR:
                throw new TransientException("Server error!");
            default:
                throw new NonTransientException("Unknown error!");
        }

        int imsiPseudonymResponseCode = imsiPseudonymResponse.getGetImsiPseudonymResponseCode();
        switch (imsiPseudonymResponseCode) {
            case Response.RESPONSE_CODE_REQUEST_SUCCESSFUL:
                break;

            /*
             * As experience, server may respond 1004(RESPONSE_CODE_INVALID_REQUEST) if it detects
             * the secondary request not going to the same server with the first initial request,
             * retry to recover it.
             */
            case Response.RESPONSE_CODE_INVALID_REQUEST:
            case Response.RESPONSE_CODE_SERVER_ERROR:
            case Response.RESPONSE_CODE_3GPP_AUTH_ONGOING:
                throw new TransientException("Server transient problem! Response code is "
                        + imsiPseudonymResponseCode);

            case Response.RESPONSE_CODE_FORBIDDEN_REQUEST:
            case Response.RESPONSE_CODE_UNSUPPORTED_OPERATION:
            default:
                throw new NonTransientException("Something wrong when getting IMSI pseudonym! "
                        + "Response code is " + imsiPseudonymResponseCode);
        }
        return imsiPseudonymResponse.toPseudonymInfo(mImsi);
    }

    private String getAuthenticationChallenge()
            throws TransientException, NonTransientException, ServiceEntitlementException {
        HttpResponse httpResponse =
                HttpClient.request(
                        mHttpRequestBuilder.setPostDataJsonArray(
                                mRequestFactory.createAuthRequest()).build());
        ChallengeResponse challengeResponse = new ChallengeResponse(httpResponse.body());
        int authResponseCode = challengeResponse.getAuthResponseCode();
        switch (authResponseCode) {
            case Response.RESPONSE_CODE_AKA_CHALLENGE:
                break;

            /*
             * As experience, server may respond 1004(RESPONSE_CODE_INVALID_REQUEST) if it detects
             * the secondary request not going to the same server with the first initial request,
             * retry to recover it.
             */
            case Response.RESPONSE_CODE_INVALID_REQUEST:
            case Response.RESPONSE_CODE_SERVER_ERROR:
                throw new TransientException("Server transient problem! Response code is "
                        + authResponseCode);

            case Response.RESPONSE_CODE_AKA_AUTH_FAILED:
            case Response.RESPONSE_CODE_REQUEST_SUCCESSFUL:
            default:
                throw new NonTransientException(
                        "Something wrong when getting authentication challenge! authResponseCode="
                                + authResponseCode);
        }
        return challengeResponse.getEapAkaChallenge();
    }

    /**
     * Callback which will be called after OOB pseudonym retrieval.
     */
    public interface Callback {

        /**
         * Indicates an OOB pseudonym have been retrieved successfully.
         * @param carrierId The target carrier ID of the retrieved OOB pseudonym.
         * @param pseudonymInfo The retrieved OOB pseudonym info.
         */
        void onSuccess(int carrierId, PseudonymInfo pseudonymInfo);

        /**
         * Indicate a failure happens when to retrieve the OOB pseudonym.
         * @param carrierId The target carrier ID of the retrieval failure.
         * @param reasonCode The failure reason code
         * @param description The description of the failure.
         */
        void onFailure(int carrierId, @FailureReasonCode int reasonCode, String description);
    }
}

