/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include "DnsProxyListener.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <dlfcn.h>
#include <linux/if.h>
#include <math.h>
#include <net/if.h>
#include <netdb.h>
#include <netinet/in.h>
#include <resolv.h>  // b64_pton()
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

#define LOG_TAG "resolv"

#include <algorithm>
#include <vector>

#include <android-base/parseint.h>
#include <android/multinetwork.h>  // ResNsendFlags
#include <cutils/misc.h>           // FIRST_APPLICATION_UID
#include <cutils/multiuser.h>
#include <netdutils/InternetAddresses.h>
#include <netdutils/ResponseCode.h>
#include <netdutils/Stopwatch.h>
#include <netdutils/ThreadUtil.h>
#include <private/android_filesystem_config.h>  // AID_SYSTEM
#include <statslog_resolv.h>
#include <sysutils/SocketClient.h>

#include "DnsResolver.h"
#include "Experiments.h"
#include "NetdPermissions.h"
#include "OperationLimiter.h"
#include "PrivateDnsConfiguration.h"
#include "ResolverEventReporter.h"
#include "dnsproxyd_protocol/DnsProxydProtocol.h"  // NETID_USE_LOCAL_NAMESERVERS
#include "getaddrinfo.h"
#include "gethnamaddr.h"
#include "res_send.h"
#include "resolv_cache.h"
#include "resolv_private.h"
#include "stats.h"  // RCODE_TIMEOUT
#include "stats.pb.h"
#include "util.h"

using aidl::android::net::metrics::INetdEventListener;
using aidl::android::net::resolv::aidl::DnsHealthEventParcel;
using aidl::android::net::resolv::aidl::IDnsResolverUnsolicitedEventListener;
using android::base::ParseInt;
using android::base::ParseUint;
using std::span;

