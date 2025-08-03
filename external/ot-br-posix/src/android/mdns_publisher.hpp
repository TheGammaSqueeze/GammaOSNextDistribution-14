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

#ifndef OTBR_ANDROID_MDNS_PUBLISHER_HPP_
#define OTBR_ANDROID_MDNS_PUBLISHER_HPP_

#include "mdns/mdns.hpp"

#include <aidl/com/android/server/thread/openthread/BnNsdStatusReceiver.h>
#include <aidl/com/android/server/thread/openthread/DnsTxtAttribute.h>
#include <aidl/com/android/server/thread/openthread/INsdPublisher.h>

namespace otbr {
namespace Android {
using aidl::com::android::server::thread::openthread::BnNsdStatusReceiver;
using aidl::com::android::server::thread::openthread::DnsTxtAttribute;
using aidl::com::android::server::thread::openthread::INsdPublisher;

class MdnsPublisher : public Mdns::Publisher
{
public:
    explicit MdnsPublisher(Publisher::StateCallback aCallback)
    {
        mNextListenerId = 0;
        mStateCallback  = std::move(aCallback);
    }

    ~MdnsPublisher(void) { Stop(); }

    // In this Publisher implementation, SetINsdPublisher() does the job to start/stop the Publisher. That's because we
    // want to ensure ot-daemon won't do any mDNS operations when Thread is disabled.
    void SetINsdPublisher(std::shared_ptr<INsdPublisher> aINsdPublisher);

    otbrError Start(void) override { return OTBR_ERROR_MDNS; }

    void Stop(void) override
    {
        mServiceRegistrations.clear();
        mHostRegistrations.clear();
        mStateCallback(Mdns::Publisher::State::kIdle);
    }

    bool IsStarted(void) const override { return mNsdPublisher != nullptr; }

    void UnpublishService(const std::string &aName, const std::string &aType, ResultCallback &&aCallback) override;

    void UnpublishHost(const std::string &aName, ResultCallback &&aCallback) override;

    void UnpublishKey(const std::string &aName, ResultCallback &&aCallback) override;

    void SubscribeService(const std::string &aType, const std::string &aInstanceName) override;

    void UnsubscribeService(const std::string &aType, const std::string &aInstanceName) override;

    void SubscribeHost(const std::string &aHostName) override;

    void UnsubscribeHost(const std::string &aHostName) override;

    class NsdStatusReceiver : public BnNsdStatusReceiver
    {
    public:
        explicit NsdStatusReceiver(Mdns::Publisher::ResultCallback aCallback)
            : mCallback(std::move(aCallback))
        {
        }

        ::ndk::ScopedAStatus onSuccess(void) override;

        ::ndk::ScopedAStatus onError(int aError) override;

    private:
        Mdns::Publisher::ResultCallback mCallback;
    };

protected:
    otbrError PublishServiceImpl(const std::string &aHostName,
                                 const std::string &aName,
                                 const std::string &aType,
                                 const SubTypeList &aSubTypeList,
                                 uint16_t           aPort,
                                 const TxtData     &aTxtData,
                                 ResultCallback   &&aCallback) override;

    otbrError PublishHostImpl(const std::string &aName, const AddressList &aAddresses, ResultCallback &&aCallback);

    otbrError PublishKeyImpl(const std::string &aName, const KeyData &aKeyData, ResultCallback &&aCallback) override;

    void OnServiceResolveFailedImpl(const std::string &aType, const std::string &aInstanceName, int32_t aErrorCode);

    void OnHostResolveFailedImpl(const std::string &aHostName, int32_t aErrorCode);

    otbrError DnsErrorToOtbrError(int32_t aError);

private:
    class NsdServiceRegistration : public ServiceRegistration
    {
    public:
        NsdServiceRegistration(const std::string             &aHostName,
                               const std::string             &aName,
                               const std::string             &aType,
                               const SubTypeList             &aSubTypeList,
                               uint16_t                       aPort,
                               const TxtData                 &aTxtData,
                               ResultCallback               &&aCallback,
                               MdnsPublisher                 *aPublisher,
                               int32_t                        aListenerId,
                               std::shared_ptr<INsdPublisher> aINsdPublisher)
            : ServiceRegistration(aHostName,
                                  aName,
                                  aType,
                                  aSubTypeList,
                                  aPort,
                                  aTxtData,
                                  std::move(aCallback),
                                  aPublisher)
            , mListenerId(aListenerId)
            , mNsdPublisher(std::move(aINsdPublisher))

        {
        }

        ~NsdServiceRegistration(void) override;

        const int32_t                      mListenerId;
        std::shared_ptr<NsdStatusReceiver> mUnregisterReceiver;

    private:
        std::shared_ptr<INsdPublisher> mNsdPublisher;
    };

    int32_t AllocateListenerId(void);

    StateCallback                  mStateCallback;
    int32_t                        mNextListenerId;
    std::shared_ptr<INsdPublisher> mNsdPublisher = nullptr;
};

} // namespace Android
} // namespace otbr

#endif // OTBR_ANDROID_MDNS_PUBLISHER_HPP_
