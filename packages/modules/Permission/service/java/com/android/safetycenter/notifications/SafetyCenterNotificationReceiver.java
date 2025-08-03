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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;
import com.android.safetycenter.ApiLock;
import com.android.safetycenter.PendingIntentFactory;
import com.android.safetycenter.SafetyCenterDataChangeNotifier;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.SafetyCenterService;
import com.android.safetycenter.SafetySourceIssues;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.data.SafetyCenterDataManager;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

/**
 * A Context-registered {@link BroadcastReceiver} that handles intents sent via Safety Center
 * notifications e.g. when a notification is dismissed.
 *
 * <p>Use {@link #register(Context)} to register this receiver with the correct {@link IntentFilter}
 * and use the {@link #newNotificationDismissedIntent(Context, SafetyCenterIssueKey)} and {@link
 * #newNotificationActionClickedIntent(Context, SafetyCenterIssueActionId)} factory methods to
 * create new {@link PendingIntent} instances for this receiver.
 *
 * @hide
 */
public final class SafetyCenterNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "SafetyCenterNR";

    private static final String ACTION_NOTIFICATION_DISMISSED =
            "com.android.safetycenter.action.NOTIFICATION_DISMISSED";
    private static final String ACTION_NOTIFICATION_ACTION_CLICKED =
            "com.android.safetycenter.action.NOTIFICATION_ACTION_CLICKED";
    private static final String EXTRA_ISSUE_KEY = "com.android.safetycenter.extra.ISSUE_KEY";
    private static final String EXTRA_ISSUE_ACTION_ID =
            "com.android.safetycenter.extra.ISSUE_ACTION_ID";

    private static final int REQUEST_CODE_UNUSED = 0;

    /**
     * Creates a broadcast {@code PendingIntent} for this receiver which will handle a Safety Center
     * notification being dismissed.
     */
    static PendingIntent newNotificationDismissedIntent(
            Context context, SafetyCenterIssueKey issueKey) {
        String issueKeyString = SafetyCenterIds.encodeToString(issueKey);
        Intent intent = new Intent(ACTION_NOTIFICATION_DISMISSED);
        intent.putExtra(EXTRA_ISSUE_KEY, issueKeyString);
        intent.setIdentifier(issueKeyString);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntentFactory.getNonProtectedSystemOnlyBroadcastPendingIntent(
                context, REQUEST_CODE_UNUSED, intent, flags);
    }

    /**
     * Creates a broadcast {@code PendingIntent} for this receiver which will handle a Safety Center
     * notification action being clicked.
     *
     * <p>Safety Center notification actions correspond to Safety Center issue actions.
     */
    static PendingIntent newNotificationActionClickedIntent(
            Context context, SafetyCenterIssueActionId issueActionId) {
        String issueActionIdString = SafetyCenterIds.encodeToString(issueActionId);
        Intent intent = new Intent(ACTION_NOTIFICATION_ACTION_CLICKED);
        intent.putExtra(EXTRA_ISSUE_ACTION_ID, issueActionIdString);
        intent.setIdentifier(issueActionIdString);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntentFactory.getNonProtectedSystemOnlyBroadcastPendingIntent(
                context, REQUEST_CODE_UNUSED, intent, flags);
    }

    @Nullable
    private static SafetyCenterIssueKey getIssueKeyExtra(Intent intent) {
        String issueKeyString = intent.getStringExtra(EXTRA_ISSUE_KEY);
        if (issueKeyString == null) {
            Log.w(TAG, "Received notification dismissed broadcast with null issue key extra");
            return null;
        }
        try {
            return SafetyCenterIds.issueKeyFromString(issueKeyString);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Could not decode the issue key extra", e);
            return null;
        }
    }

    @Nullable
    private static SafetyCenterIssueActionId getIssueActionIdExtra(Intent intent) {
        String issueActionIdString = intent.getStringExtra(EXTRA_ISSUE_ACTION_ID);
        if (issueActionIdString == null) {
            Log.w(TAG, "Received notification action broadcast with null issue action id");
            return null;
        }
        try {
            return SafetyCenterIds.issueActionIdFromString(issueActionIdString);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Could not decode the issue action id", e);
            return null;
        }
    }

    private final SafetyCenterService mService;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataManager mSafetyCenterDataManager;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataChangeNotifier mSafetyCenterDataChangeNotifier;

    private final ApiLock mApiLock;

    public SafetyCenterNotificationReceiver(
            SafetyCenterService service,
            SafetyCenterDataManager safetyCenterDataManager,
            SafetyCenterDataChangeNotifier safetyCenterDataChangeNotifier,
            ApiLock apiLock) {
        mService = service;
        mSafetyCenterDataManager = safetyCenterDataManager;
        mSafetyCenterDataChangeNotifier = safetyCenterDataChangeNotifier;
        mApiLock = apiLock;
    }

    /**
     * Register this receiver in the given {@link Context} with an {@link IntentFilter} that matches
     * any intents created by this class' static factory methods.
     *
     * @see #newNotificationDismissedIntent(Context, SafetyCenterIssueKey)
     */
    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFICATION_DISMISSED);
        filter.addAction(ACTION_NOTIFICATION_ACTION_CLICKED);
        context.registerReceiver(/* receiver= */ this, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SafetyCenterFlags.getSafetyCenterEnabled()) {
            Log.i(TAG, "Received notification broadcast but Safety Center is disabled");
            return;
        }

        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            // TODO(b/284271124): Decide what to do with existing notifications
            Log.i(TAG, "Received notification broadcast but notifications are disabled");
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Received broadcast with null action");
            return;
        }
        Log.d(TAG, "Received broadcast with action: " + action);

        switch (action) {
            case ACTION_NOTIFICATION_DISMISSED:
                onNotificationDismissed(context, intent);
                break;
            case ACTION_NOTIFICATION_ACTION_CLICKED:
                onNotificationActionClicked(context, intent);
                break;
            default:
                Log.w(TAG, "Received broadcast with unrecognized action: " + action);
                break;
        }
    }

    private void onNotificationDismissed(Context context, Intent intent) {
        SafetyCenterIssueKey issueKey = getIssueKeyExtra(intent);
        if (issueKey == null) {
            return;
        }

        int userId = issueKey.getUserId();
        UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(context, userId);

        SafetySourceIssue dismissedIssue;
        synchronized (mApiLock) {
            dismissedIssue = mSafetyCenterDataManager.getSafetySourceIssue(issueKey);
            mSafetyCenterDataManager.dismissNotification(issueKey);
            mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
        }

        if (dismissedIssue != null) {
            SafetyCenterStatsdLogger.writeNotificationDismissedEvent(
                    issueKey.getSafetySourceId(),
                    UserProfileGroup.getProfileTypeOfUser(userId, context),
                    dismissedIssue.getIssueTypeId(),
                    dismissedIssue.getSeverityLevel());
        }
    }

    private void onNotificationActionClicked(Context context, Intent intent) {
        SafetyCenterIssueActionId issueActionId = getIssueActionIdExtra(intent);
        if (issueActionId == null) {
            return;
        }

        mService.executeIssueActionInternal(issueActionId);
        logNotificationActionClicked(context, issueActionId);
    }

    private void logNotificationActionClicked(
            Context context, SafetyCenterIssueActionId issueActionId) {
        SafetyCenterIssueKey issueKey = issueActionId.getSafetyCenterIssueKey();
        SafetySourceIssue issue;
        synchronized (mApiLock) {
            issue = mSafetyCenterDataManager.getSafetySourceIssue(issueKey);
        }
        if (issue != null) {
            SafetyCenterStatsdLogger.writeNotificationActionClickedEvent(
                    issueKey.getSafetySourceId(),
                    UserProfileGroup.getProfileTypeOfUser(issueKey.getUserId(), context),
                    issue.getIssueTypeId(),
                    issue.getSeverityLevel(),
                    SafetySourceIssues.isPrimaryAction(
                            issue, issueActionId.getSafetySourceIssueActionId()));
        }
    }
}
