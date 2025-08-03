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

package com.android.imsserviceentitlement.fcm;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.android.imsserviceentitlement.ImsEntitlementPollingService;
import com.android.imsserviceentitlement.job.JobManager;
import com.android.imsserviceentitlement.utils.TelephonyUtils;
import com.android.libraries.entitlement.ServiceEntitlement;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Service for handling Firebase Cloud Messaging.*/
public class FcmService extends FirebaseMessagingService {
    private static final String TAG = "IMSSE-FcmService";

    private static final String DATA_APP_KEY = "app";
    private static final String DATA_TIMESTAMP_KEY = "timestamp";

    private JobManager mJobManager;

    @Override
    @VisibleForTesting
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    /**
     * Called when a new token for the default Firebase project is generated.
     *
     * @param token the token used for sending messages to this application instance.
     */
    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "New token: " + token);

        // TODO(b/182560867): check if we need to update the new token to server.

        // Note we cannot directly save the new token, as we don't know which subId
        // it's associated with.
        FcmRegistrationService.enqueueJob(this);
    }

    /**
     * Handles FCM message for entitlement.
     *
     * @param message holds the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        // Not testable.
        onMessageReceived(message.getSenderId(), message.getData());
    }

    @VisibleForTesting
    void onMessageReceived(String fcmSenderId, Map<String, String> fcmData) {
        Log.d(TAG, "onMessageReceived, SenderId:" + fcmSenderId);
        if (!isTs43EntitlementsChangeEvent(fcmData)) {
            Log.i(TAG, "Ignore message not related to entitlements change.");
            return;
        }
        // A corner case: a FCM received after SIM is removed, and SIM inserted back later.
        // We missed the FCM in this case.
        scheduleEntitlementStatusCheckForSubIdAssociatedWithSenderId(fcmSenderId);
    }

    private static boolean isTs43EntitlementsChangeEvent(Map<String, String> dataMap) {
        if (dataMap == null) {
            return false;
        }
        Log.v(TAG, "The payload data: " + dataMap);

        // Based on GSMA TS.43 2.4.2 Messaging Infrastructure-Based Notifications, the notification
        // payload for multiple applications follows:
        // "data":
        //  {
        //    "app": ["ap2003", "ap2004", "ap2005"],
        //    "timestamp": "2019-01-29T13:15:31-08:00"
        //  }
        if (!dataMap.containsKey(DATA_APP_KEY) || !dataMap.containsKey(DATA_TIMESTAMP_KEY)) {
            Log.d(TAG, "data format error");
            return false;
        }
        // Check if APP_VOWIFI i.e. "ap2004" is in notification data.
        if (dataMap.get(DATA_APP_KEY).contains(ServiceEntitlement.APP_VOWIFI)) {
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void setMockJobManager(JobManager jobManager) {
        mJobManager = jobManager;
    }

    private JobManager getJobManager(int subId) {
        return (mJobManager != null)
                ? mJobManager
                : JobManager.getInstance(
                        this,
                        ImsEntitlementPollingService.COMPONENT_NAME,
                        subId);
    }

    private void scheduleEntitlementStatusCheckForSubIdAssociatedWithSenderId(String msgSenderId) {
        for (int subId : TelephonyUtils.getSubIdsWithFcmSupported(this)) {
            String configSenderId = TelephonyUtils.getFcmSenderId(this, subId);
            if (msgSenderId.equals(configSenderId)) {
                Log.d(TAG, "check entitlement status for subscription id(" + subId + ")");
                getJobManager(subId).queryEntitlementStatusOnceNetworkReady();
            }
        }
    }
}
