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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION;
import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_POWER_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_SUSPENSION;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.nene.appops.AppOpsMode.DEFAULT;
import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS;
import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_APP_EXEMPTIONS;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.stats.devicepolicy.EventId;
import android.util.ArrayMap;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.ApplicationExemptions;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public class ApplicationExemptionsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final TestApp sTestApp =
            sDeviceState.testApps().query().whereActivities().contains(
                    activity().where().exported().isTrue()).get();

    private static final String INVALID_PACKAGE_NAME = "com.google.android.notapackage";
    private static final Set<Integer> INVALID_EXEMPTIONS = new HashSet<>(List.of(-1));
    private static final Map<Integer, String> APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS =
            new ArrayMap<>();
    static {
        APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.put(
                EXEMPT_FROM_SUSPENSION, OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION);
    }

    @IntTestParameter({EXEMPT_FROM_SUSPENSION })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ApplicationExemptionConstants {}

    @Test
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_noPermission_throwsSecurityException() {
        Set<Integer> exemptionSet = new HashSet<>(EXEMPT_FROM_SUSPENSION);
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(SecurityException.class, () ->
                    sLocalDevicePolicyManager.setApplicationExemptions(
                            sTestApp.packageName(),
                            exemptionSet));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_validExemptionSet_exemptionAppOpsGranted(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(), exemptionSet);

            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption)))
                    .isEqualTo(ALLOWED);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_emptyExemptionSet_unsetsAllExemptions(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(exemption);
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    new HashSet<>());

            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption)))
                    .isEqualTo(DEFAULT);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_invalidExemptionInSet_throwsIllegalArgumentException() {
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(IllegalArgumentException.class, () ->
                    sLocalDevicePolicyManager.setApplicationExemptions(
                            sTestApp.packageName(),
                            INVALID_EXEMPTIONS));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_invalidPackage_throwsNameNotFoundException() {
        Set<Integer> exemptionSet = new HashSet<>(EXEMPT_FROM_SUSPENSION);
        assertThrows(NameNotFoundException.class, () ->
                sLocalDevicePolicyManager.setApplicationExemptions(
                        INVALID_PACKAGE_NAME, exemptionSet));
    }

    @Test
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void getApplicationExemptions_noPermission_throwsSecurityException() {
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(SecurityException.class, () ->
                    sLocalDevicePolicyManager.getApplicationExemptions(sTestApp.packageName()));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void getApplicationExemptions_validPackage_returnsExemptionsSet(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            Set<Integer> applicationExemptions =
                    sLocalDevicePolicyManager.getApplicationExemptions(sTestApp.packageName());
            assertThat(applicationExemptions).isEqualTo(exemptionSet);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void getApplicationExemptions_invalidPackage_throwsNameNotFoundException() {
        assertThrows(NameNotFoundException.class, () ->
                sLocalDevicePolicyManager.getApplicationExemptions(INVALID_PACKAGE_NAME));
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_reinstallApplication_exemptionAppOpsReset()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_SUSPENSION));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);
        }

        try (TestAppInstance testApp = sTestApp.install()) {
            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(EXEMPT_FROM_SUSPENSION)))
                    .isEqualTo(DEFAULT);
        }
    }

    @PolicyAppliesTest(policy = ApplicationExemptions.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_powerRestrictionExemption_exemptedAppStandbyBucket()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_POWER_RESTRICTIONS));

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            Poll.forValue("App standby bucket ",
                    () -> sTestApp.pkg().getAppStandbyBucket())
                    .toBeEqualTo(UsageStatsManager.STANDBY_BUCKET_EXEMPTED)
                    .errorOnFail()
                    .await();
        }
    }

    @PolicyDoesNotApplyTest(policy = ApplicationExemptions.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_powerRestrictionExemption_notApplicable_isNotInExemptedAppStandbyBucket()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_POWER_RESTRICTIONS));

        try (TestAppInstance localApp = sTestApp.install();
             TestAppInstance dpcUserApp = sTestApp.install(sDeviceState.dpc().user())) {

            sDeviceState.dpc().devicePolicyManager().setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            assertThat(localApp.appOps()
                    .get(OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS))
                    .isEqualTo(DEFAULT);
        }
    }

    @CannotSetPolicyTest(policy = ApplicationExemptions.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_notPermitted_throwsException() {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_SUSPENSION));

        try (TestAppInstance app = sTestApp.install()) {

            assertThrows(SecurityException.class, () -> {
                sDeviceState.dpc().devicePolicyManager().setApplicationExemptions(
                        sTestApp.packageName(),
                        exemptionSet);
            });
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_validExemptionSet_logsEvent(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
            TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(), exemptionSet);

            assertThat(metrics.query()
                .whereType()
                .isEqualTo(EventId.SET_APPLICATION_EXEMPTIONS_VALUE)
                .whereStrings().size().isEqualTo(exemptionSet.size() + 1)
                .whereStrings().contains(sTestApp.packageName())
                .whereStrings()
                .contains(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption))
                .whereAdminPackageName()
                .isEqualTo(TestApis.context().instrumentedContext().getPackageName()))
                .wasLogged();
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_noExemption_cannotStartActivityFromBg()
            throws NameNotFoundException {
        try (TestAppInstance testApp = sTestApp.install()) {
            TestAppActivityReference testActivity = testApp.activities().any();
            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(testActivity.component().componentName());

            testApp.context().startActivity(intent);

            Poll.forValue("Start foreground activity from background",
                    () -> TestApis.activities().foregroundActivity())
                    .toNotBeEqualTo(testActivity.component()).errorOnFail().await();
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_activityBgStartRestrictionExemption_canStartActivityFromBg()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(
                List.of(EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION));

        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);
            TestAppActivityReference testActivity = testApp.activities().any();
            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(testActivity.component().componentName());

            testApp.context().startActivity(intent);

            Poll.forValue("Start foreground activity from background",
                    () -> TestApis.activities().foregroundActivity())
                    .toBeEqualTo(testActivity.component()).errorOnFail().await();
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @RequireRunOnSystemUser
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_noExemption_testAppCanBeSuspended()
            throws NameNotFoundException {
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(),
                    new String[]{sTestApp.packageName()}, true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName())).isEqualTo(true);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(),
                    new String[]{sTestApp.packageName()}, false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setApplicationExemptions_suspensionRestrictionExemption_appCannotBeSuspended()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_SUSPENSION));

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpmRoleHolder().devicePolicyManager().setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);
            String[] notSuspendedPackages =
                    sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                            sDeviceState.dpc().componentName(),
                            new String[]{sTestApp.packageName()}, true);

            assertThat(List.of(notSuspendedPackages)).contains(sTestApp.packageName());

            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    sDeviceState.dpc().componentName(),
                sTestApp.packageName())).isEqualTo(false);
        }
    }
}
