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

package com.android.customization.model.picker.quickaffordance.domain.interactor

import androidx.test.filters.SmallTest
import com.android.customization.picker.quickaffordance.data.repository.KeyguardQuickAffordancePickerRepository
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordanceSnapshotRestorer
import com.android.customization.picker.quickaffordance.shared.model.KeyguardQuickAffordancePickerSelectionModel
import com.android.systemui.shared.customization.data.content.FakeCustomizationProviderClient
import com.android.systemui.shared.keyguard.shared.model.KeyguardQuickAffordanceSlots
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class KeyguardQuickAffordancePickerInteractorTest {

    private lateinit var underTest: KeyguardQuickAffordancePickerInteractor

    private lateinit var testScope: TestScope
    private lateinit var client: FakeCustomizationProviderClient

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        client = FakeCustomizationProviderClient()
        underTest =
            KeyguardQuickAffordancePickerInteractor(
                repository =
                    KeyguardQuickAffordancePickerRepository(
                        client = client,
                        scope = testScope.backgroundScope,
                    ),
                client = client,
                snapshotRestorer = {
                    KeyguardQuickAffordanceSnapshotRestorer(
                            interactor = underTest,
                            client = client,
                        )
                        .apply { runBlocking { setUpSnapshotRestorer(FakeSnapshotStore()) } }
                },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun select() =
        testScope.runTest {
            val selections = collectLastValue(underTest.selections)

            underTest.select(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START,
                affordanceId = FakeCustomizationProviderClient.AFFORDANCE_1,
            )
            assertThat(selections())
                .isEqualTo(
                    listOf(
                        KeyguardQuickAffordancePickerSelectionModel(
                            slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START,
                            affordanceId = FakeCustomizationProviderClient.AFFORDANCE_1,
                        ),
                    )
                )

            underTest.select(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START,
                affordanceId = FakeCustomizationProviderClient.AFFORDANCE_2,
            )
            assertThat(selections())
                .isEqualTo(
                    listOf(
                        KeyguardQuickAffordancePickerSelectionModel(
                            slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START,
                            affordanceId = FakeCustomizationProviderClient.AFFORDANCE_2,
                        ),
                    )
                )
        }

    @Test
    fun unselectAll() =
        testScope.runTest {
            client.setSlotCapacity(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END, 3)
            val selections = collectLastValue(underTest.selections)
            underTest.select(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END,
                affordanceId = FakeCustomizationProviderClient.AFFORDANCE_1,
            )
            underTest.select(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END,
                affordanceId = FakeCustomizationProviderClient.AFFORDANCE_2,
            )
            underTest.select(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END,
                affordanceId = FakeCustomizationProviderClient.AFFORDANCE_3,
            )

            underTest.unselectAllFromSlot(
                slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END,
            )

            assertThat(selections()).isEmpty()
        }
}
