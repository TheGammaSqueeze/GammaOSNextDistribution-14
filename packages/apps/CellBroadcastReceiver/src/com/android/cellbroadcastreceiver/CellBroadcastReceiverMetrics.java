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

package com.android.cellbroadcastreceiver;

import static android.content.Context.MODE_PRIVATE;
import static android.telephony.SmsCbMessage.MESSAGE_FORMAT_3GPP;

import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_CDMA;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_GSM;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsCbMessage;
import android.util.Log;
import android.util.Pair;

import com.android.cellbroadcastservice.CellBroadcastModuleStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * CellBroadcastReceiverMetrics
 * Logging featureUpdated when alert message is received or channel range is updated
 * Logging onConfigUpdated when channel range is updated
 */
public class CellBroadcastReceiverMetrics {

    private static final String TAG = "CellbroadcastReceiverMetrics";
    private static final boolean VDBG = false;

    // Key to access the shared preference of cellbroadcast channel range information for metric.
    public static final String CBR_CONFIG_UPDATED = "CellBroadcastConfigUpdated";
    // Key to access the shared preference of cellbroadcast receiver feature for metric.
    private static final String CBR_METRIC_PREF = "CellBroadcastReceiverMetricSharedPref";
    private static final String CHANNEL_DELIMITER = ",";
    private static final String RANGE_DELIMITER = "-";

    private static CellBroadcastReceiverMetrics sCbrMetrics;

    HashSet<Pair<Integer, Integer>> mConfigUpdatedCachedChannelSet;

    private FeatureMetrics mFeatureMetrics;
    private FeatureMetrics mFeatureMetricsSharedPreferences;

    /**
     * Get instance of CellBroadcastReceiverMetrics.
     */
    public static CellBroadcastReceiverMetrics getInstance() {
        if (sCbrMetrics == null) {
            sCbrMetrics = new CellBroadcastReceiverMetrics();
        }
        return sCbrMetrics;
    }

    /**
     * set cached feature metrics for current status
     */
    @VisibleForTesting
    public void setFeatureMetrics(
            FeatureMetrics featureMetrics) {
        mFeatureMetrics = featureMetrics;
    }

    /**
     * get cached feature metrics for shared preferences
     */
    @VisibleForTesting
    public FeatureMetrics getFeatureMetricsSharedPreferences() {
        return mFeatureMetricsSharedPreferences;
    }

    /**
     * Set featureMetricsSharedPreferences
     *
     * @param featureMetricsSharedPreferences : Cbr features information
     */
    @VisibleForTesting
    public void setFeatureMetricsSharedPreferences(
            FeatureMetrics featureMetricsSharedPreferences) {
        mFeatureMetricsSharedPreferences = featureMetricsSharedPreferences;
    }

    /**
     * Get current configuration channel set status
     */
    @VisibleForTesting
    public HashSet<Pair<Integer, Integer>> getCachedChannelSet() {
        return mConfigUpdatedCachedChannelSet;
    }

    /**
     * CellbroadcastReceiverMetrics
     * Logging featureUpdated as needed when alert message is received or channel range is updated
     */
    public class FeatureMetrics implements Cloneable {
        public static final String ALERT_IN_CALL = "enable_alert_handling_during_call";
        public static final String OVERRIDE_DND = "override_dnd";
        public static final String ROAMING_SUPPORT = "cmas_roaming_network_strings";
        public static final String STORE_SMS = "enable_write_alerts_to_sms_inbox";
        public static final String TEST_MODE = "testing_mode";
        public static final String TTS_MODE = "enable_alert_speech_default";
        public static final String TEST_MODE_ON_USER_BUILD = "allow_testing_mode_on_user_build";

        private boolean mAlertDuringCall;
        private HashSet<Pair<Integer, Integer>> mDnDChannelSet;
        private boolean mRoamingSupport;
        private boolean mStoreSms;
        private boolean mTestMode;
        private boolean mEnableAlertSpeech;
        private boolean mTestModeOnUserBuild;

        private Context mContext;

