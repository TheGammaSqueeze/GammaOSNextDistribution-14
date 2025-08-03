/*
 *    Copyright (c) 2023, The OpenThread Authors.
 *    All rights reserved.
 *
 *    Redistribution and use in source and binary forms, with or without
 *    modification, are permitted provided that the following conditions are met:
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *    POSSIBILITY OF SUCH DAMAGE.
 */

#define OTBR_LOG_TAG "BINDER"

#include "android/otdaemon_server.hpp"

#include <net/if.h>
#include <string.h>

#include <android-base/file.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <openthread/border_router.h>
#include <openthread/icmp6.h>
#include <openthread/ip6.h>
#include <openthread/link.h>
#include <openthread/openthread-system.h>
#include <openthread/platform/infra_if.h>

#include "agent/vendor.hpp"
#include "android/otdaemon_telemetry.hpp"
#include "common/code_utils.hpp"

#define BYTE_ARR_END(arr) ((arr) + sizeof(arr))

namespace otbr {

namespace vendor {

std::shared_ptr<VendorServer> VendorServer::newInstance(Application &aApplication)
{
    return ndk::SharedRefBase::make<Android::OtDaemonServer>(aApplication);
}

} // namespace vendor

} // namespace otbr

namespace otbr {
namespace Android {

static const char       OTBR_SERVICE_NAME[] = "ot_daemon";
static constexpr size_t kMaxIp6Size         = 1280;

static void PropagateResult(int                                       aError,
                            const std::string                        &aMessage,
                            const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    if (aReceiver != nullptr)
    {
        // If an operation has already been requested or accepted, consider it succeeded
        if (aError == OT_ERROR_NONE || aError == OT_ERROR_ALREADY)
        {
            aReceiver->onSuccess();
        }
        else
        {
            aReceiver->onError(aError, aMessage);
        }
    }
}

static Ipv6AddressInfo ConvertToAddressInfo(const otIp6AddressInfo &aAddressInfo)
{
    Ipv6AddressInfo addrInfo;

    addrInfo.address.assign(aAddressInfo.mAddress->mFields.m8, BYTE_ARR_END(aAddressInfo.mAddress->mFields.m8));
    addrInfo.prefixLength = aAddressInfo.mPrefixLength;
    addrInfo.scope        = aAddressInfo.mScope;
    addrInfo.isPreferred  = aAddressInfo.mPreferred;
    return addrInfo;
}

OtDaemonServer::OtDaemonServer(Application &aApplication)
    : mNcp(aApplication.GetNcp())
    , mBorderAgent(aApplication.GetBorderAgent())
    , mMdnsPublisher(static_cast<MdnsPublisher &>(aApplication.GetPublisher()))
    , mBorderRouterConfiguration()
{
    mClientDeathRecipient =
        ::ndk::ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(&OtDaemonServer::BinderDeathCallback));
    mBorderRouterConfiguration.infraInterfaceName        = "";
    mBorderRouterConfiguration.infraInterfaceIcmp6Socket = ScopedFileDescriptor();
    mBorderRouterConfiguration.isBorderRoutingEnabled    = false;
}

void OtDaemonServer::Init(void)
{
    binder_exception_t exp = AServiceManager_registerLazyService(asBinder().get(), OTBR_SERVICE_NAME);
    SuccessOrDie(exp, "Failed to register OT daemon binder service");

    assert(GetOtInstance() != nullptr);

    mNcp.AddThreadStateChangedCallback([this](otChangedFlags aFlags) { StateCallback(aFlags); });
    otIp6SetAddressCallback(GetOtInstance(), OtDaemonServer::AddressCallback, this);
    otIp6SetReceiveCallback(GetOtInstance(), OtDaemonServer::ReceiveCallback, this);
    otBackboneRouterSetMulticastListenerCallback(GetOtInstance(), OtDaemonServer::HandleBackboneMulticastListenerEvent,
                                                 this);
    otIcmp6SetEchoMode(GetOtInstance(), OT_ICMP6_ECHO_HANDLER_DISABLED);

    mTaskRunner.Post(kTelemetryCheckInterval, [this]() { PushTelemetryIfConditionMatch(); });
}

void OtDaemonServer::BinderDeathCallback(void *aBinderServer)
{
    OtDaemonServer *thisServer = static_cast<OtDaemonServer *>(aBinderServer);

    otbrLogCrit("Client is died, removing callbacks...");
    thisServer->mCallback = nullptr;
    thisServer->mTunFd.set(-1); // the original FD will be closed automatically
}

void OtDaemonServer::StateCallback(otChangedFlags aFlags)
{
    assert(GetOtInstance() != nullptr);

    if (RefreshOtDaemonState(aFlags))
    {
        if (mCallback == nullptr)
        {
            otbrLogWarning("Ignoring OT state changes: callback is not set");
        }
        else
        {
            mCallback->onStateChanged(mState, -1);
        }
    }
    if (aFlags & OT_CHANGED_THREAD_BACKBONE_ROUTER_STATE)
    {
        if (mCallback == nullptr)
        {
            otbrLogWarning("Ignoring OT backbone router state changes: callback is not set");
        }
        else
        {
            mCallback->onBackboneRouterStateChanged(GetBackboneRouterState());
        }
    }
}

void OtDaemonServer::AddressCallback(const otIp6AddressInfo *aAddressInfo, bool aIsAdded, void *aBinderServer)
{
    OtDaemonServer *thisServer = static_cast<OtDaemonServer *>(aBinderServer);

    if (thisServer->mCallback != nullptr)
    {
        thisServer->mCallback->onAddressChanged(ConvertToAddressInfo(*aAddressInfo), aIsAdded);
    }
    else
    {
        otbrLogWarning("OT daemon callback is not set");
    }
}

void OtDaemonServer::ReceiveCallback(otMessage *aMessage, void *aBinderServer)
{
    static_cast<OtDaemonServer *>(aBinderServer)->ReceiveCallback(aMessage);
}

// FIXME(wgtdkp): We should reuse the same code in openthread/src/posix/platform/netif.cp
// after the refactor there is done: https://github.com/openthread/openthread/pull/9293
void OtDaemonServer::ReceiveCallback(otMessage *aMessage)
{
    char     packet[kMaxIp6Size];
    uint16_t length = otMessageGetLength(aMessage);
    int      fd     = mTunFd.get();

    VerifyOrExit(fd != -1, otbrLogWarning("Ignoring egress packet: invalid tunnel FD"));

    if (otMessageRead(aMessage, 0, packet, sizeof(packet)) != length)
    {
        otbrLogWarning("Failed to read packet from otMessage");
        ExitNow();
    }

    if (write(fd, packet, length) != length)
    {
        otbrLogWarning("Failed to send packet over tunnel interface: %s", strerror(errno));
    }

exit:
    otMessageFree(aMessage);
}

// FIXME(wgtdkp): this doesn't support NAT64, we should use a shared library with ot-posix
// to handle packet translations between the tunnel interface and Thread.
void OtDaemonServer::TransmitCallback(void)
{
    char              packet[kMaxIp6Size];
    ssize_t           length;
    otMessage        *message = nullptr;
    otError           error   = OT_ERROR_NONE;
    otMessageSettings settings;
    int               fd = mTunFd.get();

    assert(GetOtInstance() != nullptr);

    VerifyOrExit(fd != -1);

    length = read(fd, packet, sizeof(packet));

    if (length == -1)
    {
        otbrLogWarning("Failed to read packet from tunnel interface: %s", strerror(errno));
        ExitNow();
    }
    else if (length == 0)
    {
        otbrLogWarning("Unexpected EOF on the tunnel FD");
        ExitNow();
    }

    VerifyOrExit(GetOtInstance() != nullptr, otbrLogWarning("Ignoring tunnel packet: OT is not initialized"));

    settings.mLinkSecurityEnabled = (otThreadGetDeviceRole(GetOtInstance()) != OT_DEVICE_ROLE_DISABLED);
    settings.mPriority            = OT_MESSAGE_PRIORITY_LOW;

    message = otIp6NewMessage(GetOtInstance(), &settings);
    VerifyOrExit(message != nullptr, error = OT_ERROR_NO_BUFS);

    SuccessOrExit(error = otMessageAppend(message, packet, length));

    error   = otIp6Send(GetOtInstance(), message);
    message = nullptr;

exit:
    if (message != nullptr)
    {
        otMessageFree(message);
    }

    if (error != OT_ERROR_NONE)
    {
        if (error == OT_ERROR_DROP)
        {
            otbrLogInfo("Dropped tunnel packet (length=%d)", length);
        }
        else
        {
            otbrLogWarning("Failed to transmit tunnel packet: %s", otThreadErrorToString(error));
        }
    }
}

BackboneRouterState OtDaemonServer::GetBackboneRouterState()
{
    BackboneRouterState                       state;
    otBackboneRouterState                     bbrState;
    otBackboneRouterMulticastListenerInfo     info;
    otBackboneRouterMulticastListenerIterator iter = OT_BACKBONE_ROUTER_MULTICAST_LISTENER_ITERATOR_INIT;
    state.listeningAddresses                       = std::vector<std::string>();

    VerifyOrExit(GetOtInstance() != nullptr, otbrLogWarning("Can't get bbr state: OT is not initialized"));

    bbrState = otBackboneRouterGetState(GetOtInstance());
    switch (bbrState)
    {
    case OT_BACKBONE_ROUTER_STATE_DISABLED:
    case OT_BACKBONE_ROUTER_STATE_SECONDARY:
        state.multicastForwardingEnabled = false;
        break;
    case OT_BACKBONE_ROUTER_STATE_PRIMARY:
        state.multicastForwardingEnabled = true;
        break;
    }
    otbrLogInfo("Updating backbone router state (bbr state = %d)", bbrState);

    while (otBackboneRouterMulticastListenerGetNext(GetOtInstance(), &iter, &info) == OT_ERROR_NONE)
    {
        char string[OT_IP6_ADDRESS_STRING_SIZE];

        otIp6AddressToString(&info.mAddress, string, sizeof(string));
        state.listeningAddresses.push_back(string);
    }

exit:
    return state;
}

void OtDaemonServer::HandleBackboneMulticastListenerEvent(void                                  *aBinderServer,
                                                          otBackboneRouterMulticastListenerEvent aEvent,
                                                          const otIp6Address                    *aAddress)
{
    OtDaemonServer *thisServer = static_cast<OtDaemonServer *>(aBinderServer);
    char            addressString[OT_IP6_ADDRESS_STRING_SIZE];

    otIp6AddressToString(aAddress, addressString, sizeof(addressString));

    otbrLogDebug("Multicast forwarding address changed, %s is %s", addressString,
                 (aEvent == OT_BACKBONE_ROUTER_MULTICAST_LISTENER_ADDED) ? "added" : "removed");

    if (thisServer->mCallback == nullptr)
    {
        otbrLogWarning("Ignoring OT multicast listener event: callback is not set");
        ExitNow();
    }
    thisServer->mCallback->onBackboneRouterStateChanged(thisServer->GetBackboneRouterState());

exit:
    return;
}

otInstance *OtDaemonServer::GetOtInstance()
{
    return mNcp.GetInstance();
}

void OtDaemonServer::Update(MainloopContext &aMainloop)
{
    int fd = mTunFd.get();

    if (fd != -1)
    {
        FD_SET(fd, &aMainloop.mReadFdSet);
        aMainloop.mMaxFd = std::max(aMainloop.mMaxFd, fd);
    }
}

void OtDaemonServer::Process(const MainloopContext &aMainloop)
{
    int fd = mTunFd.get();

    if (fd != -1 && FD_ISSET(fd, &aMainloop.mReadFdSet))
    {
        TransmitCallback();
    }
}

Status OtDaemonServer::initialize(const ScopedFileDescriptor           &aTunFd,
                                  const bool                            enabled,
                                  const std::shared_ptr<INsdPublisher> &aINsdPublisher)
{
    otbrLogInfo("OT daemon is initialized by system server (tunFd=%d, enabled=%s)", aTunFd.get(),
                enabled ? "true" : "false");
    mTunFd         = aTunFd.dup();
    mINsdPublisher = aINsdPublisher;

    if (enabled)
    {
        enableThread(nullptr /* aReceiver */);
    }
    else
    {
        updateThreadEnabledState(enabled, nullptr /* Receiver */);
    }

    return Status::ok();
}

void OtDaemonServer::updateThreadEnabledState(const int enabled, const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    mThreadEnabled = enabled;
    if (aReceiver != nullptr)
    {
        aReceiver->onSuccess();
    }

    switch (enabled)
    {
    case OT_STATE_ENABLED:
        mMdnsPublisher.SetINsdPublisher(mINsdPublisher);
        break;
    case OT_STATE_DISABLED:
        mMdnsPublisher.SetINsdPublisher(nullptr);
        break;
    default:
        break;
    }

    if (mCallback != nullptr)
    {
        mCallback->onThreadEnabledChanged(mThreadEnabled);
    }
}

void OtDaemonServer::enableThread(const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    otOperationalDatasetTlvs datasetTlvs;

    if (otDatasetGetActiveTlvs(GetOtInstance(), &datasetTlvs) != OT_ERROR_NOT_FOUND && datasetTlvs.mLength > 0 &&
        !isAttached())
    {
        (void)otIp6SetEnabled(GetOtInstance(), true);
        (void)otThreadSetEnabled(GetOtInstance(), true);
    }
    updateThreadEnabledState(IOtDaemon::OT_STATE_ENABLED, aReceiver);
}

Status OtDaemonServer::setThreadEnabled(const bool enabled, const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    int         error = OT_ERROR_NONE;
    std::string message;

    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE, message = "OT is not initialized");

