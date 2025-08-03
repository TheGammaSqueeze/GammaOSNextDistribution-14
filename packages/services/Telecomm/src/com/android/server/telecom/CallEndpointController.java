/*
 * Copyright 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.Context;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.ResultReceiver;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointException;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides to {@link CallsManager} the service that can request change of CallEndpoint to the
 * {@link CallAudioManager}. And notify change of CallEndpoint status to {@link CallsManager}
 */
public class CallEndpointController extends CallsManagerListenerBase {
    public static final int CHANGE_TIMEOUT_SEC = 2;
    public static final int RESULT_REQUEST_SUCCESS = 0;
    public static final int RESULT_ENDPOINT_DOES_NOT_EXIST = 1;
    public static final int RESULT_REQUEST_TIME_OUT = 2;
    public static final int RESULT_ANOTHER_REQUEST = 3;
    public static final int RESULT_UNSPECIFIED_ERROR = 4;

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final HashMap<Integer, Integer> mRouteToTypeMap;
    private final HashMap<Integer, Integer> mTypeToRouteMap;
    private final Map<ParcelUuid, String> mBluetoothAddressMap = new HashMap<>();
    private final Set<CallEndpoint> mAvailableCallEndpoints = new HashSet<>();
    private CallEndpoint mActiveCallEndpoint;
    private ParcelUuid mRequestedEndpointId;
    private CompletableFuture<Integer> mPendingChangeRequest;

    public CallEndpointController(Context context, CallsManager callsManager) {
        mContext = context;
        mCallsManager = callsManager;

        mRouteToTypeMap = new HashMap<>(5);
        mRouteToTypeMap.put(CallAudioState.ROUTE_EARPIECE, CallEndpoint.TYPE_EARPIECE);
        mRouteToTypeMap.put(CallAudioState.ROUTE_BLUETOOTH, CallEndpoint.TYPE_BLUETOOTH);
        mRouteToTypeMap.put(CallAudioState.ROUTE_WIRED_HEADSET, CallEndpoint.TYPE_WIRED_HEADSET);
        mRouteToTypeMap.put(CallAudioState.ROUTE_SPEAKER, CallEndpoint.TYPE_SPEAKER);
        mRouteToTypeMap.put(CallAudioState.ROUTE_STREAMING, CallEndpoint.TYPE_STREAMING);

        mTypeToRouteMap = new HashMap<>(5);
        mTypeToRouteMap.put(CallEndpoint.TYPE_EARPIECE, CallAudioState.ROUTE_EARPIECE);
        mTypeToRouteMap.put(CallEndpoint.TYPE_BLUETOOTH, CallAudioState.ROUTE_BLUETOOTH);
        mTypeToRouteMap.put(CallEndpoint.TYPE_WIRED_HEADSET, CallAudioState.ROUTE_WIRED_HEADSET);
        mTypeToRouteMap.put(CallEndpoint.TYPE_SPEAKER, CallAudioState.ROUTE_SPEAKER);
        mTypeToRouteMap.put(CallEndpoint.TYPE_STREAMING, CallAudioState.ROUTE_STREAMING);
    }

    @VisibleForTesting
    public CallEndpoint getCurrentCallEndpoint() {
        return mActiveCallEndpoint;
    }

    @VisibleForTesting
    public Set<CallEndpoint> getAvailableEndpoints() {
        return mAvailableCallEndpoints;
    }

