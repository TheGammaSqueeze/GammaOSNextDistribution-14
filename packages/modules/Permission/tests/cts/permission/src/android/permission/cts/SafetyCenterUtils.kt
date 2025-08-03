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

package android.permission.cts

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.UserHandle
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueId
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import org.junit.Assert

object SafetyCenterUtils {
    /** Name of the flag that determines whether SafetyCenter is enabled. */
    const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Returns whether the device supports Safety Center. */
    @JvmStatic
    fun deviceSupportsSafetyCenter(context: Context): Boolean {
        return context.resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android")
        )
    }

    /** Enabled or disable Safety Center */
    @JvmStatic
    fun setSafetyCenterEnabled(enabled: Boolean) {
        setDeviceConfigPrivacyProperty(PROPERTY_SAFETY_CENTER_ENABLED, enabled.toString())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @JvmStatic
    fun startSafetyCenterActivity(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_SAFETY_CENTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    @JvmStatic
    fun assertSafetyCenterStarted() {
        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc("Security & privacy"))
    }

    @JvmStatic
    fun setDeviceConfigPrivacyProperty(
        propertyName: String,
        value: String,
        uiAutomation: UiAutomation = instrumentation.uiAutomation
    ) {
        runWithShellPermissionIdentity(uiAutomation) {
            val valueWasSet =
                DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    /* name = */ propertyName,
                    /* value = */ value,
                    /* makeDefault = */ false
                )
            check(valueWasSet) { "Could not set $propertyName to $value" }
        }
    }

    @JvmStatic
    fun deleteDeviceConfigPrivacyProperty(
        propertyName: String,
        uiAutomation: UiAutomation = instrumentation.uiAutomation
    ) {
        runWithShellPermissionIdentity(uiAutomation) {
            DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_PRIVACY, propertyName)
        }
    }

    @JvmStatic
    private fun getSafetyCenterIssues(
        automation: UiAutomation = instrumentation.uiAutomation
    ): List<SafetyCenterIssue> {
        val safetyCenterManager =
            instrumentation.targetContext.getSystemService(SafetyCenterManager::class.java)
        val issues = ArrayList<SafetyCenterIssue>()
        runWithShellPermissionIdentity(automation) {
            val safetyCenterData = safetyCenterManager!!.safetyCenterData
            issues.addAll(safetyCenterData.issues)
        }
        return issues
    }

    @JvmStatic
    fun assertSafetyCenterIssueExist(
        sourceId: String,
        issueId: String,
        issueTypeId: String,
        automation: UiAutomation = instrumentation.uiAutomation
    ) {
        val safetyCenterIssueId = safetyCenterIssueId(sourceId, issueId, issueTypeId)
        Assert.assertTrue(
            "Expect issues in safety center",
            getSafetyCenterIssues(automation).any { safetyCenterIssueId == it.id }
        )
    }

    @JvmStatic
    fun assertSafetyCenterIssueDoesNotExist(
        sourceId: String,
        issueId: String,
        issueTypeId: String,
        automation: UiAutomation = instrumentation.uiAutomation
    ) {
        val safetyCenterIssueId = safetyCenterIssueId(sourceId, issueId, issueTypeId)
        Assert.assertTrue(
            "Expect no issue in safety center",
            getSafetyCenterIssues(automation).none { safetyCenterIssueId == it.id }
        )
    }

    private fun safetyCenterIssueId(sourceId: String, sourceIssueId: String, issueTypeId: String) =
        SafetyCenterIds.encodeToString(
            SafetyCenterIssueId.newBuilder()
                .setSafetyCenterIssueKey(
                    SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceId)
                        .setSafetySourceIssueId(sourceIssueId)
                        .setUserId(UserHandle.myUserId())
                        .build()
                )
                .setIssueTypeId(issueTypeId)
                .build()
        )
}
