// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#define LOG_TAG "clatutils"

#include "libclat/clatutils.h"

#include <errno.h>
#include <linux/filter.h>
#include <linux/if_packet.h>
#include <linux/if_tun.h>
#include <log/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <bpf/BpfClassic.h>

extern "C" {
#include "checksum.h"
}

namespace android {
namespace net {
namespace clat {

bool isIpv4AddressFree(const in_addr_t addr) {
    const int s = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (s == -1) return 0;

    // Attempt to connect to the address. If the connection succeeds and getsockname returns the
    // same then the address is already assigned to the system and we can't use it.
    struct sockaddr_in sin = {
            .sin_family = AF_INET,
            .sin_port = htons(53),
            .sin_addr = {addr},
    };
    socklen_t len = sizeof(sin);
    const bool inuse = !connect(s, (struct sockaddr*)&sin, sizeof(sin)) &&
                       !getsockname(s, (struct sockaddr*)&sin, &len) &&
                       len == (socklen_t)sizeof(sin) &&
                       sin.sin_addr.s_addr == addr;

    close(s);
    return !inuse;
}

// Picks a free IPv4 address, starting from ip and trying all addresses in the prefix in order.
//   ip        - the IP address from the configuration file
//   prefixlen - the length of the prefix from which addresses may be selected.
//   returns: the IPv4 address, or INADDR_NONE if no addresses were available
in_addr_t selectIpv4Address(const in_addr ip, const int16_t prefixlen) {
    return selectIpv4AddressInternal(ip, prefixlen, isIpv4AddressFree);
}

// Only allow testing to use this function directly. Otherwise call selectIpv4Address(ip, pfxlen)
// which has applied valid isIpv4AddressFree function pointer.
in_addr_t selectIpv4AddressInternal(const in_addr ip, const int16_t prefixlen,
                                    const isIpv4AddrFreeFn isIpv4AddressFreeFunc) {
    // Impossible! Only test allows to apply fn.
    if (isIpv4AddressFreeFunc == nullptr) return INADDR_NONE;

    // Don't accept prefixes that are too large because we scan addresses one by one.
    if (prefixlen < 16 || prefixlen > 32) return INADDR_NONE;

    // All these are in host byte order.
    const uint32_t mask = 0xffffffff >> (32 - prefixlen) << (32 - prefixlen);
    uint32_t ipv4 = ntohl(ip.s_addr);
    const uint32_t first_ipv4 = ipv4;
    const uint32_t prefix = ipv4 & mask;

    // Pick the first IPv4 address in the pool, wrapping around if necessary.
    // So, for example, 192.0.0.4 -> 192.0.0.5 -> 192.0.0.6 -> 192.0.0.7 -> 192.0.0.0.
    do {
        if (isIpv4AddressFreeFunc(htonl(ipv4))) return htonl(ipv4);
        ipv4 = prefix | ((ipv4 + 1) & ~mask);
    } while (ipv4 != first_ipv4);

    return INADDR_NONE;
}

// Alters the bits in the IPv6 address to make them checksum neutral with v4 and nat64Prefix.
void makeChecksumNeutral(in6_addr* const v6, const in_addr v4, const in6_addr& nat64Prefix) {
    // Fill last 8 bytes of IPv6 address with random bits.
    arc4random_buf(&v6->s6_addr[8], 8);

    // Make the IID checksum-neutral. That is, make it so that:
    //   checksum(Local IPv4 | Remote IPv4) = checksum(Local IPv6 | Remote IPv6)
    // in other words (because remote IPv6 = NAT64 prefix | Remote IPv4):
    //   checksum(Local IPv4) = checksum(Local IPv6 | NAT64 prefix)
    // Do this by adjusting the two bytes in the middle of the IID.

    uint16_t middlebytes = (v6->s6_addr[11] << 8) + v6->s6_addr[12];

    uint32_t c1 = ip_checksum_add(0, &v4, sizeof(v4));
    uint32_t c2 = ip_checksum_add(0, &nat64Prefix, sizeof(nat64Prefix)) +
                  ip_checksum_add(0, v6, sizeof(*v6));

    uint16_t delta = ip_checksum_adjust(middlebytes, c1, c2);
    v6->s6_addr[11] = delta >> 8;
    v6->s6_addr[12] = delta & 0xff;
}

// Picks a random interface ID that is checksum neutral with the IPv4 address and the NAT64 prefix.
int generateIpv6Address(const char* const iface, const in_addr v4, const in6_addr& nat64Prefix,
                        in6_addr* const v6, const uint32_t mark) {
    const int s = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (s == -1) return -errno;

    // Socket's mark affects routing decisions (network selection)
    // An fwmark is necessary for clat to bypass the VPN during initialization.
    if (setsockopt(s, SOL_SOCKET, SO_MARK, &mark, sizeof(mark))) {
        const int err = errno;
        ALOGE("setsockopt(SOL_SOCKET, SO_MARK) failed: %s", strerror(err));
        close(s);
        return -err;
    }

    if (setsockopt(s, SOL_SOCKET, SO_BINDTODEVICE, iface, strlen(iface) + 1)) {
        const int err = errno;
        ALOGE("setsockopt(SOL_SOCKET, SO_BINDTODEVICE, '%s') failed: %s", iface, strerror(err));
        close(s);
        return -err;
    }

    sockaddr_in6 sin6 = {.sin6_family = AF_INET6, .sin6_addr = nat64Prefix};
    if (connect(s, reinterpret_cast<struct sockaddr*>(&sin6), sizeof(sin6))) {
        close(s);
        return -errno;
    }

    socklen_t len = sizeof(sin6);
    if (getsockname(s, reinterpret_cast<struct sockaddr*>(&sin6), &len)) {
        close(s);
        return -errno;
    }

    *v6 = sin6.sin6_addr;

    if (IN6_IS_ADDR_UNSPECIFIED(v6) || IN6_IS_ADDR_LOOPBACK(v6) || IN6_IS_ADDR_LINKLOCAL(v6) ||
        IN6_IS_ADDR_SITELOCAL(v6) || IN6_IS_ADDR_ULA(v6)) {
        close(s);
        return -ENETUNREACH;
    }

    makeChecksumNeutral(v6, v4, nat64Prefix);
    close(s);

    return 0;
}

int detect_mtu(const struct in6_addr* const plat_subnet, const uint32_t plat_suffix,
               const uint32_t mark) {
    // Create an IPv6 UDP socket.
    const int s = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (s < 0) {
        const int err = errno;
        ALOGE("socket(AF_INET6, SOCK_DGRAM, 0) failed: %s", strerror(err));
        return -err;
    }

    // Socket's mark affects routing decisions (network selection)
    if (setsockopt(s, SOL_SOCKET, SO_MARK, &mark, sizeof(mark))) {
        const int err = errno;
        ALOGE("setsockopt(SOL_SOCKET, SO_MARK) failed: %s", strerror(err));
        close(s);
        return -err;
    }

    // Try to connect udp socket to plat_subnet(96 bits):plat_suffix(32 bits)
    struct sockaddr_in6 dst = {
            .sin6_family = AF_INET6,
            .sin6_addr = *plat_subnet,
    };
    dst.sin6_addr.s6_addr32[3] = plat_suffix;
    if (connect(s, (struct sockaddr*)&dst, sizeof(dst))) {
        const int err = errno;
        ALOGE("connect() failed: %s", strerror(err));
        close(s);
        return -err;
    }

    // Fetch the socket's IPv6 mtu - this is effectively fetching mtu from routing table
    int mtu;
    socklen_t sz_mtu = sizeof(mtu);
    if (getsockopt(s, SOL_IPV6, IPV6_MTU, &mtu, &sz_mtu)) {
        const int err = errno;
        ALOGE("getsockopt(SOL_IPV6, IPV6_MTU) failed: %s", strerror(err));
        close(s);
        return -err;
    }
    if (sz_mtu != sizeof(mtu)) {
        ALOGE("getsockopt(SOL_IPV6, IPV6_MTU) returned unexpected size: %d", sz_mtu);
        close(s);
        return -EFAULT;
    }
    close(s);

    return mtu;
}

/* function: configure_packet_socket
 * Binds the packet socket and attaches the receive filter to it.
 *   sock    - the socket to configure
 *   addr    - the IP address to filter
 *   ifindex - index of interface to add the filter to
 * returns: 0 on success, -errno on failure
 */
int configure_packet_socket(const int sock, const in6_addr* const addr, const int ifindex) {
    // clang-format off
    struct sock_filter filter_code[] = {
        BPF_LOAD_IPV6_BE32(daddr.s6_addr32[0]),
        BPF2_REJECT_IF_NOT_EQUAL(ntohl(addr->s6_addr32[0])),
        BPF_LOAD_IPV6_BE32(daddr.s6_addr32[1]),
        BPF2_REJECT_IF_NOT_EQUAL(ntohl(addr->s6_addr32[1])),
        BPF_LOAD_IPV6_BE32(daddr.s6_addr32[2]),
        BPF2_REJECT_IF_NOT_EQUAL(ntohl(addr->s6_addr32[2])),
        BPF_LOAD_IPV6_BE32(daddr.s6_addr32[3]),
        BPF2_REJECT_IF_NOT_EQUAL(ntohl(addr->s6_addr32[3])),
        BPF_ACCEPT,
    };
    // clang-format on
    struct sock_fprog filter = {sizeof(filter_code) / sizeof(filter_code[0]), filter_code};

    if (setsockopt(sock, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter))) {
        const int err = errno;
        ALOGE("attach packet filter failed: %s", strerror(err));
        return -err;
    }

    struct sockaddr_ll sll = {
            .sll_family = AF_PACKET,
            .sll_protocol = htons(ETH_P_IPV6),
            .sll_ifindex = ifindex,
            .sll_pkttype =
                    PACKET_OTHERHOST,  // The 464xlat IPv6 address is not assigned to the kernel.
    };
    if (bind(sock, (struct sockaddr*)&sll, sizeof(sll))) {
        const int err = errno;
        ALOGE("binding packet socket: %s", strerror(err));
        return -err;
    }

    return 0;
}

}  // namespace clat
}  // namespace net
}  // namespace android
