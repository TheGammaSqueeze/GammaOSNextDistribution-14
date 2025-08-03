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

import static com.android.server.wifi.WifiNetworkSelector.toNetworkString;

import android.annotation.NonNull;
import android.net.wifi.WifiConfiguration;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiNetworkSuggestionsManager.ExtendedWifiNetworkSuggestion;
import com.android.server.wifi.entitlement.PseudonymInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Nominator nominate the highest available suggestion candidates.
 * Note:
 * <li> This class is not thread safe and meant to be used only from {@link WifiNetworkSelector}.
 * </li>
 *
 */
@NotThreadSafe
public class NetworkSuggestionNominator implements WifiNetworkSelector.NetworkNominator {
    private static final String TAG = "NetworkSuggestionNominator";

    private final WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private final WifiConfigManager mWifiConfigManager;
    private final LocalLog mLocalLog;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiPseudonymManager mWifiPseudonymManager;
    private final WifiMetrics mWifiMetrics;

    NetworkSuggestionNominator(WifiNetworkSuggestionsManager networkSuggestionsManager,
            WifiConfigManager wifiConfigManager,
            LocalLog localLog, WifiCarrierInfoManager wifiCarrierInfoManager,
            WifiPseudonymManager wifiPseudonymManager, WifiMetrics wifiMetrics) {
        mWifiNetworkSuggestionsManager = networkSuggestionsManager;
        mWifiConfigManager = wifiConfigManager;
        mLocalLog = localLog;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mWifiPseudonymManager = wifiPseudonymManager;
        mWifiMetrics = wifiMetrics;
    }

    @Override
    public void update(List<ScanDetail> scanDetails) {
        // Update the matching profiles into WifiConfigManager, help displaying Suggestion
        // networks in Wifi Picker
        addOrUpdateSuggestionsToWifiConfigManger(scanDetails);
    }

    @Override
    public void nominateNetworks(@NonNull List<ScanDetail> scanDetails,
            @NonNull List<Pair<ScanDetail, WifiConfiguration>> passpointCandidates,
            boolean untrustedNetworkAllowed, boolean oemPaidNetworkAllowed,
            boolean oemPrivateNetworkAllowed,
            @NonNull Set<Integer> restrictedNetworkAllowedUids,
            @NonNull OnConnectableListener onConnectableListener) {
        if (scanDetails.isEmpty()) {
            return;
        }
        MatchMetaInfo matchMetaInfo = new MatchMetaInfo();
        if (passpointCandidates != null) {
            findMatchedPasspointSuggestionNetworks(
                    passpointCandidates, matchMetaInfo, untrustedNetworkAllowed,
                    oemPaidNetworkAllowed,
                    oemPrivateNetworkAllowed, restrictedNetworkAllowedUids);
        }

        findMatchedSuggestionNetworks(scanDetails, matchMetaInfo, untrustedNetworkAllowed,
                oemPaidNetworkAllowed,
                oemPrivateNetworkAllowed, restrictedNetworkAllowedUids);

        if (matchMetaInfo.isEmpty()) {
            mLocalLog.log("did not see any matching auto-join enabled network suggestions.");
        } else {
            matchMetaInfo.findConnectableNetworksAndHighestPriority(onConnectableListener);
        }
    }

    private void addOrUpdateSuggestionsToWifiConfigManger(List<ScanDetail> scanDetails) {
        for (ScanDetail scanDetail : scanDetails) {
            Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                    mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
            if (matchingExtNetworkSuggestions == null || matchingExtNetworkSuggestions.isEmpty()) {
                continue;
            }
            for (ExtendedWifiNetworkSuggestion ewns : matchingExtNetworkSuggestions) {
                addOrUpdateSuggestionToWifiConfigManger(ewns);
            }
        }
    }

