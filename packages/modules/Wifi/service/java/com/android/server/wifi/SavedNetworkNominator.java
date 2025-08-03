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

import android.annotation.NonNull;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.util.Pair;

import com.android.server.wifi.util.WifiPermissionsUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is the WifiNetworkSelector.NetworkNominator implementation for
 * saved networks.
 */
public class SavedNetworkNominator implements WifiNetworkSelector.NetworkNominator {
    private static final String NAME = "SavedNetworkNominator";
    private final WifiConfigManager mWifiConfigManager;
    private final LocalLog mLocalLog;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiPseudonymManager mWifiPseudonymManager;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;

    SavedNetworkNominator(WifiConfigManager configManager,
            LocalLog localLog,
            WifiCarrierInfoManager wifiCarrierInfoManager,
            WifiPseudonymManager wifiPseudonymManager,
            WifiPermissionsUtil wifiPermissionsUtil,
            WifiNetworkSuggestionsManager wifiNetworkSuggestionsManager) {
        mWifiConfigManager = configManager;
        mLocalLog = localLog;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mWifiPseudonymManager = wifiPseudonymManager;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiNetworkSuggestionsManager = wifiNetworkSuggestionsManager;
    }

    private void localLog(String log) {
        mLocalLog.log(log);
    }

    /**
     * Get the Nominator type.
     */
    @Override
    public @NominatorId int getId() {
        return NOMINATOR_ID_SAVED;
    }

    /**
     * Get the Nominator name.
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Update the Nominator.
     */
    @Override
    public void update(List<ScanDetail> scanDetails) {
    }

    /**
     * Run through all scanDetails and nominate all connectable network as candidates.
     *
     */
    @Override
    public void nominateNetworks(@NonNull List<ScanDetail> scanDetails,
            @NonNull List<Pair<ScanDetail, WifiConfiguration>> passpointCandidates,
            boolean untrustedNetworkAllowed /* unused */,
            boolean oemPaidNetworkAllowed /* unused */,
            boolean oemPrivateNetworkAllowed /* unused */,
            Set<Integer> restrictedNetworkAllowedUids /* unused */,
            @NonNull OnConnectableListener onConnectableListener) {
        if (scanDetails != null) {
            findMatchedSavedNetworks(scanDetails, onConnectableListener);
        }
        if (passpointCandidates != null) {
            findMatchedPasspointNetworks(passpointCandidates, onConnectableListener);
        }
    }

