/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_ACCOUNT_DATA_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_ACCOUNT_DATA_H_

#include "location/lbs/contexthub/nanoapps/nearby/byte_array.h"

namespace nearby {

struct FastPairAccountData {
  // Returns a FastPairAccountData by parsing the BLE service_data.
  // Note, the returned FastPairAccountData has the lifetime of service_data,
  // and the return will be invalid if service_data is altered/destructed after
  // parsing.
  static FastPairAccountData Parse(const ByteArray &service_data);

  FastPairAccountData(bool is_valid, uint8_t version, ByteArray filter,
                      ByteArray salt, ByteArray battery, ByteArray rrd)
      : is_valid(is_valid),
        version(version),
        filter(filter),
        salt(salt),
        battery(battery),
        rrd(rrd) {}

  const bool is_valid;
  const uint8_t version;
  const ByteArray filter;
  const ByteArray salt;
  // battery includes the Length-Type header.
  // See
  // https://developers.google.com/nearby/fast-pair/specifications/extensions/batterynotification#BatteryNotification.
  const ByteArray battery;
  // RRD includes the Length-Type header.
  const ByteArray rrd;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_ACCOUNT_DATA_H_
