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

#include "chre/platform/linux/pal_nan.h"
#include "chre/platform/assert.h"
#include "chre/util/memory.h"

namespace chre {

uint8_t PalNanEngine::subscribe(
    const struct chreWifiNanSubscribeConfig * /*config*/,
    uint32_t *subscriptionId) {
  uint8_t errorCode = CHRE_ERROR;
  if ((mFlags & FAIL_SUBSCRIBE) == 0) {
    uint32_t id = ++mSubscriptionIdCounter;
    mActiveSubscriptions.insert(id);
    *subscriptionId = id;
    errorCode = CHRE_ERROR_NONE;
  }
  return errorCode;
}

bool PalNanEngine::subscribeCancel(uint32_t subscriptionId) {
  const bool isActive = isSubscriptionActive(subscriptionId);
  mActiveSubscriptions.erase(subscriptionId);
  return isActive;
}

bool PalNanEngine::isSubscriptionActive(uint32_t subscriptionId) {
  return mActiveSubscriptions.count(subscriptionId) == 1;
}

void PalNanEngine::sendDiscoveryEvent(uint32_t subscriptionId) {
  constexpr size_t kSomeArraySize = 11;
  auto *event = memoryAlloc<struct chreWifiNanDiscoveryEvent>();
  CHRE_ASSERT_NOT_NULL(event);

  auto *serviceSpecificInfo =
      static_cast<uint8_t *>(chre::memoryAlloc(kSomeArraySize));
  CHRE_ASSERT_NOT_NULL(serviceSpecificInfo);

  event->subscribeId = subscriptionId;
  event->publishId = --mPublisherIdCounter;
  std::memcpy(event->publisherAddress, kSomePublishMac, CHRE_WIFI_BSSID_LEN);
  event->serviceSpecificInfo = std::move(serviceSpecificInfo);
  event->serviceSpecificInfoSize = kSomeArraySize;

  mWifiCallbacks->nanServiceDiscoveryCallback(event);
}

void PalNanEngine::destroyDiscoveryEvent(chreWifiNanDiscoveryEvent *event) {
  memoryFree(const_cast<uint8_t *>(event->serviceSpecificInfo));
  memoryFree(event);
}

void PalNanEngine::onServiceLost(uint32_t subscribeId, uint32_t publishId) {
  mWifiCallbacks->nanServiceLostCallback(subscribeId, publishId);
}

void PalNanEngine::onServiceTerminated(uint32_t subscribeId) {
  mWifiCallbacks->nanServiceTerminatedCallback(CHRE_ERROR, subscribeId);
}

}  // namespace chre
