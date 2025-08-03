/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_NONE;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_PSK;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.net.MacAddress;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.LocalLog;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiNetworkSelector.NetworkNominator.OnConnectableListener;
import com.android.server.wifi.WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs;
import com.android.server.wifi.entitlement.PseudonymInfo;
import com.android.server.wifi.hotspot2.PasspointNetworkNominateHelper;
import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Unit tests for {@link SavedNetworkNominator}.
 */
@SmallTest
public class SavedNetworkNominatorTest extends WifiBaseTest {

    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession = mockitoSession()
                .mockStatic(WifiInjector.class)
                .startMocking();
        lenient().when(WifiInjector.getInstance()).thenReturn(mWifiInjector);
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        when(mPrimaryClientModeManager.getSupportedFeatures()).thenReturn(
                WifiManager.WIFI_FEATURE_WPA3_SAE | WifiManager.WIFI_FEATURE_OWE);
        when(mWifiGlobals.isWpa3SaeUpgradeEnabled()).thenReturn(true);
        when(mWifiGlobals.isOweUpgradeEnabled()).thenReturn(true);

        mLocalLog = new LocalLog(512);
        mSavedNetworkNominator = new SavedNetworkNominator(mWifiConfigManager,
                mLocalLog, mWifiCarrierInfoManager,
                mWifiPseudonymManager, mWifiPermissionsUtil, mWifiNetworkSuggestionsManager);
        when(mWifiCarrierInfoManager.isSimReady(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(VALID_SUBID);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(VALID_SUBID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(anyInt())).thenReturn(true);
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(VALID_SUBID);
        when(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(any(), any()))
                .thenReturn(false);

    }

    /** Cleans up test. */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (null != mStaticMockSession) {
            mStaticMockSession.finishMocking();
        }
    }

    private ArgumentCaptor<WifiConfiguration> mWifiConfigurationArgumentCaptor =
            ArgumentCaptor.forClass(WifiConfiguration.class);
    private static final int VALID_SUBID = 10;
    private static final int INVALID_SUBID = 1;
    private static final int TEST_CARRIER_ID = 100;
    private static final int RSSI_LEVEL = -50;
    private static final int TEST_UID = 1001;

    private SavedNetworkNominator mSavedNetworkNominator;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private Clock mClock;
    @Mock private OnConnectableListener mOnConnectableListener;
    @Mock private WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock private WifiPseudonymManager mWifiPseudonymManager;
    @Mock private PasspointNetworkNominateHelper mPasspointNetworkNominateHelper;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private @Mock WifiInjector mWifiInjector;
    private @Mock ActiveModeWarden mActiveModeWarden;
    private @Mock ClientModeManager mPrimaryClientModeManager;
    private @Mock WifiGlobals mWifiGlobals;
    private MockitoSession mStaticMockSession = null;
    private LocalLog mLocalLog;
    private List<Pair<ScanDetail, WifiConfiguration>> mPasspointCandidates =
            Collections.emptyList();

