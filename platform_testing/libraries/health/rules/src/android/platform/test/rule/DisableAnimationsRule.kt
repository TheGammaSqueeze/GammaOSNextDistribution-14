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

import android.platform.uiautomator_helpers.DeviceHelpers.shell
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DisableAnimationsRule: TestWatcher() {
    private var prevAnimationState: AnimationState = defaultAnimationState
    private var animationState: AnimationState
        get() =
            AnimationState(
                shell("settings get global transition_animation_scale").toFloatOrNull() ?: 1f,
                shell("settings get global window_animation_scale").toFloatOrNull() ?: 1f,
                shell("settings get global animator_duration_scale").toFloatOrNull() ?: 1f,
            )
        set(value) {
            shell("settings put global transition_animation_scale ${value.transitions}")
            shell("settings put global window_animation_scale ${value.windows}")
            shell("settings put global animator_duration_scale ${value.animators}")
        }

    override fun starting(description: Description) {
        super.starting(description)
        prevAnimationState = animationState
        animationState = disabledAnimationState
    }

    override fun finished(description: Description) {
        super.finished(description)
        animationState = prevAnimationState
    }

    data class AnimationState(val transitions: Float, val windows: Float, val animators: Float)

    private companion object {
        val disabledAnimationState = AnimationState(transitions = 0f, windows = 0f, animators = 0f)
        val defaultAnimationState = AnimationState(transitions = 1f, windows = 1f, animators = 1f)
    }
}