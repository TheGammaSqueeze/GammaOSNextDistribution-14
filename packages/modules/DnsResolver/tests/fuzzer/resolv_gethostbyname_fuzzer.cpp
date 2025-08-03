#include <netdb.h>
#include <sys/param.h>

#include <string>

#include "resolv_fuzzer_utils.h"

namespace android::net {
namespace {

// Tests resolv_gethostbyname.
void TestResolvGethostbyname(FuzzedDataProvider& fdp) {
    std::string hostname = fdp.ConsumeRandomLengthString(MAXHOSTNAMELEN);
    // All valid address families in socket.h, e.g. AF_INET.
    int af = fdp.ConsumeIntegralInRange<int>(0, AF_MAX);
    hostent hbuf;
    char tmpbuf[MAXPACKET];
    hostent* hp;
    NetworkDnsEventReported event;

    resolv_gethostbyname(fdp.ConsumeBool() ? hostname.c_str() : nullptr, af, &hbuf, tmpbuf,
                         sizeof(tmpbuf), &mNetContext, &hp, &event);
}

}  // namespace

// Entry point of fuzzing test.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    [[maybe_unused]] static const bool initialized = DoInit();
    FuzzedDataProvider fdp(data, size);

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    // Chooses private DNS or not.
    if (fdp.ConsumeBool()) parcel.tlsServers = {};
    resolverCtrl.setResolverConfiguration(parcel);

    TestResolvGethostbyname(fdp);

    CleanUp();
    return 0;
}

}  // namespace android::net
