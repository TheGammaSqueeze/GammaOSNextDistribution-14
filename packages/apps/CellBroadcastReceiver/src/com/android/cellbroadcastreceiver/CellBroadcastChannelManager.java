/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.telephony.ServiceState.ROAMING_TYPE_NOT_ROAMING;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.VDBG;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRSRC_CBR;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_CHANNELRANGEPARSE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SmsCbMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.VisibleForTesting;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService.AlertType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * CellBroadcastChannelManager handles the additional cell broadcast channels that
 * carriers might enable through resources.
 * Syntax: "<channel id range>:[type=<alert type>], [emergency=true/false]"
 * For example,
 * <string-array name="additional_cbs_channels_strings" translatable="false">
 *     <item>"43008:type=earthquake, emergency=true"</item>
 *     <item>"0xAFEE:type=tsunami, emergency=true"</item>
 *     <item>"0xAC00-0xAFED:type=other"</item>
 *     <item>"1234-5678"</item>
 * </string-array>
 * If no tones are specified, the alert type will be set to DEFAULT. If emergency is not set,
 * by default it's not emergency.
 */
public class CellBroadcastChannelManager {

    private static final String TAG = "CBChannelManager";

    private static final int MAX_CACHE_SIZE = 3;
    private static List<Integer> sCellBroadcastRangeResourceKeys = new ArrayList<>(
            Arrays.asList(R.array.additional_cbs_channels_strings,
                    R.array.emergency_alerts_channels_range_strings,
                    R.array.cmas_presidential_alerts_channels_range_strings,
                    R.array.cmas_alert_extreme_channels_range_strings,
                    R.array.cmas_alerts_severe_range_strings,
                    R.array.cmas_amber_alerts_channels_range_strings,
                    R.array.required_monthly_test_range_strings,
                    R.array.exercise_alert_range_strings,
                    R.array.operator_defined_alert_range_strings,
                    R.array.etws_alerts_range_strings,
                    R.array.etws_test_alerts_range_strings,
                    R.array.public_safety_messages_channels_range_strings,
                    R.array.state_local_test_alert_range_strings,
                    R.array.geo_fencing_trigger_messages_range_strings
            ));

    private static Map<Integer, Map<Integer, List<CellBroadcastChannelRange>>>
            sAllCellBroadcastChannelRangesPerSub = new ArrayMap<>();
    private static Map<String, Map<Integer, List<CellBroadcastChannelRange>>>
            sAllCellBroadcastChannelRangesPerOperator = new ArrayMap<>();

    private static final Object mChannelRangesLock = new Object();

    private final Context mContext;

    private final int mSubId;

    private final String mOperator;

    private boolean mIsDebugBuild = false;

    /**
     * Cell broadcast channel range
     * A range is consisted by starting channel id, ending channel id, and the alert type
     */
    public static class CellBroadcastChannelRange {
        /** Defines the type of the alert. */
        private static final String KEY_TYPE = "type";
        /** Defines if the alert is emergency. */
        private static final String KEY_EMERGENCY = "emergency";
        /** Defines the network RAT for the alert. */
        private static final String KEY_RAT = "rat";
        /** Defines the scope of the alert. */
        private static final String KEY_SCOPE = "scope";
        /** Defines the vibration pattern of the alert. */
        private static final String KEY_VIBRATION = "vibration";
        /** Defines the duration of the alert. */
        private static final String KEY_ALERT_DURATION = "alert_duration";
        /** Defines if Do Not Disturb should be overridden for this alert */
        private static final String KEY_OVERRIDE_DND = "override_dnd";
        /** Defines whether writing alert message should exclude from SMS inbox. */
        private static final String KEY_EXCLUDE_FROM_SMS_INBOX = "exclude_from_sms_inbox";
        /** Define whether to display this cellbroadcast messages. */
        private static final String KEY_DISPLAY = "display";
        /** Define whether to enable this only in test/debug mode. */
        private static final String KEY_TESTING_MODE_ONLY = "testing_mode";
        /** Define the channels which not allow opt-out. */
        private static final String KEY_ALWAYS_ON = "always_on";
        /** Define the duration of screen on in milliseconds. */
        private static final String KEY_SCREEN_ON_DURATION = "screen_on_duration";
        /** Define whether to display warning icon in the alert dialog. */
        private static final String KEY_DISPLAY_ICON = "display_icon";
        /** Define whether to dismiss the alert dialog for outside touches */
        private static final String KEY_DISMISS_ON_OUTSIDE_TOUCH = "dismiss_on_outside_touch";
        /** Define whether to enable this only in userdebug/eng build. */
        private static final String KEY_DEBUG_BUILD_ONLY = "debug_build";
        /** Define the ISO-639-1 language code associated with the alert message. */
        private static final String KEY_LANGUAGE_CODE = "language";
        /** Define whether to display dialog and notification */
        private static final String KEY_DIALOG_WITH_NOTIFICATION = "dialog_with_notification";
        /** Define the pulsation pattern of the alert. */
        private static final String KEY_PULSATION = "pulsation";
        /**
         * Defines whether the channel needs language filter or not. True indicates that the alert
         * will only pop-up when the alert's language matches the device's language.
         */
        private static final String KEY_FILTER_LANGUAGE = "filter_language";