        FeatureMetrics(Context context) {
            mContext = context;
            SharedPreferences sp = mContext.getSharedPreferences(CBR_METRIC_PREF, MODE_PRIVATE);
            mAlertDuringCall = sp.getBoolean(ALERT_IN_CALL, false);
            String strOverrideDnD = sp.getString(OVERRIDE_DND, String.valueOf(0));
            mDnDChannelSet = getChannelSetFromString(strOverrideDnD);
            mRoamingSupport = sp.getBoolean(ROAMING_SUPPORT, false);
            mStoreSms = sp.getBoolean(STORE_SMS, false);
            mTestMode = sp.getBoolean(TEST_MODE, false);
            mEnableAlertSpeech = sp.getBoolean(TTS_MODE, true);
            mTestModeOnUserBuild = sp.getBoolean(TEST_MODE_ON_USER_BUILD, true);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDnDChannelSet, mAlertDuringCall, mRoamingSupport, mStoreSms,
                    mTestMode, mEnableAlertSpeech, mTestModeOnUserBuild);
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof FeatureMetrics) {
                FeatureMetrics features = (FeatureMetrics) object;
                return (this.mAlertDuringCall == features.mAlertDuringCall
                        && this.mDnDChannelSet.equals(features.mDnDChannelSet)
                        && this.mRoamingSupport == features.mRoamingSupport
                        && this.mStoreSms == features.mStoreSms
                        && this.mTestMode == features.mTestMode
                        && this.mEnableAlertSpeech == features.mEnableAlertSpeech
                        && this.mTestModeOnUserBuild == features.mTestModeOnUserBuild);
            }
            return false;
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            FeatureMetrics copy = (FeatureMetrics) super.clone();
            copy.mDnDChannelSet = new HashSet<>();
            copy.mDnDChannelSet.addAll(this.mDnDChannelSet);
            return copy;
        }

        /**
         * Get current status whether alert during call is enabled
         */
        @VisibleForTesting
        public boolean isAlertDuringCall() {
            return mAlertDuringCall;
        }

        /**
         * Get current do not disturb channels set
         */
        @VisibleForTesting
        public HashSet<Pair<Integer, Integer>> getDnDChannelSet() {
            return mDnDChannelSet;
        }

        /**
         * Get whether currently roaming supported
         */
        @VisibleForTesting
        public boolean isRoamingSupport() {
            return mRoamingSupport;
        }

        /**
         * Get whether alert messages are saved inbox
         */
        @VisibleForTesting
        public boolean isStoreSms() {
            return mStoreSms;
        }

        /**
         * Get whether test mode is enabled
         */
        @VisibleForTesting
        public boolean isTestMode() {
            return mTestMode;
        }

        /**
         * Get whether alert message support text to speech
         */
        @VisibleForTesting
        public boolean isEnableAlertSpeech() {
            return mEnableAlertSpeech;
        }

        /**
         * Get whether test mode is not supporting in user build status
         */
        @VisibleForTesting
        public boolean isTestModeOnUserBuild() {
            return mTestModeOnUserBuild;
        }

        /**
         * Set alert during call
         *
         * @param current : current status of alert during call
         */
        @VisibleForTesting
        public void onChangedAlertDuringCall(boolean current) {
            mAlertDuringCall = current;
        }

        /**
         * Set alert during call
         *
         * @param channelManager : channel manager to get channel range supporting override dnd
         * @param overAllDnD     : whether override dnd is fully supported or not
         */
        @VisibleForTesting
        public void onChangedOverrideDnD(
                CellBroadcastChannelManager channelManager, boolean overAllDnD) {
            mDnDChannelSet.clear();
            if (overAllDnD) {
                mDnDChannelSet.add(new Pair(Integer.MAX_VALUE, Integer.MAX_VALUE));
            } else {
                channelManager.getAllCellBroadcastChannelRanges().forEach(r -> {
                    if (r.mOverrideDnd) {
                        mDnDChannelSet.add(new Pair(r.mStartId, r.mEndId));
                    }
                });
                if (mDnDChannelSet.size() == 0) {
                    mDnDChannelSet.add(new Pair(0, 0));
                }
            }
        }

        /**
         * Set roaming support
         *
         * @param current : current status of roaming support
         */
        @VisibleForTesting
        public void onChangedRoamingSupport(boolean current) {
            mRoamingSupport = current;
        }

        /**
         * Set current status of storing alert message inbox
         *
         * @param current : current status value of storing inbox
         */
        @VisibleForTesting
        public void onChangedStoreSms(boolean current) {
            mStoreSms = current;
        }

        /**
         * Set current status of test-mode
         *
         * @param current : current status value of test-mode
         */
        @VisibleForTesting
        public void onChangedTestMode(boolean current) {
            mTestMode = current;
        }

        /**
         * Set whether text to speech is supported for alert message
         *
         * @param current : current status tts
         */
        @VisibleForTesting
        public void onChangedEnableAlertSpeech(boolean current) {
            mEnableAlertSpeech = current;
        }

        /**
         * Set whether test mode on user build is supported
         *
         * @param current : current status of test mode on user build
         */
        public void onChangedTestModeOnUserBuild(boolean current) {
            mTestModeOnUserBuild = current;
        }

        /**
         * Calling check-in method for CB_SERVICE_FEATURE
         */
        @VisibleForTesting
        public void logFeatureChanged() {
            try {
                CellBroadcastModuleStatsLog.write(
                        CellBroadcastModuleStatsLog.CB_RECEIVER_FEATURE_CHANGED,
                        mAlertDuringCall,
                        convertToProtoBuffer(mDnDChannelSet),
                        mRoamingSupport,
                        mStoreSms,
                        mTestMode,
                        mEnableAlertSpeech,
                        mTestModeOnUserBuild);
            } catch (IOException e) {
                Log.e(TAG, "IOException while encoding array byte from channel set" + e);
            }
            if (VDBG) Log.d(TAG, this.toString());
        }

        /**
         * Update preferences for receiver feature metrics
         */
        public void updateSharedPreferences() {
            SharedPreferences sp =
                    mContext.getSharedPreferences(CBR_METRIC_PREF, MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(ALERT_IN_CALL, mAlertDuringCall);
            editor.putString(OVERRIDE_DND, getStringFromChannelSet(mDnDChannelSet));
            editor.putBoolean(ROAMING_SUPPORT, mRoamingSupport);
            editor.putBoolean(STORE_SMS, mStoreSms);
            editor.putBoolean(TEST_MODE, mTestMode);
            editor.putBoolean(TTS_MODE, mEnableAlertSpeech);
            editor.putBoolean(TEST_MODE_ON_USER_BUILD, mTestModeOnUserBuild);
            editor.apply();
        }

        @Override
        public String toString() {
            return "CellBroadcast_Receiver_Feature : "
                    + "mAlertDuringCall = " + mAlertDuringCall + " | "
                    + "mOverrideDnD = " + getStringFromChannelSet(mDnDChannelSet) + " | "
                    + "mRoamingSupport = " + mRoamingSupport + " | "
                    + "mStoreSms = " + mStoreSms + " | "
                    + "mTestMode = " + mTestMode + " | "
                    + "mEnableAlertSpeech = " + mEnableAlertSpeech + " | "
                    + "mTestModeOnUserBuild = " + mTestModeOnUserBuild;
        }
    }

    /**
     * Get current feature metrics
     *
     * @param context : Context
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
     * Convert ChannelSet to ProtoBuffer
     *
     * @param rangeList : channel range set
     */
    @VisibleForTesting
    public byte[] convertToProtoBuffer(HashSet<Pair<Integer, Integer>> rangeList)
            throws IOException {
        Cellbroadcastmetric.CellBroadcastChannelRangesProto.Builder rangeListBuilder =
                Cellbroadcastmetric.CellBroadcastChannelRangesProto.newBuilder();
        rangeList.stream().sorted((o1, o2) -> o1.first == o2.first ? o1.second - o2.second
                : o1.first - o2.first).forEach(pair -> {
            Cellbroadcastmetric.CellBroadcastChannelRangeProto.Builder rangeBuilder =
                    Cellbroadcastmetric.CellBroadcastChannelRangeProto.newBuilder();
            rangeBuilder.setStart(pair.first);
            rangeBuilder.setEnd(pair.second);
            rangeListBuilder.addChannelRanges(rangeBuilder);
            if (VDBG) {
                Log.d(TAG, "[first] : " + pair.first + " [second] : " + pair.second);
            }
        });
        return rangeListBuilder.build().toByteArray();
    }

    /**
     * Convert ProtoBuffer to ChannelSet
     *
     * @param arrayByte : channel range set encoded arrayByte
     */
    @VisibleForTesting
    public HashSet<Pair<Integer, Integer>> getDataFromProtoArrayByte(byte[] arrayByte)
            throws InvalidProtocolBufferException {
        HashSet<Pair<Integer, Integer>> convertResult = new HashSet<>();

        Cellbroadcastmetric.CellBroadcastChannelRangesProto channelRangesProto =
                Cellbroadcastmetric.CellBroadcastChannelRangesProto
                        .parser().parseFrom(arrayByte);

        for (Cellbroadcastmetric.CellBroadcastChannelRangeProto range :
                channelRangesProto.getChannelRangesList()) {
            convertResult.add(new Pair(range.getStart(), range.getEnd()));
        }

        return convertResult;
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
                Log.e(TAG, "CloneNotSupportedException error" + e);
            }
        }
    }

    /**
     * Convert ChannelSet to String
     *
     * @param curChRangeSet : channel range set
     */
    @VisibleForTesting
    public String getStringFromChannelSet(
            HashSet<Pair<Integer, Integer>> curChRangeSet) {
        StringJoiner strChannelList = new StringJoiner(CHANNEL_DELIMITER);
        curChRangeSet.forEach(pair -> strChannelList.add(
                pair.first.equals(pair.second)
                        ? String.valueOf(pair.first) : pair.first + RANGE_DELIMITER + pair.second));
        return strChannelList.toString();
    }

    /**
     * Convert String to ChannelSet
     *
     * @param strChannelRange : channel range string
     */
    public HashSet<Pair<Integer, Integer>> getChannelSetFromString(String strChannelRange) {
        String[] arrStringChannelRange = strChannelRange.split(CHANNEL_DELIMITER);
        HashSet<Pair<Integer, Integer>> channelSet = new HashSet<>();
        try {
            for (String chRange : arrStringChannelRange) {
                if (chRange.contains(RANGE_DELIMITER)) {
                    String[] range = chRange.split(RANGE_DELIMITER);
                    channelSet.add(
                            new Pair(Integer.parseInt(range[0].trim()),
                                    Integer.parseInt(range[1].trim())));
                } else {
                    channelSet.add(new Pair(Integer.parseInt(chRange.trim()),
                            Integer.parseInt(chRange.trim())));
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "NumberFormatException error" + e);
        }
        return channelSet;
    }

    /**
     * Create a new onConfigUpdated
     *
     * @param context       : Context
     * @param roamingMccMnc : country and operator information
     * @param curChRangeSet : channel range list information
     */
    public void onConfigUpdated(Context context, String roamingMccMnc,
            HashSet<Pair<Integer, Integer>> curChRangeSet) {

        if (curChRangeSet == null) return;

        SharedPreferences sp = context.getSharedPreferences(CBR_METRIC_PREF, MODE_PRIVATE);

        if (mConfigUpdatedCachedChannelSet == null) {
            mConfigUpdatedCachedChannelSet = getChannelSetFromString(
                    sp.getString(CBR_CONFIG_UPDATED, String.valueOf(0)));
        }

        if (!curChRangeSet.equals(mConfigUpdatedCachedChannelSet)) {
            logFeatureChangedAsNeeded(context);
            try {
                byte[] byteArrayChannelRange = convertToProtoBuffer(curChRangeSet);
                if (byteArrayChannelRange != null) {
                    CellBroadcastModuleStatsLog.write(
                            CellBroadcastModuleStatsLog.CB_CONFIG_UPDATED,
                            roamingMccMnc, byteArrayChannelRange);

                    String stringConfigurationUpdated = getStringFromChannelSet(curChRangeSet);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString(CBR_CONFIG_UPDATED, stringConfigurationUpdated);
                    editor.apply();

                    mConfigUpdatedCachedChannelSet.clear();
                    mConfigUpdatedCachedChannelSet.addAll(curChRangeSet);

                    if (VDBG) {
                        Log.d(TAG, "onConfigUpdated : roamingMccMnc is = " + roamingMccMnc
                                + " | channelRange is = " + stringConfigurationUpdated);
                    }
                }
            } catch (RuntimeException | IOException e) {
                Log.e(TAG, "Exception error occur " + e.getMessage());
            }
        }
    }

    /**
     * Create a new logMessageReported
     *
     * @param context  : Context
     * @param type     : radio type
     * @param source   : layer of reported message
     * @param serialNo : unique identifier of message
     * @param msgId    : service_category of message
     */
    void logMessageReported(Context context, int type, int source, int serialNo, int msgId) {
        if (VDBG) {
            Log.d(TAG,
                    "logMessageReported : " + type + " " + source + " " + serialNo + " "
                            + msgId);
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MESSAGE_REPORTED,
                type, source, serialNo, msgId);
    }

    /**
     * Create a new logMessageFiltered
     *
     * @param filterType : reason type of filtered
     * @param msg        : sms cell broadcast message information
     */
    void logMessageFiltered(int filterType, SmsCbMessage msg) {
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
    void logModuleError(int source, int errorType) {
        if (VDBG) {
            Log.d(TAG, "logModuleError : " + source + " " + errorType);
        }
        CellBroadcastModuleStatsLog.write(CellBroadcastModuleStatsLog.CB_MODULE_ERROR_REPORTED,
                source, errorType);
    }
}
