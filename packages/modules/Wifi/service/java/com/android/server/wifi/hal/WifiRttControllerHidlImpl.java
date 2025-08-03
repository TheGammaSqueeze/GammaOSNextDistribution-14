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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.RttPeerType;
import android.hardware.wifi.V1_0.RttResult;
import android.hardware.wifi.V1_0.RttStatus;
import android.hardware.wifi.V1_0.RttType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_6.RttBw;
import android.hardware.wifi.V1_6.RttPreamble;
import android.hardware.wifi.V1_6.WifiChannelWidthInMhz;
import android.net.MacAddress;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.util.GeneralUtil.Mutable;
import com.android.server.wifi.util.NativeUtil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * HIDL implementation of the IWifiRttController interface.
 */
public class WifiRttControllerHidlImpl implements IWifiRttController {
    private static final String TAG = "WifiRttControllerHidl";
    private boolean mVerboseLoggingEnabled = false;

    private android.hardware.wifi.V1_0.IWifiRttController mWifiRttController;
    private android.hardware.wifi.V1_4.IWifiRttController mWifiRttController14;
    private android.hardware.wifi.V1_6.IWifiRttController mWifiRttController16;
    private final WifiRttControllerEventCallback mWifiRttControllerEventCallback;
    private final WifiRttControllerEventCallback14 mWifiRttControllerEventCallback14;
    private final WifiRttControllerEventCallback16 mWifiRttControllerEventCallback16;
    private WifiRttController.Capabilities mRttCapabilities;
    private Set<WifiRttController.RttControllerRangingResultsCallback> mRangingResultsCallbacks =
            new HashSet<>();

    public WifiRttControllerHidlImpl(
            @NonNull android.hardware.wifi.V1_0.IWifiRttController rttController) {
        mWifiRttController = rttController;
        mWifiRttController14 =
                android.hardware.wifi.V1_4.IWifiRttController.castFrom(mWifiRttController);
        mWifiRttController16 =
                android.hardware.wifi.V1_6.IWifiRttController.castFrom(mWifiRttController);
        mWifiRttControllerEventCallback = new WifiRttControllerEventCallback();
        mWifiRttControllerEventCallback14 = new WifiRttControllerEventCallback14();
        mWifiRttControllerEventCallback16 = new WifiRttControllerEventCallback16();
    }

