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

package com.android.cellbroadcastservice;

import static android.content.Context.MODE_PRIVATE;
import static android.telephony.SmsCbMessage.MESSAGE_FORMAT_3GPP;

import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_CDMA;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_GSM;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsCbMessage;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * CellBroadcastServiceMetrics
 * Logging featureUpdated, when alert message is received or channel range is updated
 */
public class CellBroadcastServiceMetrics {

    private static final String TAG = "CellBroadcastServiceMetrics";
    private static final boolean VDBG = false;
    // Key to access the shared preference of cell broadcast service feature for metric.
    private static final String CBS_METRIC_PREF = "CellBroadcastServiceMetricSharedPref";

    private static CellBroadcastServiceMetrics sCbsMetrics;

    private FeatureMetrics mFeatureMetrics;
    private FeatureMetrics mFeatureMetricsSharedPreferences;


    /**
     * Get instance of CellBroadcastServiceMetrics.
     */
    public static CellBroadcastServiceMetrics getInstance() {
        if (sCbsMetrics == null) {
            sCbsMetrics = new CellBroadcastServiceMetrics();
        }
        return sCbsMetrics;
    }

    /**
     * CellBroadcastReceiverMetrics.FeatureMetrics
     * Logging featureUpdated as needed when alert message is received
     */
    public static class FeatureMetrics implements Cloneable {
        public static final String ADDITIONAL_CBR_PACKAGES = "additional_cbr_packages";
        public static final String AREA_INFO_PACKAGES = "area_info_packages";
        public static final String RESET_AREA_INFO = "reset_area_info";
        public static final String DEFVAL_AREAPKGS = "com.android.settings";

        private boolean mIsOverrideCbrPkgs;
        private boolean mIsOverrideAreaInfoPkgs;
        private boolean mResetAreaInfo;

        private Context mContext;

        public FeatureMetrics(Context context) {
            mContext = context;
            SharedPreferences sp = mContext.getSharedPreferences(CBS_METRIC_PREF, MODE_PRIVATE);

            mIsOverrideCbrPkgs = sp.getBoolean(ADDITIONAL_CBR_PACKAGES, false);
            mIsOverrideAreaInfoPkgs = sp.getBoolean(AREA_INFO_PACKAGES, false);
            mResetAreaInfo = sp.getBoolean(RESET_AREA_INFO, false);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIsOverrideCbrPkgs, mIsOverrideAreaInfoPkgs, mResetAreaInfo);
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof FeatureMetrics) {
                FeatureMetrics features = (FeatureMetrics) object;
                return (this.mIsOverrideCbrPkgs == features.mIsOverrideCbrPkgs
                        && this.mIsOverrideAreaInfoPkgs == features.mIsOverrideAreaInfoPkgs
                        && this.mResetAreaInfo == features.mResetAreaInfo);
            }
            return false;
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        /**
         * Get current status whether cell broadcast receiver packages are overridden
         */
        @VisibleForTesting
        public boolean isOverrideCbrPkgs() {
            return mIsOverrideCbrPkgs;
        }

        /**
         * Get current status whether area information packages are overridden
         */
        @VisibleForTesting
        public boolean isOverrideAreaInfoPkgs() {
            return mIsOverrideAreaInfoPkgs;
        }

        /**
         * Get current status whether reset area information while in out of service
         */
        @VisibleForTesting
        public boolean isResetAreaInfo() {
            return mResetAreaInfo;
        }

        /**
         * Set whether additional cbr packages are overridden
         *
         * @param override : whether additional cbr packages are overridden
         */
        @VisibleForTesting
        public void onChangedAdditionalCbrPackage(boolean override) {
            mIsOverrideCbrPkgs = override;
        }

        /**
         * Set whether area info packages are overridden
         *
         * @param current : list of area info overriding packages
         */
        @VisibleForTesting
        public void onChangedAreaInfoPackage(List<String> current) {
            mIsOverrideAreaInfoPkgs = !Arrays.asList(new String[]{DEFVAL_AREAPKGS}).equals(current);
        }

        /**
         * Set whether area info reset on our of service
         *
         * @param current : whether reset area info is supported
         */
        @VisibleForTesting
        public void onChangedResetAreaInfo(boolean current) {
            mResetAreaInfo = current;
        }

        /**
         * Calling check-in method for CB_SERVICE_FEATURE
         */
        @VisibleForTesting
        public void logFeatureChanged() {
            CellBroadcastModuleStatsLog.write(
                    CellBroadcastModuleStatsLog.CB_SERVICE_FEATURE_CHANGED,
                    mIsOverrideCbrPkgs,
                    mIsOverrideAreaInfoPkgs,
                    mResetAreaInfo);
            if (VDBG) Log.d(TAG, this.toString());
        }