    private void findMatchedSavedNetworks(List<ScanDetail> scanDetails,
            OnConnectableListener onConnectableListener) {
        for (ScanDetail scanDetail : scanDetails) {
            ScanResult scanResult = scanDetail.getScanResult();

            // One ScanResult can be associated with more than one network, hence we calculate all
            // the scores and use the highest one as the ScanResult's score.
            WifiConfiguration network =
                    mWifiConfigManager.getSavedNetworkForScanDetailAndCache(scanDetail);

            if (network == null) {
                continue;
            }

            /**
             * Ignore Passpoint and Ephemeral networks. They are configured networks,
             * but without being persisted to the storage.
             */
            if (network.isPasspoint() || network.isEphemeral()) {
                continue;
            }

            // Ignore networks that the user has disallowed auto-join for.
            if (!network.allowAutojoin) {
                localLog("Ignoring auto join disabled SSID: " + network.SSID);
                continue;
            }

            WifiConfiguration.NetworkSelectionStatus status =
                    network.getNetworkSelectionStatus();
            status.setSeenInLastQualifiedNetworkSelection(true);

            if (mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(network)) {
                localLog("Ignoring non-carrier-merged SSID: " + network.SSID);
                continue;
            }
            if (mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network.SSID)) {
                localLog("Ignoring user disabled SSID: " + network.SSID);
                continue;
            }

            if (!status.isNetworkEnabled()) {
                localLog("Ignoring network selection disabled SSID: " + network.SSID);
                continue;
            }
            if (network.BSSID != null &&  !network.BSSID.equals("any")
                    && !network.BSSID.equals(scanResult.BSSID)) {
                // App has specified the only BSSID to connect for this
                // configuration. So only the matching ScanResult can be a candidate.
                localLog("Network " + WifiNetworkSelector.toNetworkString(network)
                        + " has specified BSSID " + network.BSSID + ". Skip "
                        + scanResult.BSSID);
                continue;
            }
            List<MacAddress> bssidList = network.getBssidAllowlistInternal();
            if (bssidList != null) {
                if (bssidList.isEmpty()) {
                    localLog("Network " + WifiNetworkSelector.toNetworkString(network)
                            + " has specified BSSID list " + bssidList + ". Skip "
                            + scanResult.BSSID);
                    continue;
                }
                Set<String> bssidSet = bssidList.stream().map(MacAddress::toString)
                        .collect(Collectors.toSet());
                if (!bssidSet.contains(scanResult.BSSID)) {
                    localLog("Network " + WifiNetworkSelector.toNetworkString(network)
                            + " has specified BSSID list " + bssidList + ". Skip "
                            + scanResult.BSSID);
                    continue;
                }
            }
            if (isNetworkSimBasedCredential(network) && !isSimBasedNetworkAbleToAutoJoin(network)) {
                localLog("Ignoring SIM auto join disabled SSID: " + network.SSID);
                mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(network);
                continue;
            } else {
                mWifiPseudonymManager.updateWifiConfiguration(network);
            }

            // If the network is marked to use external scores, or is an open network with
            // curate saved open networks enabled, do not consider it for network selection.
            if (network.useExternalScores) {
                localLog("Network " + WifiNetworkSelector.toNetworkString(network)
                        + " has external score.");
                continue;
            }

            if (mWifiNetworkSuggestionsManager
                    .shouldBeIgnoredBySecureSuggestionFromSameCarrier(network,
                            scanDetails)) {
                localLog("Open Network " + WifiNetworkSelector.toNetworkString(network)
                        + " has a secure network suggestion from same carrier.");
                continue;
            }

            onConnectableListener.onConnectable(scanDetail,
                    mWifiConfigManager.getConfiguredNetwork(network.networkId));
        }
    }

    private void findMatchedPasspointNetworks(List<Pair<ScanDetail, WifiConfiguration>> candidates,
            OnConnectableListener onConnectableListener) {
        for (Pair<ScanDetail, WifiConfiguration> candidate : candidates) {
            WifiConfiguration config = candidate.second;
            if (config.fromWifiNetworkSuggestion) {
                return;
            }
            if (!config.allowAutojoin) {
                continue;
            }
            if (isNetworkSimBasedCredential(config) && !isSimBasedNetworkAbleToAutoJoin(config)) {
                mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(config);
                continue;
            } else {
                mWifiPseudonymManager.updateWifiConfiguration(config);
            }
            onConnectableListener.onConnectable(candidate.first, config);
        }
    }

    private boolean isSimBasedNetworkAbleToAutoJoin(WifiConfiguration network) {
        int carrierId = network.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID
                ? mWifiCarrierInfoManager.getDefaultDataSimCarrierId() : network.carrierId;
        int subId = mWifiCarrierInfoManager.getMatchingSubId(carrierId);
        // Ignore security type is EAP SIM/AKA/AKA' when SIM is not present.
        if (!mWifiCarrierInfoManager.isSimReady(subId)) {
            localLog("No SIM card is good for Network "
                    + WifiNetworkSelector.toNetworkString(network));
            return false;
        }
        boolean imsiProtected = false;
        if (mWifiCarrierInfoManager.requiresImsiEncryption(subId)) {
            imsiProtected = true;
            if (!mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(subId)) {
                localLog("Ignore. Imsi protection required but not available for Network "
                        + WifiNetworkSelector.toNetworkString(network));
                return false;
            }
        }
        if (mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(carrierId)) {
            imsiProtected = true;
            if (mWifiPseudonymManager.getValidPseudonymInfo(carrierId).isEmpty()) {
                mLocalLog.log("Ignore. There isn't any valid pseudonym, "
                        + "subId: " + subId + " carrierId: " + carrierId);
                return false;
            }
        }
        if (!imsiProtected && isImsiProtectionApprovalNeeded(network.creatorUid, carrierId)) {
            localLog("Ignore. Imsi protection exemption needed for Network "
                    + WifiNetworkSelector.toNetworkString(network));
            return false;
        }
        // Ignore metered network with non-data Sim.
        if (WifiConfiguration.isMetered(network, null)
                && mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(network)) {
            localLog("No default SIM is used for metered Network: "
                    + WifiNetworkSelector.toNetworkString(network));
            return false;
        }
        return true;
    }

    private boolean isNetworkSimBasedCredential(WifiConfiguration network) {
        return network != null && network.enterpriseConfig != null
                && network.enterpriseConfig.isAuthenticationSimBased();
    }

    private boolean isImsiProtectionApprovalNeeded(int creatorUid, int carrierId) {
        // User saved network got exemption.
        if (mWifiPermissionsUtil.checkNetworkSettingsPermission(creatorUid)
                || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(creatorUid)) {
            return false;
        }
        if (mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(carrierId)) {
            return false;
        }
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(carrierId);
        return true;
    }
}
