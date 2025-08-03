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

package com.android.imsserviceentitlement.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

/** A helper class for IMS relevant APIs with subscription id. */
public class ImsUtils {
    private static final String TAG = "IMSSE-ImsUtils";

    @Nullable private final PersistableBundle mCarrierConfigs;
    private final ImsMmTelManager mImsMmTelManager;
    private final ProvisioningManager mProvisioningManager;

    /**
     * Turns Volte provisioning status ON/OFF.
     * Value is in Integer format. ON (1), OFF(0).
     * Key is from {@link ProvisioningManager#KEY_VOLTE_PROVISIONING_STATUS}.
     */
    private static final int KEY_VOLTE_PROVISIONING_STATUS = 10;

    /**
     * Turns SMS over IP ON/OFF on the device.
     * Value is in Integer format. ON (1), OFF(0).
     * Key is from {@link ProvisioningManager#KEY_SMS_OVER_IP_ENABLED}.
     */
    private static final int KEY_SMS_OVER_IP_ENABLED = 14;

    /**
     * Enable voice over wifi on device.
     * Value is in Integer format. Enabled (1), or Disabled (0).
     * Key is from {@link ProvisioningManager#KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE}.
     */
    private static final int KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE = 28;

    // Cache subscription id associated {@link ImsUtils} objects for reusing.
    @GuardedBy("ImsUtils.class")
    private static SparseArray<ImsUtils> sInstances = new SparseArray<ImsUtils>();

    private ImsUtils(Context context, int subId) {
        this(
                context.getSystemService(CarrierConfigManager.class).getConfigForSubId(subId),
                getImsMmTelManager(subId),
                getProvisioningManager(subId));
    }

    @VisibleForTesting
    ImsUtils(
            PersistableBundle carrierConfigs,
            ImsMmTelManager imsMmTelManager,
            ProvisioningManager provisioningManager) {
        mCarrierConfigs = carrierConfigs;
        mImsMmTelManager = imsMmTelManager;
        mProvisioningManager = provisioningManager;
    }

    /** Returns {@link ImsUtils} instance. */
    public static synchronized ImsUtils getInstance(Context context, int subId) {
        ImsUtils instance = sInstances.get(subId);
        if (instance != null) {
            return instance;
        }

        instance = new ImsUtils(context, subId);
        sInstances.put(subId, instance);
        return instance;
    }

    /** Changes persistent WFC enabled setting. */
    private void setWfcSetting(boolean enabled) {
        try {
            mImsMmTelManager.setVoWiFiSettingEnabled(enabled);
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /** Sets whether VoWiFi is provisioned. */
    public void setVowifiProvisioned(boolean value) {
        try {
            mProvisioningManager.setProvisioningIntValue(
                    KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, value
                            ? ProvisioningManager.PROVISIONING_VALUE_ENABLED
                            : ProvisioningManager.PROVISIONING_VALUE_DISABLED);
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /** Sets whether Volte is provisioned. */
    public void setVolteProvisioned(boolean value) {
        try {
            mProvisioningManager.setProvisioningIntValue(
                    KEY_VOLTE_PROVISIONING_STATUS, value
                            ? ProvisioningManager.PROVISIONING_VALUE_ENABLED
                            : ProvisioningManager.PROVISIONING_VALUE_DISABLED);
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /** Sets whether SMSoIP is provisioned. */
    public void setSmsoipProvisioned(boolean value) {
        try {
            mProvisioningManager.setProvisioningIntValue(
                    KEY_SMS_OVER_IP_ENABLED, value
                            ? ProvisioningManager.PROVISIONING_VALUE_ENABLED
                            : ProvisioningManager.PROVISIONING_VALUE_DISABLED);
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /** Disables WFC and reset WFC mode to carrier default value */
    @VisibleForTesting
    void disableAndResetVoWiFiImsSettings() {
        try {
            disableWfc();

            // Reset WFC mode to carrier default value
            if (mCarrierConfigs != null) {
                mImsMmTelManager.setVoWiFiModeSetting(
                        mCarrierConfigs.getInt(
                                CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT));
                mImsMmTelManager.setVoWiFiRoamingModeSetting(
                        mCarrierConfigs.getInt(
                                CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT));
            }
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /**
     * Returns {@link ImsMmTelManager} with specific subscription id.
     * Returns {@code null} if provided subscription id invalid.
     */
    @Nullable
    private static ImsMmTelManager getImsMmTelManager(int subId) {
        try {
            return ImsMmTelManager.createForSubscriptionId(subId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Can't get ImsMmTelManager, IllegalArgumentException: subId = " + subId);
        }

        return null;
    }

    /**
     * Returns {@link ProvisioningManager} with specific subscription id.
     * Returns {@code null} if provided subscription id invalid.
     */
    @Nullable
    private static ProvisioningManager getProvisioningManager(int subId) {
        try {
            return ProvisioningManager.createForSubscriptionId(subId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Can't get ProvisioningManager, IllegalArgumentException: subId = " + subId);
        }
        return null;
    }

    /** Returns whether WFC is enabled by user for current subId */
    public boolean isWfcEnabledByUser() {
        try {
            return mImsMmTelManager.isVoWiFiSettingEnabled();
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
        return false;
    }

    /** Calls {@link #disableAndResetVoWiFiImsSettings()} in background thread. */
    public void turnOffWfc(Runnable action) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                disableAndResetVoWiFiImsSettings();
                return null; // To satisfy compiler
            }

            @Override
            protected void onPostExecute(Void result) {
                action.run();
            }
        }.execute();
    }

    /** Disables WFC */
    public void disableWfc() {
        setWfcSetting(false);
    }
}
