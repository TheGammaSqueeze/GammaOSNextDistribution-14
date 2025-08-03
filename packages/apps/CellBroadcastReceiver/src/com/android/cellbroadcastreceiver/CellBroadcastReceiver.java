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

import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRSRC_CBR;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_PREFMIGRATION;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.RPT_SPC;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.SRC_CBR;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Telephony;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.EventLog;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public class CellBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CellBroadcastReceiver";
    static final boolean DBG = true;
    static final boolean VDBG = false;    // STOPSHIP: change to false before ship

    // Key to access the shared preference of reminder interval default value.
    @VisibleForTesting
    public static final String CURRENT_INTERVAL_DEFAULT = "current_interval_default";

    // Key to access the shared preference of cell broadcast testing mode.
    @VisibleForTesting
    public static final String TESTING_MODE = "testing_mode";

    // Key to access the shared preference of service state.
    private static final String SERVICE_STATE = "service_state";

    // Key to access the shared preference of roaming operator.
    private static final String ROAMING_OPERATOR_SUPPORTED = "roaming_operator_supported";

    // shared preference under developer settings
    private static final String ENABLE_ALERT_MASTER_PREF = "enable_alerts_master_toggle";

    // shared preference for alert reminder interval
    private static final String ALERT_REMINDER_INTERVAL_PREF = "alert_reminder_interval";

    // SharedPreferences key used to store the last carrier
    private static final String CARRIER_ID_FOR_DEFAULT_SUB_PREF = "carrier_id_for_default_sub";
    // initial value for saved carrier ID. This helps us detect newly updated users or first boot
    private static final int NO_PREVIOUS_CARRIER_ID = -2;

    public static final String ACTION_SERVICE_STATE = "android.intent.action.SERVICE_STATE";
    public static final String EXTRA_VOICE_REG_STATE = "voiceRegState";

    // Intent actions and extras
    public static final String CELLBROADCAST_START_CONFIG_ACTION =
            "com.android.cellbroadcastreceiver.intent.START_CONFIG";
    public static final String ACTION_MARK_AS_READ =
            "com.android.cellbroadcastreceiver.intent.action.MARK_AS_READ";
    public static final String EXTRA_DELIVERY_TIME =
            "com.android.cellbroadcastreceiver.intent.extra.ID";
    public static final String EXTRA_NOTIF_ID =
            "com.android.cellbroadcastreceiver.intent.extra.NOTIF_ID";

    public static final String ACTION_TESTING_MODE_CHANGED =
            "com.android.cellbroadcastreceiver.intent.ACTION_TESTING_MODE_CHANGED";

    // System property to set roaming network config which can be multiple items split by
    // comma, and matched in sequence. This config will insert before the overlay.
    private static final String ROAMING_PLMN_SUPPORTED_PROPERTY_KEY =
            "persist.cellbroadcast.roaming_plmn_supported";

    private static final String MOCK_MODEM_BASEBAND = "mock-modem-service";

    private Context mContext;

    // This is to map the iso country code to the MCC string
    private Map<String, String> mMccMap;

    /**
     * this method is to make this class unit-testable, because CellBroadcastSettings.getResources()
     * is a static method and cannot be stubbed.
     *
     * @return resources
     */
    @VisibleForTesting
    public Resources getResourcesMethod() {
        return CellBroadcastSettings.getResourcesForDefaultSubId(mContext);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DBG) log("onReceive " + intent);

        mContext = context.getApplicationContext();
        String action = intent.getAction();
        Resources res = getResourcesMethod();

        if (mMccMap == null) {
            mMccMap = getMccMap(res);
        }

        if (ACTION_MARK_AS_READ.equals(action)) {
            // The only way this'll be called is if someone tries to maliciously set something
            // as read. Log an event.
            EventLog.writeEvent(0x534e4554, "162741784", -1, null);
        } else if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)) {
            if (!intent.getBooleanExtra(
                    "android.telephony.extra.REBROADCAST_ON_UNLOCK", false)) {
                resetCellBroadcastChannelRanges();
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                initializeSharedPreference(context, subId);
                enableLauncher();
                startConfigServiceToEnableChannels();

                // Some OEMs do not have legacyMigrationProvider active during boot-up, thus we
                // need to retry data migration from another trigger point.
                boolean hasMigrated = getDefaultSharedPreferences()
                        .getBoolean(CellBroadcastDatabaseHelper.KEY_LEGACY_DATA_MIGRATION, false);
                if (res.getBoolean(R.bool.retry_message_history_data_migration) && !hasMigrated) {
                    // migrate message history from legacy app on a background thread.
                    new CellBroadcastContentProvider.AsyncCellBroadcastTask(
                            mContext.getContentResolver()).execute(
                            (CellBroadcastContentProvider.CellBroadcastOperation) provider -> {
                                provider.call(CellBroadcastContentProvider.CALL_MIGRATION_METHOD,
                                        null, null);
                                return true;
                            });
                }
            }
        } else if (ACTION_SERVICE_STATE.equals(action)) {
            // lower layer clears channel configurations under APM, thus need to resend
            // configurations once moving back from APM. This should be fixed in lower layer
            // going forward.
            int ss = intent.getIntExtra(EXTRA_VOICE_REG_STATE, ServiceState.STATE_IN_SERVICE);
            onServiceStateChanged(context, res, ss);
        } else if (SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED.equals(action)) {
            if (!isMockModemRunning()) {
                startConfigServiceToEnableChannels();
            }
        } else if (Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            intent.setClass(mContext, CellBroadcastAlertService.class);
            mContext.startService(intent);
        } else if (Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION
                .equals(action)) {
            ArrayList<CdmaSmsCbProgramData> programDataList =
                    intent.getParcelableArrayListExtra("program_data");

            CellBroadcastReceiverMetrics.getInstance().logMessageReported(mContext,
                    RPT_SPC, SRC_CBR, 0, 0);

            if (programDataList != null) {
                handleCdmaSmsCbProgramData(programDataList);
            } else {
                loge("SCPD intent received with no program_data");
            }
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // rename registered notification channels on locale change
            CellBroadcastAlertService.createNotificationChannels(mContext);
        } else if (TelephonyManager.ACTION_SECRET_CODE.equals(action)) {
            if (SystemProperties.getInt("ro.debuggable", 0) == 1
                    || res.getBoolean(R.bool.allow_testing_mode_on_user_build)) {
                setTestingMode(!isTestingMode(mContext));
                int msgId = (isTestingMode(mContext)) ? R.string.testing_mode_enabled
                        : R.string.testing_mode_disabled;
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                        .onChangedTestMode(isTestingMode(mContext));
                String msg = res.getString(msgId);
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
                LocalBroadcastManager.getInstance(mContext)
                        .sendBroadcast(new Intent(ACTION_TESTING_MODE_CHANGED));
                log(msg);
            } else {
                if (!res.getBoolean(R.bool.allow_testing_mode_on_user_build)) {
                    CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                            .onChangedTestModeOnUserBuild(false);
                }
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            new CellBroadcastContentProvider.AsyncCellBroadcastTask(
                    mContext.getContentResolver()).execute((CellBroadcastContentProvider
                    .CellBroadcastOperation) provider -> {
                        provider.resyncToSmsInbox(mContext);
                        return true;
                    });
        } else {
            Log.w(TAG, "onReceive() unexpected action " + action);
        }
    }


    /**
     * Get SystemProperties values
     *
     * @param key string to use get the value
     * @return the matched value, but default "" for unmatched case.
     */
    @VisibleForTesting
    public String getSystemProperties(String key) {
        return SystemProperties.get(key, "").trim();
    }

    private void onServiceStateChanged(Context context, Resources res, int ss) {
        logd("onServiceStateChanged, ss: " + ss);
        // check whether to support roaming network
        String roamingOperator = null;
        if (ss != ServiceState.STATE_POWER_OFF) {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            String networkOperator = tm.getNetworkOperator();
            logd("networkOperator: " + networkOperator);

            // check the mcc on emergency only mode
            if (TextUtils.isEmpty(networkOperator)) {
                String countryCode = tm.getNetworkCountryIso();
                if (mMccMap != null && !TextUtils.isEmpty(countryCode)) {
                    networkOperator = mMccMap.get(countryCode.toLowerCase(Locale.ROOT).trim());
                    logd("networkOperator on emergency mode: " + networkOperator
                            + " for the country code: " + countryCode);
                }
            }

            // check roaming config only if the network oprator is not empty as the config
            // is based on operator numeric
            if (!TextUtils.isEmpty(networkOperator)) {
                // No roaming supported by default
                roamingOperator = "";
                if ((tm.isNetworkRoaming() || ss != ServiceState.STATE_IN_SERVICE)
                        && !networkOperator.equals(tm.getSimOperator())) {
                    String propRoamingPlmn =
                            getSystemProperties(ROAMING_PLMN_SUPPORTED_PROPERTY_KEY);
                    String[] roamingNetworks = propRoamingPlmn.isEmpty() ? res.getStringArray(
                            R.array.cmas_roaming_network_strings) : propRoamingPlmn.split(",");
                    logd("roamingNetworks: " + Arrays.toString(roamingNetworks));

                    for (String r : roamingNetworks) {
                        r = r.trim();
                        if (r.equals("XXXXXX")) {
                            //match any roaming network, store mcc+mnc
                            roamingOperator = networkOperator;
                            break;
                        } else if (r.equals("XXX")) {
                            if (tm.getSimOperator() != null) {
                                String networkMcc = networkOperator.substring(0, 3);
                                // empty sim case or inserted sim but different mcc case
                                if (!tm.getSimOperator().startsWith(networkMcc)) {
                                    //match any roaming network, only store mcc
                                    roamingOperator = networkMcc;
                                }
                            }
                            break;
                        } else if (networkOperator.startsWith(r)) {
                            roamingOperator = r;
                            break;
                        }
                    }
                }
            }
        }

        if ((ss != ServiceState.STATE_POWER_OFF
                && getServiceState(context) == ServiceState.STATE_POWER_OFF)
                || (roamingOperator != null && !roamingOperator.equals(
                getRoamingOperatorSupported(context)))) {
            if (!isMockModemRunning()) {
                startConfigServiceToEnableChannels();
            }
        }
        setServiceState(ss);

        if (roamingOperator != null) {
            log("update supported roaming operator as " + roamingOperator);
            setRoamingOperatorSupported(roamingOperator);
        }
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedRoamingSupport(!TextUtils.isEmpty(roamingOperator) ? true : false);
    }

    /**
     * Initialize the MCC mapping table
     */
    @VisibleForTesting
    @NonNull
    public static Map<String, String> getMccMap(@NonNull Resources res) {
        String[] arr = res.getStringArray(R.array.iso_country_code_mcc_table);
        Map<String, String> map = new ArrayMap<>(arr.length);

        for (String item : arr) {
            String[] val = item.split(":");
            if (val.length > 1) {
                map.put(val[0].toLowerCase(Locale.ROOT).trim(), val[1].trim());
            }
        }

        return map;
    }

    /**
     * Send an intent to reset the users WEA settings if there is a new carrier on the default subId
     *
     * The settings will be reset only when a new carrier is detected on the default subId. So it
     * tracks the previous carrier id, and ignores the case that the current carrier id is changed
     * to invalid. In case of the first boot with a sim on the new device, FDR, or upgrade from Q,
     * the carrier id will be stored as there is no previous carrier id, but the settings will not
     * be reset.
     *
     * Do nothing in other cases:
     * - SIM insertion for the non-default subId
     * - SIM insertion/bootup with no new carrier
     * - SIM removal
     * - Device just received the update which adds this carrier tracking logic
     *
     * @param context the context
     * @param subId   subId of the carrier config event
     */
    private void resetSettingsAsNeeded(Context context, int subId) {
        final int defaultSubId = SubscriptionManager.getDefaultSubscriptionId();

        // subId may be -1 if carrier config broadcast is being sent on SIM removal
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                Log.d(TAG, "ignoring carrier config broadcast because subId=-1 and it's not"
                        + " defaultSubId when device is support multi-sim");
                return;
            }

            if (getPreviousCarrierIdForDefaultSub() == NO_PREVIOUS_CARRIER_ID) {
                // on first boot only, if no SIM is inserted we save the carrier ID -1.
                // This allows us to detect the carrier change from -1 to the carrier of the first
                // SIM when it is inserted.
                saveCarrierIdForDefaultSub(TelephonyManager.UNKNOWN_CARRIER_ID);
            }
            Log.d(TAG, "ignoring carrier config broadcast because subId=-1");
            return;
        }

        Log.d(TAG, "subId=" + subId + " defaultSubId=" + defaultSubId);
        if (defaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "ignoring carrier config broadcast because defaultSubId=-1");
            return;
        }

        if (subId != defaultSubId) {
            Log.d(TAG, "ignoring carrier config broadcast for subId=" + subId
                    + " because it does not match defaultSubId=" + defaultSubId);
            return;
        }

        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        // carrierId is loaded before carrier config, so this should be safe
        int carrierId = tm.createForSubscriptionId(subId).getSimCarrierId();
        if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
            Log.e(TAG, "ignoring unknown carrier ID");
            return;
        }

        int previousCarrierId = getPreviousCarrierIdForDefaultSub();
        if (previousCarrierId == NO_PREVIOUS_CARRIER_ID) {
            // on first boot if a SIM is inserted, assume it is not new and don't apply settings
            Log.d(TAG, "ignoring carrier config broadcast for subId=" + subId
                    + " for first boot");
            saveCarrierIdForDefaultSub(carrierId);
            return;
        }

        /** When user_build_mode is true and alow_testing_mode_on_user_build is false
         *  then testing_mode is not able to be true at all.
         */
        Resources res = getResourcesMethod();
        if (!res.getBoolean(R.bool.allow_testing_mode_on_user_build)
                && SystemProperties.getInt("ro.debuggable", 0) == 0
                && CellBroadcastReceiver.isTestingMode(context)) {
            CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(context)
                    .onChangedTestModeOnUserBuild(false);
            Log.d(TAG, "it can't be testing_mode at all");
            setTestingMode(false);
        }

        if (carrierId != previousCarrierId) {
            saveCarrierIdForDefaultSub(carrierId);
            startConfigService(context,
                    CellBroadcastConfigService.ACTION_UPDATE_SETTINGS_FOR_CARRIER);
        } else {
            Log.d(TAG, "reset settings as needed for subId=" + subId + ", carrierId=" + carrierId);
            Intent intent = new Intent(CellBroadcastConfigService.ACTION_RESET_SETTINGS_AS_NEEDED,
                    null, context, CellBroadcastConfigService.class);
            intent.putExtra(CellBroadcastConfigService.EXTRA_SUB, subId);
            context.startService(intent);
        }
    }

    private int getPreviousCarrierIdForDefaultSub() {
        return getDefaultSharedPreferences()
                .getInt(CARRIER_ID_FOR_DEFAULT_SUB_PREF, NO_PREVIOUS_CARRIER_ID);
    }


    /**
     * store carrierId corresponding to the default subId.
     */
    @VisibleForTesting
    public void saveCarrierIdForDefaultSub(int carrierId) {
        getDefaultSharedPreferences().edit().putInt(CARRIER_ID_FOR_DEFAULT_SUB_PREF, carrierId)
                .apply();
    }

    /**
     * Enable/disable cell broadcast receiver testing mode.
     *
     * @param on {@code true} if testing mode is on, otherwise off.
     */
    @VisibleForTesting
    public void setTestingMode(boolean on) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putBoolean(TESTING_MODE, on).commit();
    }

    /**
     * @return {@code true} if operating in testing mode, which enables some features for testing
     * purposes.
     */
    public static boolean isTestingMode(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(TESTING_MODE, false);
    }

    /**
     * Store the current service state for voice registration.
     *
     * @param ss current voice registration service state.
     */
    private void setServiceState(int ss) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putInt(SERVICE_STATE, ss).commit();
    }

    /**
     * Store the roaming operator
     */
    private void setRoamingOperatorSupported(String roamingOperator) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putString(ROAMING_OPERATOR_SUPPORTED, roamingOperator).commit();
    }

    /**
     * @return the stored voice registration service state
     */
    private static int getServiceState(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getInt(SERVICE_STATE, ServiceState.STATE_IN_SERVICE);
    }

    /**
     * @return the supported roaming operator
     */
    public static String getRoamingOperatorSupported(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(ROAMING_OPERATOR_SUPPORTED, "");
    }

    /**
     * update reminder interval
     */
    @VisibleForTesting
    public void adjustReminderInterval() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String currentIntervalDefault = sp.getString(CURRENT_INTERVAL_DEFAULT, "0");

        // If interval default changes, reset the interval to the new default value.
        String newIntervalDefault = CellBroadcastSettings.getResourcesForDefaultSubId(mContext)
                .getString(R.string.alert_reminder_interval_in_min_default);
        if (!newIntervalDefault.equals(currentIntervalDefault)) {
            Log.d(TAG, "Default interval changed from " + currentIntervalDefault + " to " +
                    newIntervalDefault);

            Editor editor = sp.edit();
            // Reset the value to default.
            editor.putString(
                    CellBroadcastSettings.KEY_ALERT_REMINDER_INTERVAL, newIntervalDefault);
            // Save the new default value.
            editor.putString(CURRENT_INTERVAL_DEFAULT, newIntervalDefault);
            editor.commit();
        } else {
            if (DBG) Log.d(TAG, "Default interval " + currentIntervalDefault + " did not change.");
        }
    }

    /**
     * This method's purpose is to enable unit testing
     *
     * @return sharedePreferences for mContext
     */
    @VisibleForTesting
    public SharedPreferences getDefaultSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    /**
     * return if there are default values in shared preferences
     *
     * @return boolean
     */
    @VisibleForTesting
    public Boolean sharedPrefsHaveDefaultValues() {
        return mContext.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES,
                Context.MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES,
                false);
    }

    /**
     * initialize shared preferences before starting services
     */
    @VisibleForTesting
    public void initializeSharedPreference(Context context, int subId) {
        if (isSystemUser()) {
            Log.d(TAG, "initializeSharedPreference");

            resetSettingsAsNeeded(context, subId);

            SharedPreferences sp = getDefaultSharedPreferences();

            if (!sharedPrefsHaveDefaultValues()) {
                // Sets the default values of the shared preference if there isn't any.
                PreferenceManager.setDefaultValues(mContext, R.xml.preferences, false);

                sp.edit().putBoolean(CellBroadcastSettings.KEY_OVERRIDE_DND_SETTINGS_CHANGED,
                        false).apply();

                // migrate sharedpref from legacy app
                migrateSharedPreferenceFromLegacy();

                // If the device is in test harness mode, we need to disable emergency alert by
                // default.
                if (ActivityManager.isRunningInUserTestHarness()) {
                    Log.d(TAG, "In test harness mode. Turn off emergency alert by default.");
                    sp.edit().putBoolean(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE,
                            false).apply();
                }
            } else {
                Log.d(TAG, "Skip setting default values of shared preference.");
            }

            adjustReminderInterval();
        } else {
            Log.e(TAG, "initializeSharedPreference: Not system user.");
        }
    }

    /**
     * migrate shared preferences from legacy content provider client
     */
    @VisibleForTesting
    public void migrateSharedPreferenceFromLegacy() {
        String[] PREF_KEYS = {
                CellBroadcasts.Preference.ENABLE_CMAS_AMBER_PREF,
                CellBroadcasts.Preference.ENABLE_AREA_UPDATE_INFO_PREF,
                CellBroadcasts.Preference.ENABLE_TEST_ALERT_PREF,
                CellBroadcasts.Preference.ENABLE_STATE_LOCAL_TEST_PREF,
                CellBroadcasts.Preference.ENABLE_PUBLIC_SAFETY_PREF,
                CellBroadcasts.Preference.ENABLE_CMAS_SEVERE_THREAT_PREF,
                CellBroadcasts.Preference.ENABLE_CMAS_EXTREME_THREAT_PREF,
                CellBroadcasts.Preference.ENABLE_CMAS_PRESIDENTIAL_PREF,
                CellBroadcasts.Preference.ENABLE_EMERGENCY_PERF,
                CellBroadcasts.Preference.ENABLE_ALERT_VIBRATION_PREF,
                CellBroadcasts.Preference.ENABLE_CMAS_IN_SECOND_LANGUAGE_PREF,
                ENABLE_ALERT_MASTER_PREF,
                ALERT_REMINDER_INTERVAL_PREF
        };
        try (ContentProviderClient client = mContext.getContentResolver()
                .acquireContentProviderClient(Telephony.CellBroadcasts.AUTHORITY_LEGACY)) {
            if (client == null) {
                Log.d(TAG, "No legacy provider available for sharedpreference migration");
                return;
            }
            SharedPreferences.Editor sp = PreferenceManager
                    .getDefaultSharedPreferences(mContext).edit();
            for (String key : PREF_KEYS) {
                try {
                    Bundle pref = client.call(
                            CellBroadcasts.AUTHORITY_LEGACY,
                            CellBroadcasts.CALL_METHOD_GET_PREFERENCE,
                            key, null);
                    if (pref != null && pref.containsKey(key)) {
                        Object val = pref.get(key);
                        if (val == null) {
                            // noop - no value to set.
                            // Only support Boolean and String as preference types for now.
                        } else if (val instanceof Boolean) {
                            Log.d(TAG, "migrateSharedPreferenceFromLegacy: " + key + "val: "
                                    + pref.getBoolean(key));
                            sp.putBoolean(key, pref.getBoolean(key));
                        } else if (val instanceof String) {
                            Log.d(TAG, "migrateSharedPreferenceFromLegacy: " + key + "val: "
                                    + pref.getString(key));
                            sp.putString(key, pref.getString(key));
                        }
                    } else {
                        Log.d(TAG, "migrateSharedPreferenceFromLegacy: unsupported key: " + key);
                    }
                } catch (RemoteException e) {
                    CellBroadcastReceiverMetrics.getInstance().logModuleError(
                            ERRSRC_CBR, ERRTYPE_PREFMIGRATION);
                    Log.e(TAG, "fails to get shared preference " + e);
                }
            }
            sp.apply();
        } catch (Exception e) {
            // We have to guard ourselves against any weird behavior of the
            // legacy provider by trying to catch everything
            loge("Failed migration from legacy provider: " + e);
        }
    }

    /**
     * Handle Service Category Program Data message.
     * TODO: Send Service Category Program Results response message to sender
     */
    @VisibleForTesting
    public void handleCdmaSmsCbProgramData(ArrayList<CdmaSmsCbProgramData> programDataList) {
        for (CdmaSmsCbProgramData programData : programDataList) {
            switch (programData.getOperation()) {
                case CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY:
                    tryCdmaSetCategory(mContext, programData.getCategory(), true);
                    break;

                case CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY:
                    tryCdmaSetCategory(mContext, programData.getCategory(), false);
                    break;

                case CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES:
                    tryCdmaSetCategory(mContext,
                            CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT, false);
                    tryCdmaSetCategory(mContext,
                            CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT, false);
                    tryCdmaSetCategory(mContext,
                            CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false);
                    tryCdmaSetCategory(mContext,
                            CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE, false);
                    break;

                default:
                    loge("Ignoring unknown SCPD operation " + programData.getOperation());
            }
        }
    }

    /**
     * set CDMA category in shared preferences
     * @param context
     * @param category CDMA category
     * @param enable   true for add category, false otherwise
     */
    @VisibleForTesting
    public void tryCdmaSetCategory(Context context, int category, boolean enable) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        switch (category) {
            case CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT:
                sharedPrefs.edit().putBoolean(
                                CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, enable)
                        .apply();
                break;

            case CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT:
                sharedPrefs.edit().putBoolean(
                                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, enable)
                        .apply();
                break;

            case CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, enable).apply();
                break;

            case CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS, enable).apply();
                break;

            default:
                Log.w(TAG, "Ignoring SCPD command to " + (enable ? "enable" : "disable")
                        + " alerts in category " + category);
        }
    }

    /**
     * This method's purpose if to enable unit testing
     *
     * @return if the mContext user is a system user
     */
    private boolean isSystemUser() {
        return isSystemUser(mContext);
    }

    /**
     * This method's purpose if to enable unit testing
     */
    @VisibleForTesting
    public void startConfigServiceToEnableChannels() {
        startConfigService(mContext, CellBroadcastConfigService.ACTION_ENABLE_CHANNELS);
    }

    /**
     * Check if user from context is system user
     * @param context
     * @return whether the user is system user
     */
    private static boolean isSystemUser(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return userManager.isSystemUser();
    }

    /**
     * Tell {@link CellBroadcastConfigService} to enable the CB channels.
     *
     * @param context the broadcast receiver context
     */
    static void startConfigService(Context context, String action) {
        if (isSystemUser(context)) {
            Log.d(TAG, "Start Cell Broadcast configuration for intent=" + action);
            context.startService(new Intent(action, null, context,
                    CellBroadcastConfigService.class));
        } else {
            Log.e(TAG, "startConfigService: Not system user.");
        }
    }

    /**
     * Enable Launcher.
     */
    @VisibleForTesting
    public void enableLauncher() {
        boolean enable = getResourcesMethod().getBoolean(R.bool.show_message_history_in_launcher);
        final PackageManager pm = mContext.getPackageManager();
        // This alias presents the target activity, CellBroadcastListActivity, as a independent
        // entity with its own intent filter for android.intent.category.LAUNCHER.
        // This alias will be enabled/disabled at run-time based on resource overlay. Once enabled,
        // it will appear in the Launcher as a top-level application
        String aliasLauncherActivity = null;
        try {
            PackageInfo p = pm.getPackageInfo(mContext.getPackageName(),
                    PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (p != null) {
                for (ActivityInfo activityInfo : p.activities) {
                    String targetActivity = activityInfo.targetActivity;
                    if (CellBroadcastListActivity.class.getName().equals(targetActivity)) {
                        aliasLauncherActivity = activityInfo.name;
                        break;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.toString());
        }
        if (TextUtils.isEmpty(aliasLauncherActivity)) {
            Log.e(TAG, "cannot find launcher activity");
            return;
        }

        if (enable) {
            Log.d(TAG, "enable launcher activity: " + aliasLauncherActivity);
            pm.setComponentEnabledSetting(
                    new ComponentName(mContext, aliasLauncherActivity),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        } else {
            Log.d(TAG, "disable launcher activity: " + aliasLauncherActivity);
            pm.setComponentEnabledSetting(
                    new ComponentName(mContext, aliasLauncherActivity),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * Reset cached CellBroadcastChannelRanges
     *
     * This method's purpose is to enable unit testing
     */
    @VisibleForTesting
    public void resetCellBroadcastChannelRanges() {
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    /**
     * Check if mockmodem is running
     * @return true if mockmodem service is running instead of real modem
     */
    @VisibleForTesting
    public boolean isMockModemRunning() {
        return isMockModemBinded();
    }

    /**
     * Check if mockmodem is running
     * @return true if mockmodem service is running instead of real modem
     */
    public static boolean isMockModemBinded() {
        String modem = Build.getRadioVersion();
        boolean isMockModem = modem != null ? modem.contains(MOCK_MODEM_BASEBAND) : false;
        Log.d(TAG, "mockmodem is running? = " + isMockModem);
        return isMockModem;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void logd(String msg) {
        if (DBG) Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
