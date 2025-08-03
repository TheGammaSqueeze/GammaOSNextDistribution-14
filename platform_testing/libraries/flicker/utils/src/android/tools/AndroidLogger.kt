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

package android.tools

import android.os.Trace
import android.util.Log

class AndroidLogger : ILogger {
    override fun v(tag: String, msg: String, error: Throwable?) {
        Log.v(tag, msg, error)
    }

    override fun d(tag: String, msg: String, error: Throwable?) {
        Log.d(tag, msg, error)
    }

    override fun i(tag: String, msg: String, error: Throwable?) {
        Log.i(tag, msg, error)
    }

    override fun w(tag: String, msg: String, error: Throwable?) {
        Log.w(tag, msg, error)
    }

    override fun e(tag: String, msg: String, error: Throwable?) {
        Log.e(tag, msg, error)
    }

    override fun <T> withTracing(name: String, predicate: () -> T): T =
        try {
            Trace.beginSection(name)
            predicate()
        } finally {
            Trace.endSection()
        }
}
