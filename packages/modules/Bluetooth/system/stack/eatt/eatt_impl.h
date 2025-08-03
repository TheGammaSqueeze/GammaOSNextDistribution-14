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

#include <base/logging.h>
#include <bluetooth/log.h>

#include <map>
#include <vector>

#include "bind_helpers.h"
#include "device/include/controller.h"
#include "eatt.h"
#include "internal_include/bt_trace.h"
#include "internal_include/stack_config.h"
#include "l2c_api.h"
#include "os/log.h"
#include "osi/include/alarm.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_sec.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/main_thread.h"

namespace bluetooth {
namespace eatt {

#define BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK 0x01

class eatt_device {
 public:
  RawAddress bda_;
  uint16_t rx_mtu_;
  uint16_t rx_mps_;

  tGATT_TCB* eatt_tcb_;

  std::map<uint16_t, std::shared_ptr<EattChannel>> eatt_channels;
  bool collision;
  eatt_device(const RawAddress& bd_addr, uint16_t mtu, uint16_t mps)
      : rx_mtu_(mtu), rx_mps_(mps), eatt_tcb_(nullptr), collision(false) {
    bda_ = bd_addr;
  }
};

struct eatt_impl {
  std::vector<eatt_device> devices_;
  uint16_t psm_;
  uint16_t default_mtu_;
  uint16_t max_mps_;
  tL2CAP_APPL_INFO reg_info_;

  base::WeakPtrFactory<eatt_impl> weak_factory_{this};

  eatt_impl() {
    default_mtu_ = EATT_DEFAULT_MTU;
    max_mps_ = EATT_MIN_MTU_MPS;
    psm_ = BT_PSM_EATT;
  };

  ~eatt_impl() = default;

  eatt_device* find_device_by_cid(uint16_t lcid) {
    /* This works only because Android CIDs are unique across the ACL
     * connections */
    auto iter = find_if(devices_.begin(), devices_.end(),
                        [&lcid](const eatt_device& ed) {
                          auto it = ed.eatt_channels.find(lcid);
                          return it != ed.eatt_channels.end();
                        });

    return (iter == devices_.end()) ? nullptr : &(*iter);
  }

  EattChannel* find_channel_by_cid(uint16_t lcid) {
    eatt_device* eatt_dev = find_device_by_cid(lcid);
    if (!eatt_dev) return nullptr;

    auto it = eatt_dev->eatt_channels.find(lcid);
    return (it == eatt_dev->eatt_channels.end()) ? nullptr : it->second.get();
  }

  bool is_channel_connection_pending(eatt_device* eatt_dev) {
    for (const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el :
         eatt_dev->eatt_channels) {
      if (el.second->state_ == EattChannelState::EATT_CHANNEL_PENDING)
        return true;
    }
    return false;
  }

  EattChannel* find_channel_by_cid(const RawAddress& bdaddr, uint16_t lcid) {
    eatt_device* eatt_dev = find_device_by_address(bdaddr);
    if (!eatt_dev) return nullptr;

    auto it = eatt_dev->eatt_channels.find(lcid);
    return (it == eatt_dev->eatt_channels.end()) ? nullptr : it->second.get();
  }

  void remove_channel_by_cid(eatt_device* eatt_dev, uint16_t lcid) {
    auto channel = eatt_dev->eatt_channels[lcid];
    if (!channel->cl_cmd_q_.empty()) {
      log::warn("Channel {:c}, for device {} is not empty on disconnection.",
                lcid, ADDRESS_TO_LOGGABLE_STR(channel->bda_));
      channel->cl_cmd_q_.clear();
    }

    eatt_dev->eatt_channels.erase(lcid);

    if (eatt_dev->eatt_channels.size() == 0) eatt_dev->eatt_tcb_ = NULL;
  }

  void remove_channel_by_cid(uint16_t lcid) {
    eatt_device* eatt_dev = find_device_by_cid(lcid);
    if (!eatt_dev) return;

    remove_channel_by_cid(eatt_dev, lcid);
  }

