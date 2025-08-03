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

import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;


import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.ExponentialBackoff;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.libraries.entitlement.ServiceEntitlementException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class query the entitlement server to receive values for satellite services and passes the
 * response to the {@link com.android.internal.telephony.satellite.SatelliteController}.
 * @hide
 */
public class SatelliteEntitlementController extends Handler {
    private static final String TAG = "SatelliteEntitlementController";
    @NonNull private static SatelliteEntitlementController sInstance;
    /** Message code used in handleMessage() */
    private static final int CMD_START_QUERY_ENTITLEMENT = 1;
    private static final int CMD_RETRY_QUERY_ENTITLEMENT = 2;
    private static final int CMD_STOP_RETRY_QUERY_ENTITLEMENT = 3;

    /** Retry on next trigger event. */
    private static final int HTTP_RESPONSE_500 = 500;
    /** Retry after the time specified in the “Retry-After” header. After retry count doesn't exceed
     * MAX_RETRY_COUNT. */
    private static final int HTTP_RESPONSE_503 = 503;
    /** Default query refresh time is 1 month. */

    private static final int DEFAULT_QUERY_REFRESH_DAYS = 30;
    private static final long INITIAL_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(10); // 10 min
    private static final long MAX_DELAY_MILLIS = TimeUnit.DAYS.toMillis(5); // 5 days
    private static final int MULTIPLIER = 2;
    private static final int MAX_RETRY_COUNT = 5;
    @NonNull private final SubscriptionManagerService mSubscriptionManagerService;
    @NonNull private final CarrierConfigManager mCarrierConfigManager;
    @NonNull private final CarrierConfigManager.CarrierConfigChangeListener
            mCarrierConfigChangeListener;
    @NonNull private final ConnectivityManager mConnectivityManager;
    @NonNull private final ConnectivityManager.NetworkCallback mNetworkCallback;
    @NonNull private final BroadcastReceiver mReceiver;
    @NonNull private final Context mContext;
    private final Object mLock = new Object();
    /** Map key : subId, value : ExponentialBackoff. */
    private Map<Integer, ExponentialBackoff> mExponentialBackoffPerSub = new HashMap<>();
    /** Map key : subId, value : SatelliteEntitlementResult. */
    private Map<Integer, SatelliteEntitlementResult> mSatelliteEntitlementResultPerSub =
            new HashMap<>();
    /** Map key : subId, value : the last query time to millis. */
    private Map<Integer, Long> mLastQueryTimePerSub = new HashMap<>();
    /** Map key : subId, value : Count the number of retries caused by the 'ExponentialBackoff' and
     * '503 error case with the Retry-After header'. */
    private Map<Integer, Integer> mRetryCountPerSub = new HashMap<>();

