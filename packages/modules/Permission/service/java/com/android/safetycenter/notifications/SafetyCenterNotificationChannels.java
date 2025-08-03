/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.safetycenter.notifications;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.util.List;

/**
 * Class responsible for creating and updating Safety Center's notification channels.
 *
 * @hide
 */
public final class SafetyCenterNotificationChannels {

    private static final String TAG = "SafetyCenterNC";

    private static final String CHANNEL_GROUP_ID = "safety_center_channels";
    private static final String CHANNEL_ID_INFORMATION = "safety_center_information";
    private static final String CHANNEL_ID_RECOMMENDATION = "safety_center_recommendation";
    private static final String CHANNEL_ID_CRITICAL_WARNING = "safety_center_critical_warning";

    private final SafetyCenterResourcesApk mSafetyCenterResourcesApk;

    public SafetyCenterNotificationChannels(SafetyCenterResourcesApk safetyCenterResourcesApk) {
        mSafetyCenterResourcesApk = safetyCenterResourcesApk;
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @Nullable
    static NotificationManager getNotificationManagerForUser(
            Context baseContext, UserHandle userHandle) {
        Context contextAsUser = getContextAsUser(baseContext, userHandle);
        NotificationManager notificationManager =
                (contextAsUser != null)
                        ? contextAsUser.getSystemService(NotificationManager.class)
                        : null;
        if (notificationManager == null) {
            Log.w(
                    TAG,
                    "Could not retrieve NotificationManager for user id: "
                            + userHandle.getIdentifier());
        }
        return notificationManager;
    }

    @Nullable
    static Context getContextAsUser(Context baseContext, UserHandle userHandle) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return baseContext.createContextAsUser(userHandle, /* flags= */ 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not create Context as user id: " + userHandle.getIdentifier(), e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Returns the ID of the appropriate {@link NotificationChannel} for a notification about the
     * given {@code issue} after ensuring that channel has been created.
     */
    @Nullable
    String getCreatedChannelId(NotificationManager notificationManager, SafetySourceIssue issue) {
        try {
            createAllChannelsWithoutCallingIdentity(notificationManager);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to create notification channels", e);
            return null;
        }
        return getChannelIdForIssue(issue);
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group, for all
     * current users, dropping any calling identity so those channels can be unblockable.
     */
    public void createAllChannelsForAllUsers(Context context) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            // TODO(b/284271124): Decide what to do with existing channels if flag gets toggled.
            Log.i(
                    TAG,
                    "Not creating notification channels because Safety Center notifications are"
                            + " disabled");
            return;
        }

        List<UserHandle> users = UserUtils.getUserHandles(context);
        for (int i = 0; i < users.size(); i++) {
            createAllChannelsForUser(context, users.get(i));
        }
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group for the
     * given {@link UserHandle}, dropping any calling identity so those channels can be unblockable.
     */
    public void createAllChannelsForUser(Context context, UserHandle user) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            // TODO(b/284271124): Decide what to do with existing channels if flag gets toggled.
            Log.i(
                    TAG,
                    "Not creating notification channels because Safety Center notifications are"
                            + " disabled");
            return;
        }

        NotificationManager notificationManager = getNotificationManagerForUser(context, user);
        if (notificationManager == null) {
            return;
        }

        try {
            createAllChannelsWithoutCallingIdentity(notificationManager);
        } catch (RuntimeException e) {
            Log.w(
                    TAG,
                    "Error creating notification channels for user id: " + user.getIdentifier(),
                    e);
        }
    }

    @Nullable
    private String getChannelIdForIssue(SafetySourceIssue issue) {
        int issueSeverityLevel = issue.getSeverityLevel();
        switch (issueSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return CHANNEL_ID_INFORMATION;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return CHANNEL_ID_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return CHANNEL_ID_CRITICAL_WARNING;
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                Log.w(TAG, "SafetySourceData.SeverityLevel is unspecified for issue: " + issue);
                return null;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceData.SeverityLevel: "
                        + issueSeverityLevel
                        + ", for issue: "
                        + issue);
        return null;
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group using the
     * given {@link NotificationManager}, dropping any calling identity so those channels can be
     * unblockable. Throws a {@link RuntimeException} if any channel is malformed and could not be
     * created.
     */
    private void createAllChannelsWithoutCallingIdentity(NotificationManager notificationManager) {
        // Clearing calling identity to be able to make unblockable system notification channels and
        // call this for other users with the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.createNotificationChannelGroup(getChannelGroupDefinition());
            notificationManager.createNotificationChannel(getInformationChannelDefinition());
            notificationManager.createNotificationChannel(getRecommendationChannelDefinition());
            notificationManager.createNotificationChannel(getCriticalWarningChannelDefinition());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private void clearAllChannelsWithoutCallingIdentity(NotificationManager notificationManager) {
        // Clearing calling identity to do this for other users with the INTERACT_ACROSS_USERS
        // permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.deleteNotificationChannelGroup(CHANNEL_GROUP_ID);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private NotificationChannelGroup getChannelGroupDefinition() {
        return new NotificationChannelGroup(
                CHANNEL_GROUP_ID, getString("notification_channel_group_name"));
    }

    private NotificationChannel getInformationChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_INFORMATION,
                        getString("notification_channel_name_information"),
                        NotificationManager.IMPORTANCE_LOW);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(true);
        return channel;
    }

    private NotificationChannel getRecommendationChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_RECOMMENDATION,
                        getString("notification_channel_name_recommendation"),
                        NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private NotificationChannel getCriticalWarningChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_CRITICAL_WARNING,
                        getString("notification_channel_name_critical_warning"),
                        NotificationManager.IMPORTANCE_HIGH);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private String getString(String name) {
        return mSafetyCenterResourcesApk.getStringByName(name);
    }
}
