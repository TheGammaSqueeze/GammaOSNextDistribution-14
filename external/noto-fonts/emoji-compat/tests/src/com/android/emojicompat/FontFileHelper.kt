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

import com.google.common.base.Charsets

import java.io.IOException
import java.nio.ByteBuffer

const val SFNT_VERSION_1_0 = 0x0001_0000L
const val SFNT_TAG_OTTO = 0x4F_54_54_4FL

const val TAG_head = 0x68_65_61_64L
const val TAG_name = 0x6E_61_6D_65L

object FontFileHelper {

    private fun getTableOffset(buffer: ByteBuffer, tableTag: Long): Int {
        buffer.position(0)
        val sfntVersion = buffer.uint32

        if (sfntVersion != SFNT_VERSION_1_0 && sfntVersion != SFNT_TAG_OTTO) {
            throw IOException("sfntVersion is invalid $sfntVersion")
        }

        val numTables = buffer.uint16
        buffer.uint16  // ignore searchRange
        buffer.uint16  // ignore entrySelector
        buffer.uint16  // ignore rangeShift

        for (i in 0 until numTables) {
            val tag = buffer.uint32
            buffer.uint32  // ignore checkSum
            val offset = buffer.uint32
            buffer.uint32  // ignore length

            if (tag == tableTag) {
                return offset.toInt()
            }
        }

        throw IOException("Table not found: $tableTag")
    }

    /**
     * Get the font version from the head table.
     */
    fun getVersion(buffer: ByteBuffer): Float {
        val headOffset = getTableOffset(buffer, TAG_head)
        buffer.position(headOffset)

        val tableMajor = buffer.uint16
        val tableMinor = buffer.uint16
        if (tableMajor != 1 && tableMinor != 0) {
            throw IOException("head table has wrong major/minor version code. " +
            "expected 1.0 but " + tableMajor + "." + tableMinor);
        }

        return buffer.fixed
    }

    /**
     * Get the PostScript name from the name table.
     */
    fun getPostScriptName(buffer: ByteBuffer): String {
        val nameOffset = getTableOffset(buffer, TAG_name)
        buffer.position(nameOffset)

        buffer.uint16  // skip version number
        val tableCount = buffer.uint16
        val storageOffset = buffer.uint16

        for (i in 0 until tableCount) {
            val platformId = buffer.uint16
            val encodingId = buffer.uint16
            val languageId = buffer.uint16
            val nameId = buffer.uint16
            val length = buffer.uint16
            val stringOffset = buffer.uint16

            if (nameId == 6 && platformId == 3 && encodingId == 1 && languageId == 1033) {
                val name = ByteArray(length)
                buffer.position(nameOffset + storageOffset + stringOffset)
                buffer.get(name)
                return name.toString(Charsets.UTF_16BE)
            }
        }
        throw IOException("Failed to find PostScript name")
    }
}

// Helper functions for accessing buffers with OpenType types.
val ByteBuffer.uint32: Long get() = int.toLong() and 0x00000000FFFFFFFFL
val ByteBuffer.uint16: Int get() = short.toInt() and 0x0000FFFF

val ByteBuffer.fixed: Float get() {
    val integer = uint16
    val decimal = uint16
    return integer.toFloat() + decimal.toFloat() / 0x10000.toFloat()
}