  bool eatt_l2cap_connect_ind_common(const RawAddress& bda,
                                     std::vector<uint16_t>& lcids, uint16_t psm,
                                     uint16_t peer_mtu, uint8_t identifier) {
    /* The assumption is that L2CAP layer already check parameters etc.
     * Get our capabilities and accept all the channels.
     */
    eatt_device* eatt_dev = this->find_device_by_address(bda);
    if (!eatt_dev) {
      /* If there is no device it means, Android did not read yet Server
       * supported features, but according to Core 5.3, Vol 3,  Part G, 6.2.1,
       * for LE case it is not necessary to read it before establish connection.
       * Therefore assume, device supports EATT since we got request to create
       * EATT channels. Just create device here. */
      log::info("Adding device: {} on incoming EATT creation request",
                ADDRESS_TO_LOGGABLE_STR(bda));
      eatt_dev = add_eatt_device(bda);
    }

    uint16_t max_mps = controller_get_interface()->get_acl_data_size_ble();

    tL2CAP_LE_CFG_INFO local_coc_cfg = {
        .mtu = eatt_dev->rx_mtu_,
        .mps = eatt_dev->rx_mps_ < max_mps ? eatt_dev->rx_mps_ : max_mps,
        .credits = L2CA_LeCreditDefault(),
    };

    if (!L2CA_ConnectCreditBasedRsp(bda, identifier, lcids, L2CAP_CONN_OK,
                                    &local_coc_cfg))
      return false;

    if (!eatt_dev->eatt_tcb_) {
      eatt_dev->eatt_tcb_ =
          gatt_find_tcb_by_addr(eatt_dev->bda_, BT_TRANSPORT_LE);
      CHECK(eatt_dev->eatt_tcb_);
    }

    for (uint16_t cid : lcids) {
      EattChannel* channel = find_eatt_channel_by_cid(bda, cid);
      CHECK(!channel);

      auto chan = std::make_shared<EattChannel>(eatt_dev->bda_, cid, peer_mtu,
                                                eatt_dev->rx_mtu_);
      eatt_dev->eatt_channels.insert({cid, chan});

      chan->EattChannelSetState(EattChannelState::EATT_CHANNEL_OPENED);
      eatt_dev->eatt_tcb_->eatt++;

      log::info("Channel connected CID {}", loghex(cid));
    }

    return true;
  }

  /* This is for the L2CAP ECoC Testing. */
  void upper_tester_send_data_if_needed(const RawAddress& bda,
                                        uint16_t cid = 0) {
    eatt_device* eatt_dev = find_device_by_address(bda);
    auto num_of_sdu =
        stack_config_get_interface()->get_pts_l2cap_ecoc_send_num_of_sdu();
    log::info("device {}, num: {}", ADDRESS_TO_LOGGABLE_CSTR(eatt_dev->bda_),
              num_of_sdu);

    if (num_of_sdu <= 0) {
      return;
    }

    uint16_t mtu = 0;
    if (cid != 0) {
      auto chan = find_channel_by_cid(cid);
      mtu = chan->tx_mtu_;
    } else {
      for (const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el :
           eatt_dev->eatt_channels) {
        if (el.second->state_ == EattChannelState::EATT_CHANNEL_OPENED) {
          cid = el.first;
          mtu = el.second->tx_mtu_;
          break;
        }
      }
    }

    if (cid == 0 || mtu == 0) {
      log::error("There is no OPEN cid or MTU is 0");
      return;
    }

    for (int i = 0; i < num_of_sdu; i++) {
      BT_HDR* p_buf = (BT_HDR*)osi_malloc(mtu + sizeof(BT_HDR));
      p_buf->offset = L2CAP_MIN_OFFSET;
      p_buf->len = mtu;

      auto status = L2CA_DataWrite(cid, p_buf);
      log::info("Data num: {} sent with status {}", i,
                static_cast<int>(status));
    }
  }

  /* This is for the L2CAP ECoC Testing. */
  void upper_tester_delay_connect_cb(const RawAddress& bda) {
    log::info("device {}", ADDRESS_TO_LOGGABLE_CSTR(bda));
    eatt_device* eatt_dev = find_device_by_address(bda);
    if (eatt_dev == nullptr) {
      log::error("device is not available");
      return;
    }

    connect_eatt_wrap(eatt_dev);
  }

  void upper_tester_delay_connect(const RawAddress& bda, int timeout_ms) {
    bt_status_t status = do_in_main_thread_delayed(
        FROM_HERE,
        base::BindOnce(&eatt_impl::upper_tester_delay_connect_cb,
                       weak_factory_.GetWeakPtr(), bda),
        std::chrono::milliseconds(timeout_ms));

    log::info("Scheduled peripheral connect eatt for device with status: {}",
              (int)status);
  }