namespace android {

using netdutils::MAX_QUERIES_IN_TOTAL;
using netdutils::MAX_QUERIES_PER_UID;
using netdutils::ResponseCode;
using netdutils::Stopwatch;

namespace net {
namespace {

android::netdutils::OperationLimiter<uid_t> queryLimiter(MAX_QUERIES_PER_UID);

bool startQueryLimiter(uid_t uid) {
    const int globalLimit = android::net::Experiments::getInstance()->getFlag("max_queries_global",
                                                                              MAX_QUERIES_IN_TOTAL);
    return queryLimiter.start(uid, globalLimit);
}

void endQueryLimiter(uid_t uid) {
    queryLimiter.finish(uid);
}

void logArguments(int argc, char** argv) {
    if (!WOULD_LOG(VERBOSE)) return;
    for (int i = 0; i < argc; i++) {
        LOG(VERBOSE) << __func__ << ": argv[" << i << "]=" << (argv[i] ? argv[i] : "null");
    }
}

bool checkAndClearUseLocalNameserversFlag(unsigned* netid) {
    if (netid == nullptr || ((*netid) & NETID_USE_LOCAL_NAMESERVERS) == 0) {
        return false;
    }
    *netid = (*netid) & ~NETID_USE_LOCAL_NAMESERVERS;
    return true;
}

constexpr bool requestingUseLocalNameservers(unsigned flags) {
    return (flags & NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS) != 0;
}

bool queryingViaTls(unsigned dns_netid) {
    const auto privateDnsStatus = PrivateDnsConfiguration::getInstance().getStatus(dns_netid);
    switch (privateDnsStatus.mode) {
        case PrivateDnsMode::OPPORTUNISTIC:
            return !privateDnsStatus.validatedServers().empty();
        case PrivateDnsMode::STRICT:
            return true;
        default:
            return false;
    }
}

bool hasPermissionToBypassPrivateDns(uid_t uid) {
    static_assert(AID_SYSTEM >= 0 && AID_SYSTEM < FIRST_APPLICATION_UID,
                  "Calls from AID_SYSTEM must not result in a permission check to avoid deadlock.");
    if (uid >= 0 && uid < FIRST_APPLICATION_UID) {
        return true;
    }

    for (const char* const permission :
         {PERM_CONNECTIVITY_USE_RESTRICTED_NETWORKS, PERM_NETWORK_BYPASS_PRIVATE_DNS,
          PERM_MAINLINE_NETWORK_STACK}) {
        if (gResNetdCallbacks.check_calling_permission(permission)) {
            return true;
        }
    }
    return false;
}

void maybeFixupNetContext(android_net_context* ctx, pid_t pid) {
    if (requestingUseLocalNameservers(ctx->flags) && !hasPermissionToBypassPrivateDns(ctx->uid)) {
        // Not permitted; clear the flag.
        ctx->flags &= ~NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS;
    }

    if (!requestingUseLocalNameservers(ctx->flags)) {
        // If we're not explicitly bypassing DNS-over-TLS servers, check whether
        // DNS-over-TLS is in use as an indicator for when to use more modern
        // DNS resolution mechanics.
        if (queryingViaTls(ctx->dns_netid)) {
            ctx->flags |= NET_CONTEXT_FLAG_USE_DNS_OVER_TLS | NET_CONTEXT_FLAG_USE_EDNS;
        }
    }
    ctx->pid = pid;
}

void addIpAddrWithinLimit(std::vector<std::string>* ip_addrs, const sockaddr* addr,
                          socklen_t addrlen);

int extractResNsendAnswers(std::span<const uint8_t> answer, int ipType,
                           std::vector<std::string>* ip_addrs) {
    int total_ip_addr_count = 0;
    ns_msg handle;
    if (ns_initparse(answer.data(), answer.size(), &handle) < 0) {
        return 0;
    }
    int ancount = ns_msg_count(handle, ns_s_an);
    ns_rr rr;
    for (int i = 0; i < ancount; i++) {
        if (ns_parserr(&handle, ns_s_an, i, &rr) < 0) {
            continue;
        }
        const uint8_t* rdata = ns_rr_rdata(rr);
        if (ipType == ns_t_a) {
            sockaddr_in sin = {.sin_family = AF_INET};
            memcpy(&sin.sin_addr, rdata, sizeof(sin.sin_addr));
            addIpAddrWithinLimit(ip_addrs, (sockaddr*)&sin, sizeof(sin));
            total_ip_addr_count++;
        } else if (ipType == ns_t_aaaa) {
            sockaddr_in6 sin6 = {.sin6_family = AF_INET6};
            memcpy(&sin6.sin6_addr, rdata, sizeof(sin6.sin6_addr));
            addIpAddrWithinLimit(ip_addrs, (sockaddr*)&sin6, sizeof(sin6));
            total_ip_addr_count++;
        }
    }

    return total_ip_addr_count;
}

int extractGetAddrInfoAnswers(const addrinfo* result, std::vector<std::string>* ip_addrs) {
    int total_ip_addr_count = 0;
    if (result == nullptr) {
        return 0;
    }
    for (const addrinfo* ai = result; ai; ai = ai->ai_next) {
        sockaddr* ai_addr = ai->ai_addr;
        if (ai_addr) {
            addIpAddrWithinLimit(ip_addrs, ai_addr, ai->ai_addrlen);
            total_ip_addr_count++;
        }
    }
    return total_ip_addr_count;
}

int extractGetHostByNameAnswers(const hostent* hp, std::vector<std::string>* ip_addrs) {
    int total_ip_addr_count = 0;
    if (hp == nullptr) {
        return 0;
    }
    if (hp->h_addrtype == AF_INET) {
        in_addr** list = (in_addr**)hp->h_addr_list;
        for (int i = 0; list[i] != nullptr; i++) {
            sockaddr_in sin = {.sin_family = AF_INET, .sin_addr = *list[i]};
            addIpAddrWithinLimit(ip_addrs, (sockaddr*)&sin, sizeof(sin));
            total_ip_addr_count++;
        }
    } else if (hp->h_addrtype == AF_INET6) {
        in6_addr** list = (in6_addr**)hp->h_addr_list;
        for (int i = 0; list[i] != nullptr; i++) {
            sockaddr_in6 sin6 = {.sin6_family = AF_INET6, .sin6_addr = *list[i]};
            addIpAddrWithinLimit(ip_addrs, (sockaddr*)&sin6, sizeof(sin6));
            total_ip_addr_count++;
        }
    }
    return total_ip_addr_count;
}

int rcodeToAiError(int rcode) {
    switch (rcode) {
        case NOERROR:
            return 0;
        case RCODE_TIMEOUT:
            return NETD_RESOLV_TIMEOUT;
        default:
            return EAI_NODATA;
    }
}

int resNSendToAiError(int err, int rcode) {
    if (err > 0) {
        return rcodeToAiError(rcode);
    }
    if (err == -ETIMEDOUT) {
        return NETD_RESOLV_TIMEOUT;
    }
    return EAI_SYSTEM;
}

bool setQueryId(span<uint8_t> msg, uint16_t query_id) {
    if ((size_t)msg.size() < sizeof(HEADER)) {
        LOG(ERROR) << __func__ << ": Invalid parameter";
        return false;
    }
    auto hp = reinterpret_cast<HEADER*>(msg.data());
    hp->id = htons(query_id);
    return true;
}

bool parseQuery(span<const uint8_t> msg, uint16_t* query_id, int* rr_type, std::string* rr_name) {
    ns_msg handle;
    ns_rr rr;
    if (ns_initparse(msg.data(), msg.size(), &handle) < 0 ||
        ns_parserr(&handle, ns_s_qd, 0, &rr) < 0) {
        return false;
    }
    *query_id = ns_msg_id(handle);
    *rr_name = ns_rr_name(rr);
    *rr_type = ns_rr_type(rr);
    return true;
}

// Note: Even if it returns PDM_OFF, it doesn't mean there's no DoT stats in the message
// because Private DNS mode can change at any time.
PrivateDnsModes getPrivateDnsModeForMetrics(uint32_t netId) {
    // If the network `netId` doesn't exist, getStatus() sets the mode to PrivateDnsMode::OFF and
    // returns it. This is incorrect for the metrics. Consider returning PDM_UNKNOWN in such case.
    return convertEnumType(PrivateDnsConfiguration::getInstance().getStatus(netId).mode);
}

void initDnsEvent(NetworkDnsEventReported* event, const android_net_context& netContext) {
    // The value 0 has the special meaning of unset/unknown in Statsd atoms. So, we set both
    // flags to -1 as default value.
    //  1. The hints flag is only used in resolv_getaddrinfo. When user set it to -1 in
    //     resolv_getaddrinfo, the flag will cause validation (validateHints) failure in
    //     getaddrinfo, so it will not do DNS query and will upload DNS stats log with
    //     return_code = RC_EAI_BADFLAGS.
    //  2. The res_nsend flags are only used in resolv_res_nsend. When user set it to -1 in
    //     resolv_res_nsend,res_nsend will do nothing special by the setting.
    event->set_hints_ai_flags(-1);
    event->set_res_nsend_flags(-1);
    event->set_private_dns_modes(getPrivateDnsModeForMetrics(netContext.dns_netid));
}

// Return 0 if the event should not be logged.
// Otherwise, return subsampling_denom
uint32_t getDnsEventSubsamplingRate(int netid, int returnCode, bool isMdns) {
    uint32_t subsampling_denom = resolv_cache_get_subsampling_denom(netid, returnCode, isMdns);
    if (subsampling_denom == 0) return 0;
    // Sample the event with a chance of 1 / denom.
    return (arc4random_uniform(subsampling_denom) == 0) ? subsampling_denom : 0;
}

void maybeLogQuery(int eventType, const android_net_context& netContext,
                   const NetworkDnsEventReported& event, const std::string& query_name,
                   const std::vector<std::string>& ip_addrs) {
    // Skip reverse queries.
    if (eventType == INetdEventListener::EVENT_GETHOSTBYADDR) return;

    for (const auto& query : event.dns_query_events().dns_query_event()) {
        // Log it when the cache misses.
        if (query.cache_hit() != CS_FOUND) {
            const int timeTakenMs = event.latency_micros() / 1000;
            DnsQueryLog::Record record(netContext.dns_netid, netContext.uid, netContext.pid,
                                       query_name, ip_addrs, timeTakenMs);
            gDnsResolv->dnsQueryLog().push(std::move(record));
            return;
        }
    }
}

void reportDnsEvent(int eventType, const android_net_context& netContext, int latencyUs,
                    int returnCode, NetworkDnsEventReported& event, const std::string& query_name,
                    bool skipStats, const std::vector<std::string>& ip_addrs = {},
                    int total_ip_addr_count = 0) {
    int32_t rate =
            skipStats ? 0
            : (query_name.ends_with(".local") && is_mdns_supported_network(netContext.dns_netid) &&
               android::net::Experiments::getInstance()->getFlag("mdns_resolution", 1))
                    ? getDnsEventSubsamplingRate(netContext.dns_netid, returnCode, true)
                    : getDnsEventSubsamplingRate(netContext.dns_netid, returnCode, false);

    if (rate) {
        const std::string& dnsQueryStats = event.dns_query_events().SerializeAsString();
        stats::BytesField dnsQueryBytesField{dnsQueryStats.c_str(), dnsQueryStats.size()};
        event.set_return_code(static_cast<ReturnCode>(returnCode));
        event.set_network_type(resolv_get_network_types_for_net(netContext.dns_netid));
        event.set_uid(netContext.uid);
        android::net::stats::stats_write(
                android::net::stats::NETWORK_DNS_EVENT_REPORTED, event.event_type(),
                event.return_code(), event.latency_micros(), event.hints_ai_flags(),
                event.res_nsend_flags(), event.network_type(), event.private_dns_modes(),
                dnsQueryBytesField, rate, event.uid());
    }

    maybeLogQuery(eventType, netContext, event, query_name, ip_addrs);

    const auto& listeners = ResolverEventReporter::getInstance().getListeners();
    if (listeners.empty()) {
        LOG(ERROR) << __func__
                   << ": DNS event not sent since no INetdEventListener receiver is available.";
    }
    const int latencyMs = latencyUs / 1000;
    for (const auto& it : listeners) {
        it->onDnsEvent(netContext.dns_netid, eventType, returnCode, latencyMs, query_name, ip_addrs,
                       total_ip_addr_count, netContext.uid);
    }

    const auto& unsolEventListeners = ResolverEventReporter::getInstance().getUnsolEventListeners();

    if (returnCode == NETD_RESOLV_TIMEOUT) {
        const DnsHealthEventParcel dnsHealthEvent = {
                .netId = static_cast<int32_t>(netContext.dns_netid),
                .healthResult = IDnsResolverUnsolicitedEventListener::DNS_HEALTH_RESULT_TIMEOUT,
        };
        for (const auto& it : unsolEventListeners) {
            it->onDnsHealthEvent(dnsHealthEvent);
        }
    } else if (returnCode == NOERROR) {
        DnsHealthEventParcel dnsHealthEvent = {
                .netId = static_cast<int32_t>(netContext.dns_netid),
                .healthResult = IDnsResolverUnsolicitedEventListener::DNS_HEALTH_RESULT_OK,
        };
        for (const auto& query : event.dns_query_events().dns_query_event()) {
            if (query.cache_hit() != CS_FOUND && query.rcode() == NS_R_NO_ERROR) {
                dnsHealthEvent.successRttMicros.push_back(query.latency_micros());
            }
        }

        if (!dnsHealthEvent.successRttMicros.empty()) {
            for (const auto& it : unsolEventListeners) {
                it->onDnsHealthEvent(dnsHealthEvent);
            }
        }
    }
}

bool onlyIPv4Answers(const addrinfo* res) {
    // Null addrinfo pointer isn't checked because the caller doesn't pass null pointer.

    for (const addrinfo* ai = res; ai; ai = ai->ai_next)
        if (ai->ai_family != AF_INET) return false;

    return true;
}

bool isSpecialUseIPv4Address(const struct in_addr& ia) {
    const uint32_t addr = ntohl(ia.s_addr);

    // Only check necessary IP ranges in RFC 5735 section 4
    return ((addr & 0xff000000) == 0x00000000) ||  // "This" Network
           ((addr & 0xff000000) == 0x7f000000) ||  // Loopback
           ((addr & 0xffff0000) == 0xa9fe0000) ||  // Link Local
           ((addr & 0xf0000000) == 0xe0000000) ||  // Multicast
           (addr == INADDR_BROADCAST);             // Limited Broadcast
}

bool isSpecialUseIPv4Address(const struct sockaddr* sa) {
    if (sa->sa_family != AF_INET) return false;

    return isSpecialUseIPv4Address(((struct sockaddr_in*)sa)->sin_addr);
}

bool onlyNonSpecialUseIPv4Addresses(struct hostent* hp) {
    // Null hostent pointer isn't checked because the caller doesn't pass null pointer.

    if (hp->h_addrtype != AF_INET) return false;

    for (int i = 0; hp->h_addr_list[i] != nullptr; i++)
        if (isSpecialUseIPv4Address(*(struct in_addr*)hp->h_addr_list[i])) return false;

    return true;
}

bool onlyNonSpecialUseIPv4Addresses(const addrinfo* res) {
    // Null addrinfo pointer isn't checked because the caller doesn't pass null pointer.

    for (const addrinfo* ai = res; ai; ai = ai->ai_next) {
        if (ai->ai_family != AF_INET) return false;
        if (isSpecialUseIPv4Address(ai->ai_addr)) return false;
    }

    return true;
}

void logDnsQueryResult(const struct hostent* hp) {
    if (!WOULD_LOG(DEBUG)) return;
    if (hp == nullptr) return;

    LOG(DEBUG) << __func__ << ": DNS records:";
    for (int i = 0; hp->h_addr_list[i] != nullptr; i++) {
        char ip_addr[INET6_ADDRSTRLEN];
        if (inet_ntop(hp->h_addrtype, hp->h_addr_list[i], ip_addr, sizeof(ip_addr)) != nullptr) {
            LOG(DEBUG) << __func__ << ": [" << i << "] " << hp->h_addrtype;
        } else {
            PLOG(DEBUG) << __func__ << ": [" << i << "] numeric hostname translation fail";
        }
    }
}

void logDnsQueryResult(const addrinfo* res) {
    if (!WOULD_LOG(DEBUG)) return;
    if (res == nullptr) return;

    int i;
    const addrinfo* ai;
    LOG(DEBUG) << __func__ << ": DNS records:";
    for (ai = res, i = 0; ai; ai = ai->ai_next, i++) {
        if ((ai->ai_family != AF_INET) && (ai->ai_family != AF_INET6)) continue;
        // Reassign it to a local variable to avoid -Wnullable-to-nonnull-conversion on calling
        // getnameinfo.
        const sockaddr* ai_addr = ai->ai_addr;
        char ip_addr[INET6_ADDRSTRLEN];
        const int ret = getnameinfo(ai_addr, ai->ai_addrlen, ip_addr, sizeof(ip_addr), nullptr, 0,
                                    NI_NUMERICHOST);
        if (!ret) {
            LOG(DEBUG) << __func__ << ": [" << i << "] " << ai->ai_flags << " " << ai->ai_family
                       << " " << ai->ai_socktype << " " << ai->ai_protocol << " " << ip_addr;
        } else {
            LOG(DEBUG) << __func__ << ": [" << i << "] numeric hostname translation fail " << ret;
        }
    }
}

bool isValidNat64Prefix(const netdutils::IPPrefix prefix) {
    if (prefix.family() != AF_INET6) {
        LOG(ERROR) << __func__ << ": Only IPv6 NAT64 prefixes are supported " << prefix.family();
        return false;
    }
    if (prefix.length() != 96) {
        LOG(ERROR) << __func__ << ": Only /96 NAT64 prefixes are supported " << prefix.length();
        return false;
    }
    return true;
}

bool synthesizeNat64PrefixWithARecord(const netdutils::IPPrefix& prefix, struct hostent* hp) {
    if (hp == nullptr) return false;
    if (!onlyNonSpecialUseIPv4Addresses(hp)) return false;
    if (!isValidNat64Prefix(prefix)) return false;

    struct sockaddr_storage ss = netdutils::IPSockAddr(prefix.ip());
    struct sockaddr_in6* v6prefix = (struct sockaddr_in6*)&ss;
    for (int i = 0; hp->h_addr_list[i] != nullptr; i++) {
        struct in_addr iaOriginal = *(struct in_addr*)hp->h_addr_list[i];
        struct in6_addr* ia6 = (struct in6_addr*)hp->h_addr_list[i];
        memset(ia6, 0, sizeof(struct in6_addr));

        // Synthesize /96 NAT64 prefix in place. The space has reserved by getanswer() and
        // _hf_gethtbyname2() in system/netd/resolv/gethnamaddr.cpp and
        // system/netd/resolv/sethostent.cpp.
        *ia6 = v6prefix->sin6_addr;
        ia6->s6_addr32[3] = iaOriginal.s_addr;

        if (WOULD_LOG(VERBOSE)) {
            char buf[INET6_ADDRSTRLEN];  // big enough for either IPv4 or IPv6
            inet_ntop(AF_INET, &iaOriginal.s_addr, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": DNS A record: " << buf;
            inet_ntop(AF_INET6, &v6prefix->sin6_addr, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": NAT64 prefix: " << buf;
            inet_ntop(AF_INET6, ia6, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": DNS64 Synthesized AAAA record: " << buf;
        }
    }
    hp->h_addrtype = AF_INET6;
    hp->h_length = sizeof(in6_addr);

    logDnsQueryResult(hp);
    return true;
}

bool synthesizeNat64PrefixWithARecord(const netdutils::IPPrefix& prefix, addrinfo** res,
                                      bool unspecWantedButNoIPv6,
                                      const android_net_context* netcontext) {
    if (*res == nullptr) return false;
    if (!onlyNonSpecialUseIPv4Addresses(*res)) return false;
    if (!isValidNat64Prefix(prefix)) return false;

    const sockaddr_storage ss = netdutils::IPSockAddr(prefix.ip());
    const sockaddr_in6* v6prefix = (sockaddr_in6*)&ss;
    addrinfo* const head4 = *res;
    addrinfo* head6 = nullptr;
    addrinfo* cur6 = nullptr;

    // Build a synthesized AAAA addrinfo list from the queried A addrinfo list. Here is the diagram
    // for the relationship of pointers.
    //
    // head4: point to the first queried A addrinfo
    // |
    // v
    // +-------------+   +-------------+
    // | addrinfo4#1 |-->| addrinfo4#2 |--> .. queried A addrinfo(s) for DNS64 synthesis
    // +-------------+   +-------------+
    //                   ^
    //                   |
    //                   cur4: current worked-on queried A addrinfo
    //
    // head6: point to the first synthesized AAAA addrinfo
    // |
    // v
    // +-------------+   +-------------+
    // | addrinfo6#1 |-->| addrinfo6#2 |--> .. synthesized DNS64 AAAA addrinfo(s)
    // +-------------+   +-------------+
    //                   ^
    //                   |
    //                   cur6: current worked-on synthesized addrinfo
    //
    for (const addrinfo* cur4 = head4; cur4; cur4 = cur4->ai_next) {
        // Allocate a space for a synthesized AAAA addrinfo. Note that the addrinfo and sockaddr
        // occupy one contiguous block of memory and are allocated and freed as a single block.
        // See get_ai and freeaddrinfo in packages/modules/DnsResolver/getaddrinfo.cpp.
        addrinfo* sa = (addrinfo*)calloc(1, sizeof(addrinfo) + sizeof(sockaddr_in6));
        if (sa == nullptr) {
            LOG(ERROR) << "allocate memory failed for synthesized result";
            freeaddrinfo(head6);
            return false;
        }

        // Initialize the synthesized AAAA addrinfo by the queried A addrinfo. The ai_addr will be
        // set lately.
        sa->ai_flags = cur4->ai_flags;
        sa->ai_family = AF_INET6;
        sa->ai_socktype = cur4->ai_socktype;
        sa->ai_protocol = cur4->ai_protocol;
        sa->ai_addrlen = sizeof(sockaddr_in6);
        sa->ai_addr = (sockaddr*)(sa + 1);
        sa->ai_canonname = nullptr;
        sa->ai_next = nullptr;

        if (cur4->ai_canonname != nullptr) {
            // Reassign it to a local variable to avoid -Wnullable-to-nonnull-conversion on calling
            // strdup.
            const char* ai_canonname = cur4->ai_canonname;
            sa->ai_canonname = strdup(ai_canonname);
            if (sa->ai_canonname == nullptr) {
                LOG(ERROR) << "allocate memory failed for canonname";
                freeaddrinfo(sa);
                freeaddrinfo(head6);
                return false;
            }
        }

        // Synthesize /96 NAT64 prefix with the queried IPv4 address.
        const sockaddr_in* sin4 = (sockaddr_in*)cur4->ai_addr;
        sockaddr_in6* sin6 = (sockaddr_in6*)sa->ai_addr;
        sin6->sin6_addr = v6prefix->sin6_addr;
        sin6->sin6_addr.s6_addr32[3] = sin4->sin_addr.s_addr;
        sin6->sin6_family = AF_INET6;
        sin6->sin6_port = sin4->sin_port;

        // If the synthesized list is empty, this becomes the first element.
        if (head6 == nullptr) {
            head6 = sa;
        }

        // Add this element to the end of the synthesized list.
        if (cur6 != nullptr) {
            cur6->ai_next = sa;
        }
        cur6 = sa;

        if (WOULD_LOG(VERBOSE)) {
            char buf[INET6_ADDRSTRLEN];  // big enough for either IPv4 or IPv6
            inet_ntop(AF_INET, &sin4->sin_addr.s_addr, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": DNS A record: " << buf;
            inet_ntop(AF_INET6, &v6prefix->sin6_addr, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": NAT64 prefix: " << buf;
            inet_ntop(AF_INET6, &sin6->sin6_addr, buf, sizeof(buf));
            LOG(VERBOSE) << __func__ << ": DNS64 Synthesized AAAA record: " << buf;
        }
    }

    // Simply concatenate the synthesized AAAA addrinfo list and the queried A addrinfo list when
    // AF_UNSPEC is specified. In the other words, the IPv6 addresses are listed first and then
    // IPv4 addresses. For example:
    //     64:ff9b::102:304 (socktype=2, protocol=17) ->
    //     64:ff9b::102:304 (socktype=1, protocol=6) ->
    //     1.2.3.4 (socktype=2, protocol=17) ->
    //     1.2.3.4 (socktype=1, protocol=6)
    // Note that head6 and cur6 should be non-null because there was at least one IPv4 address
    // synthesized. From the above example, the synthesized addrinfo list puts IPv6 and IPv4 in
    // groups and sort by RFC 6724 later. This ordering is different from no synthesized case
    // because resolv_getaddrinfo() sorts results in explore_options. resolv_getaddrinfo() calls
    // explore_fqdn() many times by the different items of explore_options. It means that
    // resolv_rfc6724_sort() only sorts the results in each explore_options and concatenates each
    // results into one. For example, getaddrinfo() is called with null hints for a domain name
    // which has both IPv4 and IPv6 addresses. The address order of the result addrinfo may be:
    //     2001:db8::102:304 (socktype=2, protocol=17) -> 1.2.3.4 (socktype=2, protocol=17) ->
    //     2001:db8::102:304 (socktype=1, protocol=6) -> 1.2.3.4 (socktype=1, protocol=6)
    // In above example, the first two results come from one explore option and the last two come
    // from another one. They are sorted first, and then concatenate together to be the result.
    // See also resolv_getaddrinfo in packages/modules/DnsResolver/getaddrinfo.cpp.
    if (unspecWantedButNoIPv6) {
        cur6->ai_next = head4;
    } else {
        freeaddrinfo(head4);
    }

    // Sort the concatenated addresses by RFC 6724 section 2.1.
    struct addrinfo sorting_head = {.ai_next = head6};
    resolv_rfc6724_sort(&sorting_head, netcontext->app_mark, netcontext->uid);

    *res = sorting_head.ai_next;
    logDnsQueryResult(*res);
    return true;
}

bool getDns64Prefix(unsigned netId, netdutils::IPPrefix* prefix) {
    return !gDnsResolv->resolverCtrl.getPrefix64(netId, prefix);
}

std::string makeThreadName(unsigned netId, uint32_t uid) {
    // The maximum of netId and app_id are 5-digit numbers.
    return fmt::format("Dns_{}_{}", netId, multiuser_get_app_id(uid));
}

typedef int (*InitFn)();
typedef int (*IsUidBlockedFn)(uid_t, bool);

IsUidBlockedFn ADnsHelper_isUidNetworkingBlocked;

IsUidBlockedFn resolveIsUidNetworkingBlockedFn() {
    // Related BPF maps were mainlined from T.
    if (!isAtLeastT()) return nullptr;

    // TODO: Check whether it is safe to shared link the .so without using dlopen when the carrier
    // APEX module (tethering) is fully released.
    void* handle = dlopen("libcom.android.tethering.dns_helper.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        LOG(WARNING) << __func__ << ": " << dlerror();
        return nullptr;
    }

    InitFn ADnsHelper_init = reinterpret_cast<InitFn>(dlsym(handle, "ADnsHelper_init"));
    if (!ADnsHelper_init) {
        LOG(ERROR) << __func__ << ": " << dlerror();
        // TODO: Change to abort() when NDK is finalized
        return nullptr;
    }
    const int ret = (*ADnsHelper_init)();
    if (ret) {
        LOG(ERROR) << __func__ << ": ADnsHelper_init failed " << strerror(-ret);
    }

    IsUidBlockedFn f =
            reinterpret_cast<IsUidBlockedFn>(dlsym(handle, "ADnsHelper_isUidNetworkingBlocked"));
    if (!f) {
        LOG(ERROR) << __func__ << ": " << dlerror();
        // TODO: Change to abort() when NDK is finalized
        return nullptr;
    }
    return f;
}

bool isUidNetworkingBlocked(uid_t uid, unsigned netId) {
    if (!ADnsHelper_isUidNetworkingBlocked) return false;

    // The enforceDnsUid is an OEM feature that sets DNS packet with AID_DNS instead of the
    // application's UID. Its DNS packets are not subject to certain network restriction features.
    if (resolv_is_enforceDnsUid_enabled_network(netId)) return false;

    // Feature flag that can disable the feature.
    if (!android::net::Experiments::getInstance()->getFlag("fail_fast_on_uid_network_blocking",
                                                           1)) {
        return false;
    }

    return (*ADnsHelper_isUidNetworkingBlocked)(uid, resolv_is_metered_network(netId)) == 1;
}

}  // namespace

DnsProxyListener::DnsProxyListener() : FrameworkListener(SOCKET_NAME) {
    mGetAddrInfoCmd = std::make_unique<GetAddrInfoCmd>();
    registerCmd(mGetAddrInfoCmd.get());

    mGetHostByAddrCmd = std::make_unique<GetHostByAddrCmd>();
    registerCmd(mGetHostByAddrCmd.get());

    mGetHostByNameCmd = std::make_unique<GetHostByNameCmd>();
    registerCmd(mGetHostByNameCmd.get());

    mResNSendCommand = std::make_unique<ResNSendCommand>();
    registerCmd(mResNSendCommand.get());

    mGetDnsNetIdCommand = std::make_unique<GetDnsNetIdCommand>();
    registerCmd(mGetDnsNetIdCommand.get());

    ADnsHelper_isUidNetworkingBlocked = resolveIsUidNetworkingBlockedFn();
}

void DnsProxyListener::Handler::spawn() {
    const int rval = netdutils::threadLaunch(this);
    if (rval == 0) {
        return;
    }

    char* msg = nullptr;
    asprintf(&msg, "%s (%d)", strerror(-rval), -rval);
    mClient->sendMsg(ResponseCode::OperationFailed, msg, false);
    free(msg);
    delete this;
}

DnsProxyListener::GetAddrInfoHandler::GetAddrInfoHandler(SocketClient* c, std::string host,
                                                         std::string service,
                                                         std::unique_ptr<addrinfo> hints,
                                                         const android_net_context& netcontext)
    : Handler(c),
      mHost(std::move(host)),
      mService(std::move(service)),
      mHints(std::move(hints)),
      mNetContext(netcontext) {}

// Before U, the Netd callback is implemented by OEM to evaluate if a DNS query for the provided
// hostname is allowed. On U+, the Netd callback also checks if the user is allowed to send DNS on
// the specified network.
static bool evaluate_domain_name(const android_net_context& netcontext, const char* host) {
    if (!gResNetdCallbacks.evaluate_domain_name) return true;
    return gResNetdCallbacks.evaluate_domain_name(netcontext, host);
}

static int HandleArgumentError(SocketClient* cli, int errorcode, std::string strerrormessage,
                               int argc, char** argv) {
    for (int i = 0; i < argc; i++) {
        strerrormessage += "argv[" + std::to_string(i) + "]=" + (argv[i] ? argv[i] : "null") + " ";
    }

    LOG(WARNING) << strerrormessage;
    cli->sendMsg(errorcode, strerrormessage.c_str(), false);
    return -1;
}

static bool sendBE32(SocketClient* c, uint32_t data) {
    uint32_t be_data = htonl(data);
    return c->sendData(&be_data, sizeof(be_data)) == 0;
}

// Sends 4 bytes of big-endian length, followed by the data.
// Returns true on success.
static bool sendLenAndData(SocketClient* c, const int len, const void* data) {
    return sendBE32(c, len) && (len == 0 || c->sendData(data, len) == 0);
}

// Returns true on success
static bool sendhostent(SocketClient* c, hostent* hp) {
    bool success = true;
    int i;
    if (hp->h_name != nullptr) {
        const char* h_name = hp->h_name;
        success &= sendLenAndData(c, strlen(h_name) + 1, hp->h_name);
    } else {
        success &= sendLenAndData(c, 0, "") == 0;
    }

    for (i = 0; hp->h_aliases[i] != nullptr; i++) {
        const char* h_aliases = hp->h_aliases[i];
        success &= sendLenAndData(c, strlen(h_aliases) + 1, hp->h_aliases[i]);
    }
    success &= sendLenAndData(c, 0, "");  // null to indicate we're done

    uint32_t buf = htonl(hp->h_addrtype);
    success &= c->sendData(&buf, sizeof(buf)) == 0;

    buf = htonl(hp->h_length);
    success &= c->sendData(&buf, sizeof(buf)) == 0;

    for (i = 0; hp->h_addr_list[i] != nullptr; i++) {
        success &= sendLenAndData(c, 16, hp->h_addr_list[i]);
    }
    success &= sendLenAndData(c, 0, "");  // null to indicate we're done
    return success;
}

static bool sendaddrinfo(SocketClient* c, addrinfo* ai) {
    // struct addrinfo {
    //      int     ai_flags;       /* AI_PASSIVE, AI_CANONNAME, AI_NUMERICHOST */
    //      int     ai_family;      /* PF_xxx */
    //      int     ai_socktype;    /* SOCK_xxx */
    //      int     ai_protocol;    /* 0 or IPPROTO_xxx for IPv4 and IPv6 */
    //      socklen_t ai_addrlen;   /* length of ai_addr */
    //      char    *ai_canonname;  /* canonical name for hostname */
    //      struct  sockaddr *ai_addr;      /* binary address */
    //      struct  addrinfo *ai_next;      /* next structure in linked list */
    // };

    // Write the struct piece by piece because we might be a 64-bit netd
    // talking to a 32-bit process.
    bool success = sendBE32(c, ai->ai_flags) && sendBE32(c, ai->ai_family) &&
                   sendBE32(c, ai->ai_socktype) && sendBE32(c, ai->ai_protocol);
    if (!success) {
        return false;
    }

    // ai_addrlen and ai_addr.
    if (!sendLenAndData(c, ai->ai_addrlen, ai->ai_addr)) {
        return false;
    }

    // strlen(ai_canonname) and ai_canonname.
    int len = 0;
    if (ai->ai_canonname != nullptr) {
        const char* ai_canonname = ai->ai_canonname;
        len = strlen(ai_canonname) + 1;
    }
    if (!sendLenAndData(c, len, ai->ai_canonname)) {
        return false;
    }

    return true;
}

void DnsProxyListener::GetAddrInfoHandler::doDns64Synthesis(int32_t* rv, addrinfo** res,
                                                            NetworkDnsEventReported* event) {
    const bool ipv6WantedButNoData = (mHints && mHints->ai_family == AF_INET6 && *rv == EAI_NODATA);
    const bool unspecWantedButNoIPv6 =
            ((!mHints || mHints->ai_family == AF_UNSPEC) && *rv == 0 && onlyIPv4Answers(*res));

    if (!ipv6WantedButNoData && !unspecWantedButNoIPv6) {
        return;
    }

    netdutils::IPPrefix prefix{};
    if (!getDns64Prefix(mNetContext.dns_netid, &prefix)) {
        return;
    }

    if (ipv6WantedButNoData) {
        // If caller wants IPv6 answers but no data, try to query IPv4 answers for synthesis
        const char* host = mHost.starts_with('^') ? nullptr : mHost.c_str();
        const char* service = mService.starts_with('^') ? nullptr : mService.c_str();
        mHints->ai_family = AF_INET;
        // Don't need to do freeaddrinfo(res) before starting new DNS lookup because previous
        // DNS lookup is failed with error EAI_NODATA.
        *rv = resolv_getaddrinfo(host, service, mHints.get(), &mNetContext, res, event);
        if (*rv) {
            *rv = EAI_NODATA;  // return original error code
            return;
        }
    }

    if (!synthesizeNat64PrefixWithARecord(prefix, res, unspecWantedButNoIPv6, &mNetContext)) {
        if (ipv6WantedButNoData) {
            // If caller wants IPv6 answers but no data and failed to synthesize IPv6 answers,
            // don't return the IPv4 answers.
            *rv = EAI_NODATA;  // return original error code
            if (*res) {
                freeaddrinfo(*res);
                *res = nullptr;
            }
        }
    }
}

void DnsProxyListener::GetAddrInfoHandler::run() {
    LOG(INFO) << "GetAddrInfoHandler::run: {" << mNetContext.toString() << "}";

    addrinfo* result = nullptr;
    Stopwatch s;
    maybeFixupNetContext(&mNetContext, mClient->getPid());
    const uid_t uid = mClient->getUid();
    int32_t rv = 0;
    NetworkDnsEventReported event;
    initDnsEvent(&event, mNetContext);
    const bool isUidBlocked = isUidNetworkingBlocked(mNetContext.uid, mNetContext.dns_netid);
    if (isUidBlocked) {
        LOG(INFO) << "GetAddrInfoHandler::run: network access blocked";
        rv = EAI_FAIL;
    } else if (startQueryLimiter(uid)) {
        const char* host = mHost.starts_with('^') ? nullptr : mHost.c_str();
        const char* service = mService.starts_with('^') ? nullptr : mService.c_str();
        if (evaluate_domain_name(mNetContext, host)) {
            rv = resolv_getaddrinfo(host, service, mHints.get(), &mNetContext, &result, &event);
            doDns64Synthesis(&rv, &result, &event);
        } else {
            rv = EAI_SYSTEM;
        }
        endQueryLimiter(uid);
    } else {
        // Note that this error code is currently not passed down to the client.
        // android_getaddrinfo_proxy() returns EAI_NODATA on any error.
        rv = EAI_MEMORY;
        LOG(ERROR) << "GetAddrInfoHandler::run: from UID " << uid
                   << ", max concurrent queries reached";
    }

    const int32_t latencyUs = saturate_cast<int32_t>(s.timeTakenUs());
    event.set_latency_micros(latencyUs);
    event.set_event_type(EVENT_GETADDRINFO);
    event.set_hints_ai_flags((mHints ? mHints->ai_flags : 0));

    bool success = true;
    if (rv) {
        // getaddrinfo failed
        success = !mClient->sendBinaryMsg(ResponseCode::DnsProxyOperationFailed, &rv, sizeof(rv));
    } else {
        success = !mClient->sendCode(ResponseCode::DnsProxyQueryResult);
        addrinfo* ai = result;
        while (ai && success) {
            success = sendBE32(mClient, 1) && sendaddrinfo(mClient, ai);
            ai = ai->ai_next;
        }
        success = success && sendBE32(mClient, 0);
    }

    if (!success) {
        PLOG(WARNING) << "GetAddrInfoHandler::run: Error writing DNS result to client uid " << uid
                      << " pid " << mClient->getPid();
    }

    std::vector<std::string> ip_addrs;
    const int total_ip_addr_count = extractGetAddrInfoAnswers(result, &ip_addrs);
    reportDnsEvent(INetdEventListener::EVENT_GETADDRINFO, mNetContext, latencyUs, rv, event, mHost,
                   isUidBlocked, ip_addrs, total_ip_addr_count);
    freeaddrinfo(result);
}

std::string DnsProxyListener::GetAddrInfoHandler::threadName() {
    return makeThreadName(mNetContext.dns_netid, mClient->getUid());
}

namespace {

void addIpAddrWithinLimit(std::vector<std::string>* ip_addrs, const sockaddr* addr,
                          socklen_t addrlen) {
    // ipAddresses array is limited to first INetdEventListener::DNS_REPORTED_IP_ADDRESSES_LIMIT
    // addresses for A and AAAA. Total count of addresses is provided, to be able to tell whether
    // some addresses didn't get logged.
    if (ip_addrs->size() < INetdEventListener::DNS_REPORTED_IP_ADDRESSES_LIMIT) {
        char ip_addr[INET6_ADDRSTRLEN];
        if (getnameinfo(addr, addrlen, ip_addr, sizeof(ip_addr), nullptr, 0, NI_NUMERICHOST) == 0) {
            ip_addrs->push_back(std::string(ip_addr));
        }
    }
}

}  // namespace

DnsProxyListener::GetAddrInfoCmd::GetAddrInfoCmd() : FrameworkCommand("getaddrinfo") {}

int DnsProxyListener::GetAddrInfoCmd::runCommand(SocketClient* cli, int argc, char** argv) {
    logArguments(argc, argv);

    int ai_flags = 0;
    int ai_family = 0;
    int ai_socktype = 0;
    int ai_protocol = 0;
    unsigned netId = 0;
    std::string strErr = "GetAddrInfoCmd::runCommand: ";

    if (argc != 8) {
        strErr = strErr + "invalid number of arguments: " + std::to_string(argc);
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, 0, NULL);
    }

    const std::string name = argv[1];
    const std::string service = argv[2];
    if (!ParseInt(argv[3], &ai_flags))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseInt(argv[4], &ai_family))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseInt(argv[5], &ai_socktype))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseInt(argv[6], &ai_protocol))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseUint(argv[7], &netId))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);

