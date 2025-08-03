/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#include <bluetooth/log.h>

#include "eatt_impl.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "types/raw_address.h"

using bluetooth::eatt::eatt_impl;

namespace bluetooth {
namespace eatt {

struct EattExtension::impl {
  impl() = default;
  ~impl() = default;

  void Start() {
    if (eatt_impl_) {
      log::error("Eatt already started");
      return;
    };

    /* Register server for Eatt */
    memset(&reg_info_, 0, sizeof(reg_info_));
    reg_info_.pL2CA_CreditBasedConnectInd_Cb = eatt_connect_ind;
    reg_info_.pL2CA_CreditBasedConnectCfm_Cb = eatt_connect_cfm;
    reg_info_.pL2CA_CreditBasedReconfigCompleted_Cb = eatt_reconfig_completed;
    reg_info_.pL2CA_DisconnectInd_Cb = eatt_disconnect_ind;
    reg_info_.pL2CA_Error_Cb = eatt_error_cb;
    reg_info_.pL2CA_DataInd_Cb = eatt_data_ind;
    reg_info_.pL2CA_CreditBasedCollisionInd_Cb = eatt_collision_ind;

    if (L2CA_RegisterLECoc(BT_PSM_EATT, reg_info_, BTM_SEC_NONE, {}) == 0) {
      log::error("cannot register EATT");
    } else {
      eatt_impl_ = std::make_unique<eatt_impl>();
    }
  }

  void Stop() {
    if (!eatt_impl_) {
      log::error("Eatt not started");
      return;
    }
    eatt_impl_.reset(nullptr);
    L2CA_DeregisterLECoc(BT_PSM_EATT);
  }

  bool IsRunning() { return eatt_impl_ ? true : false; }

  static eatt_impl* GetImplInstance(void) {
    auto* instance = EattExtension::GetInstance();
    return instance->pimpl_->eatt_impl_.get();
  }

  static void eatt_connect_ind(const RawAddress& bda,
                               std::vector<uint16_t>& lcids, uint16_t psm,
                               uint16_t peer_mtu, uint8_t identifier) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl)
      p_eatt_impl->eatt_l2cap_connect_ind(bda, lcids, psm, peer_mtu,
                                          identifier);
  }

  static void eatt_connect_cfm(const RawAddress& bda, uint16_t lcid,
                               uint16_t peer_mtu, uint16_t result) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl)
      p_eatt_impl->eatt_l2cap_connect_cfm(bda, lcid, peer_mtu, result);
  }

  static void eatt_reconfig_completed(const RawAddress& bda, uint16_t lcid,
                                      bool is_local_cfg,
                                      tL2CAP_LE_CFG_INFO* p_cfg) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl)
      p_eatt_impl->eatt_l2cap_reconfig_completed(bda, lcid, is_local_cfg,
                                                 p_cfg);
  }

  static void eatt_collision_ind(const RawAddress& bd_addr) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl) p_eatt_impl->eatt_l2cap_collision_ind(bd_addr);
  }

  static void eatt_error_cb(uint16_t lcid, uint16_t reason) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl) p_eatt_impl->eatt_l2cap_error_cb(lcid, reason);
  }

  static void eatt_disconnect_ind(uint16_t lcid, bool please_confirm) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl)
      p_eatt_impl->eatt_l2cap_disconnect_ind(lcid, please_confirm);
  }

  static void eatt_data_ind(uint16_t lcid, BT_HDR* data_p) {
    auto p_eatt_impl = GetImplInstance();
    if (p_eatt_impl) p_eatt_impl->eatt_l2cap_data_ind(lcid, data_p);
  }

  std::unique_ptr<eatt_impl> eatt_impl_;
  tL2CAP_APPL_INFO reg_info_;
};

void EattExtension::AddFromStorage(const RawAddress& bd_addr) {
  eatt_impl* p_eatt_impl = EattExtension::impl::GetImplInstance();
  if (p_eatt_impl) p_eatt_impl->add_from_storage(bd_addr);
}

