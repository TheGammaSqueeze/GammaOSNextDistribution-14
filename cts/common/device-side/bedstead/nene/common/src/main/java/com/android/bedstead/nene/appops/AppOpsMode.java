/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.appops;

/** Valid modes for an AppOp. */
public enum AppOpsMode {
    ALLOWED(/* MODE_ALLOWED */ 0),
    IGNORED(/* MODE_IGNORED */ 1),
    ERRORED(/* MODE_ERRORED */ 2),
    DEFAULT(/* MODE_DEFAULT */ 3),
    FOREGROUND(/* MODE_FOREGROUND */ 4);

    // Values from AppOpsManager
    private static final int MODE_ALLOWED = 0;
    private static final int MODE_IGNORED = 1;
    private static final int MODE_ERRORED = 2;
    private static final int MODE_DEFAULT = 3;
    private static final int MODE_FOREGROUND = 4;

    final int mValue;

    /** The {@code AppOpsManager} equivalent value. */
    public int value() {
        return mValue;
    }

    AppOpsMode(int value) {
        this.mValue = value;
    }

    static AppOpsMode forValue(int value) {
        switch (value) {
            case MODE_ALLOWED:
                return ALLOWED;
            case MODE_IGNORED:
                return IGNORED;
            case MODE_ERRORED:
                return ERRORED;
            case MODE_DEFAULT:
                return DEFAULT;
            case MODE_FOREGROUND:
                return FOREGROUND;
            default:
                throw new IllegalStateException("Unknown AppOpsMode");
        }
    }
}
