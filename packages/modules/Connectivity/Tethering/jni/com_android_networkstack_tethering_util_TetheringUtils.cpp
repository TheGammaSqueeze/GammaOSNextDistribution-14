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

#include <errno.h>
#include <error.h>
#include <jni.h>
#include <linux/filter.h>
#include <linux/ipv6.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <netjniutils/netjniutils.h>
#include <net/if.h>
#include <netinet/ether.h>
#include <netinet/icmp6.h>
#include <sys/socket.h>
#include <stdio.h>

#include <bpf/BpfClassic.h>

namespace android {

static void throwSocketException(JNIEnv *env, const char* msg, int error) {
    jniThrowExceptionFmt(env, "java/net/SocketException", "%s: %s", msg, strerror(error));
}

static void com_android_networkstack_tethering_util_setupIcmpFilter(JNIEnv *env, jobject javaFd,
        uint32_t type) {
    sock_filter filter_code[] = {
        // Check header is ICMPv6.
        BPF_LOAD_IPV6_U8(nexthdr),
        BPF2_REJECT_IF_NOT_EQUAL(IPPROTO_ICMPV6),

        // Check ICMPv6 type.
        BPF_LOAD_NET_RELATIVE_U8(sizeof(ipv6hdr) + offsetof(icmp6_hdr, icmp6_type)),
        BPF2_REJECT_IF_NOT_EQUAL(type),

        BPF_ACCEPT,
    };

    const sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        throwSocketException(env, "setsockopt(SO_ATTACH_FILTER)", errno);
    }
}

static void com_android_networkstack_tethering_util_setupNaSocket(JNIEnv *env, jclass clazz,
        jobject javaFd) {
    com_android_networkstack_tethering_util_setupIcmpFilter(env, javaFd, ND_NEIGHBOR_ADVERT);
}

static void com_android_networkstack_tethering_util_setupNsSocket(JNIEnv *env, jclass clazz,
        jobject javaFd) {
    com_android_networkstack_tethering_util_setupIcmpFilter(env, javaFd, ND_NEIGHBOR_SOLICIT);
}

static void com_android_networkstack_tethering_util_setupRaSocket(JNIEnv *env, jclass clazz,
        jobject javaFd, jint ifIndex) {
    static const int kLinkLocalHopLimit = 255;

    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);

    // Set an ICMPv6 filter that only passes Router Solicitations.
    struct icmp6_filter rs_only;
    ICMP6_FILTER_SETBLOCKALL(&rs_only);
    ICMP6_FILTER_SETPASS(ND_ROUTER_SOLICIT, &rs_only);
    socklen_t len = sizeof(rs_only);
    if (setsockopt(fd, IPPROTO_ICMPV6, ICMP6_FILTER, &rs_only, len) != 0) {
        throwSocketException(env, "setsockopt(ICMP6_FILTER)", errno);
        return;
    }

    // Most/all of the rest of these options can be set via Java code, but
    // because we're here on account of setting an icmp6_filter go ahead
    // and do it all natively for now.

    // Set the multicast hoplimit to 255 (link-local only).
    int hops = kLinkLocalHopLimit;
    len = sizeof(hops);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &hops, len) != 0) {
        throwSocketException(env, "setsockopt(IPV6_MULTICAST_HOPS)", errno);
        return;
    }

    // Set the unicast hoplimit to 255 (link-local only).
    hops = kLinkLocalHopLimit;
    len = sizeof(hops);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &hops, len) != 0) {
        throwSocketException(env, "setsockopt(IPV6_UNICAST_HOPS)", errno);
        return;
    }

    // Explicitly disable multicast loopback.
    int off = 0;
    len = sizeof(off);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_LOOP, &off, len) != 0) {
        throwSocketException(env, "setsockopt(IPV6_MULTICAST_LOOP)", errno);
        return;
    }

    // Specify the IPv6 interface to use for outbound multicast.
    len = sizeof(ifIndex);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_IF, &ifIndex, len) != 0) {
        throwSocketException(env, "setsockopt(IPV6_MULTICAST_IF)", errno);
        return;
    }

    // Additional options to be considered:
    //     - IPV6_TCLASS
    //     - IPV6_RECVPKTINFO
    //     - IPV6_RECVHOPLIMIT

    // Bind to [::].
    const struct sockaddr_in6 sin6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 0,
            .sin6_flowinfo = 0,
            .sin6_addr = IN6ADDR_ANY_INIT,
            .sin6_scope_id = 0,
    };
    auto sa = reinterpret_cast<const struct sockaddr *>(&sin6);
    len = sizeof(sin6);
    if (bind(fd, sa, len) != 0) {
        throwSocketException(env, "bind(IN6ADDR_ANY)", errno);
        return;
    }

    // Join the all-routers multicast group, ff02::2%index.
    struct ipv6_mreq all_rtrs = {
        .ipv6mr_multiaddr = {{{0xff,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2}}},
        .ipv6mr_interface = ifIndex,
    };
    len = sizeof(all_rtrs);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_JOIN_GROUP, &all_rtrs, len) != 0) {
        throwSocketException(env, "setsockopt(IPV6_JOIN_GROUP)", errno);
        return;
    }
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "setupNaSocket", "(Ljava/io/FileDescriptor;)V",
        (void*) com_android_networkstack_tethering_util_setupNaSocket },
    { "setupNsSocket", "(Ljava/io/FileDescriptor;)V",
        (void*) com_android_networkstack_tethering_util_setupNsSocket },
    { "setupRaSocket", "(Ljava/io/FileDescriptor;I)V",
        (void*) com_android_networkstack_tethering_util_setupRaSocket },
};

int register_com_android_networkstack_tethering_util_TetheringUtils(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "com/android/networkstack/tethering/util/TetheringUtils",
            gMethods, NELEM(gMethods));
}

}; // namespace android
