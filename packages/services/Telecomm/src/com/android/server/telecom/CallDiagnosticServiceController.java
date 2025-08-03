/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.telecom;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.BluetoothCallQualityReport;
import android.telecom.CallAudioState;
import android.telecom.CallDiagnosticService;
import android.telecom.ConnectionService;
import android.telecom.CallDiagnostics;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;
import android.telecom.Log;
import android.telecom.ParcelableCall;
import android.telephony.CallQuality;
import android.telephony.ims.ImsReasonInfo;
import android.text.TextUtils;

import com.android.internal.telecom.ICallDiagnosticService;
import com.android.internal.util.IndentingPrintWriter;

import java.util.List;

/**
 * Responsible for maintaining binding to the {@link CallDiagnosticService} defined by the
 * {@code call_diagnostic_service_package_name} key in the
 * {@code packages/services/Telecomm/res/values/config.xml} file.
 */
public class CallDiagnosticServiceController extends CallsManagerListenerBase {
    /**
     * Context dependencies for the {@link CallDiagnosticServiceController}.
     */
    public interface ContextProxy {
        List<ResolveInfo> queryIntentServicesAsUser(@NonNull Intent intent,
                int resolveInfoFlags, @UserIdInt int userId);
        boolean bindServiceAsUser(@NonNull @RequiresPermission Intent service,
                @NonNull ServiceConnection conn, int flags, @NonNull UserHandle user);
        void unbindService(@NonNull ServiceConnection conn);
        UserHandle getCurrentUserHandle();
    }

    /**
     * Listener for {@link Call} events; used to propagate these changes to the
     * {@link CallDiagnosticService}.
     */
    private final Call.Listener mCallListener = new Call.ListenerBase() {
        @Override
        public void onConnectionCapabilitiesChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onConnectionPropertiesChanged(Call call, boolean didRttChange) {
            updateCall(call);
        }

        /**
         * Listens for changes to extras reported by a Telecom {@link Call}.
         *
         * Extras changes can originate from a {@link ConnectionService} or an {@link InCallService}
         * so we will only trigger an update of the call information if the source of the
         * extras change was a {@link ConnectionService}.
         *
         * @param call   The call.
         * @param source The source of the extras change
         *               ({@link Call#SOURCE_CONNECTION_SERVICE} or
         *               {@link Call#SOURCE_INCALL_SERVICE}).
         * @param extras The extras.
         */
        @Override
        public void onExtrasChanged(Call call, int source, Bundle extras,
                String requestingPackageName) {
            // Do not inform of changes which originated from an InCallService to a CDS.
            if (source == Call.SOURCE_INCALL_SERVICE) {
                return;
            }
            updateCall(call);
        }

        /**
         * Listens for changes to extras reported by a Telecom {@link Call}.
         *
         * Extras changes can originate from a {@link ConnectionService} or an {@link InCallService}
         * so we will only trigger an update of the call information if the source of the extras
         * change was a {@link ConnectionService}.
         *  @param call The call.
         * @param source The source of the extras change ({@link Call#SOURCE_CONNECTION_SERVICE} or
         *               {@link Call#SOURCE_INCALL_SERVICE}).
         * @param keys The extra key removed
         */
        @Override
        public void onExtrasRemoved(Call call, int source, List<String> keys) {
            // Do not inform InCallServices of changes which originated there.
            if (source == Call.SOURCE_INCALL_SERVICE) {
                return;
            }
            updateCall(call);
        }

        /**
         * Handles changes to the video state of a call.
         * @param call
         * @param previousVideoState
         * @param newVideoState
         */
        @Override
        public void onVideoStateChanged(Call call, int previousVideoState, int newVideoState) {
            updateCall(call);
        }

        /**
         * Relays a bluetooth call quality report received from the Bluetooth stack to the
         * CallDiagnosticService.
         * @param call The call.
         * @param report The received report.
         */
        @Override
        public void onBluetoothCallQualityReport(Call call, BluetoothCallQualityReport report) {
            handleBluetoothCallQualityReport(call, report);
        }

        /**
         * Relays a device to device message received from Telephony to the CallDiagnosticService.
         * @param call
         * @param messageType
         * @param messageValue
         */
        @Override
        public void onReceivedDeviceToDeviceMessage(Call call, int messageType, int messageValue) {
            handleReceivedDeviceToDeviceMessage(call, messageType, messageValue);
        }

        /**
         * Handles an incoming {@link CallQuality} report from a {@link android.telecom.Connection}.
         * @param call The call.
         * @param callQualityReport The call quality report.
         */
        @Override
        public void onReceivedCallQualityReport(Call call, CallQuality callQualityReport) {
            handleCallQualityReport(call, callQualityReport);
        }
    };

