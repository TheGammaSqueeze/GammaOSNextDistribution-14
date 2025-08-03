/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "NetworkStackUtils-JNI"

#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <linux/filter.h>
#include <linux/if_arp.h>
#include <net/if.h>
#include <netinet/ether.h>
#include <netinet/icmp6.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <netinet/udp.h>
#include <stdlib.h>
#include <sys/system_properties.h>

#include <string>

#include <nativehelper/JNIHelp.h>
#include <netjniutils/netjniutils.h>

#include <android/log.h>
#include <bpf/BpfClassic.h>

namespace android {
constexpr const char NETWORKSTACKUTILS_PKG_NAME[] =
    "com/android/networkstack/util/NetworkStackUtils";

static const uint16_t kDhcpClientPort = 68;

static bool checkLenAndCopy(JNIEnv* env, const jbyteArray& addr, int len, void* dst) {
    if (env->GetArrayLength(addr) != len) {
        return false;
    }
    env->GetByteArrayRegion(addr, 0, len, reinterpret_cast<jbyte*>(dst));
    return true;
}

static void network_stack_utils_addArpEntry(JNIEnv *env, jclass clazz, jbyteArray ethAddr,
        jbyteArray ipv4Addr, jstring ifname, jobject javaFd) {
    arpreq req = {};
    sockaddr_in& netAddrStruct = *reinterpret_cast<sockaddr_in*>(&req.arp_pa);
    sockaddr& ethAddrStruct = req.arp_ha;

    ethAddrStruct.sa_family = ARPHRD_ETHER;
    if (!checkLenAndCopy(env, ethAddr, ETH_ALEN, ethAddrStruct.sa_data)) {
        jniThrowException(env, "java/io/IOException", "Invalid ethAddr length");
        return;
    }

    netAddrStruct.sin_family = AF_INET;
    if (!checkLenAndCopy(env, ipv4Addr, sizeof(in_addr), &netAddrStruct.sin_addr)) {
        jniThrowException(env, "java/io/IOException", "Invalid ipv4Addr length");
        return;
    }

    int ifLen = env->GetStringLength(ifname);
    // IFNAMSIZ includes the terminating NULL character
    if (ifLen >= IFNAMSIZ) {
        jniThrowException(env, "java/io/IOException", "ifname too long");
        return;
    }
    env->GetStringUTFRegion(ifname, 0, ifLen, req.arp_dev);

    req.arp_flags = ATF_COM;  // Completed entry (ha valid)
    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (fd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }
    // See also: man 7 arp
    if (ioctl(fd, SIOCSARP, &req)) {
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl error: %s", strerror(errno));
        return;
    }
}

// fd is a "socket(AF_PACKET, SOCK_RAW, ETH_P_IP)"
// which guarantees packets already have skb->protocol == htons(ETH_P_IP)
static void network_stack_utils_attachDhcpFilter(JNIEnv *env, jclass clazz, jobject javaFd) {
    static sock_filter filter_code[] = {
        // Check the protocol is UDP.
        BPF_LOAD_IPV4_U8(protocol),
        BPF2_REJECT_IF_NOT_EQUAL(IPPROTO_UDP),

        // Check this is not a fragment.
        BPF_LOAD_IPV4_BE16(frag_off),
        BPF2_REJECT_IF_ANY_MASKED_BITS_SET(IP_MF | IP_OFFMASK),

        // Get the IP header length.
        BPF_LOADX_NET_RELATIVE_IPV4_HLEN,

        // Check the destination port.
        BPF_LOAD_NETX_RELATIVE_DST_PORT,
        BPF2_REJECT_IF_NOT_EQUAL(kDhcpClientPort),

        BPF_ACCEPT,
    };
    const sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        jniThrowErrnoException(env, "setsockopt(SO_ATTACH_FILTER)", errno);
    }
}

// fd is a "socket(AF_PACKET, SOCK_RAW, ETH_P_IPV6)"
// which guarantees packets already have skb->protocol == htons(ETH_P_IPV6)
static void network_stack_utils_attachRaFilter(JNIEnv *env, jclass clazz, jobject javaFd) {
    static sock_filter filter_code[] = {
        BPF_LOADX_CONSTANT_IPV6_HLEN,

        // Check IPv6 Next Header is ICMPv6.
        BPF_LOAD_IPV6_U8(nexthdr),
        BPF2_REJECT_IF_NOT_EQUAL(IPPROTO_ICMPV6),

        // Check ICMPv6 type is Router Advertisement.
        BPF_LOAD_NETX_RELATIVE_ICMP_TYPE,
        BPF2_REJECT_IF_NOT_EQUAL(ND_ROUTER_ADVERT),

        BPF_ACCEPT,
    };
    static const sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        jniThrowErrnoException(env, "setsockopt(SO_ATTACH_FILTER)", errno);
    }
}

