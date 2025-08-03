/*
 * Copyright (Cw) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.PermittedAccessibilityServices;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accessibility.AccessibilityService;
import com.android.bedstead.nene.packages.Package;
import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public class AccessibilityServicesTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static Set<Package> systemAccessibilityServicePackages() {
        return TestApis.accessibility().installedAccessibilityServices().stream()
                .map(AccessibilityService::pkg)
                .filter(Package::hasSystemFlag)
                .collect(Collectors.toSet());
    }

    private static final Package ACCESSIBILITY_SERVICE_PACKAGE = TestApis.packages().find("pkg");

    @Before
    public void setUp() {
        // We can only proceed with the test if no non-system services are enabled
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        } catch (Exception expected) {

        }
        assertThat(TestApis.accessibility().enabledAccessibilityServices().stream()
                .map(i -> i.pkg())
                .filter(p -> !p.hasSystemFlag())
                .collect(Collectors.toSet())).isEmpty();
    }

    @AfterClass // Already cleared in @Before
    public static void resetPermittedAccessibilityServices() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        } catch (Exception ignore) {
            // Expected in some cases
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @CanSetPolicyTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_checkWithDpc_returnsNull() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);

        assertThat(result).isTrue();
        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedAccessibilityServices(sDeviceState.dpc().componentName()))
                .isNull();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_nullPackageNames_allServicesArePermitted() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);

        assertThat(result).isTrue();
        assertThat(TestApis.devicePolicy().getPermittedAccessibilityServices()).isNull();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_nullPackageNames_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean result =
                    sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                            sDeviceState.dpc().componentName(), /* packageNames= */ null);

            assertThat(metrics.query()
                    .whereType()
                    .isEqualTo(EventId.SET_PERMITTED_ACCESSIBILITY_SERVICES_VALUE)
                    .whereStrings().size().isEqualTo(0))
                    .wasLogged();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @CanSetPolicyTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_emptyList_checkWithDpc_isEmptyList() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ ImmutableList.of());

        assertThat(result).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedAccessibilityServices(sDeviceState.dpc().componentName())).isEmpty();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_emptyList_onlyPermitsSystemServices() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ ImmutableList.of());

        assertThat(result).isTrue();

        assertWithMessage(
                "Expected permitted services to only include system packages("
                        + systemAccessibilityServicePackages() + ")")
                .that(TestApis.devicePolicy().getPermittedAccessibilityServices())
                .containsExactlyElementsIn(systemAccessibilityServicePackages());
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @CanSetPolicyTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_includeNonSystemApp_checkWithDpc_returnsOnlyNonSystemApp() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(),
                ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE.packageName()));

        assertThat(result).isTrue();
        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedAccessibilityServices(sDeviceState.dpc().componentName()))
                .containsExactly(ACCESSIBILITY_SERVICE_PACKAGE.packageName());
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_includeNonSystemApp_permitsNonSystemApp() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(),
                ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE.packageName()));

        assertThat(result).isTrue();
        assertThat(TestApis.devicePolicy().getPermittedAccessibilityServices())
                .contains(ACCESSIBILITY_SERVICE_PACKAGE);
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                    sDeviceState.dpc().componentName(),
                    ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE.packageName()));

            assertThat(metrics.query()
                    .whereType()
                    .isEqualTo(EventId.SET_PERMITTED_ACCESSIBILITY_SERVICES_VALUE)
                    .whereStrings().contains(ACCESSIBILITY_SERVICE_PACKAGE.packageName()))
                    .wasLogged();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_includeNonSystemApp_stillPermitsSystemApps() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(),
                ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE.packageName()));

        assertThat(result).isTrue();
        assertThat(TestApis.devicePolicy().getPermittedAccessibilityServices())
                .containsAtLeastElementsIn(systemAccessibilityServicePackages());
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermittedAccessibilityServices")
    @CannotSetPolicyTest(policy = PermittedAccessibilityServices.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                        sDeviceState.dpc().componentName(), null));
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPermittedAccessibilityServices")
    @CannotSetPolicyTest(policy = PermittedAccessibilityServices.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void getPermittedAccessibilityServices_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().getPermittedAccessibilityServices(
                        sDeviceState.dpc().componentName()));
    }

    // TODO: Add @PolicyDoesNotApplyTest
}
