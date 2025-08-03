/*
 * ANT Android Host Stack
 *
 * Copyright 2018 Dynastream Innovations
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

package com.dsi.ant.hidl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;

import com.dsi.ant.V1_0.IAnt;
import com.dsi.ant.V1_0.IAntCallbacks;
import com.dsi.ant.V1_0.ImplProps;
import com.dsi.ant.V1_0.OptionFlags;
import com.dsi.ant.server.AntHalDefine;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IHwBinder.DeathRecipient;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

/**
 * Class for interacting over the HIDL interface to the hal server.
 */
public final class HidlClient {

    /*
     * Notes:
     *
     * The synchronization model here is to do pretty much everything with the state lock held.
     * All private functions assume that they are called with the state lock held,
     * and the public functions generally grab the lock for the duration.
     *
     * Exceptions to this are:
     *  - Code that makes callbacks. Easy to avoid deadlocks if we never call out with locks held.
     *  - The tx message function. Sending a data message may be blocked but we still need to send control messages.
     *  - Retrieving the current state. Generally want to be able to do this while power control operations are in progress.
     *  - Separate lock used for flow controlling.
     */

    /**
     * Callbacks made by this client into the code in AntServce.java
     */
    public interface ICallback {
        void ANTRxMessage(byte[] RxMessage);

        /** State values are AntHalDefine.ANT_HAL_STATE_ constants. */
        void ANTStateChange(int NewState);
    }

    /** Logcat tag. */
    private static final String TAG = "ANT HidlClient";

    // For keepalive use the invalid message id 0.
    private static final byte[] KEEPALIVE_MESG = new byte[] {(byte)0x01, (byte)0x00, (byte)0x00};
    // Response is an invalid message id response.
    private static final byte[] KEEPALIVE_RESP = new byte[] {(byte)0x03, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x28};


    // Constants for the keep alive handler
    /** Start the keep alive process */
    private static final int KEEPALIVE_START = 0;
    /** Stop the keep alive process */
    private static final int KEEPALIVE_STOP = 1;
    /** Reset the keep alive timer */
    private static final int KEEPALIVE_RESET = 2;
    /** Internal to keep alive handler, indicates the initial timeout. */
    private static final int KEEPALIVE_PING = 3;
    /** Internal to keep alive handler, indicates that no response was received. */
    private static final int KEEPALIVE_TIMEOUT = 4;

    /** Value for mDeathCookie indicating we have no valid alive interface right now. */
    private static final long INVALID_DEATH_COOKIE = 0L;

    /** Timeout for flow control responses. */
    private static final long FLOW_CONTROL_TIMEOUT_NS = 10L * 1000L * 1000L * 1000L;

    /** Timeout for keepalive */
    private static final long KEEPALIVE_TIMEOUT_MS = 5L * 1000L;

    // Constants for server bind retries.
    private static final int HAL_BIND_RETRY_DELAY_MS = 100;
    private static final int HAL_BIND_RETRY_COUNT = 10;


    /** Lock object used for all synchronization in this class. */
    private final Object mStateLock = new Object();

    /** Lock for using flow control on the data channel. */
    private final Object mFlowControlLock = new Object();

    /** Separate thread for making state change callbacks. */
    private final Handler mStateDispatcher;
    {
        HandlerThread thread = new HandlerThread("State Dispatch");
        thread.start();
        mStateDispatcher = new Handler(thread.getLooper());
    }

    /** Handler for dispatching a recovery attempt from failed message transmits. */
    private final Handler mRecoveryDispatcher = new Handler();


    /** Power state of transport. */
    private volatile int mState = AntHalDefine.ANT_HAL_STATE_DISABLED;

    /** Callbacks to AntService. */
    private volatile ICallback mCallbacks;

    /** Handle to HIDL server. */
    private IAnt mHidl;

    /** Properties of the server implementation. */
    private ImplProps mHidlProps;

    /** Keep track of the last cookie given to linkToDeath() */
    private long mDeathCookie = INVALID_DEATH_COOKIE;

    /** Next value to use for the death cookie. */
    private long mNextDeathCookie = 1;