    private void addOrUpdateSuggestionToWifiConfigManger(ExtendedWifiNetworkSuggestion ewns) {
        WifiConfiguration config = ewns.createInternalWifiConfiguration(mWifiCarrierInfoManager);
        WifiConfiguration wCmConfiguredNetwork =
                mWifiConfigManager.getConfiguredNetwork(config.getProfileKey());
        if (wCmConfiguredNetwork != null) {
            mLocalLog.log(config.getProfileKey() + "is already in the WifiConfigManager");
            if (!wCmConfiguredNetwork.getNetworkSelectionStatus().isNetworkEnabled()
                    && !mWifiConfigManager.tryEnableNetwork(wCmConfiguredNetwork.networkId)) {
                mLocalLog.log("Ignoring blocked network: "
                        + toNetworkString(wCmConfiguredNetwork));
            }
            return;
        }
        NetworkUpdateResult result = mWifiConfigManager.addOrUpdateNetwork(
                config, ewns.perAppInfo.uid, ewns.perAppInfo.packageName, false);
        if (!result.isSuccess()) {
            mLocalLog.log("Failed to add network suggestion");
            return;
        }
        mLocalLog.log(config.getProfileKey()
                + " is added in the WifiConfigManager");
        WifiConfiguration currentWCmConfiguredNetwork =
                mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
        // Try to enable network selection
        if (!mWifiConfigManager.updateNetworkSelectionStatus(result.getNetworkId(),
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE)) {
            mLocalLog.log("Failed to make network suggestion selectable");
        }
    }

    /** Helper method to avoid code duplication in regular & passpoint based suggestions filter. */
    private boolean shouldIgnoreBasedOnChecksForTrustedOrOemPaidOrOemPrivateOrRestricted(
            WifiConfiguration config, boolean untrustedNetworkAllowed,
            boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
            Set<Integer> restrictedNetworkAllowedUids) {
        // If untrusted network is not allowed, ignore untrusted suggestion.
        if (!untrustedNetworkAllowed && !config.trusted) {
            return true;
        }
        if (!(restrictedNetworkAllowedUids.contains(config.creatorUid)
                || config.creatorUid == Process.SYSTEM_UID) && config.restricted) {
            return true;
        }
        // For suggestions with both oem paid & oem private set, ignore them If both oem paid
        // & oem private network is not allowed. If oem paid network is allowed, then mark
        // the suggestion oem paid for this connection attempt, else mark oem private for this
        // connection attempt.
        if (config.oemPaid && config.oemPrivate) {
            if (!oemPaidNetworkAllowed && !oemPrivateNetworkAllowed) {
                return true;
            }
            if (oemPaidNetworkAllowed) {
                config.oemPrivate = false; // only oemPaid set.
            } else if (oemPrivateNetworkAllowed) {
                config.oemPaid = false; // only oemPrivate set.
            }
        } else {
            // If oem paid network is not allowed, ignore oem paid suggestion.
            if (!oemPaidNetworkAllowed && config.oemPaid) {
                return true;
            }
            // If oem paid network is not allowed, ignore oem paid suggestion.
            if (!oemPrivateNetworkAllowed && config.oemPrivate) {
                return true;
            }
        }
        return false;
    }

