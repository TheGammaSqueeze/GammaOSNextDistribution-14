/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */

#pragma once

#include <map>
#include <thread>

#include <netinet/ip.h>

#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <netdutils/Slice.h>

namespace android::net {

// Given a TUN interface fd, TunForwarder reads packets from the fd, changes their IP header
// according to a set of forwarding rules (which can be set by addForwardingRule), and sends
// new packets back to the fd. Only IPv4 and IPv6 packets with recognized source and destination
// addresses are accepted; other packets are silently ignored.
class TunForwarder {
  public:
    TunForwarder(base::unique_fd tunFd);
    ~TunForwarder();

    bool addForwardingRule(const std::array<std::string, 2>& from,
                           const std::array<std::string, 2>& to);
    bool startForwarding();
    bool stopForwarding();

    static base::unique_fd createTun(const std::string& ifname);

  private:
    // TODO: Considering using IPAddress for v4pair and v6pair. This might requires adding
    // addr4() and addr6() as IPPrefix does.
    struct v4pair {
        static base::Result<v4pair> makePair(const std::array<std::string, 2>& addrs);
        v4pair() = default;
        v4pair(int32_t srcAddr, int32_t dstAddr) {
            src.s_addr = static_cast<in_addr_t>(srcAddr);
            dst.s_addr = static_cast<in_addr_t>(dstAddr);
        }
        in_addr src;
        in_addr dst;
        bool operator==(const v4pair& o) const;
        bool operator<(const v4pair& o) const;
    };

    struct v6pair {
        static base::Result<v6pair> makePair(const std::array<std::string, 2>& addrs);
        v6pair() = default;
        v6pair(const in6_addr& srcAddr, const in6_addr& dstAddr) : src(srcAddr), dst(dstAddr) {}
        in6_addr src;
        in6_addr dst;
        bool operator==(const v6pair& o) const;
        bool operator<(const v6pair& o) const;
    };

    void loop();
    void handlePacket(int fd) const;

    // Send a signal to terminate the loop thread.
    bool signalEventFd();

    // A series of functions to check the packet. Return error if the packet is neither UDP nor TCP.
    base::Result<void> validatePacket(netdutils::Slice tunPacket) const;
    base::Result<void> validateIpv4Packet(netdutils::Slice ipv4Packet) const;
    base::Result<void> validateIpv6Packet(netdutils::Slice ipv6Packet) const;
    base::Result<void> validateUdpPacket(netdutils::Slice udpPacket) const;
    base::Result<void> validateTcpPacket(netdutils::Slice tcpPacket) const;

    // The function assumes |tunPacket| is either UDP or TCP packet, changes the source/destination
    // addresses, and updates the checksum.
    base::Result<void> translatePacket(netdutils::Slice tunPacket) const;
    base::Result<void> translateIpv4Packet(netdutils::Slice ipv4Packet) const;
    base::Result<void> translateIpv6Packet(netdutils::Slice ipv6Packet) const;
    void translateUdpPacket(netdutils::Slice udpPacket, uint32_t oldPseudoSum,
                            uint32_t newPseudoSum) const;
    void translateTcpPacket(netdutils::Slice tcpPacket, uint32_t oldPseudoSum,
                            uint32_t newPseudoSum) const;

    std::thread mForwarder;
    base::unique_fd mTunFd;
    base::unique_fd mEventFd;
    std::map<v4pair, v4pair> mRulesIpv4;
    std::map<v6pair, v6pair> mRulesIpv6;

    static constexpr int kPollTimeoutMs = 5000;
};

}  // namespace android::net
