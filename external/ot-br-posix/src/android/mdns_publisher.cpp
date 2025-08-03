/*
 *    Copyright (c) 2024, The OpenThread Authors.
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

#include "android/mdns_publisher.hpp"

namespace otbr {

Mdns::Publisher *Mdns::Publisher::Create(Mdns::Publisher::StateCallback aCallback)
{
    return new Android::MdnsPublisher(std::move(aCallback));
}

namespace Android {

using Status = ::ndk::ScopedAStatus;

otbrError DnsErrorToOtbrErrorImpl(int32_t aError)
{
    return aError == 0 ? OTBR_ERROR_NONE : OTBR_ERROR_MDNS;
}

otbrError MdnsPublisher::DnsErrorToOtbrError(int32_t aError)
{
    return DnsErrorToOtbrErrorImpl(aError);
}

Status MdnsPublisher::NsdStatusReceiver::onSuccess()
{
    if (!mCallback.IsNull())
    {
        std::move(mCallback)(OTBR_ERROR_NONE);
    }

    return Status::ok();
}

void MdnsPublisher::SetINsdPublisher(std::shared_ptr<INsdPublisher> aINsdPublisher)
{
    otbrLogInfo("Set INsdPublisher %p", aINsdPublisher.get());

    if (aINsdPublisher)
    {
        mNsdPublisher = std::move(aINsdPublisher);
        mStateCallback(Mdns::Publisher::State::kReady);
    }
    else
    {
        Stop();
        mNsdPublisher = std::move(aINsdPublisher);
    }
}

Status MdnsPublisher::NsdStatusReceiver::onError(int aError)
{
    if (!mCallback.IsNull())
    {
        std::move(mCallback)(DnsErrorToOtbrErrorImpl(aError));
    }

    return Status::ok();
}

std::shared_ptr<MdnsPublisher::NsdStatusReceiver> CreateReceiver(Mdns::Publisher::ResultCallback aCallback)
{
    return ndk::SharedRefBase::make<MdnsPublisher::NsdStatusReceiver>(std::move(aCallback));
}

void DieForNotImplemented(const char *aFuncName)
{
    VerifyOrDie(false, (std::string(aFuncName) + " is not implemented").c_str());
}

otbrError MdnsPublisher::PublishServiceImpl(const std::string &aHostName,
                                            const std::string &aName,
                                            const std::string &aType,
                                            const SubTypeList &aSubTypeList,
                                            uint16_t           aPort,
                                            const TxtData     &aTxtData,
                                            ResultCallback   &&aCallback)
{
    int32_t   listenerId = AllocateListenerId();
    TxtList   txtList;
    otbrError error = OTBR_ERROR_NONE;

    std::vector<DnsTxtAttribute> txtAttributes;

    VerifyOrExit(IsStarted(), error = OTBR_ERROR_MDNS);
    if (mNsdPublisher == nullptr)
    {
        otbrLogWarning("No platform mDNS implementation registered!");
        ExitNow(error = OTBR_ERROR_MDNS);
    }

    aCallback = HandleDuplicateServiceRegistration(aHostName, aName, aType, aSubTypeList, aPort, aTxtData,
                                                   std::move(aCallback));
    VerifyOrExit(!aCallback.IsNull(), error = OTBR_ERROR_INVALID_STATE);

    SuccessOrExit(error = DecodeTxtData(txtList, aTxtData.data(), aTxtData.size()));

    for (const auto &txtEntry : txtList)
    {
        DnsTxtAttribute txtAttribute;

        txtAttribute.name  = txtEntry.mKey;
        txtAttribute.value = txtEntry.mValue;
        txtAttributes.push_back(std::move(txtAttribute));
    }
    AddServiceRegistration(MakeUnique<NsdServiceRegistration>(aHostName, aName, aType, aSubTypeList, aPort, aTxtData,
                                                              /* aCallback= */ nullptr, this, listenerId,
                                                              mNsdPublisher));

    otbrLogInfo("Publishing service %s.%s listener ID = %d", aName.c_str(), aType.c_str(), listenerId);

    mNsdPublisher->registerService(aHostName, aName, aType, aSubTypeList, aPort, txtAttributes,
                                   CreateReceiver(std::move(aCallback)), listenerId);

exit:
    return error;
}

