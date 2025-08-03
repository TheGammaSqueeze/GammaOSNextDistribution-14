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

package android.safetycenter.functional.ui

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID
import android.safetycenter.config.SafetySource
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.openPageAndExit
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.PRIVACY_SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.MORE_ISSUES_LABEL
import com.android.safetycenter.testing.UiTestHelper.clickMoreIssuesCard
import com.android.safetycenter.testing.UiTestHelper.resetRotation
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitAllTextNotDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitButtonDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueNotDisplayed
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for the Privacy subpage in Safety Center. */
@RunWith(AndroidJUnit4::class)
class PrivacySubpageTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val sensorPrivacyManager: SensorPrivacyManager =
        context.getSystemService(SensorPrivacyManager::class.java)!!

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @Before
    fun enableSafetyCenterBeforeTest() {
        SafetyCenterFlags.showSubpages = true
    }

    @After
    fun clearDataAfterTest() {
        UiAutomatorUtils2.getUiDevice().resetRotation()
    }

    @Test
    fun privacySubpage_openWithIntentExtra_showsSubpageData() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()
        val firstSource: SafetySource = sourcesGroup.safetySources.first()
        val lastSource: SafetySource = sourcesGroup.safetySources.last()
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, sourcesGroup.id)

        context.launchSafetyCenterActivity(extras) {
            waitAllTextDisplayed(
                context.getString(firstSource.titleResId),
                context.getString(firstSource.summaryResId),
                "Controls",
                context.getString(lastSource.titleResId),
                context.getString(lastSource.summaryResId)
            )
        }
    }

    @Test
    fun privacySubpage_clickingOnEntry_redirectsToDifferentScreen() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, sourcesGroup.id)

        context.launchSafetyCenterActivity(extras) {
            waitDisplayed(By.text(context.getString(source.titleResId))) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitAllTextDisplayed(
                context.getString(source.titleResId),
                context.getString(source.summaryResId)
            )
        }
    }

    @Test
    fun privacySubpage_withMultipleIssues_displaysExpectedWarningCards() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        safetyCenterTestHelper.setConfig(config)
        val firstSourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        val secondSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle
        safetyCenterTestHelper.setData(PRIVACY_SOURCE_ID_1, firstSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_1, secondSourceData)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, config.safetySourcesGroups.first().id)

        context.launchSafetyCenterActivity(extras) {
            waitSourceIssueDisplayed(firstSourceData.issues[0])
            waitAllTextDisplayed(MORE_ISSUES_LABEL)
            waitSourceIssueNotDisplayed(secondSourceData.issues[0])

            clickMoreIssuesCard()

            waitSourceIssueDisplayed(firstSourceData.issues[0])
            waitAllTextDisplayed(MORE_ISSUES_LABEL)
            waitSourceIssueDisplayed(secondSourceData.issues[0])
        }
    }

    @Test
    fun privacySubpage_openWithIntentExtra_showsPrivacyControls() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        safetyCenterTestHelper.setConfig(config)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, config.safetySourcesGroups.first().id)

        context.launchSafetyCenterActivity(extras) {
            waitAllText(
                displayed = sensorPrivacyManager.supportsSensorToggle(CAMERA),
                text = "Camera access"
            )
            waitAllText(
                displayed = sensorPrivacyManager.supportsSensorToggle(MICROPHONE),
                text = "Microphone access"
            )
            waitAllTextDisplayed("Show clipboard access")
            waitAllText(
                displayed = getPermissionControllerBool("config_display_show_password_toggle"),
                text = "Show passwords"
            )
            waitAllTextDisplayed("Location access")
        }
    }

    @Test
    fun privacySubpage_clickingOnLocationEntry_redirectsToLocationScreen() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, sourcesGroup.id)

        context.launchSafetyCenterActivity(extras) {
            openPageAndExit("Location access") {
                waitPageTitleDisplayed("Location")
                waitAllTextDisplayed("Use location")
            }

            waitAllTextDisplayed(
                context.getString(source.titleResId),
                context.getString(source.summaryResId)
            )
        }
    }

    @Test
    fun settingsSearch_openWithPrivacyIntentExtra_showsPrivacySubpage() {
        val config = safetyCenterTestConfigs.privacySubpageConfig
        val sourcesGroup = config.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()
        safetyCenterTestHelper.setConfig(config)
        val extras = Bundle()
        extras.putString(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY, "privacy_camera_toggle")

        context.launchSafetyCenterActivity(extras) {
            waitAllTextDisplayed(
                context.getString(source.titleResId),
                context.getString(source.summaryResId),
                "Controls",
            )
        }
    }

    private fun waitAllText(displayed: Boolean, text: String) {
        if (displayed) {
            waitAllTextDisplayed(text)
        } else {
            waitAllTextNotDisplayed(text)
        }
    }

    private fun getPermissionControllerBool(resourceName: String): Boolean {
        val permissionControllerContext = getPermissionControllerContext()
        val resourceId =
            permissionControllerContext.resources.getIdentifier(
                resourceName,
                "bool",
                "com.android.permissioncontroller"
            )
        return permissionControllerContext.resources.getBoolean(resourceId)
    }

    private fun getPermissionControllerContext(): Context {
        val permissionControllerPkg = context.packageManager.permissionControllerPackageName
        try {
            return context.createPackageContext(permissionControllerPkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private const val EXTRA_SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    }
}
