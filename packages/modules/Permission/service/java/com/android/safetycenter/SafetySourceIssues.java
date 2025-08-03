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

package com.android.safetycenter;

import android.safetycenter.SafetySourceIssue;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.util.List;

/**
 * A helper class to facilitate working with {@link SafetySourceIssue} objects.
 *
 * @hide
 */
public final class SafetySourceIssues {

    /**
     * Returns the {@link SafetySourceIssue.Action} with the given action ID belonging to the given
     * {@link SafetySourceIssue} or {@code null} if no such action is present.
     *
     * <p>The action will either belong to the issue directly from {@link
     * SafetySourceIssue#getActions()} or via {@link SafetySourceIssue#getCustomNotification()} if
     * the issue has a custom notification.
     */
    @Nullable
    public static SafetySourceIssue.Action findAction(SafetySourceIssue issue, String actionId) {
        SafetySourceIssue.Action action = null;
        if (SdkLevel.isAtLeastU() && issue.getCustomNotification() != null) {
            action = findAction(issue.getCustomNotification().getActions(), actionId);
        }
        if (action == null) {
            action = findAction(issue.getActions(), actionId);
        }
        return action;
    }

    @Nullable
    private static SafetySourceIssue.Action findAction(
            List<SafetySourceIssue.Action> actions, String actionId) {
        for (int i = 0; i < actions.size(); i++) {
            SafetySourceIssue.Action action = actions.get(i);
            if (action.getId().equals(actionId)) {
                return action;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if {@code actionId} corresponds to a "primary" action of the given
     * {@code issue}, or {@code false} if the action is not the primary or if no action with the
     * given ID is found.
     *
     * <p>A primary action is the first action of either the issue, or its custom notification.
     */
    public static boolean isPrimaryAction(SafetySourceIssue issue, String actionId) {
        boolean isPrimaryNotificationAction =
                SdkLevel.isAtLeastU()
                        && issue.getCustomNotification() != null
                        && matchesFirst(issue.getCustomNotification().getActions(), actionId);
        boolean isPrimaryIssueAction = matchesFirst(issue.getActions(), actionId);
        return isPrimaryNotificationAction || isPrimaryIssueAction;
    }

    private static boolean matchesFirst(List<SafetySourceIssue.Action> actions, String actionId) {
        return !actions.isEmpty() && actions.get(0).getId().equals(actionId);
    }

    private SafetySourceIssues() {}
}
