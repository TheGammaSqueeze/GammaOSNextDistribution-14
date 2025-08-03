/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_PLATFORM_BLE_H_
#define CHRE_PLATFORM_PLATFORM_BLE_H_

#include "chre/target_platform/platform_ble_base.h"
#include "chre/util/time.h"

namespace chre {

class PlatformBle : public PlatformBleBase {
 public:
  /**
   * Performs platform-specific deinitialization of the PlatformBle instance.
   */
  ~PlatformBle();

  /**
   * Initializes the platform-specific BLE implementation. This is potentially
   * called at a later stage of initialization than the constructor, so platform
   * implementations are encouraged to put any blocking initialization here.
   */
  void init();

  /**
   * Returns the set of BLE capabilities that the platform has exposed. This
   * may return CHRE_BLE_CAPABILITIES_NONE if BLE is not supported.
   *
   * @return the BLE capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  /**
   * Returns the set of BLE filter capabilities that the platform has exposed.
   * This may return CHRE_BLE_FILTER_CAPABILITIES_NONE if BLE filtering is not
   * supported.
   *
   * @return the BLE filter capabilities exposed by this platform.
   */
  uint32_t getFilterCapabilities();

  /**
   * Begins a BLE scan asynchronously. The result is delivered through a
   * CHRE_EVENT_BLE_ASYNC_RESULT event.
   *
   * @param mode Scanning mode selected among enum chreBleScanMode
   * @param reportDelayMs Maximum requested batching delay in ms. 0 indicates no
   *                      batching. Note that the system may deliver results
   *                      before the maximum specified delay is reached.
   * @param filter Pointer to the requested best-effort filter configuration as
   *               defined by struct chreBleScanFilter. The ownership of filter
   *               and its nested elements remains with the caller, and the
   *               caller may release it as soon as chreBleStartScanAsync()
   *               returns.
   * @return true if scan was successfully enabled.
   */
  bool startScanAsync(chreBleScanMode mode, uint32_t reportDelayMs,
                      const struct chreBleScanFilterV1_9 *filter);

  /**
   * End a BLE scan asynchronously. The result is delivered through a
   * CHRE_EVENT_BLE_ASYNC_RESULT event.
   *
   * @return true if scan was successfully ended.
   */
  bool stopScanAsync();

  /**
   * Releases an advertising event that was previously provided to the BLE
   * manager.
   *
   * @param event the event to release.
   */
  void releaseAdvertisingEvent(struct chreBleAdvertisementEvent *event);

  /**
   * Reads the RSSI on a given LE-ACL connection handle.
   *
   * Only one call to this method may be outstanding until the
   * readRssiCallback() is invoked. The readRssiCallback() is guaranteed to be
   * invoked exactly once within CHRE_PAL_BLE_READ_RSSI_COMPLETE_TIMEOUT_NS of
   * readRssi() being invoked.
   *
   * @param connectionHandle The LE-ACL handle upon which the RSSI is to be
   * read.
   *
   * @return true if the request was accepted, in which case a subsequent call
   *         to readRssiCallback() will be used to indicate the result of the
   *         operation.
   *
   * @since v1.8
   */
  bool readRssiAsync(uint16_t connectionHandle);

  /**
   * Initiates a flush operation where all batched advertisement events will be
   * immediately processed.
   *
   * @return true if the request was accepted, in which case a subsequent call
   * to flushCallback() will be used to indicate the result of the operation.
   *
   * @since v1.9
   */
  bool flushAsync();
};

}  // namespace chre

#endif  // CHRE_PLATFORM_PLATFORM_BLE_H_