    VerifyOrExit(mThreadEnabled != IOtDaemon::OT_STATE_DISABLING, error = OT_ERROR_BUSY,
                 message = "Thread is disabling");

    if ((mThreadEnabled == IOtDaemon::OT_STATE_ENABLED) == enabled)
    {
        aReceiver->onSuccess();
        ExitNow();
    }

    if (enabled)
    {
        enableThread(aReceiver);
    }
    else
    {
        mThreadEnabled = IOtDaemon::OT_STATE_DISABLING;
        if (mCallback != nullptr)
        {
            mCallback->onThreadEnabledChanged(mThreadEnabled);
        }

        LeaveGracefully([aReceiver, this]() {
            // Ignore errors as those operations should always succeed
            (void)otThreadSetEnabled(GetOtInstance(), false);
            (void)otIp6SetEnabled(GetOtInstance(), false);
            updateThreadEnabledState(IOtDaemon::OT_STATE_DISABLED, aReceiver);
        });
    }

exit:
    if (error != OT_ERROR_NONE)
    {
        PropagateResult(error, message, aReceiver);
    }
    return Status::ok();
}

Status OtDaemonServer::registerStateCallback(const std::shared_ptr<IOtDaemonCallback> &aCallback, int64_t listenerId)
{
    VerifyOrExit(GetOtInstance() != nullptr, otbrLogWarning("OT is not initialized"));

    mCallback = aCallback;
    if (mCallback != nullptr)
    {
        AIBinder_linkToDeath(mCallback->asBinder().get(), mClientDeathRecipient.get(), this);
    }

    // To ensure that a client app can get the latest correct state immediately when registering a
    // state callback, here needs to invoke the callback
    RefreshOtDaemonState(/* aFlags */ 0xffffffff);
    mCallback->onStateChanged(mState, listenerId);
    mCallback->onThreadEnabledChanged(mThreadEnabled);
    mCallback->onBackboneRouterStateChanged(GetBackboneRouterState());

exit:
    return Status::ok();
}

