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

#ifndef CHRE_CORE_BLE_REQUEST_H_
#define CHRE_CORE_BLE_REQUEST_H_

#include "chre/util/dynamic_vector.h"
#include "chre/util/non_copyable.h"
#include "chre/util/system/debug_dump.h"
#include "chre_api/chre/ble.h"

namespace chre {

// Indicates what the current status of this request is w.r.t. its usage by
// the PAL.
enum class RequestStatus : uint8_t {
  // Indicates the request is waiting to be sent to the PAL
  PENDING_REQ,
  // Indicates the request has been issued to the PAL, but hasn't received
  // a response yet
  PENDING_RESP,
  // Indicates this request has been successfully applied by the PAL.
  APPLIED,
};

class BleRequest : public NonCopyable {
 public:
  BleRequest();

  BleRequest(uint16_t instanceId, bool enable, const void *cookie);

  BleRequest(uint16_t instanceId, bool enable, chreBleScanMode mode,
             uint32_t reportDelayMs, const chreBleScanFilterV1_9 *filter,
             const void *cookie);

  BleRequest(BleRequest &&other);

  BleRequest &operator=(BleRequest &&other);

  /**
   * Merges current request with other request. Takes maximum value of mode and
   * minimum value of reportDelayMs and rssiThreshold. Takes superset of generic
   * filters from both requests.
   *
   * @param request The other request to compare the attributes of.
   * @return true if any of the attributes of this request changed.
   */
  bool mergeWith(const BleRequest &request);

  /**
   * Checks whether current request is equivalent to the other request.
   *
   * @param request The other request to compare the attributes of.
   * @return true if the requests are equivalent.
   */
  bool isEquivalentTo(const BleRequest &request);

  /**
   * @return The instance id of the nanoapp that owns this request
   */
  uint16_t getInstanceId() const;

  /**
   * @return The scan mode of this request.
   */
  chreBleScanMode getMode() const;

  /**
   * @return The report delay of this request.
   */
  uint32_t getReportDelayMs() const;

  /**
   * @return The RSSI threshold of this request.
   */
  int8_t getRssiThreshold() const;

  /**
   * @return The current status of this request.
   */
  RequestStatus getRequestStatus() const;

  /**
   * @param status The status this request should be set to.
   */
  void setRequestStatus(RequestStatus status);

  /**
   * @return Generic filters of this request.
   */
  const DynamicVector<chreBleGenericFilter> &getGenericFilters() const;

  /**
   * @return Broadcaster address filters of this request.
   */
  const DynamicVector<chreBleBroadcasterAddressFilter> &getBroadcasterFilters()
      const;

  /**
   * @return The cookie this request.
   */
  const void *getCookie() const;

  /**
   * @return chreBleScanFilterV1_9 that is valid only as long as the internal
   *    contents of this class are not modified
   */
  chreBleScanFilterV1_9 getScanFilter() const;

  /**
   * @return true if nanoapp intends to enable a request.
   */
  bool isEnabled() const;

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   * into one of the buffers.
   * @param isPlatformRequest true if the request to be logged was sent to the
   * platform.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump,
                        bool isPlatformRequest = false) const;

 private:
  // Maximum requested batching delay in ms.
  uint32_t mReportDelayMs;

  // Instance id of nanoapp that sent the request.
  uint16_t mInstanceId;

  // Scanning mode selected among enum chreBleScanMode.
  chreBleScanMode mMode;

  // Whether a nanoapp intends to enable this request. If set to false, the
  // following members are invalid: mMode, mReportDelayMs, mGenericFilters.
  bool mEnabled;

  // RSSI threshold filter.
  int8_t mRssiThreshold;

  // The current status of this request. Note that this value is not considered
  // when determining equivalence or whe merging to prevent extra churn by the
  // request multiplexer.
  RequestStatus mStatus;

  // Generic scan filters.
  DynamicVector<chreBleGenericFilter> mGenericFilters;

  // Broadcaster address filters.
  DynamicVector<chreBleBroadcasterAddressFilter> mBroadcasterFilters;

  // Cookie value included in this request, supplied by the nanoapp.
  const void *mCookie;
};

}  // namespace chre

#endif  // CHRE_CORE_BLE_REQUEST_H_