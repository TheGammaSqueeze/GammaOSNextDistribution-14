/*	$NetBSD: resolv.h,v 1.31 2005/12/26 19:01:47 perry Exp $	*/

/*
 * Copyright (c) 1983, 1987, 1989
 *    The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

/*
 * Copyright (c) 2004 by Internet Systems Consortium, Inc. ("ISC")
 * Portions Copyright (c) 1996-1999 by Internet Software Consortium.
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND ISC DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS.  IN NO EVENT SHALL ISC BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
 * OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

#pragma once

#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <private/android_filesystem_config.h>  // AID_DNS

#include <net/if.h>
#include <time.h>
#include <span>
#include <string>
#include <vector>

#include "DnsResolver.h"
#include "netd_resolv/resolv.h"
#include "params.h"
#include "stats.pb.h"

// Linux defines MAXHOSTNAMELEN as 64, while the domain name limit in
// RFC 1034 and RFC 1035 is 255 octets.
#ifdef MAXHOSTNAMELEN
#undef MAXHOSTNAMELEN
#endif
#define MAXHOSTNAMELEN 256

/*
 * Global defines and variables for resolver stub.
 */
#define RES_TIMEOUT 5000 /* min. milliseconds between retries */
#define RES_DFLRETRY 2    /* Default #/tries. */

// Flags for ResState::flags
#define RES_F_VC 0x00000001        // socket is TCP
#define RES_F_EDNS0ERR 0x00000004  // EDNS0 caused errors
#define RES_F_MDNS 0x00000008      // MDNS packet

// Holds either a sockaddr_in or a sockaddr_in6.
union sockaddr_union {
    struct sockaddr sa;
    struct sockaddr_in sin;
    struct sockaddr_in6 sin6;
};
constexpr int MAXPACKET = 8 * 1024;

struct ResState {
    ResState(const android_net_context* netcontext, android::net::NetworkDnsEventReported* dnsEvent)
        : netid(netcontext->dns_netid),
          uid(netcontext->uid),
          pid(netcontext->pid),
          mark(netcontext->dns_mark),
          event(dnsEvent),
          netcontext_flags(netcontext->flags) {}

    ResState clone(android::net::NetworkDnsEventReported* dnsEvent = nullptr) {
        // TODO: Separate non-copyable members to other structures and let default copy
        //       constructor do its work for below copyable members.
        ResState copy;
        copy.netid = netid;
        copy.uid = uid;
        copy.pid = pid;
        copy.search_domains = search_domains;
        copy.nsaddrs = nsaddrs;
        copy.udpsocks_ts = udpsocks_ts;
        copy.ndots = ndots;
        copy.mark = mark;
        copy.tcp_nssock_ts = tcp_nssock_ts;
        copy.flags = flags;
        copy.event = (dnsEvent == nullptr) ? event : dnsEvent;
        copy.netcontext_flags = netcontext_flags;
        copy.tc_mode = tc_mode;
        copy.enforce_dns_uid = enforce_dns_uid;
        copy.sort_nameservers = sort_nameservers;
        return copy;
    }
    void closeSockets() {
        tcp_nssock.reset();
        flags &= ~RES_F_VC;

        for (auto& sock : udpsocks) {
            sock.reset();
        }
    }

    int nameserverCount() { return nsaddrs.size(); }

    // clang-format off
    unsigned netid;                             // NetId: cache key and socket mark
    uid_t uid;                                  // uid of the app that sent the DNS lookup
    pid_t pid;                                  // pid of the app that sent the DNS lookup
    std::vector<std::string> search_domains{};  // domains to search
    std::vector<android::netdutils::IPSockAddr> nsaddrs;
    std::array<timespec, MAXNS> udpsocks_ts;    // The creation time of the UDP sockets
    android::base::unique_fd udpsocks[MAXNS];   // UDP sockets to nameservers
    unsigned ndots : 4 = 1;                     // threshold for initial abs. query
    unsigned mark;                              // Socket mark to be used by all DNS query sockets
    android::base::unique_fd tcp_nssock;        // TCP socket (but why not one per nameserver?)
    timespec tcp_nssock_ts = {};                // The creation time of the TCP socket
    uint32_t flags = 0;                         // See RES_F_* defines below
    android::net::NetworkDnsEventReported* event;
    uint32_t netcontext_flags;
    int tc_mode = 0;
    bool enforce_dns_uid = false;
    bool sort_nameservers = false;              // True if nsaddrs has been sorted.
    // clang-format on

  private:
    ResState() {}
};

/* End of stats related definitions */

