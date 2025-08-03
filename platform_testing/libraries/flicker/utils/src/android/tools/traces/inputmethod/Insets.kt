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

package android.tools.traces.inputmethod

/**
 * Represents the InsetsProto in IME traces
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
open class Insets(
    val contentTopInsets: Int,
    val visibleTopInsets: Int,
    val touchableInsets: Int,
    val touchableRegion: Int,
) {
    override fun toString(): String {
        return "${this::class.simpleName}: {contentTopInsets: $contentTopInsets," +
            "visibleTopInsets: $visibleTopInsets, touchableInsets: $touchableInsets," +
            "touchableRegion: $touchableRegion"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Insets) return false

        if (contentTopInsets != other.contentTopInsets) return false
        if (visibleTopInsets != other.visibleTopInsets) return false
        if (touchableInsets != other.touchableInsets) return false
        if (touchableRegion != other.touchableRegion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentTopInsets
        result = 31 * result + visibleTopInsets
        result = 31 * result + touchableInsets
        result = 31 * result + touchableRegion
        return result
    }
}