    /** Whether flow control is used or not. */
    private boolean mUseFlowControl = false;

    /** Flag to communicate that a flow go was received. */
    private boolean mFlowGoReceived = false;

    /** Whether keep alive should be used or not. */
    private boolean mUseKeepalive = false;


    /**
     * Initial setup at ANTHALService startup.
     */
    public void create(ICallback callback) {
        synchronized (mStateLock) {
            mCallbacks = callback;
        }
    }

    /**
     * Cleanup when ANTHALService is shutting down.
     */
    public void destroy() {
        synchronized (mStateLock) {
            // No need to send state updates since we only destroy when no one is bound.
            disable();
            cleanupHidl();
        }
    }

    /**
     * Enable the transport. Will perform the binding to the HIDL server if required.
     * @return true if transport bring up was successful, false otherwise.
     */
    public boolean enable() {
        synchronized (mStateLock) {

            // If already enabled there is nothing to do.
            if (mState == AntHalDefine.ANT_HAL_STATE_ENABLED) {
                Log.d(TAG, "Ignoring enable(), already enabled.");
                return true;
            }

            updateState(AntHalDefine.ANT_HAL_STATE_ENABLING);

            if (doBringup()) {
                updateState(AntHalDefine.ANT_HAL_STATE_ENABLED);
                return true;
            } else {
                updateState(AntHalDefine.ANT_HAL_STATE_DISABLED);
                return false;
            }
        }
    }

    /**
     * Disable the transport.
     */
    public void disable() {
        synchronized (mStateLock) {

            // If already disabled there is nothing to do.
            if (mState == AntHalDefine.ANT_HAL_STATE_DISABLED) {
                Log.d(TAG, "Ignoring disable(), already disabled.");
                return;
            }

            updateState(AntHalDefine.ANT_HAL_STATE_DISABLING);
            try {
                int status = mHidl.disable();
                if (status != 0) {
                    Log.w(TAG, "Failed to disable ANT: " + mHidl.translateStatus(status));
                }
                cleanupHidl();
            } catch (RemoteException e) {
                Log.w(TAG, "Server died in disable", e);
                handleServerDeath();
            }

            updateState(AntHalDefine.ANT_HAL_STATE_DISABLED);
        }
    }

    /**
     * Perform a full power cycle of the transport/chip. Usually used to recover from error conditions.
     * @return true if the transport/chip where successfully brought back to an operational state.
     */
    public boolean hardReset() {
        synchronized (mStateLock) {

            updateState(AntHalDefine.ANT_HAL_STATE_RESETTING);

            if (doRecovery()) {
                updateState(AntHalDefine.ANT_HAL_STATE_RESET);
                return true;
            } else {
                updateState(AntHalDefine.ANT_HAL_STATE_DISABLED);
                return false;
            }
        }
    }

    /**
     * Query the current state of the transport.
     * @return Ant ANT_HAL_STATE constant representing the current state of the transport.
     */
    public int getRadioEnabledStatus() {
        // No need to hold lock, since value is marked as volatile.
        return mState;
    }

