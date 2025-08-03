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

import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_0;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_1;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_NONE;
import static com.android.networkstack.tethering.OffloadHardwareInterface.halVerToString;
import static com.android.networkstack.tethering.util.TetheringUtils.uint16;

import android.annotation.NonNull;
import android.hardware.tetheroffload.config.V1_0.IOffloadConfig;
import android.hardware.tetheroffload.control.V1_0.IOffloadControl;
import android.hardware.tetheroffload.control.V1_0.NatTimeoutUpdate;
import android.hardware.tetheroffload.control.V1_0.NetworkProtocol;
import android.hardware.tetheroffload.control.V1_0.OffloadCallbackEvent;
import android.hardware.tetheroffload.control.V1_1.ITetheringOffloadCallback;
import android.os.Handler;
import android.os.NativeHandle;
import android.os.RemoteException;
import android.system.OsConstants;
import android.util.Log;

import com.android.net.module.util.SharedLog;
import com.android.networkstack.tethering.OffloadHardwareInterface.ForwardedStats;
import com.android.networkstack.tethering.OffloadHardwareInterface.OffloadHalCallback;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * The implementation of IOffloadHal which based on HIDL interfaces
 */
public class OffloadHalHidlImpl implements IOffloadHal {
    private static final String TAG = OffloadHalHidlImpl.class.getSimpleName();
    private static final String YIELDS = " -> ";

    private final Handler mHandler;
    private final SharedLog mLog;
    private final IOffloadConfig mIOffloadConfig;
    private final IOffloadControl mIOffloadControl;
    @OffloadHardwareInterface.OffloadHalVersion
    private final int mOffloadControlVersion;

    private OffloadHalCallback mOffloadHalCallback;
    private TetheringOffloadCallback mTetheringOffloadCallback;

    public OffloadHalHidlImpl(int version, @NonNull IOffloadConfig config,
            @NonNull IOffloadControl control, @NonNull Handler handler, @NonNull SharedLog log) {
        mOffloadControlVersion = version;
        mIOffloadConfig = config;
        mIOffloadControl = control;
        mHandler = handler;
        mLog = log.forSubComponent(TAG);
    }

