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

package com.android.car.docklib.data

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.TestUtils
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockAppItemTest {
    @Test
    fun compareAppItems_equal() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id)
        val item2: DockAppItem = TestUtils.createAppItem(id = id)

        assertThat(item1).isEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentApps() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, app = "1")
        val item2: DockAppItem = TestUtils.createAppItem(id = id, app = "2")

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentNames() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, name = "1")
        val item2: DockAppItem = TestUtils.createAppItem(id = id, name = "2")

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentStates() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, type = DockAppItem.Type.DYNAMIC)
        val item2: DockAppItem = TestUtils.createAppItem(id = id, type = DockAppItem.Type.STATIC)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentIcons() {
        val icon1 = mock<Drawable>()
        whenever(icon1.constantState).thenReturn(null)
        val icon2 = mock<Drawable>()
        whenever(icon2.constantState).thenReturn(mock<Drawable.ConstantState>())
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, icon = icon1)
        val item2: DockAppItem = TestUtils.createAppItem(id = id, icon = icon2)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentDrivingOptimized() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, isDrivingOptimized = true)
        val item2: DockAppItem = TestUtils.createAppItem(id = id, isDrivingOptimized = false)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentMediaType() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, isMediaApp = true)
        val item2: DockAppItem = TestUtils.createAppItem(id = id, isMediaApp = false)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentIconColor() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(id = id, iconColor = Color.WHITE)
        val item2: DockAppItem = TestUtils.createAppItem(id = id, iconColor = Color.BLACK)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun compareAppItems_notEqual_differentIconColorScrim() {
        val id = UUID.randomUUID()
        val item1: DockAppItem = TestUtils.createAppItem(
                id = id,
                iconColor = Color.WHITE,
                iconColorScrim = Color.argb(
                        100, // alpha
                        255, // red
                        0, // green
                        0 // blue
                )
        )
        val item2: DockAppItem = TestUtils.createAppItem(
                id = id,
                iconColor = Color.WHITE,
                iconColorScrim = Color.argb(
                        150, // alpha
                        0, // red
                        255, // green
                        0 // blue
                )
        )

        assertThat(item1).isNotEqualTo(item2)
    }
}
