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
package com.android.telephony.qns.wfc;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;

import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.qns.R;

import java.util.concurrent.Executor;

/** A class with helper methods for WfcActivationCanadaActivity */
public class WfcActivationHelper {
    private static final String TAG = WfcActivationActivity.TAG;

    @VisibleForTesting static final int PRE_EPDG_CONNECTION_DELAY_MS = 1000; // 1 second

    // Enums for Wi-Fi check result
    public static final int WIFI_CONNECTION_SUCCESS = 0;
    public static final int WIFI_CONNECTION_ERROR = 1;

    // Enums for ePDG connection result
    public static final int EPDG_CONNECTION_SUCCESS = 0;
    public static final int EPDG_CONNECTION_ERROR = 1;

    // Event IDs for ePDG connection
    @VisibleForTesting static final int EVENT_PRE_START_ATTEMPT = 0;
    @VisibleForTesting static final int EVENT_START_ATTEMPT = 1;
    @VisibleForTesting static final int EVENT_FINISH_ATTEMPT = 2;
    private static final int EVENT_RESULT_SUCCESS = 3;
    private static final int EVENT_TIMEOUT = 4;
    private static final int EVENT_RESULT_FAILURE_IKEV2 = 5;
    private static final int EVENT_RESULT_FAILURE_OTHER = 6;

    public static final String ACTION_TRY_WFC_CONNECTION =
            "com.android.qns.wfcactivation.TRY_WFC_CONNECTION";
    public static final String EXTRA_SUB_ID = "SUB_ID";
    public static final String EXTRA_TRY_STATUS = "TRY_STATUS";
    public static final int STATUS_START = 1;
    public static final int STATUS_END = 2;

    // Dependencies
    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final ImsMmTelManager mImsMmTelManager;
    private final WfcCarrierConfigManager mWfcConfigManager;

    private final int mSubId;
    private final Executor mBackgroundExecutor;

    public WfcActivationHelper(Context context, int subId) {
        this(
                context,
                subId,
                context.getSystemService(ConnectivityManager.class),
                WfcUtils.getImsMmTelManager(subId),
                new WfcCarrierConfigManager(context.getApplicationContext(), subId),
                        THREAD_POOL_EXECUTOR);
    }

    @VisibleForTesting
    WfcActivationHelper(
            Context context,
            int subId,
            ConnectivityManager cm,
            @Nullable ImsMmTelManager imsMmTelManager,
            WfcCarrierConfigManager wfcConfigManager,
            Executor backgroundExecutor) {
        mContext = context;
        mSubId = subId;
        mConnectivityManager = cm;
        mImsMmTelManager = imsMmTelManager;
        mWfcConfigManager = wfcConfigManager;
        mBackgroundExecutor = backgroundExecutor;
        mWfcConfigManager.loadConfigurations();
    }

    /**
     * Check WiFi connection
     *
     * @param msg The Message to be send with arg1 = result. Result is one of WIFI_CONNECTION_*.
     */
    public void checkWiFi(Message msg) {
        msg.arg1 = checkWiFiAvailability() ? WIFI_CONNECTION_SUCCESS : WIFI_CONNECTION_ERROR;
        msg.sendToTarget();
    }

