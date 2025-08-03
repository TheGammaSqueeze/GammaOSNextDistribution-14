/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

#include <gmock/gmock.h>

#include "btm_iso_api.h"

struct MockIsoManager {
 public:
  static MockIsoManager* GetInstance();

  MockIsoManager() = default;
  MockIsoManager(const MockIsoManager&) = delete;
  MockIsoManager& operator=(const MockIsoManager&) = delete;

  virtual ~MockIsoManager() = default;

  MOCK_METHOD((void), RegisterCigCallbacks,
              (bluetooth::hci::iso_manager::CigCallbacks * callbacks), (const));
  MOCK_METHOD((void), RegisterBigCallbacks,
              (bluetooth::hci::iso_manager::BigCallbacks * callbacks), (const));
  MOCK_METHOD((void), RegisterOnIsoTrafficActiveCallbacks,
              (void callbacks(bool)), (const));
  MOCK_METHOD(
      (void), CreateCig,
      (uint8_t cig_id,
       struct bluetooth::hci::iso_manager::cig_create_params cig_params));
  MOCK_METHOD(
      (void), ReconfigureCig,
      (uint8_t cig_id,
       struct bluetooth::hci::iso_manager::cig_create_params cig_params));
  MOCK_METHOD((void), RemoveCig, (uint8_t cig_id, bool force));
  MOCK_METHOD(
      (void), EstablishCis,
      (struct bluetooth::hci::iso_manager::cis_establish_params conn_params));
  MOCK_METHOD((void), DisconnectCis, (uint16_t cis_handle, uint8_t reason));
  MOCK_METHOD(
      (void), SetupIsoDataPath,
      (uint16_t iso_handle,
       struct bluetooth::hci::iso_manager::iso_data_path_params path_params));
  MOCK_METHOD((void), RemoveIsoDataPath,
              (uint16_t iso_handle, uint8_t data_path_dir));
  MOCK_METHOD((void), SendIsoData,
              (uint16_t iso_handle, const uint8_t* data, uint16_t data_len));
  MOCK_METHOD((void), ReadIsoLinkQuality, (uint16_t iso_handle));
  MOCK_METHOD(
      (void), CreateBig,
      (uint8_t big_id,
       struct bluetooth::hci::iso_manager::big_create_params big_params));
  MOCK_METHOD((void), TerminateBig, (uint8_t big_id, uint8_t reason));
  MOCK_METHOD((void), HandleIsoData, (void* p_msg));
  MOCK_METHOD((void), HandleDisconnect, (uint16_t handle, uint8_t reason));
  MOCK_METHOD((void), HandleNumComplDataPkts, (uint8_t * p, uint8_t evt_len));
  MOCK_METHOD((void), HandleGdNumComplDataPkts, (uint8_t * p, uint8_t evt_len));
  MOCK_METHOD((void), HandleHciEvent,
              (uint8_t sub_code, uint8_t* params, uint16_t length));

  MOCK_METHOD((void), Start, ());
  MOCK_METHOD((void), Stop, ());
};
