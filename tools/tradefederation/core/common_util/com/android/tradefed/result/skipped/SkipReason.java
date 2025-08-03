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
package com.android.tradefed.result.skipped;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Provide a reason and its metadata for skipping a test. */
public class SkipReason {

    private final String reason;
    private final String trigger;
    private final String bugId;

    // Limit the possibilities for reported trigger type, but store as a string.
    public static enum DemotionTrigger {
        UNKNOWN_TRIGGER, // Unspecified trigger
        MANUAL, // Inserted directly via API
        LATENCY, // Test is out of SLO on latency
        ERROR_RATE, // Test is out of SLO on error rate
        FLAKINESS; // Test is out of SLO on flakiness score
    }

    public SkipReason(String message, String trigger) {
        this(message, trigger, "");
    }

    public SkipReason(String message, DemotionTrigger trigger) {
        this(message, trigger, "");
    }

    public SkipReason(String message, DemotionTrigger trigger, String bugId) {
        this(
                message,
                trigger == null ? DemotionTrigger.UNKNOWN_TRIGGER.name() : trigger.name(),
                bugId);
    }

    public SkipReason(String message, String trigger, String bugId) {
        this.reason = message;
        this.trigger = trigger;
        this.bugId = bugId;
    }

    /** Returns the reason associated with the skip status. */
    public String getReason() {
        return reason;
    }

    /** Returns the trigger associated with the skip status. */
    public String getTrigger() {
        return trigger;
    }

    /** Returns the bug id associated with skip status. Optional. */
    public String getBugId() {
        return bugId;
    }

    @Override
    public String toString() {
        return "SkipReason[reason=" + reason + ", trigger=" + trigger + ", bugId=" + bugId + "]";
    }

    /** Parses {@link #toString()} into a {@link SkipReason}. */
    public static SkipReason fromString(String skipReasonMessage) {
        Pattern p = Pattern.compile("SkipReason\\[reason=(.*), trigger=(.*), bugId=(.*)\\]");
        Matcher m = p.matcher(skipReasonMessage);
        if (m.find()) {
            String reason = m.group(1);
            String trigger = m.group(2);
            String bugId = m.group(3);
            return new SkipReason(reason, DemotionTrigger.valueOf(trigger), bugId);
        }
        throw new RuntimeException(
                String.format("Cannot parse '%s' as SkipReason.", skipReasonMessage));
    }
}