    private boolean checkWiFiAvailability() {
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null
                && activeNetwork.isConnected()
                && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void notifyQnsServiceToSetWfcMode(int status) {
        String qnsPackage = mContext.getResources().getString(R.string.qns_package);
        Intent intent = new Intent(ACTION_TRY_WFC_CONNECTION);
        intent.putExtra(EXTRA_SUB_ID, mSubId);
        intent.putExtra(EXTRA_TRY_STATUS, status);
        intent.setPackage(qnsPackage);
        Log.d(TAG, "notify QNS: subId =" + mSubId + ", status =" + status);
        mContext.sendBroadcast(intent);
    }

    // This class is a effectively a one-way state machine that cannot be reset & reused. Each call
    // of tryEpdgConnectionOverWiFi() creates a new instance of this class.
    private class EpdgConnectHandler extends Handler {
        final ImsCallback imsCallback;
        final Message result;
        boolean imsCallbackRegistered;
        boolean waitingForResult; // ImsCallback wil be no-op when this is false

        EpdgConnectHandler(Looper looper, Message result) {
            super(looper);
            imsCallback = new ImsCallback(this);
            this.result = result;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_PRE_START_ATTEMPT:
                    // The callback must be registered before triggering ePDG connection, because
                    // the very 1st firing of the callback after registering MAY be the last IMS
                    // state.
                    // We assume 1 second is enough for that 1st firing.
                    // This means adding 1s delay to WFC activation flow in all cases, and it should
                    // be fine, given this can only be triggered by user manually and is not
                    // expected to be fast.
                    waitingForResult = false;
                    registerImsRegistrationCallback();
                    // Populate arg1 to EVENT_START_ATTEMPT message
                    sendMessageDelayed(
                            obtainMessage(EVENT_START_ATTEMPT, msg.arg1, 0),
                            /* delayMillis= */ msg.arg2);
                    break;

                case EVENT_START_ATTEMPT:
                    Log.d(TAG, "Try to setup ePDG connection over WiFi");
                    waitingForResult = true;

                    mBackgroundExecutor.execute(
                            () -> {
                                // WFC: on; WFC preference: WiFi preferred (2)
                                mImsMmTelManager.setVoWiFiNonPersistent(true, 2);
                                // notify IMS to program WFC on and WFC mode as Wi-Fi Preferred
                                notifyQnsServiceToSetWfcMode(STATUS_START);
                            });

                    // Timeout event
                    Log.d(TAG, "Will timeout after " + msg.arg1 + " ms");
                    sendEmptyMessageDelayed(EVENT_TIMEOUT, /* delayMillis= */ msg.arg1);
                    break;

                case EVENT_TIMEOUT:
                    Log.d(TAG, "Timeout: IKEV2 Auth failure not received.");
                    if (getTimeoutResult() == EPDG_CONNECTION_SUCCESS) {
                        sendEmptyMessage(EVENT_RESULT_SUCCESS);
                    } else {
                        sendEmptyMessage(EVENT_RESULT_FAILURE_IKEV2);
                    }
                    break;

                case EVENT_RESULT_SUCCESS:
                    result.arg1 = EPDG_CONNECTION_SUCCESS;
                    // Clean up and send result
                    sendEmptyMessage(EVENT_FINISH_ATTEMPT);
                    break;

                case EVENT_RESULT_FAILURE_IKEV2:
                    Log.d(TAG, "Turn off WFC");
                    // WFC: off; WFC preference: cellular preferred (1)
                    mBackgroundExecutor.execute(
                            () -> mImsMmTelManager.setVoWiFiNonPersistent(false, 1));
                    // Set result: failure
                    result.arg1 = EPDG_CONNECTION_ERROR;
                    // Clean up and send result
                    sendEmptyMessage(EVENT_FINISH_ATTEMPT);
                    break;

                case EVENT_FINISH_ATTEMPT:
                    waitingForResult = false;
                    // Remove timeout event - if we get here via EVENT_TIMEOUT, this do nothing.
                    removeMessages(EVENT_TIMEOUT);
                    // Unregister mImsCallback
                    unregisterImsRegistrationCallback();
                    mBackgroundExecutor.execute(
                            () -> {
                                // Turn on WFC if success. W/o this, WFC could be turned
                                // ON (by STATUS_START) - OFF (by STATUS_END) - ON (by Settings app)
                                // which causes unnecessary IMS registration traffic.
                                // This must be done before sending STATUS_END so vendor IMS will
                                // see DB value ON.
                                if (result.arg1 == EPDG_CONNECTION_SUCCESS) {
                                    Log.d(TAG, "Turn on WFC");
                                    mImsMmTelManager.setVoWiFiSettingEnabled(true);
                                }
                                // Notify IMS to revert WFC on/off and mode to follow user settings.
                                // Notify here to make sure all cases (success, failure, timeout)
                                // reach this line.
                                notifyQnsServiceToSetWfcMode(STATUS_END);
                                // Send result
                                result.sendToTarget();
                            });
                    break;

                case EVENT_RESULT_FAILURE_OTHER:
                    break;
                default: // Do nothing
            }
        }

        private void registerImsRegistrationCallback() {
            try {
                Log.d(TAG, "registerImsRegistrationCallback");
                mImsMmTelManager.registerImsRegistrationCallback(this::post, imsCallback);
                imsCallbackRegistered = true;
            } catch (ImsException | RuntimeException e) {
                Log.e(TAG, "registerImsRegistrationCallback failed", e);
                // Fail silently to trigger timeout
                imsCallbackRegistered = false;
            }
        }

        private void unregisterImsRegistrationCallback() {
            if (!imsCallbackRegistered) {
                return;
            }

            try {
                Log.d(TAG, "unregisterImsRegistrationCallback");
                mImsMmTelManager.unregisterImsRegistrationCallback(imsCallback);
                imsCallbackRegistered = false;
            } catch (RuntimeException e) {
                Log.e(TAG, "unregisterImsRegistrationCallback failed", e);
            }
        }
    }

