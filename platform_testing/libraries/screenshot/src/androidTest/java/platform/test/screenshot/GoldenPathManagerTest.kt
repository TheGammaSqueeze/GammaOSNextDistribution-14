/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class GoldenPathManagerTest {

    @Test
    fun goldenImageWithContextTest() {
        val subject = GoldenImagePathManager(InstrumentationRegistry.getInstrumentation().context)
        // Test for resolving device local paths.
        val localGoldenFullImagePath = subject.goldenIdentifierResolver(testName = "test1")
        assertThat(localGoldenFullImagePath).endsWith("/test1.png")
        assertThat(localGoldenFullImagePath.split("/").size).isEqualTo(2)
    }

    @Test
    fun goldenPathManager_includesPathConfig() {
        val subject =
            GoldenPathManager(
                InstrumentationRegistry.getInstrumentation().context,
                pathConfig = PathConfig(PathElementNoContext("something", true) { "mydevice" })
            )
        val pathSegments = subject.goldenIdentifierResolver(testName = "test1").split("/")
        assertThat(pathSegments).containsExactly("mydevice", "test1.png").inOrder()
    }

    @Test
    fun goldenPathManager_appendsPngExtensionByDefault() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val subject = GoldenPathManager(context)
        val result = subject.goldenIdentifierResolver(testName = "test1")
        assertThat(result).endsWith("/test1.png")
    }

    @Test
    fun goldenPathManager_allowsOverrideFileExtension() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val subject = GoldenPathManager(context)
        val result = subject.goldenIdentifierResolver(testName = "test1", extension = "proto")
        assertThat(result).endsWith("/test1.proto")
    }

    private fun pathContextExtractor(context: Context): String {
        return when {
            (context.resources.displayMetrics.densityDpi.toString().length > 0) -> "context"
            else -> "invalidcontext"
        }
    }

    private fun pathNoContextExtractor1() = "nocontext1"
    private fun pathNoContextExtractor2() = "nocontext2"

    @Test
    fun pathConfigTest() {
        val pc =
            PathConfig(
                PathElementNoContext("nocontext1", true, ::pathNoContextExtractor1),
                PathElementNoContext("nocontext2", true, ::pathNoContextExtractor2),
                PathElementWithContext("context1", true, ::pathContextExtractor),
                PathElementWithContext("context2", true, ::pathContextExtractor)
            )
        val context = InstrumentationRegistry.getInstrumentation().getContext()
        val pcResolvedRelativePath = pc.resolveRelativePath(context)
        assertThat(pcResolvedRelativePath).isEqualTo("nocontext1/nocontext2/context/context/")

        val pc2 = getSimplePathConfig()
        val pcResolvedRelativePath2 = pc2.resolveRelativePath(context)
        assertThat(pcResolvedRelativePath2).startsWith("cuttlefish")
    }

    @Test
    fun emulatedDevicePathConfigTest() {
        val context = InstrumentationRegistry.getInstrumentation().context

        val pc1 =
            getEmulatedDevicePathConfig(
                DeviceEmulationSpec(
                    DisplaySpec("phone", width = 100, height = 200, densityDpi = 180),
                    isDarkTheme = false,
                    isLandscape = false,
                )
            )
        assertThat(pc1.resolveRelativePath(context)).isEqualTo("phone/light_portrait_")

        val pc2 =
            getEmulatedDevicePathConfig(
                DeviceEmulationSpec(
                    DisplaySpec("tablet", width = 100, height = 200, densityDpi = 180),
                    isDarkTheme = true,
                    isLandscape = true,
                )
            )
        assertThat(pc2.resolveRelativePath(context)).isEqualTo("tablet/dark_landscape_")
    }
}
