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

package android.tools.datatypes

import android.tools.withCache

/**
 * Wrapper for ActiveBufferProto (frameworks/native/services/surfaceflinger/layerproto/layers.proto)
 *
 * This class is used by flicker and Winscope
 */
class ActiveBuffer private constructor(width: Int, height: Int, val stride: Int, val format: Int) :
    Size(width, height) {
    override fun doPrintValue() = "w:$width, h:$height, stride:$stride, format:$format"

    companion object {
        val EMPTY: ActiveBuffer
            get() = withCache { ActiveBuffer(width = 0, height = 0, stride = 0, format = 0) }
        fun from(width: Int, height: Int, stride: Int, format: Int): ActiveBuffer = withCache {
            ActiveBuffer(width, height, stride, format)
        }
    }
}