    /**
     * Try to setup ePDG connection over WiFi.
     *
     * @param msg The Message to be send with arg1 = result. Result is one of EPDG_CONNECTION_*.
     * @param timeoutMs Timeout, in milliseconds, then abort waiting for ePDG connection result.
     */
    public void tryEpdgConnectionOverWiFi(Message msg, int timeoutMs) {
        if (mImsMmTelManager == null) {
            // Send message with EPDG_CONNECTION_ERROR immediately.
            Log.e(TAG, "ImsMmTelManager is null");
            msg.arg1 = EPDG_CONNECTION_ERROR;
            msg.sendToTarget();
            return;
        }

        // NOTE: This private handler is hosted on the same looper as msg.
        EpdgConnectHandler handler = new EpdgConnectHandler(msg.getTarget().getLooper(), msg);
        // Start attempt of ePDG connection.
        handler.obtainMessage(EVENT_PRE_START_ATTEMPT, timeoutMs, PRE_EPDG_CONNECTION_DELAY_MS)
                .sendToTarget();
    }

    @VisibleForTesting
    static class ImsCallback extends ImsMmTelManager.RegistrationCallback {
        private final EpdgConnectHandler handler;

        ImsCallback(EpdgConnectHandler handler) {
            this.handler = handler;
        }

        @Override
        public void onRegistered(int imsTransportType) {
            if (!handler.waitingForResult) {
                return;
            }
            if (imsTransportType != AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                return;
            }
            Log.d(TAG, "IMS connected on WLAN.");
            handler.sendEmptyMessage(EVENT_RESULT_SUCCESS);
        }

        @Override
        public void onUnregistered(ImsReasonInfo imsReasonInfo) {
            if (!handler.waitingForResult) {
                return;
            }
            Log.d(TAG, "IMS disconnected: " + imsReasonInfo);
            if (isIkev2AuthFailure(imsReasonInfo)) {
                handler.sendEmptyMessage(EVENT_RESULT_FAILURE_IKEV2);
            } else {
                handler.obtainMessage(
                                EVENT_RESULT_FAILURE_OTHER,
                                imsReasonInfo.getCode(),
                                imsReasonInfo.getExtraCode())
                        .sendToTarget();
            }
        }

        @Override
        public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo imsReasonInfo) {
            if (!handler.waitingForResult) {
                return;
            }
            if (imsTransportType != AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                return;
            }
            Log.d(TAG, "IMS registration failed on WLAN: " + imsReasonInfo);
            if (isIkev2AuthFailure(imsReasonInfo)) {
                handler.sendEmptyMessage(EVENT_RESULT_FAILURE_IKEV2);
            } else {
                handler.obtainMessage(
                                EVENT_RESULT_FAILURE_OTHER,
                                imsReasonInfo.getCode(),
                                imsReasonInfo.getExtraCode())
                        .sendToTarget();
            }
        }
    }

    static boolean isIkev2AuthFailure(ImsReasonInfo imsReasonInfo) {
        if (imsReasonInfo.getCode() == ImsReasonInfo.CODE_EPDG_TUNNEL_ESTABLISH_FAILURE) {
            if (imsReasonInfo.getExtraCode() == ImsReasonInfo.CODE_IKEV2_AUTH_FAILURE) {
                return true;
            }
        }
        return false;
    }

    private int getTimeoutResult() {
        return mWfcConfigManager.isShowVowifiPortalAfterTimeout()
                ? EPDG_CONNECTION_ERROR
                : EPDG_CONNECTION_SUCCESS;
    }

    public String getWebPortalUrl() {
        return mWfcConfigManager.getVowifiEntitlementServerUrl();
    }

    public int getVowifiRegistrationTimerForVowifiActivation() {
        return mWfcConfigManager.getVowifiRegistrationTimerForVowifiActivation();
    }

    public boolean supportJsCallbackForVowifiPortal() {
        return mWfcConfigManager.supportJsCallbackForVowifiPortal();
    }
}
