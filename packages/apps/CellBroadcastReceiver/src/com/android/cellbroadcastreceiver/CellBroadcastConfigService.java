/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.VDBG;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRSRC_CBR;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_CHANNEL_R;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_ENABLECHANNEL;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.telephony.CellBroadcastIdRange;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    private HashSet<Pair<Integer, Integer>> mChannelRangeForMetric = new HashSet<>();

    @VisibleForTesting
    public static final String ACTION_ENABLE_CHANNELS = "ACTION_ENABLE_CHANNELS";
    public static final String ACTION_UPDATE_SETTINGS_FOR_CARRIER = "UPDATE_SETTINGS_FOR_CARRIER";
    public static final String ACTION_RESET_SETTINGS_AS_NEEDED = "RESET_SETTINGS_AS_NEEDED";

    public static final String EXTRA_SUB = "SUB";

    private static final String ACTION_SET_CHANNELS_DONE =
            "android.cellbroadcast.compliancetest.SET_CHANNELS_DONE";
    /**
     * CbConfig is consisted by starting channel id, ending channel id, and ran type,
     * whether it should be enabled or not
     */
    public static class CbConfig {
        public int mStartId;
        public int mEndId;
        public int mRanType;
        public boolean mEnable;

        public CbConfig(int startId, int endId, int type, boolean enable) {
            this.mStartId = startId;
            this.mEndId = endId;
            this.mRanType = type;
            this.mEnable = enable;
        }
    }

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_ENABLE_CHANNELS.equals(intent.getAction())) {
            try {
                SubscriptionManager subManager = (SubscriptionManager) getApplicationContext()
                        .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                if (subManager != null) {
                    mChannelRangeForMetric.clear();
                    // Retrieve all the active subscription inside and enable cell broadcast
                    // messages on all subs. The duplication detection will be done at the
                    // frameworks.
                    int[] subIds = getActiveSubIdList(subManager);
                    if (subIds.length != 0) {
                        for (int subId : subIds) {
                            log("Enable CellBroadcast on sub " + subId);
                            enableCellBroadcastChannels(subId);
                            if (!SdkLevel.isAtLeastU()) {
                                broadcastSetChannelsIsDone(subId);
                            }
                        }
                    } else {
                        // For no sim scenario.
                        enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
                    }

                    if (!mChannelRangeForMetric.isEmpty()) {
                        String roamingOperator = CellBroadcastReceiver.getRoamingOperatorSupported(
                                this);
                        CellBroadcastReceiverMetrics.getInstance().onConfigUpdated(
                                getApplicationContext(),
                                roamingOperator.isEmpty() ? "" : roamingOperator,
                                mChannelRangeForMetric);
                    }
                }
            } catch (Exception ex) {
                CellBroadcastReceiverMetrics.getInstance().logModuleError(
                        ERRSRC_CBR, ERRTYPE_ENABLECHANNEL);
                Log.e(TAG, "exception enabling cell broadcast channels", ex);
            }
        } else if (ACTION_UPDATE_SETTINGS_FOR_CARRIER.equals(intent.getAction())) {
            Context c = getApplicationContext();
            if (CellBroadcastSettings.hasAnyPreferenceChanged(c)) {
                Log.d(TAG, "Preference has changed from user set, posting notification.");

                CellBroadcastAlertService.createNotificationChannels(c);
                Intent settingsIntent = new Intent(c, CellBroadcastSettings.class);
                ActivityOptions options = ActivityOptions.makeBasic();
                if (SdkLevel.isAtLeastU()) {
                    options.setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                }
                PendingIntent pi = PendingIntent.getActivity(c,
                        CellBroadcastAlertService.SETTINGS_CHANGED_NOTIFICATION_ID, settingsIntent,
                        PendingIntent.FLAG_ONE_SHOT
                                | PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE, options.toBundle());

                Notification.Builder builder = new Notification.Builder(c,
                        CellBroadcastAlertService.NOTIFICATION_CHANNEL_SETTINGS_UPDATES)
                        .setCategory(Notification.CATEGORY_SYSTEM)
                        .setContentTitle(c.getString(R.string.notification_cb_settings_changed_title))
                        .setContentText(c.getString(R.string.notification_cb_settings_changed_text))
                        .setSmallIcon(R.drawable.ic_settings_gear_outline_24dp)
                        .setContentIntent(pi)
                        .setAutoCancel(true);
                NotificationManager notificationManager = c.getSystemService(
                        NotificationManager.class);
                notificationManager.notify(
                        CellBroadcastAlertService.SETTINGS_CHANGED_NOTIFICATION_ID,
                        builder.build());
            }
            Log.e(TAG, "Reset all preferences");
            resetAllPreferences();
        } else if (ACTION_RESET_SETTINGS_AS_NEEDED.equals(intent.getAction())) {
            Resources res = getResources(intent.getIntExtra(
                    EXTRA_SUB, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID), null);
            if (!CellBroadcastSettings.hasAnyPreferenceChanged(getApplicationContext())
                    && (isMasterToggleEnabled() != res.getBoolean(
                            R.bool.master_toggle_enabled_default))) {
                Log.d(TAG, "Reset all preferences as no user changes and master toggle is"
                        + " different as the config");
                resetAllPreferences();
            }
        }
    }

    /**
     * Encapsulate the static method to reset all preferences for testing purpose.
     */
    @VisibleForTesting
    public void resetAllPreferences() {
        CellBroadcastSettings.resetAllPreferences(getApplicationContext());
    }

    @NonNull
    private int[] getActiveSubIdList(SubscriptionManager subMgr) {
        List<SubscriptionInfo> subInfos = subMgr.getActiveSubscriptionInfoList();
        int size = subInfos != null ? subInfos.size() : 0;
        int[] subIds = new int[size];
        for (int i = 0; i < size; i++) {
            subIds[i] = subInfos.get(i).getSubscriptionId();
        }
        return subIds;
    }

    /**
     * reset cell broadcast ranges
     */
    @VisibleForTesting
    public void resetCellBroadcastChannels(int subId) {
        if (SdkLevel.isAtLeastU()) {
            return;
        }
        SmsManager manager;
        if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            manager = SmsManager.getSmsManagerForSubscriptionId(subId);
        } else {
            manager = SmsManager.getDefault();
        }
        // SmsManager.resetAllCellBroadcastRanges is a new @SystemAPI in S. We need to support
        // backward compatibility as the module need to run on R build as well.
        if (SdkLevel.isAtLeastS()) {
            manager.resetAllCellBroadcastRanges();
        } else {
            try {
                Method method = SmsManager.class.getDeclaredMethod("resetAllCellBroadcastRanges");
                method.invoke(manager);
            } catch (Exception e) {
                CellBroadcastReceiverMetrics.getInstance().logModuleError(
                        ERRSRC_CBR, ERRTYPE_CHANNEL_R);
                log("Can't reset cell broadcast ranges. e=" + e);
            }
        }
    }

    /**
     * Enable cell broadcast messages channels. Messages can be only received on the
     * enabled channels.
     *
     * @param subId Subscription index
     */
    @VisibleForTesting
    public void enableCellBroadcastChannels(int subId) {
        resetCellBroadcastChannels(subId);

        List<CbConfig> config = getCellBroadcastChannelsConfig(subId, null);

        String roamingOperator = CellBroadcastReceiver.getRoamingOperatorSupported(this);
        if (!TextUtils.isEmpty(roamingOperator)) {
            config.addAll(getCellBroadcastChannelsConfig(subId, roamingOperator));
            config = mergeConfigAsNeeded(config);
        }
        setCellBroadcastRange(subId, config);
    }

    private List<CbConfig> getCellBroadcastChannelsConfig(int subId, String roamingOperator) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources res = getResources(subId, roamingOperator);
        boolean isRoaming = !TextUtils.isEmpty(roamingOperator);

        // boolean for each user preference checkbox, true for checked, false for unchecked
        // Note: If enableAlertsMasterToggle is false, it disables ALL emergency broadcasts
        // except for always-on alerts e.g, presidential. i.e. to receive CMAS severe alerts, both
        // enableAlertsMasterToggle AND enableCmasSevereAlerts must be true.
        boolean enableAlertsMasterToggle = isRoaming
                ? res.getBoolean(R.bool.master_toggle_enabled_default) : isMasterToggleEnabled();

        boolean enableEtwsAlerts = enableAlertsMasterToggle;

        // CMAS Presidential must be always on (See 3GPP TS 22.268 Section 6.2) regardless
        // user's preference
        boolean enablePresidential = true;

        boolean enableCmasExtremeAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.extreme_threat_alerts_enabled_default)
                : prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true));

        boolean enableCmasSevereAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.severe_threat_alerts_enabled_default)
                : prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true));

        boolean enableCmasAmberAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.amber_alerts_enabled_default)
                : prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true));

        boolean enableTestAlerts = enableAlertsMasterToggle && (isRoaming
                ? (CellBroadcastSettings
                        .isTestAlertsToggleVisible(getApplicationContext(), roamingOperator)
                        && res.getBoolean(R.bool.test_alerts_enabled_default))
                : (CellBroadcastSettings.isTestAlertsToggleVisible(getApplicationContext())
                        && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS,
                        false)));

        boolean enableExerciseAlerts = enableAlertsMasterToggle && (isRoaming
                ? (res.getBoolean(R.bool.show_separate_exercise_settings)
                && res.getBoolean(R.bool.test_exercise_alerts_enabled_default))
                : (res.getBoolean(R.bool.show_separate_exercise_settings)
                        && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS,
                        false)));

        boolean enableOperatorDefined = enableAlertsMasterToggle && (isRoaming
                ? (res.getBoolean(R.bool.show_separate_operator_defined_settings)
                && res.getBoolean(R.bool.test_operator_defined_alerts_enabled_default))
                : (res.getBoolean(R.bool.show_separate_operator_defined_settings)
                        && prefs.getBoolean(CellBroadcastSettings.KEY_OPERATOR_DEFINED_ALERTS,
                        false)));

        boolean enableAreaUpdateInfoAlerts = isRoaming
                ? (res.getBoolean(R.bool.config_showAreaUpdateInfoSettings)
                && res.getBoolean(R.bool.area_update_info_alerts_enabled_default))
                : (res.getBoolean(R.bool.config_showAreaUpdateInfoSettings)
                        && prefs.getBoolean(
                                CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS, false));

        boolean enablePublicSafetyMessagesChannelAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.public_safety_messages_enabled_default)
                : prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_PUBLIC_SAFETY_MESSAGES, true));

        boolean enableStateLocalTestAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.state_local_test_alerts_enabled_default)
                : (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS,
                        false) || (!res.getBoolean(R.bool.show_state_local_test_settings)
                        && res.getBoolean(R.bool.state_local_test_alerts_enabled_default))));

        boolean enableEmergencyAlerts = enableAlertsMasterToggle && (isRoaming
                ? res.getBoolean(R.bool.emergency_alerts_enabled_default)
                : prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true));

        return getCellBroadcastChannelsConfig(subId, roamingOperator, enableAlertsMasterToggle,
                enableEtwsAlerts, enablePresidential, enableCmasExtremeAlerts,
                enableCmasSevereAlerts, enableCmasAmberAlerts, enableTestAlerts,
                enableExerciseAlerts, enableOperatorDefined, enableAreaUpdateInfoAlerts,
                enablePublicSafetyMessagesChannelAlerts, enableStateLocalTestAlerts,
                enableEmergencyAlerts, true);
    }

    private List<CbConfig> getCellBroadcastChannelsConfig(int subId, @NonNull String operator,
            boolean enableAlertsMasterToggle, boolean enableEtwsAlerts, boolean enablePresidential,
            boolean enableCmasExtremeAlerts, boolean enableCmasSevereAlerts,
            boolean enableCmasAmberAlerts, boolean enableTestAlerts, boolean enableExerciseAlerts,
            boolean enableOperatorDefined, boolean enableAreaUpdateInfoAlerts,
            boolean enablePublicSafetyMessagesChannelAlerts, boolean enableStateLocalTestAlerts,
            boolean enableEmergencyAlerts, boolean enableGeoFencingTriggerMessage) {

        if (VDBG) {
            log("setCellBroadcastChannelsEnabled for " + subId + ", operator: " + operator);
            log("enableAlertsMasterToggle = " + enableAlertsMasterToggle);
            log("enableEtwsAlerts = " + enableEtwsAlerts);
            log("enablePresidential = " + enablePresidential);
            log("enableCmasExtremeAlerts = " + enableCmasExtremeAlerts);
            log("enableCmasSevereAlerts = " + enableCmasSevereAlerts);
            log("enableCmasAmberAlerts = " + enableCmasAmberAlerts);
            log("enableTestAlerts = " + enableTestAlerts);
            log("enableExerciseAlerts = " + enableExerciseAlerts);
            log("enableOperatorDefinedAlerts = " + enableOperatorDefined);
            log("enableAreaUpdateInfoAlerts = " + enableAreaUpdateInfoAlerts);
            log("enablePublicSafetyMessagesChannelAlerts = "
                    + enablePublicSafetyMessagesChannelAlerts);
            log("enableStateLocalTestAlerts = " + enableStateLocalTestAlerts);
            log("enableEmergencyAlerts = " + enableEmergencyAlerts);
            log("enableGeoFencingTriggerMessage = " + enableGeoFencingTriggerMessage);
        }

        List<CbConfig> cbConfigList = new ArrayList<>();
        boolean isEnableOnly = !TextUtils.isEmpty(operator);
        CellBroadcastChannelManager channelManager = new CellBroadcastChannelManager(
                getApplicationContext(), subId, operator);
        /** Enable CMAS series messages. */

        // Enable/Disable Presidential messages.
        List<CellBroadcastChannelRange> ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.cmas_presidential_alerts_channels_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enablePresidential;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable CMAS extreme messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.cmas_alert_extreme_channels_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableCmasExtremeAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable CMAS severe messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.cmas_alerts_severe_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableCmasSevereAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable CMAS amber alert messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.cmas_amber_alerts_channels_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableCmasAmberAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable test messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.required_monthly_test_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableTestAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable exercise test messages.
        // This could either controlled by main test toggle or separate exercise test toggle.
        ranges = channelManager.getCellBroadcastChannelRanges(R.array.exercise_alert_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || (enableTestAlerts || enableExerciseAlerts);
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable operator defined test messages.
        // This could either controlled by main test toggle or separate operator defined test toggle
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.operator_defined_alert_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || (enableTestAlerts || enableOperatorDefined);
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable GSM ETWS messages.
        ranges = channelManager.getCellBroadcastChannelRanges(R.array.etws_alerts_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableEtwsAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable GSM ETWS test messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.etws_test_alerts_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableTestAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable GSM public safety messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.public_safety_messages_channels_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enablePublicSafetyMessagesChannelAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable GSM state/local test alerts.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.state_local_test_alert_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableStateLocalTestAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable GSM geo-fencing trigger messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.geo_fencing_trigger_messages_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableGeoFencingTriggerMessage;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable non-CMAS series messages.
        ranges = channelManager.getCellBroadcastChannelRanges(
                R.array.emergency_alerts_channels_range_strings);
        for (CellBroadcastChannelRange range : ranges) {
            boolean enable = range.mAlwaysOn || enableEmergencyAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }

        // Enable/Disable additional channels based on carrier specific requirement.
        List<CellBroadcastChannelRange> additionChannelRanges =
                channelManager.getCellBroadcastChannelRanges(
                        R.array.additional_cbs_channels_strings);

        for (CellBroadcastChannelRange range : additionChannelRanges) {
            boolean enableAlerts;
            switch (range.mAlertType) {
                case AREA:
                    enableAlerts = enableAreaUpdateInfoAlerts;
                    break;
                case TEST:
                    enableAlerts = enableTestAlerts;
                    break;
                default:
                    enableAlerts = enableAlertsMasterToggle;
            }
            boolean enable = range.mAlwaysOn || enableAlerts;
            if (enable || !isEnableOnly) {
                cbConfigList.add(
                        new CbConfig(range.mStartId, range.mEndId, range.mRanType, enable));
            }
        }
        return cbConfigList;
    }

    /**
     * Enable/disable cell broadcast with messages id range
     *
     * @param subId         Subscription index
     * @param ranges        Cell broadcast id ranges
     */
    private void setCellBroadcastRange(int subId, List<CbConfig> ranges) {
        SmsManager manager;
        if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            manager = SmsManager.getSmsManagerForSubscriptionId(subId);
        } else {
            manager = SmsManager.getDefault();
        }
        List<CellBroadcastIdRange> channelIdRanges = new ArrayList<>();

        if (ranges != null) {
            for (CbConfig range : ranges) {
                boolean enable = range.mEnable;
                if (SdkLevel.isAtLeastU()) {
                    if (VDBG) {
                        log("enableCellBroadcastRange[" + range.mStartId + "-"
                                + range.mEndId + "], type:" + range.mRanType
                                + ", enable:" + enable);
                    }
                    if (enable && (subId == SubscriptionManager.getDefaultSubscriptionId())) {
                        mChannelRangeForMetric.add(new Pair(range.mStartId, range.mEndId));
                    }
                    CellBroadcastIdRange cbRange = new CellBroadcastIdRange(range.mStartId,
                            range.mEndId, range.mRanType, enable);
                    channelIdRanges.add(cbRange);
                } else {
                    if (VDBG) {
                        log("enableCellBroadcastRange[" + range.mStartId + "-"
                                + range.mEndId + "], type:" + range.mRanType);
                    }
                    if (enable) {
                        if (subId == SubscriptionManager.getDefaultSubscriptionId()) {
                            mChannelRangeForMetric.add(new Pair(range.mStartId, range.mEndId));
                        }
                        manager.enableCellBroadcastRange(range.mStartId, range.mEndId,
                                range.mRanType);
                    } else {
                        if (VDBG) {
                            log("disableCellBroadcastRange[" + range.mStartId + "-"
                                    + range.mEndId + "], type:" + range.mRanType);
                        }
                        manager.disableCellBroadcastRange(range.mStartId, range.mEndId,
                                range.mRanType);
                    }
                }
            }
            if (SdkLevel.isAtLeastU()) {
                TelephonyManager tm = getApplicationContext().getSystemService(
                        TelephonyManager.class).createForSubscriptionId(subId);
                try {
                    tm.setCellBroadcastIdRanges(channelIdRanges, Runnable::run,  result -> {
                        if (result != TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS) {
                            Log.e(TAG, "fails to setCellBroadcastRanges, result = " + result);
                        }
                    });
                } catch (RuntimeException e) {
                    Log.e(TAG, "fails to setCellBroadcastRanges");
                }
            }
        }
    }

    /**
     * Merge the conflicted CbConfig in the list as needed
     * @param inputRanges input config lists
     * @return the list of CbConfig without conflict
     */
    @VisibleForTesting
    public static List<CbConfig> mergeConfigAsNeeded(List<CbConfig> inputRanges) {
        inputRanges.sort((r1, r2) -> r1.mRanType != r2.mRanType ? r1.mRanType - r2.mRanType
                : (r1.mStartId != r2.mStartId ? r1.mStartId - r2.mStartId
                        : r2.mEndId - r1.mEndId));
        final List<CbConfig> ranges = new ArrayList<>();
        inputRanges.forEach(r -> {
            if (ranges.isEmpty() || ranges.get(ranges.size() - 1).mRanType != r.mRanType
                    || ranges.get(ranges.size() - 1).mEndId < r.mStartId) {
                ranges.add(new CbConfig(r.mStartId, r.mEndId, r.mRanType, r.mEnable));
            } else {
                CbConfig range = ranges.get(ranges.size() - 1);
                if (range.mEnable == r.mEnable) {
                    if (r.mEndId > range.mEndId) {
                        ranges.set(ranges.size() - 1, new CbConfig(
                                range.mStartId, r.mEndId, range.mRanType, range.mEnable));
                    }
                } else if (!range.mEnable) {
                    if (range.mStartId < r.mStartId) {
                        if (range.mEndId <= r.mEndId) {
                            ranges.set(ranges.size() - 1, new CbConfig(range.mStartId,
                                    r.mStartId - 1, range.mRanType, false));
                            ranges.add(new CbConfig(r.mStartId, r.mEndId, r.mRanType, true));
                        } else {
                            ranges.set(ranges.size() - 1, new CbConfig(range.mStartId,
                                    r.mStartId - 1, range.mRanType, false));
                            ranges.add(new CbConfig(r.mStartId, r.mEndId, r.mRanType, true));
                            ranges.add(new CbConfig(r.mEndId + 1, range.mEndId,
                                    range.mRanType, false));
                        }
                    } else {
                        if (range.mEndId <= r.mEndId) {
                            ranges.set(ranges.size() - 1, new CbConfig(r.mStartId,
                                    r.mEndId, range.mRanType, true));
                        } else if (range.mStartId <= r.mEndId) {
                            ranges.set(ranges.size() - 1, new CbConfig(r.mStartId,
                                    r.mEndId, range.mRanType, true));
                            ranges.add(new CbConfig(r.mEndId + 1, range.mEndId,
                                    r.mRanType, false));
                        }
                    }
                } else {
                    if (range.mEndId < r.mEndId) {
                        ranges.add(new CbConfig(range.mEndId + 1, r.mEndId, r.mRanType, false));
                    }
                }
            }
        });
        return ranges;
    }

    /**
     * Get resource according to the operator or subId
     *
     * @param subId    Subscription index
     * @param operator Operator numeric, the resource will be retrieved by it if it is no null,
     *                 otherwise, by the sub id.
     */
    @VisibleForTesting
    public Resources getResources(int subId, String operator) {
        if (operator == null) {
            return CellBroadcastSettings.getResources(this, subId);
        }
        return CellBroadcastSettings.getResourcesByOperator(this, subId, operator);
    }

    private void broadcastSetChannelsIsDone(int subId) {
        if (!isMockModemRunning()) {
            return;
        }
        Intent intent = new Intent(ACTION_SET_CHANNELS_DONE);
        intent.putExtra("sub_id", subId);
        sendBroadcast(intent, Manifest.permission.READ_CELL_BROADCASTS);
        Log.d(TAG, "broadcastSetChannelsIsDone subId = " + subId);
    }

    private boolean isMasterToggleEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
    }

    /**
     * Check if mockmodem is running
     * @return true if mockmodem service is running instead of real modem
     */
    @VisibleForTesting
    public boolean isMockModemRunning() {
        return CellBroadcastReceiver.isMockModemBinded();
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
