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

package android.tools.traces.wm

import android.tools.withCache

/**
 * Represents the keyguard controller in the window manager hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class KeyguardControllerState
private constructor(
    val isAodShowing: Boolean,
    val isKeyguardShowing: Boolean,
    val keyguardOccludedStates: Map<Int, Boolean>
) {
    fun isKeyguardOccluded(displayId: Int): Boolean = keyguardOccludedStates[displayId] ?: false

    override fun toString(): String {
        return "KeyguardControllerState: {aod=$isAodShowing keyguard=$isKeyguardShowing}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyguardControllerState) return false

        if (isAodShowing != other.isAodShowing) return false
        if (isKeyguardShowing != other.isKeyguardShowing) return false
        if (keyguardOccludedStates != other.keyguardOccludedStates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isAodShowing.hashCode()
        result = 31 * result + isKeyguardShowing.hashCode()
        result = 31 * result + keyguardOccludedStates.hashCode()
        return result
    }

    companion object {
        fun from(
            isAodShowing: Boolean,
            isKeyguardShowing: Boolean,
            keyguardOccludedStates: Map<Int, Boolean>
        ): KeyguardControllerState = withCache {
            KeyguardControllerState(isAodShowing, isKeyguardShowing, keyguardOccludedStates)
        }
    }
}
