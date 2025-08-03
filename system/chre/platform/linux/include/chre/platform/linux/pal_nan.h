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

#ifndef CHRE_PLATFORM_LINUX_PAL_NAN_H_
#define CHRE_PLATFORM_LINUX_PAL_NAN_H_

#include <unordered_set>
#include "chre/pal/wifi.h"
#include "chre/platform/assert.h"
#include "chre/util/memory.h"
#include "chre/util/non_copyable.h"
#include "chre/util/singleton.h"
#include "chre_api/chre/wifi.h"

namespace chre {

/**
 * @brief Fake NAN engine to verify core NAN functionality.
 *
 * This class implements a fake NAN engine to verify core functionality, with
 * functionality limited to creating (meaningless) subscription and publisher
 * IDs, along with creating and destroying discovery events.
 *
 * This class is intended to be used for simulation tests only.
 */

class PalNanEngine : public NonCopyable {
 public:
  /**
   * Flags that instruct the engine to fail operations for testing. Note
   * that they must be set before calling any APIs in this class. The flags
   * also are not reset upon exiting an API call - it is the responsibility
   * of the entity setting the flags to do this.
   */
  enum Flags : uint32_t {
    NONE = 0,
    FAIL_SUBSCRIBE = (0x1 << 0),
  };

  /**
   * Obtain a subscription ID.
   *
   * This method returns a subscription ID to the caller by reference,
   * implemented by a simple up-counter.
   *
   * The method will succeed unless @ref SetFlags as been called aith the value
   * FAIL_SUBSCRIBE to simulater a failure.
   *
   * @param config The Nan service subscription config, currently unused.
   * @param subscriptionId populated with the next value of a running counter.
   * @return an error code that is a value in @ref enum chreError
   */
  uint8_t subscribe(const struct chreWifiNanSubscribeConfig *config,
                    uint32_t *subscriptionId);

  /**
   * Cancels an active subscription.
   *
   * @return whether the subscription is successfully cancelled - that is if
   *          a subscription with the passed id is currently active.
   */
  bool subscribeCancel(uint32_t subscriptionId);

  /**
   * Returns whether a subscription is active.
   *
   * @return whether the subscription is active.
   */
  bool isSubscriptionActive(uint32_t subscriptionId);

  /**
   * Send a service discovery event.
   *
   * Sends a discovery event with the passed in subscription ID, a
   * publisher ID implemented by a simple down-counter, a static MAC address
   * for the publisher, and un-filled (but not NULL) service specific info.
   *
   * @param subscriptionId The ID of the subscriber to simulate a discovery
   *        for.
   */
  void sendDiscoveryEvent(uint32_t subscriptionId);

  /**
   * Destroy a discovery event object created by @ref createDiscoveryEvent.
   *
   * @param event The NAN discovery event that was created by
   *        createDiscoveryEvent.
   */
  void destroyDiscoveryEvent(chreWifiNanDiscoveryEvent *event);

  /**
   * Triggered from the test framework to simulate the loss of a publishing
   * service.
   *
   * @param subscribeId Id of the subscribing service.
   * @param publishId Id of the service that has been lost.
   */
  void onServiceLost(uint32_t subscribeId, uint32_t publishId);

  /**
   * Triggered from the test framework to simulate a subscription termination.
   *
   * @param subscribeId Id of the subscribing service that has been terminated.
   */
  void onServiceTerminated(uint32_t subscribeId);

  /**
   * Set the Platform Wifi Callbacks object
   *
   * Maintain a copy of the Pal WiFi callbacks here: this is particularly
   * useful for triggering events that are designed to be asynchronous (like
   * discovery events) synchronously from the test/simulation framework.
   *
   * @param api Pointer to the Pal WiFi callbacks structure.
   */
  void setPlatformWifiCallbacks(const struct chrePalWifiCallbacks *api) {
    mWifiCallbacks = api;
  }

  /**
   * Set flags from the test framework to instruct the engine to take
   * appropriate actions. Flags must be a value in @ref enum Flags, and
   * multiple flags can be specified at once. Note that it is the
   * responsibility of the test framework to reset the flags by calling
   * the function again with Flags::NONE.
   *
   * @param flags Flags to be set.
   */
  void setFlags(uint32_t flags) {
    mFlags = flags;
  }

 private:
  static constexpr uint8_t kSomePublishMac[CHRE_WIFI_BSSID_LEN] = {
      0x1, 0x2, 0x3, 0x4, 0x5, 0x6};
  uint32_t mSubscriptionIdCounter = 1;
  uint32_t mPublisherIdCounter = 0xcafe;
  uint32_t mFlags;
  std::unordered_set<uint32_t> mActiveSubscriptions;

  const struct chrePalWifiCallbacks *mWifiCallbacks = nullptr;
};

//! Provide an alias to the PalNanEngine singleton.
typedef Singleton<PalNanEngine> PalNanEngineSingleton;

}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_PAL_NAN_H_