    const bool useLocalNameservers = checkAndClearUseLocalNameserversFlag(&netId);
    const uid_t uid = cli->getUid();

    android_net_context netcontext;
    gResNetdCallbacks.get_network_context(netId, uid, &netcontext);

    if (useLocalNameservers) {
        netcontext.flags |= NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS;
    }

    std::unique_ptr<addrinfo> hints;
    if (ai_flags != -1 || ai_family != -1 || ai_socktype != -1 || ai_protocol != -1) {
        hints.reset((addrinfo*)calloc(1, sizeof(addrinfo)));
        hints->ai_flags = ai_flags;
        hints->ai_family = ai_family;
        hints->ai_socktype = ai_socktype;
        hints->ai_protocol = ai_protocol;
    }

    (new GetAddrInfoHandler(cli, name, service, std::move(hints), netcontext))->spawn();
    return 0;
}

/*******************************************************
 *                  ResNSendCommand                    *
 *******************************************************/
DnsProxyListener::ResNSendCommand::ResNSendCommand() : FrameworkCommand("resnsend") {}

int DnsProxyListener::ResNSendCommand::runCommand(SocketClient* cli, int argc, char** argv) {
    logArguments(argc, argv);

    const uid_t uid = cli->getUid();
    if (argc != 4) {
        LOG(WARNING) << "ResNSendCommand::runCommand: resnsend: from UID " << uid
                     << ", invalid number of arguments to resnsend: " << argc;
        sendBE32(cli, -EINVAL);
        return -1;
    }

    unsigned netId;
    if (!ParseUint(argv[1], &netId)) {
        LOG(WARNING) << "ResNSendCommand::runCommand: resnsend: from UID " << uid
                     << ", invalid netId";
        sendBE32(cli, -EINVAL);
        return -1;
    }

    uint32_t flags;
    if (!ParseUint(argv[2], &flags)) {
        LOG(WARNING) << "ResNSendCommand::runCommand: resnsend: from UID " << uid
                     << ", invalid flags";
        sendBE32(cli, -EINVAL);
        return -1;
    }

    const bool useLocalNameservers = checkAndClearUseLocalNameserversFlag(&netId);

    android_net_context netcontext;
    gResNetdCallbacks.get_network_context(netId, uid, &netcontext);

    if (useLocalNameservers) {
        netcontext.flags |= NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS;
    }

    (new ResNSendHandler(cli, argv[3], flags, netcontext))->spawn();
    return 0;
}

DnsProxyListener::ResNSendHandler::ResNSendHandler(SocketClient* c, std::string msg, uint32_t flags,
                                                   const android_net_context& netcontext)
    : Handler(c), mMsg(std::move(msg)), mFlags(flags), mNetContext(netcontext) {}

void DnsProxyListener::ResNSendHandler::run() {
    LOG(INFO) << "ResNSendHandler::run: " << mFlags << " / {" << mNetContext.toString() << "}";

    Stopwatch s;
    maybeFixupNetContext(&mNetContext, mClient->getPid());

    // Decode
    std::vector<uint8_t> msg(MAXPACKET, 0);

    // Max length of mMsg is less than 1024 since the CMD_BUF_SIZE in FrameworkListener is 1024
    int msgLen = b64_pton(mMsg.c_str(), msg.data(), MAXPACKET);
    if (msgLen == -1) {
        // Decode fail
        sendBE32(mClient, -EILSEQ);
        return;
    }

    const uid_t uid = mClient->getUid();
    int rr_type = 0;
    std::string rr_name;
    uint16_t original_query_id = 0;

    // TODO: Handle the case which is msg contains more than one query
    if (!parseQuery(std::span(msg.data(), msgLen), &original_query_id, &rr_type, &rr_name) ||
        !setQueryId(std::span(msg.data(), msgLen), arc4random_uniform(65536))) {
        // If the query couldn't be parsed, block the request.
        LOG(WARNING) << "ResNSendHandler::run: resnsend: from UID " << uid << ", invalid query";
        sendBE32(mClient, -EINVAL);
        return;
    }

    // Send DNS query
    std::vector<uint8_t> ansBuf(MAXPACKET, 0);
    int rcode = ns_r_noerror;
    int ansLen = -1;
    NetworkDnsEventReported event;
    initDnsEvent(&event, mNetContext);
    const bool isUidBlocked = isUidNetworkingBlocked(mNetContext.uid, mNetContext.dns_netid);
    if (isUidBlocked) {
        LOG(INFO) << "ResNSendHandler::run: network access blocked";
        ansLen = -ECONNREFUSED;
    } else if (startQueryLimiter(uid)) {
        if (evaluate_domain_name(mNetContext, rr_name.c_str())) {
            ansLen = resolv_res_nsend(&mNetContext, std::span(msg.data(), msgLen), ansBuf, &rcode,
                                      static_cast<ResNsendFlags>(mFlags), &event);
        } else {
            // TODO(b/307048182): It should return -errno.
            ansLen = -EAI_SYSTEM;
        }
        endQueryLimiter(uid);
    } else {
        LOG(WARNING) << "ResNSendHandler::run: resnsend: from UID " << uid
                     << ", max concurrent queries reached";
        ansLen = -EBUSY;
    }

    const int32_t latencyUs = saturate_cast<int32_t>(s.timeTakenUs());
    event.set_latency_micros(latencyUs);
    event.set_event_type(EVENT_RES_NSEND);
    event.set_res_nsend_flags(static_cast<ResNsendFlags>(mFlags));

    // Fail, send -errno
    if (ansLen < 0) {
        if (!sendBE32(mClient, ansLen)) {
            PLOG(WARNING) << "ResNSendHandler::run: resnsend: failed to send errno to uid " << uid
                          << " pid " << mClient->getPid();
        }
        if (rr_type == ns_t_a || rr_type == ns_t_aaaa) {
            reportDnsEvent(INetdEventListener::EVENT_RES_NSEND, mNetContext, latencyUs,
                           resNSendToAiError(ansLen, rcode), event, rr_name, isUidBlocked);
        }
        return;
    }

    // Send rcode
    if (!sendBE32(mClient, rcode)) {
        PLOG(WARNING) << "ResNSendHandler::run: resnsend: failed to send rcode to uid " << uid
                      << " pid " << mClient->getPid();
        return;
    }

    // Restore query id
    if (!setQueryId(std::span(ansBuf.data(), ansLen), original_query_id)) {
        LOG(WARNING) << "ResNSendHandler::run: resnsend: failed to restore query id";
        return;
    }

    // Send answer
    if (!sendLenAndData(mClient, ansLen, ansBuf.data())) {
        PLOG(WARNING) << "ResNSendHandler::run: resnsend: failed to send answer to uid " << uid
                      << " pid " << mClient->getPid();
        return;
    }

    if (rr_type == ns_t_a || rr_type == ns_t_aaaa) {
        std::vector<std::string> ip_addrs;
        const int total_ip_addr_count =
                extractResNsendAnswers(std::span(ansBuf.data(), ansLen), rr_type, &ip_addrs);
        reportDnsEvent(INetdEventListener::EVENT_RES_NSEND, mNetContext, latencyUs,
                       resNSendToAiError(ansLen, rcode), event, rr_name, /*skipStats=*/false,
                       ip_addrs, total_ip_addr_count);
    }
}

std::string DnsProxyListener::ResNSendHandler::threadName() {
    return makeThreadName(mNetContext.dns_netid, mClient->getUid());
}

namespace {

bool sendCodeAndBe32(SocketClient* c, int code, int data) {
    return !c->sendCode(code) && sendBE32(c, data);
}

}  // namespace

/*******************************************************
 *                  GetDnsNetId                        *
 *******************************************************/
DnsProxyListener::GetDnsNetIdCommand::GetDnsNetIdCommand() : FrameworkCommand("getdnsnetid") {}

int DnsProxyListener::GetDnsNetIdCommand::runCommand(SocketClient* cli, int argc, char** argv) {
    logArguments(argc, argv);

    const uid_t uid = cli->getUid();
    if (argc != 2) {
        LOG(WARNING) << "GetDnsNetIdCommand::runCommand: getdnsnetid: from UID " << uid
                     << ", invalid number of arguments to getdnsnetid: " << argc;
        sendCodeAndBe32(cli, ResponseCode::DnsProxyQueryResult, -EINVAL);
        return -1;
    }

    unsigned netId;
    if (!ParseUint(argv[1], &netId)) {
        LOG(WARNING) << "GetDnsNetIdCommand::runCommand: getdnsnetid: from UID " << uid
                     << ", invalid netId";
        sendCodeAndBe32(cli, ResponseCode::DnsProxyQueryResult, -EINVAL);
        return -1;
    }

    const bool useLocalNameservers = checkAndClearUseLocalNameserversFlag(&netId);
    android_net_context netcontext;
    gResNetdCallbacks.get_network_context(netId, uid, &netcontext);

    if (useLocalNameservers) {
        netcontext.app_netid |= NETID_USE_LOCAL_NAMESERVERS;
    }

    const bool success =
            sendCodeAndBe32(cli, ResponseCode::DnsProxyQueryResult, netcontext.app_netid);
    if (!success) {
        PLOG(WARNING)
                << "GetDnsNetIdCommand::runCommand: getdnsnetid: failed to send result to uid "
                << uid << " pid " << cli->getPid();
    }

    return success ? 0 : -1;
}

/*******************************************************
 *                  GetHostByName                      *
 *******************************************************/
DnsProxyListener::GetHostByNameCmd::GetHostByNameCmd() : FrameworkCommand("gethostbyname") {}

int DnsProxyListener::GetHostByNameCmd::runCommand(SocketClient* cli, int argc, char** argv) {
    logArguments(argc, argv);

    unsigned netId = 0;
    int af = 0;
    std::string strErr = "GetHostByNameCmd::runCommand: ";

    if (argc != 4) {
        strErr = strErr + "invalid number of arguments: " + std::to_string(argc);
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, 0, NULL);
    }

