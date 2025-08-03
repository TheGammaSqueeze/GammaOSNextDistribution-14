/*
 * Copyright 2022 The Android Open Source Project
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
#pragma once

#include <cstdint>
#include "types/raw_address.h"

namespace bluetooth {
namespace metrics {

void LogMetricsAdapterStateChanged(uint32_t state);
void LogMetricsBondCreateAttempt(RawAddress* addr, uint32_t device_type);
void LogMetricsBondStateChanged(
    RawAddress* addr, uint32_t device_type, uint32_t status, uint32_t bond_state, int32_t fail_reason);
void LogMetricsDeviceInfoReport(
    RawAddress* addr,
    uint32_t device_type,
    uint32_t class_of_device,
    uint32_t appearance,
    uint32_t vendor_id,
    uint32_t vendor_id_src,
    uint32_t product_id,
    uint32_t version);
void LogMetricsProfileConnectionStateChanged(RawAddress* addr, uint32_t profile, uint32_t status, uint32_t state);
void LogMetricsAclConnectAttempt(RawAddress* addr, uint32_t acl_state);
void LogMetricsAclConnectionStateChanged(
    RawAddress* addr, uint32_t transport, uint32_t status, uint32_t acl_state, uint32_t direction, uint32_t hci_reason);
void LogMetricsChipsetInfoReport();

void LogMetricsSuspendIdState(uint32_t state);

}  // namespace metrics
}  // namespace bluetooth
