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

import android.app.PendingIntent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;

import com.android.modules.utils.build.SdkLevel;

final class SafetySourceDataOverrides {
    private SafetySourceDataOverrides() {}

    static SafetySourceData.Builder copyDataToBuilderWithoutIssues(SafetySourceData data) {
        if (SdkLevel.isAtLeastU()) {
            return new SafetySourceData.Builder(data).clearIssues();
        }

        // Copy T-only fields
        return new SafetySourceData.Builder().setStatus(data.getStatus());
    }

    static SafetySourceStatus.Builder copyStatusToBuilder(SafetySourceStatus status) {
        if (SdkLevel.isAtLeastU()) {
            return new SafetySourceStatus.Builder(status);
        }

        // Copy T-only fields
        return new SafetySourceStatus.Builder(
                        status.getTitle(), status.getSummary(), status.getSeverityLevel())
                .setPendingIntent(status.getPendingIntent())
                .setEnabled(status.isEnabled())
                .setIconAction(status.getIconAction());
    }

    static SafetySourceIssue.Builder copyIssueToBuilderWithoutActions(SafetySourceIssue issue) {
        if (SdkLevel.isAtLeastU()) {
            return new SafetySourceIssue.Builder(issue).clearActions();
        }

        // Copy T-only fields
        return new SafetySourceIssue.Builder(
                        issue.getId(),
                        issue.getTitle(),
                        issue.getSummary(),
                        issue.getSeverityLevel(),
                        issue.getIssueTypeId())
                .setIssueCategory(issue.getIssueCategory())
                .setSubtitle(issue.getSubtitle())
                .setOnDismissPendingIntent(issue.getOnDismissPendingIntent());
    }

    /**
     * Returns an new {@link SafetySourceIssue.Action} object, replacing its {@link PendingIntent}
     * with the one supplied.
     */
    static SafetySourceIssue.Action overrideActionPendingIntent(
            SafetySourceIssue.Action action, PendingIntent pendingIntent) {
        // TODO(b/303443020): Add setter for pendingIntent so this method can use the copy builder.
        return new SafetySourceIssue.Action.Builder(
                        action.getId(), action.getLabel(), pendingIntent)
                .setWillResolve(action.willResolve())
                .setSuccessMessage(action.getSuccessMessage())
                .build();
    }
}
