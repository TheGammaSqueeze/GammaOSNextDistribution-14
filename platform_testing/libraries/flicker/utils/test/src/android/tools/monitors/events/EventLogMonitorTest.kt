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

package android.tools.monitors.events

import android.tools.io.TraceType
import android.tools.monitors.TraceMonitorTest
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.events.CujEvent
import android.tools.traces.events.CujType
import android.tools.traces.events.EventLog.Companion.MAGIC_NUMBER
import android.tools.traces.events.FocusEvent
import android.tools.traces.io.ResultReader
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.traces.now
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.newTestResultWriter
import android.util.EventLog
import com.android.internal.jank.EventLogTags
import com.google.common.truth.Truth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test

/**
 * Contains [EventLogMonitor] tests. To run this test: {@code atest
 * FlickerLibTest:EventLogMonitorTest}
 */
class EventLogMonitorTest : TraceMonitorTest<EventLogMonitor>() {
    override val traceType = TraceType.EVENT_LOG
    override fun getMonitor(): EventLogMonitor = EventLogMonitor()

    override fun assertTrace(traceData: ByteArray) {
        Truth.assertThat(traceData.size).isAtLeast(MAGIC_NUMBER.toByteArray().size)
        Truth.assertThat(traceData.slice(0 until MAGIC_NUMBER.toByteArray().size))
            .isEqualTo(MAGIC_NUMBER.toByteArray().asList())
    }

    @Test
    fun canCaptureFocusEventLogs() {
        val monitor = EventLogMonitor()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 111 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 222 com.google.android.apps.nexuslauncher/" +
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 333 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        monitor.start()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 4749f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        val writer = newTestResultWriter()
        monitor.stop(writer)
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 2aa30cd com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace()
        requireNotNull(eventLog) { "EventLog was null" }

        assertEquals(2, eventLog.focusEvents.size)
        assertEquals(
            "4749f88 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.first().window
        )
        assertEquals(FocusEvent.Type.LOST, eventLog.focusEvents.first().type)
        assertEquals(
            "7c01447 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.drop(1).first().window
        )
        assertEquals(FocusEvent.Type.GAINED, eventLog.focusEvents.drop(1).first().type)
        assertTrue(
            eventLog.focusEvents.first().timestamp <= eventLog.focusEvents.drop(1).first().timestamp
        )
        assertEquals(eventLog.focusEvents.first().reason, "test")
    }

    @Test
    fun onlyCapturesLastTransition() {
        val monitor = EventLogMonitor()
        monitor.start()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 11111 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 22222 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        monitor.stop(newTestResultWriter())

        monitor.start()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 479f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        val writer = newTestResultWriter()
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace()
        requireNotNull(eventLog) { "EventLog was null" }

        assertEquals(2, eventLog.focusEvents.size)
        assertEquals(
            "479f88 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.first().window
        )
        assertEquals(FocusEvent.Type.LOST, eventLog.focusEvents.first().type)
        assertEquals(
            "7c01447 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.drop(1).first().window
        )
        assertEquals(FocusEvent.Type.GAINED, eventLog.focusEvents.drop(1).first().type)
        assertTrue(
            eventLog.focusEvents.first().timestamp <= eventLog.focusEvents.drop(1).first().timestamp
        )
    }

    @Test
    fun ignoreFocusRequestLogs() {
        val monitor = EventLogMonitor()
        monitor.start()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 4749f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus request 111 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        val writer = newTestResultWriter()
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace()
        requireNotNull(eventLog) { "EventLog was null" }

        assertEquals(2, eventLog.focusEvents.size)
        assertEquals(
            "4749f88 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.first().window
        )
        assertEquals(FocusEvent.Type.LOST, eventLog.focusEvents.first().type)
        assertEquals(
            "7c01447 com.android.phone/com.android.phone.settings.fdn.FdnSetting (server)",
            eventLog.focusEvents.drop(1).first().window
        )
        assertEquals(FocusEvent.Type.GAINED, eventLog.focusEvents.drop(1).first().type)
        assertTrue(
            eventLog.focusEvents.first().timestamp <= eventLog.focusEvents.drop(1).first().timestamp
        )
        assertEquals(eventLog.focusEvents.first().reason, "test")
    }