/*
 * Error code extending h_errno codes defined in bionic/libc/include/netdb.h.
 *
 * This error code, including legacy h_errno, is returned from res_nquery(), res_nsearch(),
 * res_nquerydomain(), res_queryN_parallel(), res_searchN() and res_querydomainN() for DNS metrics.
 *
 * TODO: Consider mapping legacy and extended h_errno into a unified resolver error code mapping.
 */
#define NETD_RESOLV_H_ERRNO_EXT_TIMEOUT RCODE_TIMEOUT

extern const char* const _res_opcodes[];

int res_nameinquery(const char*, int, int, const uint8_t*, const uint8_t*);
int res_queriesmatch(const uint8_t*, const uint8_t*, const uint8_t*, const uint8_t*);

int res_nquery(ResState*, const char*, int, int, std::span<uint8_t>, int*);
int res_nsearch(ResState*, const char*, int, int, std::span<uint8_t>, int*);
int res_nquerydomain(ResState*, const char*, const char*, int, int, std::span<uint8_t>, int*);
int res_nmkquery(int op, const char* qname, int cl, int type, std::span<const uint8_t> data,
                 std::span<uint8_t> msg, int netcontext_flags);
int res_nsend(ResState* statp, std::span<const uint8_t> msg, std::span<uint8_t> ans, int* rcode,
              uint32_t flags, std::chrono::milliseconds sleepTimeMs = {});
int res_nopt(ResState*, int, std::span<uint8_t>, int);

int getaddrinfo_numeric(const char* hostname, const char* servname, addrinfo hints,
                        addrinfo** result);

// Helper function for converting h_errno to the error codes visible to netd
int herrnoToAiErrno(int herrno);

// Helper function to enable MDNS resolution.
void setMdnsFlag(std::string_view hostname, unsigned netid, uint32_t* flags);

// Helper function for checking MDNS resolution is enabled or not.
bool isMdnsResolution(uint32_t flags);

// switch resolver log severity
android::base::LogSeverity logSeverityStrToEnum(const std::string& logSeverityStr);

template <typename Dest>
Dest saturate_cast(int64_t x) {
    using DestLimits = std::numeric_limits<Dest>;
    if (x > DestLimits::max()) return DestLimits::max();
    if (x < DestLimits::min()) return DestLimits::min();
    return static_cast<Dest>(x);
}

constexpr bool is_power_of_2(size_t n) {
    return n != 0 && (n & (n - 1)) == 0;
}

// Rounds up a pointer to a char buffer |p| to a multiple of |Alignment| bytes.
// Requirements:
//   |p| must be a pointer to a byte-sized type (e.g.: uint8_t)
//   |Alignment| must be a power of 2
template<uintptr_t Alignment = sizeof(void*), typename T>
        requires (sizeof(T) == 1) && (is_power_of_2(Alignment))
constexpr T* align_ptr(T* const p) {
    // Written this way to sidestep the performance-no-int-to-ptr clang-tidy warning.
    constexpr uintptr_t mask = Alignment - 1;
    const uintptr_t uintptr = reinterpret_cast<uintptr_t>(p);
    const uintptr_t aligned = (uintptr + mask) & ~mask;
    const uintptr_t bias = aligned - uintptr;
    return p + bias;
}

// Testcases for align_ptr()
// TODO: enable when libc++ has std::bit_cast - reinterpret_cast isn't allowed in consteval context
// static_assert(align_ptr((char*)1000) == (char*)1000);
// static_assert(align_ptr((char*)1001) == (char*)1000 + sizeof(void*));
// static_assert(align_ptr((char*)1003) == (char*)1000 + sizeof(void*));
// static_assert(align_ptr<sizeof(uint32_t)>((char*)1004) == (char*)1004);
// static_assert(align_ptr<sizeof(uint64_t)>((char*)1004) == (char*)1008);

android::net::NsType getQueryType(std::span<const uint8_t> msg);

android::net::IpVersion ipFamilyToIPVersion(int ipFamily);

inline void resolv_tag_socket(int sock, uid_t uid, pid_t pid) {
    // This is effectively equivalent to testing for R+
    if (android::net::gResNetdCallbacks.tagSocket != nullptr) {
        if (int err = android::net::gResNetdCallbacks.tagSocket(sock, TAG_SYSTEM_DNS, uid, pid)) {
            LOG(WARNING) << "Failed to tag socket: " << strerror(-err);
        }
    }

    // fchown() apps' uid only in R+, since it's incompatible with Q's ebpf vpn isolation feature.
    if (fchown(sock, (android::net::gApiLevel >= 30) ? uid : AID_DNS, -1) == -1) {
        PLOG(WARNING) << "Failed to chown socket";
    }
}
