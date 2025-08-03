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

#include <base/functional/callback.h>
#include <gmock/gmock.h>

#include <vector>

#include "btm_iso_api_types.h"

namespace hcic {
class HcicInterface {
 public:
  // iso_manager::cig_create_params is a workaround for the 10 params function
  // limitation that gmock sets
  virtual void SetCigParams(
      uint8_t cig_id,
      struct bluetooth::hci::iso_manager::cig_create_params cig_params,
      base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  virtual void RemoveCig(uint8_t cig_id,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  virtual void CreateCis(uint8_t num_cis,
                         const EXT_CIS_CREATE_CFG* cis_create_cfg,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  virtual void Disconnect(uint16_t handle, uint8_t reason) = 0;

  virtual void SetupIsoDataPath(
      uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
      uint8_t codec_id_format, uint16_t codec_id_company,
      uint16_t codec_id_vendor, uint32_t controller_delay,
      std::vector<uint8_t> codec_conf,
      base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  virtual void RemoveIsoDataPath(
      uint16_t iso_handle, uint8_t data_path_dir,
      base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  virtual void ReadIsoLinkQuality(
      uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb) = 0;

  // iso_manager::big_create_params is a workaround for the 10 params function
  // limitation that gmock sets
  virtual void CreateBig(
      uint8_t big_handle,
      struct bluetooth::hci::iso_manager::big_create_params big_params) = 0;

  virtual void TerminateBig(uint8_t big_handle, uint8_t reason) = 0;

  virtual ~HcicInterface() = default;
};

class MockHcicInterface : public HcicInterface {
 public:
  MOCK_METHOD((void), SetCigParams,
              (uint8_t cig_id,
               struct bluetooth::hci::iso_manager::cig_create_params cig_params,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD((void), RemoveCig,
              (uint8_t cig_id, base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD((void), CreateCis,
              (uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_create_cfg,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD((void), Disconnect, (uint16_t handle, uint8_t reason),
              (override));

  MOCK_METHOD((void), SetupIsoDataPath,
              (uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
               uint8_t codec_id_format, uint16_t codec_id_company,
               uint16_t codec_id_vendor, uint32_t controller_delay,
               std::vector<uint8_t> codec_conf,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD((void), RemoveIsoDataPath,
              (uint16_t iso_handle, uint8_t data_path_dir,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD((void), ReadIsoLinkQuality,
              (uint16_t iso_handle,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb),
              (override));

  MOCK_METHOD(
      (void), CreateBig,
      (uint8_t big_handle,
       struct bluetooth::hci::iso_manager::big_create_params big_params),
      (override));

  MOCK_METHOD((void), TerminateBig, (uint8_t big_handle, uint8_t reason),
              (override));
};

void SetMockHcicInterface(MockHcicInterface* mock_hcic_interface);

}  // namespace hcic
