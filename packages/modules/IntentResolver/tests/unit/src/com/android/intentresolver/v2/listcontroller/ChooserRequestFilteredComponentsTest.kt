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
import com.android.intentresolver.ChooserRequestParameters
import com.android.intentresolver.whenever
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class ChooserRequestFilteredComponentsTest {

    @Mock lateinit var mockChooserRequestParameters: ChooserRequestParameters

    private lateinit var chooserRequestFilteredComponents: ChooserRequestFilteredComponents

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        chooserRequestFilteredComponents =
            ChooserRequestFilteredComponents(mockChooserRequestParameters)
    }

    @Test
    fun isComponentFiltered_returnsRequestParametersFilteredState() {
        // Arrange
        whenever(mockChooserRequestParameters.filteredComponentNames)
            .thenReturn(
                ImmutableList.of(ComponentName("FilteredPackage", "FilteredClass")),
            )
        val testComponent = ComponentName("TestPackage", "TestClass")
        val filteredComponent = ComponentName("FilteredPackage", "FilteredClass")

        // Act
        val result = chooserRequestFilteredComponents.isComponentFiltered(testComponent)
        val filteredResult = chooserRequestFilteredComponents.isComponentFiltered(filteredComponent)

        // Assert
        assertThat(result).isFalse()
        assertThat(filteredResult).isTrue()
    }
}