    /**
     * Do not evaluate networks that {@link WifiConfiguration#useExternalScores}.
     */
    @Test
    public void ignoreNetworksIfUseExternalScores() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-PSK][ESS]", "[WPA2-PSK][ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        for (WifiConfiguration wifiConfiguration : savedConfigs) {
            wifiConfiguration.useExternalScores = true;
        }

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, null, false, true, true, Collections.emptySet(), mOnConnectableListener
        );

        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    /**
     * Do not evaluate networks which require SIM card when the SIM card is absent.
     */
    @Test
    public void ignoreNetworkIfSimIsAbsentForEapSimNetwork() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        int[] levels = {RSSI_LEVEL};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigForEapSimNetwork(ssids, bssids,
                        freqs, levels, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].carrierId = TEST_CARRIER_ID;
        // SIM is absent
        when(mWifiCarrierInfoManager.getMatchingSubId(TEST_CARRIER_ID)).thenReturn(INVALID_SUBID);
        when(mWifiCarrierInfoManager.isSimReady(eq(INVALID_SUBID))).thenReturn(false);

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );

        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    /**
     * Do not evaluate networks that {@link WifiConfiguration#isEphemeral}.
     */
    @Test
    public void ignoreEphemeralNetworks() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_NONE, SECURITY_NONE};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        for (WifiConfiguration wifiConfiguration : savedConfigs) {
            wifiConfiguration.ephemeral = true;
        }

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );

        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    /**
     * Pick a worse candidate that allows auto-join over a better candidate that
     * disallows auto-join.
     */
    @Test
    public void ignoreNetworksIfAutojoinNotAllowed() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_NONE, SECURITY_NONE};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, null, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );

        verify(mOnConnectableListener, times(2)).onConnectable(any(), any());
        reset(mOnConnectableListener);
        savedConfigs[1].allowAutojoin = false;
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(any(),
                mWifiConfigurationArgumentCaptor.capture());
        WifiConfigurationTestUtil.assertConfigurationEqual(savedConfigs[0],
                mWifiConfigurationArgumentCaptor.getValue());
    }

    /**
     * Do not return a candidate if all networks do not {@link WifiConfiguration#allowAutojoin}
     */
    @Test
    public void returnNoCandidateIfNoNetworksAllowAutojoin() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_NONE, SECURITY_NONE};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        for (WifiConfiguration wifiConfiguration : savedConfigs) {
            wifiConfiguration.allowAutojoin = false;
        }
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    /**
     * Ensure that we do nominate the only matching saved passponit network with auto-join enabled .
     */
    @Test
    public void returnCandidatesIfPasspointNetworksAvailableWithAutojoinEnabled() {
        ScanDetail scanDetail1 = mock(ScanDetail.class);
        ScanDetail scanDetail2 = mock(ScanDetail.class);
        List<ScanDetail> scanDetails = Arrays.asList(scanDetail1, scanDetail2);
        WifiConfiguration configuration1 = mock(WifiConfiguration.class);
        configuration1.allowAutojoin = true;
        WifiConfiguration configuration2 = mock(WifiConfiguration.class);
        configuration2.allowAutojoin = false;
        List<Pair<ScanDetail, WifiConfiguration>> passpointCandidates =
                Arrays.asList(Pair.create(scanDetail1, configuration1), Pair.create(scanDetail2,
                        configuration2));
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, passpointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(scanDetail1, configuration1);
        verify(mOnConnectableListener, never()).onConnectable(scanDetail2, configuration2);
    }

    /**
     * Verify if a network is metered and with non-data sim, will not nominate as a candidate.
     */
    @Test
    public void testIgnoreNetworksIfMeteredAndFromNonDataSim() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        int[] levels = {RSSI_LEVEL};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigForEapSimNetwork(ssids, bssids,
                        freqs, levels, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].carrierId = TEST_CARRIER_ID;
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(savedConfigs[0]))
                .thenReturn(false);
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(any(), any());
        reset(mOnConnectableListener);
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(savedConfigs[0]))
                .thenReturn(true);
        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    /**
     * Verify a saved network is from app not user, if IMSI privacy protection is not required, will
     * send notification for user to approve exemption, and not consider as a candidate.
     */
    @Test
    public void testIgnoreNetworksFromAppIfNoImsiProtection() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        int[] levels = {RSSI_LEVEL};
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(any()))
                .thenReturn(false);
        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigForEapSimNetwork(ssids, bssids,
                        freqs, levels, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].carrierId = TEST_CARRIER_ID;
        // Doesn't require Imsi protection and user didn't approved
        when(mWifiCarrierInfoManager.requiresImsiEncryption(VALID_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener, never()).onConnectable(any(), any());
        verify(mWifiCarrierInfoManager)
                .sendImsiProtectionExemptionNotificationIfRequired(TEST_CARRIER_ID);
        // Simulate user approved
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(true);
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(any(), any());
        // If from settings app, will bypass the IMSI check.
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
    }

    /**
     * Verify a saved network is from app not user, if OOB pseudonym is enabled, but not available,
     * it shouldn't be considered as a candidate.
     */
    @Test
    public void testAllowNetworksIfOobPseudonymEnabledForImsiProtection() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        int[] levels = {RSSI_LEVEL};
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(any()))
                .thenReturn(false);
        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigForEapSimNetwork(ssids, bssids,
                        freqs, levels, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].carrierId = TEST_CARRIER_ID;
        // Doesn't require Imsi protection and user didn't approved
        when(mWifiCarrierInfoManager.requiresImsiEncryption(VALID_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(true);
        when(mWifiPseudonymManager.getValidPseudonymInfo(TEST_CARRIER_ID))
                .thenReturn(Optional.of(mock(PseudonymInfo.class)));

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );

        verify(mWifiPseudonymManager).updateWifiConfiguration(any());
        verify(mOnConnectableListener).onConnectable(any(), any());
    }

    @Test
    public void testIgnoreNetworksIfOobPseudonymEnabledButNotAvailableForImsiProtection() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        int[] levels = {RSSI_LEVEL};
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(any()))
                .thenReturn(false);
        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigForEapSimNetwork(ssids, bssids,
                        freqs, levels, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].carrierId = TEST_CARRIER_ID;
        // Doesn't require Imsi protection and user didn't approved
        when(mWifiCarrierInfoManager.requiresImsiEncryption(VALID_SUBID)).thenReturn(false);
        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(TEST_CARRIER_ID))
                .thenReturn(false);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(true);
        when(mWifiPseudonymManager.getValidPseudonymInfo(TEST_CARRIER_ID))
                .thenReturn(Optional.empty());

        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );

        verify(mWifiPseudonymManager).retrievePseudonymOnFailureTimeoutExpired(any());
        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }

    @Test
    public void testIgnoreOpenNetworkWithSameNetworkSuggestionHasSecureNetworkFromSameCarrier() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[ESS]"};
        int[] levels = {RSSI_LEVEL};
        int[] securities = {SECURITY_NONE};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();

        when(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(any(), any()))
                .thenReturn(true);
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener, never()).onConnectable(any(), any());

        when(mWifiNetworkSuggestionsManager
                .shouldBeIgnoredBySecureSuggestionFromSameCarrier(any(), any()))
                .thenReturn(false);
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, mPasspointCandidates, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(any(), any());
    }

    /**
     * Only return the candidate in the BSSID allow list.
     */
    @Test
    public void returnOnlyCandidateWithBssidInAllowList() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_NONE};
        ArgumentCaptor<ScanDetail> captor = ArgumentCaptor.forClass(ScanDetail.class);

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].setBssidAllowlist(List.of(MacAddress.fromString(bssids[0])));
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, null, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener).onConnectable(captor.capture(), any());
        assertEquals(bssids[0], captor.getValue().getBSSIDString());
    }

    /**
     * Return no candidate when BSSID allow list is empty.
     */
    @Test
    public void returnNoCandidateWithEmptyBssidAllowList() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {RSSI_LEVEL, RSSI_LEVEL};
        int[] securities = {SECURITY_NONE};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].setBssidAllowlist(Collections.emptyList());
        mSavedNetworkNominator.nominateNetworks(
                scanDetails, null, false, true, true, Collections.emptySet(),
                mOnConnectableListener
        );
        verify(mOnConnectableListener, never()).onConnectable(any(), any());
    }
}
