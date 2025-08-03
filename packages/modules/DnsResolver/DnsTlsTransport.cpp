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

#include "DnsTlsTransport.h"

#include <span>

#include <android-base/format.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <arpa/inet.h>
#include <arpa/nameser.h>
#include <netdutils/Stopwatch.h>
#include <netdutils/ThreadUtil.h>
#include <private/android_filesystem_config.h>  // AID_DNS
#include <sys/poll.h>

#include "DnsTlsSocketFactory.h"
#include "Experiments.h"
#include "IDnsTlsSocketFactory.h"
#include "resolv_private.h"
#include "util.h"

using android::netdutils::setThreadName;

namespace android {
namespace net {

namespace {

// Make a DNS query for the hostname "<random>-dnsotls-ds.metric.gstatic.com".
std::vector<uint8_t> makeDnsQuery() {
    static const char kDnsSafeChars[] =
            "abcdefhijklmnopqrstuvwxyz"
            "ABCDEFHIJKLMNOPQRSTUVWXYZ"
            "0123456789";
    const auto c = [](uint8_t rnd) -> uint8_t {
        return kDnsSafeChars[(rnd % std::size(kDnsSafeChars))];
    };
    uint8_t rnd[8];
    arc4random_buf(rnd, std::size(rnd));

    return std::vector<uint8_t>{
            rnd[6], rnd[7],  // [0-1]   query ID
            1,      0,       // [2-3]   flags; query[2] = 1 for recursion desired (RD).
            0,      1,       // [4-5]   QDCOUNT (number of queries)
            0,      0,       // [6-7]   ANCOUNT (number of answers)
            0,      0,       // [8-9]   NSCOUNT (number of name server records)
            0,      0,       // [10-11] ARCOUNT (number of additional records)
            17,     c(rnd[0]), c(rnd[1]), c(rnd[2]), c(rnd[3]), c(rnd[4]), c(rnd[5]), '-', 'd', 'n',
            's',    'o',       't',       'l',       's',       '-',       'd',       's', 6,   'm',
            'e',    't',       'r',       'i',       'c',       7,         'g',       's', 't', 'a',
            't',    'i',       'c',       3,         'c',       'o',       'm',
            0,                  // null terminator of FQDN (root TLD)
            0,      ns_t_aaaa,  // QTYPE
            0,      ns_c_in     // QCLASS
    };
}

base::Result<void> checkDnsResponse(const std::span<const uint8_t> answer) {
    if (answer.size() < NS_HFIXEDSZ) {
        return Errorf("short response: {}", answer.size());
    }

    const int qdcount = (answer[4] << 8) | answer[5];
    if (qdcount != 1) {
        return Errorf("reply query count != 1: {}", qdcount);
    }

    const int ancount = (answer[6] << 8) | answer[7];
    LOG(DEBUG) << "answer count: " << ancount;

    // TODO: Further validate the response contents (check for valid AAAA record, ...).
    // Note that currently, integration tests rely on this function accepting a
    // response with zero records.

    return {};
}

// Sends |query| to the given server, and returns the DNS response.
base::Result<void> sendUdpQuery(netdutils::IPAddress ip, uint32_t mark,
                                std::span<const uint8_t> query) {
    const sockaddr_storage ss = netdutils::IPSockAddr(ip, 53);
    const sockaddr* nsap = reinterpret_cast<const sockaddr*>(&ss);
    const int nsaplen = sockaddrSize(nsap);
    const int sockType = SOCK_DGRAM | SOCK_NONBLOCK | SOCK_CLOEXEC;
    android::base::unique_fd fd{socket(nsap->sa_family, sockType, 0)};
    if (fd < 0) {
        return ErrnoErrorf("socket failed");
    }

    resolv_tag_socket(fd.get(), AID_DNS, NET_CONTEXT_INVALID_PID);
    if (setsockopt(fd.get(), SOL_SOCKET, SO_MARK, &mark, sizeof(mark)) < 0) {
        return ErrnoErrorf("setsockopt failed");
    }

    if (connect(fd.get(), nsap, (socklen_t)nsaplen) < 0) {
        return ErrnoErrorf("connect failed");
    }

    if (send(fd, query.data(), query.size(), 0) != static_cast<ptrdiff_t>(query.size())) {
        return ErrnoErrorf("send failed");
    }

    const int timeoutMs = 3000;
    while (true) {
        pollfd fds = {.fd = fd, .events = POLLIN};

        const int n = TEMP_FAILURE_RETRY(poll(&fds, 1, timeoutMs));
        if (n == 0) {
            return Errorf("poll timed out");
        }
        if (n < 0) {
            return ErrnoErrorf("poll failed");
        }
        if (fds.revents & (POLLIN | POLLERR)) {
            std::vector<uint8_t> buf(MAXPACKET);
            const int resplen = recv(fd, buf.data(), buf.size(), 0);

            if (resplen < 0) {
                return ErrnoErrorf("recvfrom failed");
            }

            buf.resize(resplen);
            if (auto result = checkDnsResponse(buf); !result.ok()) {
                return Errorf("checkDnsResponse failed: {}", result.error().message());
            }

            return {};
        }
    }
}

}  // namespace

std::future<DnsTlsTransport::Result> DnsTlsTransport::query(const netdutils::Slice query) {
    std::lock_guard guard(mLock);

    auto record = mQueries.recordQuery(query);
    if (!record) {
        return std::async(std::launch::deferred, []{
            return (Result) { .code = Response::internal_error };
        });
    }

    if (!mSocket) {
        LOG(DEBUG) << "No socket for query.  Opening socket and sending.";
        doConnect();
    } else {
        sendQuery(record->query);
    }

    return std::move(record->result);
}

int DnsTlsTransport::getConnectCounter() const {
    std::lock_guard guard(mLock);
    return mConnectCounter;
}

bool DnsTlsTransport::sendQuery(const DnsTlsQueryMap::Query& q) {
    // Strip off the ID number and send the new ID instead.
    const bool sent = mSocket->query(q.newId, netdutils::drop(netdutils::makeSlice(q.query), 2));
    if (sent) {
        mQueries.markTried(q.newId);
    }
    return sent;
}

void DnsTlsTransport::doConnect() {
    LOG(DEBUG) << "Constructing new socket";
    mSocket = mFactory->createDnsTlsSocket(mServer, mMark, this, &mCache);

    bool success = true;
    if (mSocket.get() == nullptr || !mSocket->startHandshake()) {
        success = false;
    }
    mConnectCounter++;

    if (success) {
        auto queries = mQueries.getAll();
        LOG(DEBUG) << "Initialization succeeded.  Reissuing " << queries.size() << " queries.";
        for(auto& q : queries) {
            if (!sendQuery(q)) {
                break;
            }
        }
    } else {
        LOG(DEBUG) << "Initialization failed.";
        mSocket.reset();
        LOG(DEBUG) << "Failing all pending queries.";
        mQueries.clear();
    }
}

void DnsTlsTransport::onResponse(std::vector<uint8_t> response) {
    mQueries.onResponse(std::move(response));
}

void DnsTlsTransport::onClosed() {
    std::lock_guard guard(mLock);
    if (mClosing) {
        return;
    }
    // Move remaining operations to a new thread.
    // This is necessary because
    // 1. onClosed is currently running on a thread that blocks mSocket's destructor
    // 2. doReconnect will call that destructor
    if (mReconnectThread) {
        // Complete cleanup of a previous reconnect thread, if present.
        mReconnectThread->join();
        // Joining a thread that is trying to acquire mLock, while holding mLock,
        // looks like it risks a deadlock.  However, a deadlock will not occur because
        // once onClosed is called, it cannot be called again until after doReconnect
        // acquires mLock.
    }
    mReconnectThread.reset(new std::thread(&DnsTlsTransport::doReconnect, this));
}

void DnsTlsTransport::doReconnect() {
    std::lock_guard guard(mLock);
    setThreadName(fmt::format("TlsReconn_{}", mMark & 0xffff));
    if (mClosing) {
        return;
    }
    mQueries.cleanup();
    if (!mQueries.empty()) {
        LOG(DEBUG) << "Fast reconnect to retry remaining queries";
        doConnect();
    } else {
        LOG(DEBUG) << "No pending queries.  Going idle.";
        mSocket.reset();
    }
}

DnsTlsTransport::~DnsTlsTransport() {
    LOG(DEBUG) << "Destructor";
    {
        std::lock_guard guard(mLock);
        LOG(DEBUG) << "Locked destruction procedure";
        mQueries.clear();
        mClosing = true;
    }
    // It's possible that a reconnect thread was spawned and waiting for mLock.
    // It's safe for that thread to run now because mClosing is true (and mQueries is empty),
    // but we need to wait for it to finish before allowing destruction to proceed.
    if (mReconnectThread) {
        LOG(DEBUG) << "Waiting for reconnect thread to terminate";
        mReconnectThread->join();
        mReconnectThread.reset();
    }
    // Ensure that the socket is destroyed, and can clean up its callback threads,
    // before any of this object's fields become invalid.
    mSocket.reset();
    LOG(DEBUG) << "Destructor completed";
}

// static
// TODO: Use this function to preheat the session cache.
// That may require moving it to DnsTlsDispatcher.
bool DnsTlsTransport::validate(const DnsTlsServer& server, uint32_t mark) {
    LOG(DEBUG) << "Beginning validation with mark " << std::hex << mark;

    const std::vector<uint8_t> query = makeDnsQuery();
    DnsTlsSocketFactory factory;
    DnsTlsTransport transport(server, mark, &factory);

    // Send the initial query to warm up the connection.
    auto r = transport.query(netdutils::makeSlice(query)).get();
    if (r.code != Response::success) {
        LOG(WARNING) << "query failed";
        return false;
    }

    if (auto result = checkDnsResponse(r.response); !result.ok()) {
        LOG(WARNING) << "checkDnsResponse failed: " << result.error().message();
        return false;
    }

    // If this validation is not for opportunistic mode, or the flags are not properly set,
    // the validation is done. If not, the validation will compare DoT probe latency and
    // UDP probe latency, and it will pass if:
    //   dot_probe_latency < latencyFactor * udp_probe_latency + latencyOffsetMs
    //
    // For instance, with latencyFactor = 3 and latencyOffsetMs = 10, if UDP probe latency is 5 ms,
    // DoT probe latency must less than 25 ms.
    const bool isAtLeastR = getApiLevel() >= 30;
    int latencyFactor = Experiments::getInstance()->getFlag("dot_validation_latency_factor",
                                                            (isAtLeastR ? 3 : -1));
    int latencyOffsetMs = Experiments::getInstance()->getFlag("dot_validation_latency_offset_ms",
                                                              (isAtLeastR ? 100 : -1));
    const bool shouldCompareUdpLatency =
            server.name.empty() &&
            (latencyFactor >= 0 && latencyOffsetMs >= 0 && latencyFactor + latencyOffsetMs != 0);
    if (!shouldCompareUdpLatency) {
        return true;
    }

    LOG(INFO) << fmt::format("Use flags: latencyFactor={}, latencyOffsetMs={}", latencyFactor,
                             latencyOffsetMs);

    int64_t udpProbeTimeUs = 0;
    bool udpProbeGotAnswer = false;
    std::thread udpProbeThread([&] {
        // Can issue another probe if the first one fails or is lost.
        for (int i = 1; i < 3; i++) {
            netdutils::Stopwatch stopwatch;
            auto result = sendUdpQuery(server.addr().ip(), mark, query);
            udpProbeTimeUs = stopwatch.timeTakenUs();
            udpProbeGotAnswer = result.ok();
            LOG(INFO) << fmt::format("UDP probe for {} {}, took {:.3f}ms", server.toIpString(),
                                     (udpProbeGotAnswer ? "succeeded" : "failed"),
                                     udpProbeTimeUs / 1000.0);

            if (udpProbeGotAnswer) {
                break;
            }
            LOG(WARNING) << "sendUdpQuery attempt " << i << " failed: " << result.error().message();
        }
    });

    int64_t dotProbeTimeUs = 0;
    bool dotProbeGotAnswer = false;
    std::thread dotProbeThread([&] {
        netdutils::Stopwatch stopwatch;
        auto r = transport.query(netdutils::makeSlice(query)).get();
        dotProbeTimeUs = stopwatch.timeTakenUs();

        if (r.code != Response::success) {
            LOG(WARNING) << "query failed";
        } else {
            if (auto result = checkDnsResponse(r.response); !result.ok()) {
                LOG(WARNING) << "checkDnsResponse failed: " << result.error().message();
            } else {
                dotProbeGotAnswer = true;
            }
        }

        LOG(INFO) << fmt::format("DoT probe for {} {}, took {:.3f}ms", server.toIpString(),
                                 (dotProbeGotAnswer ? "succeeded" : "failed"),
                                 dotProbeTimeUs / 1000.0);
    });

    // TODO: If DoT probe thread finishes before UDP probe thread and dotProbeGotAnswer is false,
    // actively cancel UDP probe thread.
    dotProbeThread.join();
    udpProbeThread.join();

    if (!dotProbeGotAnswer) return false;
    if (!udpProbeGotAnswer) return true;
    return dotProbeTimeUs < (latencyFactor * udpProbeTimeUs + latencyOffsetMs * 1000);
}

}  // end of namespace net
}  // end of namespace android