    public void requestCallEndpointChange(CallEndpoint endpoint, ResultReceiver callback) {
        Log.i(this, "requestCallEndpointChange %s", endpoint);
        int route = mTypeToRouteMap.get(endpoint.getEndpointType());
        String bluetoothAddress = getBluetoothAddress(endpoint);

        if (findMatchingTypeEndpoint(endpoint.getEndpointType()) == null ||
                (route == CallAudioState.ROUTE_BLUETOOTH && bluetoothAddress == null)) {
            callback.send(CallEndpoint.ENDPOINT_OPERATION_FAILED,
                    getErrorResult(RESULT_ENDPOINT_DOES_NOT_EXIST));
            return;
        }

        if (isCurrentEndpointRequestedEndpoint(route, bluetoothAddress)) {
            callback.send(CallEndpoint.ENDPOINT_OPERATION_SUCCESS, new Bundle());
            return;
        }

        if (mPendingChangeRequest != null && !mPendingChangeRequest.isDone()) {
            mPendingChangeRequest.complete(RESULT_ANOTHER_REQUEST);
            mPendingChangeRequest = null;
            mRequestedEndpointId = null;
        }

        mPendingChangeRequest = new CompletableFuture<Integer>()
                .completeOnTimeout(RESULT_REQUEST_TIME_OUT, CHANGE_TIMEOUT_SEC, TimeUnit.SECONDS);

        mPendingChangeRequest.thenAcceptAsync((result) -> {
            if (result == RESULT_REQUEST_SUCCESS) {
                callback.send(CallEndpoint.ENDPOINT_OPERATION_SUCCESS, new Bundle());
            } else {
                callback.send(CallEndpoint.ENDPOINT_OPERATION_FAILED, getErrorResult(result));
            }
        });
        mRequestedEndpointId = endpoint.getIdentifier();
        mCallsManager.getCallAudioManager().setAudioRoute(route, bluetoothAddress);
    }

    public boolean isCurrentEndpointRequestedEndpoint(int requestedRoute, String requestedAddress) {
        if (mCallsManager.getCallAudioManager() == null
                || mCallsManager.getCallAudioManager().getCallAudioState() == null) {
            return false;
        }
        CallAudioState currentAudioState = mCallsManager.getCallAudioManager().getCallAudioState();
        if (requestedRoute == currentAudioState.getRoute()) {
            if (requestedRoute != CallAudioState.ROUTE_BLUETOOTH) {
                // The audio route (earpiece, speaker, etc.) is already active
                // and Telecom can ignore the spam request!
                Log.i(this, "iCERE: user requested a non-BT route that is already active");
                return true;
            } else if (hasSameBluetoothAddress(currentAudioState, requestedAddress)) {
                // if the requested (BT route, device) is active, ignore the request...
                Log.i(this, "iCERE: user requested a BT endpoint that is already active");
                return true;
            }
        }
        return false;
    }

    public boolean hasSameBluetoothAddress(CallAudioState audioState, String requestedAddress) {
        boolean hasActiveBtDevice = audioState.getActiveBluetoothDevice() != null;
        return hasActiveBtDevice && requestedAddress.equals(
                audioState.getActiveBluetoothDevice().getAddress());
    }

    private Bundle getErrorResult(int result) {
        String message;
        int resultCode;
        switch (result) {
            case RESULT_ENDPOINT_DOES_NOT_EXIST:
                message = "Requested CallEndpoint does not exist";
                resultCode = CallEndpointException.ERROR_ENDPOINT_DOES_NOT_EXIST;
                break;
            case RESULT_REQUEST_TIME_OUT:
                message = "The operation was not completed on time";
                resultCode = CallEndpointException.ERROR_REQUEST_TIME_OUT;
                break;
            case RESULT_ANOTHER_REQUEST:
                message = "The operation was canceled by another request";
                resultCode = CallEndpointException.ERROR_ANOTHER_REQUEST;
                break;
            default:
                message = "The operation has failed due to an unknown or unspecified error";
                resultCode = CallEndpointException.ERROR_UNSPECIFIED;
        }
        CallEndpointException exception = new CallEndpointException(message, resultCode);
        Bundle extras = new Bundle();
        extras.putParcelable(CallEndpointException.CHANGE_ERROR, exception);
        return extras;
    }

    @VisibleForTesting
    public String getBluetoothAddress(CallEndpoint endpoint) {
        return mBluetoothAddressMap.get(endpoint.getIdentifier());
    }

