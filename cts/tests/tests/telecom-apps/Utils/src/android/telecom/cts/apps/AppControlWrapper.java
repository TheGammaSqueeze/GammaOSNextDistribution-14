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

package android.telecom.cts.apps;

import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.fail;

import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import java.util.List;

public class AppControlWrapper {
    private static final String TAG = AppControlWrapper.class.getSimpleName();
    private static final String CLASS = AppControlWrapper.class.getCanonicalName();
    private final IAppControl mBinder;
    private final TelecomTestApp mTelecomApps;
    private final boolean mIsManagedAppControl;

    public TelecomTestApp getTelecomApps() {
        return mTelecomApps;
    }

    public boolean isManagedAppControl() {
        return mIsManagedAppControl;
    }

    public boolean isTransactionalControl() {
        return mTelecomApps.equals(TransactionalVoipAppClone)
                || mTelecomApps.equals(TransactionalVoipAppMain);
    }

    public AppControlWrapper(IAppControl binder, TelecomTestApp name) {
        mBinder = binder;
        mTelecomApps = name;
        if (mTelecomApps.equals(TelecomTestApp.ManagedConnectionServiceApp)) {
            mIsManagedAppControl = true;
        } else {
            mIsManagedAppControl = false;
        }
    }

    /**
     * This method helps determine if the application attached to this wrapper is currently bound
     * to. Typically this can help to determine if the test process needs to wait longer before
     * calling binder methods.
     *
     * @return true if the telecom app is bound to at this current time. otherwise returns false.
     */
    public boolean isBound() {
        Log.i(TAG, "isBound");
        try {
            return mBinder.isBound();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get isBound", e);
        }
        return false;
    }

    public UserHandle getProcessUserHandle() {
        Log.i(TAG, "getProcessUserHandle");
        try {
            return mBinder.getProcessUserHandle();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get getProcessUserHandle", e);
        }
        return null;
    }


    public int getProcessUid() {
        Log.i(TAG, "getProcessUserHandle");
        try {
            return mBinder.getProcessUid();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to getProcessUid", e);
        }
        return -1;
    }

    /**
     * This method requests the app that is bound to add a new call with the given callAttributes.
     * Note: This method does not verify the call is added for ConnectionService implementations
     * and that job should be left for the InCallService to verify.
     */
    public void addCall(CallAttributes callAttributes) throws Exception {
        Log.i(TAG, "addCall");
        try {
            NoDataTransaction transactionResult = mBinder.addCall(callAttributes);
            maybeFailTest(transactionResult);
        } catch (RemoteException re) {
            handleRemoteException(re, "addCall");
        }
    }

    /**
     * This method requests the app that is bound to transition the call state to newCallState
     *
     * @param expectSuccess is used for transactional applications only so that both the success and
     *                      fail cases can be tested.
     * @param extras        contains videoState and disconnectCause info.
     */
    public CallException setCallState(String id,
            int newCallState,
            boolean expectSuccess,
            Bundle extras) throws Exception {
        Log.i(TAG, "setCallState");
        try {
            CallExceptionTransaction transactionResult =
                    mBinder.transitionCallStateTo(id, newCallState, expectSuccess, extras);
            maybeFailTest(transactionResult);
            return transactionResult.getCallException();
        } catch (RemoteException e) {
            handleRemoteException(e, "setCallState");
        }
        return null;
    }

    /**
     * Waits for the current [CallEndpoint] to be non-null before returning. Otherwise, the
     * application will throw an error.
     */
    public CallEndpoint getCurrentCallEndpoint(String id) throws Exception {
        Log.i(TAG, "getCurrentCallEndpoint");
        CallEndpointTransaction transactionResult = null;
        try {
            transactionResult = mBinder.getCurrentCallEndpoint(id);
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
            handleRemoteException(e, "getCurrentCallEndpoint");
        }
        return transactionResult.getCallEndpoint();
    }

    /**
     * Waits for the available [CallEndpoint]s to be non-null before returning. Otherwise, the
     * application will throw an error.
     */
    public List<CallEndpoint> getAvailableCallEndpoints(String id) throws Exception {
        Log.i(TAG, "getAvailableCallEndpoints");
        AvailableEndpointsTransaction transactionResult = null;
        try {
            transactionResult = mBinder.getAvailableCallEndpoints(id);
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
            handleRemoteException(e, "getAvailableCallEndpoints");
        }
        return transactionResult.getCallEndpoint();
    }

    /**
     * switches the current [CallEndpoint]
     */
    public void setAudioRouteStateAndVerify(String id, CallEndpoint newCallEndpoint)
            throws RemoteException {
        Log.i(TAG, "setAudioRouteStateAndVerify");
        try {
            NoDataTransaction transactionResult =
                    mBinder.requestCallEndpointChange(id, newCallEndpoint);
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
            handleRemoteException(e, "setAudioRouteStateAndVerify");
        }
    }

