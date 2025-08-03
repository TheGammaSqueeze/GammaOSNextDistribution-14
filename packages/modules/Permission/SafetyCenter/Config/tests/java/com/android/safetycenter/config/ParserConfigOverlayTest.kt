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

package com.android.safetycenter.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.safetycenter.config.tests.R
import com.android.safetycenter.testing.Coroutines.waitForSuccessWithTimeout
import com.android.safetycenter.testing.Coroutines.waitForWithTimeout
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParserConfigOverlayTest {
    private val context: Context = getApplicationContext()

    @Test
    fun validNotOverlayableConfig_matchesExpected() = waitForSuccessWithTimeout {
        val inputStream = context.resources.openRawResource(R.raw.config_valid_not_overlayable)

        val safetyCenterConfig =
            SafetyCenterConfigParser.parseXmlResource(inputStream, context.resources)

        assertThat(safetyCenterConfig.safetySourcesGroups.size).isEqualTo(1)
        val safetySourcesGroup = safetyCenterConfig.safetySourcesGroups[0]
        assertThat(safetySourcesGroup.id).isEqualTo("Base")
        assertThat(safetySourcesGroup.titleResId).isEqualTo(R.string.reference_not_overlayable)
        assertThat(context.resources.getResourceName(safetySourcesGroup.titleResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference_not_overlayable")
        assertThat(context.resources.getString(safetySourcesGroup.titleResId)).isEqualTo("Base")
        assertThat(safetySourcesGroup.summaryResId).isEqualTo(R.string.reference_overlayable)
        assertThat(context.resources.getResourceName(safetySourcesGroup.summaryResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference_overlayable")
        assertThat(context.resources.getString(safetySourcesGroup.summaryResId))
            .isEqualTo("Overlay")
        assertThat(safetySourcesGroup.safetySources.size).isEqualTo(1)
        val safetySource = safetySourcesGroup.safetySources[0]
        assertThat(safetySource.id).isEqualTo("Base")
        assertThat(safetySource.titleResId).isEqualTo(R.string.reference)
        assertThat(context.resources.getResourceName(safetySource.titleResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference")
        assertThat(context.resources.getString(safetySource.titleResId)).isEqualTo("Reference")
        // Note: a resource in the target package should not make assumptions on resources defined
        // exclusively in an ovelray package, but this is working as a byproduct of how runtime
        // resource resolution by resource name works.
        assertThat(context.resources.getResourceName(safetySource.summaryResId))
            .isEqualTo("$OVERLAY_PACKAGE:string/reference_overlay")
        assertThat(context.resources.getString(safetySource.summaryResId)).isEqualTo("Overlay")
    }

    @Test
    fun validOverlayableConfig_matchesExpected() = waitForSuccessWithTimeout {
        val inputStream = context.resources.openRawResource(R.raw.config_valid_overlayable)

        val safetyCenterConfig =
            SafetyCenterConfigParser.parseXmlResource(inputStream, context.resources)

        assertThat(safetyCenterConfig.safetySourcesGroups.size).isEqualTo(1)
        val safetySourcesGroup = safetyCenterConfig.safetySourcesGroups[0]
        assertThat(safetySourcesGroup.id).isEqualTo("Overlay")
        assertThat(safetySourcesGroup.titleResId).isEqualTo(R.string.reference_not_overlayable)
        assertThat(context.resources.getResourceName(safetySourcesGroup.titleResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference_not_overlayable")
        assertThat(context.resources.getString(safetySourcesGroup.titleResId)).isEqualTo("Base")
        assertThat(safetySourcesGroup.summaryResId).isEqualTo(R.string.reference_overlayable)
        assertThat(context.resources.getResourceName(safetySourcesGroup.summaryResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference_overlayable")
        assertThat(context.resources.getString(safetySourcesGroup.summaryResId))
            .isEqualTo("Overlay")
        assertThat(safetySourcesGroup.safetySources.size).isEqualTo(1)
        val safetySource = safetySourcesGroup.safetySources[0]
        assertThat(safetySource.id).isEqualTo("Overlay")
        assertThat(safetySource.titleResId).isEqualTo(R.string.reference)
        assertThat(context.resources.getResourceName(safetySource.titleResId))
            .isEqualTo("$TARGET_PACKAGE:string/reference")
        assertThat(context.resources.getString(safetySource.titleResId)).isEqualTo("Reference")
        assertThat(context.resources.getResourceName(safetySource.summaryResId))
            .isEqualTo("$OVERLAY_PACKAGE:string/reference_overlay")
        assertThat(context.resources.getString(safetySource.summaryResId)).isEqualTo("Overlay")
    }

    @Test
    fun invalidOverlayableConfig_StringResourceNameInvalid_throws() = waitForSuccessWithTimeout {
        val inputStream =
            context.resources.openRawResource(R.raw.config_string_resource_name_invalid_overlayable)

        val thrown =
            assertThrows(ParseException::class.java) {
                SafetyCenterConfigParser.parseXmlResource(inputStream, context.resources)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Resource name \"@com.android.safetycenter.config.tests:string/reference_overlay" +
                    "\" in static-safety-source.summary missing or invalid"
            )
    }

    companion object {
        private const val TARGET_PACKAGE = "com.android.safetycenter.config.tests"
        private const val OVERLAY_PACKAGE = "com.android.safetycenter.config.tests.overlay"
        private const val OVERLAY_PATH =
            "/data/local/tmp/com/safetycenter/config/tests/SafetyCenterConfigTestsOverlay.apk"
        private const val STATE_ENABLED = "STATE_ENABLED"

        private fun getStateForOverlay(overlayPackage: String): String? {
            val result: String =
                runShellCommand("cmd overlay dump --user 0 state $overlayPackage").lines().first()
            if (!result.startsWith("STATE_")) {
                return null
            }
            return result.trim()
        }

        @JvmStatic
        @BeforeClass
        fun install() {
            runShellCommand("pm install -r --force-sdk --force-queryable $OVERLAY_PATH")
            waitForWithTimeout { getStateForOverlay(OVERLAY_PACKAGE) != null }
            runShellCommand("cmd overlay enable --user 0 $OVERLAY_PACKAGE")
            waitForWithTimeout { getStateForOverlay(OVERLAY_PACKAGE) == STATE_ENABLED }
        }

        @JvmStatic
        @AfterClass
        fun uninstall() {
            runShellCommand("pm uninstall $OVERLAY_PACKAGE")
        }
    }
}
