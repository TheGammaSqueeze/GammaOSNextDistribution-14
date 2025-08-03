/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED;

import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_EAP;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_PSK;
import static com.android.server.wifi.WifiConfigurationTestUtil.generateWifiConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiSsid;
import android.os.Process;
import android.telephony.SubscriptionManager;
import android.util.LocalLog;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiNetworkSuggestionsManager.ExtendedWifiNetworkSuggestion;
import com.android.server.wifi.WifiNetworkSuggestionsManager.PerAppInfo;
import com.android.server.wifi.entitlement.PseudonymInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unit tests for {@link NetworkSuggestionNominator}.
 */
@SmallTest
public class NetworkSuggestionNominatorTest extends WifiBaseTest {
    private static final int TEST_UID = 3555;
    private static final int TEST_UID_OTHER = 3545;
    private static final int TEST_NETWORK_ID = 55;
    private static final String TEST_PACKAGE = "com.test";
    private static final String TEST_PACKAGE_OTHER = "com.test.other";
    private static final String TEST_FQDN = "fqdn";
    private static final int TEST_CARRIER_ID = 1911;
    private static final String TEST_CARRIER_NAME = "testCarrier";
    private static final int TEST_SUB_ID = 2020;
    private static final String PASSPOINT_UNIQUE_ID = "uniqueId";


