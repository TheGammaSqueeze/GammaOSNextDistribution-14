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
 *
 */

#include "resolv_fuzzer_utils.h"

namespace android::net {

// Initializes servers to simulate the DNS over UDP/TLS/HTTPS.
test::DNSResponder dns{kDefaultServer, kDnsPortString};
test::DohFrontend doh{kDefaultServer, kDohPortString, "127.0.1.3", kDnsPortString};
test::DNSResponder doh_backend{"127.0.1.3", kDnsPortString};
test::DnsTlsFrontend dot{kDefaultServer, kDotPortString, "127.0.2.3", kDnsPortString};
test::DNSResponder dot_backend{"127.0.2.3", kDnsPortString};
ResolverController resolverCtrl;

// TODO: Consider moving to packages/modules/DnsResolver/tests/resolv_test_utils.h.
void StartDns(test::DNSResponder& dns, const std::vector<DnsRecord>& records) {
    for (const auto& r : records) {
        dns.addMapping(r.host_name, r.type, r.addr);
    }

    ASSERT_TRUE(dns.startServer());
    dns.clearQueries();
}

int RandomSocketType(FuzzedDataProvider& fdp) {
    int socktype = fdp.PickValueInArray(
            {SOCK_STREAM, SOCK_DGRAM, SOCK_RAW, SOCK_RDM, SOCK_SEQPACKET, SOCK_DCCP, SOCK_PACKET});
    if (fdp.ConsumeBool()) socktype |= SOCK_CLOEXEC;
    if (fdp.ConsumeBool()) socktype |= SOCK_NONBLOCK;
    return socktype;
}

// Initializes the callback functions to createNetworkCache.
void InitDnsResolverCallbacks() {
    gResNetdCallbacks.check_calling_permission = [](const char*) -> bool { return true; };
    gResNetdCallbacks.get_network_context = [](uint32_t, uint32_t, android_net_context*) {};
    gResNetdCallbacks.log = [](const char*) {};
}

void InitServers() {
    StartDns(dns, records);
    doh.startServer();
    StartDns(doh_backend, records);
    dot.startServer();
    StartDns(dot_backend, records);
}

void CleanServers() {
    dns.clearQueries();
    doh.clearQueries();
    doh_backend.clearQueries();
    dot.clearQueries();
    dot_backend.clearQueries();
}

// Initializes servers only one time.
bool DoInit() {
    // Sets log level to WARNING to lower I/O time cost.
    resolv_set_log_severity(android::base::WARNING);
    doh_init_logger(DOH_LOG_LEVEL_WARN);

    // Needs to init callback and create netework cache.
    InitDnsResolverCallbacks();
    resolverCtrl.createNetworkCache(TEST_NETID);
    InitServers();

    return true;
}

void CleanUp() {
    CleanServers();
    resolverCtrl.flushNetworkCache(TEST_NETID);
}

}  // namespace android::net
