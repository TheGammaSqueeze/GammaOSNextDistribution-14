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

package com.android.server.wifi.hal;

import static android.hardware.wifi.V1_0.NanCipherSuiteType.SHARED_KEY_128_MASK;
import static android.hardware.wifi.V1_0.NanCipherSuiteType.SHARED_KEY_256_MASK;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanConfigRequest;
import android.hardware.wifi.V1_0.NanDataPathSecurityType;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.NanPublishRequest;
import android.hardware.wifi.V1_0.NanRangingIndication;
import android.hardware.wifi.V1_0.NanSubscribeRequest;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_2.NanConfigRequestSupplemental;
import android.net.MacAddress;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.util.Pair;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.aware.Capabilities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

public class WifiNanIfaceHidlImplTest extends WifiBaseTest {
    @Mock IWifiNanIface mIWifiNanIface;
    @Mock android.hardware.wifi.V1_2.IWifiNanIface mIWifiNanIface12Mock;

    @Rule public ErrorCollector collector = new ErrorCollector();

    private class MockableWifiNanIface extends WifiNanIfaceHidlImpl {
        MockableWifiNanIface(IWifiNanIface wifiNanIface) {
            super(wifiNanIface);
        }

        @Override
        public android.hardware.wifi.V1_2.IWifiNanIface mockableCastTo_1_2() {
            return mIsInterface12 ? mIWifiNanIface12Mock : null;
        }
    }

    private boolean mIsInterface12;
    private MockableWifiNanIface mDut;
    private static final Capabilities CAP = new Capabilities();

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        WifiStatus status = new WifiStatus();
        status.code = WifiStatusCode.SUCCESS;
        when(mIWifiNanIface.enableRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface.configRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface.startPublishRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface.startSubscribeRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface.initiateDataPathRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface.respondToDataPathIndicationRequest(anyShort(), any())).thenReturn(
                status);
        when(mIWifiNanIface12Mock.enableRequest_1_2(anyShort(), any(), any())).thenReturn(status);
        when(mIWifiNanIface12Mock.configRequest_1_2(anyShort(), any(), any())).thenReturn(status);
        when(mIWifiNanIface12Mock.startPublishRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIface12Mock.startSubscribeRequest(anyShort(), any())).thenReturn(status);

        mIsInterface12 = false;

