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

package com.android.services.telephony.domainselection;

import android.annotation.NonNull;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.DomainSelectionService;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.VopsSupportInfo;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * Implements an emergency SMS domain selector for sending an emergency SMS.
 */
public class EmergencySmsDomainSelector extends SmsDomainSelector implements
        ImsStateTracker.BarringInfoListener, ImsStateTracker.ServiceStateListener {
    protected static final int EVENT_EMERGENCY_NETWORK_SCAN_RESULT = 201;
    /**
     * Stores the configuration value of
     * {@link CarrierConfigManager#KEY_SUPPORT_EMERGENCY_SMS_OVER_IMS_BOOL}.
     * This value is always updated whenever the domain selection is requested.
     */
    private Boolean mEmergencySmsOverImsSupportedByConfig;
    private ServiceState mServiceState;
    private boolean mServiceStateReceived;
    private BarringInfo mBarringInfo;
    private boolean mBarringInfoReceived;
    private boolean mEmergencyNetworkScanInProgress;
    private CancellationSignal mEmergencyNetworkScanSignal;

    public EmergencySmsDomainSelector(Context context, int slotId, int subId,
            @NonNull Looper looper, @NonNull ImsStateTracker imsStateTracker,
            @NonNull DestroyListener listener) {
        super(context, slotId, subId, looper, imsStateTracker, listener,
                "DomainSelector-EmergencySMS");

        mImsStateTracker.addServiceStateListener(this);
        mImsStateTracker.addBarringInfoListener(this);
    }

    @Override
    public void destroy() {
        if (mDestroyed) {
            return;
        }
        mImsStateTracker.removeServiceStateListener(this);
        mImsStateTracker.removeBarringInfoListener(this);
        super.destroy();
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_EMERGENCY_NETWORK_SCAN_RESULT:
                handleEmergencyNetworkScanResult((EmergencyRegistrationResult) msg.obj);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    @Override
    public void finishSelection() {
        super.finishSelection();
        mServiceStateReceived = false;
        mServiceState = null;
        mBarringInfoReceived = false;
        mBarringInfo = null;
        mEmergencySmsOverImsSupportedByConfig = null;

        mEmergencyNetworkScanInProgress = false;
        if (mEmergencyNetworkScanSignal != null) {
            mEmergencyNetworkScanSignal.cancel();
            mEmergencyNetworkScanSignal = null;
        }
    }

    @Override
    public void onBarringInfoUpdated(BarringInfo barringInfo) {
        mBarringInfoReceived = true;
        mBarringInfo = barringInfo;
        sendMessageForDomainSelection();
    }

    @Override
    public void onServiceStateUpdated(ServiceState serviceState) {
        mServiceStateReceived = true;
        mServiceState = serviceState;
        sendMessageForDomainSelection();
    }

    /**
     * Checks whether the domain selector is ready to select the domain or not.
     * The emergency SMS requires to be updated for the {@link ServiceState} and
     * {@link BarringInfo} to confirm that the cellular network supports to send emergency SMS
     * messages over IMS.
     */
    @VisibleForTesting
    public boolean isDomainSelectionReady() {
        return mServiceStateReceived && mBarringInfoReceived;
    }

    @Override
    protected boolean isSmsOverImsAvailable() {
        if (super.isSmsOverImsAvailable()) {
            /**
             * Even though IMS is successfully registered, the cellular domain should be
             * available for the emergency SMS according to the carrier's requirement
             * when {@link CarrierConfigManager#KEY_SUPPORT_EMERGENCY_SMS_OVER_IMS_BOOL} is set
             * to true.
             */
            if (isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService()) {
                /**
                 * Emergency SMS should be supported via emergency PDN.
                 * If this condition is false, then need to fallback to CS network
                 * because the current PS network does not allow the emergency service.
                 */
                return isNetworkAvailableForImsEmergencySms();
            }

            // Emergency SMS is supported via IMS PDN.
            return true;
        }

        return isImsEmergencySmsAvailable();
    }

    @Override
    protected void selectDomain() {
        if (!isDomainSelectionRequested()) {
            logi("Domain selection is not requested!");
            return;
        }

        if (!isDomainSelectionReady()) {
            logd("Wait for the readiness of the domain selection!");
            return;
        }

        if (mEmergencyNetworkScanInProgress) {
            logi("Emergency network scan is in progress.");
            return;
        }

        logi("selectDomain: " + mImsStateTracker.imsStateToString());

        if (isSmsOverImsAvailable()) {
            boolean isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService =
                    isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService();

            if (mImsStateTracker.isImsRegisteredOverWlan()) {
                /**
                 * When {@link CarrierConfigManager#KEY_SUPPORT_EMERGENCY_SMS_OVER_IMS_BOOL}
                 * is set to true, the emergency SMS supports on the LTE/NR network using the
                 * emergency PDN. As of now, since the emergency SMS doesn't use the emergency PDN
                 * over WLAN, the domain selector reports the domain as WLAN only if
                 * {@code isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService} is set to false
                 * and IMS is registered over WLAN.
                 * Otherwise, the domain selector reports the domain as WWAN.
                 */
                if (!isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService) {
                    notifyWlanSelected(false);
                    return;
                }

                logi("DomainSelected: WLAN >> WWAN");
            }

            /**
             * The request of emergency network scan triggers the modem to request the emergency
             * service fallback because NR network doesn't support the emergency service.
             */
            if (isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService
                    && isNrEmergencyServiceFallbackRequired()) {
                requestEmergencyNetworkScan(List.of(AccessNetworkType.EUTRAN));
            } else {
                notifyWwanSelected(NetworkRegistrationInfo.DOMAIN_PS,
                        isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService);
            }
        } else {
            notifyWwanSelected(NetworkRegistrationInfo.DOMAIN_CS, false);
        }
    }

    private void requestEmergencyNetworkScan(List<Integer> preferredNetworks) {
        mEmergencyNetworkScanInProgress = true;

        if (mWwanSelectorCallback == null) {
            mTransportSelectorCallback.onWwanSelected((callback) -> {
                mWwanSelectorCallback = callback;
                requestEmergencyNetworkScanInternal(preferredNetworks);
            });
        } else {
            requestEmergencyNetworkScanInternal(preferredNetworks);
        }
    }

    private void requestEmergencyNetworkScanInternal(List<Integer> preferredNetworks) {
        logi("requestEmergencyNetworkScan: preferredNetworks=" + preferredNetworks);
        mEmergencyNetworkScanSignal = new CancellationSignal();
        mWwanSelectorCallback.onRequestEmergencyNetworkScan(
                preferredNetworks,
                DomainSelectionService.SCAN_TYPE_FULL_SERVICE, false,
                mEmergencyNetworkScanSignal,
                (regResult) -> {
                    logi("requestEmergencyNetworkScan-onComplete");
                    obtainMessage(EVENT_EMERGENCY_NETWORK_SCAN_RESULT, regResult).sendToTarget();
                });
    }

    /**
     * Handles the emergency network scan result.
     *
     * This triggers the emergency service fallback to modem when the emergency service is not
     * supported but the emergency service fallback is supported in the current network.
     *
     * @param regResult The emergency registration result that is triggered
     *                  by the emergency network scan.
     */
    private void handleEmergencyNetworkScanResult(EmergencyRegistrationResult regResult) {
        logi("handleEmergencyNetworkScanResult: " + regResult);

        mEmergencyNetworkScanInProgress = false;
        mEmergencyNetworkScanSignal = null;

        int accessNetworkType = regResult.getAccessNetwork();
        int domain = NetworkRegistrationInfo.DOMAIN_CS;

        if (accessNetworkType == AccessNetworkType.NGRAN) {
            domain = NetworkRegistrationInfo.DOMAIN_PS;
        } else if (accessNetworkType == AccessNetworkType.EUTRAN) {
            if (regResult.getDomain() == NetworkRegistrationInfo.DOMAIN_CS) {
                logi("PS emergency service is not supported in LTE network.");
            } else {
                domain = NetworkRegistrationInfo.DOMAIN_PS;
            }
        }

        notifyWwanSelected(domain, (domain == NetworkRegistrationInfo.DOMAIN_PS));
    }

    /**
     * Checks if the emergency SMS messages over IMS is available according to the carrier
     * configuration and the current network states.
     */
    private boolean isImsEmergencySmsAvailable() {
        boolean isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService =
                isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService();
        boolean networkAvailable = isNetworkAvailableForImsEmergencySms();

        logi("isImsEmergencySmsAvailable: "
                + "emergencySmsOverIms=" + isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService
                + ", mmTelFeatureAvailable=" + mImsStateTracker.isMmTelFeatureAvailable()
                + ", networkAvailable=" + networkAvailable);

        return isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService
                && mImsStateTracker.isMmTelFeatureAvailable()
                && networkAvailable;
    }

    /**
     * Checks if sending emergency SMS messages over IMS is supported when in the network(LTE/NR)
     * normal/limited(Emergency only) service mode from the carrier configuration.
     */
    private boolean isEmergencySmsOverImsSupportedIfNetworkLimitedOrInService() {
        if (mEmergencySmsOverImsSupportedByConfig == null) {
            CarrierConfigManager ccm = mContext.getSystemService(CarrierConfigManager.class);

            if (ccm == null) {
                loge("CarrierConfigManager is null");
                return false;
            }

            PersistableBundle b = ccm.getConfigForSubId(getSubId());

            if (b == null) {
                loge("PersistableBundle is null");
                return false;
            }

            mEmergencySmsOverImsSupportedByConfig = b.getBoolean(
                    CarrierConfigManager.KEY_SUPPORT_EMERGENCY_SMS_OVER_IMS_BOOL);
        }

        return mEmergencySmsOverImsSupportedByConfig;
    }

    /**
     * Checks if the emergency service is available in the LTE service mode.
     */
    private boolean isLteEmergencyAvailableInService() {
        if (mServiceState == null) {
            return false;
        }

        final NetworkRegistrationInfo regInfo = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        if (regInfo != null
                && regInfo.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_LTE
                && regInfo.isRegistered()) {
            return isEmergencyServiceSupported(regInfo) && isEmergencyServiceAllowed();
        }
        return false;
    }

    /**
     * Checks if the emergency service is available in the limited LTE service(Emergency only) mode.
     */
    private boolean isLteEmergencyAvailableInLimitedService() {
        if (mServiceState == null) {
            return false;
        }

        final NetworkRegistrationInfo regInfo = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (regInfo != null
                && regInfo.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_LTE
                && regInfo.isEmergencyEnabled()) {
            return isEmergencyServiceSupported(regInfo) && isEmergencyServiceAllowed();
        }
        return false;
    }

    /**
     * Checks if the network is available for the IMS emergency SMS.
     */
    private boolean isNetworkAvailableForImsEmergencySms() {
        return isLteEmergencyAvailableInService()
                || isLteEmergencyAvailableInLimitedService()
                || isNrEmergencyAvailable();
    }

    /**
     * Checks if the emergency service is supported by the network.
     *
     * This checks if "Emergency bearer services indicator (EMC-BS)" field (bits) set to
     * the "Emergency bearer services in S1 mode supported".
     *
     * @return {@code true} if the emergency service is supported by the network,
     *         {@code false} otherwise.
     */
    private boolean isEmergencyServiceSupported(@NonNull NetworkRegistrationInfo regInfo) {
        final DataSpecificRegistrationInfo dsRegInfo = regInfo.getDataSpecificInfo();
        if (dsRegInfo != null) {
            final VopsSupportInfo vopsSupportInfo = dsRegInfo.getVopsSupportInfo();
            return vopsSupportInfo != null
                    && vopsSupportInfo.isEmergencyServiceSupported();
        }
        return false;
    }

    /**
     * Checks if the emergency service fallback is supported by the network.
     *
     * @return {@code true} if the emergency service fallback is supported by the network,
     *         {@code false} otherwise.
     */
    private boolean isEmergencyServiceFallbackSupported(@NonNull NetworkRegistrationInfo regInfo) {
        final DataSpecificRegistrationInfo dsRegInfo = regInfo.getDataSpecificInfo();
        if (dsRegInfo != null) {
            final VopsSupportInfo vopsSupportInfo = dsRegInfo.getVopsSupportInfo();
            return vopsSupportInfo != null
                    && vopsSupportInfo.isEmergencyServiceFallbackSupported();
        }
        return false;
    }

    /**
     * Checks if the emergency service is allowed (not barred) by the network.
     *
     * This checks if SystemInformationBlockType2 includes the ac-BarringInfo and
     * with the ac-BarringForEmergency set to FALSE or
     * if the SystemInformationBlockType2 does not include the ac-BarringInfo.
     *
     * @return {@code true} if the emergency service is allowed by the network,
     *         {@code false} otherwise.
     */
    private boolean isEmergencyServiceAllowed() {
        if (mBarringInfo == null) {
            return true;
        }
        final BarringInfo.BarringServiceInfo bsi =
                mBarringInfo.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY);
        return !bsi.isBarred();
    }

    /**
     * Checks if the emergency service fallback is available in the NR network
     * because the emergency service is not supported.
     */
    private boolean isNrEmergencyServiceFallbackRequired() {
        if (mServiceState == null) {
            return false;
        }

        final NetworkRegistrationInfo regInfo = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        if (regInfo != null
                && regInfo.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_NR
                && regInfo.isRegistered()) {
            return !isEmergencyServiceSupported(regInfo)
                    && isEmergencyServiceFallbackSupported(regInfo);
        }
        return false;
    }

    /**
     * Checks if the emergency service is available in the NR network.
     */
    private boolean isNrEmergencyAvailable() {
        if (mServiceState == null) {
            return false;
        }

        final NetworkRegistrationInfo regInfo = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        if (regInfo != null
                && regInfo.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_NR
                && regInfo.isRegistered()) {
            return isEmergencyServiceSupported(regInfo)
                    || isEmergencyServiceFallbackSupported(regInfo);
        }
        return false;
    }
}