    if (!ParseUint(argv[1], &netId))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    std::string name = argv[2];
    if (!ParseInt(argv[3], &af))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    uid_t uid = cli->getUid();
    const bool useLocalNameservers = checkAndClearUseLocalNameserversFlag(&netId);

    android_net_context netcontext;
    gResNetdCallbacks.get_network_context(netId, uid, &netcontext);

    if (useLocalNameservers) {
        netcontext.flags |= NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS;
    }

    (new GetHostByNameHandler(cli, name, af, netcontext))->spawn();
    return 0;
}

DnsProxyListener::GetHostByNameHandler::GetHostByNameHandler(SocketClient* c, std::string name,
                                                             int af,
                                                             const android_net_context& netcontext)
    : Handler(c), mName(std::move(name)), mAf(af), mNetContext(netcontext) {}

void DnsProxyListener::GetHostByNameHandler::doDns64Synthesis(int32_t* rv, hostent* hbuf, char* buf,
                                                              size_t buflen, struct hostent** hpp,
                                                              NetworkDnsEventReported* event) {
    // Don't have to consider family AF_UNSPEC case because gethostbyname{, 2} only supports
    // family AF_INET or AF_INET6.
    const bool ipv6WantedButNoData = (mAf == AF_INET6 && *rv == EAI_NODATA);

    if (!ipv6WantedButNoData) {
        return;
    }

    netdutils::IPPrefix prefix{};
    if (!getDns64Prefix(mNetContext.dns_netid, &prefix)) {
        return;
    }

    // If caller wants IPv6 answers but no data, try to query IPv4 answers for synthesis
    const char* name = mName.starts_with('^') ? nullptr : mName.c_str();
    *rv = resolv_gethostbyname(name, AF_INET, hbuf, buf, buflen, &mNetContext, hpp, event);
    if (*rv) {
        *rv = EAI_NODATA;  // return original error code
        return;
    }

    if (!synthesizeNat64PrefixWithARecord(prefix, *hpp)) {
        // If caller wants IPv6 answers but no data and failed to synthesize IPv4 answers,
        // don't return the IPv4 answers.
        *hpp = nullptr;
    }
}

