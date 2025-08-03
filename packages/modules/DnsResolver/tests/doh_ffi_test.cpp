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
 */

#include "doh.h"

#include <chrono>
#include <condition_variable>
#include <mutex>

#include <resolv.h>

#include <NetdClient.h>
#include <android-base/unique_fd.h>
#include <gmock/gmock-matchers.h>
#include <gtest/gtest.h>
#include <netdutils/NetNativeTestBase.h>

constexpr char GOOGLE_SERVER_IP[] = "8.8.8.8";
constexpr char GOOGLE_SERVER_IPV6[] = "2001:4860:4860::8888";
static const int TIMEOUT_MS = 10000;
constexpr int MAXPACKET = (8 * 1024);
constexpr unsigned int MINIMAL_NET_ID = 100;

using android::base::unique_fd;

// TODO: Move to DoHFFITest class.
std::mutex m;
std::condition_variable cv;
unsigned int dnsNetId;

namespace {

bool haveIpv4() {
    const sockaddr_in server = {
            .sin_family = AF_INET,
            .sin_addr.s_addr = __constant_htonl(0x08080808L)  // 8.8.8.8
    };
    unique_fd sock(socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, IPPROTO_UDP));
    if (sock == -1) {
        PLOG(INFO) << "Failed to create socket";
        return false;
    }
    return connect(sock, reinterpret_cast<const sockaddr*>(&server), sizeof(server)) == 0;
}

bool haveIpv6() {
    const sockaddr_in6 server = {
            .sin6_family = AF_INET6,
            .sin6_addr.s6_addr = {0x20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}  // 2000::
    };
    unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, IPPROTO_UDP));
    if (sock == -1) {
        PLOG(INFO) << "Failed to create socket";
        return false;
    }
    return connect(sock, reinterpret_cast<const sockaddr*>(&server), sizeof(server)) == 0;
}

}  // namespace

class DoHFFITest : public NetNativeTestBase {
  public:
    static void SetUpTestSuite() { doh_init_logger(DOH_LOG_LEVEL_TRACE); }
};

TEST_F(DoHFFITest, SmokeTest) {
    getNetworkForDns(&dnsNetId);
    ASSERT_GE(dnsNetId, MINIMAL_NET_ID) << "No available networks";
    LOG(INFO) << "dnsNetId: " << dnsNetId;

    const bool have_ipv4 = haveIpv4();
    const bool have_ipv6 = haveIpv6();
    ASSERT_TRUE(have_ipv4 | have_ipv6) << "No connectivity on network " << dnsNetId;

    const static char* server_ip = have_ipv6 ? GOOGLE_SERVER_IPV6 : GOOGLE_SERVER_IP;
    auto validation_cb = [](uint32_t netId, bool success, const char* ip_addr, const char* host) {
        EXPECT_EQ(netId, dnsNetId);
        EXPECT_TRUE(success);
        EXPECT_STREQ(ip_addr, server_ip);
        EXPECT_STREQ(host, "");
        cv.notify_one();
    };

    auto tag_socket_cb = [](int32_t sock) { EXPECT_GE(sock, 0); };

    DohDispatcher* doh = doh_dispatcher_new(validation_cb, tag_socket_cb);
    EXPECT_TRUE(doh != nullptr);

    const FeatureFlags flags = {
            .probe_timeout_ms = TIMEOUT_MS,
            .idle_timeout_ms = TIMEOUT_MS,
            .use_session_resumption = true,
            .enable_early_data = true,
    };

    // sk_mark doesn't matter here because this test doesn't have permission to set sk_mark.
    // The DNS packet would be sent via default network.
    EXPECT_EQ(doh_net_new(doh, dnsNetId, "https://dns.google/dns-query", /* domain */ "", server_ip,
                          /* sk_mark */ 0, /* cert_path */ "", &flags,
                          /* NetworkType::NT_WIFI */ 3,
                          /* PrivateDnsMode::STRICT */ 2),
              0);
    {
        std::unique_lock<std::mutex> lk(m);
        EXPECT_EQ(cv.wait_for(lk, std::chrono::milliseconds(TIMEOUT_MS)),
                  std::cv_status::no_timeout);
    }

    std::vector<uint8_t> buf(MAXPACKET, 0);
    ssize_t len = res_mkquery(ns_o_query, "www.example.com", ns_c_in, ns_t_aaaa, nullptr, 0,
                              nullptr, buf.data(), MAXPACKET);
    uint8_t answer[8192];

    len = doh_query(doh, dnsNetId, buf.data(), len, answer, sizeof answer, TIMEOUT_MS);
    EXPECT_GT(len, 0);
    doh_net_delete(doh, dnsNetId);
    doh_dispatcher_delete(doh);
}
