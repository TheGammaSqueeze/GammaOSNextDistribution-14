/*
 * ANT Android Host Stack
 *
 * Copyright 2009-2018 Dynastream Innovations
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

package com.dsi.ant.server;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Service;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import com.dsi.ant.hidl.HidlClient;
import com.dsi.ant.server.AntHalDefine;
import com.dsi.ant.server.IAntHal;
import com.dsi.ant.server.IAntHalCallback;
import com.dsi.ant.server.Version;

import java.util.HashMap;

public class AntService extends Service
{
    private static final String TAG = "AntHalService";

    private static final boolean DEBUG = false;

    private static final boolean HAS_MULTI_USER_API =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;

    public static final String ANT_SERVICE = "AntService";

    /**
     * Allows the application to directly configure the ANT radio through the
     * proxy service. Malicious applications may prevent other ANT applications
     * from connecting to ANT devices
     */
    public static final String ANT_ADMIN_PERMISSION = "com.dsi.ant.permission.ANT_ADMIN";

    /**
     * Request that ANT be enabled
     */
    public static final String ACTION_REQUEST_ENABLE = "com.dsi.ant.server.action.REQUEST_ENABLE";

    /**
     * Request that ANT be disabled
     */
    public static final String ACTION_REQUEST_DISABLE = "com.dsi.ant.server.action.REQUEST_DISABLE";

    private HidlClient mAnt = null;

    private boolean mInitialized = false;

    /**
     * Flag for whether usage by background users is allowed. Configured in res/values/config.xml
     */
    private boolean mAllowBackgroundUsage = false;

    /**
     * Flag for if Bluetooth needs to be enabled for ANT to enable. Configured in res/values/config.xml
     */
    private boolean mRequiresBluetoothOn = false;

    /**
     * Flag which specifies if we are waiting for an ANT enable intent
     */
    private boolean mEnablePending = false;

    private Object mChangeAntPowerState_LOCK = new Object();
    private static Object sAntHalServiceDestroy_LOCK = new Object();

    /** Callback object for sending events to the upper layers */
    private volatile IAntHalCallback mCallback;
    /**
     * Used for synchronizing changes to {@link #mCallback}, {@link #mCallbackMap}, and
     * {@link #mCurrentUser}. Does not need to be used where a one-time read of the
     * {@link #mCallback} value is being done, however ALL WRITE ACCESSES must use this lock.
     */
    private final Object mUserCallback_LOCK = new Object();

    /**
     * The user handle associated with the current active user of the ANT HAL service.
     */
    private volatile UserHandle mCurrentUser;

    /**
     * Map containing the callback set for each current user.
     */
    private final HashMap<UserHandle, IAntHalCallback> mCallbackMap =
            new HashMap<UserHandle, IAntHalCallback>();

    /**
     * Receives Bluetooth State Changed intent and sends {@link ACTION_REQUEST_ENABLE}
     * and {@link ACTION_REQUEST_DISABLE} accordingly
     */
    private final StateChangedReceiver mStateChangedReceiver = new StateChangedReceiver();

    /**
     * Receives {@link ACTION_REQUEST_ENABLE} and {@link ACTION_REQUEST_DISABLE}
     * intents to enable and disable ANT.
     * Also receives {@link Intent#ACTION_USER_SWITCHED} when we are not allowing background users
     * in order to clear the current user at the appropriate time.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mRequiresBluetoothOn) {
                if (ACTION_REQUEST_ENABLE.equals(action)) {
                    if (mEnablePending) {
                        asyncSetAntPowerState(true);
                        mEnablePending = false;
                    }
                } else if (ACTION_REQUEST_DISABLE.equals(action)) {
                    if (mEnablePending) {
                        mEnablePending = false;
                    } else {
                        asyncSetAntPowerState(false);
                    }
                }
            }
            if(!mAllowBackgroundUsage)
            {
                if(HAS_MULTI_USER_API &&
                        Intent.ACTION_USER_SWITCHED.equals(action))
                {
                    clearCurrentUser();
                }
            }
        }
    };

    /**
     * Calls back the registered callback with the change to the new state
     * @param state the {@link AntHalDefine} state
     */
    private void setState(int state)
    {
        synchronized(mChangeAntPowerState_LOCK) {
            if(DEBUG) Log.i(TAG, "Setting ANT State = "+ state +" / "+ AntHalDefine.getAntHalStateString(state));

            // Use caching instead of synchronization so that we do not have to hold a lock during a callback.
            // It is safe to not hold the lock because we are not doing any write accesses.
            IAntHalCallback callback = mCallback;
            if (callback != null)
            {
                try
                {
                    if(DEBUG) Log.d(TAG, "Calling status changed callback "+ callback.toString());

                    callback.antHalStateChanged(state);
                }
                catch (RemoteException e)
                {
                    // Don't do anything as this is a problem in the application

                    if(DEBUG) Log.e(TAG, "ANT HAL State Changed callback failure in application", e);
                }
            }
            else
            {
                if(DEBUG) Log.d(TAG, "Calling status changed callback is null");
            }
        }
    }

    /**
     * Clear the current user, telling the associated ARS instance that the chip is disabled.
     */
    private void clearCurrentUser()
    {
        if (DEBUG) Log.i(TAG, "Clearing active user");
        synchronized (mUserCallback_LOCK)
        {
            setState(AntHalDefine.ANT_HAL_STATE_DISABLED);
            mCurrentUser = null;
            mCallback = null;
            doSetAntState(AntHalDefine.ANT_HAL_STATE_DISABLED);
        }
    }

    /**
     * Attempt to change the current user to the calling user.
     * @return True if the calling user is now the current user (even if they were before the call
     *         was made), False otherwise.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private boolean trySwitchToCallingUser()
    {
        // Lock held here to avoid ordering issues if it is needed within the function.
        synchronized (mChangeAntPowerState_LOCK)
        {
            synchronized (mUserCallback_LOCK)
            {
                UserHandle callingUser = Binder.getCallingUserHandle();
                if(DEBUG) Log.d(TAG, "Trying to make user: " + callingUser + " the current user.");
                boolean isActiveUser = false;
                boolean shouldSwitch = false;
                long id = 0;

                // Always allow if they already are the current user.
                if(callingUser.equals(mCurrentUser))
                {
                    shouldSwitch = true;
                }

                try
                {
                    // Check foreground user using ANT HAL Service permissions.
                    id = Binder.clearCallingIdentity();
                    UserHandle activeUser =
                            ActivityManager.getService().getCurrentUser().getUserHandle();
                    isActiveUser = activeUser.equals(callingUser);
                } catch (RemoteException e)
                {
                    if(DEBUG) Log.w(TAG, "Could not determine the foreground user.");
                    // don't know who the current user is, assume they are not the active user and
                    // continue.
                } finally
                {
                    // always restore our identity.
                    Binder.restoreCallingIdentity(id);
                }

                if(isActiveUser)
                {
                    // Always allow the active user to become the current user.
                    shouldSwitch = true;
                }

                if(mAllowBackgroundUsage)
                {
                    // Allow anyone to become the current user if there is no current user.
                    if(mCurrentUser == null)
                    {
                        shouldSwitch = true;
                    }
                }

                if(shouldSwitch)
                {
                    // Only actually do the switch if the users are different.
                    if(!callingUser.equals(mCurrentUser))
                    {
                        if (DEBUG) Log.i(TAG, "Making " + callingUser + " the current user.");
                        // Need to send state updates as the current user switches.
                        // The mChangeAntPowerState_LOCK needs to be held across these calls to
                        // prevent state updates during the user switch. It is held for this entire
                        // function to prevent lock ordering issues.
                        setState(AntHalDefine.ANT_HAL_STATE_DISABLED);
                        mCurrentUser = callingUser;
                        mCallback = mCallbackMap.get(callingUser);
                        setState(doGetAntState(true));
                    } else
                    {
                        if (DEBUG) Log.d(TAG, callingUser + " is already the current user.");
                    }
                } else
                {
                    if (DEBUG) Log.d(TAG, callingUser + " is not allowed to become the current user.");
                }

                return shouldSwitch;
            }
        }
    }

    /**
     * Requests to change the state
     * @param state The desired state to change to
     * @return An {@link AntHalDefine} result
     */
    @SuppressLint("NewApi")
    private int doSetAntState(int state)
    {
        synchronized(mChangeAntPowerState_LOCK) {
            int result = AntHalDefine.ANT_HAL_RESULT_FAIL_INVALID_REQUEST;

            switch(state)
            {
                case AntHalDefine.ANT_HAL_STATE_ENABLED:
                {
                    result = AntHalDefine.ANT_HAL_RESULT_FAIL_NOT_ENABLED;

                    // On platforms with multiple users the enable call is where we try to switch
                    // the current user.
                    if(HAS_MULTI_USER_API)
                    {
                        if(!trySwitchToCallingUser())
                        {
                            // If we cannot become the current user, fail the enable call.
                            break;
                        }
                    }

                    boolean waitForBluetoothToEnable = false;

                    if (mRequiresBluetoothOn) {

                        // Try to turn on BT if it is not enabled.
                        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                        if (bluetoothAdapter != null) {

                            // run with permissions of ANTHALService
                            long callingPid = Binder.clearCallingIdentity();

                            if (!bluetoothAdapter.isEnabled()) {

                                waitForBluetoothToEnable = true;
                                mEnablePending = true;

                                boolean isEnabling = bluetoothAdapter.enable();

                                // if enabling adapter has begun, return
                                // success.
                                if (isEnabling) {
                                    result = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                                    // StateChangedReceiver will receive
                                    // enabled status and then enable ANT
                                } else {
                                    mEnablePending = false;
                                }
                            }

                            Binder.restoreCallingIdentity(callingPid);
                        }
                    }

                    if (!waitForBluetoothToEnable) {
                        result = asyncSetAntPowerState(true);
                    }
                    break;
                }
                case AntHalDefine.ANT_HAL_STATE_DISABLED:
                {
                    if(HAS_MULTI_USER_API)
                    {
                        UserHandle user = Binder.getCallingUserHandle();
                        if(!user.equals(mCurrentUser))
                        {
                            // All disables succeed for non current users.
                            result = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                            break;
                        }

                        result = asyncSetAntPowerState(false);

                        if(result == AntHalDefine.ANT_HAL_RESULT_SUCCESS &&
                                user.equals(mCurrentUser))
                        {
                            // To match setting the current user in enable.
                            clearCurrentUser();
                        }
                    } else
                    {
                        result = asyncSetAntPowerState(false);
                    }
                    break;
                }
                case AntHalDefine.ANT_HAL_STATE_RESET:
                {
                    result = doHardReset();
                    break;
                }
            }

            return result;
        }
    }

    /**
     * Queries the native code for state
     * @return An {@link AntHalDefine} state
     */
    @SuppressLint("NewApi")
    private int doGetAntState(boolean internalCall)
    {
        if(DEBUG) Log.v(TAG, "doGetAntState start");

        int retState = AntHalDefine.ANT_HAL_STATE_DISABLED;
        if(!HAS_MULTI_USER_API || // If there is no multi-user api we don't have to fake a disabled state.
                internalCall ||
                Binder.getCallingUserHandle().equals(mCurrentUser))
        {
            retState = mAnt.getRadioEnabledStatus();
        }

        if(DEBUG) Log.i(TAG, "Get ANT State = "+ retState +" / "+ AntHalDefine.getAntHalStateString(retState));

        return retState;
    }

    /**
     * Perform a power change if required.
     * @param state true for enable, false for disable
     * @return {@link AntHalDefine#ANT_HAL_RESULT_SUCCESS} when the request has
     * been posted, false otherwise
     */
    private int asyncSetAntPowerState(final boolean state)
    {
        int result = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;

        synchronized (mChangeAntPowerState_LOCK) {
            // Check we are not already in/transitioning to the state we want
            int currentState = doGetAntState(true);

            if (state) {
                if ((AntHalDefine.ANT_HAL_STATE_ENABLED == currentState)
                        || (AntHalDefine.ANT_HAL_STATE_ENABLING == currentState)) {
                    if (DEBUG) {
                        Log.d(TAG, "Enable request ignored as already enabled/enabling");
                    }

                    return AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                } else if (AntHalDefine.ANT_HAL_STATE_DISABLING == currentState) {
                    Log.w(TAG, "Enable request ignored as already disabling");

                    return AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;
                }
            } else {
                if ((AntHalDefine.ANT_HAL_STATE_DISABLED == currentState)
                        || (AntHalDefine.ANT_HAL_STATE_DISABLING == currentState)) {
                    if (DEBUG) {
                        Log.d(TAG, "Disable request ignored as already disabled/disabling");
                    }

                    return AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                } else if (AntHalDefine.ANT_HAL_STATE_ENABLING == currentState) {
                    Log.w(TAG, "Disable request ignored as already enabling");

                    return AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;
                }
            }

            if (state) {
                result = enableBackground();
            } else {
                result = disableBackground();
            }
        }

        return result;
    }

    /**
     * Calls enable on the native libantradio.so
     * @return {@link AntHalDefine#ANT_HAL_RESULT_SUCCESS} when successful, or
     * {@link AntHalDefine#ANT_HAL_RESULT_FAIL_UNKNOWN} if unsuccessful
     */
    private int enableBlocking()
    {
        int ret = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;
        synchronized(sAntHalServiceDestroy_LOCK)
        {
            if (mAnt != null)
            {
                if(mAnt.enable())
                {
                    if(DEBUG) Log.v(TAG, "Enable call: Success");
                    ret = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                }
                else
                {
                    if(DEBUG) Log.v(TAG, "Enable call: Failure");
                }
            }
        }
        return ret;
    }

    /**
     * Calls disable on the native libantradio.so
     * @return {@link AntHalDefine#ANT_HAL_RESULT_SUCCESS} when successful, or
     * {@link AntHalDefine#ANT_HAL_RESULT_FAIL_UNKNOWN} if unsuccessful
     */
    private int disableBlocking()
    {
        int ret = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;
        synchronized(sAntHalServiceDestroy_LOCK)
        {
            if (mAnt != null)
            {
                mAnt.disable();
                if(DEBUG) Log.v(TAG, "Disable callback end: Success");
                ret = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
            }
        }
        return ret;
    }

    /**
     * Post an enable runnable.
     */
    private int enableBackground()
    {
        if(DEBUG) Log.v(TAG, "Enable start");

        if (DEBUG) Log.d(TAG, "Enable: enabling the radio");

        // TODO use handler to post runnable rather than creating a new thread.
        new Thread(new Runnable() {
            public void run() {
                enableBlocking();
            }
        }).start();

        if(DEBUG) Log.v(TAG, "Enable call end: Successfully called");
        return AntHalDefine.ANT_HAL_RESULT_SUCCESS;
    }

    /**
     * Post a disable runnable.
     */
    private int disableBackground()
    {
        if(DEBUG) Log.v(TAG, "Disable start");

        // TODO use handler to post runnable rather than creating a new thread.
        new Thread(new Runnable() {
            public void run() {
                disableBlocking();
            }
        }).start();

        if(DEBUG) Log.v(TAG, "Disable call end: Success");
        return AntHalDefine.ANT_HAL_RESULT_SUCCESS;
    }

    private int doANTTxMessage(byte[] message)
    {
        if(DEBUG) Log.v(TAG, "ANT Tx Message start");

        if(message == null)
        {
            Log.e(TAG, "ANTTxMessage invalid message: message is null");
            return AntHalDefine.ANT_HAL_RESULT_FAIL_INVALID_REQUEST;
        }

        int result = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;

        if (mAnt.ANTTxMessage(message))
        {
            if (DEBUG) Log.d (TAG, "mJAnt.ANTTxMessage returned success.");

            result = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
        }
        else
        {
            if (DEBUG) Log.w( TAG, "mJAnt.ANTTxMessage returned failure.");
        }

        if (DEBUG) Log.v(TAG, "ANTTxMessage: Result = "+ result);

        if(DEBUG) Log.v(TAG, "ANT Tx Message end");

        return result;
    }

    @SuppressLint("NewApi")
    private int doRegisterAntHalCallback(IAntHalCallback callback)
    {
        synchronized (mUserCallback_LOCK)
        {
            if(HAS_MULTI_USER_API)
            {
                UserHandle user = Binder.getCallingUserHandle();
                if(DEBUG) Log.i(TAG, "Registering callback: "+ callback + " for user: " + user);
                mCallbackMap.put(user, callback);
                if(user.equals(mCurrentUser))
                {
                    mCallback = callback;
                }
            } else
            {
                if(DEBUG) Log.i(TAG, "Registering callback: "+ callback);
                mCallback = callback;
            }
        }

        return AntHalDefine.ANT_HAL_RESULT_SUCCESS;
    }

    @SuppressLint("NewApi")
    private int doUnregisterAntHalCallback(IAntHalCallback callback)
    {
        int result = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;

        if(HAS_MULTI_USER_API)
        {
            UserHandle user = Binder.getCallingUserHandle();
            if(DEBUG) Log.i(TAG, "Unregistering callback: "+ callback.toString() + " for user: " +
                    user);
            synchronized(mUserCallback_LOCK)
            {
                IAntHalCallback currentCallback = mCallbackMap.get(user);
                if(callback != null && currentCallback != null &&
                        callback.asBinder().equals(currentCallback.asBinder()))
                {
                    mCallbackMap.remove(user);
                    result = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                }
                // Regardless of state, if the current user is leaving we need to allow others to
                // take over.
                if(user.equals(mCurrentUser))
                {
                    clearCurrentUser();
                }
            }
        } else
        {
            if(DEBUG) Log.i(TAG, "Unregistering callback: "+ callback.toString());
            synchronized(mUserCallback_LOCK)
            {
                if(callback != null && mCallback != null &&
                        callback.asBinder().equals(mCallback.asBinder()))
                {
                    mCallback = null;
                    result = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                }
            }
        }
        return result;
    }

    private int doGetServiceLibraryVersionCode()
    {
        return Version.ANT_HAL_LIBRARY_VERSION_CODE;
    }

    private String doGetServiceLibraryVersionName()
    {
        return Version.ANT_HAL_LIBRARY_VERSION_NAME;
    }

    private int doHardReset()
    {
        int ret = AntHalDefine.ANT_HAL_RESULT_FAIL_UNKNOWN;
        synchronized(sAntHalServiceDestroy_LOCK)
        {
            if (mAnt != null)
            {
                if(mAnt.hardReset())
                {
                    if(DEBUG) Log.v(TAG, "Hard Reset end: Success");
                    ret = AntHalDefine.ANT_HAL_RESULT_SUCCESS;
                }
                else
                {
                    if (DEBUG) Log.v(TAG, "Hard Reset end: Failure");
                }
            }
        }
        return ret;
    }

    // ----------------------------------------------------------------------------------------- IAntHal

    private final IAntHal.Stub mHalBinder = new IAntHal.Stub()
    {
        public int setAntState(int state)
        {
            return doSetAntState(state);
        }

        public int getAntState()
        {
            return doGetAntState(false);
        }

        public int ANTTxMessage(byte[] message)
        {
            return doANTTxMessage(message);
        }

        // Call these in onServiceConnected and when unbinding
        public int registerAntHalCallback(IAntHalCallback callback)
        {
            return doRegisterAntHalCallback(callback);
        }

        public int unregisterAntHalCallback(IAntHalCallback callback)
        {
            return doUnregisterAntHalCallback(callback);
        }

        public int getServiceLibraryVersionCode()
        {
            return doGetServiceLibraryVersionCode();
        }

        public String getServiceLibraryVersionName()
        {
            return doGetServiceLibraryVersionName();
        }
    }; // new IAntHal.Stub()

    // -------------------------------------------------------------------------------------- Service

    @Override
    public void onCreate()
    {
        if (DEBUG) Log.d(TAG, "onCreate() entered");

        super.onCreate();

        if(null != mAnt)
        {
            // This somehow happens when quickly starting/stopping an application.
            if (DEBUG) Log.e(TAG, "LAST JAnt HCI Interface object not destroyed");
        }
        // create a single new JAnt HCI Interface instance
        mAnt = new HidlClient();
        mAllowBackgroundUsage = getResources().getBoolean(R.bool.allow_background_usage);
        mRequiresBluetoothOn = getResources().getBoolean(R.bool.requires_bluetooth_on);
        mAnt.create(mAntCallback);
        mInitialized = true;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REQUEST_ENABLE);
        filter.addAction(ACTION_REQUEST_DISABLE);
        if(HAS_MULTI_USER_API)
        {
            if(!mAllowBackgroundUsage)
            {
                // If we don't allow background users, we need to monitor user switches to clear the
                // active user.
                filter.addAction(Intent.ACTION_USER_SWITCHED);
            }
        }
        registerReceiver(mReceiver, filter);

        if (mRequiresBluetoothOn) {
            IntentFilter stateChangedFilter = new IntentFilter();
            stateChangedFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mStateChangedReceiver, stateChangedFilter);
        }
    }

    @Override
    public void onDestroy()
    {
        if (DEBUG) Log.d(TAG, "onDestroy() entered");

        try
        {
            synchronized(sAntHalServiceDestroy_LOCK)
            {
                if(null != mAnt)
                {
                    int result = disableBlocking();
                    if (DEBUG) Log.d(TAG, "onDestroy: disable result is: " + AntHalDefine.getAntHalResultString(result));

                    mAnt.destroy();
                    mAnt = null;
                }
            }

            synchronized(mUserCallback_LOCK)
            {
                mCallbackMap.clear();
                mCallback = null;
            }
        }
        finally
        {
            super.onDestroy();
        }

        if (mRequiresBluetoothOn) {
            unregisterReceiver(mStateChangedReceiver);
        }

        unregisterReceiver(mReceiver);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        if (DEBUG) Log.d(TAG, "onBind() entered");

        IBinder binder = null;

        if (mInitialized)
        {
            if(intent.getAction().equals(IAntHal.class.getName()))
            {
                if (DEBUG) Log.i(TAG, "Bind: IAntHal");

                binder = mHalBinder;
            }
        }

        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        if (DEBUG) Log.d(TAG, "onUnbind() entered");

        synchronized(mUserCallback_LOCK)
        {
            mCallback = null;
            mCallbackMap.clear();
        }

        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (DEBUG) Log.d(TAG, "onStartCommand() entered");

        if (!mInitialized)
        {
            if (DEBUG) Log.e(TAG, "not initialized, stopping self");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    // ----------------------------------------------------------------------------------------- JAnt Callbacks

    private HidlClient.ICallback mAntCallback = new HidlClient.ICallback()
    {
        public synchronized void ANTRxMessage( byte[] message)
        {
            // Use caching instead of synchronization so that we do not have to hold a lock during a callback.
            // It is safe to not hold the lock because we are not doing any write accesses.
            IAntHalCallback callback = mCallback;
            if(null != callback)
            {
                try
                {
                    callback.antHalRxMessage(message);
                }
                catch (RemoteException e)
                {
                    // Don't do anything as this is a problem in the application
                    if(DEBUG) Log.e(TAG, "ANT HAL Rx Message callback failure in application", e);
                }
            }
            else
            {
                Log.w(TAG, "JAnt callback called after service has been destroyed");
            }
        }

        public synchronized void ANTStateChange(int NewState)
        {
            if (DEBUG) Log.i(TAG, "ANTStateChange callback to " + NewState);

            setState(NewState);
        }
    };
}

