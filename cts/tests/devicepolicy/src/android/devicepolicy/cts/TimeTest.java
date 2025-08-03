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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_DATE_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.provider.Settings;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.AutoTimeEnabled;
import com.android.bedstead.harrier.policies.AutoTimeRequired;
import com.android.bedstead.harrier.policies.AutoTimeZoneEnabled;
import com.android.bedstead.harrier.policies.DisallowConfigDateTime;
import com.android.bedstead.harrier.policies.Time;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.TimeZone;

@RunWith(BedsteadJUnit4.class)
public final class TimeTest {

    private static final long MILLIS_SINCE_EPOCH = 1660000000000l;

    private static final String TIMEZONE = "Singapore";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_false_setsAutoTimeNotRequired() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired();

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), false);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isFalse();

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_setsAutoTimeRequired() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired();

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), true);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeRequired.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_notAllowed_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                        sDeviceState.dpc().componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_policyDoesNotApply_autoTimeIsNotRequired() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired();

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), true);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired();
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_REQUIRED_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_false_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired();
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_REQUIRED_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_true_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeEnabled(
                sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_false_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeEnabled(
                sDeviceState.dpc().componentName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_true_autoTimeIsEnabled() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().getAutoTimeEnabled(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(TestApis.settings().global()
                    .getInt(Settings.Global.AUTO_TIME, 0)).isEqualTo(1);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_false_autoTimeIsNotEnabled() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME, 0)).isEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void getAutoTimeEnabled_returnsAutoTimeEnabled() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getAutoTimeEnabled(sDeviceState.dpc().componentName())).isEqualTo(true);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setAutoTimeEnabled(sDeviceState.dpc().componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#getAutoTimeEnabled")
    public void getAutoTimeEnabled_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getAutoTimeEnabled(sDeviceState.dpc().componentName()));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_true_autoTimeZoneIsEnabled() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(1);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_false_autoTimeZoneIsNotEnabled() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeZoneEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setAutoTimeZoneEnabled(sDeviceState.dpc().componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_true_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_false_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    // TODO: Add test of ACTION_TIME_CHANGED broadcast
    // TODO: Add test of ACTION_TIMEZONE_CHANGED broadcast

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_timeIsSet() {
        boolean originalAutoTimeEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), false);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTime(sDeviceState.dpc().componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isTrue();

            long currentTime = System.currentTimeMillis();
            long differenceSeconds = (currentTime - MILLIS_SINCE_EPOCH) / 1000;

            assertThat(differenceSeconds).isLessThan(120); // Within 2 minutes
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_doesNotApply_timeIsNotSet() {
        boolean originalAutoTimeEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), false);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTime(sDeviceState.dpc().componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isTrue();
            assertThat(System.currentTimeMillis()).isNotEqualTo(MILLIS_SINCE_EPOCH);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class, singleTestOnly = true)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_autoTimeIsEnabled_returnsFalse() {
        boolean originalAutoTimeEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), true);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTime(sDeviceState.dpc().componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isFalse();
            assertThat(System.currentTimeMillis()).isNotEqualTo(MILLIS_SINCE_EPOCH);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = Time.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setTime(sDeviceState.dpc().componentName(), MILLIS_SINCE_EPOCH));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_logsEvent() {
        boolean originalAutoTimeEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeEnabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), false);

            sDeviceState.dpc().devicePolicyManager()
                    .setTime(sDeviceState.dpc().componentName(), MILLIS_SINCE_EPOCH);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_timeZoneIsSet() {
        boolean originalAutoTimeZoneEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTimeZone(sDeviceState.dpc().componentName(), TIMEZONE);

            assertThat(returnValue).isTrue();
            Poll.forValue("timezone ID", () -> TimeZone.getDefault().getID())
                    .toBeEqualTo(TIMEZONE).errorOnFail().await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeZoneEnabledValue);
        }
    }

    // TODO(234609037): Once these APIs are accessible via permissions, this should be moved to Nene
    private void setAutoTimeZoneEnabled(RemoteDevicePolicyManager dpm,
            ComponentName componentName, boolean enabled) {
        sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                componentName, enabled);

        Poll.forValue("autoTimeZoneEnabled",
                () -> dpm.getAutoTimeZoneEnabled(componentName))
                .toBeEqualTo(enabled)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_doesNotApply_timeZoneIsNotSet() {
        boolean originalAutoTimeZoneEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            setAutoTimeZoneEnabled(sDeviceState.dpc().devicePolicyManager(),
                    sDeviceState.dpc().componentName(), false);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTimeZone(sDeviceState.dpc().componentName(), TIMEZONE);

            assertThat(returnValue).isTrue();
            assertThat(TimeZone.getDefault().getDisplayName()).isNotEqualTo(TIMEZONE);
        } finally {
            setAutoTimeZoneEnabled(sDeviceState.dpc().devicePolicyManager(),
                    sDeviceState.dpc().componentName(), originalAutoTimeZoneEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_autoTimeZoneIsEnabled_returnsFalse() {
        boolean originalAutoTimeZoneEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            setAutoTimeZoneEnabled(sDeviceState.dpc().devicePolicyManager(),
                    sDeviceState.dpc().componentName(), true);

            boolean returnValue = sDeviceState.dpc().devicePolicyManager()
                    .setTimeZone(sDeviceState.dpc().componentName(), TIMEZONE);

            assertThat(returnValue).isFalse();
            assertThat(TimeZone.getDefault().getDisplayName()).isNotEqualTo(TIMEZONE);
        } finally {
            setAutoTimeZoneEnabled(sDeviceState.dpc().devicePolicyManager(),
                    sDeviceState.dpc().componentName(), originalAutoTimeZoneEnabledValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = com.android.bedstead.harrier.policies.TimeZone.class,
            includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setTimeZone(sDeviceState.dpc().componentName(), TIMEZONE));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_logsEvent() {
        boolean originalAutoTimeZoneEnabledValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);

            sDeviceState.dpc().devicePolicyManager()
                    .setTimeZone(sDeviceState.dpc().componentName(), TIMEZONE);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeZoneEnabledValue);
        }
    }

    @CannotSetPolicyTest(policy = DisallowConfigDateTime.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DATE_TIME));
    }

    @PolicyAppliesTest(policy = DisallowConfigDateTime.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DATE_TIME);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_DATE_TIME))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DATE_TIME);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigDateTime.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DATE_TIME);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_DATE_TIME))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DATE_TIME);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_DATE_TIME)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void disallowConfigDateTimeIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_DATE_TIME)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void disallowConfigDateTimeIsSet_todo() throws Exception {
        // TODO: Test
    }
}