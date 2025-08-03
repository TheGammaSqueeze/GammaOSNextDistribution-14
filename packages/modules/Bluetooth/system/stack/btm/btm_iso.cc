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

#include <memory>

#include "btm_iso_api.h"
#include "btm_iso_impl.h"
#include "stack/include/bt_hdr.h"

using bluetooth::hci::iso_manager::BigCallbacks;
using bluetooth::hci::iso_manager::CigCallbacks;
using bluetooth::hci::iso_manager::iso_impl;

namespace bluetooth {
namespace hci {

struct IsoManager::impl {
  impl(const IsoManager& iso_manager) : iso_manager_(iso_manager) {}

  void Start() {
    LOG_ASSERT(!iso_impl_);
    iso_impl_ = std::make_unique<iso_impl>();
  }

  void Stop() {
    LOG_ASSERT(iso_impl_);
    iso_impl_.reset();
  }

  void Dump(int fd) {
    if (iso_impl_) iso_impl_->dump(fd);
  }

  bool IsRunning() { return iso_impl_ ? true : false; }

  const IsoManager& iso_manager_;
  std::unique_ptr<iso_impl> iso_impl_;
};

IsoManager::IsoManager() : pimpl_(std::make_unique<impl>(*this)) {}

void IsoManager::RegisterCigCallbacks(CigCallbacks* callbacks) const {
  pimpl_->iso_impl_->handle_register_cis_callbacks(callbacks);
}

void IsoManager::RegisterBigCallbacks(BigCallbacks* callbacks) const {
  pimpl_->iso_impl_->handle_register_big_callbacks(callbacks);
}

void IsoManager::RegisterOnIsoTrafficActiveCallback(void callback(bool)) const {
  pimpl_->iso_impl_->handle_register_on_iso_traffic_active_callback(callback);
}

void IsoManager::CreateCig(uint8_t cig_id,
                           struct iso_manager::cig_create_params cig_params) {
  pimpl_->iso_impl_->create_cig(cig_id, std::move(cig_params));
}

void IsoManager::ReconfigureCig(
    uint8_t cig_id, struct iso_manager::cig_create_params cig_params) {
  pimpl_->iso_impl_->reconfigure_cig(cig_id, std::move(cig_params));
}

void IsoManager::RemoveCig(uint8_t cig_id, bool force) {
  pimpl_->iso_impl_->remove_cig(cig_id, force);
}

void IsoManager::EstablishCis(
    struct iso_manager::cis_establish_params conn_params) {
  pimpl_->iso_impl_->establish_cis(std::move(conn_params));
}

void IsoManager::DisconnectCis(uint16_t cis_handle, uint8_t reason) {
  pimpl_->iso_impl_->disconnect_cis(cis_handle, reason);
}

void IsoManager::SetupIsoDataPath(
    uint16_t iso_handle, struct iso_manager::iso_data_path_params path_params) {
  pimpl_->iso_impl_->setup_iso_data_path(iso_handle, std::move(path_params));
}

void IsoManager::RemoveIsoDataPath(uint16_t iso_handle, uint8_t data_path_dir) {
  pimpl_->iso_impl_->remove_iso_data_path(iso_handle, data_path_dir);
}

void IsoManager::ReadIsoLinkQuality(uint16_t iso_handle) {
  pimpl_->iso_impl_->read_iso_link_quality(iso_handle);
}

void IsoManager::SendIsoData(uint16_t iso_handle, const uint8_t* data,
                             uint16_t data_len) {
  pimpl_->iso_impl_->send_iso_data(iso_handle, data, data_len);
}

void IsoManager::CreateBig(uint8_t big_id,
                           struct iso_manager::big_create_params big_params) {
  pimpl_->iso_impl_->create_big(big_id, std::move(big_params));
}

void IsoManager::TerminateBig(uint8_t big_id, uint8_t reason) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->terminate_big(big_id, reason);
}

void IsoManager::HandleIsoData(void* p_msg) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->handle_iso_data(static_cast<BT_HDR*>(p_msg));
}

void IsoManager::HandleDisconnect(uint16_t handle, uint8_t reason) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->disconnection_complete(handle, reason);
}

void IsoManager::HandleNumComplDataPkts(uint8_t* p, uint8_t evt_len) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->handle_num_completed_pkts(p, evt_len);
}

void IsoManager::HandleGdNumComplDataPkts(uint16_t handle, uint16_t credits) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->handle_gd_num_completed_pkts(handle, credits);
}

void IsoManager::HandleHciEvent(uint8_t sub_code, uint8_t* params,
                                uint16_t length) {
  if (pimpl_->IsRunning())
    pimpl_->iso_impl_->on_iso_event(sub_code, params, length);
}

void IsoManager::Start() {
  if (!pimpl_->IsRunning())
    pimpl_->Start();
}

void IsoManager::Stop() {
  if (pimpl_->IsRunning())
    pimpl_->Stop();
}

void IsoManager::Dump(int fd) {
  if (pimpl_->IsRunning()) pimpl_->Dump(fd);
}

IsoManager::~IsoManager() = default;

}  // namespace hci
}  // namespace bluetooth
