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

#define LOG_TAG "resolv"

#include "DnsTlsDispatcher.h"

#include <netdutils/Stopwatch.h>

#include "DnsTlsSocketFactory.h"
#include "Experiments.h"
#include "PrivateDnsConfiguration.h"
#include "resolv_cache.h"
#include "resolv_private.h"
#include "stats.pb.h"

#include <android-base/logging.h>

namespace android {
namespace net {

using android::netdutils::IPSockAddr;
using android::netdutils::Stopwatch;
using netdutils::Slice;

// static
std::mutex DnsTlsDispatcher::sLock;

DnsTlsDispatcher::DnsTlsDispatcher() {
    mFactory.reset(new DnsTlsSocketFactory());
}

DnsTlsDispatcher& DnsTlsDispatcher::getInstance() {
    static DnsTlsDispatcher instance;
    return instance;
}

std::list<DnsTlsServer> DnsTlsDispatcher::getOrderedAndUsableServerList(
        const std::list<DnsTlsServer>& tlsServers, unsigned netId, unsigned mark) {
    // Our preferred DnsTlsServer order is:
    //     1) reuse existing IPv6 connections
    //     2) reuse existing IPv4 connections
    //     3) establish new IPv6 connections
    //     4) establish new IPv4 connections
    std::list<DnsTlsServer> existing6;
    std::list<DnsTlsServer> existing4;
    std::list<DnsTlsServer> new6;
    std::list<DnsTlsServer> new4;

    // Pull out any servers for which we might have existing connections and
    // place them at the from the list of servers to try.
    {
        std::lock_guard guard(sLock);

        for (const auto& tlsServer : tlsServers) {
            const Key key = std::make_pair(mark, tlsServer);
            if (Transport* xport = getTransport(key); xport != nullptr) {
                if (!xport->usable()) {
                    // Don't use this xport. It will be removed after timeout
                    // (IDLE_TIMEOUT minutes).
                    LOG(DEBUG) << "Skip using DoT server " << tlsServer.toString() << " on "
                               << netId;
                    continue;
                }

                switch (tlsServer.ss.ss_family) {
                    case AF_INET:
                        existing4.push_back(tlsServer);
                        break;
                    case AF_INET6:
                        existing6.push_back(tlsServer);
                        break;
                }
            } else {
                switch (tlsServer.ss.ss_family) {
                    case AF_INET:
                        new4.push_back(tlsServer);
                        break;
                    case AF_INET6:
                        new6.push_back(tlsServer);
                        break;
                }
            }
        }
    }

    auto& out = existing6;
    out.splice(out.cend(), existing4);
    out.splice(out.cend(), new6);
    out.splice(out.cend(), new4);
    return out;
}

DnsTlsTransport::Response DnsTlsDispatcher::query(const std::list<DnsTlsServer>& tlsServers,
                                                  ResState* statp, const Slice query,
                                                  const Slice ans, int* resplen,
                                                  bool dotQuickFallback) {
    const std::list<DnsTlsServer> servers(
            getOrderedAndUsableServerList(tlsServers, statp->netid, statp->mark));

    if (servers.empty()) {
        LOG(WARNING) << "No usable DnsTlsServers";

        // Call maybeCleanup so the expired Transports can be removed as expected.
        std::lock_guard guard(sLock);
        maybeCleanup(std::chrono::steady_clock::now());
    }

    DnsTlsTransport::Response code = DnsTlsTransport::Response::internal_error;
    int serverCount = 0;
    for (const auto& server : servers) {
        DnsQueryEvent* dnsQueryEvent =
                statp->event->mutable_dns_query_events()->add_dns_query_event();

        bool connectTriggered = false;
        Stopwatch queryStopwatch;
        code = this->query(server, statp->netid, statp->mark, query, ans, resplen,
                           &connectTriggered);

        dnsQueryEvent->set_latency_micros(saturate_cast<int32_t>(queryStopwatch.timeTakenUs()));
        dnsQueryEvent->set_dns_server_index(serverCount++);
        dnsQueryEvent->set_ip_version(ipFamilyToIPVersion(server.ss.ss_family));
        dnsQueryEvent->set_protocol(PROTO_DOT);
        std::span<const uint8_t> msg(query.base(), query.size());
        dnsQueryEvent->set_type(getQueryType(msg));
        dnsQueryEvent->set_connected(connectTriggered);

        switch (code) {
            // These response codes are valid responses and not expected to
            // change if another server is queried.
            case DnsTlsTransport::Response::success:
                dnsQueryEvent->set_rcode(
                        static_cast<NsRcode>(reinterpret_cast<HEADER*>(ans.base())->rcode));
                resolv_stats_add(statp->netid, IPSockAddr::toIPSockAddr(server.ss), dnsQueryEvent);
                return code;
            case DnsTlsTransport::Response::limit_error:
                dnsQueryEvent->set_rcode(NS_R_INTERNAL_ERROR);
                resolv_stats_add(statp->netid, IPSockAddr::toIPSockAddr(server.ss), dnsQueryEvent);
                return code;
            // These response codes might differ when trying other servers, so
            // keep iterating to see if we can get a different (better) result.
            case DnsTlsTransport::Response::network_error:
                // Sync from res_tls_send in res_send.cpp
                dnsQueryEvent->set_rcode(NS_R_TIMEOUT);
                resolv_stats_add(statp->netid, IPSockAddr::toIPSockAddr(server.ss), dnsQueryEvent);
                if (dotQuickFallback) {
                    return code;
                }
                break;
            case DnsTlsTransport::Response::internal_error:
                dnsQueryEvent->set_rcode(NS_R_INTERNAL_ERROR);
                resolv_stats_add(statp->netid, IPSockAddr::toIPSockAddr(server.ss), dnsQueryEvent);
                break;
            // No "default" statement.
        }
    }

    return code;
}

DnsTlsTransport::Response DnsTlsDispatcher::query(const DnsTlsServer& server, unsigned netId,
                                                  unsigned mark, const Slice query, const Slice ans,
                                                  int* resplen, bool* connectTriggered) {
    // TODO: This can cause the resolver to create multiple connections to the same DoT server
    // merely due to different mark, such as the bit explicitlySelected unset.
    // See if we can save them and just create one connection for one DoT server.
    const Key key = std::make_pair(mark, server);
    Transport* xport;
    {
        std::lock_guard guard(sLock);
        if (xport = getTransport(key); xport == nullptr) {
            xport = addTransport(server, mark, netId);
        }
        ++xport->useCount;
    }

    // Don't call this function and hold sLock at the same time because of the following reason:
    // TLS handshake requires a lock which is also needed by this function, if the handshake gets
    // stuck, this function also gets blocked.
    const int connectCounter = xport->transport.getConnectCounter();

    const auto& result = queryInternal(*xport, query);
    *connectTriggered = (xport->transport.getConnectCounter() > connectCounter);

    DnsTlsTransport::Response code = result.code;
    if (code == DnsTlsTransport::Response::success) {
        if (result.response.size() > ans.size()) {
            LOG(DEBUG) << "Response too large: " << result.response.size() << " > " << ans.size();
            code = DnsTlsTransport::Response::limit_error;
        } else {
            LOG(DEBUG) << "Got response successfully";
            *resplen = result.response.size();
            netdutils::copy(ans, netdutils::makeSlice(result.response));
        }
    } else {
        LOG(DEBUG) << "Query failed: " << (unsigned int)code;
    }

    auto now = std::chrono::steady_clock::now();
    {
        std::lock_guard guard(sLock);
        --xport->useCount;
        xport->lastUsed = now;
        if (code == DnsTlsTransport::Response::network_error) {
            xport->continuousfailureCount++;
        } else {
            xport->continuousfailureCount = 0;
        }

        // DoT revalidation specific feature.
        if (xport->checkRevalidationNecessary()) {
            // Even if the revalidation passes, it doesn't guarantee that DoT queries
            // to the xport can stop failing because revalidation creates a new connection
            // to probe while the xport still uses an existing connection. So far, there isn't
            // a feasible way to force the xport to disconnect the connection. If the case
            // happens, the xport will be marked as unusable and DoT queries won't be sent to
            // it anymore. Eventually, after IDLE_TIMEOUT, the xport will be destroyed, and
            // a new xport will be created.
            const auto result = PrivateDnsConfiguration::getInstance().requestDotValidation(
                    netId, PrivateDnsConfiguration::ServerIdentity{server}, mark);
            LOG(WARNING) << "Requested validation for " << server.toString() << " with mark 0x"
                         << std::hex << mark << ", "
                         << (result.ok() ? "succeeded" : "failed: " + result.error().message());
        }

        maybeCleanup(now);
    }
    return code;
}

void DnsTlsDispatcher::forceCleanup(unsigned netId) {
    std::lock_guard guard(sLock);
    cleanup(std::chrono::steady_clock::now(), netId);
}

DnsTlsTransport::Result DnsTlsDispatcher::queryInternal(Transport& xport,
                                                        const netdutils::Slice query) {
    LOG(DEBUG) << "Sending query of length " << query.size();

    // If dot_async_handshake is not set, the call might block in some cases; otherwise,
    // the call should return very soon.
    auto res = xport.transport.query(query);
    LOG(DEBUG) << "Awaiting response";

    if (xport.timeout().count() == -1) {
        // Infinite timeout.
        return res.get();
    }

    const auto status = res.wait_for(xport.timeout());
    if (status == std::future_status::timeout) {
        // TODO(b/186613628): notify the Transport to remove this query.
        LOG(WARNING) << "DoT query timed out after " << xport.timeout().count() << " ms";
        return DnsTlsTransport::Result{
                .code = DnsTlsTransport::Response::network_error,
                .response = {},
        };
    }

    return res.get();
}

// This timeout effectively controls how long to keep SSL session tickets.
static constexpr std::chrono::minutes IDLE_TIMEOUT(5);
void DnsTlsDispatcher::maybeCleanup(std::chrono::time_point<std::chrono::steady_clock> now) {
    // To avoid scanning mStore after every query, return early if a cleanup has been
    // performed recently.
    if (now - mLastCleanup < IDLE_TIMEOUT) {
        return;
    }
    cleanup(now, std::nullopt);
    mLastCleanup = now;
}

void DnsTlsDispatcher::cleanup(std::chrono::time_point<std::chrono::steady_clock> now,
                               std::optional<unsigned> netId) {
    std::erase_if(mStore, [&](const auto& item) REQUIRES(sLock) {
        auto const& [_, xport] = item;
        if (xport->useCount == 0) {
            if (netId.has_value() && xport->mNetId == netId.value()) return true;
            if (now - xport->lastUsed > IDLE_TIMEOUT) return true;
        }
        return false;
    });
}

DnsTlsDispatcher::Transport* DnsTlsDispatcher::addTransport(const DnsTlsServer& server,
                                                            unsigned mark, unsigned netId) {
    const Key key = std::make_pair(mark, server);
    Transport* ret = getTransport(key);
    if (ret != nullptr) return ret;

    const Experiments* const instance = Experiments::getInstance();
    int triggerThr =
            instance->getFlag("dot_revalidation_threshold", Transport::kDotRevalidationThreshold);
    int unusableThr = instance->getFlag("dot_xport_unusable_threshold",
                                        Transport::kDotXportUnusableThreshold);
    int queryTimeout = instance->getFlag("dot_query_timeout_ms", Transport::kDotQueryTimeoutMs);

    // Check and adjust the parameters if they are improperly set.
    const bool isForOpportunisticMode = server.name.empty();
    if (triggerThr <= 0 || !isForOpportunisticMode) {
        triggerThr = -1;
    }
    if (unusableThr <= 0 || !isForOpportunisticMode) {
        unusableThr = -1;
    }
    if (queryTimeout < 0) {
        queryTimeout = -1;
    } else if (queryTimeout < 1000) {
        queryTimeout = 1000;
    }

    ret = new Transport(server, mark, netId, mFactory.get(), triggerThr, unusableThr, queryTimeout);
    LOG(INFO) << "Transport is initialized with { " << triggerThr << ", " << unusableThr << ", "
              << queryTimeout << "ms }"
              << " for server " << server.toString();

    mStore[key].reset(ret);

    return ret;
}

DnsTlsDispatcher::Transport* DnsTlsDispatcher::getTransport(const Key& key) {
    auto it = mStore.find(key);
    return (it == mStore.end() ? nullptr : it->second.get());
}

bool DnsTlsDispatcher::Transport::checkRevalidationNecessary() {
    if (triggerThreshold <= 0) return false;
    if (continuousfailureCount < triggerThreshold) return false;
    if (isRevalidationThresholdReached) return false;

    isRevalidationThresholdReached = true;
    return true;
}

bool DnsTlsDispatcher::Transport::usable() {
    if (unusableThreshold <= 0) return true;

    if (continuousfailureCount >= unusableThreshold) {
        // Once reach the threshold, mark this Transport as unusable.
        isXportUnusableThresholdReached = true;
    }
    return !isXportUnusableThresholdReached;
}

}  // end of namespace net
}  // end of namespace android
