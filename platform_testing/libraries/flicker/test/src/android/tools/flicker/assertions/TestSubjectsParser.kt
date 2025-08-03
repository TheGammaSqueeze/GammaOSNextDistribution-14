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

import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.traces.io.ResultReader

/** Wrapper for [SubjectsParser] with looser visibility */
class TestSubjectsParser(resultReader: ResultReader) : SubjectsParser(resultReader) {
    public override fun doGetEventLogSubject(): EventLogSubject? = super.doGetEventLogSubject()

    public override fun doGetWmTraceSubject(): WindowManagerTraceSubject? =
        super.doGetWmTraceSubject()

    public override fun doGetLayersTraceSubject(): LayersTraceSubject? =
        super.doGetLayersTraceSubject()

    public override fun doGetLayerTraceEntrySubject(tag: String): LayerTraceEntrySubject? =
        super.doGetLayerTraceEntrySubject(tag)

    public override fun doGetWmStateSubject(tag: String): WindowManagerStateSubject? =
        super.doGetWmStateSubject(tag)
}
