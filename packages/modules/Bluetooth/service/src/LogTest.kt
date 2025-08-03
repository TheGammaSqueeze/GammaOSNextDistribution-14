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
package com.android.server.bluetooth.test

import com.android.server.bluetooth.Log
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TAG: String = "LogTest"

@RunWith(RobolectricTestRunner::class)
class LogTest {
    @Test
    fun log_verbose() {
        Log.v(TAG, "Logging verbose")
    }

    @Test
    fun log_debug() {
        Log.d(TAG, "Logging debug")
    }

    @Test
    fun log_info() {
        Log.i(TAG, "Logging info")
    }

    @Test
    fun log_warning() {
        Log.w(TAG, "Logging warning")
    }

    @Test
    fun log_warningThrowable() {
        Log.w(TAG, "Logging warning", RuntimeException("With a Throwable"))
    }

    @Test
    fun log_error() {
        Log.e(TAG, "Logging error")
    }

    @Test
    fun log_errorThrowable() {
        Log.e(TAG, "Logging error… ", RuntimeException("With a Throwable"))
    }
}
