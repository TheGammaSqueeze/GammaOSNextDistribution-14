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

package com.android.customization.picker.notifications.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.shared.settings.data.repository.FakeSecureSettingsRepository
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class NotificationSectionViewModelTest {

    private val logger: ThemesUserEventLogger = TestThemesUserEventLogger()

    private lateinit var underTest: NotificationSectionViewModel

    private lateinit var testScope: TestScope
    private lateinit var interactor: NotificationSettingsInteractor

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        interactor =
            NotificationSettingsInteractor(
                repository =
                    NotificationSettingsRepository(
                        scope = testScope.backgroundScope,
                        backgroundDispatcher = testDispatcher,
                        secureSettingsRepository = FakeSecureSettingsRepository(),
                    ),
            )

        underTest =
            NotificationSectionViewModel(
                interactor = interactor,
                logger = logger,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggles back and forth`() =
        testScope.runTest {
            val isSwitchOn = collectLastValue(underTest.isSwitchOn)

            val initialIsSwitchOn = isSwitchOn()

            underTest.onClicked()
            assertThat(isSwitchOn()).isNotEqualTo(initialIsSwitchOn)

            underTest.onClicked()
            assertThat(isSwitchOn()).isEqualTo(initialIsSwitchOn)
        }
}
