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

import static android.app.admin.DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;

import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_1;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_2;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.BlockUninstall;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class BlockUninstallTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final String NOT_INSTALLED_PACKAGE_NAME = "not.installed.package";

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @BeforeClass
    public static void setupClass() {
        sTestApp.install();
    }

    @AfterClass
    public static void teardownClass() {
        sTestApp.uninstallFromAllUsers();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);
        });
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_isUninstallBlockedIsTrue() {
        sTestApp.install(sDeviceState.dpc().user());
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                    sDeviceState.dpc().componentName(), sTestApp.packageName()
            )).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_isUninstallBlockedIsFalse() {
        sTestApp.install(sDeviceState.dpc().user());
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                    sDeviceState.dpc().componentName(), sTestApp.packageName()
            )).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_false_isUninstallBlockedIsFalse() {
        sTestApp.install(sDeviceState.dpc().user());
        sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                sDeviceState.dpc().componentName(),
                sTestApp.packageName(), /* uninstallBlocked= */ false
        );

        assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                sDeviceState.dpc().componentName(), sTestApp.packageName()
        )).isFalse();
        assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                sTestApp.packageName())).isFalse();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_appIsNotInstalled_silentlyFails() {
        sTestApp.install(sDeviceState.dpc().user());
        sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                sDeviceState.dpc().componentName(),
                NOT_INSTALLED_PACKAGE_NAME, /* uninstallBlocked= */ true
        );

        assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                sDeviceState.dpc().componentName(), NOT_INSTALLED_PACKAGE_NAME
        )).isFalse();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_UNINSTALL_BLOCKED_VALUE)
                            .whereAdminPackageName().isEqualTo(
                                    sDeviceState.dpc().packageName())
                            .whereStrings().contains(sTestApp.packageName())
                            .whereStrings().size().isEqualTo(1)
                            .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = BlockUninstall.class)
    public void getDevicePolicyState_uninstallBlockedSet_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = BlockUninstall.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_uninstallBlockedSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    PACKAGE_UNINSTALL_BLOCKED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = BlockUninstall.class, singleTestOnly = true)
    public void getDevicePolicyState_uninstallBlocked_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostRestrictiveCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_bothTrue_isUninstallBlockedIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(
                    /* admin= */ null, sTestApp.packageName())).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostRestrictiveCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_bothFalse_isUninstallBlockedIsFalse() {
        sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                /* componentName= */ null,
                sTestApp.packageName(),
                /* uninstallBlocked= */ false);
        sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                /* componentName= */ null,
                sTestApp.packageName(),
                /* uninstallBlocked= */ false);

        assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager().isUninstallBlocked(
                /* componentName= */ null, sTestApp.packageName())).isFalse();
        assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager().isUninstallBlocked(
                /* componentName= */ null, sTestApp.packageName())).isFalse();
        assertThat(sLocalDevicePolicyManager.isUninstallBlocked(
                /* admin= */ null, sTestApp.packageName())).isFalse();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostRestrictiveCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_differentValues_isUninstallBlockedIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(
                    /* admin= */ null, sTestApp.packageName())).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostRestrictiveCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_differentValuesThenBothFalse_isUninstallBlockedIsFalse() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isFalse();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isFalse();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(
                    /* admin= */ null, sTestApp.packageName())).isFalse();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostRestrictiveCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_bothTrueThenOneFalse_isUninstallBlockedIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager().isUninstallBlocked(
                    /* componentName= */ null, sTestApp.packageName())).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(
                    /* admin= */ null, sTestApp.packageName())).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostImportantCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try {
            sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);

            // Remove DPC
            sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                    sDeviceState.dpc().packageName());

            assertThat(sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                    .isUninstallBlocked(
                            /* componentName= */ null, sTestApp.packageName())).isTrue();
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY, sTestApp.packageName()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isTrue();
        } finally {
            try {
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                        /* componentName= */ null,
                        sTestApp.packageName(),
                        /* uninstallBlocked= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
            try {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                        /* componentName= */ null,
                        sTestApp.packageName(),
                        /* uninstallBlocked= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUninstallBlocked",
            "android.app.admin.DevicePolicyManager#isUninstallBlocked"})
    @MostImportantCoexistenceTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_setByPermission_appRemoved_notEnforced() {
        try {
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                    /* componentName= */ null,
                    sTestApp.packageName(),
                    /* uninstallBlocked= */ true);

            // uninstall app
            sDeviceState.testApp(LESS_IMPORTANT).uninstall();
            SystemClock.sleep(500);

            assertThat(sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                    .isUninstallBlocked(
                            /* componentName= */ null, sTestApp.packageName())).isFalse();
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY, sTestApp.packageName()),
                    TestApis.users().instrumented().userHandle());
            if (policyState != null) {
                assertThat(policyState.getCurrentResolvedPolicy()).isFalse();
            }
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isFalse();

        } finally {
            try {
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                        /* componentName= */ null,
                        sTestApp.packageName(),
                        /* uninstallBlocked= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
            try {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setUninstallBlocked(
                        /* componentName= */ null,
                        sTestApp.packageName(),
                        /* uninstallBlocked= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }
}
