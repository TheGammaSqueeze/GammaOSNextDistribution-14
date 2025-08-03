package com.android.customization.picker.clock.domain.interactor

import androidx.test.filters.SmallTest
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.shared.ClockSize
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ClockPickerInteractorTest {

    private lateinit var underTest: ClockPickerInteractor

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        underTest =
            ClockPickerInteractor(
                repository = FakeClockPickerRepository(),
                snapshotRestorer = {
                    ClockPickerSnapshotRestorer(interactor = underTest).apply {
                        runBlocking { setUpSnapshotRestorer(store = FakeSnapshotStore()) }
                    }
                },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSelectedClock() = runTest {
        val observedSelectedClockId = collectLastValue(underTest.selectedClockId)
        underTest.setSelectedClock(FakeClockPickerRepository.fakeClocks[1].clockId)
        Truth.assertThat(observedSelectedClockId())
            .isEqualTo(FakeClockPickerRepository.fakeClocks[1].clockId)
    }

    @Test
    fun setClockSize() = runTest {
        val observedClockSize = collectLastValue(underTest.selectedClockSize)
        underTest.setClockSize(ClockSize.DYNAMIC)
        Truth.assertThat(observedClockSize()).isEqualTo(ClockSize.DYNAMIC)

        underTest.setClockSize(ClockSize.SMALL)
        Truth.assertThat(observedClockSize()).isEqualTo(ClockSize.SMALL)
    }

    @Test
    fun setColor() = runTest {
        val observedSelectedColor = collectLastValue(underTest.selectedColorId)
        val observedColorToneProgress = collectLastValue(underTest.colorToneProgress)
        val observedSeedColor = collectLastValue(underTest.seedColor)
        underTest.setClockColor(
            FakeClockPickerRepository.CLOCK_COLOR_ID,
            FakeClockPickerRepository.CLOCK_COLOR_TONE_PROGRESS,
            FakeClockPickerRepository.SEED_COLOR,
        )
        Truth.assertThat(observedSelectedColor())
            .isEqualTo(FakeClockPickerRepository.CLOCK_COLOR_ID)
        Truth.assertThat(observedColorToneProgress())
            .isEqualTo(FakeClockPickerRepository.CLOCK_COLOR_TONE_PROGRESS)
        Truth.assertThat(observedSeedColor()).isEqualTo(FakeClockPickerRepository.SEED_COLOR)
    }
}
