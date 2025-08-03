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

package com.android.net.module.util.netlink;

import static android.net.util.SocketUtils.makeNetlinkSocketAddress;
import static android.system.OsConstants.AF_NETLINK;
import static android.system.OsConstants.EIO;
import static android.system.OsConstants.EPROTO;
import static android.system.OsConstants.ETIMEDOUT;
import static android.system.OsConstants.NETLINK_INET_DIAG;
import static android.system.OsConstants.NETLINK_ROUTE;
import static android.system.OsConstants.SOCK_CLOEXEC;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_RCVBUF;
import static android.system.OsConstants.SO_RCVTIMEO;
import static android.system.OsConstants.SO_SNDTIMEO;

import static com.android.net.module.util.netlink.NetlinkConstants.hexify;
import static com.android.net.module.util.netlink.NetlinkConstants.NLMSG_DONE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTNL_FAMILY_IP6MR;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;

import android.net.util.SocketUtils;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Utilities for netlink related class that may not be able to fit into a specific class.
 * @hide
 */
public class NetlinkUtils {
    private static final String TAG = "NetlinkUtils";
    /** Corresponds to enum from bionic/libc/include/netinet/tcp.h. */
    private static final int TCP_ESTABLISHED = 1;
    private static final int TCP_SYN_SENT = 2;
    private static final int TCP_SYN_RECV = 3;

    public static final int TCP_ALIVE_STATE_FILTER =
            (1 << TCP_ESTABLISHED) | (1 << TCP_SYN_SENT) | (1 << TCP_SYN_RECV);

    public static final int UNKNOWN_MARK = 0xffffffff;
    public static final int NULL_MASK = 0;

    // Initial mark value corresponds to the initValue in system/netd/include/Fwmark.h.
    public static final int INIT_MARK_VALUE = 0;
    // Corresponds to enum definition in bionic/libc/kernel/uapi/linux/inet_diag.h
    public static final int INET_DIAG_INFO = 2;
    public static final int INET_DIAG_MARK = 15;

    public static final long IO_TIMEOUT_MS = 300L;

    public static final int DEFAULT_RECV_BUFSIZE = 8 * 1024;
    public static final int SOCKET_RECV_BUFSIZE = 64 * 1024;
    public static final int SOCKET_DUMP_RECV_BUFSIZE = 128 * 1024;

    /**
     * Return whether the input ByteBuffer contains enough remaining bytes for
     * {@code StructNlMsgHdr}.
     */
    public static boolean enoughBytesRemainForValidNlMsg(@NonNull final ByteBuffer bytes) {
        return bytes.remaining() >= StructNlMsgHdr.STRUCT_SIZE;
    }

    /**
     * Parse netlink error message
     *
     * @param bytes byteBuffer to parse netlink error message
     * @return NetlinkErrorMessage if bytes contains valid NetlinkErrorMessage, else {@code null}
     */
    @Nullable
    private static NetlinkErrorMessage parseNetlinkErrorMessage(ByteBuffer bytes) {
        final StructNlMsgHdr nlmsghdr = StructNlMsgHdr.parse(bytes);
        if (nlmsghdr == null || nlmsghdr.nlmsg_type != NetlinkConstants.NLMSG_ERROR) {
            return null;
        }

        final int messageLength = NetlinkConstants.alignedLengthOf(nlmsghdr.nlmsg_len);
        final int payloadLength = messageLength - StructNlMsgHdr.STRUCT_SIZE;
        if (payloadLength < 0 || payloadLength > bytes.remaining()) {
            // Malformed message or runt buffer.  Pretend the buffer was consumed.
            bytes.position(bytes.limit());
            return null;
        }

        return NetlinkErrorMessage.parse(nlmsghdr, bytes);
    }

    /**
     * Receive netlink ack message and check error
     *
     * @param fd fd to read netlink message
     */
    public static void receiveNetlinkAck(final FileDescriptor fd)
            throws InterruptedIOException, ErrnoException {
        final ByteBuffer bytes = recvMessage(fd, DEFAULT_RECV_BUFSIZE, IO_TIMEOUT_MS);
        // recvMessage() guaranteed to not return null if it did not throw.
        final NetlinkErrorMessage response = parseNetlinkErrorMessage(bytes);
        if (response != null && response.getNlMsgError() != null) {
            final int errno = response.getNlMsgError().error;
            if (errno != 0) {
                // TODO: consider ignoring EINVAL (-22), which appears to be
                // normal when probing a neighbor for which the kernel does
                // not already have / no longer has a link layer address.
                Log.e(TAG, "receiveNetlinkAck, errmsg=" + response.toString());
                // Note: convert kernel errnos (negative) into userspace errnos (positive).
                throw new ErrnoException(response.toString(), Math.abs(errno));
            }
        } else {
            final String errmsg;
            if (response == null) {
                bytes.position(0);
                errmsg = "raw bytes: " + NetlinkConstants.hexify(bytes);
            } else {
                errmsg = response.toString();
            }
            Log.e(TAG, "receiveNetlinkAck, errmsg=" + errmsg);
            throw new ErrnoException(errmsg, EPROTO);
        }
    }

