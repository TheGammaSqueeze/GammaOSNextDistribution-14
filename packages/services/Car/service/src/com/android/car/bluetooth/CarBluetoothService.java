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
package com.android.car.bluetooth;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.car.ICarBluetoothUserService;
import android.car.ICarPerUserService;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarPerUserServiceHelper;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * CarBluetoothService - Maintains the current user's Bluetooth devices and profile connections.
 *
 * For each user, creates:
 *   1) A {@link BluetoothDeviceManager} object, responsible for maintaining a list of
 *      the user's known devices.
 *   2) A {@link BluetoothProfileInhibitManager} object that will maintain a set of inhibited
 *      profiles for each device, keeping a device from connecting on those profiles. This provides
 *      an interface to request and release inhibits.
 *   3) A {@link BluetoothDeviceConnectionPolicy} object, representing a default implementation of
 *      a policy based method of determining and requesting times to auto-connect devices. This
 *      default is controllable through a resource overlay if one chooses to implement their own.
 *
 * Provides an interface for other programs to request auto connections.
 */
public class CarBluetoothService implements CarServiceBase {
    private static final String TAG = CarLog.tagFor(CarBluetoothService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    static final String THREAD_NAME = "CarBluetoothService";
    private final Context mContext;

    // Each time CarPerUserService connects we need to get new Bluetooth profile proxies and refresh
    // all our internal objects to use them. When it disconnects we're to assume our proxies are
    // invalid. This lock protects all our internal objects.
    private final Object mPerUserLock = new Object();

    // Default Bluetooth power policy, per user, enabled with an overlay
    private final boolean mUseDefaultPowerPolicy;
    @GuardedBy("mPerUserLock")
    private BluetoothPowerPolicy mBluetoothPowerPolicy;

    // The Bluetooth Device Manager, owns the priority connection list, updated on user
    // switch
    private BluetoothDeviceManager mDeviceManager;

    // Profile-Inhibit Manager that will temporarily inhibit connections on profiles, per user
    @GuardedBy("mPerUserLock")
    private BluetoothProfileInhibitManager mInhibitManager;

    // Default Bluetooth device connection policy, per user, enabled with an overlay
    private final boolean mUseDefaultConnectionPolicy;
    @GuardedBy("mPerUserLock")
    private BluetoothDeviceConnectionPolicy mBluetoothDeviceConnectionPolicy;

    // Bluetooth Connection Retry Manager, updated on user switch
    @GuardedBy("mPerUserLock")
    private BluetoothConnectionRetryManager mConnectionRetryManager;

    // Listen for user switch events from the CarPerUserService
    @GuardedBy("mPerUserLock")
    private int mUserId;
    @GuardedBy("mPerUserLock")
    private ICarPerUserService mCarPerUserService;
    @GuardedBy("mPerUserLock")
    private ICarBluetoothUserService mCarBluetoothUserService;
    private final CarPerUserServiceHelper mUserServiceHelper;
    private final CarPerUserServiceHelper.ServiceCallback mUserServiceCallback =
            new CarPerUserServiceHelper.ServiceCallback() {
        @Override
        public void onServiceConnected(ICarPerUserService carPerUserService) {
            if (DBG) {
                Slogf.d(TAG, "Connected to CarPerUserService");
            }
            synchronized (mPerUserLock) {
                // Explicitly clear out existing per-user objects since we can't rely on the
                // onServiceDisconnected and onPreUnbind calls to always be called before this
                destroyUserLocked();

                mCarPerUserService = carPerUserService;

                // Create new objects with our new set of profile proxies
                initializeUserLocked();
            }
        }

        @Override
        public void onPreUnbind() {
            if (DBG) {
                Slogf.d(TAG, "Before Unbinding from CarPerUserService");
            }
            synchronized (mPerUserLock) {
                destroyUserLocked();
            }
        }

        @Override
        public void onServiceDisconnected() {
            if (DBG) {
                Slogf.d(TAG, "Disconnected from CarPerUserService");
            }
            synchronized (mPerUserLock) {
                destroyUserLocked();
            }
        }
    };

    /**
     * Create an instance of CarBluetoothService
     *
     * @param context - A Context object representing the context you want this service to run
     * @param userSwitchService - An instance of CarPerUserServiceHelper that we can bind a listener
     *                            to in order to receive user switch events
     */
    public CarBluetoothService(Context context, CarPerUserServiceHelper userSwitchService) {
        mUserId = UserManagerHelper.USER_NULL;
        mContext = context;
        mUserServiceHelper = userSwitchService;
        mUseDefaultConnectionPolicy = mContext.getResources().getBoolean(
                R.bool.useDefaultBluetoothConnectionPolicy);
        mUseDefaultPowerPolicy = mContext.getResources().getBoolean(
                R.bool.useDefaultBluetoothPowerPolicy);
    }

    /**
     * Complete all necessary initialization keeping this service from being running.
     *
     * Wait for the user service helper to report a user before initializing a user.
     */
    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }
        mUserServiceHelper.registerServiceCallback(mUserServiceCallback);
    }

    /**
     * Release all resources required to run this service and stop running.
     *
     * Clean up the user context once we've detached from the user service helper, if any.
     */
    @Override
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "release()");
        }
        mUserServiceHelper.unregisterServiceCallback(mUserServiceCallback);
        synchronized (mPerUserLock) {
            destroyUserLocked();
        }
    }

    /**
     * Initialize the user context using the current active user.
     *
     * Only call this following a known user switch once we've connected to the user service helper.
     */
    @GuardedBy("mPerUserLock")
    private void initializeUserLocked() {
        if (DBG) {
            Slogf.d(TAG, "Initializing new user");
        }
        mUserId = ActivityManager.getCurrentUser();
        createBluetoothUserServiceLocked();
        createBluetoothDeviceManagerLocked();
        createBluetoothProfileInhibitManagerLocked();
        // Determine if we need to begin the default power policy
        mBluetoothPowerPolicy = null;
        if (mUseDefaultPowerPolicy) {
            createBluetoothPowerPolicyLocked();
        }
        createBluetoothConnectionRetryManagerLocked();

        // Determine if we need to begin the default device connection policy
        mBluetoothDeviceConnectionPolicy = null;
        if (mUseDefaultConnectionPolicy) {
            createBluetoothDeviceConnectionPolicyLocked();
        }
        if (DBG) {
            Slogf.d(TAG, "Switched to user %d", mUserId);
        }
    }

    /**
     * Destroy the current user context, defined by the set of profile proxies, profile device
     * managers, inhibit manager and the policy.
     */
    @GuardedBy("mPerUserLock")
    private void destroyUserLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying user %d", mUserId);
        }
        destroyBluetoothDeviceConnectionPolicyLocked();
        destroyBluetoothConnectionRetryManagerLocked();
        destroyBluetoothPowerPolicyLocked();
        destroyBluetoothProfileInhibitManagerLocked();
        destroyBluetoothDeviceManagerLocked();
        destroyBluetoothUserServiceLocked();
        mCarPerUserService = null;
        mUserId = UserManagerHelper.USER_NULL;
    }

    /**
     * Sets the Per User Car Bluetooth Service (ICarBluetoothService) from the CarPerUserService
     * which acts as a top level Service running in the current user context.
     * Also sets up the connection proxy objects required to communicate with the Bluetooth
     * Profile Services.
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothUserServiceLocked() {
        if (mCarPerUserService != null) {
            try {
                mCarBluetoothUserService = mCarPerUserService.getBluetoothUserService();
                mCarBluetoothUserService.setupBluetoothConnectionProxies();
            } catch (RemoteException e) {
                Slogf.e(TAG, "Remote Service Exception on ServiceConnection Callback: %s", e);
            } catch (java.lang.NullPointerException e) {
                Slogf.e(TAG, "Initialization Failed: %s", e);
            }
        } else {
            if (DBG) {
                Slogf.d(TAG,
                        "CarPerUserService not connected. Cannot get bluetooth user proxy objects");
            }
        }
    }

    /**
     * Close out the Per User Car Bluetooth profile proxy connections and destroys the Car Bluetooth
     * User Service object.
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothUserServiceLocked() {
        if (mCarBluetoothUserService == null) {
            return;
        }
        try {
            mCarBluetoothUserService.closeBluetoothConnectionProxies();
        } catch (RemoteException e) {
            Slogf.e(TAG, "Remote Service Exception on ServiceConnection Callback: %s", e);
        }
        mCarBluetoothUserService = null;
    }

    /**
     * Clears out Profile Device Managers and re-creates them for the current user.
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothDeviceManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Creating device manager");
        }
        if (mUserId == UserManagerHelper.USER_NULL) {
            if (DBG) {
                Slogf.d(TAG, "No foreground user, cannot create profile device managers");
            }
            return;
        }
        mDeviceManager = BluetoothDeviceManager.create(mContext);
        mDeviceManager.start();
    }

    /**
     * Stops and clears the entire set of Profile Device Managers.
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothDeviceManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying device manager");
        }
        if (mDeviceManager == null) return;
        mDeviceManager.stop();
        mDeviceManager = null;
    }

    /**
     * Creates an instance of a BluetoothProfileInhibitManager under the current user
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothProfileInhibitManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Creating inhibit manager");
        }
        if (mUserId == UserManagerHelper.USER_NULL) {
            if (DBG) {
                Slogf.d(TAG, "No foreground user, cannot create profile inhibit manager");
            }
            return;
        }
        mInhibitManager = new BluetoothProfileInhibitManager(mContext, mUserId,
                mCarBluetoothUserService);
        mInhibitManager.start();
    }

    /**
     * Destroys the current instance of a BluetoothProfileInhibitManager, if one exists
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothProfileInhibitManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying inhibit manager");
        }
        if (mInhibitManager == null) return;
        mInhibitManager.stop();
        mInhibitManager = null;
    }

    /**
     * Creates an instance of {@link BluetoothConnectionRetryManager} for the current user.
     * Clears out any existing manager from previous user.
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothConnectionRetryManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Creating connection retry manager");
        }
        if (mUserId == UserManagerHelper.USER_NULL) {
            if (DBG) {
                Slogf.d(TAG, "No foreground user, cannot create connection retry manager");
            }
            return;
        }
        if (mConnectionRetryManager != null) {
            if (DBG) {
                Slogf.d(TAG, "Removing existing connection retry manager first");
            }
            destroyBluetoothConnectionRetryManagerLocked();
        }
        mConnectionRetryManager = BluetoothConnectionRetryManager.create(mContext);
        if (mConnectionRetryManager == null) {
            if (DBG) {
                Slogf.d(TAG, "Failed to create connection retry manager");
            }
            return;
        }
        mConnectionRetryManager.init();
        if (DBG) {
            Slogf.d(TAG, "Created connection retry manager");
        }
    }

    /**
     * Releases and clears {@link BluetoothConnectionRetryManager}.
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothConnectionRetryManagerLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying connection retry manager");
        }
        if (mConnectionRetryManager == null) return;
        mConnectionRetryManager.release();
        mConnectionRetryManager = null;
        if (DBG) {
            Slogf.d(TAG, "Connection retry manager removed");
        }
    }

    /**
     * Creates an instance of a BluetoothDeviceConnectionPolicy under the current user
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothDeviceConnectionPolicyLocked() {
        if (DBG) {
            Slogf.d(TAG, "Creating device connection policy");
        }
        if (mUserId == UserManagerHelper.USER_NULL) {
            if (DBG) {
                Slogf.d(TAG, "No foreground user, cannot create device connection policy");
            }
            return;
        }
        mBluetoothDeviceConnectionPolicy = BluetoothDeviceConnectionPolicy.create(mContext,
                mUserId, this);
        if (mBluetoothDeviceConnectionPolicy == null) {
            if (DBG) {
                Slogf.d(TAG, "Failed to create default Bluetooth device connection policy.");
            }
            return;
        }
        mBluetoothDeviceConnectionPolicy.init();
    }

    /**
     * Destroys the current instance of a BluetoothDeviceConnectionPolicy, if one exists
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothDeviceConnectionPolicyLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying device connection policy");
        }
        if (mBluetoothDeviceConnectionPolicy != null) {
            mBluetoothDeviceConnectionPolicy.release();
            mBluetoothDeviceConnectionPolicy = null;
        }
    }

    /**
     * Creates an instance of a BluetoothDeviceConnectionPolicy under the current user
     */
    @GuardedBy("mPerUserLock")
    private void createBluetoothPowerPolicyLocked() {
        if (DBG) {
            Slogf.d(TAG, "Creating power policy");
        }
        if (mUserId == UserManagerHelper.USER_NULL) {

            if (DBG) {
                Slogf.d(TAG, "No foreground user, cannot create power policy");
            }
            return;
        }
        mBluetoothPowerPolicy = BluetoothPowerPolicy.create(mContext, mUserId);
        if (mBluetoothPowerPolicy == null) {
            if (DBG) {
                Slogf.d(TAG, "Failed to create Bluetooth power policy.");
            }
            return;
        }
        mBluetoothPowerPolicy.init();
    }

    /**
     * Destroys the current instance of a BluetoothDeviceConnectionPolicy, if one exists
     */
    @GuardedBy("mPerUserLock")
    private void destroyBluetoothPowerPolicyLocked() {
        if (DBG) {
            Slogf.d(TAG, "Destroying power policy");
        }
        if (mBluetoothPowerPolicy != null) {
            mBluetoothPowerPolicy.release();
            mBluetoothPowerPolicy = null;
        }
    }

    /**
     * Determine if we are using the default device connection policy or not
     *
     * @return true if the default policy is active, false otherwise
     */
    public boolean isUsingDefaultConnectionPolicy() {
        synchronized (mPerUserLock) {
            return mBluetoothDeviceConnectionPolicy != null;
        }
    }

    /**
     * Determine if we are using the default power policy or not
     *
     * @return true if the default policy is active, false otherwise
     */
    public boolean isUsingDefaultPowerPolicy() {
        synchronized (mPerUserLock) {
            return mBluetoothPowerPolicy != null;
        }
    }

   /**
     * Initiate automatated connecting of devices based on the prioritized device lists for each
     * profile.
     */
    public void connectDevices() {
        enforceBluetoothAdminPermission();
        if (DBG) {
            Slogf.d(TAG, "Connect devices for each profile");
        }
        synchronized (mPerUserLock) {
            if (mDeviceManager != null) {
                mDeviceManager.beginAutoConnecting();
            }
        }
    }

    /**
     * Get the Auto Connect priority list
     *
     * @return A list of BluetoothDevice objects, ordered by highest priority first
     */
    public List<BluetoothDevice> getProfileDevicePriorityList() {
        enforceBluetoothAdminPermission();
        synchronized (mPerUserLock) {
            if (mDeviceManager != null) {
                return mDeviceManager.getDeviceListSnapshot();
            }
        }
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * Get the Auto Connect priority for a paired Bluetooth Device.
     *
     * @param device BluetoothDevice to get priority for
     * @return integer priority value, or -1 if no priority available.
     */
    public int getDeviceConnectionPriority(BluetoothDevice device) {
        enforceBluetoothAdminPermission();
        synchronized (mPerUserLock) {
            if (mDeviceManager != null) {
                return mDeviceManager.getDeviceConnectionPriority(device);
            }
        }
        return -1;
    }

    /**
     * Set the Auto Connect priority for a paired Bluetooth Device.
     *
     * @param device   Device to set priority (Tag)
     * @param priority What priority level to set to
     */
    public void setDeviceConnectionPriority(BluetoothDevice device, int priority) {
        enforceBluetoothAdminPermission();
        synchronized (mPerUserLock) {
            if (mDeviceManager != null) {
                mDeviceManager.setDeviceConnectionPriority(device, priority);
            }
        }
        return;
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @param token   A {@link IBinder} to be used as an identity for the request. If the process
     *                owning the token dies, the request will automatically be released
     * @return True if the profile was successfully inhibited, false if an error occurred.
     */
    public boolean requestProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "Request profile inhibit: profile %s, device %s",
                    BluetoothUtils.getProfileName(profile), device.getAddress());
        }
        synchronized (mPerUserLock) {
            if (mInhibitManager == null) return false;
            return mInhibitManager.requestProfileInhibit(device, profile, token);
        }
    }

    /**
     * Undo a previous call to {@link #requestProfileInhibit} with the same parameters,
     * and reconnect the profile if no other requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return True if the request was released, false if an error occurred.
     */
    public boolean releaseProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "Release profile inhibit: profile %s, device %s",
                    BluetoothUtils.getProfileName(profile), device.getAddress());
        }
        synchronized (mPerUserLock) {
            if (mInhibitManager == null) return false;
            return mInhibitManager.releaseProfileInhibit(device, profile, token);
        }
    }


    /**
     * Checks whether a request to disconnect the given profile on the given device has been made
     * and if the inhibit request is still active.
     *
     * @param device  The device on which to verify the inhibit request.
     * @param profile The profile on which to verify the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return True if inhibit was requested and is still active, false if an error occurred or
     *         inactive.
     */
    public boolean isProfileInhibited(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "Check profile inhibit: profile %s, device %s",
                    BluetoothUtils.getProfileName(profile), device.getAddress());
        }
        synchronized (mPerUserLock) {
            if (mInhibitManager == null) return false;
            return mInhibitManager.isProfileInhibited(device, profile, token);
        }
    }

    /**
     * Triggers Bluetooth to start a BVRA session with the default HFP Client device.
     */
    public boolean startBluetoothVoiceRecognition() {
        synchronized (mPerUserLock) {
            try {
                return mCarBluetoothUserService.startBluetoothVoiceRecognition();
            } catch (RemoteException e) {
                Slogf.e(TAG, "Remote Service Exception on BVRA", e);
            }
        }
        return false;
    }

    /**
     * Make sure the caller has the Bluetooth permissions that are required to execute any function
     */
    private void enforceBluetoothAdminPermission() {
        if (mContext != null
                && PackageManager.PERMISSION_GRANTED == mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.BLUETOOTH_ADMIN)) {
            return;
        }
        if (mContext == null) {
            Slogf.e(TAG, "CarBluetoothPrioritySettings does not have a Context");
        }
        throw new SecurityException("requires permission "
                + android.Manifest.permission.BLUETOOTH_ADMIN);
    }

    /**
     * Print out the verbose debug status of this object
     */
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("* %s *\n", TAG);
        mUserServiceHelper.dump(writer);

        synchronized (mPerUserLock) {
            writer.printf("User ID: %d\n", mUserId);
            writer.printf("User Proxies: %s\n", mCarBluetoothUserService != null ? "Yes" : "No");
            writer.printf("Using default connection policy? %s\n",
                    mUseDefaultConnectionPolicy ? "Yes" : "No");
            writer.printf("Using default power policy? %s\n",
                    mUseDefaultPowerPolicy ? "Yes" : "No");

            // Device Connection Policy
            if (mBluetoothDeviceConnectionPolicy != null) {
                mBluetoothDeviceConnectionPolicy.dump(writer);
            } else {
                writer.printf("BluetoothDeviceConnectionPolicy: null\n");
            }

            // Power Policy
            if (mBluetoothPowerPolicy != null) {
                mBluetoothPowerPolicy.dump(writer);
            } else {
                writer.printf("BluetoothPowerPolicy: null\n");
            }

            // Device Manager status
            if (mDeviceManager != null) {
                mDeviceManager.dump(writer);
            } else {
                writer.printf("BluetoothDeviceManager: null\n");
            }

            // Profile Inhibits
            if (mInhibitManager != null) {
                mInhibitManager.dump(writer);
            } else {
                writer.printf("BluetoothProfileInhibitManager: null\n");
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}
}
