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

package com.android.ons;

import android.annotation.TestApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.flags.Flags;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @class ONSProfileActivator
 * @brief ONSProfileActivator makes sure that the CBRS profile is downloaded, activated and grouped
 * when an opportunistic data enabled pSIM is inserted.
 */
public class ONSProfileActivator implements ONSProfileConfigurator.ONSProfConfigListener,
        ONSProfileDownloader.IONSProfileDownloaderListener {

    private static final String TAG = ONSProfileActivator.class.getName();
    private final Context mContext;
    private final SubscriptionManager mSubManager;
    private final TelephonyManager mTelephonyManager;
    private final CarrierConfigManager mCarrierConfigMgr;
    private final EuiccManager mEuiccManager;
    private final ONSProfileConfigurator mONSProfileConfig;
    private final ONSProfileDownloader mONSProfileDownloader;
    private final ConnectivityManager mConnectivityManager;
    private final ONSStats mONSStats;
    @VisibleForTesting protected boolean mIsInternetConnAvailable = false;
    @VisibleForTesting protected boolean mRetryDownloadWhenNWConnected = false;
    @VisibleForTesting protected int mDownloadRetryCount = 0;

    @VisibleForTesting protected static final int REQUEST_CODE_DOWNLOAD_RETRY = 2;

    public ONSProfileActivator(Context context, ONSStats onsStats) {
        mContext = context;
        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (Flags.workProfileApiSplit()) {
            sm = sm.createForAllUserProfiles();
        }
        mSubManager = sm;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mCarrierConfigMgr = mContext.getSystemService(CarrierConfigManager.class);
        mEuiccManager = mContext.getSystemService(EuiccManager.class);
        mONSProfileConfig = new ONSProfileConfigurator(mContext, mSubManager,
                mCarrierConfigMgr, mEuiccManager, this);
        mONSProfileDownloader = new ONSProfileDownloader(mContext, mCarrierConfigMgr,
                mEuiccManager, mONSProfileConfig, this);

        //Monitor internet connection.
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mONSStats = onsStats;
        NetworkRequest request = new NetworkRequest.Builder().addCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        mConnectivityManager.registerNetworkCallback(request, new NetworkCallback());
    }

    /**
     * This constructor is only for JUnit testing
     */
    @TestApi
    ONSProfileActivator(Context mockContext, SubscriptionManager subscriptionManager,
                        TelephonyManager telephonyManager, CarrierConfigManager carrierConfigMgr,
                        EuiccManager euiccManager, ConnectivityManager connManager,
                        ONSProfileConfigurator onsProfileConfigurator,
                        ONSProfileDownloader onsProfileDownloader, ONSStats onsStats) {
        mContext = mockContext;
        mSubManager = subscriptionManager;
        mTelephonyManager = telephonyManager;
        mCarrierConfigMgr = carrierConfigMgr;
        mEuiccManager = euiccManager;
        mConnectivityManager = connManager;
        mONSProfileConfig = onsProfileConfigurator;
        mONSProfileDownloader = onsProfileDownloader;
        mONSStats = onsStats;
    }

    ONSProfileConfigurator getONSProfileConfigurator() {
        return mONSProfileConfig;
    }

    ONSProfileDownloader getONSProfileDownloader() {
        return mONSProfileDownloader;
    }

    private final Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_CODE_DOWNLOAD_RETRY: {
                    Result res = provisionCBRS();
                    Log.d(TAG, res.toString());
                    mONSStats.logEvent(new ONSStatsInfo().setProvisioningResult(res));
                }
                break;
            }
        }
    };

    /**
     * Called when SIM state changes. Triggers CBRS Auto provisioning.
     */
    public Result handleCarrierConfigChange() {
        Result res = provisionCBRS();
        Log.d(TAG, res.toString());
        mONSStats.logEvent(new ONSStatsInfo().setProvisioningResult(res));

        // Reset mDownloadRetryCount as carrier config change event is received. Either new SIM card
        // is inserted or carrier config values are updated.
        if (res == Result.DOWNLOAD_REQUESTED || res == Result.SUCCESS) {
            mDownloadRetryCount = 0;
        }

        return res;
    }

    @Override
    public void onOppSubscriptionDeleted(int pSIMId) {
        Result res = provisionCBRS();
        Log.d(TAG, res.toString());
        mONSStats.logEvent(new ONSStatsInfo().setProvisioningResult(res));
    }

    /**
     * Checks if AutoProvisioning is enabled, MultiSIM and eSIM support, cbrs pSIM is inserted and
     * makes sure device is in muti-SIM mode before triggering download of opportunistic eSIM.
     * Once downloaded, groups with pSIM, sets opportunistic and activates.
     */
    private Result provisionCBRS() {

        if (!isONSAutoProvisioningEnabled()) {
            return Result.ERR_AUTO_PROVISIONING_DISABLED;
        }

        //Check if device supports eSIM
        if (!isESIMSupported()) {
            return Result.ERR_ESIM_NOT_SUPPORTED;
        }

        //Check if it's a multi SIM Phone. CBRS is not supported on Single SIM phone.
        if (!isMultiSIMPhone()) {
            return Result.ERR_MULTISIM_NOT_SUPPORTED;
        }

        //Check the number of active subscriptions.
        List<SubscriptionInfo> activeSubInfos = mSubManager.getActiveSubscriptionInfoList();
        if (activeSubInfos == null || activeSubInfos.size() <= 0) {
            return Result.ERR_NO_SIM_INSERTED;
        }
        int activeSubCount = activeSubInfos.size();
        Log.d(TAG, "Active subscription count:" + activeSubCount);

        if (activeSubCount == 1) {
            SubscriptionInfo pSubInfo = activeSubInfos.get(0);
            if (pSubInfo.isOpportunistic()) {
                //Only one SIM is active and its opportunistic SIM.
                //Opportunistic eSIM shouldn't be used without pSIM.
                return Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM;
            }

            //if pSIM is not a CBRS carrier
            if (!isOppDataAutoProvisioningSupported(
                    pSubInfo.getSubscriptionId())) {
                return Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS;
            }

            if (isDeviceInSingleSIMMode()) {
                if (!switchToMultiSIMMode()) {
                    return Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE;
                }

                //Once device is Switched to Dual-SIM Mode, handleSimStateChange is triggered.
                return Result.ERR_SWITCHING_TO_DUAL_SIM_MODE;
            }

            return downloadAndActivateOpportunisticSubscription(pSubInfo);
        } else if (activeSubCount >= 2) {
            //If all the SIMs are physical SIM then it's a sure case of DUAL Active Subscription.
            boolean allPhysicalSIMs = true;
            for (SubscriptionInfo subInfo : activeSubInfos) {
                if (subInfo.isEmbedded()) {
                    allPhysicalSIMs = false;
                    break;
                }
            }

            if (allPhysicalSIMs) {
                return Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS;
            }

            //Check if one of the subscription is opportunistic but not marked.
            //if one of the SIM is opportunistic and not grouped then group the subscription.
            for (SubscriptionInfo subInfo : activeSubInfos) {
                int pSubId = subInfo.getSubscriptionId();
                if (!subInfo.isEmbedded() && isOppDataAutoProvisioningSupported(pSubId)) {

                    Log.d(TAG, "CBRS pSIM found. SubId:" + pSubId);

                    //Check if other SIM is opportunistic based on carrier-id.
                    SubscriptionInfo oppSubInfo = mONSProfileConfig
                            .findOpportunisticSubscription(pSubId);

                    //If opportunistic eSIM is found and activated.
                    if (oppSubInfo != null) {
                        if (mSubManager.isActiveSubscriptionId(oppSubInfo.getSubscriptionId())
                                && oppSubInfo.isOpportunistic()) {
                            //Already configured. No action required.
                            return Result.SUCCESS;
                        }

                        ParcelUuid pSIMGroupId = mONSProfileConfig.getPSIMGroupId(subInfo);
                        mONSProfileConfig.groupWithPSIMAndSetOpportunistic(oppSubInfo, pSIMGroupId);
                        return Result.SUCCESS;
                    }
                }
            }

            return Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS;
        }

        return Result.ERR_UNKNOWN;
    }

    private Result downloadAndActivateOpportunisticSubscription(
            SubscriptionInfo primaryCBRSSubInfo) {
        Log.d(TAG, "downloadAndActivateOpportunisticSubscription");

        //Check if pSIM is part of a group. If not then create a group.
        ParcelUuid pSIMgroupId = mONSProfileConfig.getPSIMGroupId(primaryCBRSSubInfo);

        //Check if opp eSIM is already downloaded but not grouped.
        SubscriptionInfo oppSubInfo = mONSProfileConfig.findOpportunisticSubscription(
                primaryCBRSSubInfo.getSubscriptionId());
        if (oppSubInfo != null) {
            mONSProfileConfig.groupWithPSIMAndSetOpportunistic(oppSubInfo, pSIMgroupId);
            return Result.SUCCESS;
        }

        if (!mIsInternetConnAvailable) {
            Log.d(TAG, "No internet connection. Download will be attempted when "
                    + "connection is restored");
            mRetryDownloadWhenNWConnected = true;
            return Result.ERR_WAITING_FOR_INTERNET_CONNECTION;
        }

        /* If download WiFi only flag is set and WiFi is not connected */
        if (getESIMDownloadViaWiFiOnlyFlag(primaryCBRSSubInfo.getSubscriptionId())
                && !isWiFiConnected()) {
            Log.d(TAG, "Download via WiFi only flag is set but WiFi is not connected."
                    + "Download will be attempted when WiFi connection is restored");
            mRetryDownloadWhenNWConnected = true;
            return Result.ERR_WAITING_FOR_WIFI_CONNECTION;
        }

        //Opportunistic subscription not found. Trigger Download.
        ONSProfileDownloader.DownloadProfileResult res = mONSProfileDownloader.downloadProfile(
                primaryCBRSSubInfo.getSubscriptionId());

        switch (res) {
            case DUPLICATE_REQUEST: return Result.ERR_DUPLICATE_DOWNLOAD_REQUEST;
            case INVALID_SMDP_ADDRESS: return Result.ERR_INVALID_CARRIER_CONFIG;
            case SUCCESS: return Result.DOWNLOAD_REQUESTED;
        }

        return Result.ERR_UNKNOWN;
    }

    @Override
    public void onDownloadComplete(int primarySubId) {
        mRetryDownloadWhenNWConnected = false;
        SubscriptionInfo opportunisticESIM = mONSProfileConfig.findOpportunisticSubscription(
                primarySubId);
        if (opportunisticESIM == null) {
            Log.e(TAG, "Downloaded Opportunistic eSIM not found. Unable to group with pSIM");
            mONSStats.logEvent(new ONSStatsInfo()
                    .setProvisioningResult(Result.ERR_DOWNLOADED_ESIM_NOT_FOUND)
                    .setPrimarySimSubId(primarySubId)
                    .setWifiConnected(isWiFiConnected()));
            return;
        }

        SubscriptionInfo pSIMSubInfo = mSubManager.getActiveSubscriptionInfo(primarySubId);
        if (pSIMSubInfo != null) {
            // Group with same Primary SIM for which eSIM is downloaded.
            mONSProfileConfig.groupWithPSIMAndSetOpportunistic(
                    opportunisticESIM, pSIMSubInfo.getGroupUuid());
            Log.d(TAG, "eSIM downloaded and configured successfully");
            mONSStats.logEvent(new ONSStatsInfo()
                    .setProvisioningResult(Result.SUCCESS)
                    .setRetryCount(mDownloadRetryCount)
                    .setWifiConnected(isWiFiConnected()));
        } else {
            Log.d(TAG, "ESIM downloaded but pSIM is not active or removed");
            mONSStats.logEvent(new ONSStatsInfo()
                    .setProvisioningResult(Result.ERR_PSIM_NOT_FOUND)
                    .setOppSimCarrierId(opportunisticESIM.getCarrierId())
                    .setWifiConnected(isWiFiConnected()));
        }
    }

    @Override
    public void onDownloadError(int pSIMSubId, DownloadRetryResultCode resultCode,
            int detailedErrorCode) {
        boolean logStats = true;
        switch (resultCode) {
            case ERR_MEMORY_FULL: {
                //eUICC Memory full occurred while downloading opportunistic eSIM.

                //First find and delete any opportunistic eSIMs from the operator same as the
                // current primary SIM.
                ArrayList<Integer> oppSubIds = mONSProfileConfig
                        .getOpportunisticSubIdsofPSIMOperator(pSIMSubId);
                if (oppSubIds != null && oppSubIds.size() > 0) {
                    mONSProfileConfig.deleteSubscription(oppSubIds.get(0));
                } else {
                    //else, find the inactive opportunistic eSIMs (any operator) and delete one of
                    // them and retry download again.
                    mONSProfileConfig.deleteInactiveOpportunisticSubscriptions(pSIMSubId);
                }

                //Delete subscription -> onOppSubscriptionDeleted callback ->  provisionCBRS ->
                // triggers eSIM download again.

                //Download retry will stop if there are no opportunistic eSIM profiles to delete.
            }
            break;

            case ERR_INSTALL_ESIM_PROFILE_FAILED: {
                //Since the installation of eSIM profile has failed there may be an issue with the
                //format or profile data. We retry by first deleting existing eSIM profile from the
                //operator same as the primary SIM and retry download opportunistic eSIM.
                ArrayList<Integer> oppSubIds = mONSProfileConfig
                        .getOpportunisticSubIdsofPSIMOperator(pSIMSubId);

                if (oppSubIds != null && oppSubIds.size() > 0) {
                    mONSProfileConfig.deleteSubscription(oppSubIds.get(0));
                }

                //Download retry will stop if there are no opportunistic eSIM profiles to delete
                // from the same operator.
            }
            break;

            case ERR_RETRY_DOWNLOAD: {
                if (startBackoffTimer(pSIMSubId)) {
                    // do not log the atom if download retry has not reached max limit.
                    logStats = false;
                }
            }
            break;
            default: {
                // Stop download until SIM change or device reboot.
                Log.e(TAG, "Download failed with cause=" + resultCode);
            }
        }
        if (logStats) {
            mONSStats.logEvent(new ONSStatsInfo()
                    .setDownloadResult(resultCode)
                    .setPrimarySimSubId(pSIMSubId)
                    .setRetryCount(mDownloadRetryCount)
                    .setDetailedErrCode(detailedErrorCode)
                    .setWifiConnected(isWiFiConnected()));
        }
    }

    /**
     * Called when eSIM download fails. Listener is called after a delay based on retry count with
     * the error code: BACKOFF_TIMER_EXPIRED
     *
     * @param pSIMSubId Primary Subscription ID
     * @return true if backoff timer starts; otherwise false.
     */
    @VisibleForTesting
    protected boolean startBackoffTimer(int pSIMSubId) {
        //retry logic
        mDownloadRetryCount++;
        Log.e(TAG, "Download retry count :" + mDownloadRetryCount);

        //Stop download retry if number of retries exceeded max configured value.
        if (mDownloadRetryCount > getDownloadRetryMaxAttemptsVal(pSIMSubId)) {
            Log.e(TAG, "Max download retry attempted. Stopping retry");
            return false;
        }

        int backoffTimerVal = getDownloadRetryBackOffTimerVal(pSIMSubId);
        int delay = calculateBackoffDelay(mDownloadRetryCount, backoffTimerVal);

        Message retryMsg = new Message();
        retryMsg.what = REQUEST_CODE_DOWNLOAD_RETRY;
        retryMsg.arg2 = pSIMSubId;
        mHandler.sendMessageDelayed(retryMsg, delay);

        Log.d(TAG, "Download failed. Retry after :" + delay + "MilliSecs");
        return true;
    }

    @VisibleForTesting
    protected static int calculateBackoffDelay(int retryCount, int backoffTimerVal) {
        /**
         * Timer value is calculated using "Exponential Backoff retry" algorithm.
         * When the first download failure occurs, retry download after
         * BACKOFF_TIMER_VALUE [Carrier Configurable] seconds.
         *
         * If download fails again then, retry after either BACKOFF_TIMER_VALUE,
         * 2xBACKOFF_TIMER_VALUE, or 3xBACKOFF_TIMER_VALUE seconds.
         *
         * In general after the cth failed attempt, retry after k *
         * BACKOFF_TIMER_VALUE seconds, where k is a random integer between 1 and
         * 2^c − 1. Max c value is KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT
         * [Carrier configurable]
         */
        Random random = new Random();
        //Calculate 2^c − 1
        int maxTime = (int) Math.pow(2, retryCount) - 1;

        //Random value between (1 & 2^c − 1) and convert to millisecond
        return ((random.nextInt(maxTime) + 1)) * backoffTimerVal * 1000;
    }

    /**
     * Retrieves maximum retry attempts from carrier configuration. After maximum attempts, further
     * attempts will not be made until next device reboot.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return integer value for maximum allowed retry attempts.
     */
    private int getDownloadRetryMaxAttemptsVal(int subscriptionId) {
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(subscriptionId);
        return config.getInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT);
    }

    /**
     * Retrieves backoff timer value (in seconds) from carrier configuration. Value is used to
     * calculate delay before retrying profile download.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return Backoff timer value in seconds.
     */
    private int getDownloadRetryBackOffTimerVal(int subscriptionId) {
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(subscriptionId);
        return config.getInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT);
    }


    /**
     * Checks if device supports eSIM.
     */
    private boolean isESIMSupported() {
        return (mEuiccManager != null && mEuiccManager.isEnabled());
    }

    /**
     * Fetches ONS auto provisioning enable flag from device configuration.
     * ONS auto provisioning feature executes only when the flag is set to true in device
     * configuration.
     */
    private boolean isONSAutoProvisioningEnabled() {
        return mContext.getResources().getBoolean(R.bool.enable_ons_auto_provisioning);
    }

    /**
     * Check if device support multiple active SIMs
     */
    private boolean isMultiSIMPhone() {
        return (mTelephonyManager.getSupportedModemCount() >= 2);
    }

    /**
     * Check if the given subscription is a CBRS supported carrier.
     */
    private boolean isOppDataAutoProvisioningSupported(int pSIMSubId) {
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(pSIMSubId);
        return config.getBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL);
    }

    /**
     * Checks if device is in single SIM mode.
     */
    private boolean isDeviceInSingleSIMMode() {
        return (mTelephonyManager.getActiveModemCount() <= 1);
    }

    /**
     * Switches device to multi SIM mode. Checks if reboot is required before switching and
     * configuration is triggered only if reboot not required.
     */
    private boolean switchToMultiSIMMode() {
        if (!mTelephonyManager.doesSwitchMultiSimConfigTriggerReboot()) {
            mTelephonyManager.switchMultiSimConfig(2);
            return true;
        }

        return false;
    }

    private boolean isWiFiConnected() {
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        if ((activeNetwork != null) && mConnectivityManager.getNetworkCapabilities(activeNetwork)
                .hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return true;
        }

        return false;
    }

    /**
     * Retrieves WiFi only eSIM Download flag the given subscription from carrier configuration.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return download flag.
     */
    private boolean getESIMDownloadViaWiFiOnlyFlag(int subscriptionId) {
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(subscriptionId);
        return config.getBoolean(
                CarrierConfigManager.KEY_OPPORTUNISTIC_ESIM_DOWNLOAD_VIA_WIFI_ONLY_BOOL);
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.d(TAG, "Internet connection available");
            mIsInternetConnAvailable = true;
            if (mRetryDownloadWhenNWConnected) {
                Result res = provisionCBRS();
                Log.d(TAG, res.toString());
                mONSStats.logEvent(new ONSStatsInfo().setProvisioningResult(res));
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.d(TAG, "Internet connection lost");
            mIsInternetConnAvailable = false;
        }
    }

    /**
     * Enum to map the results of the CBRS provisioning. The order of the defined enums must be kept
     * intact and new entries should be appended at the end of the list.
     */
    public enum Result {
        SUCCESS,
        DOWNLOAD_REQUESTED,
        ERR_SWITCHING_TO_DUAL_SIM_MODE,
        ERR_AUTO_PROVISIONING_DISABLED,
        ERR_ESIM_NOT_SUPPORTED,
        ERR_MULTISIM_NOT_SUPPORTED,
        ERR_CARRIER_DOESNT_SUPPORT_CBRS,
        ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
        ERR_NO_SIM_INSERTED,
        ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM,
        ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE,
        ERR_WAITING_FOR_INTERNET_CONNECTION,
        ERR_WAITING_FOR_WIFI_CONNECTION,
        ERR_DUPLICATE_DOWNLOAD_REQUEST,
        ERR_INVALID_CARRIER_CONFIG,
        ERR_DOWNLOADED_ESIM_NOT_FOUND,
        ERR_PSIM_NOT_FOUND,
        ERR_UNKNOWN;
    }
}
