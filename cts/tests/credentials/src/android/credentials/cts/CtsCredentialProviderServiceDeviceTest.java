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

package android.credentials.cts;

import static android.credentials.flags.Flags.FLAG_SETTINGS_ACTIVITY_ENABLED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.credentials.ClearCredentialStateException;
import android.credentials.ClearCredentialStateRequest;
import android.credentials.CreateCredentialException;
import android.credentials.CreateCredentialRequest;
import android.credentials.CreateCredentialResponse;
import android.credentials.CredentialManager;
import android.credentials.CredentialOption;
import android.credentials.CredentialProviderInfo;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.PrepareGetCredentialResponse;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.Timeout;
import com.android.compatibility.common.util.UserSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CtsCredentialProviderServiceDeviceTest {
    public static final String CTS_PACKAGE_NAME =
            CtsNoOpCredentialProviderService.class.getPackage().getName();
    private static final String CTS_SERVICE_NAME =
            CTS_PACKAGE_NAME
                    + "/"
                    + CTS_PACKAGE_NAME
                    + "."
                    + CtsNoOpCredentialProviderService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final String TAG = "CtsCredentialProviderServiceDeviceTest";
    private static final String PASSWORD_CREDENTIAL_TYPE =
            "android.credentials.TYPE_PASSWORD_CREDENTIAL";
    private static final String PASSKEY_CREDENTIAL_TYPE =
            "android.credentials.TYPE_PUBLIC_KEY_CREDENTIAL";
    private static final String CREDENTIAL_SERVICE = "credential_service";
    private static final String CREDENTIAL_SERVICE_PRIMARY = "credential_service_primary";
    private static final Timeout CONNECTION_TIMEOUT =
            new Timeout("CONNECTION_TIMEOUT", 1500, 2F, 1500);
    private static final UserHandle USER_ID_HANDLE = UserHandle.getUserHandleForUid(USER_ID);
    private static final int TEMPORARY_SERVICE_DURATION = 10000;
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";
    private static final String NOOP_SERVICE =
            "android.credentials.cts/android.credentials.cts.CtsNoOpCredentialProviderService";
    private static final String NOOP_SERVICE_ALT =
            "android.credentials.cts/android.credentials.cts.CtsNoOpCredentialProviderAltService";
    private static final String NOOP_SERVICE_SYSTEM =
            "android.credentials.cts/android.credentials.cts.CtsNoOpCredentialProviderSysService";
    private static final List<String> CREDENTIAL_TYPES =
            Arrays.asList(PASSKEY_CREDENTIAL_TYPE, PASSWORD_CREDENTIAL_TYPE);
    private static final List<String> PASSKEY_CREDENTIAL_TYPE_LIST =
            Arrays.asList(PASSKEY_CREDENTIAL_TYPE);
    private static final List<String> PASSWORD_CREDENTIAL_TYPE_LIST =
            Arrays.asList(PASSWORD_CREDENTIAL_TYPE);
    private static final String PROVIDER_LABEL = "Test Provider Service";
    private static final String PROVIDER_LABEL_ALT = "Test Provider Service Alternate";
    private static final String PROVIDER_LABEL_SYSTEM = "Test Provider Service System";
    private static final String PRIMARY_SETTINGS_INTENT = "android.settings.CREDENTIAL_PROVIDER";
    private static final String SECONDARY_SETTINGS_INTENT = "android.settings.SYNC_SETTINGS";
    private static final String CLASS_NAME = "android.credentials.cts.CtsNoOpCredentialProviderService";

    private CredentialManager mCredentialManager;
    private final Context mContext = getInstrumentation().getContext();
    private final UserSettings mUserSettings = new UserSettings(mContext);

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(TestCredentialActivity.class);

    // Sets up the feature flag rule and the device flag rule
    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(
                    DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL,
                    mContext,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        assumeFalse("Skipping test: Wear does not support CredentialManager yet", isWatch());
        Log.i(TAG, "Enabling service from scratch for " + CTS_SERVICE_NAME);
        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceFeature(mContext);
        mCredentialManager = mContext.getSystemService(CredentialManager.class);
        assertThat(mCredentialManager).isNotNull();
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT, BuildCompat.isAtLeastU());
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
        clearAllTestCredentialProviderServices();
        bindToTestService();
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Disabling credman services and device feature flag");
        clearAllTestCredentialProviderServices();
    }

    private void clearAllTestCredentialProviderServices() {
        mUserSettings.set(CREDENTIAL_SERVICE, null);
        mUserSettings.set(CREDENTIAL_SERVICE_PRIMARY, null);
    }

    @Test
    public void testGetCredentialManager_shouldSucceed() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.onActivity(
                activity -> {
                    assertThat(mCredentialManager).isNotNull();
                });
    }

    // TODO for all 'valid success' cases, mock credential manager the current success case
    // TODO (rightly) flips an error bit since we have test inputs
    @Test
    public void testGetPasswordCredentialRequest_serviceSetUp_onErrorInvokedForEmptyResponse()
            throws InterruptedException {
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request =
                new GetCredentialRequest.Builder(empty)
                        .addCredentialOption(
                                new CredentialOption.Builder(PASSWORD_CREDENTIAL_TYPE, empty, empty)
                                        .build())
                        .build();
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.getCredential(
                            activity, request, null, Executors.newSingleThreadExecutor(), callback);
                });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(GetCredentialException.class);
        assertThat(loadedResult.get().getType())
                .isEqualTo(GetCredentialException.TYPE_NO_CREDENTIAL);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void testGetPasswordCredentialRequest_invalidAllowedProviders_onErrorForEmptyResponse()
            throws InterruptedException {
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request =
                new GetCredentialRequest.Builder(empty)
                        .addCredentialOption(
                                new CredentialOption.Builder(PASSWORD_CREDENTIAL_TYPE, empty, empty)
                                        .addAllowedProvider(
                                                new ComponentName(
                                                        "dummpackage",
                                                        "/dummypackage.dummyservice"))
                                        .build())
                        .build();
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.getCredential(
                            activity, request, null, Executors.newSingleThreadExecutor(), callback);
                });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(GetCredentialException.class);
        assertThat(loadedResult.get().getType())
                .isEqualTo(GetCredentialException.TYPE_NO_CREDENTIAL);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void testGetPasswordCredentialRequest_validAllowedProviders_onErrorForEmptyResponse()
            throws InterruptedException {
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request =
                new GetCredentialRequest.Builder(empty)
                        .addCredentialOption(
                                new CredentialOption.Builder(PASSWORD_CREDENTIAL_TYPE, empty, empty)
                                        .addAllowedProvider(
                                                ComponentName.unflattenFromString(CTS_SERVICE_NAME))
                                        .build())
                        .build();
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.getCredential(
                            activity, request, null, Executors.newSingleThreadExecutor(), callback);
                });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(GetCredentialException.class);
        assertThat(loadedResult.get().getType())
                .isEqualTo(GetCredentialException.TYPE_NO_CREDENTIAL);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void testGetCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        GetCredentialRequest nullRequest = null;
        activityScenario.onActivity(
                activity -> {
                    assertThrows(
                            "expect null request to throw NPE",
                            NullPointerException.class,
                            () ->
                                    mCredentialManager.getCredential(
                                            activity,
                                            nullRequest,
                                            null,
                                            Executors.newSingleThreadExecutor(),
                                            callback));
                });
    }

    @Test
    public void prepareGetPasswordCredentialRequest_serviceSetUp_onErrorInvokedForEmptyResponse()
            throws InterruptedException {
        AtomicReference<PrepareGetCredentialResponse> prepareGetCredResponse =
                new AtomicReference<>();
        AtomicReference<GetCredentialException> prepareGetCredException = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request =
                new GetCredentialRequest.Builder(empty)
                        .addCredentialOption(
                                new CredentialOption.Builder(PASSWORD_CREDENTIAL_TYPE, empty, empty)
                                        .build())
                        .build();
        OutcomeReceiver<PrepareGetCredentialResponse, GetCredentialException>
                prepareGetCredCallback =
                        new OutcomeReceiver<>() {
                            @Override
                            public void onResult(@NonNull PrepareGetCredentialResponse response) {
                                prepareGetCredResponse.set(response);
                                latch.countDown();
                            }

                            @Override
                            public void onError(@NonNull GetCredentialException e) {
                                prepareGetCredException.set(e);
                                latch.countDown();
                            }
                        };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.prepareGetCredential(
                            request,
                            null,
                            Executors.newSingleThreadExecutor(),
                            prepareGetCredCallback);
                });

        latch.await(1000L, TimeUnit.MILLISECONDS);
        assertThat(prepareGetCredException.get()).isNull();
        assertThat(prepareGetCredResponse.get()).isNotNull();

        // Next, invoke the full getCredential flow.
        AtomicReference<GetCredentialResponse> getCredResponse = new AtomicReference<>();
        AtomicReference<GetCredentialException> getCredException = new AtomicReference<>();
        CountDownLatch getCredLatch = new CountDownLatch(1);
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> getCredCallback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        getCredResponse.set(response);
                        getCredLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        getCredException.set(e);
                        getCredLatch.countDown();
                    }
                };
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.getCredential(
                            activity,
                            prepareGetCredResponse.get().getPendingGetCredentialHandle(),
                            null,
                            Executors.newSingleThreadExecutor(),
                            getCredCallback);
                });

        getCredLatch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(getCredResponse.get()).isNull();
        assertThat(getCredException.get()).isNotNull();
        assertThat(getCredException.get().getClass()).isEqualTo(GetCredentialException.class);
        assertThat(getCredException.get().getType())
                .isEqualTo(GetCredentialException.TYPE_NO_CREDENTIAL);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void prepareGetCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<PrepareGetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull PrepareGetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    assertThrows(
                            "expect null request to throw NPE",
                            NullPointerException.class,
                            () ->
                                    mCredentialManager.prepareGetCredential(
                                            null,
                                            null,
                                            Executors.newSingleThreadExecutor(),
                                            callback));
                });
    }

    @Test
    public void testCreatePasswordCredentialRequest_serviceSetUp_onErrorInvokedForEmptyResponse()
            throws InterruptedException {
        AtomicReference<CreateCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        CreateCredentialRequest request =
                new CreateCredentialRequest.Builder(PASSWORD_CREDENTIAL_TYPE, empty, empty)
                        .setIsSystemProviderRequired(false)
                        .setAlwaysSendAppInfoToProvider(true)
                        .build();
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.createCredential(
                            activity, request, null, Executors.newSingleThreadExecutor(), callback);
                });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(CreateCredentialException.class);
        assertThat(loadedResult.get().getType())
                .isEqualTo(CreateCredentialException.TYPE_NO_CREATE_OPTIONS);
    }

    @Test
    public void testCreatePasswordCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    assertThrows(
                            "expect null request to throw NPE",
                            NullPointerException.class,
                            () ->
                                    mCredentialManager.createCredential(
                                            activity,
                                            null,
                                            null,
                                            Executors.newSingleThreadExecutor(),
                                            callback));
                });
    }

    @Test
    public void testClearCredentialRequest_serviceSetUp_onResponseInvoked()
            throws InterruptedException {
        AtomicReference<Boolean> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        ClearCredentialStateRequest request = new ClearCredentialStateRequest(empty);
        OutcomeReceiver<Void, ClearCredentialStateException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Void response) {
                        loadedResult.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialStateException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    mCredentialManager.clearCredentialState(
                            request, null, Executors.newSingleThreadExecutor(), callback);
                });

        latch.await(1000L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get()).isTrue();
    }

    @Test
    public void testClearCredentialRequest_nullRequest_throwsNPE() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(
                activity -> {
                    assertThrows(
                            "expect null request to throw NPE",
                            NullPointerException.class,
                            () ->
                                    mCredentialManager.clearCredentialState(
                                            null, null, Executors.newSingleThreadExecutor(), null));
                });
    }

    @Test
    public void getCredentialProviderServices_returnsAllProviders() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.onActivity(
                activity -> {
                    Map<String, CredentialProviderInfo> results =
                            getCredentialProviderServices(
                                    CredentialManager.PROVIDER_FILTER_ALL_PROVIDERS);

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi = results.get(NOOP_SERVICE);
                    assertThat(cpi).isNotNull();
                    assertThat(cpi.isSystemProvider()).isFalse();
                    assertThat(cpi.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service");
                    assertThat(cpi.getSettingsSubtitle().toString())
                            .isEqualTo("This is a subtitle");
                    assertThat(cpi.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi.getServiceInfo()).isNotNull();
                    assertThat(cpi.getCapabilities())
                            .containsExactly(PASSWORD_CREDENTIAL_TYPE, PASSKEY_CREDENTIAL_TYPE);

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi2 = results.get(NOOP_SERVICE_ALT);
                    assertThat(cpi2).isNotNull();
                    assertThat(cpi2.isSystemProvider()).isFalse();
                    assertThat(cpi2.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service Alternate");
                    assertThat(cpi2.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi2.getServiceInfo()).isNotNull();
                    assertThat(cpi2.getCapabilities())
                            .containsExactlyElementsIn(PASSWORD_CREDENTIAL_TYPE_LIST);
                });
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SETTINGS_ACTIVITY_ENABLED)
    public void getCredentialProviderServices_returnsUserProviders_settingsActivityDisabled() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        // Make sure the flag is set.
        assertThat(android.credentials.flags.Flags.settingsActivityEnabled()).isFalse();

        activityScenario.onActivity(
                activity -> {
                    Map<String, CredentialProviderInfo> results =
                            getCredentialProviderServices(
                                    CredentialManager.PROVIDER_FILTER_USER_PROVIDERS_ONLY);

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi = results.get(NOOP_SERVICE);
                    assertThat(cpi).isNotNull();
                    assertThat(cpi.isSystemProvider()).isFalse();
                    assertThat(cpi.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service");
                    assertThat(cpi.getSettingsSubtitle().toString())
                            .isEqualTo("This is a subtitle");
                    assertThat(cpi.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi.getServiceInfo()).isNotNull();
                    assertThat(cpi.getCapabilities())
                            .containsExactly(PASSWORD_CREDENTIAL_TYPE, PASSKEY_CREDENTIAL_TYPE);
                    assertThat(cpi.getSettingsActivity()).isNull();

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi2 = results.get(NOOP_SERVICE_ALT);
                    assertThat(cpi2).isNotNull();
                    assertThat(cpi2.isSystemProvider()).isFalse();
                    assertThat(cpi2.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service Alternate");
                    assertThat(cpi2.getSettingsSubtitle()).isNull();
                    assertThat(cpi2.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi2.getServiceInfo()).isNotNull();
                    assertThat(cpi2.getCapabilities())
                            .containsExactlyElementsIn(PASSWORD_CREDENTIAL_TYPE_LIST);
                    assertThat(cpi2.getSettingsActivity()).isNull();
                });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SETTINGS_ACTIVITY_ENABLED)
    public void getCredentialProviderServices_returnsUserProviders_settingsActivityEnabled() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        // Make sure the flag is set.
        assertThat(android.credentials.flags.Flags.settingsActivityEnabled()).isTrue();

        activityScenario.onActivity(
                activity -> {
                    Map<String, CredentialProviderInfo> results =
                            getCredentialProviderServices(
                                    CredentialManager.PROVIDER_FILTER_USER_PROVIDERS_ONLY);

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi = results.get(NOOP_SERVICE);
                    assertThat(cpi).isNotNull();
                    assertThat(cpi.isSystemProvider()).isFalse();
                    assertThat(cpi.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service");
                    assertThat(cpi.getSettingsSubtitle().toString())
                            .isEqualTo("This is a subtitle");
                    assertThat(cpi.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi.getServiceInfo()).isNotNull();
                    assertThat(cpi.getCapabilities())
                            .containsExactly(PASSWORD_CREDENTIAL_TYPE, PASSKEY_CREDENTIAL_TYPE);
                    assertThat(cpi.getSettingsActivity().toString())
                            .isEqualTo("com.test.TestActivity");

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi2 = results.get(NOOP_SERVICE_ALT);
                    assertThat(cpi2).isNotNull();
                    assertThat(cpi2.isSystemProvider()).isFalse();
                    assertThat(cpi2.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service Alternate");
                    assertThat(cpi2.getSettingsSubtitle()).isNull();
                    assertThat(cpi2.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi2.getServiceInfo()).isNotNull();
                    assertThat(cpi2.getCapabilities())
                            .containsExactlyElementsIn(PASSWORD_CREDENTIAL_TYPE_LIST);
                    assertThat(cpi2.getSettingsActivity()).isNull();
                });
    }

    @Test
    public void getCredentialProviderServices_returnsSystemProviders() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.onActivity(
                activity -> {
                    Map<String, CredentialProviderInfo> results =
                            getCredentialProviderServices(
                                    CredentialManager.PROVIDER_FILTER_SYSTEM_PROVIDERS_ONLY);

                    // Verify data of the returned provider.
                    CredentialProviderInfo cpi = results.get(NOOP_SERVICE_SYSTEM);
                    assertThat(cpi).isNotNull();
                    assertThat(cpi.isSystemProvider()).isTrue();
                    assertThat(cpi.getLabel(mContext).toString())
                            .isEqualTo("Test Provider Service System"); // this
                    assertThat(cpi.getSettingsSubtitle().toString())
                            .isEqualTo("This is a subtitle");
                    assertThat(cpi.getServiceIcon(mContext)).isNotNull();
                    assertThat(cpi.getSettingsSubtitle().toString())
                            .isEqualTo("This is a subtitle");
                    assertThat(cpi.getServiceInfo()).isNotNull();
                    assertThat(cpi.getCapabilities())
                            .containsExactlyElementsIn(PASSKEY_CREDENTIAL_TYPE_LIST);
                });
    }

    @Test
    public void testIsEnabledCredentialProviderService_isEnabled_primary() {
        final String componentName = CTS_PACKAGE_NAME + "/" + CLASS_NAME;
        setTestableCredentialProviderServiceInternal(componentName, CREDENTIAL_SERVICE_PRIMARY);
        assertThat(isCredentialProviderServiceEnabledInternal(componentName, CREDENTIAL_SERVICE_PRIMARY)).isTrue();

        ComponentName testService = new ComponentName(CTS_PACKAGE_NAME, CLASS_NAME);
        assertThat(mCredentialManager.isEnabledCredentialProviderService(testService)).isTrue();
    }

    @Test
    public void testIsEnabledCredentialProviderService_isEnabled_secondary() {
        ComponentName testService = new ComponentName(CTS_PACKAGE_NAME, CLASS_NAME);
        assertThat(mCredentialManager.isEnabledCredentialProviderService(testService)).isTrue();
    }

    @Test
    public void testIsEnabledCredentialProviderService_isDisabled() {
        ComponentName testService = new ComponentName(CTS_PACKAGE_NAME, "com.android.TestService");
        assertThat(mCredentialManager.isEnabledCredentialProviderService(testService)).isFalse();
    }

    /**
     * Enable the main credential manager feature. If this is off, any underlying changes for
     * autofill-credentialManager integrations are off.
     */
    public static void enableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    public static void disableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, false);
    }

    /** Enable Credential Manager related autofill changes */
    public static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context, DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /** Set device config to set flag values. */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    /** Set device config. */
    public static void setDeviceConfig(
            @NonNull DeviceConfigStateManager deviceConfigStateManager, @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }
        deviceConfigStateManager.set(value);
    }

    private void bindToTestService() {
        // On Manager, bind to test service
        setTestableCredentialProviderService(CTS_SERVICE_NAME);
        assertTrue(isCredentialProviderServiceEnabled(CTS_SERVICE_NAME));
    }

    private String getCredentialProviderServiceComponent() {
        return runShellCommand("settings get secure credential_service %d", USER_ID);
    }

    private String getCredentialProviderServicePrimaryComponent() {
        return runShellCommand("settings get secure credential_service_primary %d", USER_ID);
    }

    private void setTestableCredentialProviderService(@NonNull String serviceName) {
        setTestableCredentialProviderServiceInternal(serviceName, CREDENTIAL_SERVICE);
    }

    private void setTestableCredentialProviderServiceInternal(@NonNull String serviceName, @NonNull String key) {
        if (isCredentialProviderServiceEnabledInternal(serviceName, key)) return;

        String settingOutput = readCredentialManagerProviderSetting(key);
        settingOutput = settingOutput == null ? "" : settingOutput;
        if (settingOutput.length() > 0) {
            settingOutput += ";" + serviceName;
        } else {
            settingOutput += serviceName;
        }
        // Guaranteed to not be null now since the NoOp service exists at a minimum
        Log.i(TAG, "Attempting to set services: " + settingOutput);
        mUserSettings.set(key, settingOutput);

        // Waits until the service is actually enabled.
        try {
            CONNECTION_TIMEOUT.run(
                    "Checking if service enabled",
                    () -> isCredentialProviderServiceEnabledInternal(serviceName, key));
        } catch (Exception e) {
            Log.i(TAG, "Failure... " + e.getLocalizedMessage());
            throw new AssertionError("Enabling Credman service failed.");
        }
    }

    private Map<String, CredentialProviderInfo> getCredentialProviderServices(int providerFilter) {
        return mCredentialManager.getCredentialProviderServicesForTesting(providerFilter).stream()
                .collect(Collectors.toMap(c -> c.getComponentName().flattenToString(), c -> c));
    }

    /** Checks whether the given service is set as the credential service for the default user. */
    private boolean isCredentialProviderServiceEnabled(String serviceName) {
        return isCredentialProviderServiceEnabledInternal(serviceName, CREDENTIAL_SERVICE);
    }

    private boolean isCredentialProviderServiceEnabledInternal(String serviceName, String key) {
        final String actualNames = readCredentialManagerProviderSetting(key);
        Log.i(
                TAG,
                "actual names in setting: "
                        + actualNames
                        + " ,serviceName being "
                        + "checked : "
                        + serviceName);
        if (actualNames == null) {
            return false;
        }
        return containsName(actualNames, serviceName);
    }

    private boolean containsName(@NonNull String serviceNames, @NonNull String name) {
        Set<String> services = new LinkedHashSet<>(List.of(serviceNames.split(";")));
        return services.contains(name);
    }

    /** Gets then name of the credential service for the default user. */
    private String readCredentialManagerProviderSetting(String key) {
        String serviceNames = mUserSettings.get(key);
        return serviceNames;
    }

    /**
     * Uses Settings to disable the given autofill service for the default user, and waits until the
     * setting is deleted.
     */
    private void disableCredentialProviderService(@NonNull Context context, String service) {
        final String currentService = mUserSettings.get(CREDENTIAL_SERVICE);
        if (currentService == null) {
            return;
        }
        // remove same instance in order, including duplicates
        Set<String> services = new LinkedHashSet<>(List.of(currentService.split(";")));
        services.remove(service);
        String originalString = String.join(";", services);
        mUserSettings.set(CREDENTIAL_SERVICE, originalString);
    }

    /**
     * Assert target intent can be handled by at least one Activity.
     *
     * @param intent - the Intent will be handled.
     */
    private void assertCanBeHandled(final Intent intent) {
        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        assertThat(resolveInfoList).isNotNull();
        // one or more activity can handle this intent.
        assertTrue(resolveInfoList.size() > 0);
    }

    private boolean isWatch() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