    /**
     * Gets the current mute value of the application. This is tracked locally and updated every
     * time the audio state changes.
     */
    public boolean isMuted(String id) throws RemoteException {
        Log.i(TAG, "isMuted");
        try {
            BooleanTransaction transactionResult = mBinder.isMuted(id);
            maybeFailTest(transactionResult);
            return transactionResult.getBoolResult();
        } catch (RemoteException e) {
            handleRemoteException(e, "isMuted");
        }
        return false;
    }

    /**
     * Sets the mute state
     */
    public void setMuteState(String id, boolean isMuted) throws RemoteException {
        Log.i(TAG, "setMuteState");
        try {
            NoDataTransaction transactionResult = mBinder.setMuteState(id, isMuted);
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
            handleRemoteException(e, "setMuteState");
        }
    }

    /**
     * Registers the default account that is defined in the application.info class that corresponds
     * to the implementation class
     */
    public void registerDefaultPhoneAccount() throws RemoteException {
        Log.i(TAG, "registerDefaultPhoneAccount");
        try {
            NoDataTransaction transactionResult = mBinder.registerDefaultPhoneAccount();
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
            handleRemoteException(e, "registerDefaultPhoneAccount");
        }
    }

    /**
     * Gets the default account that is defined in the application.info class that corresponds
     * to the implementation class.
     */
    public PhoneAccount getDefaultPhoneAccount() throws RemoteException {
        Log.i(TAG, "getDefaultPhoneAccount");
        try {
            PhoneAccountTransaction transactionResult = mBinder.getDefaultPhoneAccount();
            maybeFailTest(transactionResult);
            return transactionResult.getPhoneAccount();
        } catch (RemoteException e) {
            handleRemoteException(e, "getDefaultPhoneAccount");
        }
        return null;
    }

    /**
     * Registers a custom account that is usually defined at the test class level.
     */
    public void registerCustomPhoneAccount(PhoneAccount account) throws RemoteException {
        Log.i(TAG, "registerCustomPhoneAccount");
        try {
            mBinder.registerCustomPhoneAccount(account);
        } catch (RemoteException e) {
            handleRemoteException(e, "registerCustomPhoneAccount");
        }
    }

    /**
     * Unregisters a given account from the client side
     */
    public void unregisterPhoneAccountWithHandle(PhoneAccountHandle handle) throws RemoteException {
        Log.i(TAG, "unregisterPhoneAccountWithHandle");
        try {
            mBinder.unregisterPhoneAccountWithHandle(handle);
        } catch (RemoteException e) {
            handleRemoteException(e, "unregisterPhoneAccountWithHandle");
        }
    }

    /**
     * Gets all the SELF_MANAGED accounts that are retrievable to the client! Helpful to see what
     * accounts the client is unable to fetch.
     */
    public List<PhoneAccountHandle> getAccountHandlesForApp() throws RemoteException {
        Log.i(TAG, "getAccountHandlesForApp");
        try {
            return mBinder.getOwnAccountHandlesForApp();
        } catch (RemoteException e) {
            handleRemoteException(e, "getAccountHandlesForApp");
        }
        return null;
    }

    /**
     * Fetch all the PhoneAccounts associated with the application.
     */
    public List<PhoneAccount> getRegisteredPhoneAccounts() throws RemoteException {
        Log.i(TAG, "getRegisteredPhoneAccounts");
        try {
            return mBinder.getRegisteredPhoneAccounts();
        } catch (RemoteException e) {
            handleRemoteException(e, "getRegisteredPhoneAccounts");
        }
        return null;
    }

    private void handleRemoteException(RemoteException e, String callingMethod)
            throws RemoteException {
        if (e.getClass().equals(DeadObjectException.class)) {
            fail(CLASS + "." + callingMethod + " threw a DeadObjectException meaning that "
                    + "Process=[" + mTelecomApps + "] died while processing the " + callingMethod
                    + " binder transaction. Look at earlier logs to determine what caused the test"
                    + "app to crash.");
        } else {
            throw e;
        }
    }

    private void maybeFailTest(BaseTransaction transactionResult) {
        if (transactionResult != null
                && transactionResult.getResult().equals(TestAppTransaction.Failure)) {
            fail(transactionResult.getTestAppException().getMessage());
        }
    }

    public boolean isNotificationPostedForCall(String callId) throws RemoteException {
        Log.i(TAG, "isNotificationPostedForCall");
        try {
            BooleanTransaction transactionResult = mBinder.isNotificationPostedForCall(callId);
            maybeFailTest(transactionResult);
            return transactionResult.getBoolResult();
        } catch (RemoteException e) {
           handleRemoteException(e, "isNotificationPostedForCall");
        }
        return false;
    }

    public void removeNotificationForCall(String callId) throws RemoteException {
        Log.i(TAG, "removeNotificationForCall");
        try {
            NoDataTransaction transactionResult = mBinder.removeNotificationForCall(callId);
            maybeFailTest(transactionResult);
        } catch (RemoteException e) {
           handleRemoteException(e, "removeNotificationForCall");
        }
    }
}