        public static final int SCOPE_UNKNOWN       = 0;
        public static final int SCOPE_CARRIER       = 1;
        public static final int SCOPE_DOMESTIC      = 2;
        public static final int SCOPE_INTERNATIONAL = 3;

        public static final int LEVEL_UNKNOWN          = 0;
        public static final int LEVEL_NOT_EMERGENCY    = 1;
        public static final int LEVEL_EMERGENCY        = 2;

        public int mStartId;
        public int mEndId;
        public AlertType mAlertType;
        public int mEmergencyLevel;
        public int mRanType;
        public int mScope;
        public int[] mVibrationPattern;
        public boolean mFilterLanguage;
        public boolean mDisplay;
        public boolean mTestMode;
        // by default no custom alert duration. play the alert tone with the tone's duration.
        public int mAlertDuration = -1;
        public boolean mOverrideDnd = false;
        // If enable_write_alerts_to_sms_inbox is true, write to sms inbox is enabled by default
        // for all channels except for channels which explicitly set to exclude from sms inbox.
        public boolean mWriteToSmsInbox = true;
        // only set to true for channels not allow opt-out. e.g, presidential alert.
        public boolean mAlwaysOn = false;
        // de default screen duration is 1min;
        public int mScreenOnDuration = 60000;
        // whether to display warning icon in the pop-up dialog;
        public boolean mDisplayIcon = true;
        // whether to dismiss the alert dialog on outside touch. Typically this should be false
        // to avoid accidental dismisses of emergency messages
        public boolean mDismissOnOutsideTouch = false;
        // Whether the channels are disabled
        public boolean mIsDebugBuildOnly = false;
        // This is used to override dialog title language
        public String mLanguageCode;
        // Display both ways dialog and notification
        public boolean mDisplayDialogWithNotification = false;
        // The pulsation pattern of the alert. The 1st parameter indicates the color to be changed.
        // The 2nd parameter indicates how long the pulsation will last. The 3rd and 4th parameters
        // indicate the intervals to set highlight color on/off.
        public int[] mPulsationPattern;

