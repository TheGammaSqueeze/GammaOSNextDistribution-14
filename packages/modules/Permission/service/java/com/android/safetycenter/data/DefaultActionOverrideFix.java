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

package com.android.safetycenter.data;

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.Context;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.PendingIntentFactory;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterFlags;

import java.util.List;

/**
 * Replaces {@link SafetySourceIssue.Action}s with the corresponding source's default intent drawn
 * from the Safety Center config.
 *
 * <p>Actions to be replaced are controlled by the {@code
 * safety_center_actions_to_override_with_default_intent} DeviceConfig flag.
 *
 * <p>This is done to support cases where we allow OEMs to override intents in the config, but
 * sources are unaware of and unable to access those overrides when providing issues and
 * notifications. We use the default intent when sources provide a null pending intent in their
 * status. This fix allows us to implement a similar behavior for actions, without changing the
 * non-null requirement on their pending intent fields.
 */
final class DefaultActionOverrideFix {

    private final Context mContext;
    private final PendingIntentFactory mPendingIntentFactory;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    DefaultActionOverrideFix(
            Context context,
            PendingIntentFactory pendingIntentFactory,
            SafetyCenterConfigReader safetyCenterConfigReader) {
        mContext = context;
        mPendingIntentFactory = pendingIntentFactory;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    static boolean shouldApplyFix(String sourceId) {
        List<String> actionsToOverride =
                SafetyCenterFlags.getActionsToOverrideWithDefaultIntentForSource(sourceId);
        return !actionsToOverride.isEmpty();
    }

    SafetySourceData applyFix(
            String sourceId,
            SafetySourceData safetySourceData,
            String packageName,
            @UserIdInt int userId) {
        if (safetySourceData.getIssues().isEmpty()) {
            return safetySourceData;
        }

        PendingIntent defaultIntentForSource =
                getDefaultIntentForSource(sourceId, packageName, userId);
        if (defaultIntentForSource == null) {
            // If there's no default intent, we can't override any actions with it.
            return safetySourceData;
        }

        List<String> actionsToOverride =
                SafetyCenterFlags.getActionsToOverrideWithDefaultIntentForSource(sourceId);
        if (actionsToOverride.isEmpty()) {
            // This shouldn't happen if shouldApplyFix is called first, but we check for good
            // measure.
            return safetySourceData;
        }

        SafetySourceData.Builder overriddenSafetySourceData =
                SafetySourceDataOverrides.copyDataToBuilderWithoutIssues(safetySourceData);
        List<SafetySourceIssue> issues = safetySourceData.getIssues();
        for (int i = 0; i < issues.size(); i++) {
            overriddenSafetySourceData.addIssue(
                    maybeOverrideActionsWithDefaultIntent(
                            issues.get(i), actionsToOverride, defaultIntentForSource));
        }

        return overriddenSafetySourceData.build();
    }

    @Nullable
    private PendingIntent getDefaultIntentForSource(
            String sourceId, String packageName, @UserIdInt int userId) {
        SafetyCenterConfigReader.ExternalSafetySource externalSafetySource =
                mSafetyCenterConfigReader.getExternalSafetySource(sourceId, packageName);
        if (externalSafetySource == null) {
            return null;
        }

        boolean isQuietModeEnabled =
                UserUtils.isManagedProfile(userId, mContext)
                        && !UserUtils.isProfileRunning(userId, mContext);

        return mPendingIntentFactory.getPendingIntent(
                sourceId,
                externalSafetySource.getSafetySource().getIntentAction(),
                packageName,
                userId,
                isQuietModeEnabled);
    }

    private SafetySourceIssue maybeOverrideActionsWithDefaultIntent(
            SafetySourceIssue issue, List<String> actionsToOverride, PendingIntent defaultIntent) {
        SafetySourceIssue.Builder overriddenIssue =
                SafetySourceDataOverrides.copyIssueToBuilderWithoutActions(issue);

        List<SafetySourceIssue.Action> actions = issue.getActions();
        for (int i = 0; i < actions.size(); i++) {
            overriddenIssue.addAction(
                    maybeOverrideAction(actions.get(i), actionsToOverride, defaultIntent));
        }

        if (SdkLevel.isAtLeastU()) {
            overriddenIssue.setCustomNotification(
                    maybeOverrideNotification(
                            issue.getCustomNotification(), actionsToOverride, defaultIntent));
        }

        return overriddenIssue.build();
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    @Nullable
    private static SafetySourceIssue.Notification maybeOverrideNotification(
            @Nullable SafetySourceIssue.Notification notification,
            List<String> actionsToOverride,
            PendingIntent defaultIntent) {
        if (notification == null) {
            return null;
        }

        SafetySourceIssue.Notification.Builder overriddenNotification =
                new SafetySourceIssue.Notification.Builder(notification).clearActions();

        List<SafetySourceIssue.Action> actions = notification.getActions();
        for (int i = 0; i < actions.size(); i++) {
            overriddenNotification.addAction(
                    maybeOverrideAction(actions.get(i), actionsToOverride, defaultIntent));
        }

        return overriddenNotification.build();
    }

    private static SafetySourceIssue.Action maybeOverrideAction(
            SafetySourceIssue.Action action,
            List<String> actionsToOverride,
            PendingIntent defaultIntent) {
        if (actionsToOverride.contains(action.getId())) {
            return SafetySourceDataOverrides.overrideActionPendingIntent(action, defaultIntent);
        }
        return action;
    }
}