    /**
     * Send one netlink message to kernel via netlink socket.
     *
     * @param nlProto netlink protocol type.
     * @param msg the raw bytes of netlink message to be sent.
     */
    public static void sendOneShotKernelMessage(int nlProto, byte[] msg) throws ErrnoException {
        final String errPrefix = "Error in NetlinkSocket.sendOneShotKernelMessage";
        final FileDescriptor fd = netlinkSocketForProto(nlProto, SOCKET_RECV_BUFSIZE);

        try {
            connectToKernel(fd);
            sendMessage(fd, msg, 0, msg.length, IO_TIMEOUT_MS);
            receiveNetlinkAck(fd);
        } catch (InterruptedIOException e) {
            Log.e(TAG, errPrefix, e);
            throw new ErrnoException(errPrefix, ETIMEDOUT, e);
        } catch (SocketException e) {
            Log.e(TAG, errPrefix, e);
            throw new ErrnoException(errPrefix, EIO, e);
        } finally {
            closeSocketQuietly(fd);
        }
    }

    /**
     * Send an RTM_NEWADDR message to kernel to add or update an IP address.
     *
     * @param ifIndex interface index.
     * @param ip IP address to be added.
     * @param prefixlen IP address prefix length.
     * @param flags IP address flags.
     * @param scope IP address scope.
     * @param preferred The preferred lifetime of IP address.
     * @param valid The valid lifetime of IP address.
     */
    public static boolean sendRtmNewAddressRequest(int ifIndex, @NonNull final InetAddress ip,
            short prefixlen, int flags, byte scope, long preferred, long valid) {
        Objects.requireNonNull(ip, "IP address to be added should not be null.");
        final byte[] msg = RtNetlinkAddressMessage.newRtmNewAddressMessage(1 /* seqNo*/, ip,
                prefixlen, flags, scope, ifIndex, preferred, valid);
        try {
            NetlinkUtils.sendOneShotKernelMessage(NETLINK_ROUTE, msg);
            return true;
        } catch (ErrnoException e) {
            Log.e(TAG, "Fail to send RTM_NEWADDR to add " + ip.getHostAddress(), e);
            return false;
        }
    }

    /**
     * Send an RTM_DELADDR message to kernel to delete an IPv6 address.
     *
     * @param ifIndex interface index.
     * @param ip IPv6 address to be deleted.
     * @param prefixlen IPv6 address prefix length.
     */
    public static boolean sendRtmDelAddressRequest(int ifIndex, final Inet6Address ip,
            short prefixlen) {
        Objects.requireNonNull(ip, "IPv6 address to be deleted should not be null.");
        final byte[] msg = RtNetlinkAddressMessage.newRtmDelAddressMessage(1 /* seqNo*/, ip,
                prefixlen, ifIndex);
        try {
            NetlinkUtils.sendOneShotKernelMessage(NETLINK_ROUTE, msg);
            return true;
        } catch (ErrnoException e) {
            Log.e(TAG, "Fail to send RTM_DELADDR to delete " + ip.getHostAddress(), e);
            return false;
        }
    }

    /**
     * Create netlink socket with the given netlink protocol type and buffersize.
     *
     * @param nlProto the netlink protocol
     * @param bufferSize the receive buffer size to set when the value is not 0
     *
     * @return fd the fileDescriptor of the socket.
     * @throws ErrnoException if the FileDescriptor not connect to be created successfully
     */
    public static FileDescriptor netlinkSocketForProto(int nlProto, int bufferSize)
            throws ErrnoException {
        final FileDescriptor fd = Os.socket(AF_NETLINK, SOCK_DGRAM | SOCK_CLOEXEC, nlProto);
        if (bufferSize > 0) {
            Os.setsockoptInt(fd, SOL_SOCKET, SO_RCVBUF, bufferSize);
        }
        return fd;
    }

    /**
     * Create netlink socket with the given netlink protocol type. Receive buffer size is not set.
     *
     * @param nlProto the netlink protocol
     *
     * @return fd the fileDescriptor of the socket.
     * @throws ErrnoException if the FileDescriptor not connect to be created successfully
     */
    public static FileDescriptor netlinkSocketForProto(int nlProto)
            throws ErrnoException {
        return netlinkSocketForProto(nlProto, 0);
    }

