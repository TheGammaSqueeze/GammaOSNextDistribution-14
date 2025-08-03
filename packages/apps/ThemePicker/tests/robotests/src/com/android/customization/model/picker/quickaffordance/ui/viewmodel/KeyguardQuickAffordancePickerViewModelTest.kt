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

package com.android.customization.model.picker.quickaffordance.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.quickaffordance.data.repository.KeyguardQuickAffordancePickerRepository
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordanceSnapshotRestorer
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordanceSlotViewModel
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordanceSummaryViewModel
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.systemui.shared.customization.data.content.FakeCustomizationProviderClient
import com.android.systemui.shared.keyguard.shared.model.KeyguardQuickAffordanceSlots
import com.android.themepicker.R
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.FakeWallpaperClient
import com.android.wallpaper.testing.TestCurrentWallpaperInfoFactory
import com.android.wallpaper.testing.TestInjector
import com.android.wallpaper.testing.TestWallpaperPreferences
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
class KeyguardQuickAffordancePickerViewModelTest {

    private val logger = TestThemesUserEventLogger()

    private lateinit var underTest: KeyguardQuickAffordancePickerViewModel

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var client: FakeCustomizationProviderClient
    private lateinit var quickAffordanceInteractor: KeyguardQuickAffordancePickerInteractor
    private lateinit var wallpaperInteractor: WallpaperInteractor

