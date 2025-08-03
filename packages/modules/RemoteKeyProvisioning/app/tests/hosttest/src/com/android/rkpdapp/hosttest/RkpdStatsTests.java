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
package com.android.rkpdapp.hosttest;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ReportUtils;
import android.stats.connectivity.TransportType;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.Cause;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.Enablement;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.UpTime;
import com.android.os.AtomsProto.RemoteKeyProvisioningNetworkInfo;
import com.android.os.AtomsProto.RemoteKeyProvisioningTiming;
import com.android.os.StatsLog.EventMetricData;
import com.android.remoteprovisioner.RemoteprovisionerEnums.RemoteKeyProvisioningStatus;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RkpdStatsTests extends AtomsHostTest {
    private static final int NO_HTTP_STATUS_ERROR = 0;
    private static final int HTTP_STATUS_SERVER_ERROR = 500;
    private static final int HTTPS_OK = 200;
    private static final String RPC_DEFAULT =
            "android.hardware.security.keymint.IRemotelyProvisionedComponent/default";

    private static final List<TransportType> VALID_TRANSPORT_TYPES = Arrays.asList(
            TransportType.TT_CELLULAR, TransportType.TT_WIFI, TransportType.TT_BLUETOOTH,
            TransportType.TT_ETHERNET, TransportType.TT_WIFI_AWARE, TransportType.TT_LOWPAN,
            TransportType.TT_CELLULAR_VPN, TransportType.TT_WIFI_VPN,
            TransportType.TT_BLUETOOTH_VPN, TransportType.TT_ETHERNET_VPN,
            TransportType.TT_WIFI_CELLULAR_VPN
    );

    private static final List<Enablement> VALID_ENABLEMENTS = Arrays.asList(
            Enablement.ENABLED_RKP_ONLY, Enablement.ENABLED_WITH_FALLBACK);

    public RkpdStatsTests() {
        super(AtomsProto.Atom.REMOTE_KEY_PROVISIONING_ATTEMPT_FIELD_NUMBER,
                AtomsProto.Atom.REMOTE_KEY_PROVISIONING_NETWORK_INFO_FIELD_NUMBER,
                AtomsProto.Atom.REMOTE_KEY_PROVISIONING_TIMING_FIELD_NUMBER);
    }

    @Test
    public void testDataBudgetEmptyGenerateKey() throws Exception {
        runTest("testDataBudgetEmptyGenerateKey");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isIn(VALID_ENABLEMENTS);
        assertThat(attempt.getStatus()).isEqualTo(RemoteKeyProvisioningStatus.OUT_OF_ERROR_BUDGET);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isEqualTo(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testRetryableRkpError() throws Exception {
        runTest("testRetryableRkpError");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.FETCH_GEEK_HTTP_ERROR);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(HTTP_STATUS_SERVER_ERROR);
    }

    @Test
    public void testRetryNeverWhenDeviceNotRegistered() throws Exception {
        runTest("testRetryNeverWhenDeviceNotRegistered");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.SIGN_CERTS_DEVICE_NOT_REGISTERED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(444);
    }

    @Test
    public void testKeyCreationUsesRemotelyProvisionedCertificate() throws Exception {
        runTest("testKeyCreationUsesRemotelyProvisionedCertificate");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(6);

        final List<EventMetricData> firstAttemptData = data.subList(0, 3);
        RemoteKeyProvisioningAttempt attempt = getAttemptMetric(firstAttemptData);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        // PeriodicProvisioner provisions ALL remotely provisioned components, and each one
        // has its own enablement flag, so it reports UNKNOWN or DISABLED only.
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLEMENT_UNKNOWN);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.KEYS_SUCCESSFULLY_PROVISIONED);

        RemoteKeyProvisioningTiming timing = getTimingMetric(firstAttemptData);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(firstAttemptData);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);

        // Where we actually get a key.
        verifyMetricsForKeyAssignedAfterFreshProvisioning(data.subList(3, 6));
    }

    @Test
    public void testPeriodicProvisionerNoop() throws Exception {
        // First pass of the test will provision some keys
        runIntegrationTest("testPeriodicProvisionerNoop", "RkpdHostTestHelperTests");

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(6);

        // drop the first three metrics, because those are for the first round trip and we've
        // already tested those metrics elsewhere.
        data = data.subList(3, 6);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        // PeriodicProvisioner provisions ALL remotely provisioned components, and each one
        // has its own enablement flag, so it reports UNKNOWN or DISABLED only.
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLEMENT_UNKNOWN);
        assertThat(attempt.getIsKeyPoolEmpty()).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.NO_PROVISIONING_NEEDED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(HTTPS_OK);
    }

    @Test
    public void testPeriodicProvisionerProvisioningDisabled() throws Exception {
        runTest("testPeriodicProvisionerProvisioningDisabled");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.DISABLED);
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.PROVISIONING_DISABLED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);
    }

    @Test
    public void testKeyCreationWithEmptyKeyPool() throws Exception {
        runTest("testKeyCreationWithEmptyKeyPool");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(6);

        final List<EventMetricData> firstAttemptData = data.subList(0, 3);
        RemoteKeyProvisioningAttempt attempt = getAttemptMetric(firstAttemptData);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.KEYS_SUCCESSFULLY_PROVISIONED);

        RemoteKeyProvisioningTiming timing = getTimingMetric(firstAttemptData);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        // We're going over the internet here, so it realistically must take at least 1ms
        assertThat(timing.getServerWaitMillis()).isAtLeast(1);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(firstAttemptData);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);

        verifyMetricsForKeyAssignedAfterFreshProvisioning(data.subList(3, 6));
    }

    @Test
    public void testKeyCreationWorksWhenAllKeysAssigned() throws Exception {
        runTest("testKeyCreationWorksWhenAllKeysAssigned");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(9);

        // first 3 metrics are for fresh key provisioning which we have measured elsewhere.

        // next 3 metrics should be for our of keys since all the keys are assigned.
        final List<EventMetricData> attemptData = data.subList(3, 6);
        RemoteKeyProvisioningAttempt attempt = getAttemptMetric(attemptData);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.KEYS_SUCCESSFULLY_PROVISIONED);

        RemoteKeyProvisioningTiming timing = getTimingMetric(attemptData);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        // We're going over the internet here, so it realistically must take at least 1ms
        assertThat(timing.getServerWaitMillis()).isAtLeast(1);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(attemptData);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);

        // last 3 metrics show key assignment from RegistrationBinder's provisionKeysOnKeyConsumed
        verifyMetricsForKeyAssignedAfterFreshProvisioning(data.subList(6, 9));
    }

    private static RemoteKeyProvisioningAttempt getAttemptMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningAttempt metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningAttempt()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningAttempt();
            }
        }
        return metric;
    }

    private static RemoteKeyProvisioningTiming getTimingMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningTiming metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningTiming()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningTiming();
            }
        }
        return metric;
    }

    private static RemoteKeyProvisioningNetworkInfo getNetworkMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningNetworkInfo metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningNetworkInfo()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningNetworkInfo();
            }
        }
        return metric;
    }

    private void runTest(String testName) throws Exception {
        runIntegrationTest(testName, "KeystoreIntegrationTest");
    }

    private void verifyMetricsForKeyAssignedAfterFreshProvisioning(List<EventMetricData> data) {
        RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.KEY_CONSUMED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo(RPC_DEFAULT);
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.NO_PROVISIONING_NEEDED);

        RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isIn(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        // There are no network calls, but we add network component in metrics class.
        assertThat(network.getHttpStatusError()).isEqualTo(0);
    }
}
