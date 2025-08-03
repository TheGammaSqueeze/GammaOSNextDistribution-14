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
 *
 */

package com.android.customization.model.grid.domain.interactor

import androidx.test.filters.SmallTest
import com.android.customization.model.grid.data.repository.FakeGridRepository
import com.android.customization.picker.grid.domain.interactor.GridInteractor
import com.android.customization.picker.grid.domain.interactor.GridSnapshotRestorer
import com.android.customization.picker.grid.shared.model.GridOptionItemsModel
import com.android.wallpaper.testing.FakeSnapshotStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class GridSnapshotRestorerTest {

    private lateinit var underTest: GridSnapshotRestorer
    private lateinit var testScope: TestScope
    private lateinit var repository: FakeGridRepository
    private lateinit var store: FakeSnapshotStore

    @Before
    fun setUp() {
        testScope = TestScope()
        repository =
            FakeGridRepository(
                scope = testScope.backgroundScope,
                initialOptionCount = 4,
            )
        underTest =
            GridSnapshotRestorer(
                interactor =
                    GridInteractor(
                        applicationScope = testScope.backgroundScope,
                        repository = repository,
                        snapshotRestorer = { underTest },
                    )
            )
        store = FakeSnapshotStore()
    }

    @Test
    fun restoreToSnapshot_noCallsToStore_restoresToInitialSnapshot() =
        testScope.runTest {
            runCurrent()
            val initialSnapshot = underTest.setUpSnapshotRestorer(store = store)
            assertThat(initialSnapshot.args).isNotEmpty()
            repository.setOptions(
                count = 4,
                selectedIndex = 2,
            )
            runCurrent()
            assertThat(getSelectedIndex()).isEqualTo(2)

            underTest.restoreToSnapshot(initialSnapshot)
            runCurrent()

            assertThat(getSelectedIndex()).isEqualTo(0)
        }

    @Test
    fun restoreToSnapshot_withCallToStore_restoresToInitialSnapshot() =
        testScope.runTest {
            runCurrent()
            val initialSnapshot = underTest.setUpSnapshotRestorer(store = store)
            assertThat(initialSnapshot.args).isNotEmpty()
            repository.setOptions(
                count = 4,
                selectedIndex = 2,
            )
            runCurrent()
            assertThat(getSelectedIndex()).isEqualTo(2)
            underTest.store((repository.getOptions() as GridOptionItemsModel.Loaded).options[1])
            runCurrent()

            underTest.restoreToSnapshot(initialSnapshot)
            runCurrent()

            assertThat(getSelectedIndex()).isEqualTo(0)
        }

    private suspend fun getSelectedIndex(): Int {
        return (repository.getOptions() as? GridOptionItemsModel.Loaded)?.options?.indexOfFirst {
            optionItem ->
            optionItem.isSelected.value
        }
            ?: -1
    }
}
