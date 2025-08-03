/*
 * Copyright 2023 The Android Open Source Project
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

#include "stack/include/btm_iso_api.h"

using bluetooth::hci::iso_manager::BigCallbacks;
using bluetooth::hci::iso_manager::CigCallbacks;

namespace bluetooth {
namespace hci {

struct IsoManager::impl {};

IsoManager::IsoManager() {}
IsoManager::~IsoManager() {}
void IsoManager::RegisterCigCallbacks(CigCallbacks* /* callbacks */) const {}
void IsoManager::RegisterBigCallbacks(BigCallbacks* /* callbacks */) const {}
void IsoManager::RegisterOnIsoTrafficActiveCallback(void (*)(bool)) const {}
void IsoManager::CreateCig(
    uint8_t /* cig_id */,
    struct iso_manager::cig_create_params /* cig_params */) {}
void IsoManager::ReconfigureCig(
    uint8_t /* cig_id */,
    struct iso_manager::cig_create_params /* cig_params */) {}
void IsoManager::RemoveCig(uint8_t /* cig_id */, bool /* force */) {}
void IsoManager::EstablishCis(
    struct iso_manager::cis_establish_params /* conn_params */) {}
void IsoManager::DisconnectCis(uint16_t /* cis_handle */,
                               uint8_t /* reason */) {}
void IsoManager::SetupIsoDataPath(
    uint16_t /* iso_handle */,
    struct iso_manager::iso_data_path_params /* path_params */) {}
void IsoManager::RemoveIsoDataPath(uint16_t /* iso_handle */,
                                   uint8_t /* data_path_dir */) {}
void IsoManager::ReadIsoLinkQuality(uint16_t /* iso_handle */) {}
void IsoManager::SendIsoData(uint16_t /* iso_handle */,
                             const uint8_t* /* data */,
                             uint16_t /* data_len */) {}
void IsoManager::CreateBig(
    uint8_t /* big_id */,
    struct iso_manager::big_create_params /* big_params */) {}
void IsoManager::TerminateBig(uint8_t /* big_id */, uint8_t /* reason */) {}
void IsoManager::HandleIsoData(void* /* p_msg */) {}
void IsoManager::HandleDisconnect(uint16_t /* handle */, uint8_t /* reason */) {
}
void IsoManager::HandleNumComplDataPkts(uint8_t* /* p */,
                                        uint8_t /* evt_len */) {}
void IsoManager::HandleGdNumComplDataPkts(uint16_t /* handle */,
                                          uint16_t /* credits */) {}
void IsoManager::HandleHciEvent(uint8_t /* sub_code */, uint8_t* /* params */,
                                uint16_t /* length */) {}
void IsoManager::Start() {}
void IsoManager::Stop() {}
void IsoManager::Dump(int /* fd */) {}

}  // namespace hci
}  // namespace bluetooth