    private void findMatchedPasspointSuggestionNetworks(
            List<Pair<ScanDetail, WifiConfiguration>> candidates,
            MatchMetaInfo matchMetaInfo, boolean untrustedNetworkAllowed,
            boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
            Set<Integer> restrictedNetworkAllowedUids) {
        for (Pair<ScanDetail, WifiConfiguration> candidate : candidates) {
            WifiConfiguration config = candidate.second;
            if (!config.fromWifiNetworkSuggestion) {
                continue;
            }
            Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                    mWifiNetworkSuggestionsManager.getNetworkSuggestionsForFqdn(config.FQDN);
            if (matchingExtNetworkSuggestions.isEmpty()) {
                mLocalLog.log("No user approved suggestion for FQDN:" + config.FQDN);
                continue;
            }
            Optional<ExtendedWifiNetworkSuggestion> matchingPasspointExtSuggestion =
                    matchingExtNetworkSuggestions.stream()
                            .filter(ewns -> Objects.equals(
                                    ewns.wns.wifiConfiguration.getPasspointUniqueId(),
                                    config.getPasspointUniqueId()))
                            .findFirst();
            if (!matchingPasspointExtSuggestion.isPresent()) {
                mLocalLog.log("Suggestion is missing for passpoint FQDN: " + config.FQDN
                        + " profile key: " + config.getProfileKey());
                continue;
            }
            if (!isNetworkAvailableToAutoConnect(config, untrustedNetworkAllowed,
                    oemPaidNetworkAllowed, oemPrivateNetworkAllowed,
                    restrictedNetworkAllowedUids)) {
                mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(config);
                continue;
            } else {
                mWifiPseudonymManager.updateWifiConfiguration(config);
            }

            matchMetaInfo.put(matchingPasspointExtSuggestion.get(), config, candidate.first);
        }
    }

    private void findMatchedSuggestionNetworks(List<ScanDetail> scanDetails,
            MatchMetaInfo matchMetaInfo, boolean untrustedNetworkAllowed,
            boolean oemPaidNetworkAllowed,
            boolean oemPrivateNetworkAllowed, Set<Integer> restrictedNetworkAllowedUids) {
        for (ScanDetail scanDetail : scanDetails) {
            Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                    mWifiNetworkSuggestionsManager.getNetworkSuggestionsForScanDetail(scanDetail);
            if (matchingExtNetworkSuggestions.isEmpty()) {
                continue;
            }
            if (matchingExtNetworkSuggestions.size() > 1) {
                mWifiMetrics.incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
            }
            for (ExtendedWifiNetworkSuggestion ewns : matchingExtNetworkSuggestions) {
                WifiConfiguration config = ewns.createInternalWifiConfiguration(
                        mWifiCarrierInfoManager);
                WifiConfiguration wCmConfiguredNetwork =
                        mWifiConfigManager.getConfiguredNetwork(config.getProfileKey());
                if (wCmConfiguredNetwork == null) {
                    mLocalLog.log(config.getProfileKey()
                            + "hasn't add to WifiConfigManager?");
                    continue;
                }
                if (SdkLevel.isAtLeastS()
                        && mWifiConfigManager.getConfiguredNetwork(config.getKey()) != null) {
                    // If a saved profile is available for the same network
                    mWifiMetrics.addSuggestionExistsForSavedNetwork(
                            config.getKey());
                }
                if (!wCmConfiguredNetwork.getNetworkSelectionStatus().isNetworkEnabled()
                        && !mWifiConfigManager.tryEnableNetwork(wCmConfiguredNetwork.networkId)) {
                    mLocalLog.log("Ignoring blocklisted network: "
                            + toNetworkString(wCmConfiguredNetwork));
                    continue;
                }
                if (!isNetworkAvailableToAutoConnect(wCmConfiguredNetwork, untrustedNetworkAllowed,
                        oemPaidNetworkAllowed, oemPrivateNetworkAllowed,
                        restrictedNetworkAllowedUids)) {
                    mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(
                            wCmConfiguredNetwork);
                    continue;
                } else {
                    mWifiPseudonymManager.updateWifiConfiguration(wCmConfiguredNetwork);
                }
                matchMetaInfo.put(ewns, wCmConfiguredNetwork, scanDetail);
            }
        }
    }


