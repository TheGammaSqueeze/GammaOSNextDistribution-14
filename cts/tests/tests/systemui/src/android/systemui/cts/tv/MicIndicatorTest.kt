/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.cts.tv

import android.content.ComponentName
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.Postsubmit
import android.server.wm.annotation.Group2
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests microphone indicator.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:MicIndicatorTest
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
@AppModeFull
class MicIndicatorTest : TvTestBase() {
    companion object {
        private val AUDIO_RECORD_API_SERVICE = ComponentName.createRelative(
                "android.systemui.cts.audiorecorder.audiorecord", ".AudioRecorderService")
        private val MEDIA_RECORDER_API_SERVICE = ComponentName.createRelative(
                "android.systemui.cts.audiorecorder.mediarecorder", ".AudioRecorderService")

        private const val ACTION_THROW = "android.systemui.cts.audiorecorder.ACTION_THROW"
        private const val ACTION_STOP = "android.systemui.cts.audiorecorder.ACTION_STOP"
        private const val ACTION_START = "android.systemui.cts.audiorecorder.ACTION_START"

        private const val MIC_INDICATOR_WINDOW_TITLE = "MicrophoneCaptureIndicator"
    }

    override fun onSetUp() {
        assertIndicatorWindowGone()
    }

    override fun onTearDown() {
        stopPackage(AUDIO_RECORD_API_SERVICE.packageName)
        stopPackage(MEDIA_RECORDER_API_SERVICE.packageName)
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_AudioRecordApi() {
        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_STOP)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_MediaRecorderApi() {
        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_STOP)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_AudioRecordApi_until_forceStopped() {
        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        stopPackage(AUDIO_RECORD_API_SERVICE.packageName)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_MediaRecorderApi_until_forceStopped() {
        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        stopPackage(MEDIA_RECORDER_API_SERVICE.packageName)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_AudioRecordApi_until_crashed() {
        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_THROW)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsing_MediaRecorderApi_until_crashed() {
        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_THROW)
        assertIndicatorWindowGone()
    }

    @Test
    fun micIndicator_shown_whileRecordingUsingBothApisSimultaneously() {
        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_START)
        assertIndicatorWindowVisible()

        startForegroundService(AUDIO_RECORD_API_SERVICE, ACTION_STOP)
        // The indicator should stay on, since the MR is still running.
        assertIndicatorWindowVisible()

        // Give it 7s, and make sure indicator is still there.
        Thread.sleep(7_000)
        assertIndicatorWindowVisible()

        startForegroundService(MEDIA_RECORDER_API_SERVICE, ACTION_STOP)
        // Now it should go.
        assertIndicatorWindowGone()
    }

    private fun assertIndicatorWindowVisible() {
        wmState.waitFor("Waiting for the mic indicator window to come up") {
            it.containsWindow(MIC_INDICATOR_WINDOW_TITLE) &&
                    it.isWindowVisible(MIC_INDICATOR_WINDOW_TITLE)
        } || error("Mic indicator window is not visible.")
    }

    private fun assertIndicatorWindowGone() {
        // The indicator stays visible for 5s after the access is stopped.
        Thread.sleep(6_000)
        wmState.waitFor("Waiting for the mic indicator window to disappear") {
            !it.containsWindow(MIC_INDICATOR_WINDOW_TITLE)
        } || error("Mic indicator window is present (should be gone).")
    }
}