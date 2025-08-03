/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.net.module.util.netlink.NetlinkUtils.SOCKET_RECV_BUFSIZE;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.net.util.SocketUtils;
import android.os.Handler;
import android.os.NativeHandle;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNfGenMsg;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Capture tethering dependencies, for injection.
 *
 * @hide
 */
public class OffloadHardwareInterface {
    private static final String TAG = OffloadHardwareInterface.class.getSimpleName();
    private static final String YIELDS = " -> ";
    // Change this value to control whether tether offload is enabled or
    // disabled by default in the absence of an explicit Settings value.
    // See accompanying unittest to distinguish 0 from non-0 values.
    private static final int DEFAULT_TETHER_OFFLOAD_DISABLED = 0;
    private static final String NO_INTERFACE_NAME = "";
    private static final String NO_IPV4_ADDRESS = "";
    private static final String NO_IPV4_GATEWAY = "";
    // Reference kernel/uapi/linux/netfilter/nfnetlink_compat.h
    public static final int NF_NETLINK_CONNTRACK_NEW = 1;
    public static final int NF_NETLINK_CONNTRACK_UPDATE = 2;
    public static final int NF_NETLINK_CONNTRACK_DESTROY = 4;
    // Reference libnetfilter_conntrack/linux_nfnetlink_conntrack.h
    public static final short NFNL_SUBSYS_CTNETLINK = 1;
    public static final short IPCTNL_MSG_CT_NEW = 0;
    public static final short IPCTNL_MSG_CT_GET = 1;

    private final long NETLINK_MESSAGE_TIMEOUT_MS = 500;

    private final Handler mHandler;
    private final SharedLog mLog;
    private final Dependencies mDeps;
    private IOffloadHal mIOffload;