void DnsProxyListener::GetHostByNameHandler::run() {
    LOG(INFO) << "GetHostByNameHandler::run: {" << mNetContext.toString() << "}";
    Stopwatch s;
    maybeFixupNetContext(&mNetContext, mClient->getPid());
    const uid_t uid = mClient->getUid();
    hostent* hp = nullptr;
    hostent hbuf;
    char tmpbuf[MAXPACKET];
    int32_t rv = 0;
    NetworkDnsEventReported event;
    initDnsEvent(&event, mNetContext);
    const bool isUidBlocked = isUidNetworkingBlocked(mNetContext.uid, mNetContext.dns_netid);
    if (isUidBlocked) {
        LOG(INFO) << "GetHostByNameHandler::run: network access blocked";
        rv = EAI_FAIL;
    } else if (startQueryLimiter(uid)) {
        const char* name = mName.starts_with('^') ? nullptr : mName.c_str();
        if (evaluate_domain_name(mNetContext, name)) {
            rv = resolv_gethostbyname(name, mAf, &hbuf, tmpbuf, sizeof tmpbuf, &mNetContext, &hp,
                                      &event);
            doDns64Synthesis(&rv, &hbuf, tmpbuf, sizeof tmpbuf, &hp, &event);
        } else {
            rv = EAI_SYSTEM;
        }
        endQueryLimiter(uid);
    } else {
        rv = EAI_MEMORY;
        LOG(ERROR) << "GetHostByNameHandler::run: from UID " << uid
                   << ", max concurrent queries reached";
    }

    const int32_t latencyUs = saturate_cast<int32_t>(s.timeTakenUs());
    event.set_latency_micros(latencyUs);
    event.set_event_type(EVENT_GETHOSTBYNAME);

    if (rv) {
        LOG(DEBUG) << "GetHostByNameHandler::run: result failed: " << gai_strerror(rv);
    }

    bool success = true;
    if (hp) {
        // hp is not nullptr iff. rv is 0.
        success = mClient->sendCode(ResponseCode::DnsProxyQueryResult) == 0;
        success &= sendhostent(mClient, hp);
    } else {
        success = mClient->sendBinaryMsg(ResponseCode::DnsProxyOperationFailed, nullptr, 0) == 0;
    }

    if (!success) {
        PLOG(WARNING) << "GetHostByNameHandler::run: Error writing DNS result to client uid " << uid
                      << " pid " << mClient->getPid();
    }

    std::vector<std::string> ip_addrs;
    const int total_ip_addr_count = extractGetHostByNameAnswers(hp, &ip_addrs);
    reportDnsEvent(INetdEventListener::EVENT_GETHOSTBYNAME, mNetContext, latencyUs, rv, event,
                   mName, isUidBlocked, ip_addrs, total_ip_addr_count);
}

