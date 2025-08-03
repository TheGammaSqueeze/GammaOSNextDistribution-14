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
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mockito

/** Unit test the logic for SetDefaultMinVolumeRule */
class SetDefaultVolumeRuleTest {
    private val description = Description.createTestDescription("class", "method")

    @Test
    fun testSetDefaultMinVolumeRule() {
        val mockAudioManager = Mockito.mock(AudioManager::class.java)
        val rule =
            SetDefaultVolumeRule(mockAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC))
        rule.setAudioManager(mockAudioManager)
        rule.apply(statement, description).evaluate()

        // Validate if the setStreamVolume is called to set the volume
        Mockito.verify(mockAudioManager, Mockito.times(1))
            .setStreamVolume(
                AudioManager.STREAM_MUSIC,
                mockAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                0
            )
    }

    @Test
    fun testSetDefaultMaxVolumeRule() {
        val mockAudioManager = Mockito.mock(AudioManager::class.java)
        val rule =
            SetDefaultVolumeRule(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        rule.setAudioManager(mockAudioManager)
        rule.apply(statement, description).evaluate()

        // Validate if the setStreamVolume is called to set the volume
        Mockito.verify(mockAudioManager, Mockito.times(1))
            .setStreamVolume(
                AudioManager.STREAM_MUSIC,
                mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )
    }

    private val statement: Statement =
        object : Statement() {
            override fun evaluate() {}
        }
}
