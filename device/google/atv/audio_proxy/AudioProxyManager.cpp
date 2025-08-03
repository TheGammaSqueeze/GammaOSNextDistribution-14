// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "AudioProxyManager.h"

#include <aidl/device/google/atv/audio_proxy/IAudioProxy.h>
#include <android-base/logging.h>
#include <android-base/thread_annotations.h>
#include <android/binder_manager.h>

#include <mutex>

#include "AudioProxyDevice.h"
#include "StreamProviderImpl.h"

using aidl::device::google::atv::audio_proxy::IAudioProxy;
using android::sp;
using android::status_t;

namespace audio_proxy {
namespace {

bool checkDevice(audio_proxy_device_t* device) {
  return device && device->get_address && device->open_output_stream &&
         device->close_output_stream &&
         // Check v2 extension. Currently only MediaShell uses this library and
         // we'll make sure the MediaShell will update to use the new API.
         device->v2 && device->v2->get_service_name &&
         device->v2->open_output_stream;
}

std::shared_ptr<IAudioProxy> getAudioProxyService(
    const std::string& serviceName) {
  std::string instanceName =
      std::string(IAudioProxy::descriptor) + "/" + serviceName;
  return IAudioProxy::fromBinder(
      ndk::SpAIBinder(AServiceManager_getService(instanceName.c_str())));
}

class AudioProxyManagerImpl : public AudioProxyManager {
 public:
  AudioProxyManagerImpl();
  ~AudioProxyManagerImpl() override = default;

  bool registerDevice(audio_proxy_device_t* device) override;


 private:
  static void onServiceDied(void* cookie);
  bool reconnectService();
  bool reconnectService_Locked() REQUIRES(mLock);

  ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

  std::mutex mLock;
  std::shared_ptr<IAudioProxy> mService GUARDED_BY(mLock);
  std::unique_ptr<AudioProxyDevice> mDevice GUARDED_BY(mLock);
};

AudioProxyManagerImpl::AudioProxyManagerImpl()
    : mDeathRecipient(
          AIBinder_DeathRecipient_new(AudioProxyManagerImpl::onServiceDied)) {}

bool AudioProxyManagerImpl::registerDevice(audio_proxy_device_t* device) {
  if (!checkDevice(device)) {
    LOG(ERROR) << "Invalid device.";
    return false;
  }

  std::scoped_lock<std::mutex> lock(mLock);
  if (mDevice) {
    DCHECK(mService);
    LOG(ERROR) << "Device already registered!";
    return false;
  }
  mDevice = std::make_unique<AudioProxyDevice>(device);

  DCHECK(!mService);
  return reconnectService_Locked();
}

bool AudioProxyManagerImpl::reconnectService() {
  std::scoped_lock<std::mutex> lock(mLock);
  return reconnectService_Locked();
}

bool AudioProxyManagerImpl::reconnectService_Locked() {
  DCHECK(mDevice);

  auto service = getAudioProxyService(mDevice->getServiceName());
  if (!service) {
    LOG(ERROR) << "Failed to reconnect service";
    return false;
  }

  binder_status_t binder_status = AIBinder_linkToDeath(
      service->asBinder().get(), mDeathRecipient.get(), this);
  if (binder_status != STATUS_OK) {
    LOG(ERROR) << "Failed to linkToDeath " << static_cast<int>(binder_status);
    return false;
  }

  ndk::ScopedAStatus status = service->start(
      ndk::SharedRefBase::make<StreamProviderImpl>(mDevice.get()));
  if (!status.isOk()) {
    LOG(ERROR) << "Failed to start service.";
    return false;
  }

  mService = std::move(service);
  return true;
}

// static
void AudioProxyManagerImpl::onServiceDied(void* cookie) {
  auto* manager = static_cast<AudioProxyManagerImpl*>(cookie);
  manager->reconnectService();
}

}  // namespace

std::unique_ptr<AudioProxyManager> createAudioProxyManager() {
  return std::make_unique<AudioProxyManagerImpl>();
}

}  // namespace audio_proxy
