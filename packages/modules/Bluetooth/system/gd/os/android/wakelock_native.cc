/******************************************************************************
 *
 *  Copyright 2021 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "BtGdWakelockNative"

#include "os/internal/wakelock_native.h"

#include <aidl/android/system/suspend/ISystemSuspend.h>
#include <aidl/android/system/suspend/IWakeLock.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <fcntl.h>
#include <unistd.h>

#include <cerrno>
#include <functional>
#include <string>

// We want the os/log.h definitions
#undef LOG_DEBUG
#undef LOG_INFO

#include "os/log.h"

// Save the os/log.h definitions
#pragma push_macro("LOG_DEBUG")
#pragma push_macro("LOG_INFO")

// Undef these to avoid conflicting with later imports
#undef LOG_DEBUG
#undef LOG_INFO

using ::aidl::android::system::suspend::ISystemSuspend;
using ::aidl::android::system::suspend::IWakeLock;
using ::aidl::android::system::suspend::WakeLockType;

namespace bluetooth {
namespace os {
namespace internal {

// Restore the os/log.h definitions after all imported headers
#pragma pop_macro("LOG_DEBUG")
#pragma pop_macro("LOG_INFO")

static void onSuspendDeath(void* cookie) {
  auto onDeath = static_cast<std::function<void(void)>*>(cookie);
  (*onDeath)();
}

struct WakelockNative::Impl {
  Impl() : suspend_death_recipient(AIBinder_DeathRecipient_new(onSuspendDeath)) {}

  std::function<void(void)> onDeath = [this] {
    LOG_ERROR("ISystemSuspend HAL service died!");
    this->suspend_service = nullptr;
  };

  std::shared_ptr<ISystemSuspend> suspend_service = nullptr;
  std::shared_ptr<IWakeLock> current_wakelock = nullptr;
  ::ndk::ScopedAIBinder_DeathRecipient suspend_death_recipient;
};

void WakelockNative::Initialize() {
  LOG_INFO("Initializing native wake locks");
  const std::string suspendInstance = std::string() + ISystemSuspend::descriptor + "/default";
  pimpl_->suspend_service = ISystemSuspend::fromBinder(
      ndk::SpAIBinder(AServiceManager_waitForService(suspendInstance.c_str())));
  ASSERT_LOG(pimpl_->suspend_service, "Cannot get ISystemSuspend service");
  AIBinder_linkToDeath(
      pimpl_->suspend_service->asBinder().get(),
      pimpl_->suspend_death_recipient.get(),
      static_cast<void*>(&pimpl_->onDeath));
}

WakelockNative::StatusCode WakelockNative::Acquire(const std::string& lock_name) {
  if (!pimpl_->suspend_service) {
    LOG_ERROR("lock not acquired, ISystemService is not available");
    return StatusCode::NATIVE_SERVICE_NOT_AVAILABLE;
  }

  if (pimpl_->current_wakelock) {
    LOG_INFO("wakelock is already acquired");
    return StatusCode::SUCCESS;
  }

  auto status = pimpl_->suspend_service->acquireWakeLock(
      WakeLockType::PARTIAL, lock_name, &pimpl_->current_wakelock);
  if (!pimpl_->current_wakelock) {
    LOG_ERROR("wake lock not acquired: %s", status.getDescription().c_str());
    return StatusCode::NATIVE_API_ERROR;
  }

  return StatusCode::SUCCESS;
}

WakelockNative::StatusCode WakelockNative::Release(const std::string& /* lock_name */) {
  if (!pimpl_->current_wakelock) {
    LOG_WARN("no lock is currently acquired");
    return StatusCode::SUCCESS;
  }
  pimpl_->current_wakelock->release();
  pimpl_->current_wakelock = nullptr;
  return StatusCode::SUCCESS;
}

void WakelockNative::CleanUp() {
  LOG_INFO("Cleaning up native wake locks");
  if (pimpl_->current_wakelock) {
    LOG_INFO("releasing current wakelock during clean up");
    pimpl_->current_wakelock->release();
    pimpl_->current_wakelock = nullptr;
  }
  if (pimpl_->suspend_service) {
    LOG_INFO("Unlink death recipient");
    AIBinder_unlinkToDeath(
        pimpl_->suspend_service->asBinder().get(),
        pimpl_->suspend_death_recipient.get(),
        static_cast<void*>(&pimpl_->onDeath));
    pimpl_->suspend_service = nullptr;
  }
}

WakelockNative::WakelockNative() : pimpl_(std::make_unique<Impl>()) {}

WakelockNative::~WakelockNative() = default;

}  // namespace internal
}  // namespace os
}  // namespace bluetooth
