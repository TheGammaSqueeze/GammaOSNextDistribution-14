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

#include <aidl/android/hardware/secure_element/BnSecureElement.h>
#include <aidl/android/hardware/secure_element/ISecureElement.h>
#include <aidl/android/hardware/secure_element/ISecureElementCallback.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <hardware/hardware.h>
#include <log/log.h>

#include "phNxpEse_Api.h"

#define SESTATUS_SUCCESS 0

namespace aidl {
namespace vendor {
namespace nxp {
namespace virtual_iso {

using ::aidl::android::hardware::secure_element::ISecureElementCallback;
using ::aidl::android::hardware::secure_element::LogicalChannelResponse;
using ::ndk::ICInterface;
using ndk::ScopedAStatus;

#ifndef DEFAULT_BASIC_CHANNEL
#define DEFAULT_BASIC_CHANNEL 0x00
#endif

struct VirtualISO
    : public ::aidl::android::hardware::secure_element::BnSecureElement {
  VirtualISO();
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

 private:
  uint8_t mMaxChannelCount;
  uint8_t mOpenedchannelCount = 0;
  bool mIsEseInitialized = false;
  static std::vector<bool> mOpenedChannels;

  static std::shared_ptr<ISecureElementCallback> mCallback;

  int seHalDeInit();
  ESESTATUS seHalInit();
  int internalCloseChannel(uint8_t channelNumber);
};

}  // namespace virtual_iso
}  // namespace nxp
}  // namespace vendor
}  // namespace aidl