    private boolean isNetworkAvailableToAutoConnect(WifiConfiguration config,
            boolean untrustedNetworkAllowed, boolean oemPaidNetworkAllowed,
            boolean oemPrivateNetworkAllowed, Set<Integer> restrictedNetworkAllowedUids) {
        // Ignore insecure enterprise config.
        if (config.isEnterprise() && config.enterpriseConfig.isEapMethodServerCertUsed()
                && !config.enterpriseConfig
                .isMandatoryParameterSetForServerCertValidation()) {
            mLocalLog.log("Ignoring insecure enterprise network: " + config);
            return false;
        }
        // If oem paid network is not allowed, ignore oem paid suggestion.
        if (shouldIgnoreBasedOnChecksForTrustedOrOemPaidOrOemPrivateOrRestricted(config,
                untrustedNetworkAllowed, oemPaidNetworkAllowed, oemPrivateNetworkAllowed,
                restrictedNetworkAllowedUids)) {
            mLocalLog.log("Ignoring network since it needs corresponding NetworkRequest: "
                    + toNetworkString(config));
            return false;
        }
        if (!isCarrierNetworkAvailableToAutoConnect(config)) {
            return false;
        }
        String network = config.isPasspoint() ? config.FQDN : config.SSID;
        if (mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(config)) {
            mLocalLog.log("Ignoring non-carrier-merged network: " + network);
            return false;
        }
        if (mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network)) {
            mLocalLog.log("Ignoring user disabled network: " + network);
            return false;
        }
        return config.allowAutojoin;
    }

    private boolean isCarrierNetworkAvailableToAutoConnect(WifiConfiguration config) {
        if (config.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
            return true;
        }

        if (!mWifiCarrierInfoManager.isSimReady(config.subscriptionId)) {
            mLocalLog.log("SIM is not present for subId: " + config.subscriptionId);
            return false;
        }
        if (WifiConfiguration.isMetered(config, null)
                && mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(config)) {
            mLocalLog.log("Ignoring carrier network from non default data SIM, network: "
                    + toNetworkString(config));
            return false;
        }
        if (!mWifiCarrierInfoManager
                .isCarrierNetworkOffloadEnabled(config.subscriptionId, config.carrierMerged)) {
            mLocalLog.log("Carrier offload is disabled for "
                    + (config.carrierMerged ? "merged" : "unmerged")
                    + " network from subId: " + config.subscriptionId);
            return false;
        }
        return isSimBasedNetworkAvailableToAutoConnect(config);
    }

    private boolean isSimBasedNetworkAvailableToAutoConnect(WifiConfiguration config) {
        if (config.enterpriseConfig == null
                || !config.enterpriseConfig.isAuthenticationSimBased()) {
            return true;
        }
        int subId = config.subscriptionId;
        int carrierId = config.carrierId;
        if (mWifiCarrierInfoManager.requiresImsiEncryption(subId)
                && !mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(subId)) {
            mLocalLog.log("Ignoring SIM based network IMSI encryption info not Available, subId: "
                    + subId);
            return false;
        }
        if (mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(carrierId)) {
            Optional<PseudonymInfo> pseudonymInfo =
                    mWifiPseudonymManager.getValidPseudonymInfo(carrierId);
            if (pseudonymInfo.isEmpty()) {
                // matching on network is seen.
                mLocalLog.log(
                        "Ignoring SIM based network because there isn't any valid pseudonym, "
                                + "subId: " + subId + " carrierId: " + carrierId);
                return false;
            }
        }
        return true;
    }

    @Override
    public @NominatorId int getId() {
        return NOMINATOR_ID_SUGGESTION;
    }

    @Override
    public String getName() {
        return TAG;
    }

    // Container classes to handle book-keeping while we're iterating through the scan list.
    private class PerNetworkSuggestionMatchMetaInfo {
        public final ExtendedWifiNetworkSuggestion extWifiNetworkSuggestion;
        public final ScanDetail matchingScanDetail;
        public WifiConfiguration wCmConfiguredNetwork; // Added to WifiConfigManager.

        PerNetworkSuggestionMatchMetaInfo(
                @NonNull ExtendedWifiNetworkSuggestion extWifiNetworkSuggestion,
                @Nullable WifiConfiguration wCmConfiguredNetwork,
                @NonNull ScanDetail matchingScanDetail) {
            this.extWifiNetworkSuggestion = extWifiNetworkSuggestion;
            this.wCmConfiguredNetwork = wCmConfiguredNetwork;
            this.matchingScanDetail = matchingScanDetail;
        }
    }

    private class PerAppMatchMetaInfo {
        public final List<PerNetworkSuggestionMatchMetaInfo> networkInfos = new ArrayList<>();

        /**
         * Add the network suggestion & associated info to this package meta info.
         */
        public void put(ExtendedWifiNetworkSuggestion wifiNetworkSuggestion,
                        WifiConfiguration matchingWifiConfiguration,
                        ScanDetail matchingScanDetail) {
            networkInfos.add(new PerNetworkSuggestionMatchMetaInfo(
                    wifiNetworkSuggestion, matchingWifiConfiguration, matchingScanDetail));
        }

        /**
         * Pick the highest priority networks among the current match info candidates for this
         * app.
         */
        public List<PerNetworkSuggestionMatchMetaInfo> getHighestPriorityNetworks() {
            // Partition the list to a map of network suggestions keyed in by the priorities.
            // There can be multiple networks with the same priority, hence a list in the value.
            Map<Integer, List<PerNetworkSuggestionMatchMetaInfo>> matchedNetworkInfosPerPriority =
                    networkInfos.stream()
                            .collect(Collectors.toMap(
                                    e -> e.extWifiNetworkSuggestion.wns.wifiConfiguration.priority,
                                    e -> Arrays.asList(e),
                                    (v1, v2) -> { // concatenate networks with the same priority.
                                        List<PerNetworkSuggestionMatchMetaInfo> concatList =
                                                new ArrayList<>(v1);
                                        concatList.addAll(v2);
                                        return concatList;
                                    }));
            if (matchedNetworkInfosPerPriority.isEmpty()) { // should never happen.
                Log.wtf(TAG, "Unexepectedly got empty");
                return List.of();
            }
            // Return the list associated with the highest priority value.
            return matchedNetworkInfosPerPriority.get(Collections.max(
                    matchedNetworkInfosPerPriority.keySet()));
        }
    }

    private class MatchMetaInfo {
        private SparseArray<PerAppMatchMetaInfo> mAppInfos = new SparseArray<>();

        /**
         * Add all the network suggestion & associated info.
         */
        public void put(ExtendedWifiNetworkSuggestion wifiNetworkSuggestion,
                           WifiConfiguration wCmConfiguredNetwork,
                           ScanDetail matchingScanDetail) {
            // Put the suggestion into buckets for each app to allow sorting based on
            // priorities set by app.
            int key = Objects.hash(wifiNetworkSuggestion.perAppInfo.packageName,
                    wifiNetworkSuggestion.wns.priorityGroup);
            PerAppMatchMetaInfo appInfo = mAppInfos.get(key, new PerAppMatchMetaInfo());
            appInfo.put(wifiNetworkSuggestion, wCmConfiguredNetwork, matchingScanDetail);
            mAppInfos.put(key, appInfo);
        }

        /**
         * Are there any matched candidates?
         */
        public boolean isEmpty() {
            return mAppInfos.size() == 0;
        }

        /**
         * Run through all connectable suggestions and nominate highest priority networks from each
         * app as candidates to {@link WifiNetworkSelector}.
         */
        public void findConnectableNetworksAndHighestPriority(
                @NonNull OnConnectableListener onConnectableListener) {
            for (int i = 0; i < mAppInfos.size(); i++) {
                List<PerNetworkSuggestionMatchMetaInfo> matchedNetworkInfos =
                        mAppInfos.valueAt(i).getHighestPriorityNetworks();
                for (PerNetworkSuggestionMatchMetaInfo matchedNetworkInfo : matchedNetworkInfos) {
                    mLocalLog.log("network suggestion candidate " + toNetworkString(
                            matchedNetworkInfo.wCmConfiguredNetwork) + " nominated");
                    onConnectableListener.onConnectable(
                            matchedNetworkInfo.matchingScanDetail,
                            matchedNetworkInfo.wCmConfiguredNetwork);
                }
            }
        }
    }

}
