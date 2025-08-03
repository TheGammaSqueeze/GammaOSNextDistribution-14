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

package com.android.safetycenter.testing

import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.UserManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags.isSafetyCenterEnabled
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.google.common.util.concurrent.MoreExecutors.directExecutor

/** A class that facilitates settings up Safety Center in tests. */
@RequiresApi(TIRAMISU)
class SafetyCenterTestHelper(val context: Context) {

    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val userManager = context.getSystemService(UserManager::class.java)!!
    private val listeners = mutableListOf<SafetyCenterTestListener>()

    /**
     * Sets up the state of Safety Center by enabling it on the device and setting default flag
     * values. To be called before each test.
     */
    fun setup() {
        Log.d(TAG, "setup")
        Coroutines.enableDebugging()
        SafetySourceReceiver.setup()
        TestActivity.enableHighPriorityAlias()
        SafetyCenterFlags.setup()
        setEnabled(true)
    }

    /** Resets the state of Safety Center. To be called after each test. */
    fun reset() {
        Log.d(TAG, "reset")
        setEnabled(true)
        listeners.forEach {
            safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(it)
            it.cancel()
        }
        listeners.clear()
        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()
        resetFlags()
        TestActivity.disableHighPriorityAlias()
        SafetySourceReceiver.reset()
        Coroutines.resetDebugging()
    }

    /** Enables or disables SafetyCenter based on [value]. */
    fun setEnabled(value: Boolean) {
        Log.d(TAG, "setEnabled to $value")
        val safetyCenterConfig = safetyCenterManager.getSafetyCenterConfigWithPermission()
        if (safetyCenterConfig == null) {
            // No broadcasts are dispatched when toggling the flag when SafetyCenter is not
            // supported by the device. In this case, toggling this flag should end up being a no-op
            // as Safety Center will remain disabled regardless, but we still toggle it so that this
            // no-op behavior can be covered.
            SafetyCenterFlags.isEnabled = value
            return
        }
        if (value == isEnabled()) {
            Log.d(TAG, "isEnabled is already $value")
            return
        }
        setEnabledWaitingForSafetyCenterBroadcastIdle(value, safetyCenterConfig)
    }

    /** Sets the given [SafetyCenterConfig]. */
    fun setConfig(config: SafetyCenterConfig) {
        Log.d(TAG, "setConfig")
        require(isEnabled())
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(config)
    }

    /**
     * Adds and returns a [SafetyCenterTestListener] to SafetyCenter.
     *
     * @param skipInitialData whether the returned [SafetyCenterTestListener] should receive the
     *   initial SafetyCenter update
     */
    fun addListener(skipInitialData: Boolean = true): SafetyCenterTestListener {
        Log.d(TAG, "addListener")
        require(isEnabled())
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        if (skipInitialData) {
            listener.receiveSafetyCenterData()
        }
        listeners.add(listener)
        return listener
    }

    /** Sets the [SafetySourceData] for the given [safetySourceId]. */
    fun setData(
        safetySourceId: String,
        safetySourceData: SafetySourceData?,
        safetyEvent: SafetyEvent = EVENT_SOURCE_STATE_CHANGED
    ) {
        Log.d(TAG, "setData for $safetySourceId")
        require(isEnabled())
        safetyCenterManager.setSafetySourceDataWithPermission(
            safetySourceId,
            safetySourceData,
            safetyEvent
        )
    }

    /** Dismisses the [SafetyCenterIssue] for the given [safetyCenterIssueId]. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun dismissSafetyCenterIssue(safetyCenterIssueId: String) {
        Log.d(TAG, "dismissSafetyCenterIssue")
        require(isEnabled())
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(safetyCenterIssueId)
    }

    private fun resetFlags() {
        setEnabled(SafetyCenterFlags.snapshot.isSafetyCenterEnabled())
        SafetyCenterFlags.reset()
    }

    private fun setEnabledWaitingForSafetyCenterBroadcastIdle(
        value: Boolean,
        safetyCenterConfig: SafetyCenterConfig
    ) =
        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE, READ_SAFETY_CENTER_STATUS) {
            val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)
            SafetyCenterFlags.isEnabled = value
            // Wait for all ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcasts to be dispatched to
            // avoid them leaking onto other tests.
            if (safetyCenterConfig.containsTestSource()) {
                Log.d(TAG, "Waiting for test source enabled changed broadcast")
                SafetySourceReceiver.receiveSafetyCenterEnabledChanged()
                // The explicit ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcast is also sent to the
                // dynamically registered receivers.
                enabledChangedReceiver.receiveSafetyCenterEnabledChanged()
            }
            // Wait for the implicit ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcast. This is because
            // the test config could later be set in another test. Since broadcasts are dispatched
            // asynchronously, a wrong sequencing could still cause failures (e.g: 1: flag switched,
            // 2: test finishes, 3: new test starts, 4: a test config is set, 5: broadcast from 1
            // dispatched).
            if (userManager.isSystemUser) {
                Log.d(TAG, "Waiting for system enabled changed broadcast")
                // The implicit broadcast is only sent to the system user.
                enabledChangedReceiver.receiveSafetyCenterEnabledChanged()
            }
            enabledChangedReceiver.unregister()
            // NOTE: We could be using ActivityManager#waitForBroadcastIdle() to achieve the same
            // thing.
            // However:
            // 1. We can't solely rely on this call to wait for the
            // ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcasts to be dispatched, as the DeviceConfig
            // listener is called on a background thread (so waitForBroadcastIdle() could
            // immediately return prior to the listener being called)
            // 2. waitForBroadcastIdle() sleeps 1s in a loop when the broadcast queue is not empty,
            // which would slow down our tests significantly
        }

    private fun SafetyCenterConfig.containsTestSource(): Boolean =
        safetySourcesGroups
            .flatMap { it.safetySources }
            .filter { it.type != SAFETY_SOURCE_TYPE_STATIC }
            .any { it.packageName == context.packageName }

    private fun isEnabled() = safetyCenterManager.isSafetyCenterEnabledWithPermission()

    private companion object {
        const val TAG: String = "SafetyCenterTestHelper"
    }
}
