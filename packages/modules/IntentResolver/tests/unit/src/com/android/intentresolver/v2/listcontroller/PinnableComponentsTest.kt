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

package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import android.content.SharedPreferences
import com.android.intentresolver.any
import com.android.intentresolver.eq
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class PinnableComponentsTest {

    @Mock lateinit var mockSharedPreferences: SharedPreferences

    private val unpinnedComponent = ComponentName("TestPackage", "TestClass")
    private val pinnedComponent = ComponentName("PinnedPackage", "PinnedClass")
    private val noComponentPinning = NoComponentPinning()

    private lateinit var sharedPreferencesPinnedComponents: PinnableComponents

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        sharedPreferencesPinnedComponents = SharedPreferencesPinnedComponents(mockSharedPreferences)
    }

    @Test
    fun isComponentPinned_noComponentPinning_neverPins() {
        // Arrange

        // Act
        val unpinnedResult = noComponentPinning.isComponentPinned(unpinnedComponent)
        val pinnedResult = noComponentPinning.isComponentPinned(pinnedComponent)

        // Assert
        assertThat(unpinnedResult).isFalse()
        assertThat(pinnedResult).isFalse()
    }

    @Test
    fun isComponentFiltered_chooserRequestFilteredComponents_filtersAccordingToChooserRequest() {
        // Arrange
        whenever(mockSharedPreferences.getBoolean(eq(pinnedComponent.flattenToString()), any()))
            .thenReturn(true)

        // Act
        val unpinnedResult = sharedPreferencesPinnedComponents.isComponentPinned(unpinnedComponent)
        val pinnedResult = sharedPreferencesPinnedComponents.isComponentPinned(pinnedComponent)

        // Assert
        assertThat(unpinnedResult).isFalse()
        assertThat(pinnedResult).isTrue()
    }
}
