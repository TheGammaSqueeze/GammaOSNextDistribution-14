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

package com.android.bedstead.nene.packages;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.Map;
import java.util.Set;

/** Parser for `adb dumpsys package`. */
@TargetApi(Build.VERSION_CODES.O)
interface AdbPackageParser {

    static AdbPackageParser get(int sdkVersion) {
        return new AdbPackageParser26();
    }

    /**
     * The result of parsing.
     *
     * <p>Values which are not used on the current version of Android will be {@code null}.
     */
    class ParseResult {
        Map<String, AdbPackage> mPackages;
        Set<String> mFeatures;
    }

    ParseResult parse(String dumpsysPackageOutput) throws AdbParseException;
}