  void upper_tester_l2cap_connect_ind(const RawAddress& bda,
                                      std::vector<uint16_t>& lcids,
                                      uint16_t psm, uint16_t peer_mtu,
                                      uint8_t identifier) {
    /* This is just for L2CAP PTS test cases*/
    auto min_key_size =
        stack_config_get_interface()->get_pts_l2cap_ecoc_min_key_size();
    if (min_key_size > 0 && (min_key_size >= 7 && min_key_size <= 16)) {
      auto key_size = btm_ble_read_sec_key_size(bda);
      if (key_size < min_key_size) {
        std::vector<uint16_t> empty;
        log::error("Insufficient key size ({}<{}) for device {}", key_size,
                   min_key_size, ADDRESS_TO_LOGGABLE_CSTR(bda));
        L2CA_ConnectCreditBasedRsp(bda, identifier, empty,
                                   L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP_KEY_SIZE,
                                   nullptr);
        return;
      }
    }

    if (!eatt_l2cap_connect_ind_common(bda, lcids, psm, peer_mtu, identifier)) {
      log::debug("Reject L2CAP Connection request.");
      return;
    }

    /* Android let Central to create EATT (PTS initiates EATT). Some PTS test
     * cases wants Android to do it anyway (Android initiates EATT).
     */
    if (stack_config_get_interface()
            ->get_pts_eatt_peripheral_collision_support()) {
      upper_tester_delay_connect(bda, 500);
      return;
    }

    upper_tester_send_data_if_needed(bda);

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_reconfigure()) {
      bt_status_t status = do_in_main_thread_delayed(
          FROM_HERE,
          base::BindOnce(&eatt_impl::reconfigure_all,
                         weak_factory_.GetWeakPtr(), bda, 300),
          std::chrono::seconds(4));
      log::info("Scheduled ECOC reconfiguration with status: {}", (int)status);
    }
  }

  void eatt_l2cap_connect_ind(const RawAddress& bda,
                              std::vector<uint16_t>& lcids, uint16_t psm,
                              uint16_t peer_mtu, uint8_t identifier) {
    log::info("Device {}, num of cids: {}, psm 0x{:04x}, peer_mtu {}",
              ADDRESS_TO_LOGGABLE_CSTR(bda), static_cast<int>(lcids.size()),
              psm, peer_mtu);

    if (!stack_config_get_interface()
             ->get_pts_connect_eatt_before_encryption() &&
        !BTM_IsEncrypted(bda, BT_TRANSPORT_LE)) {
      /* If Link is not encrypted, we shall not accept EATT channel creation. */
      std::vector<uint16_t> empty;
      uint16_t result = L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION;
      if (BTM_IsLinkKeyKnown(bda, BT_TRANSPORT_LE)) {
        result = L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP;
      }
      log::error("ACL to device {} is unencrypted.",
                 ADDRESS_TO_LOGGABLE_CSTR(bda));
      L2CA_ConnectCreditBasedRsp(bda, identifier, empty, result, nullptr);
      return;
    }

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_upper_tester()) {
      log::info("Upper tester for the L2CAP ECoC enabled");
      return upper_tester_l2cap_connect_ind(bda, lcids, psm, peer_mtu,
                                            identifier);
    }

    eatt_l2cap_connect_ind_common(bda, lcids, psm, peer_mtu, identifier);
  }

  void eatt_retry_after_collision_if_needed(eatt_device* eatt_dev) {
    if (!eatt_dev->collision) {
      log::debug("No collision.");
      return;
    }
    /* We are here, because remote device wanted to create channels when
     * Android proceed its own EATT creation. How to handle it is described
     * here: BT Core 5.3, Volume 3, Part G, 5.4
     */
    log::info(
        "EATT collision detected. If we are Central we will retry right away");

    eatt_dev->collision = false;
    uint8_t role = L2CA_GetBleConnRole(eatt_dev->bda_);
    if (role == HCI_ROLE_CENTRAL) {
      log::info("Retrying EATT setup due to previous collision for device {}",
                ADDRESS_TO_LOGGABLE_CSTR(eatt_dev->bda_));
      connect_eatt_wrap(eatt_dev);
    } else if (stack_config_get_interface()
                   ->get_pts_eatt_peripheral_collision_support()) {
      /* This is only for the PTS. Android does not setup EATT when is a
       * peripheral.
       */
      upper_tester_delay_connect(eatt_dev->bda_, 500);
    }
  }

