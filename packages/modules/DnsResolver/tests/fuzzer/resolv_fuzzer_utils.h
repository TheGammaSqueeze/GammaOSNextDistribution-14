#ifndef RESOLV_FUZZER_UTILS_H_
#define RESOLV_FUZZER_UTILS_H_

#include <arpa/inet.h>  // for inet_pton
#include <fuzzer/FuzzedDataProvider.h>

#include "DnsResolver.h"
#include "Experiments.h"  // for update property
#include "ResolverController.h"
#include "dns_responder/dns_responder_client_ndk.h"
#include "dns_responder/dns_tls_frontend.h"
#include "doh.h"  // for DOH_LOG_LEVEL_DEBUG
#include "doh_frontend.h"
#include "getaddrinfo.h"
#include "gethnamaddr.h"
#include "res_debug.h"  // for resolv_set_log_severity
#include "resolv_cache.h"
#include "resolv_test_utils.h"

namespace android::net {

// TODO: Consider moving to packages/modules/DnsResolver/tests/resolv_test_utils.h.
constexpr int MAXPACKET = 8 * 1024;

// Tests A/AAAA/CNAME type and CNAME chain.
const std::vector<DnsRecord> records = {
        {kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4},
        {kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6},
        {kCnameA, ns_type::ns_t_cname, kCnameB},
        {kCnameB, ns_type::ns_t_a, kHelloExampleComAddrV4},
        {kCnameC, ns_type::ns_t_cname, kCnameD},
};

const android_net_context mNetContext = {
        .app_netid = TEST_NETID,
        .app_mark = MARK_UNSET,
        .dns_netid = TEST_NETID,
        .dns_mark = MARK_UNSET,
        .uid = TEST_UID,
};

extern test::DnsTlsFrontend dot;
extern ResolverController resolverCtrl;

void StartDns(test::DNSResponder& dns, const std::vector<DnsRecord>& records);
int RandomSocketType(FuzzedDataProvider& fdp);
void InitDnsResolverCallbacks();
void InitServers();
void CleanServers();
bool DoInit();
void CleanUp();

}  // namespace android::net

#endif  // RESOLV_FUZZER_UTILS_H_
