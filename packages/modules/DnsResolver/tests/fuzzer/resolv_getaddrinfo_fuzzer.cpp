#include <netdb.h>
#include <sys/param.h>

#include <string>

#include "resolv_fuzzer_utils.h"

namespace android::net {
namespace {

// Tests resolv_getaddrinfo.
void TestResolvGetaddrinfo(FuzzedDataProvider& fdp) {
    std::string hostname = fdp.ConsumeRandomLengthString(MAXHOSTNAMELEN);
    std::string servname = fdp.ConsumeRandomLengthString(MAXHOSTNAMELEN);
    // All valid address families in socket.h, e.g. AF_INET.
    int af = fdp.ConsumeIntegralInRange<int>(0, AF_MAX);
    int socktype = RandomSocketType(fdp);
    addrinfo hints = {.ai_family = af, .ai_socktype = socktype};
    addrinfo* result;
    NetworkDnsEventReported event;

    resolv_getaddrinfo(hostname.c_str(), fdp.ConsumeBool() ? servname.c_str() : nullptr,
                       fdp.ConsumeBool() ? &hints : nullptr, &mNetContext, &result, &event);
    netdutils::ScopedAddrinfo result_cleanup(result);
}

}  // namespace

// Entry point of fuzzing test.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    [[maybe_unused]] static const bool initialized = DoInit();
    // Sets delayQueries to let DnsTlsFrontend handle 2 queries at once.
    // If the Address Family is AF_UNSPEC, the frontend will receive both ipv4 and ipv6 queries.
    // Without setting delayQueries, the second query's connection between the dns_tls_frontend and
    // the fuzzing test may be closed and cause SSL_ERROR_SYSCALL. Then, the service will crash
    // after calling SSL_shutdown.
    // TODO: Make the test work without seeing delayQueries.
    dot.setDelayQueries(2);
    dot.setDelayQueriesTimeout(1000);
    FuzzedDataProvider fdp(data, size);

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    // Chooses private DNS or not.
    if (fdp.ConsumeBool()) parcel.tlsServers = {};
    resolverCtrl.setResolverConfiguration(parcel);

    TestResolvGetaddrinfo(fdp);

    CleanUp();
    return 0;
}

}  // namespace android::net
