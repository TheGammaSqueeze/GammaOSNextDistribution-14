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

import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.IntDef;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetySource;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceIssues;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.data.SafetyCenterDataManager;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Class responsible for posting, updating and dismissing Safety Center notifications each time
 * Safety Center's issues change.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterNotificationSender {

    private static final String TAG = "SafetyCenterNS";

    // We use a fixed notification ID because notifications are keyed by (tag, id) and it easier
    // to differentiate our notifications using the tag
    private static final int FIXED_NOTIFICATION_ID = 2345;

    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_NEVER = 100;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED = 200;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY = 300;

    /**
     * Internal notification behavior {@code @IntDef} which is related to the {@code
     * SafetySourceIssue.NotificationBehavior} type introduced in Android U.
     *
     * <p>This definition is available on T+.
     *
     * <p>Unlike the U+/external {@code @IntDef}, this one has no "unspecified behavior" value. Any
     * issues which have unspecified behavior are resolved to one of these specific behaviors based
     * on their other properties.
     */
    @IntDef(
            prefix = {"NOTIFICATION_BEHAVIOR_INTERNAL"},
            value = {
                NOTIFICATION_BEHAVIOR_INTERNAL_NEVER,
                NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED,
                NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY
            })
    @Retention(RetentionPolicy.SOURCE)
    private @interface NotificationBehaviorInternal {}

    private final Context mContext;

    private final SafetyCenterNotificationFactory mNotificationFactory;

    private final SafetyCenterDataManager mSafetyCenterDataManager;

    private final ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> mNotifiedIssues =
            new ArrayMap<>();

    private SafetyCenterNotificationSender(
            Context context,
            SafetyCenterNotificationFactory notificationFactory,
            SafetyCenterDataManager safetyCenterDataManager) {
        mContext = context;
        mNotificationFactory = notificationFactory;
        mSafetyCenterDataManager = safetyCenterDataManager;
    }

    public static SafetyCenterNotificationSender newInstance(
            Context context,
            SafetyCenterResourcesApk safetyCenterResourcesApk,
            SafetyCenterNotificationChannels notificationChannels,
            SafetyCenterDataManager dataManager) {
        return new SafetyCenterNotificationSender(
                context,
                new SafetyCenterNotificationFactory(
                        context, notificationChannels, safetyCenterResourcesApk),
                dataManager);
    }

    /**
     * Replaces an issue's notification with one displaying the success message of the {@link
     * SafetySourceIssue.Action} that resolved that issue.
     *
     * <p>The given {@link SafetyEvent} have type {@link
     * SafetyEvent#SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED} and include issue and action IDs
     * that correspond to a {@link SafetySourceIssue} for which a notification is currently
     * displayed. Otherwise, this method has no effect.
     *
     * @param sourceId of the source which reported the issue
     * @param safetyEvent the source provided upon successful action resolution
     * @param userId to which the source, issue and notification belong
     */
    public void notifyActionSuccess(
            String sourceId, SafetyEvent safetyEvent, @UserIdInt int userId) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            // TODO(b/284271124): Decide what to do with existing notifications if flag gets
            // toggled.
            return;
        }

        if (safetyEvent.getType() != SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED) {
            Log.w(TAG, "Received safety event of wrong type");
            return;
        }

        String sourceIssueId = safetyEvent.getSafetySourceIssueId();
        if (sourceIssueId == null) {
            Log.w(TAG, "Received safety event without a safety source issue id");
            return;
        }

        String sourceIssueActionId = safetyEvent.getSafetySourceIssueActionId();
        if (sourceIssueActionId == null) {
            Log.w(TAG, "Received safety event without a safety source issue action id");
            return;
        }

        SafetyCenterIssueKey issueKey =
                SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceId)
                        .setSafetySourceIssueId(sourceIssueId)
                        .setUserId(userId)
                        .build();
        SafetySourceIssue notifiedIssue = mNotifiedIssues.get(issueKey);
        if (notifiedIssue == null) {
            Log.w(TAG, "No notification for this issue");
            return;
        }

        SafetySourceIssue.Action successfulAction =
                SafetySourceIssues.findAction(notifiedIssue, sourceIssueActionId);
        if (successfulAction == null) {
            Log.w(TAG, "Successful action not found");
            return;
        }

        NotificationManager notificationManager = getNotificationManagerForUser(userId);

        if (notificationManager == null) {
            return;
        }

        Notification notification =
                mNotificationFactory.newNotificationForSuccessfulAction(
                        notificationManager, notifiedIssue, successfulAction, userId);
        if (notification == null) {
            Log.w(TAG, "Could not create successful action notification");
            return;
        }
        String tag = getNotificationTag(issueKey);
        boolean wasPosted = notifyFromSystem(notificationManager, tag, notification);
        if (wasPosted) {
            // If the original issue notification was successfully replaced the key removed from
            // mNotifiedIssues to prevent the success notification from being removed by
            // cancelStaleNotifications below.
            mNotifiedIssues.remove(issueKey);
        }
    }

    /** Updates Safety Center notifications for the given {@link UserProfileGroup}. */
    public void updateNotifications(UserProfileGroup userProfileGroup) {
        int[] allProfilesUserIds = userProfileGroup.getAllProfilesUserIds();
        for (int i = 0; i < allProfilesUserIds.length; i++) {
            updateNotifications(allProfilesUserIds[i]);
        }
    }

    /**
     * Updates Safety Center notifications, usually in response to a change in the issues for the
     * given userId.
     */
    public void updateNotifications(@UserIdInt int userId) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            // TODO(b/284271124): Decide what to do with existing notifications
            return;
        }

        NotificationManager notificationManager = getNotificationManagerForUser(userId);

        if (notificationManager == null) {
            return;
        }

        ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> issuesToNotify =
                getIssuesToNotify(userId);

        // Post or update notifications for notifiable issues. We keep track of the "fresh" issues
        // keys of those issues which were just notified because doing so allows us to cancel any
        // notifications for other, non-fresh issues.
        ArraySet<SafetyCenterIssueKey> freshIssueKeys = new ArraySet<>();
        for (int i = 0; i < issuesToNotify.size(); i++) {
            SafetyCenterIssueKey issueKey = issuesToNotify.keyAt(i);
            SafetySourceIssue issue = issuesToNotify.valueAt(i);

            boolean unchanged = issue.equals(mNotifiedIssues.get(issueKey));
            if (unchanged) {
                freshIssueKeys.add(issueKey);
                continue;
            }

            boolean wasPosted = postNotificationForIssue(notificationManager, issue, issueKey);
            if (wasPosted) {
                freshIssueKeys.add(issueKey);
            }
        }

        cancelStaleNotifications(notificationManager, userId, freshIssueKeys);
    }

    /** Cancels all notifications previously posted by this class */
    public void cancelAllNotifications() {
        // Loop in reverse index order to be able to remove entries while iterating
        for (int i = mNotifiedIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mNotifiedIssues.keyAt(i);
            int userId = issueKey.getUserId();
            NotificationManager notificationManager = getNotificationManagerForUser(userId);
            if (notificationManager == null) {
                continue;
            }
            cancelNotificationFromSystem(notificationManager, getNotificationTag(issueKey));
            mNotifiedIssues.removeAt(i);
        }
    }

    /** Dumps state for debugging purposes. */
    public void dump(PrintWriter fout) {
        int notifiedIssuesCount = mNotifiedIssues.size();
        fout.println("NOTIFICATION SENDER (" + notifiedIssuesCount + " notified issues)");
        for (int i = 0; i < notifiedIssuesCount; i++) {
            SafetyCenterIssueKey key = mNotifiedIssues.keyAt(i);
            SafetySourceIssue issue = mNotifiedIssues.valueAt(i);
            fout.println("\t[" + i + "] " + toUserFriendlyString(key) + " -> " + issue);
        }
        fout.println();
    }

    /** Gets all the key-issue pairs for which notifications should be posted or updated now. */
    private ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> getIssuesToNotify(
            @UserIdInt int userId) {
        ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> result = new ArrayMap<>();
        List<SafetySourceIssueInfo> allIssuesInfo =
                mSafetyCenterDataManager.getIssuesForUser(userId);

        for (int i = 0; i < allIssuesInfo.size(); i++) {
            SafetySourceIssueInfo issueInfo = allIssuesInfo.get(i);
            SafetyCenterIssueKey issueKey = issueInfo.getSafetyCenterIssueKey();
            SafetySourceIssue issue = issueInfo.getSafetySourceIssue();

            if (!areNotificationsAllowedForSource(issueInfo.getSafetySource())) {
                continue;
            }

            if (mSafetyCenterDataManager.isNotificationDismissedNow(
                    issueKey, issue.getSeverityLevel())) {
                continue;
            }

            // Get the notification behavior for this issue which determines whether we should
            // send a notification about it now
            int behavior = getBehavior(issue, issueKey);
            if (behavior == NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY) {
                result.put(issueKey, issue);
            } else if (behavior == NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED) {
                if (canNotifyDelayedIssueNow(issueKey)) {
                    result.put(issueKey, issue);
                }
                // TODO(b/259094736): else handle delayed notifications using a scheduled job
            }
        }
        return result;
    }

    @NotificationBehaviorInternal
    private int getBehavior(SafetySourceIssue issue, SafetyCenterIssueKey issueKey) {
        if (SdkLevel.isAtLeastU()) {
            int notificationBehavior = issue.getNotificationBehavior();
            switch (notificationBehavior) {
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_NEVER;
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED;
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY;
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_UNSPECIFIED:
                    return getBehaviorForIssueWithUnspecifiedBehavior(issue, issueKey);
            }
            Log.w(
                    TAG,
                    "Unexpected SafetySourceIssue.NotificationBehavior: " + notificationBehavior);
        }
        // On Android T all issues are assumed to have "unspecified" behavior
        return getBehaviorForIssueWithUnspecifiedBehavior(issue, issueKey);
    }

    @NotificationBehaviorInternal
    private int getBehaviorForIssueWithUnspecifiedBehavior(
            SafetySourceIssue issue, SafetyCenterIssueKey issueKey) {
        String flagKey = issueKey.getSafetySourceId() + "/" + issue.getIssueTypeId();
        if (SafetyCenterFlags.getImmediateNotificationBehaviorIssues().contains(flagKey)) {
            return NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY;
        } else {
            return NOTIFICATION_BEHAVIOR_INTERNAL_NEVER;
        }
    }

    private boolean areNotificationsAllowedForSource(SafetySource safetySource) {
        if (SdkLevel.isAtLeastU()) {
            if (safetySource.areNotificationsAllowed()) {
                return true;
            }
        }
        return SafetyCenterFlags.getNotificationsAllowedSourceIds().contains(safetySource.getId());
    }

    private boolean canNotifyDelayedIssueNow(SafetyCenterIssueKey issueKey) {
        Duration minNotificationsDelay = SafetyCenterFlags.getNotificationsMinDelay();
        Instant threshold = Instant.now().minus(minNotificationsDelay);
        Instant seenAt = mSafetyCenterDataManager.getIssueFirstSeenAt(issueKey);
        return seenAt != null && seenAt.isBefore(threshold);
    }

    private boolean postNotificationForIssue(
            NotificationManager notificationManager,
            SafetySourceIssue issue,
            SafetyCenterIssueKey key) {
        Notification notification =
                mNotificationFactory.newNotificationForIssue(notificationManager, issue, key);
        if (notification == null) {
            return false;
        }
        String tag = getNotificationTag(key);
        boolean wasPosted = notifyFromSystem(notificationManager, tag, notification);
        if (wasPosted) {
            mNotifiedIssues.put(key, issue);
            SafetyCenterStatsdLogger.writeNotificationPostedEvent(
                    key.getSafetySourceId(),
                    UserProfileGroup.getProfileTypeOfUser(key.getUserId(), mContext),
                    issue.getIssueTypeId(),
                    issue.getSeverityLevel());
        }
        return wasPosted;
    }

    private void cancelStaleNotifications(
            NotificationManager notificationManager,
            @UserIdInt int userId,
            ArraySet<SafetyCenterIssueKey> freshIssueKeys) {
        // Loop in reverse index order to be able to remove entries while iterating
        for (int i = mNotifiedIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey key = mNotifiedIssues.keyAt(i);
            if (key.getUserId() == userId && !freshIssueKeys.contains(key)) {
                String tag = getNotificationTag(key);
                cancelNotificationFromSystem(notificationManager, tag);
                mNotifiedIssues.removeAt(i);
            }
        }
    }

    private static String getNotificationTag(SafetyCenterIssueKey issueKey) {
        // Base 64 encoding of the issueKey proto:
        return SafetyCenterIds.encodeToString(issueKey);
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @Nullable
    private NotificationManager getNotificationManagerForUser(@UserIdInt int userId) {
        return SafetyCenterNotificationChannels.getNotificationManagerForUser(
                mContext, UserHandle.of(userId));
    }

    /**
     * Sends a {@link Notification} from the system, dropping any calling identity. Returns {@code
     * true} if successful or {@code false} otherwise.
     *
     * <p>The recipient of the notification depends on the {@link Context} of the given {@link
     * NotificationManager}. Use {@link #getNotificationManagerForUser(int)} to send notifications
     * to a specific user.
     */
    private boolean notifyFromSystem(
            NotificationManager notificationManager,
            @Nullable String tag,
            Notification notification) {
        // This call is needed to send a notification from the system and this also grants the
        // necessary POST_NOTIFICATIONS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            // The fixed notification ID is OK because notifications are keyed by (tag, id)
            notificationManager.notify(tag, FIXED_NOTIFICATION_ID, notification);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Unable to send system notification", e);
            return false;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Cancels a {@link Notification} from the system, dropping any calling identity.
     *
     * <p>The recipient of the notification depends on the {@link Context} of the given {@link
     * NotificationManager}. Use {@link #getNotificationManagerForUser(int)} to cancel notifications
     * sent to a specific user.
     */
    private void cancelNotificationFromSystem(
            NotificationManager notificationManager, @Nullable String tag) {
        // This call is needed to cancel a notification previously sent from the system
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.cancel(tag, FIXED_NOTIFICATION_ID);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to cancel system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
