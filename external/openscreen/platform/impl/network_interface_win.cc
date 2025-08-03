// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "platform/impl/network_interface.h"

#include <winsock2.h>
#include <ws2tcpip.h>
#include <iphlpapi.h>

#include "util/osp_logging.h"

namespace openscreen {

std::vector<InterfaceInfo> GetAllInterfaces() {
    constexpr size_t INITIAL_BUFFER_SIZE = 15000;
    ULONG outbuflen = INITIAL_BUFFER_SIZE;
    std::vector<unsigned char> charbuf(INITIAL_BUFFER_SIZE);
    DWORD ret = NO_ERROR;
    constexpr int MAX_RETRIES = 5;

    for (int i = 0; i < MAX_RETRIES; ++i) {
        // TODO: This does not include the loopback interface. Decide if we need it.
        ret = GetAdaptersAddresses(AF_UNSPEC /* get both v4/v6 addrs */,
                                   GAA_FLAG_INCLUDE_PREFIX,
                                   NULL,
                                   reinterpret_cast<IP_ADAPTER_ADDRESSES*>(charbuf.data()),
                                   &outbuflen);
        if (ret == ERROR_BUFFER_OVERFLOW) {
            charbuf.resize(outbuflen);
            continue;
        }
        break;
    }

    if (ret != NO_ERROR) {
        OSP_DVLOG << "GetAdapterAddresses failed err=" << ret;
        return std::vector<InterfaceInfo>();
    }

    std::vector<InterfaceInfo> infos;
    auto pcurraddrs = reinterpret_cast<IP_ADAPTER_ADDRESSES*>(charbuf.data());
    while (pcurraddrs != nullptr) {
        // TODO: return the interfaces
        OSP_DVLOG << "\tIfIndex=" << pcurraddrs->IfIndex;
        OSP_DVLOG << "\tAdapter name=" << pcurraddrs->AdapterName;

        // Ignore interfaces that are down
        if (pcurraddrs->OperStatus == IfOperStatusDown) {
            pcurraddrs = pcurraddrs->Next;
            continue;
        }

        infos.emplace_back();
        InterfaceInfo& info = infos.back();

        info.index = pcurraddrs->IfIndex;
        std::memcpy(info.hardware_address.data(), pcurraddrs->PhysicalAddress,
                std::min((unsigned long)sizeof(info.hardware_address),
                    pcurraddrs->PhysicalAddressLength));
        info.name = pcurraddrs->AdapterName;

        // Determine the interface type
        switch (pcurraddrs->IfType) {
            case IF_TYPE_ETHERNET_CSMACD:
                info.type = InterfaceInfo::Type::kEthernet;
                break;
            case IF_TYPE_IEEE80211:
                info.type = InterfaceInfo::Type::kWifi;
                break;
            case IF_TYPE_SOFTWARE_LOOPBACK:
                info.type = InterfaceInfo::Type::kLoopback;
                break;
            default:
                info.type = InterfaceInfo::Type::kOther;
                break;
        }

        auto punicast = pcurraddrs->FirstUnicastAddress;
        if (punicast != nullptr) {
            for (int i = 0; punicast != nullptr; ++i) {
                if (punicast->Address.lpSockaddr->sa_family == AF_INET) {
                    sockaddr_in* sa_in = (sockaddr_in*)punicast->Address.lpSockaddr;
                    char buff[100];
                    DWORD bufflen = 100;
                    OSP_DVLOG << "\tIPV4:" << inet_ntop(AF_INET, &(sa_in->sin_addr), buff, bufflen);
                    OSP_DVLOG << "\t  prefixsize=" << (unsigned int)punicast->OnLinkPrefixLength;
                    IPAddress ip(IPAddress::Version::kV4,
                            reinterpret_cast<uint8_t*>(&(sa_in->sin_addr.s_addr)));
                    info.addresses.emplace_back(ip, punicast->OnLinkPrefixLength);
                } else if (punicast->Address.lpSockaddr->sa_family == AF_INET6) {
                    sockaddr_in6* sa_in6 = (sockaddr_in6*)punicast->Address.lpSockaddr;
                    char buff[100];
                    DWORD bufflen = 100;
                    OSP_DVLOG << "\tIPV6:" << inet_ntop(AF_INET6, &(sa_in6->sin6_addr), buff, bufflen);
                    OSP_DVLOG << "\t  prefixsize=" << (unsigned int)punicast->OnLinkPrefixLength;
                    IPAddress ip(IPAddress::Version::kV6,
                            reinterpret_cast<uint8_t*>(&(sa_in6->sin6_addr.s6_addr)));
                    info.addresses.emplace_back(ip, punicast->OnLinkPrefixLength);
                } else {
                    OSP_DVLOG << "\tUNSPEC";
                }
                punicast = punicast->Next;
            }
        }
        OSP_DVLOG << "\tIfType=" << pcurraddrs->IfType;
        // TODO: Convert the wide strings to the proper narrow encoding (e.g.
        // UTF-8) rather than print the addresses.
        OSP_DVLOG << "\tDescription=" << static_cast<void*>(pcurraddrs->Description);
        OSP_DVLOG << "\tFriendlyName=" << static_cast<void*>(pcurraddrs->FriendlyName);
        pcurraddrs = pcurraddrs->Next;
    }
    return infos;
}

}  // namespace openscreen
