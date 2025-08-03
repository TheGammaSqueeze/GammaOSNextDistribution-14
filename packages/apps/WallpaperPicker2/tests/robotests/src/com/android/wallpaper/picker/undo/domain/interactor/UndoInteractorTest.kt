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

package com.android.wallpaper.picker.undo.domain.interactor

import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.testing.FAKE_RESTORERS
import com.android.wallpaper.testing.collectLastValue
import com.android.wallpaper.testing.snapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class UndoInteractorTest {

    private lateinit var underTest: UndoInteractor

    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        underTest =
            UndoInteractor(
                scope = testScope.backgroundScope,
                repository = UndoRepository(),
                restorerByOwnerId = FAKE_RESTORERS,
            )
    }

    @Test
    fun `start session - update - and undo all`() =
        testScope.runTest {
            val isUndoable = collectLastValue(underTest.isUndoable)
            assertThat(isUndoable()).isFalse()

            underTest.startSession()
            assertThat(isUndoable()).isFalse()

            FAKE_RESTORERS[1]?.update(2)
            assertThat(isUndoable()).isTrue()

            FAKE_RESTORERS[1]?.update(0) // This resets back to the initial snapshot
            assertThat(isUndoable()).isFalse()

            FAKE_RESTORERS[1]?.update(1)
            FAKE_RESTORERS[2]?.update(2)
            assertThat(isUndoable()).isTrue()

            underTest.revertAll()
            assertThat(isUndoable()).isFalse()
            assertThat(FAKE_RESTORERS[1]?.restored).isEqualTo(snapshot(1, 0))
            assertThat(FAKE_RESTORERS[2]?.restored).isEqualTo(snapshot(2, 0))
            assertThat(FAKE_RESTORERS[3]?.restored).isNull()
        }
}
