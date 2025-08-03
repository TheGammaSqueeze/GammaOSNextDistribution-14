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

package com.android.server.wifi;

import static android.net.wifi.WifiManager.WIFI_FEATURE_MBO;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OCE;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.server.wifi.SupplicantStaIfaceHal.MboAssocDisallowedReasonCode;

/**
 * MboOceController is responsible for controlling MBO and OCE operations.
 */
public class MboOceController {
    private static final String TAG = "MboOceController";

    /** State of MBO/OCE module. */
    private boolean mEnabled = false;
    private boolean mIsMboSupported = false;
    private boolean mIsOceSupported = false;
    private boolean mVerboseLoggingEnabled = false;

    private final TelephonyManager mTelephonyManager;
    private final ActiveModeWarden mActiveModeWarden;
    private final WifiThreadRunner mWifiThreadRunner;

    /**
     * Create new instance of MboOceController.
     */
    public MboOceController(TelephonyManager telephonyManager, ActiveModeWarden activeModeWarden,
            WifiThreadRunner wifiThreadRunner) {
        mTelephonyManager = telephonyManager;
        mActiveModeWarden = activeModeWarden;
        mWifiThreadRunner = wifiThreadRunner;
    }

    /**
     * Enable MBO and OCE functionality.
     */
    public void enable() {
        ClientModeManager clientModeManager =
                mActiveModeWarden.getPrimaryClientModeManagerNullable();
        if (clientModeManager == null) {
            return;
        }
        long supportedFeatures = clientModeManager.getSupportedFeatures();
        mIsMboSupported = (supportedFeatures & WIFI_FEATURE_MBO) != 0;
        mIsOceSupported = (supportedFeatures & WIFI_FEATURE_OCE) != 0;
        mEnabled = true;
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Enable MBO-OCE MBO support: " + mIsMboSupported
                    + " OCE support: " + mIsOceSupported);
        }
        if (mIsMboSupported) {
            // Register for data connection state change events (Cellular).
            mTelephonyManager.listen(mDataConnectionStateListener,
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
        }
    }

    /**
     * Disable MBO and OCE functionality.
     */
    public void disable() {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Disable MBO-OCE");
        }
        if (mIsMboSupported) {
            // Un-register for data connection state change events (Cellular).
            mTelephonyManager.listen(mDataConnectionStateListener, PhoneStateListener.LISTEN_NONE);
        }
        mEnabled = false;
    }

    /**
     * Enable/Disable verbose logging.
     *
     * @param verbose true to enable and false to disable.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Listen for changes to the data connection state (Cellular).
     */
    private PhoneStateListener mDataConnectionStateListener = new PhoneStateListener(){
        public void onDataConnectionStateChanged(int state, int networkType) {
            mWifiThreadRunner.post(
                    () -> {
                        boolean dataAvailable;
                        ClientModeManager clientModeManager =
                                mActiveModeWarden.getPrimaryClientModeManagerNullable();
                        if (clientModeManager == null) {
                            return;
                        }
                        if (!mEnabled) {
                            Log.e(TAG, "onDataConnectionStateChanged called when MBO is "
                                    + "disabled!!");
                            return;
                        }
                        if (state == TelephonyManager.DATA_CONNECTED) {
                            dataAvailable = true;
                        } else if (state == TelephonyManager.DATA_DISCONNECTED) {
                            dataAvailable = false;
                        } else {
                            Log.e(TAG, "onDataConnectionStateChanged unexpected State: " + state);
                            return;
                        }
                        if (mVerboseLoggingEnabled) {
                            Log.d(TAG, "Cell Data: " + dataAvailable);
                        }
                        clientModeManager.setMboCellularDataStatus(dataAvailable);
                    }
            );
        }
    };

    /**
     * BtmFrameData carries the data retried from received BTM
     * request frame handled in supplicant.
     */
    public static class BtmFrameData {
        public @MboOceConstants.BtmResponseStatus int mStatus =
                MboOceConstants.BTM_RESPONSE_STATUS_INVALID;
        public int mBssTmDataFlagsMask = 0;
        public long mBlockListDurationMs = 0;
        public @MboOceConstants.MboTransitionReason int mTransitionReason =
                MboOceConstants.MBO_TRANSITION_REASON_INVALID;
        public @MboOceConstants.MboCellularDataConnectionPreference int mCellPreference =
                MboOceConstants.MBO_CELLULAR_DATA_CONNECTION_INVALID;

        @Override
        public String toString() {
            return new StringBuilder("BtmFrameData status=").append(mStatus).append(
                    ", flags=").append(mBssTmDataFlagsMask).append(
                    ", assocRetryDelay=").append(mBlockListDurationMs).append(
                    ", transitionReason=").append(mTransitionReason).append(
                    ", cellPref=").append(mCellPreference).toString();
        }
    }

    /**
     * OceRssiBasedAssocRejectAttr is extracted from (Re-)Association response frame from an OCE AP
     * to indicate that the AP has rejected the (Re-)Association request on the basis of
     * insufficient RSSI.
     * Refer OCE spec v1.0 section 4.2.2 Table 7.
     */
    public static class OceRssiBasedAssocRejectAttr {
        /*
         * Delta RSSI - The difference in dB between the minimum RSSI at which
         * the AP would accept a (Re-)Association request from the STA before
         * Retry Delay expires and the AP's measurement of the RSSI at which the
         * (Re-)Association request was received.
         */
        public int mDeltaRssi;
        /*
         * Retry Delay - The time period in seconds for which the AP will not
         * accept any subsequent (Re-)Association requests from the STA, unless
         * the received RSSI has improved by Delta RSSI.
         */
        public int mRetryDelayS;

        public OceRssiBasedAssocRejectAttr(int deltaRssi, int retryDelayS) {
            this.mDeltaRssi = deltaRssi;
            this.mRetryDelayS = retryDelayS;
        }

        @Override
        public String toString() {
            return new StringBuilder("OceRssiBasedAssocRejectAttr Delta Rssi=")
                    .append(mDeltaRssi).append(
                    ", Retry Delay=").append(mRetryDelayS).toString();
        }
    }

    /**
     * MboAssocDisallowedAttr is extracted from (Re-)Association response frame from the MBO AP
     * to indicate that the AP is not accepting new associations.
     * Refer MBO spec v1.2 section 4.2.4 Table 13 for the details of reason code.
     */
    public static class MboAssocDisallowedAttr {
        /*
         * Reason Code - The reason why the AP is not accepting new
         * associations.
         */
        public @MboOceConstants.MboAssocDisallowedReasonCode int mReasonCode;

        public MboAssocDisallowedAttr(int reasonCode) {
            mReasonCode = halToFrameworkMboAssocRDisallowedReasonCode(reasonCode);
        }

        @Override
        public String toString() {
            return new StringBuilder("MboAssocDisallowedAttr Reason code=")
                    .append(mReasonCode).toString();
        }

        private @MboOceConstants.MboAssocDisallowedReasonCode int
                halToFrameworkMboAssocRDisallowedReasonCode(int reasonCode) {
            switch (reasonCode) {
                case MboAssocDisallowedReasonCode.RESERVED:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_RESERVED_0;
                case MboAssocDisallowedReasonCode.UNSPECIFIED:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_UNSPECIFIED;
                case MboAssocDisallowedReasonCode.MAX_NUM_STA_ASSOCIATED:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_MAX_NUM_STA_ASSOCIATED;
                case MboAssocDisallowedReasonCode.AIR_INTERFACE_OVERLOADED:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_AIR_INTERFACE_OVERLOADED;
                case MboAssocDisallowedReasonCode.AUTH_SERVER_OVERLOADED:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_AUTH_SERVER_OVERLOADED;
                case MboAssocDisallowedReasonCode.INSUFFICIENT_RSSI:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_INSUFFICIENT_RSSI;
                default:
                    return MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_RESERVED;
            }
        }
    }
}