        mDut = new MockableWifiNanIface(mIWifiNanIface);
        CAP.supportedDataPathCipherSuites = WIFI_AWARE_CIPHER_SUITE_NCS_SK_128
                | WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
    }

    @Test
    public void testDiscoveryRangingSettings() throws RemoteException {
        short tid = 250;
        byte pid = 34;
        int minDistanceMm = 100;
        int maxDistanceMm = 555;
        short minDistanceCm = (short) (minDistanceMm / 10);
        short maxDistanceCm = (short) (maxDistanceMm / 10);

        ArgumentCaptor<NanPublishRequest> pubCaptor = ArgumentCaptor.forClass(
                NanPublishRequest.class);
        ArgumentCaptor<NanSubscribeRequest> subCaptor = ArgumentCaptor.forClass(
                NanSubscribeRequest.class);

        PublishConfig pubDefault = new PublishConfig.Builder().setServiceName("XXX").build();
        PublishConfig pubWithRanging = new PublishConfig.Builder().setServiceName(
                "XXX").setRangingEnabled(true).build();
        SubscribeConfig subDefault = new SubscribeConfig.Builder().setServiceName("XXX").build();
        SubscribeConfig subWithMin = new SubscribeConfig.Builder().setServiceName(
                "XXX").setMinDistanceMm(minDistanceMm).build();
        SubscribeConfig subWithMax = new SubscribeConfig.Builder().setServiceName(
                "XXX").setMaxDistanceMm(maxDistanceMm).build();
        SubscribeConfig subWithMinMax = new SubscribeConfig.Builder().setServiceName(
                "XXX").setMinDistanceMm(minDistanceMm).setMaxDistanceMm(maxDistanceMm).build();

        mDut.publish(tid, pid, pubDefault, null);
        mDut.publish(tid, pid, pubWithRanging, null);
        mDut.subscribe(tid, pid, subDefault, null);
        mDut.subscribe(tid, pid, subWithMin, null);
        mDut.subscribe(tid, pid, subWithMax, null);
        mDut.subscribe(tid, pid, subWithMinMax, null);

        verify(mIWifiNanIface, times(2)).startPublishRequest(eq(tid), pubCaptor.capture());
        verify(mIWifiNanIface, times(4)).startSubscribeRequest(eq(tid), subCaptor.capture());

        NanPublishRequest halPubReq;
        NanSubscribeRequest halSubReq;

        // pubDefault
        halPubReq = pubCaptor.getAllValues().get(0);
        collector.checkThat("pubDefault.baseConfigs.sessionId", pid,
                equalTo(halPubReq.baseConfigs.sessionId));
        collector.checkThat("pubDefault.baseConfigs.rangingRequired", false,
                equalTo(halPubReq.baseConfigs.rangingRequired));

        // pubWithRanging
        halPubReq = pubCaptor.getAllValues().get(1);
        collector.checkThat("pubWithRanging.baseConfigs.sessionId", pid,
                equalTo(halPubReq.baseConfigs.sessionId));
        collector.checkThat("pubWithRanging.baseConfigs.rangingRequired", true,
                equalTo(halPubReq.baseConfigs.rangingRequired));

        // subDefault
        halSubReq = subCaptor.getAllValues().get(0);
        collector.checkThat("subDefault.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subDefault.baseConfigs.rangingRequired", false,
                equalTo(halSubReq.baseConfigs.rangingRequired));

        // subWithMin
        halSubReq = subCaptor.getAllValues().get(1);
        collector.checkThat("subWithMin.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMin.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMin.baseConfigs.configRangingIndications",
                NanRangingIndication.EGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceEgressCm", minDistanceCm,
                equalTo(halSubReq.baseConfigs.distanceEgressCm));

        // subWithMax
        halSubReq = subCaptor.getAllValues().get(2);
        collector.checkThat("subWithMax.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMax.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMax.baseConfigs.configRangingIndications",
                NanRangingIndication.INGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceIngressCm", maxDistanceCm,
                equalTo(halSubReq.baseConfigs.distanceIngressCm));

        // subWithMinMax
        halSubReq = subCaptor.getAllValues().get(3);
        collector.checkThat("subWithMinMax.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMinMax.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMinMax.baseConfigs.configRangingIndications",
                NanRangingIndication.INGRESS_MET_MASK | NanRangingIndication.EGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceEgressCm", minDistanceCm,
                equalTo(halSubReq.baseConfigs.distanceEgressCm));
        collector.checkThat("subWithMin.baseConfigs.distanceIngressCm", maxDistanceCm,
                equalTo(halSubReq.baseConfigs.distanceIngressCm));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using default values at the default power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsDefaults() throws RemoteException {
        byte default24 = 1;
        byte default5 = 1;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), true,
                        true, true, false, default24, default5, false);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", default5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", default24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified non-interactive values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsNoneInteractive() throws RemoteException {
        byte interactive24 = 3;
        byte interactive5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        false, false, false, interactive24, interactive5, false);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", interactive5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", interactive24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified idle (doze) values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsIdle() throws RemoteException {
        byte idle24 = -1;
        byte idle5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        true, false, true, idle24, idle5, false);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", idle5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", false,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using default values at the default power state.
     *
     * Using HAL 1.2: additional power configurations.
     */
    @Test
    public void testEnableAndConfigPowerSettingsDefaults_1_2() throws RemoteException {
        byte default24 = 1;
        byte default5 = 1;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), true,
                        true, true, false, default24, default5, true);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", default5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", default24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));

        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified non-interactive values when in that power state.
     *
     * Using HAL 1.2: additional power configurations.
     */
    @Test
    public void testEnableAndConfigPowerSettingsNoneInteractive_1_2() throws RemoteException {
        byte interactive24 = 3;
        byte interactive5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        false, false, false, interactive24, interactive5, true);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", interactive5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", interactive24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));

        // Note: still defaults (i.e. disabled) - will be tweaked for low power
        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified idle (doze) values when in that power state.
     *
     * Using HAL 1.2: additional power configurations.
     */
    @Test
    public void testEnableAndConfigPowerSettingsIdle_1_2() throws RemoteException {
        byte idle24 = -1;
        byte idle5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        true, false, true, idle24, idle5, true);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", idle5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", false,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));

        // Note: still defaults (i.e. disabled) - will be tweaked for low power
        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate the initiation of NDP for an open link.
     */
    @Test
    public void testInitiateDataPathOpen() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ false,
                /* usePassphrase */ false,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ 0,
                /* halCipherSuite */ 0);
    }

    /**
     * Validate the initiation of NDP for an PMK protected link with in-band discovery.
     */
    @Test
    public void testInitiateDataPathPmkInBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK);

    }

    /**
     * Validate the initiation of NDP for an Passphrase protected link with in-band discovery.
     */
    @Test
    public void testInitiateDataPathPassphraseInBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ false,
                /* usePassphrase */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK);
    }

    /**
     * Validate the initiation of NDP for an PMK protected link with out-of-band discovery.
     */
    @Test
    public void testInitiateDataPathPmkOutOfBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* isOutOfBand */ true,
                /* supportedCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* expectedCipherSuite */ SHARED_KEY_128_MASK);
    }

    /**
     * Validate the response to an NDP request for an open link.
     */
    @Test
    public void testRespondToDataPathRequestOpenInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ false,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */  WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK);
    }

    /**
     * Validate the response to an NDP request for a PMK-protected link with in-band discovery.
     */
    @Test
    public void testRespondToDataPathRequestPmkInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK);
    }

    /**
     * Validate the response to an NDP request for a Passphrase-protected link with in-band
     * discovery.
     */
    @Test
    public void testRespondToDataPathRequestPassphraseInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ false,
                /* usePassphrase */ true,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK);
    }

    /**
     * Validate the response to an NDP request for a PMK-protected link with out-of-band discovery.
     */
    @Test
    public void testRespondToDataPathRequestPmkOutOfBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ true,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK);
    }

    /**
     * Validate the response to an NDP request - when request is rejected.
     */
    @Test
    public void testRespondToDataPathRequestReject() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ false,
                /* isOutOfBand */ true,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ 0);
    }


    // utilities

    private Pair<NanConfigRequest, NanConfigRequestSupplemental> validateEnableAndConfigure(
            short transactionId, ConfigRequest configRequest, boolean notifyIdentityChange,
            boolean initialConfiguration, boolean isInteractive, boolean isIdle,
            int discoveryWindow24Ghz, int discoveryWindow5Ghz, boolean isHal12)
            throws RemoteException {
        mIsInterface12 = isHal12;

        mDut.enableAndConfigure(transactionId, configRequest, notifyIdentityChange,
                initialConfiguration, false, false, 2437, -1 /* clusterId */,
                1800 /* PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT */,
                getPowerParams(isInteractive, isIdle, discoveryWindow24Ghz, discoveryWindow5Ghz));

        ArgumentCaptor<NanEnableRequest> enableReqCaptor = ArgumentCaptor.forClass(
                NanEnableRequest.class);
        ArgumentCaptor<NanConfigRequest> configReqCaptor = ArgumentCaptor.forClass(
                NanConfigRequest.class);
        ArgumentCaptor<NanConfigRequestSupplemental> configSuppCaptor = ArgumentCaptor.forClass(
                NanConfigRequestSupplemental.class);
        NanConfigRequest config;
        NanConfigRequestSupplemental configSupp = null;

        if (initialConfiguration) {
            if (isHal12) {
                verify(mIWifiNanIface12Mock).enableRequest_1_2(eq(transactionId),
                        enableReqCaptor.capture(), configSuppCaptor.capture());
                configSupp = configSuppCaptor.getValue();
            } else {
                verify(mIWifiNanIface).enableRequest(eq(transactionId),
                        enableReqCaptor.capture());
            }
            config = enableReqCaptor.getValue().configParams;
        } else {
            if (isHal12) {
                verify(mIWifiNanIface12Mock).configRequest_1_2(eq(transactionId),
                        configReqCaptor.capture(), configSuppCaptor.capture());
                configSupp = configSuppCaptor.getValue();
            } else {
                verify(mIWifiNanIface).configRequest(eq(transactionId),
                        configReqCaptor.capture());
            }
            config = configReqCaptor.getValue();
        }

        collector.checkThat("disableDiscoveryAddressChangeIndication", !notifyIdentityChange,
                equalTo(config.disableDiscoveryAddressChangeIndication));
        collector.checkThat("disableStartedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableStartedClusterIndication));
        collector.checkThat("disableJoinedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableJoinedClusterIndication));

        return new Pair<>(config, configSupp);
    }

    private WifiNanIface.PowerParameters getPowerParams(boolean isInteractive, boolean isIdle,
            int discoveryWindow24Ghz, int discoveryWindow5Ghz) {
        WifiNanIface.PowerParameters params = new WifiNanIface.PowerParameters();
        params.discoveryBeaconIntervalMs = 0;   // PARAM_DISCOVERY_BEACON_INTERVAL_MS_DEFAULT
        params.enableDiscoveryWindowEarlyTermination = false;  // PARAM_ENABLE_DW_EARLY_TERM_DEFAULT
        params.numberOfSpatialStreamsInDiscovery = 0;   // PARAM_NUM_SS_IN_DISCOVERY_DEFAULT
        if (isInteractive && !isIdle) {
            params.discoveryWindow24Ghz = 1;    // PARAM_DW_24GHZ_DEFAULT
            params.discoveryWindow5Ghz = 1;     // PARAM_DW_5GHZ_DEFAULT
            params.discoveryWindow6Ghz = 1;     // PARAM_DW_6GHZ_DEFAULT
        } else {
            params.discoveryWindow24Ghz = discoveryWindow24Ghz;
            params.discoveryWindow5Ghz = discoveryWindow5Ghz;
            params.discoveryWindow6Ghz = 0;
        }
        return params;
    }

    private void validateInitiateDataPath(boolean usePmk, boolean usePassphrase,
            boolean isOutOfBand, int publicCipherSuites, int halCipherSuite)
            throws Exception {
        short tid = 44;
        int peerId = 555;
        int channelRequestType =
                android.hardware.wifi.V1_0.NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED;
        int channel = 2146;
        MacAddress peer = MacAddress.fromString("00:01:02:03:04:05");
        String interfaceName = "aware_data5";
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        String passphrase = "blahblah";
        final byte[] appInfo = "Out-of-band info".getBytes();

        ArgumentCaptor<android.hardware.wifi.V1_0.NanInitiateDataPathRequest> captor =
                ArgumentCaptor.forClass(
                        android.hardware.wifi.V1_0.NanInitiateDataPathRequest.class);
        WifiAwareDataPathSecurityConfig securityConfig = null;
        if (usePassphrase) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPskPassphrase(passphrase)
                    .build();
        } else if (usePmk) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPmk(pmk)
                    .build();
        }

        mDut.initiateDataPath(tid, peerId, channelRequestType, channel, peer, interfaceName,
                isOutOfBand, appInfo, CAP, securityConfig, (byte) 0);

        verify(mIWifiNanIface).initiateDataPathRequest(eq(tid), captor.capture());

        android.hardware.wifi.V1_0.NanInitiateDataPathRequest nidpr = captor.getValue();
        collector.checkThat("peerId", peerId, equalTo(nidpr.peerId));
        collector.checkThat("peerDiscMacAddr", peer.toByteArray(),
                equalTo(nidpr.peerDiscMacAddr));
        collector.checkThat("channelRequestType", channelRequestType,
                equalTo(nidpr.channelRequestType));
        collector.checkThat("channel", channel, equalTo(nidpr.channel));
        collector.checkThat("ifaceName", interfaceName, equalTo(nidpr.ifaceName));

        if (usePmk) {
            collector.checkThat("securityConfig.securityType",
                    NanDataPathSecurityType.PMK,
                    equalTo(nidpr.securityConfig.securityType));
            collector.checkThat("securityConfig.cipherType", halCipherSuite,
                    equalTo(nidpr.securityConfig.cipherType));
            collector.checkThat("securityConfig.pmk", pmk, equalTo(nidpr.securityConfig.pmk));
            collector.checkThat("securityConfig.passphrase.length", 0,
                    equalTo(nidpr.securityConfig.passphrase.size()));
        }

        if (usePassphrase) {
            collector.checkThat("securityConfig.securityType",
                    NanDataPathSecurityType.PASSPHRASE,
                    equalTo(nidpr.securityConfig.securityType));
            collector.checkThat("securityConfig.cipherType", halCipherSuite,
                    equalTo(nidpr.securityConfig.cipherType));
            collector.checkThat("securityConfig.passphrase", passphrase.getBytes(),
                    equalTo(convertArrayListToNativeByteArray(nidpr.securityConfig.passphrase)));
        }

        collector.checkThat("appInfo", appInfo,
                equalTo(convertArrayListToNativeByteArray(nidpr.appInfo)));

        if ((usePmk || usePassphrase) && isOutOfBand) {
            collector.checkThat("serviceNameOutOfBand",
                    WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(),
                    equalTo(convertArrayListToNativeByteArray(nidpr.serviceNameOutOfBand)));
        } else {
            collector.checkThat("serviceNameOutOfBand.length", 0,
                    equalTo(nidpr.serviceNameOutOfBand.size()));
        }
    }

    private void validateRespondToDataPathRequest(boolean usePmk, boolean usePassphrase,
            boolean accept, boolean isOutOfBand, int publicCipherSuites, int halCipherSuite)
            throws Exception {
        short tid = 33;
        int ndpId = 44;
        String interfaceName = "aware_whatever22";
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        String passphrase = "blahblah";
        final byte[] appInfo = "Out-of-band info".getBytes();

        ArgumentCaptor<android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest> captor =
                ArgumentCaptor.forClass(
                        android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest.class);
        WifiAwareDataPathSecurityConfig securityConfig = null;
        if (usePassphrase) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPskPassphrase(passphrase)
                    .build();
        } else if (usePmk) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPmk(pmk)
                    .build();
        }

        mDut.respondToDataPathRequest(tid, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, CAP, securityConfig, (byte) 0);

        verify(mIWifiNanIface).respondToDataPathIndicationRequest(eq(tid), captor.capture());

        android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest nrtdpir =
                captor.getValue();
        collector.checkThat("acceptRequest", accept, equalTo(nrtdpir.acceptRequest));
        collector.checkThat("ndpInstanceId", ndpId, equalTo(nrtdpir.ndpInstanceId));
        collector.checkThat("ifaceName", interfaceName, equalTo(nrtdpir.ifaceName));

        if (accept) {
            if (usePmk) {
                collector.checkThat("securityConfig.securityType",
                        NanDataPathSecurityType.PMK,
                        equalTo(nrtdpir.securityConfig.securityType));
                collector.checkThat("securityConfig.cipherType", halCipherSuite,
                        equalTo(nrtdpir.securityConfig.cipherType));
                collector.checkThat("securityConfig.pmk", pmk, equalTo(nrtdpir.securityConfig.pmk));
                collector.checkThat("securityConfig.passphrase.length", 0,
                        equalTo(nrtdpir.securityConfig.passphrase.size()));
            }

            if (usePassphrase) {
                collector.checkThat("securityConfig.securityType",
                        NanDataPathSecurityType.PASSPHRASE,
                        equalTo(nrtdpir.securityConfig.securityType));
                collector.checkThat("securityConfig.cipherType", halCipherSuite,
                        equalTo(nrtdpir.securityConfig.cipherType));
                collector.checkThat("securityConfig.passphrase", passphrase.getBytes(),
                        equalTo(convertArrayListToNativeByteArray(
                                nrtdpir.securityConfig.passphrase)));
            }

            collector.checkThat("appInfo", appInfo,
                    equalTo(convertArrayListToNativeByteArray(nrtdpir.appInfo)));

            if ((usePmk || usePassphrase) && isOutOfBand) {
                collector.checkThat("serviceNameOutOfBand",
                        WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(),
                        equalTo(convertArrayListToNativeByteArray(nrtdpir.serviceNameOutOfBand)));
            } else {
                collector.checkThat("serviceNameOutOfBand.length", 0,
                        equalTo(nrtdpir.serviceNameOutOfBand.size()));
            }
        }
    }

    private byte[] convertArrayListToNativeByteArray(ArrayList<Byte> from) {
        if (from == null) {
            return null;
        }

        byte[] to = new byte[from.size()];
        for (int i = 0; i < from.size(); ++i) {
            to[i] = from.get(i);
        }
        return to;
    }
}