    @Before
    fun setUp() {
        InjectorProvider.setInjector(TestInjector(logger))
        context = ApplicationProvider.getApplicationContext()
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        client = FakeCustomizationProviderClient()

        quickAffordanceInteractor =
            KeyguardQuickAffordancePickerInteractor(
                repository =
                    KeyguardQuickAffordancePickerRepository(
                        client = client,
                        scope = testScope.backgroundScope,
                    ),
                client = client,
                snapshotRestorer = {
                    KeyguardQuickAffordanceSnapshotRestorer(
                            interactor = quickAffordanceInteractor,
                            client = client,
                        )
                        .apply { runBlocking { setUpSnapshotRestorer(FakeSnapshotStore()) } }
                },
            )
        wallpaperInteractor =
            WallpaperInteractor(
                repository =
                    WallpaperRepository(
                        scope = testScope.backgroundScope,
                        client = FakeWallpaperClient(),
                        wallpaperPreferences = TestWallpaperPreferences(),
                        backgroundDispatcher = testDispatcher,
                    ),
            )
        underTest =
            KeyguardQuickAffordancePickerViewModel.Factory(
                    context = context,
                    quickAffordanceInteractor = quickAffordanceInteractor,
                    wallpaperInteractor = wallpaperInteractor,
                    wallpaperInfoFactory = TestCurrentWallpaperInfoFactory(context),
                    logger = logger,
                )
                .create(KeyguardQuickAffordancePickerViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Select an affordance for each side`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)

            // Initially, the first slot is selected with the "none" affordance selected.
            assertPickerUiState(
                slots = slots(),
                affordances = quickAffordances(),
                selectedSlotText = "Left button",
                selectedAffordanceText = "None",
            )
            assertPreviewUiState(
                slots = slots(),
                expectedAffordanceNameBySlotId =
                    mapOf(
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START to null,
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END to null,
                    ),
            )

            // Select "affordance 1" for the first slot.
            selectAffordance(quickAffordances, 1)
            assertPickerUiState(
                slots = slots(),
                affordances = quickAffordances(),
                selectedSlotText = "Left button",
                selectedAffordanceText = FakeCustomizationProviderClient.AFFORDANCE_1,
            )
            assertPreviewUiState(
                slots = slots(),
                expectedAffordanceNameBySlotId =
                    mapOf(
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START to
                            FakeCustomizationProviderClient.AFFORDANCE_1,
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END to null,
                    ),
            )

            // Select an affordance for the second slot.
            // First, switch to the second slot:
            slots()?.get(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END)?.onClicked?.invoke()
            // Second, select the "affordance 3" affordance:
            selectAffordance(quickAffordances, 3)
            assertPickerUiState(
                slots = slots(),
                affordances = quickAffordances(),
                selectedSlotText = "Right button",
                selectedAffordanceText = FakeCustomizationProviderClient.AFFORDANCE_3,
            )
            assertPreviewUiState(
                slots = slots(),
                expectedAffordanceNameBySlotId =
                    mapOf(
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START to
                            FakeCustomizationProviderClient.AFFORDANCE_1,
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END to
                            FakeCustomizationProviderClient.AFFORDANCE_3,
                    ),
            )

            // Select a different affordance for the second slot.
            selectAffordance(quickAffordances, 2)
            assertPickerUiState(
                slots = slots(),
                affordances = quickAffordances(),
                selectedSlotText = "Right button",
                selectedAffordanceText = FakeCustomizationProviderClient.AFFORDANCE_2,
            )
            assertPreviewUiState(
                slots = slots(),
                expectedAffordanceNameBySlotId =
                    mapOf(
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START to
                            FakeCustomizationProviderClient.AFFORDANCE_1,
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END to
                            FakeCustomizationProviderClient.AFFORDANCE_2,
                    ),
            )
        }

    @Test
    fun `Unselect - AKA selecting the none affordance - on one side`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)

            // Select "affordance 1" for the first slot.
            selectAffordance(quickAffordances, 1)
            // Select an affordance for the second slot.
            // First, switch to the second slot:
            slots()?.get(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END)?.onClicked?.invoke()
            // Second, select the "affordance 3" affordance:
            selectAffordance(quickAffordances, 3)

            // Switch back to the first slot:
            slots()?.get(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START)?.onClicked?.invoke()
            // Select the "none" affordance, which is always in position 0:
            selectAffordance(quickAffordances, 0)

            assertPickerUiState(
                slots = slots(),
                affordances = quickAffordances(),
                selectedSlotText = "Left button",
                selectedAffordanceText = "None",
            )
            assertPreviewUiState(
                slots = slots(),
                expectedAffordanceNameBySlotId =
                    mapOf(
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START to null,
                        KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END to
                            FakeCustomizationProviderClient.AFFORDANCE_3,
                    ),
            )
        }

    @Test
    fun `Show enablement dialog when selecting a disabled affordance`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)
            val dialog = collectLastValue(underTest.dialog)
            val activityStartRequest = collectLastValue(underTest.activityStartRequests)

            val enablementExplanation = "enablementExplanation"
            val enablementActionText = "enablementActionText"
            val packageName = "packageName"
            val action = "action"
            val enablementActionIntent = Intent(action).apply { `package` = packageName }
            // Lets add a disabled affordance to the picker:
            val affordanceIndex =
                client.addAffordance(
                    CustomizationProviderClient.Affordance(
                        id = "disabled",
                        name = "disabled",
                        iconResourceId = 1,
                        isEnabled = false,
                        enablementExplanation = enablementExplanation,
                        enablementActionText = enablementActionText,
                        enablementActionIntent = enablementActionIntent,
                    )
                )

            // Lets try to select that disabled affordance:
            selectAffordance(quickAffordances, affordanceIndex + 1)

            // We expect there to be a dialog that should be shown:
            assertThat(dialog()?.icon)
                .isEqualTo(Icon.Loaded(FakeCustomizationProviderClient.ICON_1, null))
            assertThat(dialog()?.headline)
                .isEqualTo(Text.Resource(R.string.keyguard_affordance_enablement_dialog_headline))
            assertThat(dialog()?.message).isEqualTo(Text.Loaded(enablementExplanation))
            assertThat(dialog()?.buttons?.size).isEqualTo(2)
            assertThat(dialog()?.buttons?.first()?.text).isEqualTo(Text.Resource(R.string.cancel))
            assertThat(dialog()?.buttons?.get(1)?.text).isEqualTo(Text.Loaded(enablementActionText))

            // When the button is clicked, we expect an intent of the given enablement action
            // component name to be emitted.
            dialog()?.buttons?.get(1)?.onClicked?.invoke()
            assertThat(activityStartRequest()?.`package`).isEqualTo(packageName)
            assertThat(activityStartRequest()?.action).isEqualTo(action)

            // Once we report that the activity was started, the activity start request should be
            // nullified.
            underTest.onActivityStarted()
            assertThat(activityStartRequest()).isNull()

            // Once we report that the dialog has been dismissed by the user, we expect there to be
            // no dialog to be shown:
            underTest.onDialogDismissed()
            assertThat(dialog()).isNull()
        }

    @Test
    fun `Start settings activity when long-pressing an affordance`() =
        testScope.runTest {
            val quickAffordances = collectLastValue(underTest.quickAffordances)
            val activityStartRequest = collectLastValue(underTest.activityStartRequests)

            // Lets add a configurable affordance to the picker:
            val configureIntent = Intent("some.action")
            val affordanceIndex =
                client.addAffordance(
                    CustomizationProviderClient.Affordance(
                        id = "affordance",
                        name = "affordance",
                        iconResourceId = 1,
                        isEnabled = true,
                        configureIntent = configureIntent,
                    )
                )

            // Lets try to long-click the affordance:
            quickAffordances()?.get(affordanceIndex + 1)?.onLongClicked?.invoke()

            assertThat(activityStartRequest()).isEqualTo(configureIntent)
            // Once we report that the activity was started, the activity start request should be
            // nullified.
            underTest.onActivityStarted()
            assertThat(activityStartRequest()).isNull()
        }

    @Test
    fun `summary - affordance selected in both bottom-start and bottom-end`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)
            val summary = collectLastValue(underTest.summary)

            // Select "affordance 1" for the first slot.
            selectAffordance(quickAffordances, 1)
            // Select an affordance for the second slot.
            // First, switch to the second slot:
            slots()?.get(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END)?.onClicked?.invoke()
            // Second, select the "affordance 3" affordance:
            selectAffordance(quickAffordances, 3)

            assertThat(summary())
                .isEqualTo(
                    KeyguardQuickAffordanceSummaryViewModel(
                        description =
                            Text.Loaded(
                                "${FakeCustomizationProviderClient.AFFORDANCE_1}," +
                                    " ${FakeCustomizationProviderClient.AFFORDANCE_3}"
                            ),
                        icon1 =
                            Icon.Loaded(
                                FakeCustomizationProviderClient.ICON_1,
                                Text.Loaded("Left shortcut")
                            ),
                        icon2 =
                            Icon.Loaded(
                                FakeCustomizationProviderClient.ICON_3,
                                Text.Loaded("Right shortcut")
                            ),
                    )
                )
        }

    @Test
    fun `summary - affordance selected only on bottom-start`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)
            val summary = collectLastValue(underTest.summary)

            // Select "affordance 1" for the first slot.
            selectAffordance(quickAffordances, 1)

            assertThat(summary())
                .isEqualTo(
                    KeyguardQuickAffordanceSummaryViewModel(
                        description = Text.Loaded(FakeCustomizationProviderClient.AFFORDANCE_1),
                        icon1 =
                            Icon.Loaded(
                                FakeCustomizationProviderClient.ICON_1,
                                Text.Loaded("Left shortcut")
                            ),
                        icon2 = null,
                    )
                )
        }

    @Test
    fun `summary - affordance selected only on bottom-end`() =
        testScope.runTest {
            val slots = collectLastValue(underTest.slots)
            val quickAffordances = collectLastValue(underTest.quickAffordances)
            val summary = collectLastValue(underTest.summary)

            // Select an affordance for the second slot.
            // First, switch to the second slot:
            slots()?.get(KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END)?.onClicked?.invoke()
            // Second, select the "affordance 3" affordance:
            selectAffordance(quickAffordances, 3)

            assertThat(summary())
                .isEqualTo(
                    KeyguardQuickAffordanceSummaryViewModel(
                        description = Text.Loaded(FakeCustomizationProviderClient.AFFORDANCE_3),
                        icon1 = null,
                        icon2 =
                            Icon.Loaded(
                                FakeCustomizationProviderClient.ICON_3,
                                Text.Loaded("Right shortcut")
                            ),
                    )
                )
        }

    @Test
    fun `summary - no affordances selected`() =
        testScope.runTest {
            val summary = collectLastValue(underTest.summary)

            assertThat(summary()?.description)
                .isEqualTo(Text.Resource(R.string.keyguard_quick_affordance_none_selected))
            assertThat(summary()?.icon1).isNotNull()
            assertThat(summary()?.icon2).isNull()
        }

    /** Simulates a user selecting the affordance at the given index, if that is clickable. */
    private fun TestScope.selectAffordance(
        affordances: () -> List<OptionItemViewModel<Icon>>?,
        index: Int,
    ) {
        val onClickedFlow = affordances()?.get(index)?.onClicked
        val onClickedLastValueOrNull: (() -> (() -> Unit)?)? =
            onClickedFlow?.let { collectLastValue(it) }
        onClickedLastValueOrNull?.let { onClickedLastValue ->
            val onClickedOrNull: (() -> Unit)? = onClickedLastValue()
            onClickedOrNull?.let { onClicked -> onClicked() }
        }
    }

    /**
     * Asserts the entire picker UI state is what is expected. This includes the slot tabs and the
     * affordance list.
     *
     * @param slots The observed slot view-models, keyed by slot ID
     * @param affordances The observed affordances
     * @param selectedSlotText The text of the slot that's expected to be selected
     * @param selectedAffordanceText The text of the affordance that's expected to be selected
     */
    private fun TestScope.assertPickerUiState(
        slots: Map<String, KeyguardQuickAffordanceSlotViewModel>?,
        affordances: List<OptionItemViewModel<Icon>>?,
        selectedSlotText: String,
        selectedAffordanceText: String,
    ) {
        assertSlotTabUiState(
            slots = slots,
            slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START,
            isSelected = "Left button" == selectedSlotText,
        )
        assertSlotTabUiState(
            slots = slots,
            slotId = KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END,
            isSelected = "Right button" == selectedSlotText,
        )

        var foundSelectedAffordance = false
        assertThat(affordances).isNotNull()
        affordances?.forEach { affordance ->
            val nameMatchesSelectedName =
                Text.evaluationEquals(
                    context,
                    affordance.text,
                    Text.Loaded(selectedAffordanceText),
                )
            val isSelected: Boolean? = collectLastValue(affordance.isSelected).invoke()
            assertWithMessage(
                    "Expected affordance with name \"${affordance.text}\" to have" +
                        " isSelected=$nameMatchesSelectedName but it was $isSelected"
                )
                .that(isSelected)
                .isEqualTo(nameMatchesSelectedName)
            foundSelectedAffordance = foundSelectedAffordance || nameMatchesSelectedName
        }
        assertWithMessage("No affordance is selected!").that(foundSelectedAffordance).isTrue()
    }

    /**
     * Asserts that a slot tab has the correct UI state.
     *
     * @param slots The observed slot view-models, keyed by slot ID
     * @param slotId the ID of the slot to assert
     * @param isSelected Whether that slot should be selected
     */
    private fun assertSlotTabUiState(
        slots: Map<String, KeyguardQuickAffordanceSlotViewModel>?,
        slotId: String,
        isSelected: Boolean,
    ) {
        val viewModel = slots?.get(slotId) ?: error("No slot with ID \"$slotId\"!")
        assertThat(viewModel.isSelected).isEqualTo(isSelected)
    }

    /**
     * Asserts the UI state of the preview.
     *
     * @param slots The observed slot view-models, keyed by slot ID
     * @param expectedAffordanceNameBySlotId The expected name of the selected affordance for each
     *   slot ID or `null` if it's expected for there to be no affordance for that slot in the
     *   preview
     */
    private fun assertPreviewUiState(
        slots: Map<String, KeyguardQuickAffordanceSlotViewModel>?,
        expectedAffordanceNameBySlotId: Map<String, String?>,
    ) {
        assertThat(slots).isNotNull()
        slots?.forEach { (slotId, slotViewModel) ->
            val expectedAffordanceName = expectedAffordanceNameBySlotId[slotId]
            val actualAffordanceName = slotViewModel.selectedQuickAffordances.firstOrNull()?.text
            assertWithMessage(
                    "At slotId=\"$slotId\", expected affordance=\"$expectedAffordanceName\" but" +
                        " was \"${actualAffordanceName?.asString(context)}\"!"
                )
                .that(actualAffordanceName?.asString(context))
                .isEqualTo(expectedAffordanceName)
        }
    }
}
