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

package com.android.ons;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import java.util.List;

public class ONSStats {
    private static final String ONS_ATOM_LOG_FILE = "ons_atom_log_info";
    private static final String KEY_PROVISIONING_RESULT = "_provisioning_result";
    private static final String KEY_DOWNLOAD_RESULT = "_download_result";
    private static final String KEY_RETRY_COUNT = "_retry_count";
    private static final String KEY_DETAILED_ERROR_CODE = "_detailed_error_code";
    private static final String KEY_OPP_CARRIER_ID = "_opportunistic_carrier_id";
    private static final String KEY_PRIMARY_CARRIER_ID = "_primary_sim_carrier_id";
    private final Context mContext;
    private final SubscriptionManager mSubscriptionManager;

    /** Constructor to create instance for ONSStats. */
    public ONSStats(Context context, SubscriptionManager subscriptionManager) {
        mContext = context;
        mSubscriptionManager = subscriptionManager;
    }

    /**
     * It logs the ONS atom with the info passed as ONSStatsInfo. If the information is already
     * logged, it will be skipped.
     *
     * @param info information to be logged.
     * @return returns true if information is logged, otherwise false.
     */
    public boolean logEvent(ONSStatsInfo info) {
        // check if the info needs to be ignored.
        if (ignoreEvent(info)) {
            return false;
        }
        int statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_UNKNOWN;
        if (info.isProvisioningResultUpdated()) {
            switch (info.getProvisioningResult()) {
                case SUCCESS:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_SUCCESS;
                    break;
                case ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_SWITCH_TO_MULTISIM_FAILED;
                    break;
                case ERR_CARRIER_DOESNT_SUPPORT_CBRS:
                case ERR_AUTO_PROVISIONING_DISABLED:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_AUTO_PROVISIONING_DISABLED;
                    break;
                case ERR_ESIM_NOT_SUPPORTED:
                case ERR_MULTISIM_NOT_SUPPORTED:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_DEVICE_NOT_CAPABLE;
                    break;
                case ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM:
                case ERR_DUAL_ACTIVE_SUBSCRIPTIONS:
                case ERR_PSIM_NOT_FOUND:
                case ERR_DOWNLOADED_ESIM_NOT_FOUND:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_ESIM_PROVISIONING_FAILED;
                    break;
                case ERR_WAITING_FOR_INTERNET_CONNECTION:
                case ERR_WAITING_FOR_WIFI_CONNECTION:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_INTERNET_NOT_AVAILABLE;
                    break;
                case ERR_INVALID_CARRIER_CONFIG:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_UNRESOLVABLE_ERROR;
                    break;
                default:
                    break;
            }
        } else {
            switch (info.getDownloadResult()) {
                case ERR_UNRESOLVABLE:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_UNRESOLVABLE_ERROR;
                    break;
                case ERR_MEMORY_FULL:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_MEMORY_FULL;
                    break;
                case ERR_INSTALL_ESIM_PROFILE_FAILED:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_INSTALL_ESIM_PROFILE_FAILED;
                    break;
                case ERR_RETRY_DOWNLOAD:
                    statsCode = OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE__ERROR_CODE__RESULT_CONNECTION_ERROR;
                    break;
                default:
                    break;
            }
        }
        OnsStatsLog.write(
                OnsStatsLog.ONS_OPPORTUNISTIC_ESIM_PROVISIONING_COMPLETE,
                getSimCarrierId(info.getPrimarySimSubId()),
                info.getOppSimCarrierId(),
                info.isWifiConnected(),
                statsCode,
                info.getRetryCount(),
                info.getDetailedErrCode());
        updateSharedPreferences(info);
        return true;
    }

    private void updateSharedPreferences(ONSStatsInfo info) {
        SharedPreferences sharedPref =
                mContext.getSharedPreferences(ONS_ATOM_LOG_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        if (info.isProvisioningResultUpdated()) {
            editor.putInt(KEY_PROVISIONING_RESULT, info.getProvisioningResult().ordinal());
            editor.remove(KEY_DOWNLOAD_RESULT);
        } else {
            editor.putInt(KEY_DOWNLOAD_RESULT, info.getDownloadResult().ordinal());
            editor.remove(KEY_PROVISIONING_RESULT);
        }
        editor.putInt(KEY_PRIMARY_CARRIER_ID, getSimCarrierId(info.getPrimarySimSubId()))
                .putInt(KEY_RETRY_COUNT, info.getRetryCount())
                .putInt(KEY_OPP_CARRIER_ID, info.getOppSimCarrierId())
                .putInt(KEY_DETAILED_ERROR_CODE, info.getDetailedErrCode())
                .apply();
    }

    private boolean ignoreEvent(ONSStatsInfo info) {
        Result result = info.getProvisioningResult();
        if (info.isProvisioningResultUpdated()) {
            info.setDetailedErrCode(result.ordinal());
            // Codes are ignored since they are intermediate state of CBRS provisioning check.
            if ((result == Result.DOWNLOAD_REQUESTED)
                    || result == Result.ERR_NO_SIM_INSERTED
                    || result == Result.ERR_DUPLICATE_DOWNLOAD_REQUEST
                    || result == Result.ERR_SWITCHING_TO_DUAL_SIM_MODE) {
                return true;
            }

            // add subscription id for carrier if it doesn't support CBRS.
            if (result == Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS) {
                List<SubscriptionInfo> subInfos =
                        mSubscriptionManager.getActiveSubscriptionInfoList();
                info.setPrimarySimSubId(
                        (subInfos != null && !subInfos.isEmpty())
                                ? subInfos.get(0).getSubscriptionId()
                                : -1);
            }
        }

        SharedPreferences sharedPref =
                mContext.getSharedPreferences(ONS_ATOM_LOG_FILE, Context.MODE_PRIVATE);

        boolean errorCodeUpdated =
                (info.isProvisioningResultUpdated()
                        ? sharedPref.getInt(KEY_PROVISIONING_RESULT, -1) != result.ordinal()
                        : sharedPref.getInt(KEY_DOWNLOAD_RESULT, -1)
                                != info.getDownloadResult().ordinal());
        boolean carrierIdUpdated =
                sharedPref.getInt(KEY_PRIMARY_CARRIER_ID, -1)
                        != getSimCarrierId(info.getPrimarySimSubId());
        boolean retryCountUpdated = sharedPref.getInt(KEY_RETRY_COUNT, -1) != info.getRetryCount();
        boolean oppCarrierIdChanged =
                sharedPref.getInt(KEY_OPP_CARRIER_ID, -1) != info.getOppSimCarrierId();
        boolean detailedErrorChanged =
                sharedPref.getInt(KEY_DETAILED_ERROR_CODE, -1) != info.getDetailedErrCode();
        if (!(errorCodeUpdated
                || carrierIdUpdated
                || retryCountUpdated
                || oppCarrierIdChanged
                || detailedErrorChanged)) {
            // Result codes are meant to log on every occurrence. These should not be ignored.
            if (result == Result.SUCCESS
                    || result == Result.ERR_DOWNLOADED_ESIM_NOT_FOUND
                    || info.getDownloadResult()
                            == DownloadRetryResultCode.ERR_INSTALL_ESIM_PROFILE_FAILED) {
                return false;
            }
            return true;
        }
        return false;
    }

    private int getSimCarrierId(int subId) {
        if (subId == -1) return -1;
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        return (subInfo != null) ? subInfo.getCarrierId() : -1;
    }
}
