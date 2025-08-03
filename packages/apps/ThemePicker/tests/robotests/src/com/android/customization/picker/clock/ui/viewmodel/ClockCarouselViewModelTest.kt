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
 */
package com.android.customization.picker.clock.ui.viewmodel

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.clock.data.repository.ClockPickerRepository
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.domain.interactor.ClockPickerSnapshotRestorer
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.clock.ui.FakeClockViewFactory
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ClockCarouselViewModelTest {
    private val repositoryWithMultipleClocks by lazy { FakeClockPickerRepository() }
    private val repositoryWithSingleClock by lazy {
        FakeClockPickerRepository(
            listOf(
                ClockMetadataModel(
                    clockId = FakeClockPickerRepository.CLOCK_ID_0,
                    isSelected = true,
                    selectedColorId = null,
                    colorToneProgress = ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS,
                    seedColor = null,
                ),
            )
        )
    }

    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var underTest: ClockCarouselViewModel
    private lateinit var interactor: ClockPickerInteractor
    private lateinit var clockViewFactory: ClockViewFactory

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        clockViewFactory = FakeClockViewFactory()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSelectedClock() = runTest {
        underTest =
            ClockCarouselViewModel(
                getClockPickerInteractor(repositoryWithMultipleClocks),
                backgroundDispatcher = testDispatcher,
                clockViewFactory = clockViewFactory,
                resources = InstrumentationRegistry.getInstrumentation().targetContext.resources,
                logger = TestThemesUserEventLogger(),
            )
        val observedSelectedIndex = collectLastValue(underTest.selectedIndex)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        underTest.setSelectedClock(FakeClockPickerRepository.fakeClocks[2].clockId)

        assertThat(observedSelectedIndex()).isEqualTo(2)
    }

    private fun getClockPickerInteractor(repository: ClockPickerRepository): ClockPickerInteractor {
        return ClockPickerInteractor(
                repository = repository,
                snapshotRestorer = {
                    ClockPickerSnapshotRestorer(interactor = interactor).apply {
                        runBlocking { setUpSnapshotRestorer(store = FakeSnapshotStore()) }
                    }
                }
            )
            .also { interactor = it }
    }
}
