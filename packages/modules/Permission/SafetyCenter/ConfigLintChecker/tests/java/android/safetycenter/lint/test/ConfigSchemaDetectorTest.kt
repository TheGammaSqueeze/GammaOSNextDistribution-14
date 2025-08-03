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

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.lint.ConfigSchemaDetector
import android.safetycenter.lint.FileSdk
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UnstableApiUsage")
@RunWith(JUnit4::class)
class ConfigSchemaDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ConfigSchemaDetector()

    override fun getIssues(): List<Issue> = listOf(ConfigSchemaDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @After
    fun resetOverrides() {
        FileSdk.maxVersionOverride = null
    }

    @Test
    fun validMinimumConfig_doesNotThrow() {
        lint()
            .files(
                (xml(
                    "res/raw/safety_center_config.xml",
                    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@package:string/reference"
            summary="@package:string/reference">
            <static-safety-source
                id="source"
                title="@package:string/reference"
                summary="@package:string/reference"
                intentAction="intent"
                profile="primary_profile_only"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
                    """
                ))
            )
            .run()
            .expectClean()
    }

    @Test
    fun validFutureConfig_doesNotThrow() {
        lint()
            .files(
                (xml(
                    "res/raw-99/safety_center_config.xml",
                    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@package:string/reference"
            summary="@package:string/reference">
            <static-safety-source
                id="source"
                title="@package:string/reference"
                summary="@package:string/reference"
                intentAction="intent"
                profile="primary_profile_only"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
                    """
                ))
            )
            .run()
            .expectClean()
    }

    @Test
    fun invalidConfigWithSingleSchema_throwsOneError() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files((xml("res/raw-v34/safety_center_config.xml", "<invalid-root/>")))
            .run()
            .expect(
                "res/raw-v34/safety_center_config.xml: Error: SAXException exception at sdk=34: " +
                    "\"cvc-elt.1.a: Cannot find the declaration of element 'invalid-root'.\" " +
                    "[InvalidSafetyCenterConfigSchema]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun invalidConfigWithMultipleSchemas_throwsMultipleErrors() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files((xml("res/raw/safety_center_config.xml", "<invalid-root/>")))
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: SAXException exception at sdk=33: " +
                    "\"cvc-elt.1.a: Cannot find the declaration of element 'invalid-root'.\" " +
                    "[InvalidSafetyCenterConfigSchema]\nres/raw/safety_center_config.xml: " +
                    "Error: SAXException exception at sdk=34: \"cvc-elt.1.a: Cannot find the " +
                    "declaration of element 'invalid-root'.\" " +
                    "[InvalidSafetyCenterConfigSchema]\n2 errors, 0 warnings"
            )
    }

    @Test
    fun validUConfigWithUFields_throwsInT() {
        FileSdk.maxVersionOverride = UPSIDE_DOWN_CAKE
        lint()
            .files(
                (xml(
                    "res/raw/safety_center_config.xml",
                    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@package:string/reference"
            summary="@package:string/reference">
            <dynamic-safety-source
                id="source"
                packageName="package"
                title="@package:string/reference"
                summary="@package:string/reference"
                intentAction="intent"
                profile="primary_profile_only"
                notificationsAllowed="true"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
                    """
                ))
            )
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: SAXException exception at sdk=33: " +
                    "\"cvc-complex-type.3.2.2: Attribute 'notificationsAllowed' is not allowed " +
                    "to appear in element 'dynamic-safety-source'.\" " +
                    "[InvalidSafetyCenterConfigSchema]\n1 errors, 0 warnings"
            )
    }

    @Test
    fun unrelatedFile_doesNotThrow() {
        lint()
            .files((xml("res/raw/some_other_config.xml", "<some-other-root/>")))
            .run()
            .expectClean()
    }

    @Test
    fun unrelatedFolder_doesNotThrow() {
        lint().files((xml("res/values/strings.xml", "<some-other-root/>"))).run().expectClean()
    }
}
