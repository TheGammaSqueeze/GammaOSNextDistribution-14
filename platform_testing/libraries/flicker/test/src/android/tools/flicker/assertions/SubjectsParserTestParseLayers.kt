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

package android.tools.flicker.assertions

import android.annotation.SuppressLint
import android.tools.Timestamp
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.FlickerTraceSubject
import android.tools.io.TraceType
import android.tools.utils.TestTraces

@SuppressLint("VisibleForTests")
class SubjectsParserTestParseLayers : BaseSubjectsParserTestParse() {
    override val assetFile = TestTraces.LayerTrace.FILE
    override val expectedStartTime = TestTraces.LayerTrace.START_TIME
    override val expectedEndTime = TestTraces.LayerTrace.END_TIME
    override val subjectName = "SF Trace"
    override val traceType = TraceType.SF

    override fun getTime(timestamp: Timestamp) = timestamp.systemUptimeNanos

    override fun doParseTrace(parser: TestSubjectsParser): FlickerTraceSubject<*>? =
        parser.doGetLayersTraceSubject()

    override fun doParseState(parser: TestSubjectsParser, tag: String): FlickerSubject? =
        parser.doGetLayerTraceEntrySubject(tag)
}
