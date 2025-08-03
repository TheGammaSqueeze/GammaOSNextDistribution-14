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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_FILTER_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_FILTER_H_

#include "location/lbs/contexthub/nanoapps/nearby/ble_scan_record.h"
#include "location/lbs/contexthub/nanoapps/nearby/proto/ble_filter.nanopb.h"

namespace nearby {

bool MatchFastPair(const nearby_BleFilter &filter,
                   const BleScanRecord &scan_record,
                   nearby_BleFilterResult *result);

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FAST_PAIR_FILTER_H_