bool OtDaemonServer::RefreshOtDaemonState(otChangedFlags aFlags)
{
    bool haveUpdates = false;

    if (aFlags & OT_CHANGED_THREAD_NETIF_STATE)
    {
        mState.isInterfaceUp = otIp6IsEnabled(GetOtInstance());
        haveUpdates          = true;
    }

    if (aFlags & OT_CHANGED_THREAD_ROLE)
    {
        mState.deviceRole = otThreadGetDeviceRole(GetOtInstance());
        haveUpdates       = true;
    }

    if (aFlags & OT_CHANGED_THREAD_PARTITION_ID)
    {
        mState.partitionId = otThreadGetPartitionId(GetOtInstance());
        haveUpdates        = true;
    }

    if (aFlags & OT_CHANGED_ACTIVE_DATASET)
    {
        otOperationalDatasetTlvs datasetTlvs;
        if (otDatasetGetActiveTlvs(GetOtInstance(), &datasetTlvs) == OT_ERROR_NONE)
        {
            mState.activeDatasetTlvs.assign(datasetTlvs.mTlvs, datasetTlvs.mTlvs + datasetTlvs.mLength);
        }
        else
        {
            mState.activeDatasetTlvs.clear();
        }
        haveUpdates = true;
    }

    if (aFlags & OT_CHANGED_PENDING_DATASET)
    {
        otOperationalDatasetTlvs datasetTlvs;
        if (otDatasetGetPendingTlvs(GetOtInstance(), &datasetTlvs) == OT_ERROR_NONE)
        {
            mState.pendingDatasetTlvs.assign(datasetTlvs.mTlvs, datasetTlvs.mTlvs + datasetTlvs.mLength);
        }
        else
        {
            mState.pendingDatasetTlvs.clear();
        }
        haveUpdates = true;
    }

    if (isAttached() && !mState.activeDatasetTlvs.empty() && mJoinReceiver != nullptr)
    {
        mJoinReceiver->onSuccess();
        mJoinReceiver = nullptr;
    }

    return haveUpdates;
}

