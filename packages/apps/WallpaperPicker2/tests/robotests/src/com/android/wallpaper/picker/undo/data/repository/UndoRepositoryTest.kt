/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.wallpaper.picker.undo.data.repository

import androidx.test.filters.SmallTest
import com.android.wallpaper.testing.collectLastValue
import com.android.wallpaper.testing.snapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class UndoRepositoryTest {

    private lateinit var underTest: UndoRepository

    @Before
    fun setUp() {
        underTest = UndoRepository()
    }

    @Test
    fun `put and get initial snapshot`() {
        val ownerId1 = 1
        val ownerId2 = 2

        underTest.putSnapshot(ownerId1, snapshot(ownerId1, 1))
        underTest.putSnapshot(ownerId2, snapshot(ownerId2, 1))

        assertThat(underTest.getSnapshot(ownerId1)).isEqualTo(snapshot(ownerId1, 1))
        assertThat(underTest.getSnapshot(ownerId2)).isEqualTo(snapshot(ownerId2, 1))
    }

    @Test
    fun dirty() = runTest {
        val ownerId1 = 1
        val ownerId2 = 2
        val isUndoable = collectLastValue(underTest.isAnythingDirty)

        assertThat(isUndoable()).isFalse()
        assertThat(underTest.getAllDirty()).isEmpty()

        underTest.putDirty(ownerId1, true)
        assertThat(isUndoable()).isTrue()
        assertThat(underTest.getAllDirty()).isEqualTo(setOf(ownerId1))

        underTest.putDirty(ownerId2, true)
        assertThat(isUndoable()).isTrue()
        assertThat(underTest.getAllDirty()).isEqualTo(setOf(ownerId1, ownerId2))

        underTest.putDirty(ownerId1, false)
        assertThat(isUndoable()).isTrue()
        assertThat(underTest.getAllDirty()).isEqualTo(setOf(ownerId2))

        underTest.putDirty(ownerId2, false)
        assertThat(isUndoable()).isFalse()
        assertThat(underTest.getAllDirty()).isEmpty()
    }
}
