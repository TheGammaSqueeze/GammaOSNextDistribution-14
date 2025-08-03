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

package android.devicepolicy.cts;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import android.content.ComponentName;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AdbProvisioningTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName sComponentName = new ComponentName(
            RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX,
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver");
    private static final TestApp sRemoteDpcTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
            .get();

    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @Test
    public void setDeviceOwnerUsingAdb_isLogged() throws Exception {
        try (TestAppInstance testApp = sRemoteDpcTestApp.install()) {
            try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
                ShellCommand.builderForUser(
                            TestApis.users().instrumented(), "dpm set-device-owner")
                        .addOperand(sComponentName.flattenToShortString())
                        .execute();

                assertThat(metrics.query()
                        .whereType().isEqualTo(EventId.PROVISIONING_ENTRY_POINT_ADB_VALUE)
                        .whereAdminPackageName().isEqualTo(
                                RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
                        .whereStrings().containsExactly("device-owner")
                        .whereBoolean().isEqualTo(false))
                        .wasLogged();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        }
    }

    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @Test
    public void setProfileOwnerUsingAdb_isLogged() throws Exception {
        try (TestAppInstance testApp = sRemoteDpcTestApp.install()) {
            try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
                ShellCommand.builderForUser(
                                TestApis.users().instrumented(), "dpm set-profile-owner")
                        .addOperand(sComponentName.flattenToShortString())
                        .execute();

                assertThat(metrics.query()
                        .whereType().isEqualTo(EventId.PROVISIONING_ENTRY_POINT_ADB_VALUE)
                        .whereAdminPackageName().isEqualTo(
                                RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
                        .whereStrings().containsExactly("profile-owner")
                        .whereBoolean().isEqualTo(false))
                        .wasLogged();
            } finally {
                ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner();
                if (profileOwner != null) {
                    profileOwner.remove();
                }
            }
        }
    }

}
