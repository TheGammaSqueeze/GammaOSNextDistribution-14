/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_MANAGED_ACCOUNT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ProvisioningIntentProvider}.
 */
public class ProvisioningIntentProviderTest {

    private static final String DEVICE_ADMIN_PACKAGE_NAME = "com.example.package";
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            new ComponentName(DEVICE_ADMIN_PACKAGE_NAME, "com.android.AdminReceiver");
    private static final PersistableBundle ADMIN_EXTRAS_BUNDLE =
            PersistableBundle.forPair("test_key", "test_value");
    private static final UserHandle WORK_PROFILE_USER_HANDLE = UserHandle.of(10);
    private static final ProvisioningParams ADMIN_INTEGRATED_FLOW_PARAMS =
            new ProvisioningParams.Builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(DEVICE_ADMIN_COMPONENT_NAME)
                    .setAdminExtrasBundle(ADMIN_EXTRAS_BUNDLE)
                    .setStartedByTrustedSource(true)
                    .setFlowType(ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED)
                    .build();
    private static final ProvisioningParams LEGACY_FLOW_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminComponentName(DEVICE_ADMIN_COMPONENT_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setAdminExtrasBundle(ADMIN_EXTRAS_BUNDLE)
            .build();
    private static final ProvisioningParams LEGACY_FLOW_PARAMS_WITH_PROVISIONING_TRIGGER =
            new ProvisioningParams.Builder()
                    .setDeviceAdminComponentName(DEVICE_ADMIN_COMPONENT_NAME)
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                    .setAdminExtrasBundle(ADMIN_EXTRAS_BUNDLE)
                    .setProvisioningTrigger(PROVISIONING_TRIGGER_MANAGED_ACCOUNT)
                    .build();

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    @Mock private PolicyComplianceUtils mPolicyComplianceUtils;
    @Mock private SettingsFacade mSettingsFacade;
    @Mock private SharedPreferences mSharedPreferences;

    private final ProvisioningIntentProvider mProvisioningIntentProvider =
            new ProvisioningIntentProvider();
    private final Context mTargetContext = InstrumentationRegistry.getTargetContext();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mContext.getResources()).thenReturn(mTargetContext.getResources());
    }

    @Test
    public void maybeLaunchDpc_adminIntegratedFlow_policyComplianceActionLaunched() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(true);
        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();

        mProvisioningIntentProvider.maybeLaunchDpc(ADMIN_INTEGRATED_FLOW_PARAMS, 0, mUtils,
                mContext, mProvisioningAnalyticsTracker, policyComplianceUtils, mSettingsFacade);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), any());
        verify(mProvisioningAnalyticsTracker).logDpcSetupStarted(any(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(ACTION_ADMIN_POLICY_COMPLIANCE);
    }

    @Test
    public void maybeLaunchDpc_legacyFlow_provisioningSuccessfulLaunched() {
        makeProvisioningSuccessfulIntentResolvable();
        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();

        mProvisioningIntentProvider.maybeLaunchDpc(LEGACY_FLOW_PARAMS, 0, mUtils, mContext,
                mProvisioningAnalyticsTracker, policyComplianceUtils, mSettingsFacade);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), any());
        verify(mProvisioningAnalyticsTracker).logDpcSetupStarted(any(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(ACTION_PROVISIONING_SUCCESSFUL);
    }

    @Test
    public void maybeLaunchDpc_legacyFlow_duringSetupWizard_provisioningSuccessfulLaunched() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(true);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(false);
        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();

        mProvisioningIntentProvider.maybeLaunchDpc(LEGACY_FLOW_PARAMS, 0, mUtils, mContext,
                mProvisioningAnalyticsTracker, policyComplianceUtils, mSettingsFacade);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), any());
        verify(mProvisioningAnalyticsTracker).logDpcSetupStarted(any(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(ACTION_PROVISIONING_SUCCESSFUL);
    }

    @Test
    public void maybeLaunchDpc_legacyFlow_duringSetupWizard_policyComplianceResolvable_policyComplianceLaunched() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(true);
        when(mUtils.getManagedProfile(mContext)).thenReturn(WORK_PROFILE_USER_HANDLE);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();

        mProvisioningIntentProvider.maybeLaunchDpc(
                LEGACY_FLOW_PARAMS, WORK_PROFILE_USER_HANDLE.getIdentifier(), mUtils, mContext,
                mProvisioningAnalyticsTracker, policyComplianceUtils, mSettingsFacade);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), any());
        verify(mProvisioningAnalyticsTracker).logDpcSetupStarted(any(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(ACTION_ADMIN_POLICY_COMPLIANCE);
    }

    @Test
    public void maybeLaunchDpc_legacyFlow_policyComplianceResolvable_hasProvisioningTrigger_provisioningSuccessfulLaunched() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(true);
        when(mUtils.getManagedProfile(mContext)).thenReturn(WORK_PROFILE_USER_HANDLE);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();

        mProvisioningIntentProvider.maybeLaunchDpc(
                LEGACY_FLOW_PARAMS_WITH_PROVISIONING_TRIGGER,
                WORK_PROFILE_USER_HANDLE.getIdentifier(), mUtils, mContext,
                mProvisioningAnalyticsTracker, policyComplianceUtils, mSettingsFacade);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), any());
        verify(mProvisioningAnalyticsTracker).logDpcSetupStarted(any(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(ACTION_PROVISIONING_SUCCESSFUL);
    }

    @Test
    public void maybeLaunchDpc_cannotResolveIntent() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(false);

        mProvisioningIntentProvider.maybeLaunchDpc(LEGACY_FLOW_PARAMS, 0, mUtils, mContext,
                mProvisioningAnalyticsTracker, mPolicyComplianceUtils, mSettingsFacade);

        verify(mContext, never()).startActivityAsUser(any(), any());
        verify(mProvisioningAnalyticsTracker, never()).logDpcSetupStarted(any(), any());
    }

    @Test
    public void createProvisioningIntent_success() {
        Intent intent = mProvisioningIntentProvider
                .createProvisioningCompleteIntent(LEGACY_FLOW_PARAMS, 0, mUtils, mContext);

        assertThat(intent.getAction()).isEqualTo(ACTION_PROFILE_PROVISIONING_COMPLETE);
        assertThat(intent.getComponent()).isEqualTo(DEVICE_ADMIN_COMPONENT_NAME);
        assertThat(intent.<PersistableBundle>getParcelableExtra(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)).isEqualTo(ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void createProvisioningIntent_cannotFindAdmin()
            throws IllegalProvisioningArgumentException {
        ProvisioningParams provisioningParams = new ProvisioningParams.Builder()
                .setDeviceAdminPackageName(DEVICE_ADMIN_PACKAGE_NAME)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .build();
        when(mUtils.findDeviceAdmin(eq(DEVICE_ADMIN_PACKAGE_NAME), any(), any(), anyInt()))
                .thenThrow(IllegalProvisioningArgumentException.class);

        assertThat(mProvisioningIntentProvider.createProvisioningCompleteIntent(
                provisioningParams, 0, mUtils, mContext)).isNull();
    }

    private void makeProvisioningSuccessfulIntentResolvable() {
        when(mUtils.canResolveIntentAsUser(any(), any(Intent.class), anyInt()))
                .thenAnswer(invocation -> {
                    Intent intentArgument = invocation.getArgument(/* index= */ 1);
                    return intentArgument.getAction().equals(ACTION_PROVISIONING_SUCCESSFUL);
                });
    }
}
