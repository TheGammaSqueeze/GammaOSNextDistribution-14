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

import static com.android.queryable.queries.IntentFilterQuery.intentFilter;
import static com.android.queryable.queries.ServiceQuery.service;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.PackagePolicy;
import android.content.pm.PackageManager;
import android.credentials.CredentialProviderInfo;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.CredentialManagerPolicy;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public class CtsDevicePolicyTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String PACKAGE_NAME = "arbitrary.package.name";

    private static final TestApp sSystemCredentialProvider =
            sDeviceState
                    .testApps()
                    .query()
                    .whereServices()
                    .contains(
                            service()
                                    // TODO(274580396): We shouldn't need to specify class name
                                    .where()
                                    .serviceClass()
                                    .className()
                                    .isEqualTo(
                                            "com.android.bedstead.testapp.NotEmptyTestApp"
                                                    + ".TestSystemCredentialProviderService")
                                    .where()
                                    .intentFilters()
                                    .contains(
                                            intentFilter()
                                                    .where()
                                                    .actions()
                                                    .contains(
                                                            "android.service.credentials.system.CredentialProviderService")))
                    .get();

    private static final TestApp sNonSystemCredentialProvider =
            sDeviceState
                    .testApps()
                    .query()
                    .whereServices()
                    .contains(
                            service()
                                    // TODO(274580396): We shouldn't need to specify class name
                                    .where()
                                    .serviceClass()
                                    .className()
                                    .isEqualTo("com.android.TestApp.CredentialProviderService")
                                    .where()
                                    .intentFilters()
                                    .contains(
                                            intentFilter()
                                                    .where()
                                                    .actions()
                                                    .contains(
                                                            "android.service.credentials.CredentialProviderService")))
                    .whereMetadata()
                    .key("android.credentials.testsystemprovider")
                    .stringValue()
                    .isNotEqualTo("true")
                    .get();

    @Test
    @CannotSetPolicyTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () ->
                        sDeviceState
                                .dpc()
                                .devicePolicyManager()
                                .setCredentialManagerPolicy(
                                        new PackagePolicy(
                                                PackagePolicy.PACKAGE_POLICY_ALLOWLIST,
                                                Set.of(PACKAGE_NAME))));
    }

    @Test
    @CanSetPolicyTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_policyIsSet() {
        PackagePolicy pp =
                new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST, Set.of(PACKAGE_NAME));

        try {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(pp);

            assertThat(sDeviceState.dpc().devicePolicyManager().getCredentialManagerPolicy())
                    .isEqualTo(pp);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }

    @Test
    @CanSetPolicyTest(policy = CredentialManagerPolicy.class, singleTestOnly = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_null_setsPolicy() {
        sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);

        assertThat(sDeviceState.dpc().devicePolicyManager().getCredentialManagerPolicy()).isNull();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyAppliesTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void
            setCredentialManagerPolicy_allowlistAndSystemPolicy_allowsAllowlistedAndSystemProviders() { // this
        try (TestAppInstance systemCredentialProvider = sSystemCredentialProvider.install();
                TestAppInstance nonSystemCredentialProvider =
                        sNonSystemCredentialProvider.install()) {
            String systemCredentialProviderService =
                    systemCredentialProvider.packageName()
                            + "/"
                            + systemCredentialProvider.packageName()
                            + ".TestSystemCredentialProviderService";
            String nonSystemCredentialProviderService =
                    nonSystemCredentialProvider.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setCredentialManagerPolicy(
                            new PackagePolicy(
                                    PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM,
                                    Set.of(nonSystemCredentialProvider.packageName())));

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components)
                    .containsAtLeast(
                            systemCredentialProviderService, nonSystemCredentialProviderService);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyAppliesTest(policy = CredentialManagerPolicy.class)
    @RequireTargetSdkVersion(max = android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void
            setCredentialManagerPolicy_allowlistAndSystemPolicy_allowsAllowlistedAndSystemProviders_SDK() {
        try (TestAppInstance systemCredentialProvider = sSystemCredentialProvider.install();
                TestAppInstance nonSystemCredentialProvider =
                        sNonSystemCredentialProvider.install()) {
            String systemCredentialProviderService =
                    systemCredentialProvider.packageName()
                            + "/"
                            + systemCredentialProvider.packageName()
                            + ".TestSystemCredentialProviderService";
            String nonSystemCredentialProviderService =
                    nonSystemCredentialProvider.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setCredentialManagerPolicy(
                            new PackagePolicy(
                                    PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM,
                                    Set.of(nonSystemCredentialProvider.packageName())));

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components)
                    .containsAtLeast(
                            systemCredentialProviderService, nonSystemCredentialProviderService);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyAppliesTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_allowlistPolicy_allowsAllowlistedProviders() {
        try (TestAppInstance systemCredentialProvider = sSystemCredentialProvider.install();
                TestAppInstance nonSystemCredentialProvider =
                        sNonSystemCredentialProvider.install()) {
            String systemCredentialProviderService =
                    systemCredentialProvider.packageName()
                            + "/"
                            + systemCredentialProvider.packageName()
                            + ".TestSystemCredentialProviderService";
            String nonSystemCredentialProviderService =
                    nonSystemCredentialProvider.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setCredentialManagerPolicy(
                            new PackagePolicy(
                                    PackagePolicy.PACKAGE_POLICY_ALLOWLIST,
                                    Set.of(nonSystemCredentialProvider.packageName())));

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components).contains(nonSystemCredentialProviderService);
            assertThat(components).doesNotContain(systemCredentialProviderService);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyAppliesTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_blocklistPolicy_allowsNotBlocklistedProviders() {
        try (TestAppInstance credentialProvider1 = sSystemCredentialProvider.install();
                TestAppInstance credentialProvider2 = sNonSystemCredentialProvider.install()) {
            String credentialProviderService1 =
                    credentialProvider1.packageName()
                            + "/"
                            + credentialProvider1.packageName()
                            + ".TestSystemCredentialProviderService";
            String credentialProviderService2 =
                    credentialProvider2.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setCredentialManagerPolicy(
                            new PackagePolicy(
                                    PackagePolicy.PACKAGE_POLICY_BLOCKLIST,
                                    Set.of(credentialProvider1.packageName())));

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components).contains(credentialProviderService2);
            assertThat(components).doesNotContain(credentialProviderService1);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyAppliesTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_nullPolicy_allowsAllProviders() {
        try (TestAppInstance credentialProvider1 = sSystemCredentialProvider.install();
                TestAppInstance credentialProvider2 = sNonSystemCredentialProvider.install()) {
            String credentialProviderService1 =
                    credentialProvider1.packageName()
                            + "/"
                            + credentialProvider1.packageName()
                            + ".TestSystemCredentialProviderService";
            String credentialProviderService2 =
                    credentialProvider2.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components)
                    .containsAtLeast(credentialProviderService1, credentialProviderService2);
        }
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_CREDENTIALS)
    @PolicyDoesNotApplyTest(policy = CredentialManagerPolicy.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCredentialManagerPolicy")
    public void setCredentialManagerPolicy_allowListPolicy_doesNotApply_allowsAllProviders() {
        try (TestAppInstance credentialProvider1 = sSystemCredentialProvider.install();
                TestAppInstance credentialProvider2 = sNonSystemCredentialProvider.install()) {
            String credentialProviderService1 =
                    credentialProvider1.packageName()
                            + "/"
                            + credentialProvider1.packageName()
                            + ".TestSystemCredentialProviderService";
            String credentialProviderService2 =
                    credentialProvider2.packageName()
                            + "/"
                            + "com.android.TestApp.CredentialProviderService";

            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setCredentialManagerPolicy(
                            new PackagePolicy(
                                    PackagePolicy.PACKAGE_POLICY_ALLOWLIST,
                                    Set.of(credentialProvider1.packageName())));

            Set<CredentialProviderInfo> services =
                    TestApis.credentials().getCredentialProviderServices();
            Set<String> components =
                    services.stream()
                            .map(s -> s.getComponentName().flattenToString())
                            .collect(Collectors.toSet());
            assertThat(components)
                    .containsAtLeast(credentialProviderService1, credentialProviderService2);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCredentialManagerPolicy(null);
        }
    }
}