    private void notifyCallEndpointChange() {
        if (mActiveCallEndpoint == null) {
            Log.i(this, "notifyCallEndpointChange, invalid CallEndpoint");
            return;
        }

        if (mRequestedEndpointId != null && mPendingChangeRequest != null &&
                mRequestedEndpointId.equals(mActiveCallEndpoint.getIdentifier())) {
            mPendingChangeRequest.complete(RESULT_REQUEST_SUCCESS);
            mPendingChangeRequest = null;
            mRequestedEndpointId = null;
        }
        mCallsManager.updateCallEndpoint(mActiveCallEndpoint);

        Set<Call> calls = mCallsManager.getTrackedCalls();
        for (Call call : calls) {
            if (call != null && call.getConnectionService() != null) {
                call.getConnectionService().onCallEndpointChanged(call, mActiveCallEndpoint);
            } else if (call != null && call.getTransactionServiceWrapper() != null) {
                call.getTransactionServiceWrapper()
                        .onCallEndpointChanged(call, mActiveCallEndpoint);
            }
        }
    }

    private void notifyAvailableCallEndpointsChange() {
        mCallsManager.updateAvailableCallEndpoints(mAvailableCallEndpoints);

        Set<Call> calls = mCallsManager.getTrackedCalls();
        for (Call call : calls) {
            if (call != null && call.getConnectionService() != null) {
                call.getConnectionService().onAvailableCallEndpointsChanged(call,
                        mAvailableCallEndpoints);
            } else if (call != null && call.getTransactionServiceWrapper() != null) {
                call.getTransactionServiceWrapper()
                        .onAvailableCallEndpointsChanged(call, mAvailableCallEndpoints);
            }
        }
    }

    private void notifyMuteStateChange(boolean isMuted) {
        mCallsManager.updateMuteState(isMuted);

        Set<Call> calls = mCallsManager.getTrackedCalls();
        for (Call call : calls) {
            if (call != null && call.getConnectionService() != null) {
                call.getConnectionService().onMuteStateChanged(call, isMuted);
            } else if (call != null && call.getTransactionServiceWrapper() != null) {
                call.getTransactionServiceWrapper().onMuteStateChanged(call, isMuted);
            }
        }
    }

    private void createAvailableCallEndpoints(CallAudioState state) {
        Set<CallEndpoint> newAvailableEndpoints = new HashSet<>();
        Map<ParcelUuid, String> newBluetoothDevices = new HashMap<>();

        mRouteToTypeMap.forEach((route, type) -> {
            if ((state.getSupportedRouteMask() & route) != 0) {
                if (type == CallEndpoint.TYPE_STREAMING) {
                    if (state.getRoute() == CallAudioState.ROUTE_STREAMING) {
                        if (mActiveCallEndpoint == null
                                || mActiveCallEndpoint.getEndpointType() != type) {
                            mActiveCallEndpoint = new CallEndpoint(getEndpointName(type) != null
                                    ? getEndpointName(type) : "", type);
                        }
                    }
                } else if (type == CallEndpoint.TYPE_BLUETOOTH) {
                    for (BluetoothDevice device : state.getSupportedBluetoothDevices()) {
                        CallEndpoint endpoint = findMatchingBluetoothEndpoint(device);
                        if (endpoint == null) {
                            String deviceName = device.getName();
                            endpoint = new CallEndpoint(
                                    deviceName != null ? deviceName : "",
                                    CallEndpoint.TYPE_BLUETOOTH);
                        }
                        newAvailableEndpoints.add(endpoint);
                        newBluetoothDevices.put(endpoint.getIdentifier(), device.getAddress());

                        BluetoothDevice activeDevice = state.getActiveBluetoothDevice();
                        if (state.getRoute() == route && device.equals(activeDevice)) {
                            mActiveCallEndpoint = endpoint;
                        }
                    }
                } else {
                    CallEndpoint endpoint = findMatchingTypeEndpoint(type);
                    if (endpoint == null) {
                        endpoint = new CallEndpoint(
                                getEndpointName(type) != null ? getEndpointName(type) : "", type);
                    }
                    newAvailableEndpoints.add(endpoint);
                    if (state.getRoute() == route) {
                        mActiveCallEndpoint = endpoint;
                    }
                }
            }
        });
        mAvailableCallEndpoints.clear();
        mAvailableCallEndpoints.addAll(newAvailableEndpoints);
        mBluetoothAddressMap.clear();
        mBluetoothAddressMap.putAll(newBluetoothDevices);
    }

