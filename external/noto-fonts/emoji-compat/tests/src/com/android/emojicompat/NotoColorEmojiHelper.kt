/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.emojicompat

import androidx.test.InstrumentationRegistry

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object NotoColorEmojiHelper {

    private fun getAssetFile(filePath: String) : ByteBuffer {
        val ctx = InstrumentationRegistry.getTargetContext()
        val assetManager = ctx.resources.assets

        val fd = assetManager.openFd(filePath)
        return fd.createInputStream()?.use {
            it.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
        } ?: throw IOException("Failed to mmap $filePath")
    }

    fun getSubsetFont() = getAssetFile("NotoColorEmojiCompat.ttf").apply {
        order(ByteOrder.BIG_ENDIAN)
    }

    fun getFlagFont() = getAssetFile("NotoColorEmojiFlags.ttf").apply {
        order(ByteOrder.BIG_ENDIAN)
    }
}