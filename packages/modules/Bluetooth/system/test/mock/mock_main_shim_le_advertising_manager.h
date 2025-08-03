/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/memory/weak_ptr.h>
#include <gmock/gmock.h>

#include <vector>

#include "include/hardware/ble_advertiser.h"

class MockBleAdvertisingManager : public BleAdvertiserInterface {
 public:
  MockBleAdvertisingManager() = default;
  MockBleAdvertisingManager(const MockBleAdvertisingManager&) = delete;
  MockBleAdvertisingManager& operator=(const MockBleAdvertisingManager&) =
      delete;

  ~MockBleAdvertisingManager() override = default;

  static void Initialize();
  static void CleanUp();
  static MockBleAdvertisingManager* Get();

  MOCK_METHOD((void), StartAdvertising,
              (uint8_t advertiser_id, StatusCallback cb,
               AdvertiseParameters params, std::vector<uint8_t> advertise_data,
               std::vector<uint8_t> scan_response_data, int timeout_s,
               StatusCallback timeout_cb),
              (override));
  MOCK_METHOD((void), StartAdvertisingSet,
              (uint8_t client_id, int reg_id,
               IdTxPowerStatusCallback register_cb, AdvertiseParameters params,
               std::vector<uint8_t> advertise_data,
               std::vector<uint8_t> scan_response_data,
               PeriodicAdvertisingParameters periodic_params,
               std::vector<uint8_t> periodic_data, uint16_t duration,
               uint8_t maxExtAdvEvents, IdStatusCallback timeout_cb),
              (override));
  MOCK_METHOD((void), RegisterAdvertiser, (IdStatusCallback cb), (override));
  MOCK_METHOD((void), Enable,
              (uint8_t advertiser_id, bool enable, StatusCallback cb,
               uint16_t duration, uint8_t maxExtAdvEvents,
               StatusCallback timeout_cb),
              (override));
  MOCK_METHOD((void), SetParameters,
              (uint8_t advertiser_id, AdvertiseParameters params,
               ParametersCallback cb),
              (override));
  MOCK_METHOD((void), SetData,
              (int advertiser_id, bool set_scan_rsp, std::vector<uint8_t> data,
               StatusCallback cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingParameters,
              (int advertiser_id, PeriodicAdvertisingParameters periodic_params,
               StatusCallback cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingData,
              (int advertiser_id, std::vector<uint8_t> data, StatusCallback cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingEnable,
              (int advertiser_id, bool enable, bool include_adi,
               StatusCallback cb),
              (override));
  MOCK_METHOD((void), Unregister, (uint8_t advertiser_id), (override));
  MOCK_METHOD((void), GetOwnAddress,
              (uint8_t advertiser_id, GetAddressCallback cb), (override));
  MOCK_METHOD((void), RegisterCallbacks, (AdvertisingCallbacks * callbacks),
              (override));
  MOCK_METHOD((void), RegisterCallbacksNative,
              (AdvertisingCallbacks * callbacks, uint8_t client_id),
              (override));
};