EattExtension::EattExtension() : pimpl_(std::make_unique<impl>()) {}

bool EattExtension::IsEattSupportedByPeer(const RawAddress& bd_addr) {
  return pimpl_->eatt_impl_->is_eatt_supported_by_peer(bd_addr);
}

void EattExtension::Connect(const RawAddress& bd_addr) {
  pimpl_->eatt_impl_->connect(bd_addr);
}

void EattExtension::Disconnect(const RawAddress& bd_addr, uint16_t cid) {
  pimpl_->eatt_impl_->disconnect(bd_addr, cid);
}

void EattExtension::Reconfigure(const RawAddress& bd_addr, uint16_t cid,
                                uint16_t mtu) {
  pimpl_->eatt_impl_->reconfigure(bd_addr, cid, mtu);
}
void EattExtension::ReconfigureAll(const RawAddress& bd_addr, uint16_t mtu) {
  pimpl_->eatt_impl_->reconfigure_all(bd_addr, mtu);
}

EattChannel* EattExtension::FindEattChannelByCid(const RawAddress& bd_addr,
                                                 uint16_t cid) {
  return pimpl_->eatt_impl_->find_eatt_channel_by_cid(bd_addr, cid);
}

EattChannel* EattExtension::FindEattChannelByTransId(const RawAddress& bd_addr,
                                                     uint32_t trans_id) {
  return pimpl_->eatt_impl_->find_eatt_channel_by_transid(bd_addr, trans_id);
}

bool EattExtension::IsIndicationPending(const RawAddress& bd_addr,
                                        uint16_t indication_handle) {
  return pimpl_->eatt_impl_->is_indication_pending(bd_addr, indication_handle);
}

EattChannel* EattExtension::GetChannelAvailableForIndication(
    const RawAddress& bd_addr) {
  return pimpl_->eatt_impl_->get_channel_available_for_indication(bd_addr);
}

void EattExtension::FreeGattResources(const RawAddress& bd_addr) {
  pimpl_->eatt_impl_->free_gatt_resources(bd_addr);
}

bool EattExtension::IsOutstandingMsgInSendQueue(const RawAddress& bd_addr) {
  return pimpl_->eatt_impl_->is_outstanding_msg_in_send_queue(bd_addr);
}

EattChannel* EattExtension::GetChannelWithQueuedDataToSend(
    const RawAddress& bd_addr) {
  return pimpl_->eatt_impl_->get_channel_with_queued_data(bd_addr);
}

EattChannel* EattExtension::GetChannelAvailableForClientRequest(
    const RawAddress& bd_addr) {
  return pimpl_->eatt_impl_->get_channel_available_for_client_request(bd_addr);
}

/* Start stop GATT indication timer per CID */
void EattExtension::StartIndicationConfirmationTimer(const RawAddress& bd_addr,
                                                     uint16_t cid) {
  pimpl_->eatt_impl_->start_indication_confirm_timer(bd_addr, cid);
}

void EattExtension::StopIndicationConfirmationTimer(const RawAddress& bd_addr,
                                                    uint16_t cid) {
  pimpl_->eatt_impl_->stop_indication_confirm_timer(bd_addr, cid);
}

/* Start stop application indication timeout */
void EattExtension::StartAppIndicationTimer(const RawAddress& bd_addr,
                                            uint16_t cid) {
  pimpl_->eatt_impl_->start_app_indication_timer(bd_addr, cid);
}

void EattExtension::StopAppIndicationTimer(const RawAddress& bd_addr,
                                           uint16_t cid) {
  pimpl_->eatt_impl_->stop_app_indication_timer(bd_addr, cid);
}

void EattExtension::Start() { pimpl_->Start(); }

void EattExtension::Stop() { pimpl_->Stop(); }

EattExtension::~EattExtension() = default;

}  // namespace eatt
}  // namespace bluetooth