    private CallEndpoint findMatchingTypeEndpoint(int targetType) {
        for (CallEndpoint endpoint : mAvailableCallEndpoints) {
            if (endpoint.getEndpointType() == targetType) {
                return endpoint;
            }
        }
        return null;
    }

    private CallEndpoint findMatchingBluetoothEndpoint(BluetoothDevice device) {
        final String targetAddress = device.getAddress();
        if (targetAddress != null) {
            for (CallEndpoint endpoint : mAvailableCallEndpoints) {
                final String address = mBluetoothAddressMap.get(endpoint.getIdentifier());
                if (targetAddress.equals(address)) {
                    return endpoint;
                }
            }
        }
        return null;
    }

    private boolean isAvailableEndpointChanged(CallAudioState oldState, CallAudioState newState) {
        if (oldState == null) {
            return true;
        }
        if ((oldState.getSupportedRouteMask() ^ newState.getSupportedRouteMask()) != 0) {
            return true;
        }
        if (oldState.getSupportedBluetoothDevices().size() !=
                newState.getSupportedBluetoothDevices().size()) {
            return true;
        }
        for (BluetoothDevice device : newState.getSupportedBluetoothDevices()) {
            if (!oldState.getSupportedBluetoothDevices().contains(device)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEndpointChanged(CallAudioState oldState, CallAudioState newState) {
        if (oldState == null) {
            return true;
        }
        if (oldState.getRoute() != newState.getRoute()) {
            return true;
        }
        if (newState.getRoute() == CallAudioState.ROUTE_BLUETOOTH) {
            if (oldState.getActiveBluetoothDevice() == null) {
                if (newState.getActiveBluetoothDevice() == null) {
                    return false;
                }
                return true;
            }
            return !oldState.getActiveBluetoothDevice().equals(newState.getActiveBluetoothDevice());
        }
        return false;
    }

    private boolean isMuteStateChanged(CallAudioState oldState, CallAudioState newState) {
        if (oldState == null) {
            return true;
        }
        return oldState.isMuted() != newState.isMuted();
    }

    private CharSequence getEndpointName(int endpointType) {
        switch (endpointType) {
            case CallEndpoint.TYPE_EARPIECE:
                return mContext.getText(R.string.callendpoint_name_earpiece);
            case CallEndpoint.TYPE_BLUETOOTH:
                return mContext.getText(R.string.callendpoint_name_bluetooth);
            case CallEndpoint.TYPE_WIRED_HEADSET:
                return mContext.getText(R.string.callendpoint_name_wiredheadset);
            case CallEndpoint.TYPE_SPEAKER:
                return mContext.getText(R.string.callendpoint_name_speaker);
            case CallEndpoint.TYPE_STREAMING:
                return mContext.getText(R.string.callendpoint_name_streaming);
            default:
                return mContext.getText(R.string.callendpoint_name_unknown);
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState oldState, CallAudioState newState) {
        Log.i(this, "onCallAudioStateChanged, audioState: %s -> %s", oldState, newState);

        if (newState == null) {
            Log.i(this, "onCallAudioStateChanged, invalid audioState");
            return;
        }

        createAvailableCallEndpoints(newState);

        boolean isforce = true;
        if (isAvailableEndpointChanged(oldState, newState)) {
            notifyAvailableCallEndpointsChange();
            isforce = false;
        }

        if (isEndpointChanged(oldState, newState)) {
            notifyCallEndpointChange();
            isforce = false;
        }

        if (isMuteStateChanged(oldState, newState)) {
            notifyMuteStateChange(newState.isMuted());
            isforce = false;
        }

        if (isforce) {
            notifyAvailableCallEndpointsChange();
            notifyCallEndpointChange();
            notifyMuteStateChange(newState.isMuted());
        }
    }
}