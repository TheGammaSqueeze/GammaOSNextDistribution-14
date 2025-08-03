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

package com.android.intentresolver

import android.content.ComponentName
import android.provider.Settings
import android.testing.TestableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooserIntegratedDeviceComponentsTest {
    private val secureSettings = mock<SecureSettings>()
    private val testableContext =
        TestableContext(InstrumentationRegistry.getInstrumentation().getContext())

    @Test
    fun testEditorAndNearby() {
        val resources = testableContext.getOrCreateTestableResources()

        resources.addOverride(R.string.config_systemImageEditor, "")
        resources.addOverride(R.string.config_defaultNearbySharingComponent, "")

        var components = ChooserIntegratedDeviceComponents.get(testableContext, secureSettings)

        assertThat(components.editSharingComponent).isNull()
        assertThat(components.nearbySharingComponent).isNull()

        val editor = ComponentName.unflattenFromString("com.android/com.android.Editor")
        val nearby = ComponentName.unflattenFromString("com.android/com.android.nearby")

        resources.addOverride(R.string.config_systemImageEditor, editor?.flattenToString())
        resources.addOverride(
            R.string.config_defaultNearbySharingComponent, nearby?.flattenToString())

        components = ChooserIntegratedDeviceComponents.get(testableContext, secureSettings)

        assertThat(components.editSharingComponent).isEqualTo(editor)
        assertThat(components.nearbySharingComponent).isEqualTo(nearby)

        val anotherNearby =
            ComponentName.unflattenFromString("com.android/com.android.another_nearby")
        whenever(
            secureSettings.getString(
                any(),
                eq(Settings.Secure.NEARBY_SHARING_COMPONENT)
            )
        ).thenReturn(anotherNearby?.flattenToString())

        components = ChooserIntegratedDeviceComponents.get(testableContext, secureSettings)

        assertThat(components.nearbySharingComponent).isEqualTo(anotherNearby)
    }
}
