/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <memory>
#include <string>
#include <vector>

#include <android-base/format.h>
#include <android-base/logging.h>
#include <android-base/result.h>

#include <aidl/android/net/IDnsResolver.h>
#include <aidl/android/net/INetd.h>
#include "ResolverStats.h"  // TODO: stop depending on this internal header
#include "dns_responder.h"
#include "dns_tls_certificate.h"
#include "params.h"

using aidl::android::net::NativeNetworkConfig;
using aidl::android::net::NativeNetworkType;
using aidl::android::net::NativeVpnType;

inline constexpr char kDefaultServer[] = "127.0.0.3";
inline constexpr char kDefaultSearchDomain[] = "example.com";

#define SKIP_IF_REMOTE_VERSION_LESS_THAN(service, version)                                         \
    do {                                                                                           \
        if (!DnsResponderClient::isRemoteVersionSupported(service, version)) {                     \
            std::cerr << "    Skip test. Remote version is too old, required version: " << version \
                      << std::endl;                                                                \
            return;                                                                                \
        }                                                                                          \
    } while (0)

// A thin wrapper to store the outputs of DnsResolver::getResolverInfo().
struct ResolverInfo {
    std::vector<std::string> dnsServers;
    std::vector<std::string> domains;
    std::vector<std::string> dotServers;
    res_params params;
    std::vector<android::net::ResolverStats> stats;
    int waitForPendingReqTimeoutCount;
};

class ResolverParams {
  public:
    class Builder {
      public:
        Builder();
        constexpr Builder& setDnsServers(const std::vector<std::string>& servers) {
            mParcel.servers = servers;
            return *this;
        }
        constexpr Builder& setDotServers(const std::vector<std::string>& servers) {
            mParcel.tlsServers = servers;
            return *this;
        }
        constexpr Builder& setDomains(const std::vector<std::string>& domains) {
            mParcel.domains = domains;
            return *this;
        }
        constexpr Builder& setPrivateDnsProvider(const std::string& provider) {
            mParcel.tlsName = provider;
            return *this;
        }
        constexpr Builder& setParams(
                const std::array<int, aidl::android::net::IDnsResolver::RESOLVER_PARAMS_COUNT>&
                        params) {
            using aidl::android::net::IDnsResolver;
            mParcel.sampleValiditySeconds = params[IDnsResolver::RESOLVER_PARAMS_SAMPLE_VALIDITY];
            mParcel.successThreshold = params[IDnsResolver::RESOLVER_PARAMS_SUCCESS_THRESHOLD];
            mParcel.minSamples = params[IDnsResolver::RESOLVER_PARAMS_MIN_SAMPLES];
            mParcel.maxSamples = params[IDnsResolver::RESOLVER_PARAMS_MAX_SAMPLES];
            mParcel.baseTimeoutMsec = params[IDnsResolver::RESOLVER_PARAMS_BASE_TIMEOUT_MSEC];
            mParcel.retryCount = params[IDnsResolver::RESOLVER_PARAMS_RETRY_COUNT];
            return *this;
        }
        constexpr Builder& setMetered(const bool metered) {
            mParcel.meteredNetwork = metered;
            return *this;
        }
        constexpr Builder& setDohParams(
                const aidl::android::net::resolv::aidl::DohParamsParcel& dohParams) {
            mParcel.dohParams = dohParams;
            return *this;
        }
        aidl::android::net::ResolverParamsParcel build() { return mParcel; }

      private:
        aidl::android::net::ResolverParamsParcel mParcel;
    };
};

// TODO: Remove dns_responder_client_ndk.{h,cpp} after replacing the binder usage of
// dns_responder_client.*
class DnsResponderClient {
  public:
    struct Mapping {
        std::string host;
        std::string entry;
        std::string ip4;
        std::string ip6;
    };

    virtual ~DnsResponderClient() = default;

    static void SetupMappings(unsigned num_hosts, const std::vector<std::string>& domains,
                              std::vector<Mapping>* mappings);

    // Sets up DnsResolver with given DNS servers. This is used to set up for private DNS off mode.
    bool SetResolversForNetwork(const std::vector<std::string>& servers = {kDefaultServer},
                                const std::vector<std::string>& domains = {kDefaultSearchDomain});

    // Sets up DnsResolver from a given parcel.
    bool SetResolversFromParcel(const aidl::android::net::ResolverParamsParcel& resolverParams);

    template <class T>
    static bool isRemoteVersionSupported(T remoteService, int requiredVersion) {
        int remoteVersion = 0;
        if (!remoteService->getInterfaceVersion(&remoteVersion).isOk()) {
            LOG(FATAL) << "Can't get remote version";
        }
        if (remoteVersion < requiredVersion) {
            LOG(WARNING) << fmt::format("Remote version: {} < Required version: {}", remoteVersion,
                                        requiredVersion);
            return false;
        }
        return true;
    };

    static NativeNetworkConfig makeNativeNetworkConfig(int netId, NativeNetworkType networkType,
                                                       int permission, bool secure);

    android::base::Result<ResolverInfo> getResolverInfo();

    // Return a default resolver configuration for opportunistic mode.
    static aidl::android::net::ResolverParamsParcel GetDefaultResolverParamsParcel();

    static void SetupDNSServers(unsigned numServers, const std::vector<Mapping>& mappings,
                                std::vector<std::unique_ptr<test::DNSResponder>>* dns,
                                std::vector<std::string>* servers);

    // Returns 0 on success and a negative value on failure.
    int SetupOemNetwork(int oemNetId);
    int TearDownOemNetwork(int oemNetId);

    virtual void SetUp();
    virtual void TearDown();

    aidl::android::net::IDnsResolver* resolvService() const { return mDnsResolvSrv.get(); }
    aidl::android::net::INetd* netdService() const { return mNetdSrv.get(); }

  private:
    std::shared_ptr<aidl::android::net::INetd> mNetdSrv;
    std::shared_ptr<aidl::android::net::IDnsResolver> mDnsResolvSrv;
};
