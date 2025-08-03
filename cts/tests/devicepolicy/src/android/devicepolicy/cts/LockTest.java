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

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.LockNow;
import com.android.bedstead.harrier.policies.MaximumTimeToLock;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature("android.software.secure_lock_screen")
public class LockTest {

    private static final long TIMEOUT = 10000;

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final KeyguardManager sLocalKeyguardManager =
            TestApis.context().instrumentedContext().getSystemService(KeyguardManager.class);

    @CannotSetPolicyTest(policy = LockNow.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().lockNow());
    }

    @PolicyAppliesTest(policy = LockNow.class)
    @Postsubmit(reason = "New test")
    @EnsurePasswordNotSet
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_logsMetric() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().lockNow(/* flags= */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.LOCK_NOW_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereInteger().isEqualTo(0)
            ).wasLogged();
        }
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_noPasswordSet_turnsScreenOff() throws Exception {
        Assume.assumeFalse("LockNow on profile won't turn off screen",
                sDeviceState.dpc().user().isProfile());
        sDeviceState.dpc().devicePolicyManager().lockNow();

        Poll.forValue("isScreenOn", () -> TestApis.device().isScreenOn())
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_automotive_noPasswordSet_doesNotTurnScreenOff() throws Exception {
        sDeviceState.dpc().devicePolicyManager().lockNow();

        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_passwordSet_locksDevice() throws Exception {
        sDeviceState.dpc().devicePolicyManager().lockNow();

        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @CannotSetPolicyTest(policy = MaximumTimeToLock.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaximumTimeToLock")
    public void setMaximumTimeToLock_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setMaximumTimeToLock(sDeviceState.dpc().componentName(), TIMEOUT));
    }

    @PolicyAppliesTest(policy = MaximumTimeToLock.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMaximumTimeToLock",
            "android.app.admin.DevicePolicyManager#getMaximumTimeToLock"})
    @Ignore // Incorrect logic
    public void setMaximumTimeToLock_maximumTimeToLockIsSet() {
        long originalTimeout = sDeviceState.dpc().devicePolicyManager()
                .getMaximumTimeToLock(sDeviceState.dpc().componentName());

        assertThat(TestApis.devicePolicy().getMaximumTimeToLock()).isEqualTo(TIMEOUT);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setMaximumTimeToLock(sDeviceState.dpc().componentName(), TIMEOUT);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setMaximumTimeToLock(
                    sDeviceState.dpc().componentName(), originalTimeout);
        }
    }

    @PolicyDoesNotApplyTest(policy = MaximumTimeToLock.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMaximumTimeToLock",
            "android.app.admin.DevicePolicyManager#getMaximumTimeToLock"})
    public void setMaximumTimeToLock_doesNotApply_maximumTimeToLockIsNotSet() {
        long originalTimeout = sDeviceState.dpc().devicePolicyManager()
                .getMaximumTimeToLock(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setMaximumTimeToLock(sDeviceState.dpc().componentName(), TIMEOUT);

            assertThat(TestApis.devicePolicy().getMaximumTimeToLock()).isNotEqualTo(TIMEOUT);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setMaximumTimeToLock(
                    sDeviceState.dpc().componentName(), originalTimeout);
        }
    }
}