    /**
     * Send a message to the firmware over the transport.
     * @param message The ANT message to send.
     * @return True if the message was successfully sent, false otherwise.
     */
    public boolean ANTTxMessage(byte[] message) {

        // Cache a few things that might change during the unsynchronized portion.
        IAnt cachedHidl;
        long cachedDeathCookie;
        boolean succeeded = false;

        synchronized (mStateLock) {

            // Hidl interface is only valid while enabled.
            if (mState != AntHalDefine.ANT_HAL_STATE_ENABLED) {
                Log.w(TAG, "Failing message tx because transport was not enabled.");
                return false;
            }

            cachedHidl = mHidl;
            cachedDeathCookie = mDeathCookie;
        }

        // The hidl interface uses ArrayLists, so need to convert.
        ArrayList<Byte> hidlMessage = new ArrayList<>(message.length);
        for (byte b : message) {
            hidlMessage.add(b);
        }

        // Dispatch to proper channel.
        try {
            int status;
            boolean timeout = false;

            switch (message[Mesg.ID_OFFSET]) {
            case Mesg.BROADCAST_DATA_ID:
            case Mesg.ACKNOWLEDGED_DATA_ID:
            case Mesg.BURST_DATA_ID:
            case Mesg.EXT_BROADCAST_DATA_ID:
            case Mesg.EXT_ACKNOWLEDGED_DATA_ID:
            case Mesg.EXT_BURST_DATA_ID:
            case Mesg.ADV_BURST_DATA_ID:
                // Data messages may be flow controlled at protocol level.
                synchronized (mFlowControlLock) {
                    status = cachedHidl.sendDataMessage(hidlMessage);
                    if (mUseFlowControl) {
                        timeout = !waitForFlowGoLocked();
                    }
                }
                break;
            default:
                status = cachedHidl.sendCommandMessage(hidlMessage);
                break;
            }

            if (timeout) {
                Log.e(TAG, "Timeout waiting for flow control response.");
            } else if (status != 0) {
                Log.e(TAG, "Failed to send message: " + mHidl.translateStatus(status));
            } else {
                succeeded = true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Server died while sending message.", e);
            // Treat the interface going down during sending of a message the
            // same as if an async crash was detected.
            mRecoveryDispatcher.post(new Runnable() {
                private long deathCookie = cachedDeathCookie;

                @Override
                public void run() {
                    mDeathHandler.serviceDied(deathCookie);
                }
            });
        }

        return succeeded;
    }

    /**
     * Wait for a flow go to be received. This should only be called with the flow control
     * lock held, and the lock should be grabbed before sending the message that prompts the flow go response.
     * @return True if a flow go message was received within the timeout.
     */
    public boolean waitForFlowGoLocked() {
        mFlowGoReceived = false;
        long deadline = System.nanoTime() + FLOW_CONTROL_TIMEOUT_NS;
        long diff = deadline - System.nanoTime();

        while (diff > 0 && !mFlowGoReceived) {
            try {
                // Wait takes a time in milliseconds
                long diff_ms = diff / (1000L * 1000L);
                if (diff_ms > 0) {
                    mFlowControlLock.wait(diff_ms);
                } else {
                    break;
                }
            } catch (InterruptedException e) {
                // Shouldn't be interrupted, but if it is just move the deadline to now.
                deadline = System.nanoTime();
            }
            diff = deadline - System.nanoTime();
        }

        return mFlowGoReceived;
    }

    /**
     * Make sure that the interface to the hidl server is fully brought up.
     * @return true if the interface is fully initialized, false otherwise.
     */
    private boolean setupHidl() {
        boolean succeeded = false;

        try {
            // If the interface is already up there is nothing to do.
            if (mHidl != null) {
                return true;
            }

            // Use a unique cookie for link to death. This filters out extraneous death notifications.
            mDeathCookie = mNextDeathCookie++;
            // Shouldn't ever reach wrap around, but just in case...
            if (mNextDeathCookie == INVALID_DEATH_COOKIE) {
                mNextDeathCookie++;
            }

            // Initial setup of the interface.
            int bindAttempts = 0;
            while ((mHidl == null) && (bindAttempts < HAL_BIND_RETRY_COUNT)) {
                if (bindAttempts > 0) {
                    Log.i(TAG, "Could not find hal. Retrying in a little bit.");
                    try {
                        Thread.sleep(HAL_BIND_RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                bindAttempts++;
                try {
                    mHidl = IAnt.getService();
                } catch (NoSuchElementException e) {
                    // Do nothing, mHidl is null and we will delay then retry.
                }
            }
            if (mHidl == null) {
                Log.e(TAG, "Unable to bind to hidl server.");
                handleServerDeath();
            } else {
                mHidl.linkToDeath(mDeathHandler, mDeathCookie);
                mHidlProps = mHidl.getProperties();
                mUseFlowControl = (OptionFlags.USE_ANT_FLOW_CONTROL & mHidlProps.options) != 0;
                mUseKeepalive = (OptionFlags.USE_KEEPALIVE & mHidlProps.options) != 0;
                mHidl.setCallbacks(mHidlCallbacks);

                Log.i(TAG, "Successfully bound to HAL server.");
                Log.d(TAG, "Server properties: " + mHidlProps);

                succeeded = true;
            }

        } catch (RemoteException e) {
            Log.e(TAG, "Unable to bind to hidl server.", e);
            // Make sure everything is cleaned up.
            handleServerDeath();
        }

        return succeeded;
    }

    /**
     * Cleanup the binding to the HIDL server.
     */
    private void cleanupHidl() {

        // Nothing to do if we don't currently have an interface binding.
        if (mHidl == null) {
            return;
        }

        // This can only be done if the server is still alive.
        try {
            mHidl.unlinkToDeath(mDeathHandler);
            mHidl.setCallbacks(null);
        } catch (RemoteException e) {
            Log.w(TAG, "Error clearing hidl callbacks", e);
        }

        // Perform any cleanup for the server being gone.
        handleServerDeath();
    }

    /**
     * Cleanup local resources after the server has gone away.
     */
    private void handleServerDeath() {
        mDeathCookie = INVALID_DEATH_COOKIE;
        mHidl = null;
    }

    /**
     * Attempt to power cycle the chip.
     * This is the implementation of hardReset, but extracted for clearer state signaling.
     *
     * @return true if the chip was restored to an operational state, false otherwise.
     */
    private boolean doRecovery() {
        boolean succeeded = false;

        // Can't do anything without the interface being up.
        if (!setupHidl()) {
            return false;
        }

        try {
            int status;
            status = mHidl.disable();
            if (status != 0) {
                Log.w(TAG, "Reset - Failed to disable: " + mHidl.translateStatus(status));
            }
            // Even if disable failed we can try to enable again anyways and hope it fixes the issue.

            status = mHidl.enable();
            if (status == 0) {
                succeeded = true;
            } else {
                Log.e(TAG, "Reset - Failed to enable: " + mHidl.translateStatus(status));
            }

        } catch (RemoteException e) {
            handleServerDeath();
        }

        return succeeded;
    }

    /**
     * Attempt to bring the chip to an operational state. This is the implementation of enable,
     * but extracted for clearer state signaling code.
     * @return true if the chip and transport where successfully brought up, false otherwise.
     */
    private boolean doBringup() {
        boolean succeeded = false;

        // Make sure interface is up.
        if (!setupHidl()) {
            return false;
        }

        // Now perform actual enable operation.
        try {
            int status = mHidl.enable();
            if (status == 0) {
                succeeded = true;
            } else {
                Log.e(TAG, "Failed to enable ANT: " + mHidl.translateStatus(status));

                // Try to clean things up.
                status = mHidl.disable();
                if (status != 0) {
                    Log.w(TAG, "Disable failed: " + mHidl.translateStatus(status));
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Server died in enable.", e);
            handleServerDeath();
        }

        return succeeded;
    }

    /**
     * Update the internal state, and dispatch the update callback on a separate thread.
     * @param newState The new internal state. A callback will only be dispatched if this is different than
     *                 the previous state.
     */
    private void updateState(int newState) {

        // Ignore non-updates.
        if (newState == mState) {
            return;
        }

        mState = newState;

        // RESET is not actually a valid state, only used for signaling. In the case where it does occur we are
        // in an enabled state.
        if (mState == AntHalDefine.ANT_HAL_STATE_RESET) {
            mState = AntHalDefine.ANT_HAL_STATE_ENABLED;
        }

        if (mUseKeepalive) {
            // Change keep alive state based on whether we are now enabled or disabled.
            int msg = (mState == AntHalDefine.ANT_HAL_STATE_ENABLED) ? KEEPALIVE_START : KEEPALIVE_STOP;
            mKeepAliveHandler.sendMessage(mKeepAliveHandler.obtainMessage(msg));
        }


        // Dispatch state updates with a handler so that they don't block the current operation.
        mStateDispatcher.post(new Runnable() {
            @Override
            public void run() {
                // Cache since check+call is not atomic.
                ICallback cachedCallbacks = mCallbacks;
                if (cachedCallbacks != null) {
                    cachedCallbacks.ANTStateChange(newState);
                }
            }
        });
    }

    /**
     * Listen for notifications about the server crashing.
     */
    private final DeathRecipient mDeathHandler = new DeathRecipient() {
        @Override
        public void serviceDied(long cookie) {
            synchronized (mStateLock) {

                // Only do anything if we don't already know about this death.
                if (cookie != mDeathCookie) {
                    return;
                }

                Log.e(TAG, "Detected asynchronous server death.");

                handleServerDeath();
                // Attempt automated recovery.
                // Result is ignored, errors already logged to logcat and radio service notified by state callbacks.
                hardReset();
            }
        }
    };

    /**
     * Handle callbacks from the HIDL server.
     */
    private final IAntCallbacks mHidlCallbacks = new IAntCallbacks.Stub() {

        @Override
        public void onTransportDown(String cause) throws RemoteException {
            synchronized (mStateLock) {
                Log.e(TAG, "Transport is down: " + cause);
                // Attempt automated recovery.
                hardReset();
            }
        }

        @Override
        public void onMessageReceived(ArrayList<Byte> data) throws RemoteException {

            // Translate to a byte array.
            byte[] message = new byte[data.size()];
            for (int i = 0; i < data.size(); i++) {
                message[i] = data.get(i);
            }

            if (!keepaliveHandler(message) && !flowControlHandler(message)) {
                // No one requested the message be filtered.
                // Cache the callback since check+call is non atomic.
                ICallback cachedCallbacks = mCallbacks;
                if (cachedCallbacks != null) {
                    cachedCallbacks.ANTRxMessage(message);
                }
            }
        }

        /**
         * Message handling for keep alive monitoring.
         *
         * @return true if mesg was for keep alive purposes and should be filtered out.
         */
        private boolean keepaliveHandler(byte[] mesg) {
            if (mUseKeepalive) {
                // All activity resets the timers.
                mKeepAliveHandler.sendMessage(mKeepAliveHandler.obtainMessage(KEEPALIVE_RESET));

                return Arrays.equals(mesg, KEEPALIVE_RESP);
            } else {
                return false;
            }
        }

        /**
         * Message handling for data channel flow control.
         * @return True if mesg was for flow control purposes and should be filtered out.
         */
        private boolean flowControlHandler(byte[] mesg) {
            boolean filter = false;

            if (mUseFlowControl) {
                if (mesg[Mesg.ID_OFFSET] == Mesg.FLOW_CONTROL_ID) {

                    filter = true;

                    if (mesg[Mesg.DATA_OFFSET] == Mesg.FLOW_CONTROL_GO) {

                        synchronized (mFlowControlLock) {
                            mFlowGoReceived = true;
                            mFlowControlLock.notify();
                        }
                    }
                }
            }

            return filter;
        }
    };

    private final Handler mKeepAliveHandler = new Handler() {

        private boolean started = false;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case KEEPALIVE_START:
                if (!started) {
                    started = true;
                    sendMessageDelayed(obtainMessage(KEEPALIVE_PING), KEEPALIVE_TIMEOUT_MS);
                }
                break;

            case KEEPALIVE_STOP:
                started = false;
                removeMessages(KEEPALIVE_PING);
                removeMessages(KEEPALIVE_TIMEOUT);
                break;

            case KEEPALIVE_RESET:
                removeMessages(KEEPALIVE_PING);
                removeMessages(KEEPALIVE_TIMEOUT);
                if (started) {
                    sendMessageDelayed(obtainMessage(KEEPALIVE_PING), KEEPALIVE_TIMEOUT_MS);
                }
                break;

            case KEEPALIVE_PING:
                // Safe to ignore the result, error will be detected either the next time the radio service sends a message
                // or when the keepalive timeout expires.
                ANTTxMessage(KEEPALIVE_MESG);
                sendMessageDelayed(obtainMessage(KEEPALIVE_TIMEOUT), KEEPALIVE_TIMEOUT_MS);
                break;

            case KEEPALIVE_TIMEOUT:
                Log.e(TAG, "No response to keepalive message, attempting recovery.");
                // Return ignored for same reasons as in mDeathHandler.
                hardReset();
                break;
            }
        }
    };
}
