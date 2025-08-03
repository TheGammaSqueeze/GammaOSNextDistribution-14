/*
 * Copyright (C) 2010 The Android Open Source Project
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

#pragma once

#include <string>

#include <netd_resolv/resolv.h>  // android_net_context
#include <sysutils/FrameworkCommand.h>
#include <sysutils/FrameworkListener.h>

struct addrinfo;
struct hostent;

namespace android {
namespace net {

class NetworkDnsEventReported;

class DnsProxyListener : public FrameworkListener {
  public:
    DnsProxyListener();
    virtual ~DnsProxyListener() {}

    static constexpr const char* SOCKET_NAME = "dnsproxyd";

  private:
    class Handler {
      public:
        Handler(SocketClient* c) : mClient(c) { mClient->incRef(); }
        virtual ~Handler() { mClient->decRef(); }
        void operator=(const Handler&) = delete;

        // Attept to spawn the worker thread, or return an error to the client.
        // The Handler instance will self-delete in either case.
        void spawn();

        virtual void run() = 0;
        virtual std::string threadName() = 0;

        SocketClient* mClient;  // ref-counted
    };

    /* ------ getaddrinfo ------*/
    class GetAddrInfoCmd : public FrameworkCommand {
      public:
        GetAddrInfoCmd();
        virtual ~GetAddrInfoCmd() {}
        int runCommand(SocketClient* c, int argc, char** argv) override;
    };

    class GetAddrInfoHandler : public Handler {
      public:
        // Note: All of host, service, and hints may be NULL
        GetAddrInfoHandler(SocketClient* c, std::string host, std::string service,
                           std::unique_ptr<addrinfo> hints, const android_net_context& netcontext);
        ~GetAddrInfoHandler() override = default;

        void run() override;
        std::string threadName() override;

      private:
        void doDns64Synthesis(int32_t* rv, addrinfo** res, NetworkDnsEventReported* event);

        std::string mHost;
        std::string mService;
        std::unique_ptr<addrinfo> mHints;
        android_net_context mNetContext;
    };

    /* ------ gethostbyname ------*/
    class GetHostByNameCmd : public FrameworkCommand {
      public:
        GetHostByNameCmd();
        virtual ~GetHostByNameCmd() {}
        int runCommand(SocketClient* c, int argc, char** argv) override;
    };

    class GetHostByNameHandler : public Handler {
      public:
        GetHostByNameHandler(SocketClient* c, std::string name, int af,
                             const android_net_context& netcontext);
        ~GetHostByNameHandler() override = default;

        void run() override;
        std::string threadName() override;

      private:
        void doDns64Synthesis(int32_t* rv, hostent* hbuf, char* buf, size_t buflen, hostent** hpp,
                              NetworkDnsEventReported* event);

        std::string mName;
        int mAf;
        android_net_context mNetContext;
    };

    /* ------ gethostbyaddr ------*/
    class GetHostByAddrCmd : public FrameworkCommand {
      public:
        GetHostByAddrCmd();
        virtual ~GetHostByAddrCmd() {}
        int runCommand(SocketClient* c, int argc, char** argv) override;
    };

    class GetHostByAddrHandler : public Handler {
      public:
        GetHostByAddrHandler(SocketClient* c, in6_addr address, int addressLen, int addressFamily,
                             const android_net_context& netcontext);
        ~GetHostByAddrHandler() override = default;

        void run() override;
        std::string threadName() override;

      private:
        void doDns64ReverseLookup(hostent* hbuf, char* buf, size_t buflen, hostent** hpp,
                                  NetworkDnsEventReported* event);

        in6_addr mAddress;
        int mAddressLen;        // length of address to look up
        int mAddressFamily;     // address family
        android_net_context mNetContext;
    };

    /* ------ resnsend ------*/
    class ResNSendCommand : public FrameworkCommand {
      public:
        ResNSendCommand();
        virtual ~ResNSendCommand() {}
        int runCommand(SocketClient* c, int argc, char** argv) override;
    };

    class ResNSendHandler : public Handler {
      public:
        ResNSendHandler(SocketClient* c, std::string msg, uint32_t flags,
                        const android_net_context& netcontext);
        ~ResNSendHandler() override = default;

        void run() override;
        std::string threadName() override;

      private:
        std::string mMsg;
        uint32_t mFlags;
        android_net_context mNetContext;
    };

    /* ------ getdnsnetid ------*/
    class GetDnsNetIdCommand : public FrameworkCommand {
      public:
        GetDnsNetIdCommand();
        virtual ~GetDnsNetIdCommand() {}
        int runCommand(SocketClient* c, int argc, char** argv) override;
    };

    std::unique_ptr<GetAddrInfoCmd> mGetAddrInfoCmd;
    std::unique_ptr<GetHostByAddrCmd> mGetHostByAddrCmd;
    std::unique_ptr<GetHostByNameCmd> mGetHostByNameCmd;
    std::unique_ptr<ResNSendCommand> mResNSendCommand;
    std::unique_ptr<GetDnsNetIdCommand> mGetDnsNetIdCommand;
};

}  // namespace net
}  // namespace android