    // TODO: Use major-minor version control to prevent from defining new constants.
    static final int OFFLOAD_HAL_VERSION_NONE = 0;
    static final int OFFLOAD_HAL_VERSION_HIDL_1_0 = 1;
    static final int OFFLOAD_HAL_VERSION_HIDL_1_1 = 2;
    static final int OFFLOAD_HAL_VERSION_AIDL = 3;
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "OFFLOAD_HAL_VERSION_", value = {
            OFFLOAD_HAL_VERSION_NONE,
            OFFLOAD_HAL_VERSION_HIDL_1_0,
            OFFLOAD_HAL_VERSION_HIDL_1_1,
            OFFLOAD_HAL_VERSION_AIDL,
    })
    public @interface OffloadHalVersion {}

    @NonNull
    static String halVerToString(int version) {
        switch(version) {
            case OFFLOAD_HAL_VERSION_HIDL_1_0:
                return "HIDL 1.0";
            case OFFLOAD_HAL_VERSION_HIDL_1_1:
                return "HIDL 1.1";
            case OFFLOAD_HAL_VERSION_AIDL:
                return "AIDL";
            case OFFLOAD_HAL_VERSION_NONE:
                return "None";
            default:
                throw new IllegalArgumentException("Unsupported version int " + version);
        }
    }

    private OffloadHalCallback mOffloadHalCallback;

    /** The callback to notify status of offload management process. */
    public static class OffloadHalCallback {
        /** Offload started. */
        public void onStarted() {}
        /**
         * Offload stopped because an error has occurred in lower layer.
         */
        public void onStoppedError() {}
        /**
         * Offload stopped because the device has moved to a bearer on which hardware offload is
         * not supported. Subsequent calls to setUpstreamParameters and add/removeDownstream will
         * likely fail and cannot be presumed to be saved inside of the hardware management process.
         * Upon receiving #onSupportAvailable(), the caller should reprogram the hardware to begin
         * offload again.
         */
        public void onStoppedUnsupported() {}
        /** Indicate that offload is able to proivde support for this time. */
        public void onSupportAvailable() {}
        /** Offload stopped because of usage limit reached. */
        public void onStoppedLimitReached() {}
        /** Indicate that data warning quota is reached. */
        public void onWarningReached() {}

        /** Indicate to update NAT timeout. */
        public void onNatTimeoutUpdate(int proto,
                                       String srcAddr, int srcPort,
                                       String dstAddr, int dstPort) {}
    }

    /** The object which records Tx/Rx forwarded bytes. */
    public static class ForwardedStats {
        public long rxBytes;
        public long txBytes;

        public ForwardedStats() {
            rxBytes = 0;
            txBytes = 0;
        }

        @VisibleForTesting
        public ForwardedStats(long rxBytes, long txBytes) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }

        /** Add Tx/Rx bytes. */
        public void add(ForwardedStats other) {
            rxBytes += other.rxBytes;
            txBytes += other.txBytes;
        }

        /** Returns the string representation of this object. */
        public String toString() {
            return String.format("rx:%s tx:%s", rxBytes, txBytes);
        }
    }

    public OffloadHardwareInterface(Handler h, SharedLog log) {
        this(h, log, new Dependencies(h, log));
    }

    OffloadHardwareInterface(Handler h, SharedLog log, Dependencies deps) {
        mHandler = h;
        mLog = log.forSubComponent(TAG);
        mDeps = deps;
    }

    /** Capture OffloadHardwareInterface dependencies, for injection. */
    static class Dependencies {
        private final Handler mHandler;
        private final SharedLog mLog;

        Dependencies(Handler handler, SharedLog log) {
            mHandler = handler;
            mLog = log;
        }

        public IOffloadHal getOffload() {
            // Prefer AIDL implementation if its service is declared.
            IOffloadHal hal = OffloadHalAidlImpl.getIOffloadHal(mHandler, mLog);
            if (hal == null) {
                hal = OffloadHalHidlImpl.getIOffloadHal(mHandler, mLog);
            }
            return hal;
        }

        public NativeHandle createConntrackSocket(final int groups) {
            final FileDescriptor fd;
            try {
                fd = NetlinkUtils.netlinkSocketForProto(OsConstants.NETLINK_NETFILTER,
                        SOCKET_RECV_BUFSIZE);
            } catch (ErrnoException e) {
                mLog.e("Unable to create conntrack socket " + e);
                return null;
            }

            final SocketAddress sockAddr = SocketUtils.makeNetlinkSocketAddress(0, groups);
            try {
                Os.bind(fd, sockAddr);
            } catch (ErrnoException | SocketException e) {
                mLog.e("Unable to bind conntrack socket for groups " + groups + " error: " + e);
                try {
                    SocketUtils.closeSocket(fd);
                } catch (IOException ie) {
                    // Nothing we can do here
                }
                return null;
            }
            try {
                Os.connect(fd, sockAddr);
            } catch (ErrnoException | SocketException e) {
                mLog.e("connect to kernel fail for groups " + groups + " error: " + e);
                try {
                    SocketUtils.closeSocket(fd);
                } catch (IOException ie) {
                    // Nothing we can do here
                }
                return null;
            }

            return new NativeHandle(fd, true);
        }
    }

    /** Get default value indicating whether offload is supported. */
    public int getDefaultTetherOffloadDisabled() {
        return DEFAULT_TETHER_OFFLOAD_DISABLED;
    }

    @VisibleForTesting
    void sendIpv4NfGenMsg(@NonNull NativeHandle handle, short type, short flags) {
        final int length = StructNlMsgHdr.STRUCT_SIZE + StructNfGenMsg.STRUCT_SIZE;
        final byte[] msg = new byte[length];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(msg);
        byteBuffer.order(ByteOrder.nativeOrder());

        final StructNlMsgHdr nlh = new StructNlMsgHdr();
        nlh.nlmsg_len = length;
        nlh.nlmsg_type = type;
        nlh.nlmsg_flags = flags;
        nlh.nlmsg_seq = 0;
        nlh.pack(byteBuffer);

        // Header needs to be added to buffer since a generic netlink request is being sent.
        final StructNfGenMsg nfh = new StructNfGenMsg((byte) OsConstants.AF_INET);
        nfh.pack(byteBuffer);

        try {
            NetlinkUtils.sendMessage(handle.getFileDescriptor(), msg, 0 /* offset */, length,
                                      NETLINK_MESSAGE_TIMEOUT_MS);
        } catch (ErrnoException | InterruptedIOException e) {
            mLog.e("Unable to send netfilter message, error: " + e);
        }
    }

    @VisibleForTesting
    void requestSocketDump(NativeHandle handle) {
        sendIpv4NfGenMsg(handle, (short) ((NFNL_SUBSYS_CTNETLINK << 8) | IPCTNL_MSG_CT_GET),
                (short) (NLM_F_REQUEST | NLM_F_DUMP));
    }

    private void maybeCloseFdInNativeHandles(final NativeHandle... handles) {
        for (NativeHandle h : handles) {
            if (h == null) continue;
            try {
                h.close();
            } catch (IOException | IllegalStateException e) {
                // IllegalStateException means fd is already closed, do nothing here.
                // Also nothing we can do if IOException.
            }
        }
    }

    private int initWithHandles(NativeHandle h1, NativeHandle h2) {
        if (h1 == null || h2 == null) {
            // Set mIOffload to null has two purposes:
            // 1. NativeHandles can be closed after initWithHandles() fails
            // 2. Prevent mIOffload.stopOffload() to be called in stopOffload()
            mIOffload = null;
            mLog.e("Failed to create socket.");
            return OFFLOAD_HAL_VERSION_NONE;
        }

        requestSocketDump(h1);
        if (!mIOffload.initOffload(h1, h2, mOffloadHalCallback)) {
            mLog.e("Failed to initialize offload.");
            return OFFLOAD_HAL_VERSION_NONE;
        }

        return mIOffload.getVersion();
    }

    /**
     * Initialize the tethering offload HAL.
     *
     * @return one of {@code OFFLOAD_HAL_VERSION_*} represents the HAL version, or
     *         {@link #OFFLOAD_HAL_VERSION_NONE} if failed.
     */
    public int initOffload(OffloadHalCallback offloadCb) {
        if (mIOffload == null) {
            mIOffload = mDeps.getOffload();
            if (mIOffload == null) {
                mLog.i("No tethering offload HAL service found.");
                return OFFLOAD_HAL_VERSION_NONE;
            }
            mLog.i("Tethering offload version "
                    + halVerToString(mIOffload.getVersion()) + " is supported.");
        }

        // Per the IOffload definition:
        //
        // h1    provides a file descriptor bound to the following netlink groups
        //       (NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY).
        //
        // h2    provides a file descriptor bound to the following netlink groups
        //       (NF_NETLINK_CONNTRACK_UPDATE | NF_NETLINK_CONNTRACK_DESTROY).
        final NativeHandle h1 = mDeps.createConntrackSocket(
                NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY);
        final NativeHandle h2 = mDeps.createConntrackSocket(
                NF_NETLINK_CONNTRACK_UPDATE | NF_NETLINK_CONNTRACK_DESTROY);

        mOffloadHalCallback = offloadCb;
        final int version = initWithHandles(h1, h2);

        // Explicitly close FDs for HIDL or when mIOffload is null (cleared in initWithHandles).
        // AIDL will pass the original FDs to the service, they shouldn't be closed here.
        if (mIOffload == null || mIOffload.getVersion() < OFFLOAD_HAL_VERSION_AIDL) {
            maybeCloseFdInNativeHandles(h1, h2);
        }
        return version;
    }

    /** Stop the tethering offload HAL. */
    public void stopOffload() {
        if (mIOffload != null) {
            if (!mIOffload.stopOffload()) {
                mLog.e("Failed to stop offload.");
            }
        }
        mIOffload = null;
        mOffloadHalCallback = null;
    }

    /** Get Tx/Rx usage from last query. */
    public ForwardedStats getForwardedStats(String upstream) {
        return mIOffload.getForwardedStats(upstream);
    }

    /** Set local prefixes to offload management process. */
    public boolean setLocalPrefixes(ArrayList<String> localPrefixes) {
        return mIOffload.setLocalPrefixes(localPrefixes);
    }

    /** Set data limit value to offload management process. */
    public boolean setDataLimit(String iface, long limit) {
        return mIOffload.setDataLimit(iface, limit);
    }

    /** Set data warning and limit value to offload management process. */
    public boolean setDataWarningAndLimit(String iface, long warning, long limit) {
        if (mIOffload.getVersion() < OFFLOAD_HAL_VERSION_HIDL_1_1) {
            throw new UnsupportedOperationException(
                    "setDataWarningAndLimit is not supported below HAL V1.1");
        }
        return mIOffload.setDataWarningAndLimit(iface, warning, limit);
    }

    /** Set upstream parameters to offload management process. */
    public boolean setUpstreamParameters(
            String iface, String v4addr, String v4gateway, ArrayList<String> v6gws) {
        iface = (iface != null) ? iface : NO_INTERFACE_NAME;
        v4addr = (v4addr != null) ? v4addr : NO_IPV4_ADDRESS;
        v4gateway = (v4gateway != null) ? v4gateway : NO_IPV4_GATEWAY;
        v6gws = (v6gws != null) ? v6gws : new ArrayList<>();
        return mIOffload.setUpstreamParameters(iface, v4addr, v4gateway, v6gws);
    }

    /** Add downstream prefix to offload management process. */
    public boolean addDownstream(String ifname, String prefix) {
        return  mIOffload.addDownstream(ifname, prefix);
    }

    /** Remove downstream prefix from offload management process. */
    public boolean removeDownstream(String ifname, String prefix) {
        return  mIOffload.removeDownstream(ifname, prefix);
    }
}
