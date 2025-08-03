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

import android.content.ContentInterface
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.util.SparseArray
import com.android.intentresolver.any
import com.android.intentresolver.anyOrNull
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CursorUriReaderTest {
    private val scope = TestScope()

    @Test
    fun readEmptyCursor() {
        val testSubject =
            CursorUriReader(
                cursor = MatrixCursor(arrayOf("uri")),
                startPos = 0,
                pageSize = 128,
            ) {
                true
            }

        assertThat(testSubject.hasMoreBefore).isFalse()
        assertThat(testSubject.hasMoreAfter).isFalse()
        assertThat(testSubject.count).isEqualTo(0)
        assertThat(testSubject.readPageBefore().size()).isEqualTo(0)
        assertThat(testSubject.readPageAfter().size()).isEqualTo(0)
    }

    @Test
    fun readCursorFromTheMiddle() {
        val count = 3
        val testSubject =
            CursorUriReader(
                cursor =
                    MatrixCursor(arrayOf("uri")).apply {
                        for (i in 1..count) {
                            addRow(arrayOf(createUri(i)))
                        }
                    },
                startPos = 1,
                pageSize = 2,
            ) {
                true
            }

        assertThat(testSubject.hasMoreBefore).isTrue()
        assertThat(testSubject.hasMoreAfter).isTrue()
        assertThat(testSubject.count).isEqualTo(3)

        testSubject.readPageBefore().let { page ->
            assertThat(testSubject.hasMoreBefore).isFalse()
            assertThat(testSubject.hasMoreAfter).isTrue()
            assertThat(page.size()).isEqualTo(1)
            assertThat(page.keyAt(0)).isEqualTo(0)
            assertThat(page.valueAt(0)).isEqualTo(createUri(1))
        }

        testSubject.readPageAfter().let { page ->
            assertThat(testSubject.hasMoreBefore).isFalse()
            assertThat(testSubject.hasMoreAfter).isFalse()
            assertThat(page.size()).isEqualTo(2)
            assertThat(page.getKeys()).asList().containsExactly(1, 2).inOrder()
            assertThat(page.getValues())
                .asList()
                .containsExactly(createUri(2), createUri(3))
                .inOrder()
        }
    }

    // TODO: add tests with filtered-out items
    // TODO: add tests with a failing cursor

    @Test
    fun testFailingQueryCall_emptyCursorCreated() =
        scope.runTest {
            val contentResolver =
                mock<ContentInterface> {
                    whenever(query(any(), any(), anyOrNull(), any()))
                        .thenThrow(SecurityException("Test exception"))
                }
            val cursorReader =
                CursorUriReader.createCursorReader(
                    contentResolver,
                    Uri.parse("content://auth"),
                    Intent(Intent.ACTION_CHOOSER)
                )

            assertWithMessage("Empty cursor reader is expected")
                .that(cursorReader.count)
                .isEqualTo(0)
        }
}

private fun createUri(id: Int) = Uri.parse("content://org.pkg/$id")

private fun <T> SparseArray<T>.getKeys(): IntArray = IntArray(size()) { i -> keyAt(i) }

private inline fun <reified T> SparseArray<T>.getValues(): Array<T> =
    Array(size()) { i -> valueAt(i) }
