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

import android.net.Uri
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.core.util.containsKey
import androidx.core.util.isNotEmpty

/**
 * Tracks selected items (including those that has not been read frm the cursor) and their relative
 * order.
 */
class SelectionTracker<Item>(
    selectedItems: List<Item>,
    private val focusedItemIdx: Int,
    private val cursorCount: Int,
    private val getUri: Item.() -> Uri,
) {
    /** Contains selected items keys. */
    private val selections = SparseArray<Item>(selectedItems.size)

    /**
     * A set of initially selected items that has not yet been observed by the lazy read of the
     * cursor and thus has unknown key (cursor position). Initially, all [selectedItems] are put in
     * this map with items at the index less than [focusedItemIdx] with negative keys (to the left
     * of all cursor items) and items at the index more or equal to [focusedItemIdx] with keys more
     * or equal to [cursorCount] (to the right of all cursor items) in their relative order. Upon
     * reading the cursor, [onEndItemsAdded]/[onStartItemsAdded], all pending items from that
     * collection in the corresponding direction get their key assigned and gets removed from the
     * map. Items that were missing from the cursor get removed from the map by
     * [getPendingItems] + [onStartItemsAdded]/[onEndItemsAdded] combination.
     */
    private val pendingKeys = HashMap<Uri, SparseIntArray>()

    init {
        selectedItems.forEachIndexed { i, item ->
            // all items before focusedItemIdx gets "positioned" before all the cursor items
            // and all the reset after all the cursor items in their relative order.
            // Also see the comments to pendingKeys property.
            val key =
                if (i < focusedItemIdx) {
                    i - focusedItemIdx
                } else {
                    i + cursorCount - focusedItemIdx
                }
            selections.append(key, item)
            pendingKeys.getOrPut(item.getUri()) { SparseIntArray(1) }.append(key, key)
        }
    }

    /** Update selections based on the set of items read from the end of the cursor */
    fun onEndItemsAdded(items: SparseArray<Item>) {
        for (i in 0 until items.size()) {
            val item = items.valueAt(i)
            pendingKeys[item.getUri()]
                // if only one pending (unmatched) item with this URI is left, removed this URI
                ?.also {
                    if (it.size() <= 1) {
                        pendingKeys.remove(item.getUri())
                    }
                }
                // a safeguard, we should not observe empty arrays at this point
                ?.takeIf { it.isNotEmpty() }
                // pick a matching pending items from the right side
                ?.let { pendingUriPositions ->
                    val key = items.keyAt(i)
                    val insertPos =
                        pendingUriPositions
                            .findBestKeyPosition(key)
                            .coerceIn(0, pendingUriPositions.size() - 1)
                    // select next pending item from the right, if not such item exists then
                    // the data is inconsistent and we pick the closes one from the left
                    val keyPlaceholder = pendingUriPositions.keyAt(insertPos)
                    pendingUriPositions.removeAt(insertPos)
                    selections.remove(keyPlaceholder)
                    selections[key] = item
                }
        }
    }

    /** Update selections based on the set of items read from the head of the cursor */
    fun onStartItemsAdded(items: SparseArray<Item>) {
        for (i in (items.size() - 1) downTo 0) {
            val item = items.valueAt(i)
            pendingKeys[item.getUri()]
                // if only one pending (unmatched) item with this URI is left, removed this URI
                ?.also {
                    if (it.size() <= 1) {
                        pendingKeys.remove(item.getUri())
                    }
                }
                // a safeguard, we should not observe empty arrays at this point
                ?.takeIf { it.isNotEmpty() }
                // pick a matching pending items from the left side
                ?.let { pendingUriPositions ->
                    val key = items.keyAt(i)
                    val insertPos =
                        pendingUriPositions
                            .findBestKeyPosition(key)
                            .coerceIn(1, pendingUriPositions.size())
                    // select next pending item from the left, if not such item exists then
                    // the data is inconsistent and we pick the closes one from the right
                    val keyPlaceholder = pendingUriPositions.keyAt(insertPos - 1)
                    pendingUriPositions.removeAt(insertPos - 1)
                    selections.remove(keyPlaceholder)
                    selections[key] = item
                }
        }
    }

    /** Updated selection status for the given item */
    fun setItemSelection(key: Int, item: Item, isSelected: Boolean): Boolean {
        val idx = selections.indexOfKey(key)
        if (isSelected && idx < 0) {
            selections[key] = item
            return true
        }
        if (!isSelected && idx >= 0 && selections.size() > 1) {
            selections.removeAt(idx)
            return true
        }
        return false
    }

    /** Return selection status for the given item */
    fun isItemSelected(key: Int): Boolean = selections.containsKey(key)

    fun getSelection(): List<Item> =
        buildList(selections.size()) {
            for (i in 0 until selections.size()) {
                add(selections.valueAt(i))
            }
        }

    /** Return all selected items that has not yet been read from the cursor */
    fun getPendingItems(): List<Item> =
        if (pendingKeys.isEmpty()) {
            emptyList()
        } else {
            buildList {
                for (i in 0 until selections.size()) {
                    val item = selections.valueAt(i) ?: continue
                    if (isPending(item, selections.keyAt(i))) {
                        add(item)
                    }
                }
            }
        }

    private fun isPending(item: Item, key: Int): Boolean {
        val keys = pendingKeys[item.getUri()] ?: return false
        return keys.containsKey(key)
    }

    private fun SparseIntArray.findBestKeyPosition(key: Int): Int =
        // undocumented, but indexOfKey behaves in the same was as
        // java.util.Collections#binarySearch()
        indexOfKey(key).let { if (it < 0) it.inv() else it }
}
