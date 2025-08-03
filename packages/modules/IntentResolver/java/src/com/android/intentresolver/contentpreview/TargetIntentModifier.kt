/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.content.ClipData
import android.content.ClipDescription.compareMimeTypes
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_STREAM
import android.net.Uri

/** Modifies target intent based on current payload selection. */
class TargetIntentModifier<Item>(
    private val originalTargetIntent: Intent,
    private val getUri: Item.() -> Uri,
    private val getMimeType: Item.() -> String?,
) : (List<Item>) -> Intent {
    fun onSelectionChanged(selection: List<Item>): Intent {
        val uris = ArrayList<Uri>(selection.size)
        var targetMimeType: String? = null
        for (item in selection) {
            targetMimeType = updateMimeType(item.getMimeType(), targetMimeType)
            uris.add(item.getUri())
        }
        val action = if (uris.size == 1) ACTION_SEND else ACTION_SEND_MULTIPLE
        return Intent(originalTargetIntent).apply {
            this.action = action
            this.type = targetMimeType
            if (action == ACTION_SEND) {
                putExtra(EXTRA_STREAM, uris[0])
            } else {
                putParcelableArrayListExtra(EXTRA_STREAM, uris)
            }
            if (uris.isNotEmpty()) {
                clipData =
                    ClipData("", arrayOf(targetMimeType), ClipData.Item(uris[0])).also {
                        for (i in 1 until uris.size) {
                            it.addItem(ClipData.Item(uris[i]))
                        }
                    }
            }
        }
    }

    private fun updateMimeType(itemMimeType: String?, unitedMimeType: String?): String {
        itemMimeType ?: return "*/*"
        unitedMimeType ?: return itemMimeType
        if (compareMimeTypes(itemMimeType, unitedMimeType)) return unitedMimeType
        val slashIdx = unitedMimeType.indexOf('/')
        if (slashIdx >= 0 && unitedMimeType.regionMatches(0, itemMimeType, 0, slashIdx + 1)) {
            return buildString {
                append(unitedMimeType.substring(0, slashIdx + 1))
                append('*')
            }
        }
        return "*/*"
    }

    override fun invoke(selection: List<Item>): Intent = onSelectionChanged(selection)
}