Status OtDaemonServer::join(const std::vector<uint8_t>               &aActiveOpDatasetTlvs,
                            const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    int                      error = OT_ERROR_NONE;
    std::string              message;
    otOperationalDatasetTlvs datasetTlvs;

    VerifyOrExit(mThreadEnabled != IOtDaemon::OT_STATE_DISABLING, error = OT_ERROR_BUSY,
                 message = "Thread is disabling");

    VerifyOrExit(mThreadEnabled == IOtDaemon::OT_STATE_ENABLED,
                 error   = static_cast<int>(IOtDaemon::ErrorCode::OT_ERROR_THREAD_DISABLED),
                 message = "Thread is disabled");

    otbrLogInfo("Start joining...");

    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE, message = "OT is not initialized");

    if (otThreadGetDeviceRole(GetOtInstance()) != OT_DEVICE_ROLE_DISABLED)
    {
        LeaveGracefully([aActiveOpDatasetTlvs, aReceiver, this]() { join(aActiveOpDatasetTlvs, aReceiver); });
        ExitNow();
    }

    std::copy(aActiveOpDatasetTlvs.begin(), aActiveOpDatasetTlvs.end(), datasetTlvs.mTlvs);
    datasetTlvs.mLength = aActiveOpDatasetTlvs.size();
    SuccessOrExit(error   = otDatasetSetActiveTlvs(GetOtInstance(), &datasetTlvs),
                  message = "Failed to set Active Operational Dataset");

    // TODO(b/273160198): check how we can implement join as a child

    // Shouldn't we have an equivalent `otThreadAttach` method vs `otThreadDetachGracefully`?
    SuccessOrExit(error = otIp6SetEnabled(GetOtInstance(), true), message = "Failed to bring up Thread interface");
    SuccessOrExit(error = otThreadSetEnabled(GetOtInstance(), true), message = "Failed to bring up Thread stack");

    // Abort an ongoing join()
    if (mJoinReceiver != nullptr)
    {
        mJoinReceiver->onError(OT_ERROR_ABORT, "Join() is aborted");
    }
    mJoinReceiver = aReceiver;

