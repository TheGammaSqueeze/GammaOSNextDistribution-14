/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLCAMERASTREAM_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLCAMERASTREAM_H

#include <aidl/android/hardware/automotive/evs/BnEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <android/hardware/automotive/evs/1.1/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/types.h>

#include <list>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class AidlCameraStream final :
      public ::aidl::android::hardware::automotive::evs::BnEvsCameraStream {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsCameraStream follow.
    ::ndk::ScopedAStatus deliverFrame(const std::vector<aidlevs::BufferDesc>& buffer) override;
    ::ndk::ScopedAStatus notify(const aidlevs::EvsEventDesc& event) override;

    explicit AidlCameraStream(const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& stream);
    virtual ~AidlCameraStream() { mImpl = nullptr; }

    bool getBuffer(int id, aidlevs::BufferDesc* _return);

private:
    class IHidlCameraStream;
    class ImplV0;
    class ImplV1;

    std::shared_ptr<IHidlCameraStream> mImpl;
};

class AidlCameraStream::IHidlCameraStream {
public:
    virtual ::ndk::ScopedAStatus deliverFrame(const std::vector<aidlevs::BufferDesc>& buffer) = 0;
    virtual ::ndk::ScopedAStatus notify(const aidlevs::EvsEventDesc& event) = 0;
    virtual bool getBuffer(int id, aidlevs::BufferDesc* _return);

    explicit IHidlCameraStream(const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& stream) :
          mStream(stream) {}
    virtual ~IHidlCameraStream() {
        mStream = nullptr;
        mBuffers.clear();
    }

protected:
    ::android::sp<hidlevs::V1_0::IEvsCameraStream> mStream;
    std::list<aidlevs::BufferDesc> mBuffers;
};

class AidlCameraStream::ImplV0 final : public IHidlCameraStream {
public:
    ::ndk::ScopedAStatus deliverFrame(const std::vector<aidlevs::BufferDesc>& buffer) override;
    ::ndk::ScopedAStatus notify(const aidlevs::EvsEventDesc& event) override;

    explicit ImplV0(const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& stream);
    virtual ~ImplV0() { mStream = nullptr; }
};

class AidlCameraStream::ImplV1 final : public IHidlCameraStream {
public:
    ::ndk::ScopedAStatus deliverFrame(const std::vector<aidlevs::BufferDesc>& buffer) override;
    ::ndk::ScopedAStatus notify(const aidlevs::EvsEventDesc& event) override;

    explicit ImplV1(const ::android::sp<hidlevs::V1_1::IEvsCameraStream>& stream);
    virtual ~ImplV1() { mStream = nullptr; }

private:
    ::android::sp<hidlevs::V1_1::IEvsCameraStream> mStream;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLCAMERASTREAM_H
