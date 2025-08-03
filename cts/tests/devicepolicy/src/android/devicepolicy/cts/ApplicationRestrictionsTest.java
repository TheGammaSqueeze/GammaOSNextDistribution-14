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
package android.devicepolicy.cts;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.devicepolicy.cts.utils.BundleUtils;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.ApplicationRestrictions;
import com.android.bedstead.harrier.policies.ApplicationRestrictionsManagingPackage;
import com.android.bedstead.harrier.policies.DpcOnlyApplicationRestrictions;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ApplicationRestrictionsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TAG = ApplicationRestrictionsTest.class.getSimpleName();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final TestApp sDifferentTestApp = sDeviceState.testApps().any();

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = DpcOnlyApplicationRestrictions.class)
    public void setApplicationRestrictions_applicationRestrictionsAreSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_applicationRestrictionsAreSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            BundleUtils.assertEqualToBundle(
                    "setApplicationRestrictions_applicationRestrictionsAreSet",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

  @Postsubmit(reason = "New test")
  @PolicyAppliesTest(policy = DpcOnlyApplicationRestrictions.class)
  @Ignore("b/290932414")
  public void setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            new Bundle());
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertEqualToBundle(
          "setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions",
          testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
    Bundle bundle =
        BundleUtils.createBundle(
            "getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions");

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertEqualToBundle(
          "getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions",
          sDeviceState
              .dpc()
              .devicePolicyManager()
              .getApplicationRestrictions(
                  sDeviceState.dpc().componentName(), sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_differentPackage_throwsException() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictions_differentPackage_throwsException");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThrows(SecurityException.class,
                    () -> differentTestApp.userManager().getApplicationRestrictions(
                            sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_setForOtherPackage_returnsNull() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictions_setForOtherPackage_returnsNull");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            BundleUtils.assertNotEqualToBundle(
                    "getApplicationRestrictions_setForOtherPackage_returnsNull",
                    differentTestApp.userManager().getApplicationRestrictions(
                    sDifferentTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictions(
                        sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertNotEqualToBundle(
          "setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet",
          testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_cannotSetPolicy_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_cannotSetPolicy_throwsException");
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);
        });
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName());
        });
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class, singleTestOnly = true)
    public void setApplicationRestrictions_nullComponent_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_nullComponent_throwsException");
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(null,
                        sTestApp.packageName(), bundle));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_restrictionsChangedBroadcastIsReceived() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_restrictionsChangedBroadcastIsReceived");

        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.registerReceiver(new IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
                    RECEIVER_EXPORTED);

            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(testApp.events().broadcastReceived().whereIntent().action().isEqualTo(
                    ACTION_APPLICATION_RESTRICTIONS_CHANGED)).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_applicationRestrictionsManagingPackageIsSet()
            throws Exception {
        final String originalApplicationRestrictionsManagingPackage =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName());
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                    sDeviceState.dpc().componentName(), sTestApp.packageName());

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictionsManagingPackage(sDeviceState.dpc().componentName()))
                    .isEqualTo(sTestApp.packageName());
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName(),
                        originalApplicationRestrictionsManagingPackage);
            } catch (Throwable expected) {
                // If the original has been removed this can throw
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_appNotInstalled_throwsException() {
        sDifferentTestApp.uninstall();

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setApplicationRestrictionsManagingPackage(
                                sDeviceState.dpc().componentName(),
                                sDifferentTestApp.packageName()));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_logged() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle("setApplicationRestrictions_logged");

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
             TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_APPLICATION_RESTRICTIONS_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereStrings().contains(sTestApp.packageName())
                    .whereStrings().size().isEqualTo(1))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_invalidPackageName_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_invalidPackageName_throwsException");
        assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                        sDeviceState.dpc().componentName(), "/../blah", bundle));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictionsPerAdmin_restrictionsSetForOneAdmin_returnsApplicationRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictionsPerAdmin_applicationRestrictionsAreSetForOneAdmin"
                        + "_returnsApplicationRestrictions");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            List<Bundle> restrictions = testApp.restrictionsManager()
                    .getApplicationRestrictionsPerAdmin();
            assertThat(restrictions.size()).isEqualTo(1);
            BundleUtils.assertEqualToBundle("getApplicationRestrictionsPerAdmin"
                            + "_applicationRestrictionsAreSetForOneAdmin"
                            + "_returnsApplicationRestrictions",
                    restrictions.get(0));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }
}