exit:
    if (error != OT_ERROR_NONE)
    {
        PropagateResult(error, message, aReceiver);
    }
    return Status::ok();
}

Status OtDaemonServer::leave(const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    std::string message;
    int         error = OT_ERROR_NONE;

    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE, message = "OT is not initialized");

    VerifyOrExit(mThreadEnabled != IOtDaemon::OT_STATE_DISABLING, error = OT_ERROR_BUSY,
                 message = "Thread is disabling");

    if (mThreadEnabled == IOtDaemon::OT_STATE_DISABLED)
    {
        (void)otInstanceErasePersistentInfo(GetOtInstance());
        aReceiver->onSuccess();
        ExitNow();
    }

    LeaveGracefully([aReceiver, this]() {
        (void)otInstanceErasePersistentInfo(GetOtInstance());
        aReceiver->onSuccess();
    });

exit:
    if (error != OT_ERROR_NONE)
    {
        PropagateResult(error, message, aReceiver);
    }
    return Status::ok();
}

void OtDaemonServer::LeaveGracefully(const LeaveCallback &aReceiver)
{
    mLeaveCallbacks.push_back(aReceiver);

    // Ignores the OT_ERROR_BUSY error if a detach has already been requested
    (void)otThreadDetachGracefully(GetOtInstance(), DetachGracefullyCallback, this);
}

