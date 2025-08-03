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

package com.android.customization.model.grid.ui.viewmodel

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.model.grid.data.repository.FakeGridRepository
import com.android.customization.picker.grid.domain.interactor.GridInteractor
import com.android.customization.picker.grid.domain.interactor.GridSnapshotRestorer
import com.android.customization.picker.grid.ui.viewmodel.GridIconViewModel
import com.android.customization.picker.grid.ui.viewmodel.GridScreenViewModel
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class GridScreenViewModelTest {

    private lateinit var underTest: GridScreenViewModel
    private lateinit var testScope: TestScope
    private lateinit var interactor: GridInteractor
    private lateinit var store: FakeSnapshotStore

    @Before
    fun setUp() {
        testScope = TestScope()
        store = FakeSnapshotStore()
        interactor =
            GridInteractor(
                applicationScope = testScope.backgroundScope,
                repository =
                    FakeGridRepository(
                        scope = testScope.backgroundScope,
                        initialOptionCount = 4,
                    ),
                snapshotRestorer = {
                    GridSnapshotRestorer(
                            interactor = interactor,
                        )
                        .apply { runBlocking { setUpSnapshotRestorer(store) } }
                }
            )

        underTest =
            GridScreenViewModel(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                interactor = interactor,
            )
    }

    @Test
    @Ignore("b/270371382")
    fun clickOnItem_itGetsSelected() =
        testScope.runTest {
            val optionItemsValueProvider = collectLastValue(underTest.optionItems)
            var optionItemsValue = checkNotNull(optionItemsValueProvider.invoke())
            assertThat(optionItemsValue).hasSize(4)
            assertThat(getSelectedIndex(optionItemsValue)).isEqualTo(0)
            assertThat(getOnClick(optionItemsValue[0])).isNull()

            val item1OnClickedValue = getOnClick(optionItemsValue[1])
            assertThat(item1OnClickedValue).isNotNull()
            item1OnClickedValue?.invoke()

            optionItemsValue = checkNotNull(optionItemsValueProvider.invoke())
            assertThat(optionItemsValue).hasSize(4)
            assertThat(getSelectedIndex(optionItemsValue)).isEqualTo(1)
            assertThat(getOnClick(optionItemsValue[0])).isNotNull()
            assertThat(getOnClick(optionItemsValue[1])).isNull()
        }

    private fun TestScope.getSelectedIndex(
        optionItems: List<OptionItemViewModel<GridIconViewModel>>
    ): Int {
        return optionItems.indexOfFirst { optionItem ->
            collectLastValue(optionItem.isSelected).invoke() == true
        }
    }

    private fun TestScope.getOnClick(
        optionItem: OptionItemViewModel<GridIconViewModel>
    ): (() -> Unit)? {
        return collectLastValue(optionItem.onClicked).invoke()
    }
}
