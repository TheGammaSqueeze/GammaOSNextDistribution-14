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
 */

package com.android.safetycenter.resources

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalStateException
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyCenterResourcesApkTest {
    private val context: Context = getApplicationContext()

    @Test
    fun init_withValidInputs_returnsTrue() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val initialized = resourcesApk.init()

        assertThat(initialized).isTrue()
    }

    @Test
    fun init_withWrongAction_returnsFalse() {
        val resourcesApk = newSafetyCenterResourcesApk(resourcesApkAction = "wrong")

        val initialized = resourcesApk.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun init_withWrongPath_returnsFalse() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkPath = "/apex/com.android.permission")

        val initialized = resourcesApk.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun init_withWrongFlags_returnsFalse() {
        val resourcesApk = newSafetyCenterResourcesApk(flags = PackageManager.MATCH_SYSTEM_ONLY)

        val initialized = resourcesApk.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun getContext_withValidInputs_returnsResourcesApkContext() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val resourcesApkContext = resourcesApk.context

        assertThat(resourcesApkContext.packageName).isEqualTo(RESOURCES_APK_PKG_NAME)
    }

    @Test
    fun getContext_withWrongAction_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(resourcesApkAction = "wrong")

        assertFailsWith(IllegalStateException::class) { resourcesApk.context }
    }

    @Test
    fun getContext_withWrongPath_throws() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkPath = "/apex/com.android.permission")

        assertFailsWith(IllegalStateException::class) { resourcesApk.context }
    }

    @Test
    fun getContext_withWrongFlags_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(flags = PackageManager.MATCH_SYSTEM_ONLY)

        assertFailsWith(IllegalStateException::class) { resourcesApk.context }
    }

    @Test
    fun getResources_withValidInputs_returnsResourcesApkContextResources() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val resources = resourcesApk.resources

        assertThat(resources).isEqualTo(resourcesApk.context.resources)
    }

    @Test
    fun getResources_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) { resourcesApk.resources }
    }

    @Test
    fun getSafetyCenterConfig_withValidInputs_returnsConfigContent() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val config = resourcesApk.safetyCenterConfig
        val configContent = config?.bufferedReader().use { it?.readText() }

        assertThat(config).isNotNull()
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
    }

    @Test
    fun getSafetyCenterConfig_anotherValidConfigName_returnsConfigContent() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val config = resourcesApk.getSafetyCenterConfig(CONFIG_NAME)
        val configContent = config?.bufferedReader().use { it?.readText() }

        assertThat(config).isNotNull()
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
    }

    @Test
    fun getSafetyCenterConfig_invalidConfigNameWithFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getSafetyCenterConfig("wrong")).isNull()
    }

    @Test
    fun getSafetyCenterConfig_invalidConfigNameWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getSafetyCenterConfig("wrong")
        }
    }

    @Test
    fun getSafetyCenterConfig_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) { resourcesApk.safetyCenterConfig }
    }

    @Test
    fun getString_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val ok = resourcesApk.getString(android.R.string.ok)

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getString_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getString(android.R.string.ok)
        }
    }

    @Test
    fun getStringWithFormatArgs_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val ok = resourcesApk.getString(android.R.string.ok, "")

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getStringWithFormatArgs_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getString(android.R.string.ok, "")
        }
    }

    @Test
    fun getStringByName_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getStringByName("valid_string")).isEqualTo("I exist!")
    }

    @Test
    fun getStringByName_invalidStringWithFallback_returnsEmptyString() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getStringByName("invalid_string")).isEqualTo("")
    }

    @Test
    fun getStringByName_invalidStringWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getStringByName("invalid_string")
        }
    }

    @Test
    fun getStringByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getStringByName("valid_string")
        }
    }

    @Test
    fun getStringByNameWithFormatArgs_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getStringByName("valid_string", "")).isEqualTo("I exist!")
    }

    @Test
    fun getStringByNameWithFormatArgs_invalidStringWithFallback_returnsEmptyString() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getStringByName("invalid_string", "")).isEqualTo("")
    }

    @Test
    fun getStringByNameWithFormatArgs_invalidStringWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getStringByName("invalid_string", "")
        }
    }

    @Test
    fun getStringByNameWithFormatArgs_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getStringByName("valid_string", "")
        }
    }

    @Test
    fun getOptionalString_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val ok = resourcesApk.getOptionalString(android.R.string.ok)

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getOptionalString_resourceIdNull_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk()

        val string = resourcesApk.getOptionalString(Resources.ID_NULL)

        assertThat(string).isNull()
    }

    @Test
    fun getOptionalString_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getOptionalString(android.R.string.ok)
        }
    }

    @Test
    fun getOptionalStringByName_validString_returnsString() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getOptionalStringByName("valid_string")).isEqualTo("I exist!")
    }

    @Test
    fun getOptionalStringByName_invalidStringWithFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getOptionalStringByName("invalid_string")).isNull()
    }

    @Test
    fun getOptionalStringByName_invalidStringWithoutFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertThat(resourcesApk.getOptionalStringByName("invalid_string")).isNull()
    }

    @Test
    fun getOptionalStringByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getOptionalStringByName("valid_string")
        }
    }

    @Test
    fun getDrawableByName_validDrawable_returnsDrawable() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getDrawableByName("valid_drawable", context.theme)).isNotNull()
    }

    @Test
    fun getDrawableByName_invalidDrawableWithFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getDrawableByName("invalid_drawable", context.theme)).isNull()
    }

    @Test
    fun getDrawableByName_invalidDrawableWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getDrawableByName("invalid_drawable", context.theme)
        }
    }

    @Test
    fun getDrawableByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getDrawableByName("valid_drawable", context.theme)
        }
    }

    @Test
    fun getIconByDrawableName_validDrawable_returnsIcon() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getIconByDrawableName("valid_drawable")).isNotNull()
    }

    @Test
    fun getIconByDrawableName_invalidDrawableWithFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getIconByDrawableName("invalid_drawable")).isNull()
    }

    @Test
    fun getIconByDrawableName_invalidDrawableWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getIconByDrawableName("invalid_drawable")
        }
    }

    @Test
    fun getIconByDrawableByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesApk.getIconByDrawableName("valid_drawable")
        }
    }

    @Test
    fun getColorByName_validColor_returnsColor() {
        val resourcesApk = newSafetyCenterResourcesApk()

        assertThat(resourcesApk.getColorByName("valid_color")).isNotNull()
    }

    @Test
    fun getColorByName_invalidColorWithFallback_returnsNull() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = true)

        assertThat(resourcesApk.getColorByName("invalid_color")).isNull()
    }

    @Test
    fun getColorByName_invalidColorWithoutFallback_throws() {
        val resourcesApk = newSafetyCenterResourcesApk(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesApk.getColorByName("invalid_color")
        }
    }

    @Test
    fun getColorByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesApk =
            newSafetyCenterResourcesApk(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) { resourcesApk.getColorByName("valid_color") }
    }

    private fun newSafetyCenterResourcesApk(
        resourcesApkAction: String = RESOURCES_APK_ACTION,
        resourcesApkPath: String = "",
        flags: Int = 0,
        fallback: Boolean = false
    ) = SafetyCenterResourcesApk(context, resourcesApkAction, resourcesApkPath, flags, fallback)

    companion object {
        const val RESOURCES_APK_ACTION =
            "com.android.safetycenter.tests.intent.action.SAFETY_CENTER_TEST_RESOURCES_APK"
        const val RESOURCES_APK_PKG_NAME =
            "com.android.safetycenter.tests.config.safetycenterresourceslibtestresources"
        const val CONFIG_NAME = "test"
        const val CONFIG_CONTENT = "TEST"
    }
}
