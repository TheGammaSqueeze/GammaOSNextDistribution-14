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

package com.android.networkstack.tethering;

import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_AIDL;

import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.hardware.tetheroffload.ForwardedStats;
import android.hardware.tetheroffload.IOffload;
import android.hardware.tetheroffload.ITetheringOffloadCallback;
import android.hardware.tetheroffload.NatTimeoutUpdate;
import android.hardware.tetheroffload.NetworkProtocol;
import android.hardware.tetheroffload.OffloadCallbackEvent;
import android.os.Build;
import android.os.Handler;
import android.os.NativeHandle;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import android.system.OsConstants;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.tethering.OffloadHardwareInterface.OffloadHalCallback;

import java.util.ArrayList;

/**
 * The implementation of IOffloadHal which based on Stable AIDL interface
 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class OffloadHalAidlImpl implements IOffloadHal {
    private static final String TAG = OffloadHalAidlImpl.class.getSimpleName();
    private static final String HAL_INSTANCE_NAME = IOffload.DESCRIPTOR + "/default";

    private final Handler mHandler;
    private final SharedLog mLog;
    private final IOffload mIOffload;
    @OffloadHardwareInterface.OffloadHalVersion
    private final int mOffloadVersion;

    private TetheringOffloadCallback mTetheringOffloadCallback;

    @VisibleForTesting
    public OffloadHalAidlImpl(int version, @NonNull IOffload offload, @NonNull Handler handler,
            @NonNull SharedLog log) {
        mOffloadVersion = version;
        mIOffload = offload;
        mHandler = handler;
        mLog = log.forSubComponent(TAG);
    }

    /**
     * Initialize the Tetheroffload HAL. Provides bound netlink file descriptors for use in the
     * management process.
     */
    public boolean initOffload(@NonNull NativeHandle handle1, @NonNull NativeHandle handle2,
            @NonNull OffloadHalCallback callback) {
        final String methodStr = String.format("initOffload(%d, %d, %s)",
                handle1.getFileDescriptor().getInt$(), handle2.getFileDescriptor().getInt$(),
                (callback == null) ? "null"
                : "0x" + Integer.toHexString(System.identityHashCode(callback)));
        mTetheringOffloadCallback = new TetheringOffloadCallback(mHandler, callback, mLog);
        try {
            mIOffload.initOffload(
                    ParcelFileDescriptor.adoptFd(handle1.getFileDescriptor().getInt$()),
                    ParcelFileDescriptor.adoptFd(handle2.getFileDescriptor().getInt$()),
                    mTetheringOffloadCallback);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /** Stop the Tetheroffload HAL. */
    public boolean stopOffload() {
        final String methodStr = "stopOffload()";
        try {
            mIOffload.stopOffload();
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }

        mTetheringOffloadCallback = null;
        mLog.i(methodStr);
        return true;
    }

    /** Get HAL interface version number. */
    public int getVersion() {
        return mOffloadVersion;
    }

    /** Get Tx/Rx usage from last query. */
    public OffloadHardwareInterface.ForwardedStats getForwardedStats(@NonNull String upstream) {
        ForwardedStats stats = new ForwardedStats();
        final String methodStr = String.format("getForwardedStats(%s)",  upstream);
        try {
            stats = mIOffload.getForwardedStats(upstream);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
        }
        mLog.i(methodStr);
        return new OffloadHardwareInterface.ForwardedStats(stats.rxBytes, stats.txBytes);
    }

    /** Set local prefixes to offload management process. */
    public boolean setLocalPrefixes(@NonNull ArrayList<String> localPrefixes) {
        final String methodStr = String.format("setLocalPrefixes([%s])",
                String.join(",", localPrefixes));
        try {
            mIOffload.setLocalPrefixes(localPrefixes.toArray(new String[localPrefixes.size()]));
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /**
     * Set data limit value to offload management process.
     * Method setDataLimit is deprecated in AIDL, so call setDataWarningAndLimit instead,
     * with warningBytes set to its MAX_VALUE.
     */
    public boolean setDataLimit(@NonNull String iface, long limit) {
        final long warning = Long.MAX_VALUE;
        final String methodStr = String.format("setDataLimit(%s, %d)", iface, limit);
        try {
            mIOffload.setDataWarningAndLimit(iface, warning, limit);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /** Set data warning and limit value to offload management process. */
    public boolean setDataWarningAndLimit(@NonNull String iface, long warning, long limit) {
        final String methodStr =
                String.format("setDataWarningAndLimit(%s, %d, %d)", iface, warning, limit);
        try {
            mIOffload.setDataWarningAndLimit(iface, warning, limit);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /** Set upstream parameters to offload management process. */
    public boolean setUpstreamParameters(@NonNull String iface, @NonNull String v4addr,
            @NonNull String v4gateway, @NonNull ArrayList<String> v6gws) {
        final String methodStr = String.format("setUpstreamParameters(%s, %s, %s, [%s])",
                iface, v4addr, v4gateway, String.join(",", v6gws));
        try {
            mIOffload.setUpstreamParameters(iface, v4addr, v4gateway,
                    v6gws.toArray(new String[v6gws.size()]));
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /** Add downstream prefix to offload management process. */
    public boolean addDownstream(@NonNull String ifname, @NonNull String prefix) {
        final String methodStr = String.format("addDownstream(%s, %s)", ifname, prefix);
        try {
            mIOffload.addDownstream(ifname, prefix);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /** Remove downstream prefix from offload management process. */
    public boolean removeDownstream(@NonNull String ifname, @NonNull String prefix) {
        final String methodStr = String.format("removeDownstream(%s, %s)", ifname, prefix);
        try {
            mIOffload.removeDownstream(ifname, prefix);
        } catch (Exception e) {
            logAndIgnoreException(e, methodStr);
            return false;
        }
        mLog.i(methodStr);
        return true;
    }

    /**
     * Get {@link IOffloadHal} object from the AIDL service.
     *
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @param log Log to be used by the repository.
     */
    public static IOffloadHal getIOffloadHal(Handler handler, SharedLog log) {
        // Tetheroffload AIDL interface is only supported after U.
        if (!SdkLevel.isAtLeastU() || !ServiceManager.isDeclared(HAL_INSTANCE_NAME)) return null;

        IOffload offload = IOffload.Stub.asInterface(
                ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
        if (offload == null) return null;

        return new OffloadHalAidlImpl(OFFLOAD_HAL_VERSION_AIDL, offload, handler, log);
    }

    private void logAndIgnoreException(Exception e, final String methodStr) {
        mLog.e(methodStr + " failed with " + e.getClass().getSimpleName() + ": ", e);
    }

    private static class TetheringOffloadCallback extends ITetheringOffloadCallback.Stub {
        public final Handler handler;
        public final OffloadHalCallback callback;
        public final SharedLog log;

        TetheringOffloadCallback(
                Handler h, OffloadHalCallback cb, SharedLog sharedLog) {
            handler = h;
            callback = cb;
            log = sharedLog;
        }

        private void handleOnEvent(int event) {
            switch (event) {
                case OffloadCallbackEvent.OFFLOAD_STARTED:
                    callback.onStarted();
                    break;
                case OffloadCallbackEvent.OFFLOAD_STOPPED_ERROR:
                    callback.onStoppedError();
                    break;
                case OffloadCallbackEvent.OFFLOAD_STOPPED_UNSUPPORTED:
                    callback.onStoppedUnsupported();
                    break;
                case OffloadCallbackEvent.OFFLOAD_SUPPORT_AVAILABLE:
                    callback.onSupportAvailable();
                    break;
                case OffloadCallbackEvent.OFFLOAD_STOPPED_LIMIT_REACHED:
                    callback.onStoppedLimitReached();
                    break;
                case OffloadCallbackEvent.OFFLOAD_WARNING_REACHED:
                    callback.onWarningReached();
                    break;
                default:
                    log.e("Unsupported OffloadCallbackEvent: " + event);
            }
        }

        @Override
        public void onEvent(int event) {
            handler.post(() -> {
                handleOnEvent(event);
            });
        }

        @Override
        public void updateTimeout(NatTimeoutUpdate params) {
            handler.post(() -> {
                callback.onNatTimeoutUpdate(
                        networkProtocolToOsConstant(params.proto),
                        params.src.addr, params.src.port,
                        params.dst.addr, params.dst.port);
            });
        }

        @Override
        public String getInterfaceHash() {
            return ITetheringOffloadCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ITetheringOffloadCallback.VERSION;
        }
    }

    private static int networkProtocolToOsConstant(int proto) {
        switch (proto) {
            case NetworkProtocol.TCP: return OsConstants.IPPROTO_TCP;
            case NetworkProtocol.UDP: return OsConstants.IPPROTO_UDP;
            default:
                // The caller checks this value and will log an error. Just make
                // sure it won't collide with valid OsConstants.IPPROTO_* values.
                return -Math.abs(proto);
        }
    }
}
