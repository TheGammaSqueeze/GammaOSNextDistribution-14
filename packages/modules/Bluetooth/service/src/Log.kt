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
package com.android.server.bluetooth

import android.util.Log

private const val SYSTEM_SERVER_TAG = "BluetoothSystemServer"

public class Log private constructor() {
    companion object {

        // Kotlin could shorten below method by having a Throwable? that is default to null but the
        // current implementation of util.Log is behaving differently depending if it is called with
        // 2 or 3 parameters. We do not want to change the behavior in this class, just add a common
        // TAG to all the Bluetooth System Server logs.

        @JvmStatic
        fun v(subtag: String, msg: String) = Log.v(SYSTEM_SERVER_TAG, "${subtag}: ${msg}")

        @JvmStatic
        fun d(subtag: String, msg: String) = Log.d(SYSTEM_SERVER_TAG, "${subtag}: ${msg}")

        @JvmStatic
        fun i(subtag: String, msg: String) = Log.i(SYSTEM_SERVER_TAG, "${subtag}: ${msg}")

        @JvmStatic
        fun w(subtag: String, msg: String) = Log.w(SYSTEM_SERVER_TAG, "${subtag}: ${msg}")

        @JvmStatic
        fun w(subtag: String, msg: String, tr: Throwable) =
            Log.w(SYSTEM_SERVER_TAG, "${subtag}: ${msg}", tr)

        @JvmStatic
        fun e(subtag: String, msg: String) = Log.e(SYSTEM_SERVER_TAG, "${subtag}: ${msg}")

        @JvmStatic
        fun e(subtag: String, msg: String, tr: Throwable) =
            Log.e(SYSTEM_SERVER_TAG, "${subtag}: ${msg}", tr)
    }
}