// TODO: Move all this filter code into libnetutils.
// fd is a "socket(AF_PACKET, SOCK_RAW, ETH_P_ALL)"
static void network_stack_utils_attachControlPacketFilter(
        JNIEnv *env, jclass clazz, jobject javaFd) {
    // Capture all:
    //     - ARPs
    //     - DHCPv4 packets
    //     - Router Advertisements & Solicitations
    //     - Neighbor Advertisements & Solicitations
    //
    // tcpdump:
    //     arp or
    //     '(ip and udp port 68)' or
    //     '(icmp6 and ip6[40] >= 133 and ip6[40] <= 136)'
    static sock_filter filter_code[] = {
        // Load the ethertype from skb->protocol
        BPF_LOAD_SKB_PROTOCOL,

        // Accept all ARP.
        // TODO: Figure out how to better filter ARPs on noisy networks.
        BPF2_ACCEPT_IF_EQUAL(ETHERTYPE_ARP),

        // If IPv4:  (otherwise jump to the 'IPv6 ...' below)
        BPF_JUMP_IF_NOT_EQUAL(ETHERTYPE_IP, 14),

        // Check the protocol is UDP.
        BPF_LOAD_IPV4_U8(protocol),
        BPF2_REJECT_IF_NOT_EQUAL(IPPROTO_UDP),

        // Check this is not a fragment.
        BPF_LOAD_IPV4_BE16(frag_off),
        BPF2_REJECT_IF_ANY_MASKED_BITS_SET(IP_MF | IP_OFFMASK),

        // Get the IP header length.
        BPF_LOADX_NET_RELATIVE_IPV4_HLEN,

        // Check the source port.
        BPF_LOAD_NETX_RELATIVE_SRC_PORT,
        BPF2_ACCEPT_IF_EQUAL(kDhcpClientPort),

        // Check the destination port.
        BPF_LOAD_NETX_RELATIVE_DST_PORT,
        BPF2_ACCEPT_IF_EQUAL(kDhcpClientPort),

        // Reject any other UDPv4
        BPF_REJECT,

        // IPv6 ...
        BPF2_REJECT_IF_NOT_EQUAL(ETHERTYPE_IPV6),
        // Assume standard, 40-byte, extension header-less ipv6 packet
        BPF_LOADX_CONSTANT_IPV6_HLEN,
        // ... check IPv6 Next Header is ICMPv6 (ignore fragments), ...
        BPF_LOAD_IPV6_U8(nexthdr),
        BPF2_REJECT_IF_NOT_EQUAL(IPPROTO_ICMPV6),
        // ... and check the ICMPv6 type is one of RS/RA/NS/NA.
        BPF_LOAD_NETX_RELATIVE_ICMP_TYPE,
        BPF3_REJECT_IF_NOT_IN_RANGE(ND_ROUTER_SOLICIT, ND_NEIGHBOR_ADVERT),

        BPF_ACCEPT,
    };
    static const sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        jniThrowErrnoException(env, "setsockopt(SO_ATTACH_FILTER)", errno);
    }
}

/*
 * JNI registration.
 */
static const JNINativeMethod gNetworkStackUtilsMethods[] = {
    /* name, signature, funcPtr */
    { "addArpEntry", "([B[BLjava/lang/String;Ljava/io/FileDescriptor;)V", (void*) network_stack_utils_addArpEntry },
    { "attachDhcpFilter", "(Ljava/io/FileDescriptor;)V", (void*) network_stack_utils_attachDhcpFilter },
    { "attachRaFilter", "(Ljava/io/FileDescriptor;)V", (void*) network_stack_utils_attachRaFilter },
    { "attachControlPacketFilter", "(Ljava/io/FileDescriptor;)V", (void*) network_stack_utils_attachControlPacketFilter },
};

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "ERROR: GetEnv failed");
        return JNI_ERR;
    }

    if (jniRegisterNativeMethods(env, NETWORKSTACKUTILS_PKG_NAME,
            gNetworkStackUtilsMethods, NELEM(gNetworkStackUtilsMethods)) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;

}
}; // namespace android
