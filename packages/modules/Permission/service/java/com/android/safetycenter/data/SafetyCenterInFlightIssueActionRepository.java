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

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.SystemClock;
import android.safetycenter.SafetySourceIssue;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.SafetySourceIssues;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.PrintWriter;
import java.time.Duration;

import javax.annotation.concurrent.NotThreadSafe;

/** Maintains data about in-flight issue actions. */
@NotThreadSafe
final class SafetyCenterInFlightIssueActionRepository {

    private static final String TAG = "SafetyCenterInFlight";

    private final ArrayMap<SafetyCenterIssueActionId, Long> mSafetyCenterIssueActionsInFlight =
            new ArrayMap<>();

    private final Context mContext;

    /** Constructs a new instance of {@link SafetyCenterInFlightIssueActionRepository}. */
    SafetyCenterInFlightIssueActionRepository(Context context) {
        mContext = context;
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    void markSafetyCenterIssueActionInFlight(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterIssueActionsInFlight.put(
                safetyCenterIssueActionId, SystemClock.elapsedRealtime());
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight and returns {@code true} if
     * the given action was valid and unmarked successfully.
     *
     * <p>Also logs an event to statsd with the given {@code result} value.
     */
    boolean unmarkSafetyCenterIssueActionInFlight(
            SafetyCenterIssueActionId safetyCenterIssueActionId,
            @Nullable SafetySourceIssue safetySourceIssue,
            @SafetyCenterStatsdLogger.SystemEventResult int result) {
        Long startElapsedMillis =
                mSafetyCenterIssueActionsInFlight.remove(safetyCenterIssueActionId);
        if (startElapsedMillis == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark unknown in-flight action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        SafetyCenterIssueKey issueKey = safetyCenterIssueActionId.getSafetyCenterIssueKey();
        String issueTypeId = safetySourceIssue == null ? null : safetySourceIssue.getIssueTypeId();
        Duration duration = Duration.ofMillis(SystemClock.elapsedRealtime() - startElapsedMillis);

        SafetyCenterStatsdLogger.writeInlineActionSystemEvent(
                issueKey.getSafetySourceId(),
                UserProfileGroup.getProfileTypeOfUser(issueKey.getUserId(), mContext),
                issueTypeId,
                duration,
                result);

        if (safetySourceIssue == null
                || getSafetySourceIssueAction(safetyCenterIssueActionId, safetySourceIssue)
                        == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark in-flight action for a non-existent issue or action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        return true;
    }

    /** Returns {@code true} if the given issue action is in flight. */
    boolean actionIsInFlight(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterIssueActionsInFlight.containsKey(safetyCenterIssueActionId);
    }

    /** Returns a list of IDs of in-flight actions for the given source and user */
    ArraySet<SafetyCenterIssueActionId> getInFlightActions(String sourceId, @UserIdInt int userId) {
        ArraySet<SafetyCenterIssueActionId> result = new ArraySet<>();
        for (int i = 0; i < mSafetyCenterIssueActionsInFlight.size(); i++) {
            SafetyCenterIssueActionId actionId = mSafetyCenterIssueActionsInFlight.keyAt(i);
            SafetyCenterIssueKey issueKey = actionId.getSafetyCenterIssueKey();
            if (sourceId.equals(issueKey.getSafetySourceId()) && issueKey.getUserId() == userId) {
                result.add(actionId);
            }
        }
        return result;
    }

    /**
     * Returns {@link SafetySourceIssue.Action} identified by the given {@link
     * SafetyCenterIssueActionId} and {@link SafetySourceIssue}.
     */
    @Nullable
    SafetySourceIssue.Action getSafetySourceIssueAction(
            SafetyCenterIssueActionId safetyCenterIssueActionId,
            SafetySourceIssue safetySourceIssue) {
        if (actionIsInFlight(safetyCenterIssueActionId)) {
            return null;
        }

        return SafetySourceIssues.findAction(
                safetySourceIssue, safetyCenterIssueActionId.getSafetySourceIssueActionId());
    }

    /** Dumps in-flight action data for debugging purposes. */
    void dump(PrintWriter fout) {
        int actionInFlightCount = mSafetyCenterIssueActionsInFlight.size();
        fout.println("ACTIONS IN FLIGHT (" + actionInFlightCount + ")");
        for (int i = 0; i < actionInFlightCount; i++) {
            String printableId = toUserFriendlyString(mSafetyCenterIssueActionsInFlight.keyAt(i));
            long startElapsedMillis = mSafetyCenterIssueActionsInFlight.valueAt(i);
            long durationMillis = SystemClock.elapsedRealtime() - startElapsedMillis;
            fout.println("\t[" + i + "] " + printableId + "(duration=" + durationMillis + "ms)");
        }
        fout.println();
    }

    /** Clears all in-flight action data. */
    void clear() {
        mSafetyCenterIssueActionsInFlight.clear();
    }

    /** Clears in-flight action data for given {@code userId}. */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetyCenterIssueActionsInFlight.size() - 1; i >= 0; i--) {
            SafetyCenterIssueActionId issueActionId = mSafetyCenterIssueActionsInFlight.keyAt(i);
            if (issueActionId.getSafetyCenterIssueKey().getUserId() == userId) {
                mSafetyCenterIssueActionsInFlight.removeAt(i);
            }
        }
    }
}
