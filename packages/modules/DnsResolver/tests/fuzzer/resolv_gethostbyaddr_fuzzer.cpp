#include "resolv_fuzzer_utils.h"

namespace android::net {
namespace {

// Tests resolv_gethostbyaddr.
void TestResolvGethostbyaddr(FuzzedDataProvider& fdp) {
    in6_addr v6addr;
    fdp.ConsumeBool() ? fdp.ConsumeData(&v6addr, sizeof(v6addr))    // Fuzzing data.
                      : inet_pton(AF_INET6, "::1.2.3.4", &v6addr);  // Correct data.
    // Fuzzs some values defined in nameser.h, e.g. NS_INADDRSZ.
    socklen_t mAddressLen = fdp.ConsumeIntegralInRange<int>(0, NS_IN6ADDRSZ + 1);
    // All valid address families in socket.h, e.g. AF_INET.
    int af = fdp.ConsumeIntegralInRange<int>(0, AF_MAX);
    hostent hbuf;
    char tmpbuf[MAXPACKET];
    hostent* hp;
    NetworkDnsEventReported event;

    resolv_gethostbyaddr(&v6addr, mAddressLen, af, &hbuf, tmpbuf, sizeof(tmpbuf), &mNetContext, &hp,
                         &event);
}

}  // namespace

// Entry point of fuzzing test.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    [[maybe_unused]] static bool initialized = DoInit();
    FuzzedDataProvider fdp(data, size);

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    // Chooses private DNS or not.
    if (fdp.ConsumeBool()) parcel.tlsServers = {};
    resolverCtrl.setResolverConfiguration(parcel);

    TestResolvGethostbyaddr(fdp);

    CleanUp();
    return 0;
}

}  // namespace android::net