    /**
     * Initialize the Tetheroffload HAL. Provides bound netlink file descriptors for use in the
     * management process.
     */
    public boolean initOffload(@NonNull NativeHandle handle1, @NonNull NativeHandle handle2,
            @NonNull OffloadHalCallback callback) {
        final String logmsg = "initOffload()";

        mOffloadHalCallback = callback;
        mTetheringOffloadCallback = new TetheringOffloadCallback(
                mHandler, mOffloadHalCallback, mLog, mOffloadControlVersion);
        final CbResults results = new CbResults();
        try {
            mIOffloadConfig.setHandles(handle1, handle2,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
            mIOffloadControl.initOffload(
                    mTetheringOffloadCallback,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Stop the Tetheroffload HAL. */
    public boolean stopOffload() {
        try {
            mIOffloadControl.stopOffload(
                    (boolean success, String errMsg) -> {
                        if (!success) mLog.e("stopOffload failed: " + errMsg);
                    });
        } catch (RemoteException e) {
            mLog.e("failed to stopOffload: " + e);
        }
        mOffloadHalCallback = null;
        mTetheringOffloadCallback = null;
        mLog.log("stopOffload()");
        return true;
    }

    /** Get HAL interface version number. */
    public int getVersion() {
        return mOffloadControlVersion;
    }

    /** Get Tx/Rx usage from last query. */
    public ForwardedStats getForwardedStats(@NonNull String upstream) {
        final String logmsg = String.format("getForwardedStats(%s)",  upstream);

        final ForwardedStats stats = new ForwardedStats();
        try {
            mIOffloadControl.getForwardedStats(
                    upstream,
                    (long rxBytes, long txBytes) -> {
                        stats.rxBytes = (rxBytes > 0) ? rxBytes : 0;
                        stats.txBytes = (txBytes > 0) ? txBytes : 0;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return stats;
        }

        return stats;
    }

    /** Set local prefixes to offload management process. */
    public boolean setLocalPrefixes(@NonNull ArrayList<String> localPrefixes) {
        final String logmsg = String.format("setLocalPrefixes([%s])",
                String.join(",", localPrefixes));

        final CbResults results = new CbResults();
        try {
            mIOffloadControl.setLocalPrefixes(localPrefixes,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Set data limit value to offload management process. */
    public boolean setDataLimit(@NonNull String iface, long limit) {

        final String logmsg = String.format("setDataLimit(%s, %d)", iface, limit);

        final CbResults results = new CbResults();
        try {
            mIOffloadControl.setDataLimit(
                    iface, limit,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Set data warning and limit value to offload management process. */
    public boolean setDataWarningAndLimit(@NonNull String iface, long warning, long limit) {
        if (mOffloadControlVersion < OFFLOAD_HAL_VERSION_HIDL_1_1) {
            throw new UnsupportedOperationException(
                    "setDataWarningAndLimit is not supported below HAL V1.1");
        }
        final String logmsg =
                String.format("setDataWarningAndLimit(%s, %d, %d)", iface, warning, limit);

        final CbResults results = new CbResults();
        try {
            ((android.hardware.tetheroffload.control.V1_1.IOffloadControl) mIOffloadControl)
                    .setDataWarningAndLimit(
                            iface, warning, limit,
                            (boolean success, String errMsg) -> {
                                results.mSuccess = success;
                                results.mErrMsg = errMsg;
                            });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Set upstream parameters to offload management process. */
    public boolean setUpstreamParameters(@NonNull String iface, @NonNull String v4addr,
            @NonNull String v4gateway, @NonNull ArrayList<String> v6gws) {
        final String logmsg = String.format("setUpstreamParameters(%s, %s, %s, [%s])",
                iface, v4addr, v4gateway, String.join(",", v6gws));

        final CbResults results = new CbResults();
        try {
            mIOffloadControl.setUpstreamParameters(
                    iface, v4addr, v4gateway, v6gws,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Add downstream prefix to offload management process. */
    public boolean addDownstream(@NonNull String ifname, @NonNull String prefix) {
        final String logmsg = String.format("addDownstream(%s, %s)", ifname, prefix);

        final CbResults results = new CbResults();
        try {
            mIOffloadControl.addDownstream(ifname, prefix,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /** Remove downstream prefix from offload management process. */
    public boolean removeDownstream(@NonNull String ifname, @NonNull String prefix) {
        final String logmsg = String.format("removeDownstream(%s, %s)", ifname, prefix);

        final CbResults results = new CbResults();
        try {
            mIOffloadControl.removeDownstream(ifname, prefix,
                    (boolean success, String errMsg) -> {
                        results.mSuccess = success;
                        results.mErrMsg = errMsg;
                    });
        } catch (RemoteException e) {
            record(logmsg, e);
            return false;
        }

        record(logmsg, results);
        return results.mSuccess;
    }

    /**
     * Get {@link IOffloadHal} object from the HIDL service.
     *
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @param log Log to be used by the repository.
     */
    public static IOffloadHal getIOffloadHal(Handler handler, SharedLog log) {
        IOffloadConfig config = null;
        try {
            config = IOffloadConfig.getService(true /*retry*/);
        } catch (RemoteException e) {
            log.e("getIOffloadConfig error " + e);
            return null;
        } catch (NoSuchElementException e) {
            log.i("getIOffloadConfig Tether Offload HAL not present/implemented");
            return null;
        }

        IOffloadControl control = null;
        int version = OFFLOAD_HAL_VERSION_NONE;
        try {
            control = android.hardware.tetheroffload.control
                    .V1_1.IOffloadControl.getService(true /*retry*/);
            version = OFFLOAD_HAL_VERSION_HIDL_1_1;
        } catch (NoSuchElementException e) {
            // Unsupported by device.
        } catch (RemoteException e) {
            log.e("Unable to get offload control " + OFFLOAD_HAL_VERSION_HIDL_1_1);
        }
        if (control == null) {
            try {
                control = IOffloadControl.getService(true /*retry*/);
                version = OFFLOAD_HAL_VERSION_HIDL_1_0;
            } catch (NoSuchElementException e) {
                // Unsupported by device.
            } catch (RemoteException e) {
                log.e("Unable to get offload control " + OFFLOAD_HAL_VERSION_HIDL_1_0);
            }
        }

        if (config == null || control == null) return null;

        return new OffloadHalHidlImpl(version, config, control, handler, log);
    }

    private void record(String msg, Throwable t) {
        mLog.e(msg + YIELDS + "exception: " + t);
    }

    private void record(String msg, CbResults results) {
        final String logmsg = msg + YIELDS + results;
        if (!results.mSuccess) {
            mLog.e(logmsg);
        } else {
            mLog.log(logmsg);
        }
    }

    private static class TetheringOffloadCallback extends ITetheringOffloadCallback.Stub {
        public final Handler handler;
        public final OffloadHalCallback callback;
        public final SharedLog log;
        private final int mOffloadControlVersion;

        TetheringOffloadCallback(
                Handler h, OffloadHalCallback cb, SharedLog sharedLog, int offloadControlVersion) {
            handler = h;
            callback = cb;
            log = sharedLog;
            this.mOffloadControlVersion = offloadControlVersion;
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
                case android.hardware.tetheroffload.control
                        .V1_1.OffloadCallbackEvent.OFFLOAD_WARNING_REACHED:
                    callback.onWarningReached();
                    break;
                default:
                    log.e("Unsupported OffloadCallbackEvent: " + event);
            }
        }

        @Override
        public void onEvent(int event) {
            // The implementation should never call onEvent()) if the event is already reported
            // through newer callback.
            if (mOffloadControlVersion > OFFLOAD_HAL_VERSION_HIDL_1_0) {
                Log.wtf(TAG, "onEvent(" + event + ") fired on HAL "
                        + halVerToString(mOffloadControlVersion));
            }
            handler.post(() -> {
                handleOnEvent(event);
            });
        }

        @Override
        public void onEvent_1_1(int event) {
            if (mOffloadControlVersion < OFFLOAD_HAL_VERSION_HIDL_1_1) {
                Log.wtf(TAG, "onEvent_1_1(" + event + ") fired on HAL "
                        + halVerToString(mOffloadControlVersion));
                return;
            }
            handler.post(() -> {
                handleOnEvent(event);
            });
        }

        @Override
        public void updateTimeout(NatTimeoutUpdate params) {
            handler.post(() -> {
                callback.onNatTimeoutUpdate(
                        networkProtocolToOsConstant(params.proto),
                        params.src.addr, uint16(params.src.port),
                        params.dst.addr, uint16(params.dst.port));
            });
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

    private static class CbResults {
        boolean mSuccess;
        String mErrMsg;

        @Override
        public String toString() {
            if (mSuccess) {
                return "ok";
            } else {
                return "fail: " + mErrMsg;
            }
        }
    }
}