        public CellBroadcastChannelRange(Context context, int subId,
                Resources res, String channelRange) {
            mAlertType = AlertType.DEFAULT;
            mEmergencyLevel = LEVEL_UNKNOWN;
            mRanType = SmsCbMessage.MESSAGE_FORMAT_3GPP;
            mScope = SCOPE_UNKNOWN;

            mVibrationPattern = res.getIntArray(R.array.default_vibration_pattern);
            mFilterLanguage = false;
            // by default all received messages should be displayed.
            mDisplay = true;
            mTestMode = false;
            boolean hasVibrationPattern = false;
            mPulsationPattern = res.getIntArray(R.array.default_pulsation_pattern);

            int colonIndex = channelRange.indexOf(':');
            if (colonIndex != -1) {
                // Parse the alert type and emergency flag
                String[] pairs = channelRange.substring(colonIndex + 1).trim().split(",");
                for (String pair : pairs) {
                    pair = pair.trim();
                    String[] tokens = pair.split("=");
                    if (tokens.length == 2) {
                        String key = tokens[0].trim();
                        String value = tokens[1].trim();
                        switch (key) {
                            case KEY_TYPE:
                                mAlertType = AlertType.valueOf(value.toUpperCase());
                                break;
                            case KEY_EMERGENCY:
                                if (value.equalsIgnoreCase("true")) {
                                    mEmergencyLevel = LEVEL_EMERGENCY;
                                } else if (value.equalsIgnoreCase("false")) {
                                    mEmergencyLevel = LEVEL_NOT_EMERGENCY;
                                }
                                break;
                            case KEY_RAT:
                                mRanType = value.equalsIgnoreCase("cdma")
                                        ? SmsCbMessage.MESSAGE_FORMAT_3GPP2 :
                                        SmsCbMessage.MESSAGE_FORMAT_3GPP;
                                break;
                            case KEY_SCOPE:
                                if (value.equalsIgnoreCase("carrier")) {
                                    mScope = SCOPE_CARRIER;
                                } else if (value.equalsIgnoreCase("domestic")) {
                                    mScope = SCOPE_DOMESTIC;
                                } else if (value.equalsIgnoreCase("international")) {
                                    mScope = SCOPE_INTERNATIONAL;
                                }
                                break;
                            case KEY_VIBRATION:
                                String[] vibration = value.split("\\|");
                                if (vibration.length > 0) {
                                    mVibrationPattern = new int[vibration.length];
                                    for (int i = 0; i < vibration.length; i++) {
                                        mVibrationPattern[i] = Integer.parseInt(vibration[i]);
                                    }
                                    hasVibrationPattern = true;
                                }
                                break;
                            case KEY_FILTER_LANGUAGE:
                                if (value.equalsIgnoreCase("true")) {
                                    mFilterLanguage = true;
                                }
                                break;
                            case KEY_ALERT_DURATION:
                                mAlertDuration = Integer.parseInt(value);
                                break;
                            case KEY_OVERRIDE_DND:
                                if (value.equalsIgnoreCase("true")) {
                                    mOverrideDnd = true;
                                }
                                break;
                            case KEY_EXCLUDE_FROM_SMS_INBOX:
                                if (value.equalsIgnoreCase("true")) {
                                    mWriteToSmsInbox = false;
                                }
                                break;
                            case KEY_DISPLAY:
                                if (value.equalsIgnoreCase("false")) {
                                    mDisplay = false;
                                }
                                break;
                            case KEY_TESTING_MODE_ONLY:
                                if (value.equalsIgnoreCase("true")) {
                                    mTestMode = true;
                                }
                                break;
                            case KEY_ALWAYS_ON:
                                if (value.equalsIgnoreCase("true")) {
                                    mAlwaysOn = true;
                                }
                                break;
                            case KEY_SCREEN_ON_DURATION:
                                mScreenOnDuration = Integer.parseInt(value);
                                break;
                            case KEY_DISPLAY_ICON:
                                if (value.equalsIgnoreCase("false")) {
                                    mDisplayIcon = false;
                                }
                                break;
                            case KEY_DISMISS_ON_OUTSIDE_TOUCH:
                                if (value.equalsIgnoreCase("true")) {
                                    mDismissOnOutsideTouch = true;
                                }
                                break;
                            case KEY_DEBUG_BUILD_ONLY:
                                if (value.equalsIgnoreCase("true")) {
                                    mIsDebugBuildOnly = true;
                                }
                                break;
                            case KEY_LANGUAGE_CODE:
                                mLanguageCode = value;
                                break;
                            case KEY_DIALOG_WITH_NOTIFICATION:
                                if (value.equalsIgnoreCase("true")) {
                                    mDisplayDialogWithNotification = true;
                                }
                                break;
                            case KEY_PULSATION:
                                String[] pulsation = value.split("\\|");
                                if (pulsation.length > 0) {
                                    mPulsationPattern = new int[pulsation.length];
                                    for (int i = 0; i < pulsation.length; i++) {
                                        try {
                                            mPulsationPattern[i] = Long.decode(
                                                    pulsation[i]).intValue();
                                        } catch (NumberFormatException e) {
                                            Log.wtf(TAG, "Bad pulsation pattern[" + i + "]:"
                                                    + pulsation[i]);
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
                channelRange = channelRange.substring(0, colonIndex).trim();
            }

            // If alert type is info, override vibration pattern
            if (!hasVibrationPattern && mAlertType.equals(AlertType.INFO)) {
                mVibrationPattern = res.getIntArray(R.array.default_notification_vibration_pattern);
            }

            // Parse the channel range
            int dashIndex = channelRange.indexOf('-');
            if (dashIndex != -1) {
                // range that has start id and end id
                mStartId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                mEndId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
            } else {
                // Not a range, only a single id
                mStartId = mEndId = Integer.decode(channelRange);
            }
        }

        @Override
        public String toString() {
            return "Range:[channels=" + mStartId + "-" + mEndId + ",emergency level="
                    + mEmergencyLevel + ",type=" + mAlertType + ",scope=" + mScope + ",vibration="
                    + Arrays.toString(mVibrationPattern) + ",alertDuration=" + mAlertDuration
                    + ",filter_language=" + mFilterLanguage + ",override_dnd=" + mOverrideDnd
                    + ",display=" + mDisplay + ",testMode=" + mTestMode + ",mAlwaysOn="
                    + mAlwaysOn + ",ScreenOnDuration=" + mScreenOnDuration + ", displayIcon="
                    + mDisplayIcon + "dismissOnOutsideTouch=" + mDismissOnOutsideTouch
                    + ", mIsDebugBuildOnly =" + mIsDebugBuildOnly
                    + ", languageCode=" + mLanguageCode
                    + ", mDisplayDialogWithNotification=" + mDisplayDialogWithNotification
                    + ", mPulsationPattern=" + Arrays.toString(mPulsationPattern) + "]";
        }
    }

    /**
     * Constructor
     *
     * @param context Context
     * @param subId Subscription index
     */
    public CellBroadcastChannelManager(Context context, int subId) {
        this(context, subId, CellBroadcastReceiver.getRoamingOperatorSupported(context),
                SystemProperties.getInt("ro.debuggable", 0) == 1);
    }

    public CellBroadcastChannelManager(Context context, int subId, @Nullable String operator) {
        this(context, subId, operator, SystemProperties.getInt("ro.debuggable", 0) == 1);
    }

    @VisibleForTesting
    public CellBroadcastChannelManager(Context context, int subId,
            String operator, boolean isDebugBuild) {
        mContext = context;
        mSubId = subId;
        mOperator = operator;
        mIsDebugBuild = isDebugBuild;
        initAsNeeded();
    }

    /**
     * Parse channel ranges from resources, and initialize the cache as needed
     */
    private void initAsNeeded() {
        if (!TextUtils.isEmpty(mOperator)) {
            synchronized (mChannelRangesLock) {
                if (!sAllCellBroadcastChannelRangesPerOperator.containsKey(mOperator)) {
                    if (VDBG) {
                        log("init for operator: " + mOperator);
                    }
                    if (sAllCellBroadcastChannelRangesPerOperator.size() == MAX_CACHE_SIZE) {
                        sAllCellBroadcastChannelRangesPerOperator.clear();
                    }
                    sAllCellBroadcastChannelRangesPerOperator.put(mOperator,
                            getChannelRangesMapFromResoures(CellBroadcastSettings
                                    .getResourcesByOperator(mContext, mSubId, mOperator)));
                }
            }
        }

        synchronized (mChannelRangesLock) {
            if (!sAllCellBroadcastChannelRangesPerSub.containsKey(mSubId)) {
                if (sAllCellBroadcastChannelRangesPerSub.size() == MAX_CACHE_SIZE) {
                    sAllCellBroadcastChannelRangesPerSub.clear();
                }
                if (VDBG) {
                    log("init for sub: " + mSubId);
                }
                sAllCellBroadcastChannelRangesPerSub.put(mSubId,
                        getChannelRangesMapFromResoures(CellBroadcastSettings
                                .getResources(mContext, mSubId)));
            }
        }
    }

    private @NonNull Map<Integer, List<CellBroadcastChannelRange>> getChannelRangesMapFromResoures(
            @NonNull Resources res) {
        Map<Integer, List<CellBroadcastChannelRange>> map = new ArrayMap<>();

        for (int key : sCellBroadcastRangeResourceKeys) {
            String[] ranges = res.getStringArray(key);
            if (ranges != null) {
                List<CellBroadcastChannelRange> rangesList = new ArrayList<>();
                for (String range : ranges) {
                    try {
                        if (VDBG) {
                            log("parse channel range: " + range);
                        }
                        CellBroadcastChannelRange r =
                                new CellBroadcastChannelRange(mContext, mSubId, res, range);
                        // Bypass if the range is disabled
                        if (r.mIsDebugBuildOnly && !mIsDebugBuild) {
                            continue;
                        }
                        rangesList.add(r);
                    } catch (Exception e) {
                        CellBroadcastReceiverMetrics.getInstance().logModuleError(
                                ERRSRC_CBR, ERRTYPE_CHANNELRANGEPARSE);
                        loge("Failed to parse \"" + range + "\". e=" + e);
                    }
                }
                map.put(key, rangesList);
            }
        }

        return map;
    }

    /**
     * Get cell broadcast channels enabled by the carriers from resource key
     *
     * @param key Resource key
     *
     * @return The list of channel ranges enabled by the carriers.
     */
    public @NonNull List<CellBroadcastChannelRange> getCellBroadcastChannelRanges(int key) {
        List<CellBroadcastChannelRange> result = null;

        synchronized (mChannelRangesLock) {
            initAsNeeded();

            // Check the config per network first if applicable
            if (!TextUtils.isEmpty(mOperator)) {
                result = sAllCellBroadcastChannelRangesPerOperator.get(mOperator).get(key);
            }

            if (result == null) {
                result = sAllCellBroadcastChannelRangesPerSub.get(mSubId).get(key);
            }
        }

        return result == null ? new ArrayList<>() : result;
    }

    /**
     * Get all cell broadcast channels
     *
     * @return all cell broadcast channels
     */
    public @NonNull List<CellBroadcastChannelRange> getAllCellBroadcastChannelRanges() {
        final List<CellBroadcastChannelRange> result = new ArrayList<>();
        synchronized (mChannelRangesLock) {
            if (!TextUtils.isEmpty(mOperator)
                    && sAllCellBroadcastChannelRangesPerOperator.containsKey(mOperator)) {
                sAllCellBroadcastChannelRangesPerOperator.get(mOperator).forEach(
                        (k, v)->result.addAll(v));
            }

            sAllCellBroadcastChannelRangesPerSub.get(mSubId).forEach((k, v)->result.addAll(v));
        }
        return result;
    }

    /**
     * Clear broadcast channel range list
     */
    public static void clearAllCellBroadcastChannelRanges() {
        synchronized (mChannelRangesLock) {
            Log.d(TAG, "Clear channel range list");
            sAllCellBroadcastChannelRangesPerSub.clear();
            sAllCellBroadcastChannelRangesPerOperator.clear();
        }
    }

    /**
     * @param channel Cell broadcast message channel
     * @param key Resource key
     *
     * @return {@code TRUE} if the input channel is within the channel range defined from resource.
     * return {@code FALSE} otherwise
     */
    public boolean checkCellBroadcastChannelRange(int channel, int key) {
        return getCellBroadcastChannelResourcesKey(channel) == key;
    }

    /**
     * Get the resources key for the channel
     * @param channel Cell broadcast message channel
     *
     * @return 0 if the key is not found, otherwise the value of the resources key
     */
    public int getCellBroadcastChannelResourcesKey(int channel) {
        Pair<Integer, CellBroadcastChannelRange> p = findChannelRange(channel);

        return p != null ? p.first : 0;
    }

    /**
     * Get the CellBroadcastChannelRange for the channel
     * @param channel Cell broadcast message channel
     *
     * @return the CellBroadcastChannelRange for the channel, null if not found
     */
    public @Nullable CellBroadcastChannelRange getCellBroadcastChannelRange(int channel) {
        Pair<Integer, CellBroadcastChannelRange> p = findChannelRange(channel);

        return p != null ? p.second : null;
    }

    private @Nullable Pair<Integer, CellBroadcastChannelRange> findChannelRange(int channel) {
        if (!TextUtils.isEmpty(mOperator)) {
            Pair<Integer, CellBroadcastChannelRange> p = findChannelRange(
                    sAllCellBroadcastChannelRangesPerOperator.get(mOperator), channel);
            if (p != null) {
                return p;
            }
        }

        return findChannelRange(sAllCellBroadcastChannelRangesPerSub.get(mSubId), channel);
    }

    private @Nullable Pair<Integer, CellBroadcastChannelRange> findChannelRange(
            Map<Integer, List<CellBroadcastChannelRange>> channelRangeMap, int channel) {
        if (channelRangeMap != null) {
            for (Map.Entry<Integer, List<CellBroadcastChannelRange>> entry
                    : channelRangeMap.entrySet()) {
                for (CellBroadcastChannelRange range : entry.getValue()) {
                    if (channel >= range.mStartId && channel <= range.mEndId
                            && checkScope(range.mScope)) {
                        return new Pair<>(entry.getKey(), range);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if the channel scope matches the current network condition.
     *
     * @param rangeScope Range scope. Must be SCOPE_CARRIER, SCOPE_DOMESTIC, or SCOPE_INTERNATIONAL.
     * @return True if the scope matches the current network roaming condition.
     */
    public boolean checkScope(int rangeScope) {
        if (rangeScope == CellBroadcastChannelRange.SCOPE_UNKNOWN) return true;
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(mSubId);
        ServiceState ss = tm.getServiceState();
        if (ss != null) {
            NetworkRegistrationInfo regInfo = ss.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_CS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (regInfo != null) {
                if (regInfo.getRegistrationState()
                        == NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                        || regInfo.getRegistrationState()
                        == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING
                        || regInfo.isEmergencyEnabled()) {
                    int voiceRoamingType = regInfo.getRoamingType();
                    if (voiceRoamingType == ROAMING_TYPE_NOT_ROAMING) {
                        return true;
                    } else if (voiceRoamingType == ServiceState.ROAMING_TYPE_DOMESTIC
                            && rangeScope == CellBroadcastChannelRange.SCOPE_DOMESTIC) {
                        return true;
                    } else if (voiceRoamingType == ServiceState.ROAMING_TYPE_INTERNATIONAL
                            && rangeScope == CellBroadcastChannelRange.SCOPE_INTERNATIONAL) {
                        return true;
                    }
                    return false;
                }
            }
        }
        // If we can't determine the scope, for safe we should assume it's in.
        return true;
    }

    /**
     * Return corresponding cellbroadcast range where message belong to
     *
     * @param message Cell broadcast message
     */
    public CellBroadcastChannelRange getCellBroadcastChannelRangeFromMessage(SmsCbMessage message) {
        if (mSubId != message.getSubscriptionId()) {
            Log.e(TAG, "getCellBroadcastChannelRangeFromMessage: This manager is created for "
                    + "sub " + mSubId + ", should not be used for message from sub "
                    + message.getSubscriptionId());
        }

        return getCellBroadcastChannelRange(message.getServiceCategory());
    }

    /**
     * Check if the cell broadcast message is an emergency message or not
     *
     * @param message Cell broadcast message
     * @return True if the message is an emergency message, otherwise false.
     */
    public boolean isEmergencyMessage(SmsCbMessage message) {
        if (message == null) {
            return false;
        }

        if (mSubId != message.getSubscriptionId()) {
            Log.e(TAG, "This manager is created for sub " + mSubId
                    + ", should not be used for message from sub " + message.getSubscriptionId());
        }

        int id = message.getServiceCategory();
        CellBroadcastChannelRange range = getCellBroadcastChannelRange(id);

        if (range != null) {
            switch (range.mEmergencyLevel) {
                case CellBroadcastChannelRange.LEVEL_EMERGENCY:
                    Log.d(TAG, "isEmergencyMessage: true, message id = " + id);
                    return true;
                case CellBroadcastChannelRange.LEVEL_NOT_EMERGENCY:
                    Log.d(TAG, "isEmergencyMessage: false, message id = " + id);
                    return false;
                case CellBroadcastChannelRange.LEVEL_UNKNOWN:
                default:
                    break;
            }
        }

        Log.d(TAG, "isEmergencyMessage: " + message.isEmergencyMessage()
                + ", message id = " + id);
        // If the configuration does not specify whether the alert is emergency or not, use the
        // emergency property from the message itself, which is checking if the channel is between
        // MESSAGE_ID_PWS_FIRST_IDENTIFIER (4352) and MESSAGE_ID_PWS_LAST_IDENTIFIER (6399).
        return message.isEmergencyMessage();
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