    /**
     * Construct a netlink inet_diag socket.
     */
    public static FileDescriptor createNetLinkInetDiagSocket() throws ErrnoException {
        return netlinkSocketForProto(NETLINK_INET_DIAG);
    }

    /**
     * Connect the given file descriptor to the Netlink interface to the kernel.
     *
     * The fd must be a SOCK_DGRAM socket : create it with {@link #netlinkSocketForProto}
     *
     * @throws ErrnoException if the {@code fd} could not connect to kernel successfully
     * @throws SocketException if there is an error accessing a socket.
     */
    public static void connectToKernel(@NonNull FileDescriptor fd)
            throws ErrnoException, SocketException {
        Os.connect(fd, makeNetlinkSocketAddress(0, 0));
    }

    private static void checkTimeout(long timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Negative timeouts not permitted");
        }
    }

    /**
     * Wait up to |timeoutMs| (or until underlying socket error) for a
     * netlink message of at most |bufsize| size.
     *
     * Multi-threaded calls with different timeouts will cause unexpected results.
     */
    public static ByteBuffer recvMessage(FileDescriptor fd, int bufsize, long timeoutMs)
            throws ErrnoException, IllegalArgumentException, InterruptedIOException {
        checkTimeout(timeoutMs);

        Os.setsockoptTimeval(fd, SOL_SOCKET, SO_RCVTIMEO, StructTimeval.fromMillis(timeoutMs));

        final ByteBuffer byteBuffer = ByteBuffer.allocate(bufsize);
        final int length = Os.read(fd, byteBuffer);
        if (length == bufsize) {
            Log.w(TAG, "maximum read");
        }
        byteBuffer.position(0);
        byteBuffer.limit(length);
        byteBuffer.order(ByteOrder.nativeOrder());
        return byteBuffer;
    }

    /**
     * Send a message to a peer to which this socket has previously connected.
     *
     * This waits at most |timeoutMs| milliseconds for the send to complete, will get the exception
     * if it times out.
     */
    public static int sendMessage(
            FileDescriptor fd, byte[] bytes, int offset, int count, long timeoutMs)
            throws ErrnoException, IllegalArgumentException, InterruptedIOException {
        checkTimeout(timeoutMs);
        Os.setsockoptTimeval(fd, SOL_SOCKET, SO_SNDTIMEO, StructTimeval.fromMillis(timeoutMs));
        return Os.write(fd, bytes, offset, count);
    }

    private static final long CLOCK_TICKS_PER_SECOND = Os.sysconf(OsConstants._SC_CLK_TCK);

    /**
     * Convert the system time in clock ticks(clock_t type in times(), not in clock()) to
     * milliseconds. times() clock_t ticks at the kernel's USER_HZ (100) while clock() clock_t
     * ticks at CLOCKS_PER_SEC (1000000).
     *
     * See the NOTES on https://man7.org/linux/man-pages/man2/times.2.html for the difference
     * of clock_t used in clock() and times().
     */
    public static long ticksToMilliSeconds(int intClockTicks) {
        final long longClockTicks = intClockTicks & 0xffffffffL;
        return (longClockTicks * 1000) / CLOCK_TICKS_PER_SECOND;
    }

    private NetlinkUtils() {}

    private static <T extends NetlinkMessage> void getAndProcessNetlinkDumpMessagesWithFd(
            FileDescriptor fd, byte[] dumpRequestMessage, int nlFamily, Class<T> msgClass,
            Consumer<T> func)
            throws SocketException, InterruptedIOException, ErrnoException {
        // connecToKernel throws ErrnoException and SocketException, should be handled by caller
        connectToKernel(fd);

        // sendMessage throws InterruptedIOException and ErrnoException,
        // should be handled by caller
        sendMessage(fd, dumpRequestMessage, 0, dumpRequestMessage.length, IO_TIMEOUT_MS);

        while (true) {
            // recvMessage throws ErrnoException, InterruptedIOException
            // should be handled by caller
            final ByteBuffer buf = recvMessage(
                    fd, NetlinkUtils.DEFAULT_RECV_BUFSIZE, IO_TIMEOUT_MS);

            while (buf.remaining() > 0) {
                final int position = buf.position();
                final NetlinkMessage nlMsg = NetlinkMessage.parse(buf, nlFamily);
                if (nlMsg == null) {
                    // Move to the position where parse started for error log.
                    buf.position(position);
                    Log.e(TAG, "Failed to parse netlink message: " + hexify(buf));
                    break;
                }

                if (nlMsg.getHeader().nlmsg_type == NLMSG_DONE) {
                    return;
                }

                if (!msgClass.isInstance(nlMsg)) {
                    Log.wtf(TAG, "Received unexpected netlink message: " + nlMsg);
                    continue;
                }

                final T msg = (T) nlMsg;
                func.accept(msg);
            }
        }
    }
    /**
     * Sends a netlink dump request and processes the returned dump messages
     *
     * @param <T> extends NetlinkMessage
     * @param dumpRequestMessage netlink dump request message to be sent
     * @param nlFamily netlink family
     * @param msgClass expected class of the netlink message
     * @param func function defined by caller to handle the dump messages
     * @throws SocketException when fails to connect socket to kernel
     * @throws InterruptedIOException when fails to read the dumpFd
     * @throws ErrnoException when fails to create dump fd, send dump request
     *                        or receive messages
     */
    public static <T extends NetlinkMessage> void getAndProcessNetlinkDumpMessages(
            byte[] dumpRequestMessage, int nlFamily, Class<T> msgClass,
            Consumer<T> func)
            throws SocketException, InterruptedIOException, ErrnoException {
        // Create socket
        final FileDescriptor fd = netlinkSocketForProto(nlFamily, SOCKET_DUMP_RECV_BUFSIZE);
        try {
            getAndProcessNetlinkDumpMessagesWithFd(fd, dumpRequestMessage, nlFamily,
                    msgClass, func);
        } finally {
            closeSocketQuietly(fd);
        }
    }

    /**
     * Construct a RTM_GETROUTE message for dumping multicast IPv6 routes from kernel.
     */
    private static byte[] newIpv6MulticastRouteDumpRequest() {
        final StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_type = NetlinkConstants.RTM_GETROUTE;
        nlmsghdr.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP;
        final short shortZero = 0;

        // family must be RTNL_FAMILY_IP6MR to dump IPv6 multicast routes.
        // dstLen, srcLen, tos and scope must be zero in FIB dump request.
        // protocol, flags must be 0, and type must be RTN_MULTICAST (if not 0) for multicast
        // dump request.
        // table or RTA_TABLE attributes can be used to dump a specific routing table.
        // RTA_OIF attribute can be used to dump only routes containing given oif.
        // Here no attributes are set so the kernel can return all multicast routes.
        final StructRtMsg rtMsg =
                new StructRtMsg(RTNL_FAMILY_IP6MR /* family */, shortZero /* dstLen */,
                        shortZero /* srcLen */, shortZero /* tos */, shortZero /* table */,
                        shortZero /* protocol */, shortZero /* scope */, shortZero /* type */,
                        0L /* flags */);
        final RtNetlinkRouteMessage msg =
            new RtNetlinkRouteMessage(nlmsghdr, rtMsg);

        final int spaceRequired = StructNlMsgHdr.STRUCT_SIZE + StructRtMsg.STRUCT_SIZE;
        nlmsghdr.nlmsg_len = spaceRequired;
        final byte[] bytes = new byte[NetlinkConstants.alignedLengthOf(spaceRequired)];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());
        msg.pack(byteBuffer);
        return bytes;
     }

    /**
     * Get the list of IPv6 multicast route messages from kernel.
     */
    public static List<RtNetlinkRouteMessage> getIpv6MulticastRoutes() {
        final byte[] dumpMsg = newIpv6MulticastRouteDumpRequest();
        List<RtNetlinkRouteMessage> routes = new ArrayList<>();
        Consumer<RtNetlinkRouteMessage> handleNlDumpMsg = (msg) -> {
            if (msg.getRtmFamily() == RTNL_FAMILY_IP6MR) {
                // Sent rtmFamily RTNL_FAMILY_IP6MR in dump request to make sure ipv6
                // multicast routes are included in netlink reply messages, the kernel
                // may also reply with other kind of routes, so we filter them out here.
                routes.add(msg);
            }
        };
        try {
            NetlinkUtils.<RtNetlinkRouteMessage>getAndProcessNetlinkDumpMessages(
                    dumpMsg, NETLINK_ROUTE, RtNetlinkRouteMessage.class,
                    handleNlDumpMsg);
        } catch (SocketException | InterruptedIOException | ErrnoException e) {
            Log.e(TAG, "Failed to dump multicast routes");
            return routes;
        }

        return routes;
    }

    private static void closeSocketQuietly(final FileDescriptor fd) {
        try {
            SocketUtils.closeSocket(fd);
        } catch (IOException e) {
            // Nothing we can do here
        }
    }
}