    private @Mock WifiConfigManager mWifiConfigManager;
    private @Mock WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private @Mock Clock mClock;
    private @Mock WifiCarrierInfoManager mWifiCarrierInfoManager;
    private @Mock WifiPseudonymManager mWifiPseudonymManager;
    private @Mock WifiMetrics mWifiMetrics;
    private NetworkSuggestionNominator mNetworkSuggestionNominator;
    private List<Pair<ScanDetail, WifiConfiguration>> mPasspointCandidates =
            Collections.emptyList();

    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mNetworkSuggestionNominator = new NetworkSuggestionNominator(
                mWifiNetworkSuggestionsManager, mWifiConfigManager,
                new LocalLog(100), mWifiCarrierInfoManager, mWifiPseudonymManager,
                mWifiMetrics);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        when(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(anyString())).thenReturn(false);
    }

    /**
     * Ensure that we ignore all scan results not matching the network suggestion.
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForNoMatch() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {};
        int[] securities = {};
        boolean[] appInteractions = {};
        boolean[] meteredness = {};
        int[] priorities = {};
        int[] uids = {};
        String[] packageNames = {};
        boolean[] autojoin = {};
        boolean[] shareWithUser = {};
        int[] priorityGroup = {};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the only matching network suggestion.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatch() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};
        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    @Test
    public void testSelectNetworkSuggestionForOneMatchWithInsecureEnterpriseSuggestion() {
        String[] scanSsids = {"test1"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_EAP};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        WifiConfiguration config = suggestions[0].wns.wifiConfiguration;
        config.enterpriseConfig.setCaPath(null);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        // Verify no network is nominated.
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the all network suggestion corresponding to the scan results
     * Expected connectable Networks: {suggestionSsids[0], suggestionSsids[1]}
     */
    @Test
    public void testSelectNetworkSuggestionForMultipleMatch() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-56, -45};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true};
        boolean[] meteredness = {true, true};
        int[] priorities = {-1, -1};
        int[] uids = {TEST_UID, TEST_UID};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE};
        boolean[] autojoin = {true, true};
        boolean[] shareWithUser = {true, true};
        int[] priorityGroup = {0, 0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0], scanSsids[1]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the network suggestion corresponding to the scan result with
     * higest priority.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForMultipleMatchHighPriorityWins() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-56, -45};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true};
        boolean[] meteredness = {true, true};
        int[] priorities = {5, 1};
        int[] uids = {TEST_UID, TEST_UID};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE};
        boolean[] autojoin = {true, true};
        boolean[] shareWithUser = {true, true};
        int[] priorityGroup = {1, 1};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the network suggestion corresponding to the scan result with
     * higest priority from each priority group within same suggestor app.
     * Expected connectable Networks: {suggestionSsids[0], suggestionSsids[2]}
     */
    @Test
    public void testSelectNetworkSuggestionForMultipleMatchHighPriorityFromEachPriorityGroupWins() {
        String[] scanSsids = {"test1", "test2", "test3", "test4"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4", "6c:f3:7f:ae:8c:f5",
                "6c:f3:7f:ae:8c:f6"};
        int[] freqs = {2470, 2437, 2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]",
                "[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-56, -45, -44, -43};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\"",
                "\"" + scanSsids[2] + "\"", "\"" + scanSsids[3] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK, SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true, true, true};
        boolean[] meteredness = {false, false, false, false};
        int[] priorities = {5, 4, 3, 1};
        int[] uids = {TEST_UID, TEST_UID, TEST_UID, TEST_UID};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE, TEST_PACKAGE, TEST_PACKAGE};
        boolean[] autojoin = {true, true, true, true};
        boolean[] shareWithUser = {true, true, true, true};
        int[] priorityGroup = {0, 0 , 1 , 1};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);

        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1], suggestions[2], suggestions[3]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0], scanSsids[2]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate all profiles when multiple suggestor suggested same network.
     *
     * Expected connectable Networks: {suggestionSsids[0], suggestionSsids[1], suggestionSsids[2]}
     */
    @Test
    public void testSelectNetworkSuggestionForMultipleMatchWithMultipleSuggestions() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-23, -45};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\"",
                "\"" + scanSsids[1] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true, false};
        boolean[] meteredness = {true, true, false};
        int[] priorities = {-1, -1, -1};
        int[] uids = {TEST_UID, TEST_UID, TEST_UID_OTHER};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE, TEST_PACKAGE_OTHER};
        boolean[] autojoin = {true, true, true};
        boolean[] shareWithUser = {true, true, true};
        int[] priorityGroup = {0, 0, 0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1], suggestions[2]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0], scanSsids[1], scanSsids[1]);
        verify(mWifiMetrics).incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the network suggestion with the higest priority among network
     * suggestions from the same package. Among different packages, nominate all the suggestion
     * corresponding to the scan result.
     *
     * The suggestion[1] has higher priority than suggestion[0].
     *
     * Expected connectable Networks: {suggestionSsids[1],
     *                                 (suggestionSsids[2],
     *                                  suggestionSsids[3]}
     */
    @Test
    public void
            testSelectNetworkSuggestionForMultipleMatchWithMultipleSuggestionsHighPriorityWins() {
        String[] scanSsids = {"test1", "test2", "test3", "test4"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4", "6c:fc:de:34:12",
                "6c:fd:a1:11:11:98"};
        int[] freqs = {2470, 2437, 2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]",
                "[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-23, -45, -56, -65};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\"",
                "\"" + scanSsids[2] + "\"", "\"" + scanSsids[3] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK, SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true, false, false};
        boolean[] meteredness = {true, true, false, false};
        int[] priorities = {0, 5, -1, -1};
        int[] uids = {TEST_UID, TEST_UID, TEST_UID_OTHER, TEST_UID_OTHER};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE, TEST_PACKAGE_OTHER,
                TEST_PACKAGE_OTHER};
        boolean[] autojoin = {true, true, true, true};
        boolean[] shareWithUser = {true, true, true, true};
        int[] priorityGroup = {0, 0, 0, 0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1], suggestions[2], suggestions[3]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[1], scanSsids[2], scanSsids[3]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no candidate if the only matching network suggestion, but we failed
     * the {@link WifiConfigManager} interactions.
     *
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchButInToWifiConfigManager() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {1};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());

        verify(mWifiConfigManager).getConfiguredNetwork(eq(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getProfileKey()));
        // Verify we did not try to add any new networks or other interactions with
        // WifiConfigManager.
        verifyNoMoreInteractions(mWifiConfigManager);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the only matching network suggestion, but that matches an existing
     * saved network (maybe saved or maybe it exists from a previous connection attempt) .
     *
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchForExistingNetwork() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        suggestions[0].wns.wifiConfiguration.fromWifiNetworkSuggestion = true;
        suggestions[0].wns.wifiConfiguration.ephemeral = true;
        setupAddToWifiConfigManager(suggestions[0]);
        // Existing saved network matching the credentials.
        when(mWifiConfigManager.getConfiguredNetwork(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getKey()))
                .thenReturn(suggestions[0].wns.wifiConfiguration);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, new String[] {scanSsids[0]});

        // check for any saved networks.
        verify(mWifiConfigManager, times(suggestionSsids.length))
                .isNetworkTemporarilyDisabledByUser(anyString());
        verify(mWifiConfigManager).getConfiguredNetwork(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getProfileKey());
        verify(mWifiConfigManager).isNonCarrierMergedNetworkTemporarilyDisabled(any());
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiConfigManager).getConfiguredNetwork(suggestions[0]
                    .createInternalWifiConfiguration(mWifiCarrierInfoManager).getKey());
        }
        // Verify we did not try to add any new networks or other interactions with
        // WifiConfigManager.
        verifyNoMoreInteractions(mWifiConfigManager);
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiMetrics).addSuggestionExistsForSavedNetwork(
                    suggestions[0].wns.wifiConfiguration.getKey());
        }
    }

    /**
     * Ensure that we don't nominate the only matching network suggestion if it was previously
     * disabled by the user.
     *
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchButUserForgotTheNetwork() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);
        // Network was disabled by the user.
        when(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(suggestionSsids[0]))
                .thenReturn(true);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) ->
                        connectableNetworks.add(Pair.create(scanDetail, configuration)));

        assertTrue(connectableNetworks.isEmpty());

        verify(mWifiConfigManager, times(suggestionSsids.length))
                .isNetworkTemporarilyDisabledByUser(anyString());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we don't nominate the only matching network suggestion if the network
     * configuration already exists (maybe saved or maybe it exists from a previous connection
     * attempt) and blacklisted.
     *
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchForExistingNetworkButTempDisabled() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // Mark the network disabled.
        suggestions[0].wns.wifiConfiguration.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NETWORK_SELECTION_TEMPORARY_DISABLED);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        // Existing network matching the credentials.
        when(mWifiConfigManager.getConfiguredNetwork(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getProfileKey()))
                .thenReturn(suggestions[0].wns.wifiConfiguration);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiConfigManager).getConfiguredNetwork(eq(
                suggestions[0].wns.wifiConfiguration.getProfileKey()));
        verify(mWifiConfigManager).tryEnableNetwork(eq(
                suggestions[0].wns.wifiConfiguration.networkId));
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiConfigManager).getConfiguredNetwork(eq(
                    suggestions[0].wns.wifiConfiguration.getKey()));
        }
        // Verify we did not try to add any new networks or other interactions with
        // WifiConfigManager.
        verifyNoMoreInteractions(mWifiConfigManager);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we do nominate the only matching network suggestion if the network configuration
     * already exists (maybe saved or maybe it exists from a previous connection attempt) and a
     * temporary blacklist expired.
     *
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchForExistingNetworkButTempDisableExpired() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        suggestions[0].wns.wifiConfiguration.fromWifiNetworkSuggestion = true;
        suggestions[0].wns.wifiConfiguration.ephemeral = true;
        setupAddToWifiConfigManager(suggestions[0]);
        // Mark the network disabled.
        suggestions[0].wns.wifiConfiguration.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NETWORK_SELECTION_TEMPORARY_DISABLED);
        // Existing network matching the credentials.
        when(mWifiConfigManager.getConfiguredNetwork(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getProfileKey()))
                .thenReturn(suggestions[0].wns.wifiConfiguration);
        when(mWifiConfigManager.tryEnableNetwork(suggestions[0].wns.wifiConfiguration.networkId))
                .thenReturn(true);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, new String[] {scanSsids[0]});

        verify(mWifiConfigManager, times(suggestionSsids.length))
                .isNetworkTemporarilyDisabledByUser(anyString());
        verify(mWifiConfigManager).getConfiguredNetwork(eq(suggestions[0]
                .createInternalWifiConfiguration(mWifiCarrierInfoManager).getProfileKey()));
        verify(mWifiConfigManager).tryEnableNetwork(eq(
                suggestions[0].wns.wifiConfiguration.networkId));
        verify(mWifiConfigManager).isNonCarrierMergedNetworkTemporarilyDisabled(any());
        if (SdkLevel.isAtLeastS()) {
            verify(mWifiConfigManager).getConfiguredNetwork(eq(suggestions[0]
                    .createInternalWifiConfiguration(mWifiCarrierInfoManager).getKey()));
        }
        // Verify we did not try to add any new networks or other interactions with
        // WifiConfigManager.
        verifyNoMoreInteractions(mWifiConfigManager);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we do nominate the only matching passponit network suggestion.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSuggestionPasspointNetworkCandidatesMatches() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids, packageNames,
                autojoin, shareWithUser, priorityGroup);
        HashSet<ExtendedWifiNetworkSuggestion> matchedExtSuggestions = new HashSet<>();
        matchedExtSuggestions.add(suggestions[0]);
        List<Pair<ScanDetail, WifiConfiguration>> passpointCandidates = new ArrayList<>();
        suggestions[0].wns.wifiConfiguration.FQDN = TEST_FQDN;
        suggestions[0].wns.wifiConfiguration.setPasspointUniqueId(PASSPOINT_UNIQUE_ID);

        passpointCandidates.add(Pair.create(scanDetails[0],
                suggestions[0].createInternalWifiConfiguration(mWifiCarrierInfoManager)));
        when(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN))
                .thenReturn(matchedExtSuggestions);
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), passpointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });
        assertEquals(1, connectableNetworks.size());
        validateConnectableNetworks(connectableNetworks, new String[] {scanSsids[0]});
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we don't nominate the only matching passponit network suggestion when it's not
     * approved.
     * Expected connectable Networks: {}
     */
    @Test
    public void testSuggestionPasspointNetworkCandidatesNoApprovedMatches() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids, packageNames,
                autojoin, shareWithUser, priorityGroup);
        HashSet<ExtendedWifiNetworkSuggestion> matchedExtSuggestions = new HashSet<>();
        matchedExtSuggestions.add(suggestions[0]);
        List<Pair<ScanDetail, WifiConfiguration>> passpointCandidates = new ArrayList<>();
        suggestions[0].wns.wifiConfiguration.FQDN = TEST_FQDN;
        suggestions[0].wns.wifiConfiguration.setPasspointUniqueId(PASSPOINT_UNIQUE_ID);

        passpointCandidates.add(Pair.create(scanDetails[0],
                suggestions[0].createInternalWifiConfiguration(mWifiCarrierInfoManager)));
        // As user haven't approved this suggestion, return null
        when(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(TEST_FQDN))
                .thenReturn(Set.of());
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), passpointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we will not nominate the matching network suggestions which auto-join is
     * disabled.
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchButAutojoinDisabled() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\"", "\"" + scanSsids[1] + "\""};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};
        boolean[] appInteractions = {true, true};
        boolean[] meteredness = {true, true};
        int[] priorities = {-1, -1};
        int[] uids = {TEST_UID, TEST_UID};
        String[] packageNames = {TEST_PACKAGE, TEST_PACKAGE};
        boolean[] autojoin = {false, false};
        boolean[] shareWithUser = {true, false};
        int[] priorityGroup = {0, 0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0], suggestions[1]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        // Verify no network is nominated.
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    @Test
    public void testSelectNetworkSuggestionForOneMatchCarrierNetworkWithNoSim() {
        String[] scanSsids = {"test1"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_EAP};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        when(mWifiCarrierInfoManager
                .getBestMatchSubscriptionId(suggestions[0].wns.wifiConfiguration))
                .thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(false);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        // Verify no network is nominated.
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    @Test
    public void testSelectNetworkSuggestionForOneMatchSimBasedWithEncryptionInfoNotAvailabele() {
        String[] scanSsids = {"test1"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_EAP};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        WifiConfiguration eapSimConfig = suggestions[0].wns.wifiConfiguration;
        eapSimConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        eapSimConfig.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        eapSimConfig.carrierId = TEST_CARRIER_ID;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(eapSimConfig))
                .thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.requiresImsiEncryption(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(TEST_SUB_ID)).thenReturn(false);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        // Verify no network is nominated.
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    @Test
    public void testSelectNetworkSuggestionForOneMatchSimBasedWithOobPseudonymInfoAvailable() {
        String[] scanSsids = {"test1"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_EAP};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        WifiConfiguration eapSimConfig = suggestions[0].wns.wifiConfiguration;
        eapSimConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        eapSimConfig.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        eapSimConfig.carrierId = TEST_CARRIER_ID;
        eapSimConfig.subscriptionId = TEST_SUB_ID;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(anyInt(), anyBoolean()))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(true);
        PseudonymInfo mockPseudonymInfo = mock(PseudonymInfo.class);
        when(mWifiPseudonymManager.getValidPseudonymInfo(anyInt()))
                .thenReturn(Optional.of(mockPseudonymInfo));
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        verify(mWifiPseudonymManager).updateWifiConfiguration(any());
        assertEquals(1, connectableNetworks.size());
    }

    @Test
    public void testSelectNetworkSuggestionForOneMatchSimBasedWithOobPseudonymIsNotAvailable() {
        String[] scanSsids = {"test1"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_EAP};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        WifiConfiguration eapSimConfig = suggestions[0].wns.wifiConfiguration;
        eapSimConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        eapSimConfig.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        eapSimConfig.carrierId = TEST_CARRIER_ID;
        eapSimConfig.subscriptionId = TEST_SUB_ID;
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(any())).thenReturn(TEST_SUB_ID);
        when(mWifiCarrierInfoManager.isSimReady(TEST_SUB_ID)).thenReturn(true);
        when(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(anyInt(), anyBoolean()))
                .thenReturn(true);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(TEST_CARRIER_ID))
                .thenReturn(true);
        when(mWifiPseudonymManager.getValidPseudonymInfo(anyInt())).thenReturn(Optional.empty());
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        // setup config manager interactions.
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        // Verify no network is nominated.
        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiPseudonymManager).retrievePseudonymOnFailureTimeoutExpired(any());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no matching network suggestion.
     * Because the only matched suggestion is untrusted and untrusted is not allowed
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchUntrustedNotAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.trusted = false;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion is untrusted and untrusted is allowed
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchUntrustedAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.trusted = false;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, true, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no matching network suggestion.
     * Because the only matched suggestion is oem paid and oem paid is not allowed
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchOemPaidNotAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }


    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion is oem paid and oem paid is allowed
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchOemPaidAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, true, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no matching network suggestion.
     * Because the only matched suggestion is oem private and oem private is not allowed
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchOemPrivateNotAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }


    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion is oem private and oem private is allowed
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchOemPrivateAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, true,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion has oem private & oem paid flags and both oem private and
     * oem paid is allowed.
     * Ensure that only the oemPaid flag is set in the candidate chosen to connect.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void
    testSelectNetworkSuggestionForOneOemPaidAndOemPrivateMatchWithOemPaidAndOemPrivateAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, true, true,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no matching network suggestion.
     * Because the only matched suggestion has oem private & oem paid flags and both oem private and
     * oem paid is not allowed.
     * Expected connectable Networks: {}
     */
    @Test
    public void
    testSelectNetworkSuggestionForOneOemPaidAndOemPrivateMatchWithOemPaidAndOemPrivateNotAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion has oem private & oem paid flags and only oem paid is
     * allowed.
     * Ensure that only the oemPaid flag is set in the candidate chosen to connect.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void
            testSelectNetworkSuggestionForOneOemPaidAndOemPrivateMatchWithOemPaidAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, true, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion has oem private & oem paid flags and only oem private is
     * allowed.
     * Ensure that only the oemPrivate flag is set in the candidate chosen to connect.
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void
            testSelectNetworkSuggestionForOneOemPaidAndOemPrivateMatchWithOemPrivateAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.oemPaid = true;
        suggestions[0].wns.wifiConfiguration.oemPrivate = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, true,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }


    /**
     * Ensure that we nominate the no matching network suggestion.
     * Because the only matched suggestion is metered carrier network from Non data SIM
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchMeteredNonDataSim() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        suggestions[0].wns.wifiConfiguration.meteredHint = true;
        when(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(any())).thenReturn(true);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the no matching network suggestion.
     * Because the only matched suggestion is carrier network which offloading is disabled.
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchCarrierOffloadDisabled() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.carrierId = TEST_CARRIER_ID;
        when(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(anyInt(), anyBoolean()))
                .thenReturn(false);
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);
        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate no matching network suggestion.
     * Because the only matched suggestion is restricted and restricted is not allowed
     * Expected connectable Networks: {}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchRestrictedNotAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.restricted = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, false, false, false,
                Collections.emptySet(),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        assertTrue(connectableNetworks.isEmpty());
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion is restricted and restricted is allowed
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchRestrictedAllow() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {TEST_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
                buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
                securities, appInteractions, meteredness, priorities, uids,
                packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.restricted = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, true, false, false,
                Set.of(suggestions[0].wns.wifiConfiguration.creatorUid),
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                    connectableNetworks.add(Pair.create(scanDetail, configuration));
                });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    /**
     * Ensure that we nominate the one matching network suggestion.
     * Because the only matched suggestion is restricted and suggested by the SYSTEM_SERVER
     * Expected connectable Networks: {suggestionSsids[0]}
     */
    @Test
    public void testSelectNetworkSuggestionForOneMatchRestrictedAllowBySystemServer() {
        String[] scanSsids = {"test1", "test2"};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP/SHA1-CCMP][ESS]", "[WPA2-EAP/SHA1-CCMP][ESS]"};
        int[] levels = {-67, -76};
        String[] suggestionSsids = {"\"" + scanSsids[0] + "\""};
        int[] securities = {SECURITY_PSK};
        boolean[] appInteractions = {true};
        boolean[] meteredness = {true};
        int[] priorities = {-1};
        int[] uids = {Process.SYSTEM_UID};
        String[] packageNames = {TEST_PACKAGE};
        boolean[] autojoin = {true};
        boolean[] shareWithUser = {true};
        int[] priorityGroup = {0};

        ScanDetail[] scanDetails =
            buildScanDetails(scanSsids, bssids, freqs, caps, levels, mClock);
        ExtendedWifiNetworkSuggestion[] suggestions = buildNetworkSuggestions(suggestionSsids,
            securities, appInteractions, meteredness, priorities, uids,
            packageNames, autojoin, shareWithUser, priorityGroup);
        suggestions[0].wns.wifiConfiguration.restricted = true;
        // Link the scan result with suggestions.
        linkScanDetailsWithNetworkSuggestions(scanDetails, suggestions);

        setupAddToWifiConfigManager(suggestions[0]);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        mNetworkSuggestionNominator.nominateNetworks(
                Arrays.asList(scanDetails), mPasspointCandidates, true, false, false,
                Collections.emptySet(), // restrictedNetworkAllowedUids is empty
                (ScanDetail scanDetail, WifiConfiguration configuration) -> {
                connectableNetworks.add(Pair.create(scanDetail, configuration));
            });

        validateConnectableNetworks(connectableNetworks, scanSsids[0]);
        verify(mWifiMetrics, never())
                .incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
    }

    private void setupAddToWifiConfigManager(ExtendedWifiNetworkSuggestion...candidates) {
        for (int i = 0; i < candidates.length; i++) {
            WifiConfiguration candidate = candidates[i].createInternalWifiConfiguration(
                    mWifiCarrierInfoManager);
            WifiConfiguration.NetworkSelectionStatus status =
                    mock(WifiConfiguration.NetworkSelectionStatus.class);
            when(status.isNetworkEnabled()).thenReturn(true);
            candidate.setNetworkSelectionStatus(status);
            when(mWifiConfigManager.getConfiguredNetwork(candidate.getProfileKey()))
                    .thenReturn(candidate);
        }
    }

    private void verifyAddToWifiConfigManager(
            boolean trustedSet, boolean oemPaidSet, boolean oemPrivateSet,
            WifiConfiguration...candidates) {
        // check for any saved networks.
        verify(mWifiConfigManager, atLeast(candidates.length)).getConfiguredNetwork(anyString());

        ArgumentCaptor<WifiConfiguration> wifiConfigurationCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager, times(candidates.length)).addOrUpdateNetwork(
                wifiConfigurationCaptor.capture(), anyInt(), anyString(), eq(false));
        verify(mWifiConfigManager, times(candidates.length)).allowAutojoin(anyInt(), anyBoolean());
        for (int i = 0; i < candidates.length; i++) {
            WifiConfiguration addedWifiConfiguration = null;
            for (WifiConfiguration configuration : wifiConfigurationCaptor.getAllValues()) {
                if (configuration.SSID.equals(candidates[i].SSID)) {
                    addedWifiConfiguration = configuration;
                    break;
                }
            }
            assertNotNull(addedWifiConfiguration);
            assertTrue(addedWifiConfiguration.ephemeral);
            assertTrue(addedWifiConfiguration.fromWifiNetworkSuggestion);
            assertEquals(trustedSet, addedWifiConfiguration.trusted);
            assertEquals(oemPaidSet, addedWifiConfiguration.oemPaid);
            assertEquals(oemPrivateSet, addedWifiConfiguration.oemPrivate);
        }

        verify(mWifiConfigManager, times(candidates.length)).updateNetworkSelectionStatus(
                anyInt(), anyInt());
        verify(mWifiConfigManager, times(candidates.length)).getConfiguredNetwork(anyInt());
    }

    /**
     * Build an array of scanDetails based on the caller supplied network SSID, BSSID,
     * frequency, capability and RSSI level information.
     */
    private static ScanDetail[] buildScanDetails(String[] ssids, String[] bssids, int[] freqs,
                                                    String[] caps, int[] levels, Clock clock) {
        if (ssids == null || ssids.length == 0) return new ScanDetail[0];

        ScanDetail[] scanDetails = new ScanDetail[ssids.length];
        long timeStamp = clock.getElapsedSinceBootMillis();
        for (int index = 0; index < ssids.length; index++) {
            scanDetails[index] = new ScanDetail(WifiSsid.fromUtf8Text(ssids[index]),
                    bssids[index], caps[index], levels[index], freqs[index], timeStamp, 0);
        }
        return scanDetails;
    }

    /**
     * Generate an array of {@link android.net.wifi.WifiConfiguration} based on the caller
     * supplied network SSID and security information.
     */
    private static WifiConfiguration[] buildWifiConfigurations(String[] ssids, int[] securities) {
        if (ssids == null || ssids.length == 0) return new WifiConfiguration[0];

        WifiConfiguration[] configs = new WifiConfiguration[ssids.length];
        for (int index = 0; index < ssids.length; index++) {
            configs[index] = generateWifiConfig(-1, 0, ssids[index], false, true, null,
                    null, securities[index]);
        }
        return configs;
    }

    private ExtendedWifiNetworkSuggestion[] buildNetworkSuggestions(
            String[] ssids, int[] securities, boolean[] appInteractions, boolean[] meteredness,
            int[] priorities, int[] uids, String[] packageNames, boolean[] autojoin,
            boolean[] shareWithUser, int[] priorityGroup) {
        WifiConfiguration[] configs = buildWifiConfigurations(ssids, securities);
        ExtendedWifiNetworkSuggestion[] suggestions =
                new ExtendedWifiNetworkSuggestion[configs.length];
        for (int i = 0; i < configs.length; i++) {
            configs[i].priority = priorities[i];
            configs[i].creatorUid = uids[i];
            configs[i].meteredOverride = meteredness[i]
                    ? WifiConfiguration.METERED_OVERRIDE_METERED
                    : WifiConfiguration.METERED_OVERRIDE_NONE;
            configs[i].creatorName = packageNames[i];
            configs[i].ephemeral = true;
            configs[i].fromWifiNetworkSuggestion = true;
            PerAppInfo perAppInfo = new PerAppInfo(uids[i], packageNames[i], null);
            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion(configs[i], null, appInteractions[i], false,
                            shareWithUser[i], true, priorityGroup[i]);
            suggestions[i] = new ExtendedWifiNetworkSuggestion(suggestion, perAppInfo, autojoin[i]);
        }
        return suggestions;
    }

    /**
     * Link scan results to the network suggestions.
     *
     * The shorter of the 2 input params will be used to loop over so the inputs don't
     * need to be of equal length.
     * If there are more scan details than suggestions, the remaining
     * scan details will be associated with a NULL suggestions.
     * If there are more suggestions than scan details, the remaining
     * suggestions will be associated with the last scan detail.
     */
    private void linkScanDetailsWithNetworkSuggestions(
            ScanDetail[] scanDetails, ExtendedWifiNetworkSuggestion[] suggestions) {
        if (suggestions == null || scanDetails == null) {
            return;
        }
        int minLength = Math.min(scanDetails.length, suggestions.length);

        // 1 to 1 mapping from scan detail to suggestion.
        for (int i = 0; i < minLength; i++) {
            ScanDetail scanDetail = scanDetails[i];
            final ExtendedWifiNetworkSuggestion matchingSuggestion = suggestions[i];
            Set<ExtendedWifiNetworkSuggestion> matchingSuggestions = Set.of(matchingSuggestion);
            when(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(eq(scanDetail)))
                    .thenReturn(matchingSuggestions);
        }
        if (scanDetails.length > suggestions.length) {
            // No match for the remaining scan details.
            for (int i = minLength; i < scanDetails.length; i++) {
                ScanDetail scanDetail = scanDetails[i];
                when(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                        eq(scanDetail))).thenReturn(Set.of());
            }
        } else if (suggestions.length > scanDetails.length) {
            // All the additional suggestions match the last scan detail.
            HashSet<ExtendedWifiNetworkSuggestion> matchingSuggestions = new HashSet<>(
                    Arrays.asList(suggestions).subList(minLength - 1, suggestions.length));
            ScanDetail lastScanDetail = scanDetails[minLength - 1];
            when(mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(
                    eq(lastScanDetail))).thenReturn(matchingSuggestions);
        }
    }

    private void validateConnectableNetworks(List<Pair<ScanDetail, WifiConfiguration>> actual,
                                             String...expectedSsids) {
        assertEquals(expectedSsids.length, actual.size());
        Set<String> actualSsids = new HashSet<>();

        for (Pair<ScanDetail, WifiConfiguration> candidate : actual) {
            // check if the scan detail matches the wificonfiguration.
            assertEquals("\"" + candidate.first.getSSID() + "\"", candidate.second.SSID);
            actualSsids.add(candidate.first.getSSID());
        }
        // Verify actual matches the expected.
        assertTrue(actualSsids.containsAll(Arrays.asList(expectedSsids)));
    }
}
