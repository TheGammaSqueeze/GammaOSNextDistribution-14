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
package com.android.managedprovisioning.common

import android.app.Activity
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import com.android.managedprovisioning.analytics.MetricsWriterFactory
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker
import com.android.managedprovisioning.finalization.SendDpcBroadcastService
import com.android.managedprovisioning.model.ProvisioningParams
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PolicyComplianceUtilsTest {
    private val mContext = ApplicationProvider.getApplicationContext<Context>()
    private val mPolicyComplianceUtils = PolicyComplianceUtils()
    private val mUtils = Utils()
    private val mManagedProvisioningSharedPreferences = ManagedProvisioningSharedPreferences(mContext)
    private val mProvisioningAnalyticsTracker = ProvisioningAnalyticsTracker(
            MetricsWriterFactory.getMetricsWriter(mContext, SettingsFacade()),
            mManagedProvisioningSharedPreferences)
    private val mTransitionHelper = TransitionHelper()

    @Test
    fun isPolicyComplianceActivityResolvableForSystemUser_activityExists_returnsTrue(): Unit {
        val intent = createPolicyComplianceIntent()
        shadowOf(mContext.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
        val result = mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForUser(
                mContext,
                createTrustedSourceParamsBuilder().build(),
                mUtils,
                UserHandle.SYSTEM)
        assertThat(result).isTrue()
    }

    @Test
    fun isPolicyComplianceActivityResolvableForSystemUser_activityExists_returnsFalse(): Unit {
        val result = mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForUser(
                mContext,
                createTrustedSourceParamsBuilder().build(),
                mUtils,
                UserHandle.SYSTEM)
        assertThat(result).isFalse()
    }

    @Ignore("b/218480743")
    @Test
    fun startPolicyComplianceActivityForResultIfResolved_activityExists_isStarted() {
        val intent = createPolicyComplianceIntent()
        shadowOf(mContext.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
        val parentActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val result = mPolicyComplianceUtils.startPolicyComplianceActivityForResultIfResolved(
                parentActivity,
                createTrustedSourceParamsBuilder().build(),
                POLICY_COMPLIANCE_ACTIVITY_REQUEST_CODE,
                mUtils,
                mProvisioningAnalyticsTracker,
                mTransitionHelper)
        val startedIntent = shadowOf(parentActivity).peekNextStartedActivity()
        assertThat(startedIntent.action).isEqualTo(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE)
        assertThat(startedIntent.getPackage()).isEqualTo(TEST_MDM_PACKAGE_NAME)
        assertThat(startedIntent.flags).isEqualTo(NO_FLAGS)
        assertThat(result).isTrue()
    }

    @Ignore("b/218480743")
    @Test
    fun startPolicyComplianceActivityForResultIfResolved_activityDoesNotExist_notStarted() {
        val parentActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val result = mPolicyComplianceUtils.startPolicyComplianceActivityForResultIfResolved(
                parentActivity,
                createTrustedSourceParamsBuilder().build(),
                POLICY_COMPLIANCE_ACTIVITY_REQUEST_CODE,
                mUtils,
                mProvisioningAnalyticsTracker,
                mTransitionHelper)
        val startedIntent = shadowOf(parentActivity).peekNextStartedActivity()
        assertThat(startedIntent).isNull()
        assertThat(result).isFalse()
    }

    @Ignore("b/218480743")
    @Test
    fun startPolicyComplianceActivityIfResolved_activityExists_isStarted() {
        val intent = createPolicyComplianceIntent()
        shadowOf(mContext.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
        val parentActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val result = mPolicyComplianceUtils.startPolicyComplianceActivityIfResolved(
                parentActivity,
                createTrustedSourceParamsBuilder().build(),
                mUtils,
                mProvisioningAnalyticsTracker)
        val startedIntent = shadowOf(parentActivity).peekNextStartedActivity()
        assertThat(startedIntent.action).isEqualTo(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE)
        assertThat(startedIntent.getPackage()).isEqualTo(TEST_MDM_PACKAGE_NAME)
        assertThat(startedIntent.flags).isEqualTo(NO_FLAGS)
        assertThat(result).isTrue()
    }

    @Ignore("b/218480743")
    @Test
    fun startPolicyComplianceActivityIfResolved_activityDoesNotExist_notStarted() {
        val parentActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val result = mPolicyComplianceUtils.startPolicyComplianceActivityIfResolved(
                parentActivity,
                createTrustedSourceParamsBuilder().build(),
                mUtils,
                mProvisioningAnalyticsTracker)
        val startedIntent = shadowOf(parentActivity).peekNextStartedActivity()
        assertThat(startedIntent).isNull()
        assertThat(result).isFalse()
    }

    @Test
    fun startPolicyComplianceActivityIfResolved_fromNonActivityContext_isStartedWithNewTaskFlag() {
        val intent = createPolicyComplianceIntent()
        shadowOf(mContext.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
        val service: Service = Robolectric.buildService(SendDpcBroadcastService::class.java).create().get()
        val result = mPolicyComplianceUtils.startPolicyComplianceActivityIfResolved(
                service,
                createTrustedSourceParamsBuilder().build(),
                mUtils,
                mProvisioningAnalyticsTracker)
        val startedIntent = shadowOf(service).peekNextStartedActivity()
        assertThat(startedIntent.action).isEqualTo(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE)
        assertThat(startedIntent.getPackage()).isEqualTo(TEST_MDM_PACKAGE_NAME)
        assertThat(startedIntent.flags).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(result).isTrue()
    }

    @Test
    fun isPolicyComplianceActivityResolvableForManagedUser_withSystemUser_activityExists_returnsTrue(): Unit {
        val intent = createPolicyComplianceIntent()
        shadowOf(mContext.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
        val result = mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForManagedUser(
                mContext,
                createTrustedSourceParamsBuilder().build(),
                mUtils)
        assertThat(result).isTrue()
    }

    @Test
    fun isPolicyComplianceActivityResolvableForManagedUser_withSystemUser_activityDoesNotExist_returnsFalse() {
        val result = mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForManagedUser(
                mContext,
                createTrustedSourceParamsBuilder().build(),
                mUtils)
        assertThat(result).isFalse()
    }

    private fun createPolicyComplianceIntent(): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE)
        intent.setPackage(TEST_MDM_PACKAGE_NAME)
        return intent
    }

    companion object {
        private const val TEST_MDM_PACKAGE_NAME = "mdm.package.name"
        private const val TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver"
        private val TEST_MDM_ADMIN = ComponentName(TEST_MDM_PACKAGE_NAME,
                TEST_MDM_ADMIN_RECEIVER)
        private const val POLICY_COMPLIANCE_ACTIVITY_REQUEST_CODE = 123
        private const val NO_FLAGS = 0
        private fun createTrustedSourceParamsBuilder(): ProvisioningParams.Builder {
            return ProvisioningParams.Builder.builder()
                    .setProvisioningAction(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(TEST_MDM_ADMIN)
                    .setStartedByTrustedSource(true)
        }
    }
}