std::string DnsProxyListener::GetHostByNameHandler::threadName() {
    return makeThreadName(mNetContext.dns_netid, mClient->getUid());
}

/*******************************************************
 *                  GetHostByAddr                      *
 *******************************************************/
DnsProxyListener::GetHostByAddrCmd::GetHostByAddrCmd() : FrameworkCommand("gethostbyaddr") {}

int DnsProxyListener::GetHostByAddrCmd::runCommand(SocketClient* cli, int argc, char** argv) {
    logArguments(argc, argv);
    int addrLen = 0;
    int addrFamily = 0;
    unsigned netId = 0;
    std::string strErr = "GetHostByAddrCmd::runCommand: ";

    if (argc != 5) {
        strErr = strErr + "invalid number of arguments: " + std::to_string(argc);
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, 0, NULL);
    }

    char* addrStr = argv[1];
    if (!ParseInt(argv[2], &addrLen))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseInt(argv[3], &addrFamily))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    if (!ParseUint(argv[4], &netId))
        return HandleArgumentError(cli, ResponseCode::CommandParameterError, strErr, argc, argv);
    uid_t uid = cli->getUid();
    const bool useLocalNameservers = checkAndClearUseLocalNameserversFlag(&netId);

    in6_addr addr;
    errno = 0;
    int result = inet_pton(addrFamily, addrStr, &addr);
    if (result <= 0) {
        strErr = strErr + "inet_pton(\"" + addrStr + "\") failed " + strerror(errno);
        return HandleArgumentError(cli, ResponseCode::OperationFailed, strErr, 0, NULL);
    }

    android_net_context netcontext;
    gResNetdCallbacks.get_network_context(netId, uid, &netcontext);

    if (useLocalNameservers) {
        netcontext.flags |= NET_CONTEXT_FLAG_USE_LOCAL_NAMESERVERS;
    }

    (new GetHostByAddrHandler(cli, addr, addrLen, addrFamily, netcontext))->spawn();
    return 0;
}

