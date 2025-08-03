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
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class GridInteractorTest {

    private lateinit var underTest: GridInteractor
    private lateinit var testScope: TestScope
    private lateinit var repository: FakeGridRepository
    private lateinit var store: FakeSnapshotStore

    @Before
    fun setUp() {
        testScope = TestScope()
        repository =
            FakeGridRepository(
                scope = testScope.backgroundScope,
                initialOptionCount = 3,
            )
        store = FakeSnapshotStore()
        underTest =
            GridInteractor(
                applicationScope = testScope.backgroundScope,
                repository = repository,
                snapshotRestorer = {
                    GridSnapshotRestorer(
                            interactor = underTest,
                        )
                        .apply {
                            runBlocking {
                                setUpSnapshotRestorer(
                                    store = store,
                                )
                            }
                        }
                },
            )
    }

    @Test
    fun selectingOptionThroughModel_updatesOptions() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)
            assertThat(options()).isInstanceOf(GridOptionItemsModel.Loaded::class.java)
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                assertThat(loaded.options).hasSize(3)
                assertThat(loaded.options[0].isSelected.value).isTrue()
                assertThat(loaded.options[1].isSelected.value).isFalse()
                assertThat(loaded.options[2].isSelected.value).isFalse()
            }

            val storedSnapshot = store.retrieve()
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                loaded.options[1].onSelected()
            }

            assertThat(options()).isInstanceOf(GridOptionItemsModel.Loaded::class.java)
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                assertThat(loaded.options).hasSize(3)
                assertThat(loaded.options[0].isSelected.value).isFalse()
                assertThat(loaded.options[1].isSelected.value).isTrue()
                assertThat(loaded.options[2].isSelected.value).isFalse()
            }
            assertThat(store.retrieve()).isNotEqualTo(storedSnapshot)
        }

    @Test
    fun selectingOptionThroughSetter_returnsSelectedOptionFromGetter() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)
            assertThat(options()).isInstanceOf(GridOptionItemsModel.Loaded::class.java)
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                assertThat(loaded.options).hasSize(3)
            }

            val storedSnapshot = store.retrieve()
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                underTest.setSelectedOption(loaded.options[1])
                runCurrent()
                assertThat(underTest.getSelectedOption()?.name).isEqualTo(loaded.options[1].name)
                assertThat(store.retrieve()).isNotEqualTo(storedSnapshot)
            }
        }

    @Test
    fun externalUpdates_reloadInvoked() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)
            assertThat(options()).isInstanceOf(GridOptionItemsModel.Loaded::class.java)
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                assertThat(loaded.options).hasSize(3)
            }

            val storedSnapshot = store.retrieve()
            repository.setOptions(4)

            assertThat(options()).isInstanceOf(GridOptionItemsModel.Loaded::class.java)
            (options() as? GridOptionItemsModel.Loaded)?.let { loaded ->
                assertThat(loaded.options).hasSize(4)
            }
            // External updates do not record a new snapshot with the undo system.
            assertThat(store.retrieve()).isEqualTo(storedSnapshot)
        }

    @Test
    fun unavailableRepository_emptyOptions() =
        testScope.runTest {
            repository.available = false
            val options = collectLastValue(underTest.options)
            assertThat(options()).isNull()
        }
}
