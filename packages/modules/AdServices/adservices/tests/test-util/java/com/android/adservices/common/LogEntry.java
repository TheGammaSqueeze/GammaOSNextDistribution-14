/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.common;

import androidx.annotation.Nullable;

import java.util.Objects;

// TODO(b/306522832): move to shared project (device-side)
/** Represents a call to a static method on {@code android.util.Log}. */
public final class LogEntry {
    public final LogEntry.Level level;
    public final String tag;
    public final String message;
    public final @Nullable Throwable exception;

    public LogEntry(LogEntry.Level level, String tag, String message) {
        this(level, tag, message, /* exception= */ null, /* checkException= */ false);
    }

    public LogEntry(LogEntry.Level level, String tag, String message, Throwable exception) {
        this(level, tag, message, /* exception= */ exception, /* checkException= */ true);
    }

    private LogEntry(
            LogEntry.Level level,
            String tag,
            String message,
            Throwable exception,
            boolean checkException) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.tag = Objects.requireNonNull(tag, "tag cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.exception =
                checkException
                        ? Objects.requireNonNull(exception, "exception cannot be null")
                        : exception;
    }

    @Override
    public String toString() {
        StringBuilder string =
                new StringBuilder(level.toString()).append("(").append(tag).append(": ");

        if (exception == null) {
            string.append(message);
        } else {
            string.append("msg=").append(message).append(", e=").append(exception);
        }
        return string.append(")").toString();
    }

    /** Represents a {@code android.util.Log} level. */
    public enum Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        ASSERT;
    }
}
