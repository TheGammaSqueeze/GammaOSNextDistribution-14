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

package com.android.server.net;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PRIVATE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Helper class for NetworkStatsService to log events.
 *
 * @hide
 */
public class NetworkStatsEventLogger {
    static final int POLL_REASON_DUMPSYS = 0;
    static final int POLL_REASON_FORCE_UPDATE = 1;
    static final int POLL_REASON_GLOBAL_ALERT = 2;
    static final int POLL_REASON_NETWORK_STATUS_CHANGED = 3;
    static final int POLL_REASON_OPEN_SESSION = 4;
    static final int POLL_REASON_PERIODIC = 5;
    static final int POLL_REASON_RAT_CHANGED = 6;
    static final int POLL_REASON_REG_CALLBACK = 7;
    static final int POLL_REASON_REMOVE_UIDS = 8;
    static final int POLL_REASON_UPSTREAM_CHANGED = 9;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "POLL_REASON_" }, value = {
            POLL_REASON_DUMPSYS,
            POLL_REASON_FORCE_UPDATE,
            POLL_REASON_GLOBAL_ALERT,
            POLL_REASON_NETWORK_STATUS_CHANGED,
            POLL_REASON_OPEN_SESSION,
            POLL_REASON_PERIODIC,
            POLL_REASON_RAT_CHANGED,
            POLL_REASON_REMOVE_UIDS,
            POLL_REASON_REG_CALLBACK,
            POLL_REASON_UPSTREAM_CHANGED
    })
    public @interface PollReason {
    }
    static final int MAX_POLL_REASON = POLL_REASON_UPSTREAM_CHANGED;

    @VisibleForTesting(visibility = PRIVATE)
    public static final int MAX_EVENTS_LOGS = 50;
    private final LocalLog mEventChanges = new LocalLog(MAX_EVENTS_LOGS);
    private final int[] mPollEventCounts = new int[MAX_POLL_REASON + 1];

    /**
     * Log a poll event.
     *
     * @param flags Flags used when polling. See NetworkStatsService#FLAG_PERSIST_*.
     * @param event The event of polling to be logged.
     */
    public void logPollEvent(int flags, @NonNull PollEvent event) {
        mEventChanges.log("Poll(flags=" + flags + ", " + event + ")");
        mPollEventCounts[event.reason]++;
    }

    /**
     * Print poll counts per reason into the given stream.
     */
    @VisibleForTesting(visibility = PRIVATE)
    public void dumpPollCountsPerReason(@NonNull IndentingPrintWriter pw) {
        pw.println("Poll counts per reason:");
        pw.increaseIndent();
        for (int i = 0; i <= MAX_POLL_REASON; i++) {
            pw.println(PollEvent.pollReasonNameOf(i) + ": " + mPollEventCounts[i]);
        }
        pw.decreaseIndent();
        pw.println();
    }

    /**
     * Print recent poll events into the given stream.
     */
    @VisibleForTesting(visibility = PRIVATE)
    public void dumpRecentPollEvents(@NonNull IndentingPrintWriter pw) {
        pw.println("Recent poll events:");
        pw.increaseIndent();
        mEventChanges.reverseDump(pw);
        pw.decreaseIndent();
        pw.println();
    }

    /**
     * Print the object's state into the given stream.
     */
    public void dump(@NonNull IndentingPrintWriter pw) {
        dumpPollCountsPerReason(pw);
        dumpRecentPollEvents(pw);
    }

    public static class PollEvent {
        public final int reason;

        public PollEvent(@PollReason int reason) {
            if (reason < 0 || reason > MAX_POLL_REASON) {
                throw new IllegalArgumentException("Unsupported poll reason: " + reason);
            }
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "PollEvent{" + "reason=" + pollReasonNameOf(reason) + "}";
        }

        /**
         * Get the name of the given reason.
         *
         * If the reason does not have a String representation, returns its integer representation.
         */
        @NonNull
        public static String pollReasonNameOf(@PollReason int reason) {
            switch (reason) {
                case POLL_REASON_DUMPSYS:                   return "DUMPSYS";
                case POLL_REASON_FORCE_UPDATE:              return "FORCE_UPDATE";
                case POLL_REASON_GLOBAL_ALERT:              return "GLOBAL_ALERT";
                case POLL_REASON_NETWORK_STATUS_CHANGED:    return "NETWORK_STATUS_CHANGED";
                case POLL_REASON_OPEN_SESSION:              return "OPEN_SESSION";
                case POLL_REASON_PERIODIC:                  return "PERIODIC";
                case POLL_REASON_RAT_CHANGED:               return "RAT_CHANGED";
                case POLL_REASON_REMOVE_UIDS:               return "REMOVE_UIDS";
                case POLL_REASON_REG_CALLBACK:              return "REG_CALLBACK";
                case POLL_REASON_UPSTREAM_CHANGED:          return "UPSTREAM_CHANGED";
                default:                                    return Integer.toString(reason);
            }
        }
    }
}