    /**
     * {@link ServiceConnection} handling changes to binding of the {@link CallDiagnosticService}.
     */
    private class CallDiagnosticServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.startSession("CDSC.oSC", Log.getPackageAbbreviation(name));
            try {
                synchronized (mLock) {
                    mCallDiagnosticService = ICallDiagnosticService.Stub.asInterface(service);

                    handleConnectionComplete(mCallDiagnosticService);
                }
                Log.i(CallDiagnosticServiceController.this, "onServiceConnected: cmp=%s", name);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.startSession("CDSC.oSD", Log.getPackageAbbreviation(name));
            try {
                synchronized (mLock) {
                    mCallDiagnosticService = null;
                    mConnection = null;
                }
                Log.i(CallDiagnosticServiceController.this, "onServiceDisconnected: cmp=%s", name);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.startSession("CDSC.oBD", Log.getPackageAbbreviation(name));
            try {
                synchronized (mLock) {
                    mCallDiagnosticService = null;
                    mConnection = null;
                }
                Log.w(CallDiagnosticServiceController.this, "onBindingDied: cmp=%s", name);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.startSession("CDSC.oNB", Log.getPackageAbbreviation(name));
            try {
                synchronized (mLock) {
                    maybeUnbindCallScreeningService();
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private final String mPackageName;
    private final ContextProxy mContextProxy;
    private InCallTonePlayer.Factory mPlayerFactory;
    private String mTestPackageName;
    private CallDiagnosticServiceConnection mConnection;
    private CallDiagnosticServiceAdapter mAdapter;
    private final TelecomSystem.SyncRoot mLock;
    private ICallDiagnosticService mCallDiagnosticService;
    private final CallIdMapper mCallIdMapper = new CallIdMapper(Call::getId);

    public CallDiagnosticServiceController(@NonNull ContextProxy contextProxy,
            @Nullable String packageName, @NonNull TelecomSystem.SyncRoot lock) {
        mContextProxy = contextProxy;
        mPackageName = packageName;
        mLock = lock;
    }

    /**
     * Sets the current {@link InCallTonePlayer.Factory} for this instance.
     * @param factory the factory.
     */
    public void setInCallTonePlayerFactory(InCallTonePlayer.Factory factory) {
        mPlayerFactory = factory;
    }

    /**
     * Handles Telecom adding new calls.  Will bind to the call diagnostic service if needed and
     * send the calls, or send to an already bound service.
     * @param call The call to add.
     */
    @Override
    public void onCallAdded(@NonNull Call call) {
        if (!call.isSimCall() || call.isExternalCall()) {
            Log.i(this, "onCallAdded: skipping call %s as non-sim or external.", call.getId());
            return;
        }
        if (mCallIdMapper.getCallId(call) == null) {
            mCallIdMapper.addCall(call);
            call.addListener(mCallListener);
        }
        if (isConnected()) {
            sendCallToBoundService(call, mCallDiagnosticService);
        } else {
            maybeBindCallDiagnosticService();
        }
    }

    /**
     * Handles a newly disconnected call signalled from {@link CallsManager}.
     * @param call The call
     * @param disconnectCause The disconnect cause
     * @return {@code true} if the {@link CallDiagnosticService} was sent the call, {@code false}
     * if the call was not applicable to the CDS or if there was an issue sending it.
     */
    public boolean onCallDisconnected(@NonNull Call call,
            @NonNull DisconnectCause disconnectCause) {
        if (!call.isSimCall() || call.isExternalCall()) {
            Log.i(this, "onCallDisconnected: skipping call %s as non-sim or external.",
                    call.getId());
            return false;
        }
        String callId = mCallIdMapper.getCallId(call);
        try {
            if (isConnected()) {
                mCallDiagnosticService.notifyCallDisconnected(callId, disconnectCause);
                return true;
            }
        } catch (RemoteException e) {
            Log.w(this, "onCallDisconnected: callId=%s, exception=%s", call.getId(), e);
        }
        return false;
    }

    /**
     * Handles Telecom removal of calls; will remove the call from the bound service and if the
     * number of tracked calls falls to zero, unbind from the service.
     * @param call The call to remove from the bound CDS.
     */
    @Override
    public void onCallRemoved(@NonNull Call call) {
        if (!call.isSimCall() || call.isExternalCall()) {
            Log.i(this, "onCallRemoved: skipping call %s as non-sim or external.", call.getId());
            return;
        }
        mCallIdMapper.removeCall(call);
        call.removeListener(mCallListener);
        removeCallFromBoundService(call, mCallDiagnosticService);

        if (mCallIdMapper.getCalls().size() == 0) {
            maybeUnbindCallScreeningService();
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        updateCall(call);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState oldCallAudioState,
            CallAudioState newCallAudioState) {
        if (mCallDiagnosticService != null) {
            try {
                mCallDiagnosticService.updateCallAudioState(newCallAudioState);
            } catch (RemoteException e) {
                Log.w(this, "onCallAudioStateChanged: failed %s", e);
            }
        }
    }

    /**
     * Sets the test call diagnostic service; used by the telecom command line command to override
     * the {@link CallDiagnosticService} to bind to for CTS test purposes.
     * @param packageName The package name to set to.
     */
    public void setTestCallDiagnosticService(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            mTestPackageName = null;
        } else {
            mTestPackageName = packageName;
        }

        Log.i(this, "setTestCallDiagnosticService: packageName=%s", packageName);
    }

    /**
     * Determines the active call diagnostic service, taking into account the test override.
     * @return The package name of the active call diagnostic service.
     */
    private @Nullable String getActiveCallDiagnosticService() {
        if (mTestPackageName != null) {
            return mTestPackageName;
        }

        return mPackageName;
    }

    /**
     * If we are not already bound to the {@link CallDiagnosticService}, attempts to initiate a
     * binding tho that service.
     * @return {@code true} if we bound, {@code false} otherwise.
     */
    private boolean maybeBindCallDiagnosticService() {
        if (mConnection != null) {
            return false;
        }

        mConnection = new CallDiagnosticServiceConnection();
        boolean bound = bindCallDiagnosticService(mContextProxy.getCurrentUserHandle(),
                getActiveCallDiagnosticService(), mConnection);
        if (!bound) {
            mConnection = null;
        }
        return bound;
    }

    /**
     * Performs binding to the {@link CallDiagnosticService}.
     * @param userHandle user name to bind via.
     * @param packageName package name of the CDS.
     * @param serviceConnection The service connection to be notified of bind events.
     * @return
     */
    private boolean bindCallDiagnosticService(UserHandle userHandle,
            String packageName, CallDiagnosticServiceConnection serviceConnection) {

        if (TextUtils.isEmpty(packageName)) {
            Log.i(this, "bindCallDiagnosticService: no package; skip binding.");
            return false;
        }

        Intent intent = new Intent(CallDiagnosticService.SERVICE_INTERFACE)
                .setPackage(packageName);
        Log.i(this, "bindCallDiagnosticService: user %d.", userHandle.getIdentifier());
        List<ResolveInfo> entries = mContextProxy.queryIntentServicesAsUser(intent, 0,
                userHandle.getIdentifier());
        if (entries.isEmpty()) {
            Log.i(this, "bindCallDiagnosticService: %s has no service.", packageName);
            return false;
        }

        ResolveInfo entry = entries.get(0);
        if (entry.serviceInfo == null) {
            Log.i(this, "bindCallDiagnosticService: %s has no service info.", packageName);
            return false;
        }

        if (entry.serviceInfo.permission == null || !entry.serviceInfo.permission.equals(
                Manifest.permission.BIND_CALL_DIAGNOSTIC_SERVICE)) {
            Log.i(this, "bindCallDiagnosticService: %s doesn't require "
                    + "BIND_CALL_DIAGNOSTIC_SERVICE.", packageName);
            return false;
        }

        ComponentName componentName =
                new ComponentName(entry.serviceInfo.packageName, entry.serviceInfo.name);
        intent.setComponent(componentName);
        if (mContextProxy.bindServiceAsUser(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE,
                UserHandle.CURRENT)) {
            Log.d(this, "bindCallDiagnosticService, found service, waiting for it to connect");
            return true;
        }
        return false;
    }

    /**
     * If we are bound to a {@link CallDiagnosticService}, unbind from it.
     */
    public void maybeUnbindCallScreeningService() {
        if (mConnection != null) {
            Log.i(this, "maybeUnbindCallScreeningService - unbinding from %s",
                    getActiveCallDiagnosticService());
            try {
                mContextProxy.unbindService(mConnection);
                mCallDiagnosticService = null;
                mConnection = null;
            } catch (IllegalArgumentException e) {
                Log.i(this, "maybeUnbindCallScreeningService: Exception when unbind %s : %s",
                        getActiveCallDiagnosticService(), e.getMessage());
            }
        } else {
            Log.w(this, "maybeUnbindCallScreeningService - already unbound");
        }
    }

    /**
     * Implements the abstracted Telecom functionality the {@link CallDiagnosticServiceAdapter}
     * depends on.
     */
    private CallDiagnosticServiceAdapter.TelecomAdapter mTelecomAdapter =
            new CallDiagnosticServiceAdapter.TelecomAdapter() {

        @Override
        public void displayDiagnosticMessage(String callId, int messageId, CharSequence message) {
            handleDisplayDiagnosticMessage(callId, messageId, message);
        }

        @Override
        public void clearDiagnosticMessage(String callId, int messageId) {
            handleClearDiagnosticMessage(callId, messageId);
        }

        @Override
        public void sendDeviceToDeviceMessage(String callId,
                        @CallDiagnostics.MessageType int message, int value) {
            handleSendD2DMessage(callId, message, value);
        }

        @Override
        public void overrideDisconnectMessage(String callId, CharSequence message) {
            handleOverrideDisconnectMessage(callId, message);
        }
    };

    /**
     * Sends all calls to the specified {@link CallDiagnosticService}.
     * @param callDiagnosticService the CDS to send calls to.
     */
    private void handleConnectionComplete(@NonNull ICallDiagnosticService callDiagnosticService) {
        mAdapter = new CallDiagnosticServiceAdapter(mTelecomAdapter,
                getActiveCallDiagnosticService(), mLock);
        try {
            // Add adapter for communication back from the call diagnostic service to Telecom.
            callDiagnosticService.setAdapter(mAdapter);

            // Loop through all the calls we've got ready to send since binding.
            for (Call call : mCallIdMapper.getCalls()) {
                sendCallToBoundService(call, callDiagnosticService);
            }
        } catch (RemoteException e) {
            Log.w(this, "handleConnectionComplete: error=%s", e);
        }
    }

    /**
     * Handles a request from a {@link CallDiagnosticService} to display a diagnostic message.
     * @param callId the ID of the call to display the message for.
     * @param message the message.
     */
    private void handleDisplayDiagnosticMessage(@NonNull String callId, int messageId,
            @Nullable CharSequence message) {
        Call call = mCallIdMapper.getCall(callId);
        if (call == null) {
            Log.w(this, "handleDisplayDiagnosticMessage: callId=%s; msg=%d/%s; invalid call",
                    callId, messageId, message);
            return;
        }
        Log.i(this, "handleDisplayDiagnosticMessage: callId=%s; msg=%d/%s",
                callId, messageId, message);
        if (mPlayerFactory != null) {
            // Play that tone!
            mPlayerFactory.createPlayer(call, InCallTonePlayer.TONE_IN_CALL_QUALITY_NOTIFICATION)
                    .startTone();
        }
        call.displayDiagnosticMessage(messageId, message);
    }

    /**
     * Handles a request from a {@link CallDiagnosticService} to clear a previously displayed
     * diagnostic message.
     * @param callId the ID of the call to display the message for.
     * @param messageId the message ID which was previous posted.
     */
    private void handleClearDiagnosticMessage(@NonNull String callId, int messageId) {
        Call call = mCallIdMapper.getCall(callId);
        if (call == null) {
            Log.w(this, "handleClearDiagnosticMessage: callId=%s; msg=%d; invalid call",
                    callId, messageId);
            return;
        }
        Log.i(this, "handleClearDiagnosticMessage: callId=%s; msg=%d; invalid call",
                callId, messageId);
        call.clearDiagnosticMessage(messageId);
    }

    /**
     * Handles a request from a {@link CallDiagnosticService} to send a device to device message.
     * @param callId The ID of the call to send the D2D message for.
     * @param message The message type.
     * @param value The message value.
     */
    private void handleSendD2DMessage(@NonNull String callId,
            @CallDiagnostics.MessageType int message, int value) {
        Call call = mCallIdMapper.getCall(callId);
        if (call == null) {
            Log.w(this, "handleSendD2DMessage: callId=%s; msg=%d/%d; invalid call", callId,
                    message, value);
            return;
        }
        Log.i(this, "handleSendD2DMessage: callId=%s; msg=%d/%d", callId, message, value);
        call.sendDeviceToDeviceMessage(message, value);
    }

    /**
     * Handles a request from a {@link CallDiagnosticService} to override the disconnect message
     * for a call.  This is the response path from a previous call into the
     * {@link CallDiagnosticService} via {@link CallDiagnostics#onCallDisconnected(ImsReasonInfo)}.
     * @param callId The telecom call ID the disconnect override is pending for.
     * @param message The new disconnect message, or {@code null} if no override.
     */
    private void handleOverrideDisconnectMessage(@NonNull String callId,
            @Nullable CharSequence message) {
        Call call = mCallIdMapper.getCall(callId);
        if (call == null) {
            Log.w(this, "handleOverrideDisconnectMessage: callId=%s; msg=%s; invalid call", callId,
                    message);
            return;
        }
        Log.i(this, "handleOverrideDisconnectMessage: callId=%s; msg=%s", callId, message);
        call.handleOverrideDisconnectMessage(message);
    }

    /**
     * Sends a single call to the bound {@link CallDiagnosticService}.
     * @param call The call to send.
     * @param callDiagnosticService The CDS to send it to.
     */
    private void sendCallToBoundService(@NonNull Call call,
            @NonNull ICallDiagnosticService callDiagnosticService) {
        try {
            if (isConnected()) {
                Log.w(this, "sendCallToBoundService: initializing %s", call.getId());
                callDiagnosticService.initializeDiagnosticCall(getParceledCall(call));
            } else {
                Log.w(this, "sendCallToBoundService: not bound, skipping %s", call.getId());
            }
        } catch (RemoteException e) {
            Log.w(this, "sendCallToBoundService: callId=%s, exception=%s", call.getId(), e);
        }
    }

    /**
     * Removes a call from a bound {@link CallDiagnosticService}.
     * @param call The call to remove.
     * @param callDiagnosticService The CDS to remove it from.
     */
    private void removeCallFromBoundService(@NonNull Call call,
            @NonNull ICallDiagnosticService callDiagnosticService) {
        try {
            if (isConnected()) {
                callDiagnosticService.removeDiagnosticCall(call.getId());
            }
        } catch (RemoteException e) {
            Log.w(this, "removeCallFromBoundService: callId=%s, exception=%s", call.getId(), e);
        }
    }

    /**
     * @return {@code true} if the call diagnostic service is bound/connected.
     */
    public boolean isConnected() {
        return mCallDiagnosticService != null;
    }

    /**
     * Updates the Call diagnostic service with changes to a call.
     * @param call The updated call.
     */
    private void updateCall(@NonNull Call call) {
        try {
            if (isConnected()) {
                mCallDiagnosticService.updateCall(getParceledCall(call));
            }
        } catch (RemoteException e) {
            Log.w(this, "updateCall: callId=%s, exception=%s", call.getId(), e);
        }
    }

    /**
     * Updates the call diagnostic service with a received bluetooth quality report.
     * @param call The call.
     * @param report The bluetooth call quality report.
     */
    private void handleBluetoothCallQualityReport(@NonNull Call call,
            @NonNull BluetoothCallQualityReport report) {
        try {
            if (isConnected()) {
                mCallDiagnosticService.receiveBluetoothCallQualityReport(report);
            }
        } catch (RemoteException e) {
            Log.w(this, "handleBluetoothCallQualityReport: callId=%s, exception=%s", call.getId(),
                    e);
        }
    }

    /**
     * Informs a CallDiagnosticService of an incoming device to device message which was received
     * via the carrier network.
     * @param call the call the message was received via.
     * @param messageType The message type.
     * @param messageValue The message value.
     */
    private void handleReceivedDeviceToDeviceMessage(@NonNull Call call, int messageType,
            int messageValue) {
        try {
            if (isConnected()) {
                mCallDiagnosticService.receiveDeviceToDeviceMessage(call.getId(), messageType,
                        messageValue);
            }
        } catch (RemoteException e) {
            Log.w(this, "handleReceivedDeviceToDeviceMessage: callId=%s, exception=%s",
                    call.getId(), e);
        }
    }

    /**
     * Handles a reported {@link CallQuality} report from a {@link android.telecom.Connection}.
     * @param call The call the report originated from.
     * @param callQualityReport The {@link CallQuality} report.
     */
    private void handleCallQualityReport(@NonNull Call call,
            @NonNull CallQuality callQualityReport) {
        try {
            if (isConnected()) {
                mCallDiagnosticService.callQualityChanged(call.getId(), callQualityReport);
            }
        } catch (RemoteException e) {
            Log.w(this, "handleCallQualityReport: callId=%s, exception=%s",
                    call.getId(), e);
        }
    }

    /**
     * Get a parcelled representation of a call for transport to the service.
     * @param call The call.
     * @return The parcelled call.
     */
    private @NonNull ParcelableCall getParceledCall(@NonNull Call call) {
        return ParcelableCallUtils.toParcelableCall(
                call,
                false /* includeVideoProvider */,
                null /* phoneAcctRegistrar */,
                false /* supportsExternalCalls */,
                false /* includeRttCall */,
                false /* isForSystemDialer */
        );
    }

    /**
     * Dumps the state of the {@link CallDiagnosticServiceController}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.print("activeCallDiagnosticService: ");
        pw.println(getActiveCallDiagnosticService());
        pw.print("isConnected: ");
        pw.println(isConnected());
    }
}
