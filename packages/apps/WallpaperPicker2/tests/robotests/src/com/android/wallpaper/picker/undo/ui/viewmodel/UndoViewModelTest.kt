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

package com.android.wallpaper.picker.undo.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.wallpaper.module.logging.TestUserEventLogger
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.testing.FAKE_RESTORERS
import com.android.wallpaper.testing.collectLastValue
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
class UndoViewModelTest {

    private lateinit var underTest: UndoViewModel

    private lateinit var testScope: TestScope
    private lateinit var interactor: UndoInteractor

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        interactor =
            UndoInteractor(
                scope = testScope.backgroundScope,
                repository = UndoRepository(),
                restorerByOwnerId = FAKE_RESTORERS,
            )

        underTest =
            UndoViewModel(
                interactor = interactor,
                logger = TestUserEventLogger(),
            )
    }

    @Test
    fun revert() =
        testScope.runTest {
            val isRevertButtonVisible = collectLastValue(underTest.isRevertButtonVisible)
            val dialog = collectLastValue(underTest.dialog)
            assertThat(isRevertButtonVisible()).isFalse()
            assertThat(dialog()).isNull()

            // Start the session without anything to revert.
            interactor.startSession()
            assertThat(isRevertButtonVisible()).isFalse()
            assertThat(dialog()).isNull()

            // Record a change that can be reverted.
            FAKE_RESTORERS[1]?.update(2)
            assertThat(isRevertButtonVisible()).isTrue()
            assertThat(dialog()).isNull()

            // Click the revert button.
            underTest.onRevertButtonClicked()
            assertThat(isRevertButtonVisible()).isTrue()
            assertThat(dialog()).isNotNull()

            // Cancel the revert.
            dialog()?.onDismissed?.invoke()
            assertThat(isRevertButtonVisible()).isTrue()
            assertThat(dialog()).isNull()

            // Click the revert button again.
            underTest.onRevertButtonClicked()
            assertThat(isRevertButtonVisible()).isTrue()
            assertThat(dialog()).isNotNull()

            // Confirm the revert.
            dialog()?.buttons?.last()?.onClicked?.invoke()
            assertThat(isRevertButtonVisible()).isFalse()
        }
}
