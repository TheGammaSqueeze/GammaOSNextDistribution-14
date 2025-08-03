/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.nearby.managers;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.TargetApi;
import android.hardware.bluetooth.finder.Eid;
import android.hardware.bluetooth.finder.IBluetoothFinder;
import android.nearby.PoweredOffFindingEphemeralId;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.List;

/** Connects to {@link IBluetoothFinder} HAL and invokes its API. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class BluetoothFinderManager {

    private static final String HAL_INSTANCE_NAME = IBluetoothFinder.DESCRIPTOR + "/default";

    private IBluetoothFinder mBluetoothFinder;
    private IBinder.DeathRecipient mServiceDeathRecipient;
    private final Object mLock = new Object();

    private boolean initBluetoothFinderHal() {
        final String methodStr = "initBluetoothFinderHal";
        if (!SdkLevel.isAtLeastV()) return false;
        synchronized (mLock) {
            if (mBluetoothFinder != null) {
                Log.i(TAG, "Bluetooth Finder HAL is already initialized");
                return true;
            }
            try {
                mBluetoothFinder = getServiceMockable();
                if (mBluetoothFinder == null) {
                    Log.e(TAG, "Unable to obtain IBluetoothFinder");
                    return false;
                }
                Log.i(TAG, "Obtained IBluetoothFinder. Local ver: " + IBluetoothFinder.VERSION
                        + ", Remote ver: " + mBluetoothFinder.getInterfaceVersion());

                IBinder serviceBinder = getServiceBinderMockable();
                if (serviceBinder == null) {
                    Log.e(TAG, "Unable to obtain the service binder for IBluetoothFinder");
                    return false;
                }
                mServiceDeathRecipient = new BluetoothFinderDeathRecipient();
                serviceBinder.linkToDeath(mServiceDeathRecipient, /* flags= */ 0);

                Log.i(TAG, "Bluetooth Finder HAL initialization was successful");
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (Exception e) {
                Log.e(TAG, methodStr + " encountered an exception: "  + e);
            }
            return false;
        }
    }

    @VisibleForTesting
    protected IBluetoothFinder getServiceMockable() {
        return IBluetoothFinder.Stub.asInterface(
                ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
    }

    @VisibleForTesting
    protected IBinder getServiceBinderMockable() {
        return mBluetoothFinder.asBinder();
    }

    private class BluetoothFinderDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            Log.e(TAG, "BluetoothFinder service died.");
            synchronized (mLock) {
                mBluetoothFinder = null;
            }
        }
    }

    /** See comments for {@link IBluetoothFinder#sendEids(Eid[])} */
    public void sendEids(List<PoweredOffFindingEphemeralId> eids) {
        final String methodStr = "sendEids";
        if (!checkHalAndLogFailure(methodStr)) return;
        Eid[] eidArray = eids.stream().map(
                ephmeralId -> {
                    Eid eid = new Eid();
                    eid.bytes = ephmeralId.bytes;
                    return eid;
                }).toArray(Eid[]::new);
        try {
            mBluetoothFinder.sendEids(eidArray);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
    }

    /** See comments for {@link IBluetoothFinder#setPoweredOffFinderMode(boolean)} */
    public void setPoweredOffFinderMode(boolean enable) {
        final String methodStr = "setPoweredOffMode";
        if (!checkHalAndLogFailure(methodStr)) return;
        try {
            mBluetoothFinder.setPoweredOffFinderMode(enable);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
    }

    /** See comments for {@link IBluetoothFinder#getPoweredOffFinderMode()} */
    public boolean getPoweredOffFinderMode() {
        final String methodStr = "getPoweredOffMode";
        if (!checkHalAndLogFailure(methodStr)) return false;
        try {
            return mBluetoothFinder.getPoweredOffFinderMode();
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    private boolean checkHalAndLogFailure(String methodStr) {
        if ((mBluetoothFinder == null) && !initBluetoothFinderHal()) {
            Log.e(TAG, "Unable to call " + methodStr + " because IBluetoothFinder is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mBluetoothFinder = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
