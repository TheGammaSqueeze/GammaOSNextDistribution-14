/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "FwmarkServer.h"

#include <net/if.h>
#include <netinet/in.h>
#include <selinux/selinux.h>
#include <sys/socket.h>
#include <unistd.h>
#include <utils/String16.h>

#include <android-base/cmsg.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <binder/IServiceManager.h>
#include <netd_resolv/resolv.h>  // NETID_UNSET

#include "Fwmark.h"
#include "FwmarkCommand.h"
#include "NetdConstants.h"
#include "NetworkController.h"

#include "NetdUpdatablePublic.h"

using android::base::ReceiveFileDescriptorVector;
using android::base::unique_fd;
using android::net::metrics::INetdEventListener;

namespace android {
namespace net {

FwmarkServer::FwmarkServer(NetworkController* networkController, EventReporter* eventReporter)
    : SocketListener(SOCKET_NAME, true),
      mNetworkController(networkController),
      mEventReporter(eventReporter) {}

bool FwmarkServer::onDataAvailable(SocketClient* client) {
    int socketFd = -1;
    int error = processClient(client, &socketFd);
    if (socketFd >= 0) {
        close(socketFd);
    }

    // Always send a response even if there were connection errors or read errors, so that we don't
    // inadvertently cause the client to hang (which always waits for a response).
    client->sendData(&error, sizeof(error));

    // Always close the client connection (by returning false). This prevents a DoS attack where
    // the client issues multiple commands on the same connection, never reading the responses,
    // causing its receive buffer to fill up, and thus causing our client->sendData() to block.
    return false;
}

static bool hasDestinationAddress(FwmarkCommand::CmdId cmdId) {
    switch (cmdId) {
      case FwmarkCommand::ON_CONNECT:
      case FwmarkCommand::ON_CONNECT_COMPLETE:
      case FwmarkCommand::ON_SENDMSG:
      case FwmarkCommand::ON_SENDMMSG:
      case FwmarkCommand::ON_SENDTO:
        return true;
      default:
        return false;
    }
}

int FwmarkServer::processClient(SocketClient* client, int* socketFd) {
    struct {
        FwmarkCommand command;
        FwmarkConnectInfo connectInfo;
    } buf;

    // make sure there is no spurious padding
    static_assert(sizeof(buf) == sizeof(buf.command) + sizeof(buf.connectInfo));

    std::vector<unique_fd> received_fds;
    ssize_t messageLength =
            ReceiveFileDescriptorVector(client->getSocket(), &buf, sizeof(buf), 1, &received_fds);

    if (messageLength < 0) {
        return -errno;
    } else if (messageLength == 0) {
        return -ESHUTDOWN;
    }

    const FwmarkCommand &command = buf.command;
    const FwmarkConnectInfo &connectInfo = buf.connectInfo;

    size_t expectedLen = sizeof(command);
    if (hasDestinationAddress(command.cmdId)) {
        expectedLen += sizeof(connectInfo);
    }

    if (messageLength != static_cast<ssize_t>(expectedLen)) {
        return -EBADMSG;
    }

    Permission permission = mNetworkController->getPermissionForUser(client->getUid());

    if (command.cmdId == FwmarkCommand::QUERY_USER_ACCESS) {
        if ((permission & PERMISSION_SYSTEM) != PERMISSION_SYSTEM) {
            return -EPERM;
        }
        return mNetworkController->checkUserNetworkAccess(command.uid, command.netId);
    }

    if (received_fds.size() != 1) {
        LOG(ERROR) << "FwmarkServer received " << received_fds.size() << " fds from client?";
        return -EBADF;
    } else if (received_fds[0] < 0) {
        LOG(ERROR) << "FwmarkServer received fd -1 from ReceiveFileDescriptorVector?";
        return -EBADF;
    }

    *socketFd = received_fds[0].release();

    int family;
    socklen_t familyLen = sizeof(family);
    if (getsockopt(*socketFd, SOL_SOCKET, SO_DOMAIN, &family, &familyLen) == -1) {
        return -errno;
    }
    if (!FwmarkCommand::isSupportedFamily(family)) {
        return -EAFNOSUPPORT;
    }

    Fwmark fwmark;
    socklen_t fwmarkLen = sizeof(fwmark.intValue);
    if (getsockopt(*socketFd, SOL_SOCKET, SO_MARK, &fwmark.intValue, &fwmarkLen) == -1) {
        return -errno;
    }

    switch (command.cmdId) {
        case FwmarkCommand::ON_ACCEPT: {
            // Called after a socket accept(). The kernel would've marked the NetId and necessary
            // permissions bits, so we just add the rest of the user's permissions here.
            permission = static_cast<Permission>(permission | fwmark.permission);
            break;
        }

        case FwmarkCommand::ON_CONNECT: {
            // Called before a socket connect() happens. Set an appropriate NetId into the fwmark so
            // that the socket routes consistently over that network. Do this even if the socket
            // already has a NetId, so that calling connect() multiple times still works.
            //
            // But if the explicit bit was set, the existing NetId was explicitly preferred (and not
            // a case of connect() being called multiple times). Don't reset the NetId in that case.
            //
            // An "appropriate" NetId is the NetId of a bypassable VPN that applies to the user, or
            // failing that, the default network. We'll never set the NetId of a secure VPN here.
            // See the comments in the implementation of getNetworkForConnect() for more details.
            //
            // If the protect bit is set, this could be either a system proxy (e.g.: the dns proxy
            // or the download manager) acting on behalf of another user, or a VPN provider. If it's
            // a proxy, we shouldn't reset the NetId. If it's a VPN provider, we should set the
            // default network's NetId.
            //
            // There's no easy way to tell the difference between a proxy and a VPN app. We can't
            // use PERMISSION_SYSTEM to identify the proxy because a VPN app may also have those
            // permissions. So we use the following heuristic:
            //
            // If it's a proxy, but the existing NetId is not a VPN, that means the user (that the
            // proxy is acting on behalf of) is not subject to a VPN, so the proxy must have picked
            // the default network's NetId. So, it's okay to replace that with the current default
            // network's NetId (which in all likelihood is the same).
            //
            // Conversely, if it's a VPN provider, the existing NetId cannot be a VPN. The only time
            // we set a VPN's NetId into a socket without setting the explicit bit is here, in
            // ON_CONNECT, but we won't do that if the socket has the protect bit set.
            // If the VPN provider connect()ed (and got the VPN NetId set) and then called
            // protect(), we would've unset the NetId in PROTECT_FROM_VPN below.
            //
            // So, overall (when the explicit bit is not set but the protect bit is set), if the
            // existing NetId is a VPN, don't reset it. Else, set the default network's NetId.
            if (!fwmark.explicitlySelected) {
                if (family == AF_INET6 && connectInfo.addr.sin6.sin6_scope_id &&
                    IN6_IS_ADDR_LINKLOCAL(&connectInfo.addr.sin6.sin6_addr)) {
                    fwmark.netId = mNetworkController->getNetworkForInterface(
                            connectInfo.addr.sin6.sin6_scope_id);
                } else if (!fwmark.protectedFromVpn) {
                    fwmark.netId = mNetworkController->getNetworkForConnect(client->getUid());
                } else if (!mNetworkController->isVirtualNetwork(fwmark.netId)) {
                    fwmark.netId = mNetworkController->getDefaultNetwork();
                }
            }
            break;
        }

        case FwmarkCommand::ON_CONNECT_COMPLETE: {
            // Called after a socket connect() completes.
            // This reports connect event including netId, destination IP address, destination port,
            // uid, connect latency, and connect errno if any.

            // Skip reporting if connect() happened on a UDP socket.
            int socketProto;
            socklen_t intSize = sizeof(socketProto);
            const int ret = getsockopt(*socketFd, SOL_SOCKET, SO_PROTOCOL, &socketProto, &intSize);
            if ((ret != 0) || (socketProto == IPPROTO_UDP)) {
                break;
            }

            android::sp<android::net::metrics::INetdEventListener> netdEventListener =
                    mEventReporter->getNetdEventListener();

            if (netdEventListener != nullptr) {
                char addrstr[INET6_ADDRSTRLEN + IFNAMSIZ];  // ipv6 address + optional %scope
                char portstr[sizeof("65535")];
                static_assert(sizeof(addrstr) >= 62);
                static_assert(sizeof(portstr) >= 6);
                const int ret = getnameinfo(&connectInfo.addr.s, sizeof(connectInfo.addr.s),
                        addrstr, sizeof(addrstr), portstr, sizeof(portstr),
                        NI_NUMERICHOST | NI_NUMERICSERV);

                netdEventListener->onConnectEvent(fwmark.netId, connectInfo.error,
                        connectInfo.latencyMs,
                        (ret == 0) ? String16(addrstr) : String16(""),
                        (ret == 0) ? strtoul(portstr, nullptr, 10) : 0, client->getUid());
            }
            break;
        }

        case FwmarkCommand::ON_SENDMMSG:
        case FwmarkCommand::ON_SENDMSG:
        case FwmarkCommand::ON_SENDTO: {
            return 0;
        }

        case FwmarkCommand::SELECT_NETWORK: {
            fwmark.netId = command.netId;
            if (command.netId == NETID_UNSET) {
                fwmark.explicitlySelected = false;
                fwmark.protectedFromVpn = false;
                permission = PERMISSION_NONE;
            } else {
                if (int ret = mNetworkController->checkUserNetworkAccess(client->getUid(),
                                                                         command.netId)) {
                    return ret;
                }
                fwmark.explicitlySelected = true;
                fwmark.protectedFromVpn = mNetworkController->canProtect(client->getUid());
            }
            break;
        }

        case FwmarkCommand::PROTECT_FROM_VPN: {
            if (!mNetworkController->canProtect(client->getUid())) {
                LOG(ERROR) << "uid " << client->getUid() << " protect from VPN failed.";
                return -EPERM;
            }
            // If a bypassable VPN's provider app calls connect() and then protect(), it will end up
            // with a socket that looks like that of a system proxy but is not (see comments for
            // ON_CONNECT above). So, reset the NetId.
            //
            // In any case, it's appropriate that if the socket has an implicit VPN NetId mark, the
            // PROTECT_FROM_VPN command should unset it.
            if (!fwmark.explicitlySelected && mNetworkController->isVirtualNetwork(fwmark.netId)) {
                fwmark.netId = mNetworkController->getDefaultNetwork();
            }
            fwmark.protectedFromVpn = true;
            permission = static_cast<Permission>(permission | fwmark.permission);
            break;
        }

        case FwmarkCommand::SELECT_FOR_USER: {
            if ((permission & PERMISSION_SYSTEM) != PERMISSION_SYSTEM) {
                return -EPERM;
            }
            fwmark.netId = mNetworkController->getNetworkForUser(command.uid);
            fwmark.protectedFromVpn = true;
            break;
        }

        case FwmarkCommand::TAG_SOCKET: {
            // If the UID is -1, tag as the caller's UID:
            //  - TrafficStats and NetworkManagementSocketTagger use -1 to indicate "use the
            //    caller's UID".
            //  - xt_qtaguid will see -1 on the command line, fail to parse it as a uint32_t,
            //    and fall back to current_fsuid().
            uid_t tagUid = command.uid;
            if (static_cast<int>(tagUid) == -1) tagUid = client->getUid();
            return libnetd_updatable_tagSocket(*socketFd, command.trafficCtrlInfo, tagUid,
                                               client->getUid());
        }

        case FwmarkCommand::UNTAG_SOCKET: {
            // Any process can untag a socket it has an fd for.
            return libnetd_updatable_untagSocket(*socketFd);
        }

        default: {
            // unknown command
            return -EPROTO;
        }
    }

    fwmark.permission = permission;

    if (setsockopt(*socketFd, SOL_SOCKET, SO_MARK, &fwmark.intValue,
                   sizeof(fwmark.intValue)) == -1) {
        return -errno;
    }

    return 0;
}

}  // namespace net
}  // namespace android
