/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.telecom.cts.apps;

import static android.telecom.cts.apps.NotificationUtils.createCallStyleNotification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.telecom.CallAttributes;
import android.util.Log;

/**
 * This class should contain all the resources associated with a single call. This includes things
 * like Notifications, Audio Records, Audio Tracks, but is not limited to just those objects.
 */
public class CallResources {
    private static final String TAG = CallResources.class.getSimpleName();
    private final CallAttributes mCallAttributes;
    private String mCallId = "";

    /**
     * Notification properties
     */
    private final Notification mNotification;
    private final String mCallChannelId;
    private final int mNotificationId;

    public CallResources(Context context,
            CallAttributes callAttributes,
            String callChannelId,
            int notificationId) {
        mCallAttributes = callAttributes;
        mCallChannelId = callChannelId;
        mNotificationId = notificationId;
        mNotification = createCallStyleNotification(
                context,
                callChannelId,
                callAttributes.getDisplayName().toString(),
                AttributesUtil.isOutgoing(callAttributes));
        postInitialCallStyleNotification(context);
    }

    public CallAttributes getCallAttributes() {
        return mCallAttributes;
    }

    public void setCallId(String id) {
        mCallId = id;
    }

    /**
     * Notification helpers
     */

    public int getNotificationId() {
        return mNotificationId;
    }

    public void postInitialCallStyleNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.notify(mNotificationId, mNotification);
        Log.i(TAG, String.format("postInitialCallStyleNotification: posted notification for"
                        + " callId=[%s], mNotificationId=[%s], notification=[%s]", mCallId,
                mNotificationId, mNotification));
    }

    public void updateNotificationToOngoing(Context context) {
        NotificationUtils.updateNotificationToOngoing(
                context,
                mCallChannelId,
                mCallAttributes.getDisplayName().toString(),
                mNotificationId);
    }

    public void clearCallNotification(Context context) {
        NotificationUtils.clearNotification(context, mNotificationId);
    }

    public void destroyResources(Context context) {
        clearCallNotification(context);
    }
}
