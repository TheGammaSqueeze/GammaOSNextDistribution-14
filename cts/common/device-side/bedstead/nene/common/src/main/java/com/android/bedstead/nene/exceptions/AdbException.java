/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.exceptions;

import com.android.bedstead.nene.annotations.Nullable;

/**
 * An exception that gets thrown when interacting with Adb.
 */
public class AdbException extends Exception {

    private final String mCommand;
    private final @Nullable String mOutput;
    private final @Nullable String mErr;

    public AdbException(String message, String command, String output) {
        this(message, command, output, /* err= */ (String) null);
    }

    public AdbException(String message, String command, String output, String err) {
        super(message);
        if (command == null) {
            throw new NullPointerException();
        }
        this.mCommand = command;
        this.mOutput = output;
        this.mErr = err;
    }

    public AdbException(String message, String command, Throwable cause) {
        this(message, command, /* output= */ null, cause);
    }

    public AdbException(String message, String command, String output, Throwable cause) {
        super(message, cause);
        if (command == null) {
            throw new NullPointerException();
        }
        this.mCommand = command;
        this.mOutput = output;
        this.mErr = null;
    }

    public String command() {
        return mCommand;
    }

    public String output() {
        return mOutput;
    }

    public String error() {
        return mErr;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(super.toString());

        stringBuilder.append("[command=\"").append(mCommand).append("\"");
        if (mOutput != null) {
            stringBuilder.append(", output=\"").append(mOutput).append("\"");
        }
        if (mErr != null) {
            stringBuilder.append(", err=\"").append(mErr).append("\"");
        }
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
