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

package android.safetycenter.lint.test

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.lint.FileSdk
import android.safetycenter.lint.ParserExceptionDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UnstableApiUsage")
@RunWith(JUnit4::class)
class ParserExceptionDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ParserExceptionDetector()

    override fun getIssues(): List<Issue> = listOf(ParserExceptionDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @After
    fun resetOverrides() {
        FileSdk.maxVersionOverride = null
    }

    @Test
    fun validMinimumConfig_doesNotThrow() {
        lint()
            .files(
                xml("res/raw/safety_center_config.xml", VALID_TIRAMISU_CONFIG),
                STRINGS_WITH_REFERENCE_XML_DEFAULT
            )
            .run()
            .expectClean()
    }

    @Test
    fun validFutureConfig_doesNotThrow() {
        lint()
            .files(
                xml("res/raw-99/safety_center_config.xml", VALID_TIRAMISU_CONFIG),
                STRINGS_WITH_REFERENCE_XML_DEFAULT
            )
            .run()
            .expectClean()
    }

    @Test
    fun invalidConfigWithSingleSchema_throwsOneError() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files(xml("res/raw-v34/safety_center_config.xml", "<invalid-root/>"))
            .run()
            .expect(
                "res/raw-v34/safety_center_config.xml: Error: Parser exception at sdk=34: " +
                    "\"Element safety-center-config missing\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun invalidConfigWithMultipleSchemas_throwsMultipleErrors() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files(xml("res/raw/safety_center_config.xml", "<invalid-root/>"))
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: Parser exception at sdk=33: " +
                    "\"Element safety-center-config missing\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\nres/raw/safety_center_config.xml: " +
                    "Error: Parser exception at sdk=34: " +
                    "\"Element safety-center-config missing\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\n2 errors, 0 warnings"
            )
    }

    @Test
    fun validUConfigWithUFields_throwsInT() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files(
                xml("res/raw/safety_center_config.xml", VALID_UDC_CONFIG),
                STRINGS_WITH_REFERENCE_XML_DEFAULT
            )
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: Parser exception at sdk=33: " +
                    "\"Unexpected attribute dynamic-safety-source.notificationsAllowed\", cause: " +
                    "\"null\" [InvalidSafetyCenterConfig]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun validTConfigWithUReference_throwsInT() {
        FileSdk.maxVersionOverride = TIRAMISU
        lint()
            .files(
                xml("res/raw/safety_center_config.xml", VALID_TIRAMISU_CONFIG),
                xml("res/values-v34/strings.xml", STRINGS_WITH_REFERENCE_XML),
            )
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: Parser exception at sdk=33: " +
                    "\"Resource name \"@lint.test.pkg:string/reference\" in " +
                    "safety-sources-group.title missing or invalid\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun validTConfigWithOtherLanguageReference_throwsInT() {
        FileSdk.maxVersionOverride = TIRAMISU
        lint()
            .files(
                xml("res/raw/safety_center_config.xml", VALID_TIRAMISU_CONFIG),
                xml("res/values-ar/strings.xml", STRINGS_WITH_REFERENCE_XML),
            )
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: Parser exception at sdk=33: " +
                    "\"Resource name \"@lint.test.pkg:string/reference\" in " +
                    "safety-sources-group.title missing or invalid\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun unrelatedFile_doesNotThrow() {
        lint().files(xml("res/raw/some_other_config.xml", "<some-other-root/>")).run().expectClean()
    }

    @Test
    fun unrelatedFolder_doesNotThrow() {
        lint().files(xml("res/values/strings.xml", "<some-other-root/>")).run().expectClean()
    }

    private companion object {
        const val STRINGS_WITH_REFERENCE_XML =
            """
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="reference" translatable="false">Reference</string>
</resources>
                """

        val STRINGS_WITH_REFERENCE_XML_DEFAULT: TestFile =
            xml("res/values/strings.xml", STRINGS_WITH_REFERENCE_XML)

        const val VALID_TIRAMISU_CONFIG =
            """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@lint.test.pkg:string/reference"
            summary="@lint.test.pkg:string/reference">
            <static-safety-source
                id="source"
                title="@lint.test.pkg:string/reference"
                summary="@lint.test.pkg:string/reference"
                intentAction="intent"
                profile="primary_profile_only"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
            """

        const val VALID_UDC_CONFIG =
            """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@lint.test.pkg:string/reference"
            summary="@lint.test.pkg:string/reference">
            <dynamic-safety-source
                id="source"
                packageName="package"
                title="@lint.test.pkg:string/reference"
                summary="@lint.test.pkg:string/reference"
                intentAction="intent"
                profile="primary_profile_only"
                notificationsAllowed="true"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
            """
    }
}
