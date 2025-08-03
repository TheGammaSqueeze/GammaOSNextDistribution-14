package com.android.customization.picker.clock.ui.viewmodel

import android.content.Context
import android.stats.style.StyleEnums
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.domain.interactor.ClockPickerSnapshotRestorer
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerSnapshotRestorer
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ClockSettingsViewModelTest {

    private val logger = TestThemesUserEventLogger()
    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var colorPickerInteractor: ColorPickerInteractor
    private lateinit var clockPickerInteractor: ClockPickerInteractor
    private lateinit var underTest: ClockSettingsViewModel
    private lateinit var colorMap: Map<String, ClockColorViewModel>
    // We make the condition that CLOCK_ID_3 is not reactive to tone
    private val getIsReactiveToTone: (clockId: String?) -> Boolean = { clockId ->
        when (clockId) {
            FakeClockPickerRepository.CLOCK_ID_0 -> true
            FakeClockPickerRepository.CLOCK_ID_1 -> true
            FakeClockPickerRepository.CLOCK_ID_2 -> true
            FakeClockPickerRepository.CLOCK_ID_3 -> false
            else -> false
        }
    }

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testScope = TestScope(testDispatcher)
        clockPickerInteractor =
            ClockPickerInteractor(
                repository = FakeClockPickerRepository(),
                snapshotRestorer = {
                    ClockPickerSnapshotRestorer(interactor = clockPickerInteractor).apply {
                        runBlocking { setUpSnapshotRestorer(store = FakeSnapshotStore()) }
                    }
                },
            )
        colorPickerInteractor =
            ColorPickerInteractor(
                repository = FakeColorPickerRepository(context = context),
                snapshotRestorer = {
                    ColorPickerSnapshotRestorer(interactor = colorPickerInteractor).apply {
                        runBlocking { setUpSnapshotRestorer(store = FakeSnapshotStore()) }
                    }
                },
            )
        underTest =
            ClockSettingsViewModel.Factory(
                    context = context,
                    clockPickerInteractor = clockPickerInteractor,
                    colorPickerInteractor = colorPickerInteractor,
                    logger = logger,
                    getIsReactiveToTone = getIsReactiveToTone,
                )
                .create(ClockSettingsViewModel::class.java)
        colorMap = ClockColorViewModel.getPresetColorMap(context.resources)

        testScope.launch {
            clockPickerInteractor.setSelectedClock(FakeClockPickerRepository.CLOCK_ID_0)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clickOnColorSettingsTab() = runTest {
        val tabs = collectLastValue(underTest.tabs)
        assertThat(tabs()?.get(0)?.name).isEqualTo("Color")
        assertThat(tabs()?.get(0)?.isSelected).isTrue()
        assertThat(tabs()?.get(1)?.name).isEqualTo("Size")
        assertThat(tabs()?.get(1)?.isSelected).isFalse()

        tabs()?.get(1)?.onClicked?.invoke()
        assertThat(tabs()?.get(0)?.isSelected).isFalse()
        assertThat(tabs()?.get(1)?.isSelected).isTrue()
    }

    @Test
    fun setSelectedColor() = runTest {
        val observedClockColorOptions = collectLastValue(underTest.colorOptions)
        val observedSelectedColorOptionPosition =
            collectLastValue(underTest.selectedColorOptionPosition)
        val observedSliderProgress = collectLastValue(underTest.sliderProgress)
        val observedSeedColor = collectLastValue(underTest.seedColor)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option0IsSelected = collectLastValue(observedClockColorOptions()!![0].isSelected)
        val option0OnClicked = collectLastValue(observedClockColorOptions()!![0].onClicked)
        assertThat(option0IsSelected()).isTrue()
        assertThat(option0OnClicked()).isNull()
        assertThat(observedSelectedColorOptionPosition()).isEqualTo(0)

        val option1OnClickedBefore = collectLastValue(observedClockColorOptions()!![1].onClicked)
        option1OnClickedBefore()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1IsSelected = collectLastValue(observedClockColorOptions()!![1].isSelected)
        val option1OnClickedAfter = collectLastValue(observedClockColorOptions()!![1].onClicked)
        assertThat(option1IsSelected()).isTrue()
        assertThat(option1OnClickedAfter()).isNull()
        assertThat(observedSelectedColorOptionPosition()).isEqualTo(1)
        assertThat(observedSliderProgress())
            .isEqualTo(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)
        val expectedSelectedColorModel = colorMap.values.first() // RED
        assertThat(observedSeedColor())
            .isEqualTo(
                ClockSettingsViewModel.blendColorWithTone(
                    expectedSelectedColorModel.color,
                    expectedSelectedColorModel.getColorTone(
                        ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS
                    ),
                )
            )
    }

    @Test
    fun setColorTone() = runTest {
        val observedClockColorOptions = collectLastValue(underTest.colorOptions)
        val observedIsSliderEnabled = collectLastValue(underTest.isSliderEnabled)
        val observedSliderProgress = collectLastValue(underTest.sliderProgress)
        val observedSeedColor = collectLastValue(underTest.seedColor)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option0IsSelected = collectLastValue(observedClockColorOptions()!![0].isSelected)
        assertThat(option0IsSelected()).isTrue()
        assertThat(observedIsSliderEnabled()).isFalse()

        val option1OnClicked = collectLastValue(observedClockColorOptions()!![1].onClicked)
        option1OnClicked()?.invoke()

        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        assertThat(observedIsSliderEnabled()).isTrue()
        val targetProgress1 = 99
        underTest.onSliderProgressChanged(targetProgress1)
        assertThat(observedSliderProgress()).isEqualTo(targetProgress1)
        val targetProgress2 = 55
        testScope.launch { underTest.onSliderProgressStop(targetProgress2) }
        assertThat(observedSliderProgress()).isEqualTo(targetProgress2)
        val expectedSelectedColorModel = colorMap.values.first() // RED
        assertThat(observedSeedColor())
            .isEqualTo(
                ClockSettingsViewModel.blendColorWithTone(
                    expectedSelectedColorModel.color,
                    expectedSelectedColorModel.getColorTone(targetProgress2),
                )
            )
    }

    @Test
    fun setClockSize() = runTest {
        val observedClockSize = collectLastValue(underTest.selectedClockSize)
        underTest.setClockSize(ClockSize.DYNAMIC)
        assertThat(observedClockSize()).isEqualTo(ClockSize.DYNAMIC)
        assertThat(logger.getLoggedClockSize()).isEqualTo(StyleEnums.CLOCK_SIZE_DYNAMIC)

        underTest.setClockSize(ClockSize.SMALL)
        assertThat(observedClockSize()).isEqualTo(ClockSize.SMALL)
        assertThat(logger.getLoggedClockSize()).isEqualTo(StyleEnums.CLOCK_SIZE_SMALL)
    }

    @Test
    fun getIsReactiveToTone() = runTest {
        val observedClockColorOptions = collectLastValue(underTest.colorOptions)
        val isSliderEnabled = collectLastValue(underTest.isSliderEnabled)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(observedClockColorOptions()!![1].onClicked)
        option1OnClicked()?.invoke()

        clockPickerInteractor.setSelectedClock(FakeClockPickerRepository.CLOCK_ID_0)
        assertThat(isSliderEnabled()).isTrue()

        // We make the condition that CLOCK_ID_0 is not reactive to tone
        clockPickerInteractor.setSelectedClock(FakeClockPickerRepository.CLOCK_ID_3)
        assertThat(isSliderEnabled()).isFalse()
    }
}
