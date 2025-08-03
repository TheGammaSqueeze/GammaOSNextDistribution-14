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

import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED;
import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED;

import android.annotation.UserIdInt;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;

import java.util.List;

/**
 * Works around sources sending unexpected {@link SafetyEvent}s by optionally replacing them using
 * heuristics based on the incoming {@link SafetySourceData} and Safety Center's current state.
 *
 * @hide
 */
public final class SafetyEventFix {

    private static final String TAG = "SafetyEventFix";

    private SafetyEventFix() {}

    /**
     * Optionally returns a new {@link SafetyEvent} if heuristics indicate that the one provided by
     * the source is inappropriate, otherwise returns the source-provided event unchanged.
     *
     * <p>If the incoming event has type {@link SafetyEvent#SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED}
     * but the {@link SafetySourceData} no longer includes an issue, for which Safety Center has a
     * record of an in-flight, resolving action, then the event will be exchanged for a new one of
     * type {@link SafetyEvent#SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED}.
     */
    public static SafetyEvent maybeOverrideSafetyEvent(
            SafetyCenterDataManager dataManager,
            String safetySourceId,
            @Nullable SafetySourceData safetySourceData,
            SafetyEvent safetyEvent,
            @UserIdInt int userId) {
        if (safetySourceData == null
                || safetyEvent.getType() != SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED) {
            return safetyEvent;
        }

        ArraySet<SafetyCenterIssueActionId> possiblySuccessfulActions =
                dataManager.getInFlightActions(safetySourceId, userId);

        if (possiblySuccessfulActions.isEmpty()) {
            return safetyEvent;
        }

        // Discard any actions for which the issue is still present in the latest source data, they
        // cannot have been resolved successfully!
        ArraySet<String> presentSourceIssueIds = getSourceIssueIds(safetySourceData);
        for (int i = possiblySuccessfulActions.size() - 1; i >= 0; i--) {
            String sourceIssueId =
                    possiblySuccessfulActions
                            .valueAt(i)
                            .getSafetyCenterIssueKey()
                            .getSafetySourceIssueId();
            if (presentSourceIssueIds.contains(sourceIssueId)) {
                possiblySuccessfulActions.removeAt(i);
            }
        }

        if (possiblySuccessfulActions.isEmpty()) {
            return safetyEvent;
        }

        if (possiblySuccessfulActions.size() > 1) {
            Log.i(TAG, "Multiple actions resolved, not overriding " + safetyEvent);
            return safetyEvent;
        }

        SafetyCenterIssueActionId successfulAction = possiblySuccessfulActions.valueAt(0);
        SafetyEvent replacement = newActionSucceededEvent(successfulAction);
        Log.i(TAG, "Replacing incoming " + safetyEvent + " with " + replacement);
        return replacement;
    }

    private static ArraySet<String> getSourceIssueIds(SafetySourceData safetySourceData) {
        List<SafetySourceIssue> issues = safetySourceData.getIssues();
        ArraySet<String> issueIds = new ArraySet<>(issues.size());
        for (int i = 0; i < issues.size(); i++) {
            issueIds.add(issues.get(i).getId());
        }
        return issueIds;
    }

    private static SafetyEvent newActionSucceededEvent(SafetyCenterIssueActionId actionId) {
        return new SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                .setSafetySourceIssueId(actionId.getSafetyCenterIssueKey().getSafetySourceIssueId())
                .setSafetySourceIssueActionId(actionId.getSafetySourceIssueActionId())
                .build();
    }
}
