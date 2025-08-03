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

package com.android.managedprovisioning.networkconnection;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_USE_MOBILE_DATA;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_DOMAIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_EAP_METHOD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PHASE2_AUTH;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;

import static com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.STATE_CONNECTED;
import static com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.STATE_CONNECTING;
import static com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.STATE_ERROR;
import static com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.STATE_IDLE;
import static com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.STATE_SHOW_NETWORK_PICKER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import com.android.managedprovisioning.testcommon.FakeSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

public final class EstablishNetworkConnectionViewModelTest {
    private EstablishNetworkConnectionViewModel mViewModel;
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_SSID = "\"test ssid\"";
    private static final String TEST_SECURITY_TYPE_EAP = "EAP";
    private static final String TEST_PROXY_HOST = "www.google.com";
    private static final int TEST_PROXY_PORT = 1234;
    private static final String TEST_PROXY_BYPASS = "www.android.com";
    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final Intent INVALID_INTENT =
            new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
    private static final int DIALOG_TITLE_RES_ID = 1;
    private static final int DIALOG_MESSAGE_RES_ID = 2;
    private static final String DIALOG_MESSAGE = "dialog message";
    private static final Intent INTENT =
            new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, ADMIN)
                    .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, ADMIN_PACKAGE)
                    .putExtra(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID)
                    .putExtra(EXTRA_PROVISIONING_WIFI_HIDDEN, true)
                    .putExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE_EAP)
                    .putExtra(EXTRA_PROVISIONING_WIFI_PASSWORD, "test password")
                    .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST)
                    .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT)
                    .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS)
                    .putExtra(EXTRA_PROVISIONING_WIFI_PAC_URL, "test pac url")
                    .putExtra(EXTRA_PROVISIONING_WIFI_EAP_METHOD, "PEAP")
                    .putExtra(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, "PAP")
                    .putExtra(EXTRA_PROVISIONING_WIFI_IDENTITY, "test identity")
                    .putExtra(EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, "test anonymous identity")
                    .putExtra(EXTRA_PROVISIONING_WIFI_DOMAIN, "test wifi domain")
                    .putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
    private static final ProvisioningParams EXPECTED_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setStartedByTrustedSource(true)
            .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
            .setAllowedProvisioningModes(new ArrayList<>(Arrays.asList(2, 1)))
            .setReturnBeforePolicyCompliance(true)
            .setProvisioningId(new ManagedProvisioningSharedPreferences(sContext)
                    .getProvisioningId() + 1)
            .setWifiInfo(new WifiInfo.Builder()
                    .setSsid(TEST_SSID)
                    .setHidden(true)
                    .setSecurityType(TEST_SECURITY_TYPE_EAP)
                    .setPassword("test password")
                    .setProxyHost(TEST_PROXY_HOST)
                    .setProxyPort(1234)
                    .setProxyBypassHosts(TEST_PROXY_BYPASS)
                    .setPacUrl("test pac url")
                    .setEapMethod("PEAP")
                    .setPhase2Auth("PAP")
                    .setIdentity("test identity")
                    .setAnonymousIdentity("test anonymous identity")
                    .setDomain("test wifi domain")
                    .build())
            .setUseMobileData(true)
            .build();
    private static final ProvisioningParams PARAMS_WITHOUT_NETWORK_DATA =
            new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setStartedByTrustedSource(true)
            .build();
    private static final ProvisioningParams PARAMS = EXPECTED_PARAMS;
    @Mock
    private Utils mUtils;
    private final Instrumentation mInstrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    private final FakeSharedPreferences mSharedPreferences = new FakeSharedPreferences();

    @Before
    public void setUp() throws IllegalProvisioningArgumentException {
        MockitoAnnotations.initMocks(this);
        when(mUtils.findDeviceAdmin(anyString(), any(), any(), anyInt())).thenReturn(ADMIN);
        when(mUtils.containsBinaryFlags(anyInt(), eq(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)))
                .thenReturn(true);
        mViewModel = new EstablishNetworkConnectionViewModel(
                mUtils, new SettingsFacade(), mSharedPreferences);
    }

    @Test
    public void constructor_initialStateIdle() {
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_IDLE);
    }

    @Test
    public void connectToNetwork_updatesSharedPreference() {
        mViewModel.connectToNetwork(mInstrumentation.getContext(), PARAMS);
        blockUntilNextUiThreadCycle();

        assertThat(mSharedPreferences.isEstablishNetworkConnectionRun()).isTrue();
    }

    @Test
    public void connectToNetwork_defaultSharedPreferenceIsFalse() {
        assertThat(mSharedPreferences.isEstablishNetworkConnectionRun()).isFalse();
    }

    @Test
    public void connectToNetwork_goesToConnectingState() {
        mViewModel.connectToNetwork(mInstrumentation.getContext(), PARAMS);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_CONNECTING);
    }

    @Test
    public void connectToNetwork_noNetworkDataProvided_goesToPickNetworkState() {
        mViewModel.connectToNetwork(
                mInstrumentation.getContext(), PARAMS_WITHOUT_NETWORK_DATA);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_SHOW_NETWORK_PICKER);
    }

    @Test
    public void provisioningTasksCompleted_goesToConnectedState() {
        mViewModel.provisioningTasksCompleted();
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_CONNECTED);
    }

    @Test
    public void error_withMessageResId_goesToErrorState() {
        mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID, false);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_ERROR);
    }

    @Test
    public void error_withMessageText_goesToErrorState() {
        mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE, false);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_ERROR);
    }


    @Test
    public void getError_withMessageResId_works() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID,
                            /* factoryResetRequired= */ true);

                    assertThat(mViewModel.getError().dialogTitleId).isEqualTo(DIALOG_TITLE_RES_ID);
                    assertThat(mViewModel.getError().errorMessageResId)
                            .isEqualTo(DIALOG_MESSAGE_RES_ID);
                    assertThat(mViewModel.getError().factoryResetRequired).isTrue();
                });
    }

    @Test
    public void getError_errorStateFollowedByNonErrorState_isNull() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID,
                            /* factoryResetRequired= */ true);
                    mViewModel.connectToNetwork(mInstrumentation.getContext(), PARAMS);

                    assertThat(mViewModel.getError()).isNull();
                });
    }

    @Test
    public void getError_withMessageText_isNull() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE,
                            /* factoryResetRequired= */ true);

                    assertThat(mViewModel.getError()).isNull();
                });
    }

    @Test
    public void getError_nullByDefault() {
        assertThat(mViewModel.getError()).isNull();
    }

    @Test
    public void parseExtras_works() {
        ProvisioningParams provisioningParams = mViewModel.parseExtras(sContext, INTENT);

        assertThat(provisioningParams).isEqualTo(EXPECTED_PARAMS);
    }

    @Test
    public void parseExtras_invalidIntent_returnsNull() {
        ProvisioningParams provisioningParams = mViewModel.parseExtras(sContext, INVALID_INTENT);

        assertThat(provisioningParams).isNull();
    }

    @Test
    public void parseExtras_nullContext_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mViewModel.parseExtras(/* context= */ null, INTENT));
    }

    @Test
    public void parseExtras_nullIntent_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mViewModel.parseExtras(sContext, /* intent= */ null));
    }

    private void blockUntilNextUiThreadCycle() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> {});
    }
}