    /**
     * Create the SatelliteEntitlementController singleton instance.
     * @param context      The Context to use to create the SatelliteEntitlementController.
     * @param featureFlags The feature flag.
     */
    public static void make(@NonNull Context context, @NonNull FeatureFlags featureFlags) {
        if (!featureFlags.carrierEnabledSatelliteFlag()) {
            logd("carrierEnabledSatelliteFlag is disabled. don't created this.");
            return;
        }
        if (sInstance == null) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            sInstance =
                    new SatelliteEntitlementController(context, handlerThread.getLooper());
        }
    }

    /**
     * Create a SatelliteEntitlementController to request query to the entitlement server for
     * satellite services and receive responses.
     *
     * @param context      The Context for the SatelliteEntitlementController.
     * @param looper       The looper for the handler. It does not run on main thread.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public SatelliteEntitlementController(@NonNull Context context, @NonNull Looper looper) {
        super(looper);
        mContext = context;
        mSubscriptionManagerService = SubscriptionManagerService.getInstance();
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mCarrierConfigChangeListener = (slotIndex, subId, carrierId, specificCarrierId) ->
                handleCarrierConfigChanged(slotIndex, subId, carrierId, specificCarrierId);
        mCarrierConfigManager.registerCarrierConfigChangeListener(this::post,
                mCarrierConfigChangeListener);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handleInternetConnected();
            }

            @Override
            public void onLost(Network network) {
                handleInternetDisconnected();
            }
        };
        NetworkRequest networkrequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        mConnectivityManager.registerNetworkCallback(networkrequest, mNetworkCallback, this);
        mReceiver = new SatelliteEntitlementControllerReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CMD_START_QUERY_ENTITLEMENT:
                handleCmdStartQueryEntitlement();
                break;
            case CMD_RETRY_QUERY_ENTITLEMENT:
                handleCmdRetryQueryEntitlement(msg.arg1);
                break;
            case CMD_STOP_RETRY_QUERY_ENTITLEMENT:
                stopExponentialBackoff(msg.arg1);
                break;
            default:
                logd("do not used this message");
        }
    }

    private void handleCarrierConfigChanged(int slotIndex, int subId, int carrierId,
            int specificCarrierId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }
        logd("handleCarrierConfigChanged(): slotIndex(" + slotIndex + "), subId("
                + subId + "), carrierId(" + carrierId + "), specificCarrierId("
                + specificCarrierId + ")");

        sendEmptyMessage(CMD_START_QUERY_ENTITLEMENT);
    }

    private class SatelliteEntitlementControllerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                handleAirplaneModeChange(airplaneMode);
            }
        }
    }

    private void handleAirplaneModeChange(boolean airplaneMode) {
        if (!airplaneMode) {
            resetEntitlementQueryCounts(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        }
    }

    private boolean isInternetConnected() {
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities =
                mConnectivityManager.getNetworkCapabilities(activeNetwork);
        // TODO b/319780796 Add checking if it is not a satellite.
        return networkCapabilities != null
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void handleInternetConnected() {
        sendEmptyMessage(CMD_START_QUERY_ENTITLEMENT);
    }

    private void handleInternetDisconnected() {
        mExponentialBackoffPerSub.forEach((key, value) -> {
            Message message = obtainMessage();
            message.what = CMD_STOP_RETRY_QUERY_ENTITLEMENT;
            message.arg1 = key;
            sendMessage(message);
        });
    }

    /**
     * Check if the device can request to entitlement server (if there is an internet connection and
     * if the throttle time has passed since the last request), and then pass the response to
     * SatelliteController if the response is received.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void handleCmdStartQueryEntitlement() {
        if (!isInternetConnected()) {
            logd("Internet disconnected");
            return;
        }

        for (int subId : mSubscriptionManagerService.getActiveSubIdList(true)) {
            if (!shouldQueryEntitlement(subId)) {
                return;
            }

            // Check the satellite service query result from the entitlement server for the
            // satellite service.
            try {
                mSatelliteEntitlementResultPerSub.remove(subId);
                mSatelliteEntitlementResultPerSub.put(subId, getSatelliteEntitlementApi(
                        subId).checkEntitlementStatus());
            } catch (ServiceEntitlementException e) {
                loge(e.toString());
                if (!isInternetConnected()) {
                    logd("handleCmdStartQueryEntitlement: disconnected. " + e);
                    return;
                }
                if (shouldHandleErrorResponse(e, subId)) {
                    logd("handleCmdStartQueryEntitlement: handle response.");
                    return;
                }
                startExponentialBackoff(subId);
                return;
            }
            queryCompleted(subId);
        }
    }

    /** When airplane mode changes from on to off, reset the values required to start the first
     * query. */
    private void resetEntitlementQueryCounts(String event) {
        logd("resetEntitlementQueryCounts: " + event);
        mLastQueryTimePerSub = new HashMap<>();
        mExponentialBackoffPerSub = new HashMap<>();
        mRetryCountPerSub = new HashMap<>();
    }

    /**
     * If the HTTP response does not receive a body containing the 200 ok with sat mode
     * configuration,
     *
     * 1. If the 500 response received, then no more retry until next event occurred.
     * 2. If the 503 response with Retry-After header received, then the query is retried until
     * MAX_RETRY_COUNT.
     * 3. If other response or exception is occurred, then the query is retried until
     * MAX_RETRY_COUNT is reached using the ExponentialBackoff.
     */
    private void handleCmdRetryQueryEntitlement(int subId) {
        logd("handleCmdRetryQueryEntitlement: " + subId);
        try {
            synchronized (mLock) {
                mSatelliteEntitlementResultPerSub.put(subId, getSatelliteEntitlementApi(
                        subId).checkEntitlementStatus());
            }
        } catch (ServiceEntitlementException e) {
            if (!isInternetConnected()) {
                logd("retryQuery: Internet disconnected. reset the retry and after the "
                        + "internet is connected then the first query is triggered." + e);
                stopExponentialBackoff(subId);
                return;
            }
            if (shouldHandleErrorResponse(e, subId)) {
                logd("retryQuery: handle response.");
                stopExponentialBackoff(subId);
                return;
            }
            mExponentialBackoffPerSub.get(subId).notifyFailed();
            mRetryCountPerSub.put(subId,
                    mRetryCountPerSub.getOrDefault(subId, 0) + 1);
            logd("handleCmdRetryQueryEntitlement:" + e + "[" + subId + "] cnt="
                    + mRetryCountPerSub.getOrDefault(subId, 0) + "] Retrying in "
                    + mExponentialBackoffPerSub.get(subId).getCurrentDelay() + " ms.");
        }
    }

    /** Only handle '500' and '503 with retry-after header' error responses received.
     * If the 500 response is received, no retry until the next trigger event occurs.
     * If the 503 response with Retry-After header, retry is attempted according to the value in the
     * Retry-After header up to MAX_RETRY_COUNT.
     * In other cases, it performs an exponential backoff process. */
    private boolean shouldHandleErrorResponse(ServiceEntitlementException e, int subId) {
        int responseCode = e.getHttpStatus();
        logd("shouldHandleErrorResponse: received the " + responseCode);
        if (responseCode == HTTP_RESPONSE_503 && e.getRetryAfter() != null
                && !e.getRetryAfter().isEmpty()) {
            if (mRetryCountPerSub.getOrDefault(subId, 0) >= MAX_RETRY_COUNT) {
                logd("The 503 retry after reaching the " + MAX_RETRY_COUNT
                        + "The retry will not be attempted until the next trigger event.");
                queryCompleted(subId);
                return true;
            }
            long retryAfterSeconds = parseSecondsFromRetryAfter(e.getRetryAfter());
            if (retryAfterSeconds == -1) {
                logd("Unable parsing the retry-after. try to exponential backoff.");
                return false;
            }
            mRetryCountPerSub.put(subId, mRetryCountPerSub.getOrDefault(subId, 0) + 1);
            logd("[" + subId + "] cnt=" + mRetryCountPerSub.getOrDefault(subId, 0)
                    + " Retrying in " + TimeUnit.SECONDS.toMillis(retryAfterSeconds) + " sec");
            Message message = obtainMessage();
            message.what = CMD_RETRY_QUERY_ENTITLEMENT;
            message.arg1 = subId;
            sendMessageDelayed(message, TimeUnit.SECONDS.toMillis(retryAfterSeconds));
            return true;
        } else if (responseCode == HTTP_RESPONSE_500) {
            logd("The retry on the next trigger event.");
            queryCompleted(subId);
            return true;
        }
        return false;
    }

    /** Parse the HTTP-date or a number of seconds in the retry-after value. */
    private long parseSecondsFromRetryAfter(String retryAfter) {
        try {
            return Long.parseLong(retryAfter);
        } catch (NumberFormatException numberFormatException) {
        }

        try {
            return SECONDS.between(
                    Instant.now(), RFC_1123_DATE_TIME.parse(retryAfter, Instant::from));
        } catch (DateTimeParseException dateTimeParseException) {
        }

        return -1;
    }

    private void startExponentialBackoff(int subId) {
        stopExponentialBackoff(subId);
        mExponentialBackoffPerSub.put(subId,
                new ExponentialBackoff(INITIAL_DELAY_MILLIS, MAX_DELAY_MILLIS,
                        MULTIPLIER, this.getLooper(), () -> {
                    synchronized (mLock) {
                        if (mSatelliteEntitlementResultPerSub.containsKey(subId)) {
                            logd("handleCmdStartQueryEntitlement: get the response "
                                    + "successfully.");
                            mExponentialBackoffPerSub.get(subId).stop();
                            queryCompleted(subId);
                            return;
                        }

                        if (mRetryCountPerSub.getOrDefault(subId, 0) >= MAX_RETRY_COUNT) {
                            logd("The ExponentialBackoff is  stopped after reaching the "
                                    + MAX_RETRY_COUNT + ". The retry don't attempted until the"
                                    + " refresh time expires.");
                            mExponentialBackoffPerSub.get(subId).stop();
                            queryCompleted(subId);
                            return;
                        }
                        if (!mSatelliteEntitlementResultPerSub.containsKey(subId)) {
                            handleCmdRetryQueryEntitlement(subId);
                        }
                    }
                }));
        mExponentialBackoffPerSub.get(subId).start();
        mRetryCountPerSub.put(subId, mRetryCountPerSub.getOrDefault(subId, 0) + 1);
        logd("start ExponentialBackoff [" + mRetryCountPerSub.getOrDefault(subId, 0)
                + "] Retrying in " + mExponentialBackoffPerSub.get(subId).getCurrentDelay()
                + " ms.");
    }

    /** If the Internet connection is lost during the ExponentialBackoff, stop the
     * ExponentialBackoff and reset it. */
    private void stopExponentialBackoff(int subId) {
        if (isExponentialBackoffInProgress(subId)) {
            logd("stopExponentialBackoff: reset ExponentialBackoff");
            mExponentialBackoffPerSub.get(subId).stop();
            mExponentialBackoffPerSub.remove(subId);
        }
    }

    /**
     * No more query retry, update the result. If there is no response from the server, then used
     * the default value - 'satellite disabled' and empty 'PLMN allowed list'.
     * And then it send a delayed message to trigger the query again after A refresh day has passed.
     */
    private void queryCompleted(int subId) {
        if (!mSatelliteEntitlementResultPerSub.containsKey(subId)) {
            logd("queryCompleted: create default SatelliteEntitlementResult");
            mSatelliteEntitlementResultPerSub.put(subId,
                    SatelliteEntitlementResult.getDefaultResult());
        }

        saveLastQueryTime(subId);
        Message message = obtainMessage();
        message.what = CMD_START_QUERY_ENTITLEMENT;
        message.arg1 = subId;
        sendMessageDelayed(message, TimeUnit.DAYS.toMillis(
                getSatelliteEntitlementStatusRefreshDays(subId)));
        logd("queryCompleted: updateSatelliteEntitlementStatus");
        updateSatelliteEntitlementStatus(subId,
                mSatelliteEntitlementResultPerSub.get(subId).getEntitlementStatus()
                        == SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                mSatelliteEntitlementResultPerSub.get(subId).getAllowedPLMNList());
        stopExponentialBackoff(subId);
        mRetryCountPerSub.remove(subId);
    }

    /** Check whether there is a saved subId. Returns true if there is a saved subId,
     * otherwise return false.*/
    private boolean isExponentialBackoffInProgress(int subId) {
        return mExponentialBackoffPerSub.containsKey(subId);
    }

    /**
     * Check if the subId can query the entitlement server to get the satellite configuration.
     */
    private boolean shouldQueryEntitlement(int subId) {
        if (!isSatelliteEntitlementSupported(subId)) {
            logd("Doesn't support entitlement query for satellite.");
            return false;
        }

        if (isExponentialBackoffInProgress(subId)) {
            logd("In progress ExponentialBackoff.");
            return false;
        }

        return shouldRefreshEntitlementStatus(subId);
    }

    /**
     * Compare the last query time to the refresh time from the CarrierConfig to see if the device
     * can query the entitlement server.
     */
    private boolean shouldRefreshEntitlementStatus(int subId) {
        long lastQueryTimeMillis = getLastQueryTime(subId);
        long refreshTimeMillis = TimeUnit.DAYS.toMillis(
                getSatelliteEntitlementStatusRefreshDays(subId));
        boolean isAvailable =
                (System.currentTimeMillis() - lastQueryTimeMillis) > refreshTimeMillis;
        if (!isAvailable) {
            logd("query is already done. can query after " + Instant.ofEpochMilli(
                    refreshTimeMillis + lastQueryTimeMillis));
        }
        return isAvailable;
    }

    /**
     * Get the SatelliteEntitlementApi.
     *
     * @param subId The subId of the subscription for creating SatelliteEntitlementApi
     * @return A new SatelliteEntitlementApi object.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public SatelliteEntitlementApi getSatelliteEntitlementApi(int subId) {
        return new SatelliteEntitlementApi(mContext, getConfigForSubId(subId), subId);
    }

    /** If there is a value stored in the cache, it is used. If there is no value stored in the
     * cache, it is considered the first query. */
    private long getLastQueryTime(int subId) {
        return mLastQueryTimePerSub.getOrDefault(subId, 0L);
    }

    /** Return the satellite entitlement status refresh days from carrier config. */
    private int getSatelliteEntitlementStatusRefreshDays(int subId) {
        return getConfigForSubId(subId).getInt(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                DEFAULT_QUERY_REFRESH_DAYS);
    }

    /** Return the satellite entitlement supported bool from carrier config. */
    private boolean isSatelliteEntitlementSupported(int subId) {
        return getConfigForSubId(subId).getBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL);
    }

    @NonNull
    private PersistableBundle getConfigForSubId(int subId) {
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId,
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL);
        if (config == null || config.isEmpty()) {
            config = CarrierConfigManager.getDefaultConfig();
        }
        return config;
    }

    private void saveLastQueryTime(int subId) {
        long lastQueryTimeMillis = System.currentTimeMillis();
        mLastQueryTimePerSub.put(subId, lastQueryTimeMillis);
    }

    /**
     * Send to satelliteController for update the satellite service enabled or not and plmn Allowed
     * list.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void updateSatelliteEntitlementStatus(int subId, boolean enabled,
            List<String> plmnAllowedList) {
        SatelliteController.getInstance().onSatelliteEntitlementStatusUpdated(subId, enabled,
                plmnAllowedList, null);
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(String log) {
        Rlog.e(TAG, log);
    }
}