    @Test
    fun savesEventLogsToFile() {
        val monitor = EventLogMonitor()
        monitor.start()
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 4749f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus request 111 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )
        val writer = newTestResultWriter()
        monitor.stop(writer)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        Truth.assertWithMessage("Trace not found")
            .that(reader.hasTraceFile(TraceType.EVENT_LOG))
            .isTrue()
    }

    @Test
    fun cropsEventsFromBeforeMonitorStart() {
        val monitor = EventLogMonitor()

        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 4749f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )

        monitor.start()

        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )

        val writer = newTestResultWriter()
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace() ?: error("EventLog should have been created")

        Truth.assertThat(eventLog.focusEvents).hasSize(1)
        Truth.assertThat(eventLog.focusEvents.first().type).isEqualTo(FocusEvent.Type.GAINED)
    }

    @Test
    fun cropsEventsOutsideOfTransitionTimes() {
        val monitor = EventLogMonitor()
        val writer = newTestResultWriter()
        monitor.start()

        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus leaving 4749f88 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )

        writer.setTransitionStartTime(now())

        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )

        writer.setTransitionEndTime(now())

        EventLog.writeEvent(
            INPUT_FOCUS_TAG,
            "Focus entering 7c01447 com.android.phone/" +
                "com.android.phone.settings.fdn.FdnSetting (server)",
            "reason=test"
        )

        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace() ?: error("EventLog should have been created")

        Truth.assertThat(eventLog.focusEvents).hasSize(1)
        Truth.assertThat(eventLog.focusEvents.first().hasFocus()).isTrue()
    }

    @Test
    fun canCaptureCujEvents() {
        val monitor = EventLogMonitor()
        val writer = newTestResultWriter()
        monitor.start()
        var now = now()
        EventLogTags.writeJankCujEventsBeginRequest(
            CujType.CUJ_NOTIFICATION_APP_START.ordinal,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos,
            ""
        )
        now = now()
        EventLogTags.writeJankCujEventsEndRequest(
            CujType.CUJ_NOTIFICATION_APP_START.ordinal,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos
        )
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace() ?: error("EventLog should have been created")

        assertEquals(2, eventLog.cujEvents.size)
    }

    @Test
    fun collectsCujEventData() {
        val monitor = EventLogMonitor()
        val writer = newTestResultWriter()
        monitor.start()
        var now = now()
        EventLogTags.writeJankCujEventsBeginRequest(
            CujType.CUJ_LAUNCHER_QUICK_SWITCH.id,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos,
            ""
        )
        now = now()
        EventLogTags.writeJankCujEventsEndRequest(
            CujType.CUJ_LAUNCHER_ALL_APPS_SCROLL.id,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos
        )
        now = now()
        EventLogTags.writeJankCujEventsCancelRequest(
            CujType.CUJ_LOCKSCREEN_LAUNCH_CAMERA.id,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos
        )
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace() ?: error("EventLog should have been created")

        assertEquals(3, eventLog.cujEvents.size)

        Truth.assertThat(eventLog.cujEvents.first().type).isEqualTo(CujEvent.Companion.Type.START)
        Truth.assertThat(eventLog.cujEvents.first().cuj)
            .isEqualTo(CujType.CUJ_LAUNCHER_QUICK_SWITCH)

        Truth.assertThat(eventLog.cujEvents.drop(1).first().type)
            .isEqualTo(CujEvent.Companion.Type.END)
        Truth.assertThat(eventLog.cujEvents.drop(1).first().cuj)
            .isEqualTo(CujType.CUJ_LAUNCHER_ALL_APPS_SCROLL)

        Truth.assertThat(eventLog.cujEvents.drop(2).first().type)
            .isEqualTo(CujEvent.Companion.Type.CANCEL)
        Truth.assertThat(eventLog.cujEvents.drop(2).first().cuj)
            .isEqualTo(CujType.CUJ_LOCKSCREEN_LAUNCH_CAMERA)
    }

    @Test
    fun canParseHandleUnknownCujTypes() {
        val unknownCujId = Int.MAX_VALUE
        val monitor = EventLogMonitor()
        val writer = newTestResultWriter()
        monitor.start()
        var now = now()
        EventLogTags.writeJankCujEventsBeginRequest(
            unknownCujId,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos,
            ""
        )
        now = now()
        EventLogTags.writeJankCujEventsEndRequest(
            unknownCujId,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos
        )
        now = now()
        EventLogTags.writeJankCujEventsCancelRequest(
            unknownCujId,
            now.unixNanos,
            now.elapsedNanos,
            now.systemUptimeNanos
        )
        monitor.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val eventLog = reader.readEventLogTrace()
        requireNotNull(eventLog) { "EventLog should have been created" }

        assertEquals(3, eventLog.cujEvents.size)
        Truth.assertThat(eventLog.cujEvents.first().cuj).isEqualTo(CujType.UNKNOWN)
        Truth.assertThat(eventLog.cujEvents.drop(1).first().cuj).isEqualTo(CujType.UNKNOWN)
        Truth.assertThat(eventLog.cujEvents.drop(2).first().cuj).isEqualTo(CujType.UNKNOWN)
    }

    private companion object {
        const val INPUT_FOCUS_TAG = 62001

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