void OtDaemonServer::DetachGracefullyCallback(void *aBinderServer)
{
    OtDaemonServer *thisServer = static_cast<OtDaemonServer *>(aBinderServer);
    thisServer->DetachGracefullyCallback();
}

void OtDaemonServer::DetachGracefullyCallback(void)
{
    otbrLogInfo("detach success...");

    if (mJoinReceiver != nullptr)
    {
        mJoinReceiver->onError(OT_ERROR_ABORT, "Aborted by leave/disable operation");
        mJoinReceiver = nullptr;
    }

    if (mMigrationReceiver != nullptr)
    {
        mMigrationReceiver->onError(OT_ERROR_ABORT, "Aborted by leave/disable operation");
        mMigrationReceiver = nullptr;
    }

    for (auto &callback : mLeaveCallbacks)
    {
        callback();
    }
    mLeaveCallbacks.clear();
}

bool OtDaemonServer::isAttached()
{
    otDeviceRole role = otThreadGetDeviceRole(GetOtInstance());

    return role == OT_DEVICE_ROLE_CHILD || role == OT_DEVICE_ROLE_ROUTER || role == OT_DEVICE_ROLE_LEADER;
}

Status OtDaemonServer::scheduleMigration(const std::vector<uint8_t>               &aPendingOpDatasetTlvs,
                                         const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    int                  error;
    std::string          message;
    otOperationalDataset emptyDataset;

    VerifyOrExit(mThreadEnabled != IOtDaemon::OT_STATE_DISABLING, error = OT_ERROR_BUSY,
                 message = "Thread is disabling");

    VerifyOrExit(mThreadEnabled == IOtDaemon::OT_STATE_ENABLED,
                 error   = static_cast<int>(IOtDaemon::ErrorCode::OT_ERROR_THREAD_DISABLED),
                 message = "Thread is disabled");

    if (GetOtInstance() == nullptr)
    {
        message = "OT is not initialized";
        ExitNow(error = OT_ERROR_INVALID_STATE);
    }

    if (!isAttached())
    {
        message = "Cannot schedule migration when this device is detached";
        ExitNow(error = static_cast<int>(IOtDaemon::ErrorCode::OT_ERROR_FAILED_PRECONDITION));
    }

    // TODO: check supported channel mask

    error = otDatasetSendMgmtPendingSet(GetOtInstance(), &emptyDataset, aPendingOpDatasetTlvs.data(),
                                        aPendingOpDatasetTlvs.size(), SendMgmtPendingSetCallback,
                                        /* aBinderServer= */ this);
    if (error != OT_ERROR_NONE)
    {
        message = "Failed to send MGMT_PENDING_SET.req";
    }

exit:
    if (error != OT_ERROR_NONE)
    {
        PropagateResult(error, message, aReceiver);
    }
    else
    {
        // otDatasetSendMgmtPendingSet() returns OT_ERROR_BUSY if it has already been called before but the
        // callback hasn't been invoked. So we can guarantee that mMigrationReceiver is always nullptr here
        assert(mMigrationReceiver == nullptr);
        mMigrationReceiver = aReceiver;
    }
    return Status::ok();
}

void OtDaemonServer::SendMgmtPendingSetCallback(otError aResult, void *aBinderServer)
{
    OtDaemonServer *thisServer = static_cast<OtDaemonServer *>(aBinderServer);

    if (thisServer->mMigrationReceiver != nullptr)
    {
        PropagateResult(aResult, "Failed to register Pending Dataset to leader", thisServer->mMigrationReceiver);
        thisServer->mMigrationReceiver = nullptr;
    }
}

Status OtDaemonServer::setCountryCode(const std::string                        &aCountryCode,
                                      const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    static constexpr int kCountryCodeLength = 2;
    otError              error              = OT_ERROR_NONE;
    std::string          message;
    uint16_t             countryCode;

    VerifyOrExit((aCountryCode.length() == kCountryCodeLength) && isalpha(aCountryCode[0]) && isalpha(aCountryCode[1]),
                 error = OT_ERROR_INVALID_ARGS, message = "The country code is invalid");

    otbrLogInfo("Set country code: %c%c", aCountryCode[0], aCountryCode[1]);
    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE, message = "OT is not initialized");
    countryCode = (static_cast<uint16_t>(aCountryCode[0]) << 8) | aCountryCode[1];
    SuccessOrExit(error = otLinkSetRegion(GetOtInstance(), countryCode), message = "Failed to set the country code");

