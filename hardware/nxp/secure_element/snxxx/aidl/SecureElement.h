/******************************************************************************
 *
 *  Copyright 2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
#pragma once

#include <SyncEvent.h>
#include <aidl/android/hardware/secure_element/BnSecureElement.h>
#include <aidl/android/hardware/secure_element/ISecureElementCallback.h>
#include <android-base/logging.h>
#include <log/log.h>

#include "OsuHalExtn.h"
#include "phNxpEse_Api.h"

#define SESTATUS_SUCCESS 0

class ThreadMutex {
 public:
  ThreadMutex();
  virtual ~ThreadMutex();
  void lock();
  void unlock();
  operator pthread_mutex_t*() { return &mMutex; }

 private:
  pthread_mutex_t mMutex;
};

class AutoThreadMutex {
 public:
  AutoThreadMutex(ThreadMutex& m);
  virtual ~AutoThreadMutex();
  operator ThreadMutex&() { return mm; }
  operator pthread_mutex_t*() { return (pthread_mutex_t*)mm; }

 private:
  ThreadMutex& mm;
};

namespace aidl {
namespace android {
namespace hardware {
namespace secure_element {

using ::ndk::ICInterface;
using ndk::ScopedAStatus;

#ifndef MIN_APDU_LENGTH
#define MIN_APDU_LENGTH 0x04
#endif
#ifndef DEFAULT_BASIC_CHANNEL
#define DEFAULT_BASIC_CHANNEL 0x00
#endif
#ifndef MAX_AID_LENGTH
#define MAX_AID_LENGTH 0x10
#endif

struct SecureElement : public BnSecureElement {
 public:
  SecureElement();
  ::ndk::ScopedAStatus closeChannel(int8_t in_channelNumber) override;
  ::ndk::ScopedAStatus getAtr(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus init(
      const std::shared_ptr<
          ::aidl::android::hardware::secure_element::ISecureElementCallback>&
          in_clientCallback) override;
  ::ndk::ScopedAStatus isCardPresent(bool* _aidl_return) override;
  ::ndk::ScopedAStatus openBasicChannel(
      const std::vector<uint8_t>& in_aid, int8_t in_p2,
      std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus openLogicalChannel(
      const std::vector<uint8_t>& in_aid, int8_t in_p2,
      ::aidl::android::hardware::secure_element::LogicalChannelResponse*
          _aidl_return) override;
  ::ndk::ScopedAStatus reset() override;
  ::ndk::ScopedAStatus transmit(const std::vector<uint8_t>& in_data,
                                std::vector<uint8_t>* _aidl_return) override;

  static void NotifySeWaitExtension(phNxpEse_wtxState state);
  void updateSeHalInitState(bool);
  int seHalDeInit();

 private:
  uint8_t mMaxChannelCount;
  uint8_t mOpenedchannelCount = 0;
  Mutex seHalLock;
  bool mIsEseInitialized = false;
  static std::vector<bool> mOpenedChannels;

  static std::shared_ptr<ISecureElementCallback> mCb;
  bool mHasPriorityAccess = false;

  ESESTATUS seHalInit();
  int internalCloseChannel(uint8_t channelNumber);
  uint8_t getReserveChannelCnt(const std::vector<uint8_t>& aid);
  uint8_t getMaxChannelCnt();
};

}  // namespace secure_element
}  // namespace hardware
}  // namespace android
}  // namespace aidl