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

package com.android.customization.model.themedicon.domain.interactor

import androidx.test.filters.SmallTest
import com.android.customization.model.themedicon.data.repository.ThemeIconRepository
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class ThemedIconInteractorTest {

    private lateinit var underTest: ThemedIconInteractor

    @Before
    fun setUp() {
        underTest =
            ThemedIconInteractor(
                repository = ThemeIconRepository(),
            )
    }

    @Test
    fun `end-to-end`() = runTest {
        val isActivated = collectLastValue(underTest.isActivated)

        underTest.setActivated(isActivated = true)
        assertThat(isActivated()).isTrue()

        underTest.setActivated(isActivated = false)
        assertThat(isActivated()).isFalse()
    }
}