exit:
    PropagateResult(error, message, aReceiver);
    return Status::ok();
}

Status OtDaemonServer::getChannelMasks(const std::shared_ptr<IChannelMasksReceiver> &aReceiver)
{
    otError  error = OT_ERROR_NONE;
    uint32_t supportedChannelMask;
    uint32_t preferredChannelMask;

    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE);

    supportedChannelMask = otLinkGetSupportedChannelMask(GetOtInstance());
    preferredChannelMask = otPlatRadioGetPreferredChannelMask(GetOtInstance());

exit:
    if (aReceiver != nullptr)
    {
        if (error == OT_ERROR_NONE)
        {
            aReceiver->onSuccess(supportedChannelMask, preferredChannelMask);
        }
        else
        {
            aReceiver->onError(error, "OT is not initialized");
        }
    }

    return Status::ok();
}

Status OtDaemonServer::configureBorderRouter(const BorderRouterConfigurationParcel    &aBorderRouterConfiguration,
                                             const std::shared_ptr<IOtStatusReceiver> &aReceiver)
{
    int         icmp6SocketFd = aBorderRouterConfiguration.infraInterfaceIcmp6Socket.dup().release();
    std::string message;
    otError     error = OT_ERROR_NONE;

    otbrLogInfo("Configuring Border Router: %s", aBorderRouterConfiguration.toString().c_str());

    VerifyOrExit(GetOtInstance() != nullptr, error = OT_ERROR_INVALID_STATE, message = "OT is not initialized");

    if (mBorderRouterConfiguration != aBorderRouterConfiguration)
    {
        if (aBorderRouterConfiguration.isBorderRoutingEnabled)
        {
            int infraIfIndex = if_nametoindex(aBorderRouterConfiguration.infraInterfaceName.c_str());
            SuccessOrExit(error   = otBorderRoutingSetEnabled(GetOtInstance(), false /* aEnabled */),
                          message = "failed to disable border routing");
            otSysSetInfraNetif(aBorderRouterConfiguration.infraInterfaceName.c_str(), icmp6SocketFd);
            icmp6SocketFd = -1;
            SuccessOrExit(error   = otBorderRoutingInit(GetOtInstance(), infraIfIndex, otSysInfraIfIsRunning()),
                          message = "failed to initialize border routing");
            SuccessOrExit(error   = otBorderRoutingSetEnabled(GetOtInstance(), true /* aEnabled */),
                          message = "failed to enable border routing");
            // TODO: b/320836258 - Make BBR independently configurable
            otBackboneRouterSetEnabled(GetOtInstance(), true /* aEnabled */);
        }
        else
        {
            SuccessOrExit(error   = otBorderRoutingSetEnabled(GetOtInstance(), false /* aEnabled */),
                          message = "failed to disable border routing");
            otBackboneRouterSetEnabled(GetOtInstance(), false /* aEnabled */);
        }
    }

    mBorderRouterConfiguration.isBorderRoutingEnabled = aBorderRouterConfiguration.isBorderRoutingEnabled;
    mBorderRouterConfiguration.infraInterfaceName     = aBorderRouterConfiguration.infraInterfaceName;

exit:
    if (error != OT_ERROR_NONE)
    {
        close(icmp6SocketFd);
    }
    PropagateResult(error, message, aReceiver);

    return Status::ok();
}

binder_status_t OtDaemonServer::dump(int aFd, const char **aArgs, uint32_t aNumArgs)
{
    OT_UNUSED_VARIABLE(aArgs);
    OT_UNUSED_VARIABLE(aNumArgs);

    // TODO: Use ::android::base::WriteStringToFd to dump infomration.
    fsync(aFd);

    return STATUS_OK;
}

void OtDaemonServer::PushTelemetryIfConditionMatch()
{
    VerifyOrExit(GetOtInstance() != nullptr);

    // TODO: Push telemetry per kTelemetryUploadIntervalThreshold instead of on startup.
    // TODO: Save unpushed telemetries in local cache to avoid data loss.
    RetrieveAndPushAtoms(GetOtInstance());
    mTaskRunner.Post(kTelemetryUploadIntervalThreshold, [this]() { PushTelemetryIfConditionMatch(); });

exit:
    return;
}

} // namespace Android
} // namespace otbr
