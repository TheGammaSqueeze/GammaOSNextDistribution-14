/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.WorkSource;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.hal.WifiNanIface;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Manages the interface to the Wi-Fi Aware HAL.
 */
public class WifiAwareNativeManager {
    private static final String TAG = "WifiAwareNativeManager";
    private boolean mVerboseLoggingEnabled = false;

    // to be used for synchronizing access to any of the WifiAwareNative objects
    private final Object mLock = new Object();

    private WifiAwareStateManager mWifiAwareStateManager;
    private HalDeviceManager mHalDeviceManager;
    private Handler mHandler;
    private WifiAwareNativeCallback mWifiAwareNativeCallback;
    private WifiNanIface mWifiNanIface = null;
    private InterfaceDestroyedListener mInterfaceDestroyedListener;
    private int mReferenceCount = 0;

    WifiAwareNativeManager(WifiAwareStateManager awareStateManager,
            HalDeviceManager halDeviceManager,
            WifiAwareNativeCallback wifiAwareNativeCallback) {
        mWifiAwareStateManager = awareStateManager;
        mHalDeviceManager = halDeviceManager;
        mWifiAwareNativeCallback = wifiAwareNativeCallback;
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
        if (mWifiNanIface != null) {
            mWifiNanIface.enableVerboseLogging(halVerboseEnabled);
        }
    }

    /**
     * Initialize the class - intended for late initialization.
     *
     * @param handler Handler on which to execute interface available callbacks.
     */
    public void start(Handler handler) {
        mHandler = handler;
        mHalDeviceManager.initialize();
        mHalDeviceManager.registerStatusListener(
                new HalDeviceManager.ManagerStatusListener() {
                    @Override
                    public void onStatusChanged() {
                        if (mVerboseLoggingEnabled) Log.v(TAG, "onStatusChanged");
                        // only care about isStarted (Wi-Fi started) not isReady - since if not
                        // ready then Wi-Fi will also be down.
                        if (mHalDeviceManager.isStarted()) {
                            mWifiAwareStateManager.tryToGetAwareCapability();
                        } else {
                            awareIsDown(false);
                        }
                    }
                }, mHandler);
        if (mHalDeviceManager.isStarted()) {
            mWifiAwareStateManager.tryToGetAwareCapability();
        }
    }

    /**
     * Returns the WifiNanIface through which commands to the NAN HAL are dispatched.
     * Return may be null if not initialized/available.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public WifiNanIface getWifiNanIface() {
        synchronized (mLock) {
            return mWifiNanIface;
        }
    }

    /**
     * Attempt to obtain the HAL NAN interface.
     */
    public void tryToGetAware(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "tryToGetAware: mWifiNanIface=" + mWifiNanIface
                        + ", mReferenceCount=" + mReferenceCount + ", requestorWs=" + requestorWs);
            }

            if (mWifiNanIface != null) {
                mReferenceCount++;
                return;
            }
            if (mHalDeviceManager == null) {
                Log.e(TAG, "tryToGetAware: mHalDeviceManager is null!?");
                awareIsDown(false);
                return;
            }

            mInterfaceDestroyedListener = new InterfaceDestroyedListener();
            WifiNanIface iface = mHalDeviceManager.createNanIface(mInterfaceDestroyedListener,
                    mHandler, requestorWs);
            if (iface == null) {
                Log.e(TAG, "Was not able to obtain a WifiNanIface (even though enabled!?)");
                awareIsDown(true);
            } else {
                if (mVerboseLoggingEnabled) Log.v(TAG, "Obtained a WifiNanIface");
                if (!iface.registerFrameworkCallback(mWifiAwareNativeCallback)) {
                    Log.e(TAG, "Unable to register callback with WifiNanIface");
                    mHalDeviceManager.removeIface(iface);
                    awareIsDown(false);
                    return;
                }
                mWifiNanIface = iface;
                mReferenceCount = 1;
                mWifiNanIface.enableVerboseLogging(mVerboseLoggingEnabled);
            }
        }
    }

    /**
     * Release the HAL NAN interface.
     */
    public void releaseAware() {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "releaseAware: mWifiNanIface=" + mWifiNanIface + ", mReferenceCount="
                    + mReferenceCount);
        }

        if (mWifiNanIface == null) {
            return;
        }
        if (mHalDeviceManager == null) {
            Log.e(TAG, "releaseAware: mHalDeviceManager is null!?");
            return;
        }

        synchronized (mLock) {
            mReferenceCount--;
            if (mReferenceCount != 0) {
                return;
            }
            mInterfaceDestroyedListener.active = false;
            mInterfaceDestroyedListener = null;
            mHalDeviceManager.removeIface(mWifiNanIface);
            mWifiNanIface = null;
            mWifiAwareNativeCallback.resetChannelInfo();
        }
    }

    /**
     * Replace requestorWs in-place when iface is already enabled.
     */
    public boolean replaceRequestorWs(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "replaceRequestorWs: mWifiNanIface=" + mWifiNanIface
                        + ", mReferenceCount=" + mReferenceCount + ", requestorWs=" + requestorWs);
            }

            if (mWifiNanIface == null) {
                return false;
            }
            if (mHalDeviceManager == null) {
                Log.e(TAG, "tryToGetAware: mHalDeviceManager is null!?");
                awareIsDown(false);
                return false;
            }

            return mHalDeviceManager.replaceRequestorWsForNanIface(mWifiNanIface, requestorWs);
        }
    }

    private void awareIsDown(boolean markAsAvailable) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "awareIsDown: mWifiNanIface=" + mWifiNanIface
                        + ", mReferenceCount =" + mReferenceCount);
            }
            mWifiNanIface = null;
            mReferenceCount = 0;
            mWifiAwareStateManager.disableUsage(markAsAvailable);
        }
    }

    private class InterfaceDestroyedListener implements
            HalDeviceManager.InterfaceDestroyedListener {
        public boolean active = true;

        @Override
        public void onDestroyed(@NonNull String ifaceName) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Interface was destroyed: mWifiNanIface=" + mWifiNanIface
                        + ", active=" + active);
            }
            if (active && mWifiNanIface != null) {
                awareIsDown(true);
            } // else: we released it locally so no need to disable usage
        }
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiAwareNativeManager:");
        pw.println("  mWifiNanIface: " + mWifiNanIface);
        pw.println("  mReferenceCount: " + mReferenceCount);
        mWifiAwareNativeCallback.dump(fd, pw, args);
    }
}