DnsProxyListener::GetHostByAddrHandler::GetHostByAddrHandler(SocketClient* c, in6_addr address,
                                                             int addressLen, int addressFamily,
                                                             const android_net_context& netcontext)
    : Handler(c),
      mAddress(address),
      mAddressLen(addressLen),
      mAddressFamily(addressFamily),
      mNetContext(netcontext) {}

void DnsProxyListener::GetHostByAddrHandler::doDns64ReverseLookup(hostent* hbuf, char* buf,
                                                                  size_t buflen,
                                                                  struct hostent** hpp,
                                                                  NetworkDnsEventReported* event) {
    if (*hpp != nullptr || mAddressFamily != AF_INET6) {
        return;
    }

    netdutils::IPPrefix prefix{};
    if (!getDns64Prefix(mNetContext.dns_netid, &prefix)) {
        return;
    }

    if (!isValidNat64Prefix(prefix)) {
        return;
    }

    struct sockaddr_storage ss = netdutils::IPSockAddr(prefix.ip());
    struct sockaddr_in6* v6prefix = (struct sockaddr_in6*)&ss;
    struct in6_addr v6addr = mAddress;
    // Check if address has NAT64 prefix. Only /96 IPv6 NAT64 prefixes are supported
    if ((v6addr.s6_addr32[0] != v6prefix->sin6_addr.s6_addr32[0]) ||
        (v6addr.s6_addr32[1] != v6prefix->sin6_addr.s6_addr32[1]) ||
        (v6addr.s6_addr32[2] != v6prefix->sin6_addr.s6_addr32[2])) {
        return;
    }

    // Remove NAT64 prefix and do reverse DNS query
    struct in_addr v4addr = {.s_addr = v6addr.s6_addr32[3]};
    resolv_gethostbyaddr(&v4addr, sizeof(v4addr), AF_INET, hbuf, buf, buflen, &mNetContext, hpp,
                         event);
    if (*hpp && (*hpp)->h_addr_list[0]) {
        // Replace IPv4 address with original queried IPv6 address in place. The space has
        // reserved by dns_gethtbyaddr() and netbsd_gethostent_r() in
        // system/netd/resolv/gethnamaddr.cpp.
        // Note that resolv_gethostbyaddr() returns only one entry in result.
        char* addr = (*hpp)->h_addr_list[0];
        memcpy(addr, &v6addr, sizeof(v6addr));
        (*hpp)->h_addrtype = AF_INET6;
        (*hpp)->h_length = sizeof(struct in6_addr);
    } else {
        LOG(ERROR) << __func__ << ": hpp or (*hpp)->h_addr_list[0] is null";
    }
}

