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

package android.tools.traces.parsers.wm

import android.tools.parsers.AbstractParser
import android.tools.traces.wm.WindowManagerTrace
import com.android.server.wm.nano.WindowManagerServiceDumpProto

/** Parser for [WindowManagerTrace] objects containing dumps */
class WindowManagerDumpParser :
    AbstractParser<WindowManagerServiceDumpProto, WindowManagerTrace>() {
    override val traceName: String = "WM Dump"

    override fun doDecodeByteArray(bytes: ByteArray): WindowManagerServiceDumpProto =
        WindowManagerServiceDumpProto.parseFrom(bytes)

    override fun doParse(input: WindowManagerServiceDumpProto): WindowManagerTrace {
        val parsedEntry = WindowManagerStateBuilder().forProto(input).build()
        return WindowManagerTrace(listOf(parsedEntry))
    }
}
