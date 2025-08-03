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
import android.net.MacAddress;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

/**
 * AIDL implementation of the IWifiApIface interface.
 */
public class WifiApIfaceAidlImpl implements IWifiApIface {
    private static final String TAG = "WifiApIfaceAidlImpl";
    private android.hardware.wifi.IWifiApIface mWifiApIface;
    private final Object mLock = new Object();
    private String mIfaceName;

    public WifiApIfaceAidlImpl(@NonNull android.hardware.wifi.IWifiApIface apIface) {
        mWifiApIface = apIface;
    }

    /**
     * See comments for {@link IWifiApIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mIfaceName != null) return mIfaceName;
            try {
                String ifaceName = mWifiApIface.getName();
                mIfaceName = ifaceName;
                return mIfaceName;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiApIface#getBridgedInstances()}
     */
    @Override
    @Nullable
    public List<String> getBridgedInstances() {
        final String methodStr = "getBridgedInstances";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] instances = mWifiApIface.getBridgedInstances();
                if (instances == null) {
                    Log.e(TAG, methodStr + " received a null array from the HAL");
                    return null;
                }
                return Arrays.asList(instances);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiApIface#getFactoryMacAddress()}
     */
    @Override
    @Nullable
    public MacAddress getFactoryMacAddress() {
        final String methodStr = "getFactoryMacAddress";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            try {
                byte[] macBytes = mWifiApIface.getFactoryMacAddress();
                return MacAddress.fromBytes(macBytes);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, methodStr + " received invalid MAC address: " + e);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiApIface#setCountryCode(byte[])}
     */
    @Override
    public boolean setCountryCode(byte[] countryCode) {
        final String methodStr = "setCountryCode";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiApIface.setCountryCode(countryCode);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiApIface#resetToFactoryMacAddress()}
     */
    @Override
    public boolean resetToFactoryMacAddress() {
        final String methodStr = "resetToFactoryMacAddress";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiApIface.resetToFactoryMacAddress();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiApIface#isSetMacAddressSupported()}
     */
    @Override
    public boolean isSetMacAddressSupported() {
        return true; // supported by default in the AIDL HAL
    }

    /**
     * See comments for {@link IWifiApIface#setMacAddress(MacAddress)}
     */
    @Override
    public boolean setMacAddress(MacAddress mac) {
        final String methodStr = "setMacAddress";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiApIface.setMacAddress(mac.toByteArray());
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiApIface == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiApIface = null;
        mIfaceName = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