void DnsProxyListener::GetHostByAddrHandler::run() {
    LOG(INFO) << "GetHostByAddrHandler::run: {" << mNetContext.toString() << "}";
    Stopwatch s;
    maybeFixupNetContext(&mNetContext, mClient->getPid());
    const uid_t uid = mClient->getUid();
    hostent* hp = nullptr;
    hostent hbuf;
    char tmpbuf[MAXPACKET];
    int32_t rv = 0;
    NetworkDnsEventReported event;
    initDnsEvent(&event, mNetContext);

    const bool isUidBlocked = isUidNetworkingBlocked(mNetContext.uid, mNetContext.dns_netid);
    if (isUidBlocked) {
        LOG(INFO) << "GetHostByAddrHandler::run: network access blocked";
        rv = EAI_FAIL;
    } else if (startQueryLimiter(uid)) {
        // From Android U, evaluate_domain_name() is not only for OEM customization, but also tells
        // DNS resolver whether the UID can send DNS on the specified network. The function needs
        // to be called even when there is no domain name to evaluate (GetHostByAddr). This is
        // applied on U+ only so that the behavior won’t change on T- OEM devices.
        // TODO: pass the actual name into evaluate_domain_name, e.g., 238.26.217.172.in-addr.arpa
        //       when the lookup address is 172.217.26.238.
        if (isAtLeastU() && !evaluate_domain_name(mNetContext, nullptr)) {
            rv = EAI_SYSTEM;
        } else {
            rv = resolv_gethostbyaddr(&mAddress, mAddressLen, mAddressFamily, &hbuf, tmpbuf,
                                      sizeof tmpbuf, &mNetContext, &hp, &event);
            doDns64ReverseLookup(&hbuf, tmpbuf, sizeof tmpbuf, &hp, &event);
        }
        endQueryLimiter(uid);
    } else {
        rv = EAI_MEMORY;
        LOG(ERROR) << "GetHostByAddrHandler::run: from UID " << uid
                   << ", max concurrent queries reached";
    }

    const int32_t latencyUs = saturate_cast<int32_t>(s.timeTakenUs());
    event.set_latency_micros(latencyUs);
    event.set_event_type(EVENT_GETHOSTBYADDR);

    if (rv) {
        LOG(DEBUG) << "GetHostByAddrHandler::run: result failed: " << gai_strerror(rv);
    }

    bool success = true;
    if (hp) {
        success = mClient->sendCode(ResponseCode::DnsProxyQueryResult) == 0;
        success &= sendhostent(mClient, hp);
    } else {
        success = mClient->sendBinaryMsg(ResponseCode::DnsProxyOperationFailed, nullptr, 0) == 0;
    }

    if (!success) {
        PLOG(WARNING) << "GetHostByAddrHandler::run: Error writing DNS result to client uid " << uid
                      << " pid " << mClient->getPid();
    }

    reportDnsEvent(INetdEventListener::EVENT_GETHOSTBYADDR, mNetContext, latencyUs, rv, event,
                   (hp && hp->h_name) ? hp->h_name : "null", isUidBlocked, {}, 0);
}

std::string DnsProxyListener::GetHostByAddrHandler::threadName() {
    return makeThreadName(mNetContext.dns_netid, mClient->getUid());
}

}  // namespace net
}  // namespace android