        /**
         * Update preferences for service feature metrics
         */
        @VisibleForTesting
        public void updateSharedPreferences() {
            SharedPreferences sp = mContext.getSharedPreferences(CBS_METRIC_PREF, MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(ADDITIONAL_CBR_PACKAGES, mIsOverrideCbrPkgs);
            editor.putBoolean(AREA_INFO_PACKAGES, mIsOverrideAreaInfoPkgs);
            editor.putBoolean(RESET_AREA_INFO, mResetAreaInfo);
            editor.apply();
        }

        @Override
        public String toString() {
            return "CellBroadcast_Service_Feature : "
                    + "mIsOverrideCbrPkgs = " + mIsOverrideCbrPkgs + " | "
                    + "mIsOverrideAreaInfoPkgs = " + mIsOverrideAreaInfoPkgs + " | "
                    + "mResetAreaInfo = " + mResetAreaInfo;
        }
    }

    /**
     * get cached feature metrics for shared preferences
     */
    @VisibleForTesting
    public FeatureMetrics getFeatureMetricsSharedPreferences() {
        return mFeatureMetricsSharedPreferences;
    }

    /**
     * set cached feature metrics for current status
     */
    @VisibleForTesting
    public void setFeatureMetrics(FeatureMetrics featureMetrics) {
        mFeatureMetrics = featureMetrics;
    }

    /**
     * Set featureMetricsSharedPreferences
     *
     * @param featureMetricsSharedPreferences : Cbs features information
     */
    @VisibleForTesting
    public void setFeatureMetricsSharedPreferences(FeatureMetrics featureMetricsSharedPreferences) {
        mFeatureMetricsSharedPreferences = featureMetricsSharedPreferences;
    }

    /**
     * Get featureMetrics if null then create
     */
    @VisibleForTesting
    public FeatureMetrics getFeatureMetrics(Context context) {
        if (mFeatureMetrics == null) {
            mFeatureMetrics = new FeatureMetrics(context);
            mFeatureMetricsSharedPreferences = new FeatureMetrics(context);
        }
        return mFeatureMetrics;
    }

    /**
     * When feature changed and net alert message received then check-in logging
     *
     * @param context : Context
     */
    @VisibleForTesting
    public void logFeatureChangedAsNeeded(Context context) {
        if (!getFeatureMetrics(context).equals(mFeatureMetricsSharedPreferences)) {
            mFeatureMetrics.logFeatureChanged();
            mFeatureMetrics.updateSharedPreferences();
            try {
                mFeatureMetricsSharedPreferences = (FeatureMetrics) mFeatureMetrics.clone();
            } catch (CloneNotSupportedException e) {
                Log.e(TAG, "exception during making clone for service feature metrics:  " + e);
            }
        }
    }

    /**
     * Create a new logMessageReported
     *
     * @param type     : radio type
     * @param source   : layer of reported message
     * @param serialNo : unique identifier of message
     * @param msgId    : service_category of message
     */
    public void logMessageReported(Context context, int type, int source, int serialNo, int msgId) {
        if (VDBG) {
            Log.d(TAG,
                    "logMessageReported : " + type + " " + source + " " + serialNo + " " + msgId);
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MESSAGE_REPORTED, type,
                source, serialNo, msgId);
    }

    /**
     * Create a new logMessageError
     *
     * @param type             : error type
     * @param exceptionMessage : error message
     */
    public void logMessageError(int type, String exceptionMessage) {
        if (VDBG) {
            Log.d(TAG, "logMessageError : " + type + " " + exceptionMessage);
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MESSAGE_ERROR,
                type, exceptionMessage);
    }

    /**
     * Create a new logMessageFiltered
     *
     * @param filterType : reason type of filtered
     * @param msg        : sms cell broadcast message information
     */
    public void logMessageFiltered(int filterType, SmsCbMessage msg) {
        int ratType = msg.getMessageFormat() == MESSAGE_FORMAT_3GPP ? FILTER_GSM : FILTER_CDMA;
        if (VDBG) {
            Log.d(TAG, "logMessageFiltered : " + ratType + " " + filterType + " "
                    + msg.getSerialNumber() + " " + msg.getServiceCategory());
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MESSAGE_FILTERED,
                ratType, filterType, msg.getSerialNumber(), msg.getServiceCategory());
    }

    /**
     * Create a new logModuleError
     *
     * @param source    : where this log happened
     * @param errorType : type of error
     */
    public void logModuleError(int source, int errorType) {
        if (VDBG) {
            Log.d(TAG, "logModuleError : " + source + " " + errorType);
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MODULE_ERROR_REPORTED,
                source, errorType);
    }
}
