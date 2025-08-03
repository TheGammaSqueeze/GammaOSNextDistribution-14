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

package android.platform.test.rule

import android.media.AudioManager
import android.platform.uiautomator_helpers.DeviceHelpers
import androidx.annotation.VisibleForTesting
import org.junit.runner.Description

/** This rule will set the default minimum volume on the device */
class SetDefaultVolumeRule(private val volumeIndex: Int) : TestWatcher() {
    private var mAudioManager =
        DeviceHelpers.context.getSystemService(AudioManager::class.java)
            ?: error("Can't get the AudioManager for ${SetDefaultVolumeRule::class}}")

    override fun starting(description: Description) {
        mAudioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volumeIndex,
            0 // Do nothing, prevent opening UI.
        )
    }

    @VisibleForTesting
    fun setAudioManager(audioManager: AudioManager) {
        mAudioManager = audioManager
    }
}