  /* This is for the L2CAP ECoC Testing. */
  void upper_tester_l2cap_connect_cfm(eatt_device* eatt_dev) {
    log::info("Upper tester for L2CAP Ecoc {}",
              ADDRESS_TO_LOGGABLE_CSTR(eatt_dev->bda_));
    if (is_channel_connection_pending(eatt_dev)) {
      log::info("Waiting for all channels to be connected");
      return;
    }

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_connect_remaining() &&
        (static_cast<int>(eatt_dev->eatt_channels.size()) <
         L2CAP_CREDIT_BASED_MAX_CIDS)) {
      log::info("Connecting remaining channels {}",
                L2CAP_CREDIT_BASED_MAX_CIDS -
                    static_cast<int>(eatt_dev->eatt_channels.size()));
      upper_tester_delay_connect(eatt_dev->bda_, 1000);
      return;
    }
    upper_tester_send_data_if_needed(eatt_dev->bda_);
  }

  void eatt_l2cap_connect_cfm(const RawAddress& bda, uint16_t lcid,
                              uint16_t peer_mtu, uint16_t result) {
    log::info("bda: {} cid: {}peer mtu: {} result {}",
              ADDRESS_TO_LOGGABLE_STR(bda), +lcid, +peer_mtu, +result);

    eatt_device* eatt_dev = find_device_by_address(bda);
    if (!eatt_dev) {
      log::error("unknown device");
      return;
    }

    EattChannel* channel = this->find_channel_by_cid(bda, lcid);
    if (!channel) {
      log::error("unknown cid: {}", loghex(lcid));
      return;
    }

    if (result != L2CAP_CONN_OK) {
      log::error("Could not connect CoC result: {}", loghex(result));
      remove_channel_by_cid(eatt_dev, lcid);

      /* If there is no channels connected, check if there was collision */
      if (!is_channel_connection_pending(eatt_dev)) {
        eatt_retry_after_collision_if_needed(eatt_dev);
      }
      return;
    }

    channel->EattChannelSetState(EattChannelState::EATT_CHANNEL_OPENED);
    channel->EattChannelSetTxMTU(peer_mtu);

    CHECK(eatt_dev->eatt_tcb_);
    CHECK(eatt_dev->bda_ == channel->bda_);
    eatt_dev->eatt_tcb_->eatt++;

    log::info("Channel connected CID 0x{:04x}", lcid);

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_upper_tester()) {
      upper_tester_l2cap_connect_cfm(eatt_dev);
    }
  }

  void eatt_l2cap_reconfig_completed(const RawAddress& bda, uint16_t lcid,
                                     bool is_local_cfg,
                                     tL2CAP_LE_CFG_INFO* p_cfg) {
    log::info("lcid: {} local cfg?: {}", loghex(lcid), is_local_cfg);

    EattChannel* channel = find_channel_by_cid(bda, lcid);
    if (!channel) return;

    // regardless of success result, we have finished reconfiguration
    channel->EattChannelSetState(EattChannelState::EATT_CHANNEL_OPENED);

    if (p_cfg->result != L2CAP_CFG_OK) {
      log::info("reconfig failed lcid: {} result: {}", loghex(lcid),
                loghex(p_cfg->result));
      return;
    }

    /* On this layer we don't care about mps as this is handled in L2CAP layer
     */
    if (is_local_cfg)
      channel->rx_mtu_ = p_cfg->mtu;
    else
      channel->EattChannelSetTxMTU(p_cfg->mtu);

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_reconfigure()) {
      /* Upper tester for L2CAP - schedule sending data */
      do_in_main_thread_delayed(
          FROM_HERE,
          base::BindOnce(&eatt_impl::upper_tester_send_data_if_needed,
                         weak_factory_.GetWeakPtr(), bda, lcid),
          std::chrono::seconds(1));
    }
  }

  void eatt_l2cap_collision_ind(const RawAddress& bda) {
    eatt_device* eatt_dev = find_device_by_address(bda);
    if (!eatt_dev) {
      log::error("Device {} not available anymore:",
                 ADDRESS_TO_LOGGABLE_CSTR(bda));
      return;
    }
    /* Remote wanted to setup channels as well. Let's retry remote's request
     * when we are done with ours.*/
    eatt_dev->collision = true;
  }

  void eatt_l2cap_error_cb(uint16_t lcid, uint16_t reason) {
    log::info("cid: {} reason {}", loghex(lcid), loghex(reason));

    /*TODO: provide address in the L2CAP callback */

    EattChannel* channel = find_channel_by_cid(lcid);
    if (!channel) {
      log::error("Unknown lcid");
      return;
    }

    eatt_device* eatt_dev = find_device_by_address(channel->bda_);

    switch (channel->state_) {
      case EattChannelState::EATT_CHANNEL_PENDING:
        log::error("Connecting failed");
        remove_channel_by_cid(eatt_dev, lcid);
        break;
      case EattChannelState::EATT_CHANNEL_RECONFIGURING:
        /* Just go back to open state */
        log::error("Reconfig failed");
        channel->EattChannelSetState(EattChannelState::EATT_CHANNEL_OPENED);
        break;
      default:
        log::error("Invalid state: {}", static_cast<uint8_t>(channel->state_));
        break;
    }

    if (!is_channel_connection_pending(eatt_dev)) {
      eatt_retry_after_collision_if_needed(eatt_dev);
    }
  }

  void eatt_l2cap_disconnect_ind(uint16_t lcid, bool please_confirm) {
    log::info("cid: {}", loghex(lcid));
    eatt_device* eatt_dev = find_device_by_cid(lcid);
    if (!eatt_dev) {
      log::error("unknown cid: {}", loghex(lcid));
      return;
    }

    eatt_dev->eatt_tcb_->eatt--;
    remove_channel_by_cid(eatt_dev, lcid);
  }

  void eatt_l2cap_data_ind(uint16_t lcid, BT_HDR* data_p) {
    log::info("cid: {}", loghex(lcid));
    eatt_device* eatt_dev = find_device_by_cid(lcid);
    if (!eatt_dev) {
      log::error("unknown cid: {}", loghex(lcid));
      return;
    }

    EattChannel* channel = find_channel_by_cid(eatt_dev->bda_, lcid);
    if (!channel) {
      log::error("Received data on closed channel {}", loghex(lcid));
      return;
    }

    gatt_data_process(*eatt_dev->eatt_tcb_, channel->cid_, data_p);
    osi_free(data_p);
  }

  bool is_eatt_supported_by_peer(const RawAddress& bd_addr) {
    return gatt_profile_get_eatt_support(bd_addr);
  }

  eatt_device* find_device_by_address(const RawAddress& bd_addr) {
    auto iter = find_if(
        devices_.begin(), devices_.end(),
        [&bd_addr](const eatt_device& ed) { return ed.bda_ == bd_addr; });

    return iter == devices_.end() ? nullptr : &(*iter);
  }

  eatt_device* add_eatt_device(const RawAddress& bd_addr) {
    devices_.push_back(eatt_device(bd_addr, default_mtu_, max_mps_));
    eatt_device* eatt_dev = &devices_.back();
    return eatt_dev;
  }

  void connect_eatt_wrap(eatt_device* eatt_dev) {
    if (stack_config_get_interface()
            ->get_pts_eatt_peripheral_collision_support()) {
      /* For PTS case, lets assume we support only 5 channels */
      log::info("Number of existing channels {}",
                (int)eatt_dev->eatt_channels.size());
      connect_eatt(eatt_dev, L2CAP_CREDIT_BASED_MAX_CIDS -
                                 (int)eatt_dev->eatt_channels.size());
      return;
    }

    connect_eatt(eatt_dev);
  }

  void connect_eatt(eatt_device* eatt_dev,
                    uint8_t num_of_channels = L2CAP_CREDIT_BASED_MAX_CIDS) {
    /* Let us use maximum possible mps */
    if (eatt_dev->rx_mps_ == EATT_MIN_MTU_MPS)
      eatt_dev->rx_mps_ = controller_get_interface()->get_acl_data_size_ble();

    tL2CAP_LE_CFG_INFO local_coc_cfg = {
        .mtu = eatt_dev->rx_mtu_,
        .mps = eatt_dev->rx_mps_,
        .credits = L2CA_LeCreditDefault(),
        .number_of_channels = num_of_channels,
    };

    log::info("Connecting device {}, cnt count {}",
              ADDRESS_TO_LOGGABLE_CSTR(eatt_dev->bda_), num_of_channels);

    /* Warning! CIDs in Android are unique across the ACL connections */
    std::vector<uint16_t> connecting_cids =
        L2CA_ConnectCreditBasedReq(psm_, eatt_dev->bda_, &local_coc_cfg);

    if (connecting_cids.size() == 0) {
      log::error("Unable to get cid");
      return;
    }

    log::info("Successfully sent CoC request, number of channel: {}",
              +connecting_cids.size());

    for (uint16_t cid : connecting_cids) {
      log::info(" \t cid: {}", loghex(cid));

      auto chan = std::make_shared<EattChannel>(eatt_dev->bda_, cid, 0,
                                                eatt_dev->rx_mtu_);
      eatt_dev->eatt_channels.insert({cid, chan});
    }

    if (eatt_dev->eatt_tcb_) {
      log::info("has tcb ? {}", eatt_dev->eatt_tcb_ == nullptr);
      return;
    }

    eatt_dev->eatt_tcb_ =
        gatt_find_tcb_by_addr(eatt_dev->bda_, BT_TRANSPORT_LE);
    CHECK(eatt_dev->eatt_tcb_);
  }

  EattChannel* find_eatt_channel_by_cid(const RawAddress& bd_addr,
                                        uint16_t cid) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return nullptr;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [&cid](const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          return el.first == cid;
        });

    return iter == eatt_dev->eatt_channels.end() ? nullptr : iter->second.get();
  }

  EattChannel* find_eatt_channel_by_transid(const RawAddress& bd_addr,
                                            uint32_t trans_id) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return nullptr;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [&trans_id](
            const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          return el.second->server_outstanding_cmd_.trans_id == trans_id;
        });

    return iter == eatt_dev->eatt_channels.end() ? nullptr : iter->second.get();
  }

  bool is_indication_pending(const RawAddress& bd_addr,
                             uint16_t indication_handle) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return false;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [&indication_handle](
            const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          return el.second->indicate_handle_ == indication_handle;
        });

    return (iter != eatt_dev->eatt_channels.end());
  };

  EattChannel* get_channel_available_for_indication(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [](const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          return el.second->state_ == EattChannelState::EATT_CHANNEL_OPENED &&
                 !GATT_HANDLE_IS_VALID(el.second->indicate_handle_);
        });

    return (iter == eatt_dev->eatt_channels.end()) ? nullptr
                                                   : iter->second.get();
  };

  EattChannel* get_channel_available_for_client_request(
      const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return nullptr;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [](const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          return el.second->state_ == EattChannelState::EATT_CHANNEL_OPENED &&
                 el.second->cl_cmd_q_.empty();
        });

    return (iter == eatt_dev->eatt_channels.end()) ? nullptr
                                                   : iter->second.get();
  }

  void free_gatt_resources(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return;

    auto iter = eatt_dev->eatt_channels.begin();
    while (iter != eatt_dev->eatt_channels.end()) {
      EattChannel* channel = iter->second.get();

      fixed_queue_free(channel->server_outstanding_cmd_.multi_rsp_q, NULL);
      channel->server_outstanding_cmd_.multi_rsp_q = NULL;
      iter++;
    }
  }

  bool is_outstanding_msg_in_send_queue(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return false;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [](const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          if (el.second->cl_cmd_q_.empty()) return false;

          tGATT_CMD_Q& cmd = el.second->cl_cmd_q_.front();
          return cmd.to_send;
        });
    return (iter != eatt_dev->eatt_channels.end());
  }

  EattChannel* get_channel_with_queued_data(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) return nullptr;

    auto iter = find_if(
        eatt_dev->eatt_channels.begin(), eatt_dev->eatt_channels.end(),
        [](const std::pair<uint16_t, std::shared_ptr<EattChannel>>& el) {
          if (el.second->cl_cmd_q_.empty()) return false;

          tGATT_CMD_Q& cmd = el.second->cl_cmd_q_.front();
          return cmd.to_send;
        });
    return (iter == eatt_dev->eatt_channels.end()) ? nullptr
                                                   : iter->second.get();
  }

  static void eatt_ind_ack_timeout(void* data) {
    EattChannel* channel = (EattChannel*)data;
    tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(channel->bda_, BT_TRANSPORT_LE);

    log::warn("send ack now");
    attp_send_cl_confirmation_msg(*p_tcb, channel->cid_);
  }

  static void eatt_ind_confirmation_timeout(void* data) {
    EattChannel* channel = (EattChannel*)data;
    tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(channel->bda_, BT_TRANSPORT_LE);

    log::warn("disconnecting...");
    gatt_disconnect(p_tcb);
  }

  void start_indication_confirm_timer(const RawAddress& bd_addr, uint16_t cid) {
    EattChannel* channel = find_eatt_channel_by_cid(bd_addr, cid);
    if (!channel) {
      log::error("Unknown cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    alarm_set_on_mloop(channel->ind_confirmation_timer_,
                       GATT_WAIT_FOR_RSP_TIMEOUT_MS,
                       eatt_ind_confirmation_timeout, channel);
  }

  void stop_indication_confirm_timer(const RawAddress& bd_addr, uint16_t cid) {
    EattChannel* channel = find_eatt_channel_by_cid(bd_addr, cid);
    if (!channel) {
      log::error("Unknown cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    alarm_cancel(channel->ind_confirmation_timer_);
  }

  void start_app_indication_timer(const RawAddress& bd_addr, uint16_t cid) {
    EattChannel* channel = find_eatt_channel_by_cid(bd_addr, cid);
    if (!channel) {
      log::error("Unknown cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    alarm_set_on_mloop(channel->ind_ack_timer_, GATT_WAIT_FOR_RSP_TIMEOUT_MS,
                       eatt_ind_ack_timeout, channel);
  }

  void stop_app_indication_timer(const RawAddress& bd_addr, uint16_t cid) {
    EattChannel* channel = find_eatt_channel_by_cid(bd_addr, cid);
    if (!channel) {
      log::error("Unknown cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    alarm_cancel(channel->ind_ack_timer_);
  }

  void reconfigure(const RawAddress& bd_addr, uint16_t cid, uint16_t new_mtu) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) {
      log::error("Unknown device {}", ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    EattChannel* channel = find_eatt_channel_by_cid(bd_addr, cid);
    if (!channel) {
      log::error("Unknown cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    if (new_mtu <= channel->rx_mtu_) {
      log::error("Invalid mtu: {}", loghex(new_mtu));
      return;
    }

    std::vector<uint16_t> cids = {cid};

    tL2CAP_LE_CFG_INFO cfg = {.mtu = new_mtu, .mps = eatt_dev->rx_mps_};

    if (!L2CA_ReconfigCreditBasedConnsReq(eatt_dev->bda_, cids, &cfg)) {
      log::error("Could not start reconfig cid: {} or device {}", loghex(cid),
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    channel->EattChannelSetState(EattChannelState::EATT_CHANNEL_RECONFIGURING);
  }

  void reconfigure_all(const RawAddress& bd_addr, uint16_t new_mtu) {
    log::info("Device {}, new mtu {}", ADDRESS_TO_LOGGABLE_STR(bd_addr),
              new_mtu);
    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) {
      log::error("Unknown device {}", ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    uint8_t num_of_channels = eatt_dev->eatt_channels.size();
    if (num_of_channels == 0) {
      log::error("No channels for device {}", ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    std::vector<uint16_t> cids;

    auto iter = eatt_dev->eatt_channels.begin();
    while (iter != eatt_dev->eatt_channels.end()) {
      uint16_t cid = iter->first;
      cids.push_back(cid);
      iter++;
    }

    if (new_mtu <= EATT_MIN_MTU_MPS) {
      log::error("Invalid mtu: {}", loghex(new_mtu));
      return;
    }

    tL2CAP_LE_CFG_INFO cfg = {.mtu = new_mtu, .mps = eatt_dev->rx_mps_};

    if (!L2CA_ReconfigCreditBasedConnsReq(eatt_dev->bda_, cids, &cfg)) {
      log::error("Could not start reconfig for device {}",
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    for (auto& channel : eatt_dev->eatt_channels) {
      channel.second->EattChannelSetState(
          EattChannelState::EATT_CHANNEL_RECONFIGURING);
    }
  }

  void supported_features_cb(uint8_t role, const RawAddress& bd_addr,
                             uint8_t features) {
    bool is_eatt_supported = features & BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK;

    log::info("{} is_eatt_supported = {}", ADDRESS_TO_LOGGABLE_STR(bd_addr),
              int(is_eatt_supported));
    if (!is_eatt_supported) return;

    eatt_device* eatt_dev = this->find_device_by_address(bd_addr);
    if (!eatt_dev) {
      log::info("Adding device: {} on supported features callback.",
                ADDRESS_TO_LOGGABLE_STR(bd_addr));
      eatt_dev = add_eatt_device(bd_addr);
    }

    if (role != HCI_ROLE_CENTRAL) {
      /* TODO For now do nothing, we could run a timer here and start EATT if
       * not started by central */
      log::info(" EATT Should be connected by the central. Let's wait for it.");
      return;
    }

    connect_eatt_wrap(eatt_dev);
  }

  void disconnect_channel(uint16_t cid) { L2CA_DisconnectReq(cid); }

  void disconnect(const RawAddress& bd_addr, uint16_t cid) {
    log::info("Device: {}, cid: 0x{:04x}", ADDRESS_TO_LOGGABLE_STR(bd_addr),
              cid);

    eatt_device* eatt_dev = find_device_by_address(bd_addr);
    if (!eatt_dev) {
      log::warn("no eatt device found");
      return;
    }

    if (!eatt_dev->eatt_tcb_) {
      LOG_ASSERT(eatt_dev->eatt_channels.size() == 0);
      log::warn("no eatt channels found");
      return;
    }

    if (cid != EATT_ALL_CIDS) {
      auto chan = find_channel_by_cid(cid);
      if (!chan) {
        log::warn("Cid {} not found for device {}", cid,
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        return;
      }
      log::info("Disconnecting cid {}", cid);
      disconnect_channel(cid);
      remove_channel_by_cid(cid);
      return;
    }

    auto iter = eatt_dev->eatt_channels.begin();
    while (iter != eatt_dev->eatt_channels.end()) {
      uint16_t cid = iter->first;
      disconnect_channel(cid);
      /* When initiating disconnection, stack will not notify us that it is
       * done. We need to assume success
       */
      iter = eatt_dev->eatt_channels.erase(iter);
    }
    eatt_dev->eatt_tcb_->eatt = 0;
    eatt_dev->eatt_tcb_ = nullptr;
    eatt_dev->collision = false;
  }

  void upper_tester_connect(const RawAddress& bd_addr, eatt_device* eatt_dev,
                            uint8_t role) {
    log::info(
        "L2CAP Upper tester enabled, {} ({}), role: {}({})",
        ADDRESS_TO_LOGGABLE_STR(bd_addr), fmt::ptr(eatt_dev),
        role == HCI_ROLE_CENTRAL ? "HCI_ROLE_CENTRAL" : "HCI_ROLE_PERIPHERAL",
        role);

    auto num_of_chan =
        stack_config_get_interface()->get_pts_l2cap_ecoc_initial_chan_cnt();
    if (num_of_chan <= 0) {
      num_of_chan = L2CAP_CREDIT_BASED_MAX_CIDS;
    }

    /* This is needed for L2CAP test cases */
    if (stack_config_get_interface()->get_pts_connect_eatt_unconditionally()) {
      /* Normally eatt_dev exist only if EATT is supported by remote device.
       * Here it is created unconditionally */
      if (eatt_dev == nullptr) eatt_dev = add_eatt_device(bd_addr);
      /* For PTS just start connecting EATT right away */
      connect_eatt(eatt_dev, num_of_chan);
      return;
    }

    if (eatt_dev != nullptr && role == HCI_ROLE_CENTRAL) {
      connect_eatt(eatt_dev, num_of_chan);
      return;
    }

    /* If we don't know yet, read GATT server supported features. */
    if (gatt_cl_read_sr_supp_feat_req(
            bd_addr, base::BindOnce(&eatt_impl::supported_features_cb,
                                    weak_factory_.GetWeakPtr(), role)) ==
        false) {
      log::info("Read server supported features failed for device {}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }

  void connect(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);

    uint8_t role = L2CA_GetBleConnRole(bd_addr);
    if (role == HCI_ROLE_UNKNOWN) {
      log::error("Could not get device role{}",
                 ADDRESS_TO_LOGGABLE_STR(bd_addr));
      return;
    }

    if (stack_config_get_interface()->get_pts_l2cap_ecoc_upper_tester()) {
      upper_tester_connect(bd_addr, eatt_dev, role);
      return;
    }

    log::info("Device {}, role {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
              (role == HCI_ROLE_CENTRAL ? "central" : "peripheral"));

    if (eatt_dev) {
      /* We are reconnecting device we know that support EATT.
       * Just connect CoC
       */
      log::info("Known device, connect eCoC");

      if (role != HCI_ROLE_CENTRAL) {
        log::info(
            " EATT Should be connected by the central. Let's wait for it.");
        return;
      }

      connect_eatt_wrap(eatt_dev);
      return;
    }

    if (role != HCI_ROLE_CENTRAL) return;

    if (gatt_profile_get_eatt_support(bd_addr)) {
      log::debug("Eatt is supported for device {}",
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      supported_features_cb(role, bd_addr, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
      return;
    }

    /* If we don't know yet, read GATT server supported features. */
    if (gatt_cl_read_sr_supp_feat_req(
            bd_addr, base::BindOnce(&eatt_impl::supported_features_cb,
                                    weak_factory_.GetWeakPtr(), role)) ==
        false) {
      log::info("Read server supported features failed for device {}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }

  void add_from_storage(const RawAddress& bd_addr) {
    eatt_device* eatt_dev = find_device_by_address(bd_addr);

    log::info("restoring: {}", ADDRESS_TO_LOGGABLE_STR(bd_addr));

    if (!eatt_dev) add_eatt_device(bd_addr);
  }
};

}  // namespace eatt
}  // namespace bluetooth
