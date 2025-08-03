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

package com.android.imsserviceentitlement;

import static android.telephony.TelephonyManager.SIM_STATE_LOADED;

import static com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior.NEEDS_TO_RESET;
import static com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior.VALID_DURING_VALIDITY;
import static com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior.VALID_WITHOUT_DURATION;
import static com.android.imsserviceentitlement.utils.Executors.getAsyncExecutor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration;
import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior;
import com.android.imsserviceentitlement.job.JobManager;
import com.android.imsserviceentitlement.utils.TelephonyUtils;

/** Watches events and manages service entitlement polling. */
public class ImsEntitlementReceiver extends BroadcastReceiver {
    private static final String TAG = "IMSSE-ImsEntitlementReceiver";

    /**
     * Shared preference name for activation information, the key used in this file should append
     * slot id if the value depended on carrier.
     */
    private static final String PREFERENCE_ACTIVATION_INFO = "PREFERENCE_ACTIVATION_INFO";
    /**
     * Shared preference key for last known subscription id of a SIM slot; default value {@link
     * SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
     */
    private static final String KEY_LAST_SUB_ID = "last_sub_id_";
    /** Shared preference key for last boot count. */
    private static final String KEY_LAST_BOOT_COUNT = "last_boot_count_";

    @Override
    public void onReceive(Context context, Intent intent) {
        int currentSubId =
                intent.getIntExtra(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        int slotId =
                intent.getIntExtra(
                        SubscriptionManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        Dependencies dependencies = createDependency(context, currentSubId);
        if (!dependencies.userManager.isSystemUser()
                || !SubscriptionManager.isValidSubscriptionId(currentSubId)
                || dependencies.telephonyUtils.getSimApplicationState() != SIM_STATE_LOADED
                || !TelephonyUtils.isImsProvisioningRequired(context, currentSubId)) {
            return;
        }

        String action = intent.getAction();
        if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)) {
            final PendingResult result = goAsync();
            getAsyncExecutor().execute(
                    () -> handleCarrierConfigChanged(
                            context, currentSubId, slotId, dependencies.jobManager, result));
        }
    }

    /**
     * Handles the event of SIM change and device boot up while receiving {@link
     * CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED}.
     */
    @WorkerThread
    private void handleCarrierConfigChanged(
            Context context, int currentSubId, int slotId, JobManager jobManager,
            PendingResult result) {
        boolean shouldQuery = false;

        // Handle device boot up.
        if (isBootUp(context, slotId)) {
            ClientBehavior clientBehavior =
                    new EntitlementConfiguration(context, currentSubId).entitlementValidation();
            Log.d(TAG, "Device boot up, clientBehavior=" + clientBehavior);
            if (clientBehavior == VALID_DURING_VALIDITY
                    || clientBehavior == VALID_WITHOUT_DURATION
                    || clientBehavior == NEEDS_TO_RESET) {
                shouldQuery = true;
            }
        }

        // Handle SIM changed.
        int lastSubId = getAndSetSubId(context, currentSubId, slotId);
        if (currentSubId != lastSubId) {
            Log.d(TAG,
                    "SubId for slot " + slotId + " changed: " + lastSubId + " -> " + currentSubId);
            if (SubscriptionManager.isValidSubscriptionId(lastSubId)) {
                new EntitlementConfiguration(context, lastSubId).reset();
            }
            shouldQuery = true;
        }

        if (shouldQuery) {
            jobManager.queryEntitlementStatusOnceNetworkReady();
        }

        if (result != null) {
            result.finish();
        }
    }

    /**
     * Returns {@code true} if current boot count greater than previous one. Saves the latest boot
     * count into shared preference.
     */
    @VisibleForTesting
    boolean isBootUp(Context context, int slotId) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCE_ACTIVATION_INFO, Context.MODE_PRIVATE);
        int lastBootCount = preferences.getInt(KEY_LAST_BOOT_COUNT + slotId, 0);
        int currentBootCount =
                Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.BOOT_COUNT, /* def= */ -1);
        preferences.edit().putInt(KEY_LAST_BOOT_COUNT + slotId, currentBootCount).apply();

        return currentBootCount != lastBootCount;
    }

    private int getAndSetSubId(Context context, int currentSubId, int slotId) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCE_ACTIVATION_INFO, Context.MODE_PRIVATE);
        int lastSubId = preferences.getInt(
                KEY_LAST_SUB_ID + slotId, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        preferences.edit().putInt(KEY_LAST_SUB_ID + slotId, currentSubId).apply();
        return lastSubId;
    }

    /** Returns initialized dependencies */
    @VisibleForTesting
    Dependencies createDependency(Context context, int subId) {
        // Wrap return value
        Dependencies ret = new Dependencies();
        ret.telephonyUtils = new TelephonyUtils(context, subId);
        ret.userManager = context.getSystemService(UserManager.class);
        ret.jobManager =
                JobManager.getInstance(context, ImsEntitlementPollingService.COMPONENT_NAME, subId);
        return ret;
    }

    /** A collection of dependency objects */
    protected static class Dependencies {
        public TelephonyUtils telephonyUtils;
        public UserManager userManager;
        public JobManager jobManager;
    }
}