    /**
     * See comments for {@link IWifiRttController#setup()}
     */
    public boolean setup() {
        final String methodStr = "setup";
        return validateAndCall(methodStr, false,
                () -> setupInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiRttController#enableVerboseLogging(boolean)}
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * See comments for {@link IWifiRttController#registerRangingResultsCallback(
     *                         WifiRttController.RttControllerRangingResultsCallback)}
     */
    public void registerRangingResultsCallback(
            WifiRttController.RttControllerRangingResultsCallback callback) {
        if (!mRangingResultsCallbacks.add(callback)) {
            Log.e(TAG, "Ranging results callback was already registered");
        }
    }

    /**
     * See comments for {@link IWifiRttController#validate()}
     */
    public boolean validate() {
        final String methodStr = "validate";
        return validateAndCall(methodStr, false,
                () -> validateInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiRttController#getRttCapabilities()}
     */
    @Nullable
    public WifiRttController.Capabilities getRttCapabilities() {
        return mRttCapabilities;
    }

    /**
     * See comments for {@link IWifiRttController#rangeRequest(int, RangingRequest)}
     */
    public boolean rangeRequest(int cmdId, RangingRequest request) {
        final String methodStr = "rangeRequest";
        return validateAndCall(methodStr, false,
                () -> rangeRequestInternal(methodStr, cmdId, request));
    }

    /**
     * See comments for {@link IWifiRttController#rangeCancel(int, List)}
     */
    public boolean rangeCancel(int cmdId, List<MacAddress> macAddresses) {
        final String methodStr = "rangeCancel";
        return validateAndCall(methodStr, false,
                () -> rangeCancelInternal(methodStr, cmdId, macAddresses));
    }

    /**
     * See comments for {@link IWifiRttController#dump(PrintWriter)}
     */
    public void dump(PrintWriter pw) {
        pw.println("WifiRttController:");
        pw.println("  mIWifiRttController: " + mWifiRttController);
        pw.println("  mRttCapabilities: " + mRttCapabilities);
    }


    // Internal Implementations

    private boolean setupInternal(String methodStr) {
        try {
            if (mWifiRttController16 != null) {
                mWifiRttController16.registerEventCallback_1_6(mWifiRttControllerEventCallback16);
            } else if (mWifiRttController14 != null) {
                mWifiRttController14.registerEventCallback_1_4(mWifiRttControllerEventCallback14);
            } else {
                mWifiRttController.registerEventCallback(mWifiRttControllerEventCallback);
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
        updateRttCapabilities();
        return true;
    }

    private boolean validateInternal(String methodStr) {
        Mutable<Boolean> isRttControllerValid = new Mutable<>(false);
        try {
            mWifiRttController.getBoundIface(
                    (status, iface) -> {
                        if (isOk(status, methodStr)) {
                            isRttControllerValid.value = true;
                        }
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return isRttControllerValid.value;
    }

    private boolean rangeRequestInternal(String methodStr, int cmdId, RangingRequest request) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "rangeRequest: cmdId=" + cmdId + ", # of requests="
                    + request.mRttPeers.size() + ", request=" + request);
        }

        updateRttCapabilities();
        if (mWifiRttController16 != null) {
            return sendRangeRequest16(cmdId, request);
        } else if (mWifiRttController14 != null) {
            return sendRangeRequest14(cmdId, request);
        } else {
            return sendRangeRequest(cmdId, request);
        }
    }

    private boolean sendRangeRequest(int cmdId, RangingRequest request) {
        final String methodStr = "sendRangeRequest";
        ArrayList<RttConfig> rttConfig =
                convertRangingRequestToRttConfigs(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mWifiRttController.rangeRequest(cmdId, rttConfig);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean sendRangeRequest14(int cmdId, RangingRequest request) {
        final String methodStr = "sendRangeRequest14";
        ArrayList<android.hardware.wifi.V1_4.RttConfig> rttConfig =
                convertRangingRequestToRttConfigs14(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest14: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest14: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mWifiRttController14.rangeRequest_1_4(cmdId, rttConfig);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean sendRangeRequest16(int cmdId, RangingRequest request) {
        final String methodStr = "sendRangeRequest16";
        ArrayList<android.hardware.wifi.V1_6.RttConfig> rttConfig =
                convertRangingRequestToRttConfigs16(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest16: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest16: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mWifiRttController16.rangeRequest_1_6(cmdId, rttConfig);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    boolean rangeCancelInternal(String methodStr, int cmdId, List<MacAddress> macAddresses) {
        if (mVerboseLoggingEnabled) Log.v(TAG, "rangeCancel: cmdId=" + cmdId);
        try {
            ArrayList<byte[]> macBytes = new ArrayList<>();
            for (MacAddress mac : macAddresses) {
                macBytes.add(mac.toByteArray());
            }
            WifiStatus status = mWifiRttController.rangeCancel(cmdId, macBytes);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    /**
     *  Callback for HAL events on 1.0 WifiRttController
     */
    private class WifiRttControllerEventCallback extends IWifiRttControllerEventCallback.Stub {
        /**
         * Callback from the HAL with range results.
         *
         * @param cmdId Command ID specified in the original request.
         * @param halResults A list of range results.
         */
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                int numResults = halResults != null ? halResults.size() : -1;
                Log.v(TAG, "onResults: cmdId=" + cmdId + ", # of results=" + numResults);
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }

    /**
     *  Callback for HAL events on 1.4 WifiRttController
     */
    private class WifiRttControllerEventCallback14 extends
            android.hardware.wifi.V1_4.IWifiRttControllerEventCallback.Stub {
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_4(int cmdId,
                ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                int numResults = halResults != null ? halResults.size() : -1;
                Log.v(TAG, "onResults_1_4: cmdId=" + cmdId + ", # of results=" + numResults);
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults14(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }

    /**
     *  Callback for HAL events on 1.6 WifiRttController
     */
    private class WifiRttControllerEventCallback16 extends
            android.hardware.wifi.V1_6.IWifiRttControllerEventCallback.Stub {
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_4(int cmdId,
                ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_6(int cmdId,
                ArrayList<android.hardware.wifi.V1_6.RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                int numResults = halResults != null ? halResults.size() : -1;
                Log.v(TAG, "onResults_1_6: cmdId=" + cmdId + ", # of results=" + numResults);
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults16(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }


    // Helper Functions

    private ArrayList<RangingResult> convertHalResultsRangingResults(
            ArrayList<RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult.Builder()
                    .setStatus(convertHalStatusToFrameworkStatus(rttResult.status))
                    .setMacAddress(MacAddress.fromBytes(rttResult.addr))
                    .setDistanceMm(rttResult.distanceInMm)
                    .setDistanceStdDevMm(rttResult.distanceSdInMm)
                    .setRssi(rttResult.rssi / -2)
                    .setNumAttemptedMeasurements(rttResult.numberPerBurstPeer)
                    .setNumSuccessfulMeasurements(rttResult.successNumber)
                    .setLci(lci)
                    .setLcr(lcr)
                    .setUnverifiedResponderLocation(responderLocation)
                    .setRangingTimestampMillis(
                            rttResult.timeStampInUs / WifiRttController.CONVERSION_US_TO_MS)
                    .set80211mcMeasurement(rttResult.type == RttType.TWO_SIDED)
                    .build());
        }
        return rangingResults;
    }

    private ArrayList<RangingResult> convertHalResultsRangingResults14(
            ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (android.hardware.wifi.V1_4.RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult.Builder()
                    .setStatus(convertHalStatusToFrameworkStatus(rttResult.status))
                    .setMacAddress(MacAddress.fromBytes(rttResult.addr))
                    .setDistanceMm(rttResult.distanceInMm)
                    .setDistanceStdDevMm(rttResult.distanceSdInMm)
                    .setRssi(rttResult.rssi / -2)
                    .setNumAttemptedMeasurements(rttResult.numberPerBurstPeer)
                    .setNumSuccessfulMeasurements(rttResult.successNumber)
                    .setLci(lci)
                    .setLcr(lcr)
                    .setUnverifiedResponderLocation(responderLocation)
                    .setRangingTimestampMillis(
                            rttResult.timeStampInUs / WifiRttController.CONVERSION_US_TO_MS)
                    .set80211mcMeasurement(rttResult.type == RttType.TWO_SIDED)
                    .build());
        }
        return rangingResults;
    }

    private ArrayList<RangingResult> convertHalResultsRangingResults16(
            ArrayList<android.hardware.wifi.V1_6.RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (android.hardware.wifi.V1_6.RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult.Builder()
                    .setStatus(convertHalStatusToFrameworkStatus(rttResult.status))
                    .setMacAddress(MacAddress.fromBytes(rttResult.addr))
                    .setDistanceMm(rttResult.distanceInMm)
                    .setDistanceStdDevMm(rttResult.distanceSdInMm)
                    .setRssi(rttResult.rssi / -2)
                    .setNumAttemptedMeasurements(rttResult.numberPerBurstPeer)
                    .setNumSuccessfulMeasurements(rttResult.successNumber)
                    .setLci(lci)
                    .setLcr(lcr)
                    .setUnverifiedResponderLocation(responderLocation)
                    .setRangingTimestampMillis(
                            rttResult.timeStampInUs / WifiRttController.CONVERSION_US_TO_MS)
                    .set80211mcMeasurement(rttResult.type == RttType.TWO_SIDED)
                    .build());
        }
        return rangingResults;
    }

    private @WifiRttController.FrameworkRttStatus
            int convertHalStatusToFrameworkStatus(int halStatus) {
        switch (halStatus) {
            case RttStatus.SUCCESS:
                return WifiRttController.FRAMEWORK_RTT_STATUS_SUCCESS;
            case RttStatus.FAILURE:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAILURE;
            case RttStatus.FAIL_NO_RSP:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_NO_RSP;
            case RttStatus.FAIL_REJECTED:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_REJECTED;
            case RttStatus.FAIL_NOT_SCHEDULED_YET:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_NOT_SCHEDULED_YET;
            case RttStatus.FAIL_TM_TIMEOUT:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_TM_TIMEOUT;
            case RttStatus.FAIL_AP_ON_DIFF_CHANNEL:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_AP_ON_DIFF_CHANNEL;
            case RttStatus.FAIL_NO_CAPABILITY:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_NO_CAPABILITY;
            case RttStatus.ABORTED:
                return WifiRttController.FRAMEWORK_RTT_STATUS_ABORTED;
            case RttStatus.FAIL_INVALID_TS:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_INVALID_TS;
            case RttStatus.FAIL_PROTOCOL:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_PROTOCOL;
            case RttStatus.FAIL_SCHEDULE:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_SCHEDULE;
            case RttStatus.FAIL_BUSY_TRY_LATER:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_BUSY_TRY_LATER;
            case RttStatus.INVALID_REQ:
                return WifiRttController.FRAMEWORK_RTT_STATUS_INVALID_REQ;
            case RttStatus.NO_WIFI:
                return WifiRttController.FRAMEWORK_RTT_STATUS_NO_WIFI;
            case RttStatus.FAIL_FTM_PARAM_OVERRIDE:
                return WifiRttController.FRAMEWORK_RTT_STATUS_FAIL_FTM_PARAM_OVERRIDE;
            default:
                Log.e(TAG, "Unrecognized RttStatus: " + halStatus);
                return WifiRttController.FRAMEWORK_RTT_STATUS_UNKNOWN;
        }
    }

    private void updateRttCapabilities() {
        if (mRttCapabilities != null) return;
        if (mVerboseLoggingEnabled) Log.v(TAG, "updateRttCapabilities");

        try {
            if (mWifiRttController16 != null) {
                mWifiRttController16.getCapabilities_1_6(
                        (status, capabilities16) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities16);
                            }
                            mRttCapabilities = new WifiRttController.Capabilities(capabilities16);
                        });
            } else if (mWifiRttController14 != null) {
                mWifiRttController14.getCapabilities_1_4(
                        (status, capabilities14) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities14);
                            }
                            mRttCapabilities = new WifiRttController.Capabilities(capabilities14);
                        });
            } else {
                mWifiRttController.getCapabilities(
                        (status, capabilities) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities);
                            }
                            mRttCapabilities = new WifiRttController.Capabilities(capabilities);
                        });

            }
        } catch (RemoteException e) {
            handleRemoteException(e, "updateRttCapabilities");
        }

        if (mRttCapabilities != null && !mRttCapabilities.rttFtmSupported) {
            Log.wtf(TAG, "Firmware indicates RTT is not supported - but device supports RTT - "
                    + "ignored!?");
        }
    }

    private static ArrayList<RttConfig> convertRangingRequestToRttConfigs(
            RangingRequest request, WifiRttController.Capabilities cap) {
        ArrayList<RttConfig> rttConfigs = new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            RttConfig config = new RttConfig();

            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreambleFromResponderPreamble(responder.preamble);
                validateBwAndPreambleCombination(config.bw, config.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
    }

    private static void validateBwAndPreambleCombination(int bw, int preamble) {
        if (bw <= RttBw.BW_20MHZ) {
            return;
        }
        if (bw == RttBw.BW_40MHZ && preamble >= RttPreamble.HT) {
            return;
        }
        if (bw == RttBw.BW_320MHZ && preamble == RttPreamble.EHT) {
            return;
        }
        if (bw >= RttBw.BW_80MHZ && bw < RttBw.BW_320MHZ && preamble >= RttPreamble.VHT) {
            return;
        }
        throw new IllegalArgumentException(
                "bw and preamble combination is invalid, bw: " + bw + " preamble: " + preamble);
    }

    private static ArrayList<android.hardware.wifi.V1_4.RttConfig>
            convertRangingRequestToRttConfigs14(RangingRequest request,
            WifiRttController.Capabilities cap) {
        ArrayList<android.hardware.wifi.V1_4.RttConfig> rttConfigs =
                new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            android.hardware.wifi.V1_4.RttConfig config =
                    new android.hardware.wifi.V1_4.RttConfig();
            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreamble14FromResponderPreamble(responder.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
    }

    private static ArrayList<android.hardware.wifi.V1_6.RttConfig>
            convertRangingRequestToRttConfigs16(RangingRequest request,
            WifiRttController.Capabilities cap) {
        ArrayList<android.hardware.wifi.V1_6.RttConfig> rttConfigs =
                new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            android.hardware.wifi.V1_6.RttConfig config =
                    new android.hardware.wifi.V1_6.RttConfig();
            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreamble14FromResponderPreamble(responder.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
    }

    private static int halRttPeerTypeFromResponderType(int responderType) {
        switch (responderType) {
            case ResponderConfig.RESPONDER_AP:
                return RttPeerType.AP;
            case ResponderConfig.RESPONDER_STA:
                return RttPeerType.STA;
            case ResponderConfig.RESPONDER_P2P_GO:
                return RttPeerType.P2P_GO;
            case ResponderConfig.RESPONDER_P2P_CLIENT:
                return RttPeerType.P2P_CLIENT;
            case ResponderConfig.RESPONDER_AWARE:
                return RttPeerType.NAN;
            default:
                throw new IllegalArgumentException(
                        "halRttPeerTypeFromResponderType: bad " + responderType);
        }
    }

    private static int halChannelWidthFromResponderChannelWidth(int responderChannelWidth) {
        switch (responderChannelWidth) {
            case ResponderConfig.CHANNEL_WIDTH_20MHZ:
                return WifiChannelWidthInMhz.WIDTH_20;
            case ResponderConfig.CHANNEL_WIDTH_40MHZ:
                return WifiChannelWidthInMhz.WIDTH_40;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ:
                return WifiChannelWidthInMhz.WIDTH_80;
            case ResponderConfig.CHANNEL_WIDTH_160MHZ:
                return WifiChannelWidthInMhz.WIDTH_160;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return WifiChannelWidthInMhz.WIDTH_80P80;
            case ResponderConfig.CHANNEL_WIDTH_320MHZ:
                return WifiChannelWidthInMhz.WIDTH_320;
            default:
                throw new IllegalArgumentException(
                        "halChannelWidthFromResponderChannelWidth: bad " + responderChannelWidth);
        }
    }

    private static int halRttChannelBandwidthFromResponderChannelWidth(int responderChannelWidth) {
        switch (responderChannelWidth) {
            case ResponderConfig.CHANNEL_WIDTH_20MHZ:
                return RttBw.BW_20MHZ;
            case ResponderConfig.CHANNEL_WIDTH_40MHZ:
                return RttBw.BW_40MHZ;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ:
                return RttBw.BW_80MHZ;
            case ResponderConfig.CHANNEL_WIDTH_160MHZ:
            case ResponderConfig.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return RttBw.BW_160MHZ;
            case ResponderConfig.CHANNEL_WIDTH_320MHZ:
                return RttBw.BW_320MHZ;
            default:
                throw new IllegalArgumentException(
                        "halRttChannelBandwidthFromHalBandwidth: bad " + responderChannelWidth);
        }
    }

    private static int halRttPreambleFromResponderPreamble(int responderPreamble) {
        switch (responderPreamble) {
            case ResponderConfig.PREAMBLE_LEGACY:
                return RttPreamble.LEGACY;
            case ResponderConfig.PREAMBLE_HT:
                return RttPreamble.HT;
            case ResponderConfig.PREAMBLE_VHT:
                return RttPreamble.VHT;
            case ResponderConfig.PREAMBLE_HE:
                return RttPreamble.HE;
            case ResponderConfig.PREAMBLE_EHT:
                return RttPreamble.EHT;
            default:
                throw new IllegalArgumentException(
                        "halRttPreambleFromResponderPreamble: bad " + responderPreamble);
        }
    }

    private static int halRttPreamble14FromResponderPreamble(int responderPreamble) {
        switch (responderPreamble) {
            case ResponderConfig.PREAMBLE_LEGACY:
                return RttPreamble.LEGACY;
            case ResponderConfig.PREAMBLE_HT:
                return RttPreamble.HT;
            case ResponderConfig.PREAMBLE_VHT:
                return RttPreamble.VHT;
            case ResponderConfig.PREAMBLE_HE:
                return RttPreamble.HE;
            default:
                throw new IllegalArgumentException(
                        "halRttPreamble14FromResponderPreamble: bad " + responderPreamble);
        }
    }

    /**
     * Check whether the selected RTT channel bandwidth is supported by the device.
     * If supported, return the requested bandwidth.
     * If not supported, return the next lower bandwidth which is supported.
     * If none, throw an IllegalArgumentException.
     *
     * Note: the halRttChannelBandwidth is a single bit flag from the HAL RttBw type.
     */
    private static int halRttChannelBandwidthCapabilityLimiter(int halRttChannelBandwidth,
            WifiRttController.Capabilities cap) {
        int requestedBandwidth = halRttChannelBandwidth;
        while ((halRttChannelBandwidth != 0) && ((halRttChannelBandwidth & cap.bwSupported) == 0)) {
            halRttChannelBandwidth >>= 1;
        }

        if (halRttChannelBandwidth != 0) {
            return halRttChannelBandwidth;
        }

        throw new IllegalArgumentException(
                "RTT BW=" + requestedBandwidth + ", not supported by device capabilities=" + cap
                        + " - and no supported alternative");
    }

    /**
     * Check whether the selected RTT preamble is supported by the device.
     * If supported, return the requested preamble.
     * If not supported, return the next "lower" preamble which is supported.
     * If none, throw an IllegalArgumentException.
     *
     * Note: the halRttPreamble is a single bit flag from the HAL RttPreamble type.
     */
    private static int halRttPreambleCapabilityLimiter(int halRttPreamble,
            WifiRttController.Capabilities cap) {
        int requestedPreamble = halRttPreamble;
        while ((halRttPreamble != 0) && ((halRttPreamble & cap.preambleSupported) == 0)) {
            halRttPreamble >>= 1;
        }

        if (halRttPreamble != 0) {
            return halRttPreamble;
        }

        throw new IllegalArgumentException(
                "RTT Preamble=" + requestedPreamble + ", not supported by device capabilities="
                        + cap + " - and no supported alternative");
    }

    private void dispatchOnRangingResults(int cmdId, List<RangingResult> rangingResults) {
        for (WifiRttController.RttControllerRangingResultsCallback
                callback : mRangingResultsCallbacks) {
            callback.onRangingResults(cmdId, rangingResults);
        }
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifiRttController = null;
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiRttController == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiRttController is null");
            return defaultVal;
        }
        return supplier.get();
    }
}
