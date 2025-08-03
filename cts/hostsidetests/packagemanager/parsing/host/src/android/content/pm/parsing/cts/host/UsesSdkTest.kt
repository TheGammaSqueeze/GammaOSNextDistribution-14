/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.pm.parsing.cts.host

import android.content.pm.parsing.cts.generator.api.AndroidManifestXml
import android.content.pm.parsing.cts.generator.api.ApkGenerator
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class UsesSdkTest : BaseHostJUnit4Test() {

    companion object {

        @JvmField
        @ClassRule
        val tempFolder = TemporaryFolder()

        @JvmStatic
        @BeforeClass
        fun preloadApks() {
            ApkGenerator.initialize(tempFolder)
        }
    }

    @Before
    @After
    fun uninstall() {
        ApkGenerator.uninstallAll(device, tempFolder)
    }

    @Test
    fun defaultsNoTag() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 0)
    }

    @Test
    fun defaultsWithTag() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest>
                <uses-sdk/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 1)
    }

    @Test
    fun missingMinDefaults() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:targetSdkVersion="29"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 29)
    }

    @Test
    fun missingTargetCoercedToMin() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="29"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 29, target = 29)
    }

    @Test
    fun takeAndroidTargetSdk() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk targetSdkVersion="27" android:targetSdkVersion="27">
                    <extension-sdk android:sdkVersion="30" android:minExtensionVersion="0"/>
                </uses-sdk>
                <uses-sdk android:targetSdkVersion="29" targetSdkVersion="27">
                    <extension-sdk android:sdkVersion="31" android:minExtensionVersion="0"/>
                </uses-sdk>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, target = 29, extensions = mapOf(31 to 0))
    }

    @Test
    fun emptyUsesSdk() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:targetSdkVersion="29">
                    <extension-sdk android:sdkVersion="30" android:minExtensionVersion="0"/>
                </uses-sdk>
                <uses-sdk targetSdkVersion="27"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 1, extensions = emptyMap())
    }

    @Test
    fun takeLastTargetSdk() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:targetSdkVersion="28"/>
                <uses-sdk android:targetSdkVersion="29"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, target = 29)
    }

    @Test
    fun takeLastMinSdk() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="28"/>
                <uses-sdk android:minSdkVersion="29"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        // Target gets coerced to 29 since it's not possible to target below min
        assertSdks(result, min = 29, target = 29)
    }

    @Test
    fun takeLastExtensionSdk() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk>
                    <extension-sdk android:sdkVersion="30" android:minExtensionVersion="0"/>
                </uses-sdk>
                <uses-sdk>
                    <extension-sdk android:sdkVersion="31" android:minExtensionVersion="0"/>
                </uses-sdk>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 1, extensions = mapOf(31 to 0))
    }

    @Test
    fun lastDeclarationOverridesAllPrevious() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="21">
                    <extension-sdk android:sdkVersion="30" android:minExtensionVersion="0"/>
                </uses-sdk>
                <uses-sdk android:targetSdkVersion="30"/>
                <application/>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 1, target = 30, extensions = emptyMap())
    }

    @Test
    fun takeLastAfterAppTag() {
        // language=XML
        @AndroidManifestXml
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="29">
                    <extension-sdk android:sdkVersion="30" android:minExtensionVersion="0"/>
                </uses-sdk>
                <application/>
                <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="30">
                    <extension-sdk android:sdkVersion="31" android:minExtensionVersion="0"/>
                </uses-sdk>
            </manifest>
        """
        val result = ApkGenerator.install(device, xml, tempFolder)
        assertThat(result.error).isEmpty()
        assertSdks(result, min = 30, target = 30, extensions = mapOf(31 to 0))
    }

    private fun assertSdks(
        result: ApkGenerator.InstallResult,
        min: Int = 1,
        target: Int = 1,
        extensions: Map<Int, Int> = emptyMap()
    ) {
        val output = device.executeShellCommand("dumpsys package ${result.packageName}")
        assertThat(output).contains("minSdk=$min")
        assertThat(output).contains("targetSdk=$target")

        val minExtensionVersions = output.lineSequence()
            .dropWhile { !it.startsWith("Packages:") }
            .map { it.trim() }
            .first { it.startsWith("minExtensionVersions=") }
            .removePrefix("minExtensionVersions=[")
            .removeSuffix("]")
            .trim()
            .takeIf(String::isNotEmpty)
            ?.split(",")
            ?.associate {
                it.split("=")
                    .map(String::trim)
                    .map(String::toInt)
                    .let { it.first() to it.last() }
            }
            ?: emptyMap()

        assertWithMessage(output).that(minExtensionVersions).containsExactlyEntriesIn(extensions)
    }
}
