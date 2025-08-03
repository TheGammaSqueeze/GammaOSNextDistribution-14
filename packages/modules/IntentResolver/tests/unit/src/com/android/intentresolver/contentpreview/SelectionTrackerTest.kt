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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SelectionTrackerTest {
    @Test
    fun noSelectedItems() {
        val testSubject = SelectionTracker<Uri>(emptyList(), 0, 10) { this }

        val items =
            (1..5).fold(SparseArray<Uri>(5)) { acc, i ->
                acc.apply { append(i * 2, makeUri(i * 2)) }
            }
        testSubject.onEndItemsAdded(items)

        assertThat(testSubject.getSelection()).isEmpty()
    }

    @Test
    fun testNoItems() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val testSubject = SelectionTracker(listOf(u1, u2, u3), 1, 0) { this }

        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()
    }

    @Test
    fun focusedItemInPlaceAllItemsOnTheRight_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val count = 7
        val testSubject = SelectionTracker(listOf(u1, u2, u3), 0, count) { this }

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(1, u1)
                append(2, makeUri(4))
                append(3, makeUri(5))
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()
        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(3, makeUri(6))
                append(4, u2)
                append(5, u3)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()
    }

    @Test
    fun focusedItemInPlaceElementsOnBothSides_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val count = 10
        val testSubject = SelectionTracker(listOf(u1, u2, u3), 1, count) { this }

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(4, u2)
                append(5, makeUri(4))
                append(6, makeUri(5))
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(1, makeUri(6))
                append(2, u1)
                append(3, makeUri(7))
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()

        testSubject.onEndItemsAdded(SparseArray<Uri>(3).apply { append(8, u3) })
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()
    }

    @Test
    fun focusedItemInPlaceAllItemsOnTheLeft_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val count = 7
        val testSubject = SelectionTracker(listOf(u1, u2, u3), 2, count) { this }

        testSubject.onEndItemsAdded(SparseArray<Uri>(3).apply { append(6, u3) })

        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(3, makeUri(4))
                append(4, u2)
                append(5, makeUri(5))
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(1, u1)
                append(2, makeUri(6))
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3).inOrder()
    }

    @Test
    fun focusedItemInPlaceDuplicatesOnBothSides_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val count = 5
        val testSubject = SelectionTracker(listOf(u1, u2, u1), 1, count) { this }

        testSubject.onEndItemsAdded(SparseArray<Uri>(3).apply { append(2, u2) })
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u1).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(0, u1)
                append(1, u3)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u1).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(3, u1)
                append(4, u3)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u1).inOrder()
    }

    @Test
    fun focusedItemInPlaceDuplicatesOnTheRight_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val count = 4
        val testSubject = SelectionTracker(listOf(u1, u2), 0, count) { this }

        testSubject.onEndItemsAdded(SparseArray<Uri>(1).apply { append(0, u1) })
        assertThat(testSubject.getSelection()).containsExactly(u1, u2).inOrder()

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(1, u2)
                append(2, u1)
                append(3, u2)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2).inOrder()
    }

    @Test
    fun focusedItemInPlaceDuplicatesOnTheLeft_selectionsInTheInitialOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val count = 4
        val testSubject = SelectionTracker(listOf(u1, u2), 1, count) { this }

        testSubject.onEndItemsAdded(SparseArray<Uri>(1).apply { append(3, u2) })
        assertThat(testSubject.getSelection()).containsExactly(u1, u2).inOrder()

        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(0, u1)
                append(1, u2)
                append(2, u1)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2).inOrder()
    }

    @Test
    fun differentItemsOrder_selectionsInTheCursorOrder() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val u4 = makeUri(3)
        val count = 10
        val testSubject = SelectionTracker(listOf(u1, u2, u3, u4), 2, count) { this }

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(4, makeUri(5))
                append(5, u1)
                append(6, makeUri(6))
            }
        )
        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(2, makeUri(7))
                append(3, u4)
            }
        )
        testSubject.onEndItemsAdded(
            SparseArray<Uri>(3).apply {
                append(7, u3)
                append(8, makeUri(8))
            }
        )
        testSubject.onStartItemsAdded(
            SparseArray<Uri>(3).apply {
                append(0, makeUri(9))
                append(1, u2)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u2, u4, u1, u3).inOrder()
    }

    @Test
    fun testPendingItems() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val u4 = makeUri(4)
        val u5 = makeUri(5)

        val testSubject = SelectionTracker(listOf(u1, u2, u3, u4, u5), 2, 5) { this }

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(2).apply {
                append(2, u3)
                append(3, u4)
            }
        )
        testSubject.onStartItemsAdded(SparseArray<Uri>(2).apply { append(1, u2) })

        assertThat(testSubject.getPendingItems()).containsExactly(u1, u5).inOrder()
    }

    @Test
    fun testItemSelection() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)
        val u3 = makeUri(3)
        val u4 = makeUri(4)
        val u5 = makeUri(5)

        val testSubject = SelectionTracker(listOf(u1, u2, u3, u4, u5), 2, 10) { this }

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(2).apply {
                append(2, u3)
                append(3, u4)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3, u4, u5).inOrder()

        assertThat(testSubject.setItemSelection(2, u3, false)).isTrue()
        assertThat(testSubject.setItemSelection(3, u4, true)).isFalse()
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u4, u5).inOrder()

        testSubject.onEndItemsAdded(
            SparseArray<Uri>(1).apply {
                append(4, u5)
                append(5, u3)
            }
        )
        testSubject.onStartItemsAdded(
            SparseArray<Uri>(2).apply {
                append(0, u1)
                append(1, u2)
            }
        )
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u4, u5).inOrder()

        assertThat(testSubject.setItemSelection(2, u3, true)).isTrue()
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3, u4, u5).inOrder()
        assertThat(testSubject.setItemSelection(5, u3, true)).isTrue()
        assertThat(testSubject.getSelection()).containsExactly(u1, u2, u3, u4, u5, u3).inOrder()
    }

    @Test
    fun testItemSelectionWithDuplicates() {
        val u1 = makeUri(1)
        val u2 = makeUri(2)

        val testSubject = SelectionTracker(listOf(u1, u2, u1), 1, 3) { this }
        testSubject.onEndItemsAdded(
            SparseArray<Uri>(2).apply {
                append(1, u2)
                append(2, u1)
            }
        )

        assertThat(testSubject.getPendingItems()).containsExactly(u1)
    }

    @Test
    fun testUnselectOnlySelectedItem_itemRemainsSelected() {
        val u1 = makeUri(1)

        val testSubject = SelectionTracker(listOf(u1), 0, 1) { this }
        testSubject.onEndItemsAdded(SparseArray<Uri>(1).apply { append(0, u1) })
        assertThat(testSubject.isItemSelected(0)).isTrue()
        assertThat(testSubject.setItemSelection(0, u1, false)).isFalse()
        assertThat(testSubject.isItemSelected(0)).isTrue()
    }
}

private fun makeUri(id: Int) = Uri.parse("content://org.pkg.app/img-$id.png")
