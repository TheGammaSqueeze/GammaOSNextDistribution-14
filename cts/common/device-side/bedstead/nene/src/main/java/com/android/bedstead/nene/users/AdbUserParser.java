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

package com.android.bedstead.nene.users;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.Map;

/**
 * Parser for the output of "adb dumpsys user".
 */
@TargetApi(Build.VERSION_CODES.O)
interface AdbUserParser {

    @SuppressLint("NewApi")
    static AdbUserParser get(int sdkVersion) {
        if (sdkVersion >= 31) {
            return new AdbUserParser31();
        }
        if (sdkVersion >= 30) {
            return new AdbUserParser30();
        }
        return new AdbUserParser26();
    }

    /**
     * The result of parsing.
     *
     * <p>Values which are not used on the current version of Android will be {@code null}.
     */
    class ParseResult {
        Map<Integer, AdbUser> mUsers;
        @Nullable Map<String, UserType> mUserTypes;
    }

    ParseResult parse(String dumpsysUsersOutput) throws AdbParseException;
}