void MdnsPublisher::UnpublishService(const std::string &aName, const std::string &aType, ResultCallback &&aCallback)
{
    NsdServiceRegistration *serviceRegistration =
        static_cast<NsdServiceRegistration *>(FindServiceRegistration(aName, aType));

    VerifyOrExit(IsStarted(), std::move(aCallback)(OTBR_ERROR_MDNS));
    if (mNsdPublisher != nullptr)
    {
        otbrLogWarning("No platform mDNS implementation registered!");
        ExitNow(std::move(aCallback)(OTBR_ERROR_MDNS));
    }
    VerifyOrExit(serviceRegistration != nullptr, std::move(aCallback)(OTBR_ERROR_NONE));

    serviceRegistration->mUnregisterReceiver = CreateReceiver(std::move(aCallback));
    RemoveServiceRegistration(aName, aType, OTBR_ERROR_NONE);

exit:
    return;
}

otbrError MdnsPublisher::PublishHostImpl(const std::string &aName,
                                         const AddressList &aAddresses,
                                         ResultCallback   &&aCallback)
{
    OTBR_UNUSED_VARIABLE(aName);
    OTBR_UNUSED_VARIABLE(aAddresses);
    OTBR_UNUSED_VARIABLE(aCallback);

    DieForNotImplemented(__func__);

    return OTBR_ERROR_MDNS;
}

otbrError MdnsPublisher::PublishKeyImpl(const std::string &aName, const KeyData &aKeyData, ResultCallback &&aCallback)
{
    OTBR_UNUSED_VARIABLE(aName);
    OTBR_UNUSED_VARIABLE(aKeyData);
    OTBR_UNUSED_VARIABLE(aCallback);

    DieForNotImplemented(__func__);

    return OTBR_ERROR_MDNS;
}

void MdnsPublisher::UnpublishHost(const std::string &aName, ResultCallback &&aCallback)
{
    OTBR_UNUSED_VARIABLE(aName);
    OTBR_UNUSED_VARIABLE(aCallback);

    DieForNotImplemented(__func__);

    return;
}

void MdnsPublisher::UnpublishKey(const std::string &aName, ResultCallback &&aCallback)
{
    OTBR_UNUSED_VARIABLE(aName);
    OTBR_UNUSED_VARIABLE(aCallback);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::SubscribeService(const std::string &aType, const std::string &aInstanceName)
{
    OTBR_UNUSED_VARIABLE(aType);
    OTBR_UNUSED_VARIABLE(aInstanceName);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::UnsubscribeService(const std::string &aType, const std::string &aInstanceName)
{
    OTBR_UNUSED_VARIABLE(aType);
    OTBR_UNUSED_VARIABLE(aInstanceName);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::SubscribeHost(const std::string &aHostName)
{
    OTBR_UNUSED_VARIABLE(aHostName);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::UnsubscribeHost(const std::string &aHostName)
{
    OTBR_UNUSED_VARIABLE(aHostName);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::OnServiceResolveFailedImpl(const std::string &aType,
                                               const std::string &aInstanceName,
                                               int32_t            aErrorCode)
{
    OTBR_UNUSED_VARIABLE(aType);
    OTBR_UNUSED_VARIABLE(aInstanceName);
    OTBR_UNUSED_VARIABLE(aErrorCode);

    DieForNotImplemented(__func__);
}

void MdnsPublisher::OnHostResolveFailedImpl(const std::string &aHostName, int32_t aErrorCode)
{
    OTBR_UNUSED_VARIABLE(aHostName);
    OTBR_UNUSED_VARIABLE(aErrorCode);

    DieForNotImplemented(__func__);
}

int32_t MdnsPublisher::AllocateListenerId(void)
{
    if (mNextListenerId == std::numeric_limits<int32_t>::max())
    {
        mNextListenerId = 0;
    }
    return mNextListenerId++;
}

MdnsPublisher::NsdServiceRegistration::~NsdServiceRegistration(void)
{
    VerifyOrExit(mPublisher->IsStarted() && mNsdPublisher != nullptr);

    otbrLogInfo("Unpublishing service %s.%s listener ID = %d", mName.c_str(), mType.c_str(), mListenerId);

    if (!mUnregisterReceiver)
    {
        mUnregisterReceiver = CreateReceiver([](int) {});
    }

    mNsdPublisher->unregister(mUnregisterReceiver, mListenerId);

exit:
    return;
}

} // namespace Android
} // namespace otbr
