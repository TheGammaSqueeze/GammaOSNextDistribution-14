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

#include <list>
#include <map>
#include <memory>
#include <mutex>

#include "base/functional/bind.h"
#include "base/functional/callback.h"
#include "base/logging.h"
#include "btm_dev.h"
#include "btm_iso_api.h"
#include "common/time_util.h"
#include "device/include/controller.h"
#include "hci/include/hci_layer.h"
#include "internal_include/bt_trace.h"
#include "internal_include/stack_config.h"
#include "main/shim/hci_layer.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcidefs.h"
#include "stack/include/hcimsgs.h"

namespace bluetooth {
namespace hci {
namespace iso_manager {
static constexpr uint8_t kIsoHeaderWithTsLen = 12;
static constexpr uint8_t kIsoHeaderWithoutTsLen = 8;

static constexpr uint8_t kStateFlagsNone = 0x00;
static constexpr uint8_t kStateFlagIsConnecting = 0x01;
static constexpr uint8_t kStateFlagIsConnected = 0x02;
static constexpr uint8_t kStateFlagHasDataPathSet = 0x04;
static constexpr uint8_t kStateFlagIsBroadcast = 0x10;

constexpr char kBtmLogTag[] = "ISO";

struct iso_sync_info {
  uint16_t seq_nb;
};

struct iso_base {
  union {
    uint8_t cig_id;
    uint8_t big_handle;
  };

  struct iso_sync_info sync_info;
  std::atomic_uint8_t state_flags;
  uint32_t sdu_itv;
  std::atomic_uint16_t used_credits;

  struct credits_stats {
    size_t credits_underflow_bytes = 0;
    size_t credits_underflow_count = 0;
    uint64_t credits_last_underflow_us = 0;
  };

  struct event_stats {
    size_t evt_lost_count = 0;
    size_t seq_nb_mismatch_count = 0;
    uint64_t evt_last_lost_us = 0;
  };

  credits_stats cr_stats;
  event_stats evt_stats;
};

typedef iso_base iso_cis;
typedef iso_base iso_bis;

struct iso_impl {
  iso_impl() {
    iso_credits_ = controller_get_interface()->get_iso_buffer_count();
    iso_buffer_size_ = controller_get_interface()->get_iso_data_size();
    log::info("{} created, iso credits: {}, buffer size: {}.", fmt::ptr(this),
              iso_credits_.load(), iso_buffer_size_);
  }

  ~iso_impl() { log::info("{} removed.", fmt::ptr(this)); }

  void handle_register_cis_callbacks(CigCallbacks* callbacks) {
    LOG_ASSERT(callbacks != nullptr) << "Invalid CIG callbacks";
    cig_callbacks_ = callbacks;
  }

  void handle_register_big_callbacks(BigCallbacks* callbacks) {
    LOG_ASSERT(callbacks != nullptr) << "Invalid BIG callbacks";
    big_callbacks_ = callbacks;
  }

  void handle_register_on_iso_traffic_active_callback(void callback(bool)) {
    LOG_ASSERT(callback != nullptr) << "Invalid OnIsoTrafficActive callback";
    const std::lock_guard<std::mutex> lock(
        on_iso_traffic_active_callbacks_list_mutex_);
    on_iso_traffic_active_callbacks_list_.push_back(callback);
  }

  void on_set_cig_params(uint8_t cig_id, uint32_t sdu_itv_mtos, uint8_t* stream,
                         uint16_t len) {
    uint8_t cis_cnt;
    uint16_t conn_handle;
    cig_create_cmpl_evt evt;

    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";
    LOG_ASSERT(len >= 3) << "Invalid packet length: " << +len;

    STREAM_TO_UINT8(evt.status, stream);
    STREAM_TO_UINT8(evt.cig_id, stream);
    STREAM_TO_UINT8(cis_cnt, stream);

    uint8_t evt_code = IsCigKnown(cig_id) ? kIsoEventCigOnReconfigureCmpl
                                          : kIsoEventCigOnCreateCmpl;

    BTM_LogHistory(
        kBtmLogTag, RawAddress::kEmpty, "CIG Create complete",
        base::StringPrintf(
            "cig_id:0x%02x, status: %s", evt.cig_id,
            hci_status_code_text((tHCI_STATUS)(evt.status)).c_str()));

    if (evt.status == HCI_SUCCESS) {
      LOG_ASSERT(len >= (3) + (cis_cnt * sizeof(uint16_t)))
          << "Invalid CIS count: " << +cis_cnt;

      /* Remove entries for the reconfigured CIG */
      if (evt_code == kIsoEventCigOnReconfigureCmpl) {
        auto cis_it = conn_hdl_to_cis_map_.cbegin();
        while (cis_it != conn_hdl_to_cis_map_.cend()) {
          if (cis_it->second->cig_id == evt.cig_id)
            cis_it = conn_hdl_to_cis_map_.erase(cis_it);
          else
            ++cis_it;
        }
      }

      evt.conn_handles.reserve(cis_cnt);
      for (int i = 0; i < cis_cnt; i++) {
        STREAM_TO_UINT16(conn_handle, stream);

        evt.conn_handles.push_back(conn_handle);

        auto cis = std::unique_ptr<iso_cis>(new iso_cis());
        cis->cig_id = cig_id;
        cis->sdu_itv = sdu_itv_mtos;
        cis->sync_info = {.seq_nb = 0};
        cis->used_credits = 0;
        cis->state_flags = kStateFlagsNone;
        conn_hdl_to_cis_map_[conn_handle] = std::move(cis);
      }
    }

    cig_callbacks_->OnCigEvent(evt_code, &evt);

    if (evt_code == kIsoEventCigOnCreateCmpl) {
      const std::lock_guard<std::mutex> lock(
          on_iso_traffic_active_callbacks_list_mutex_);
      for (auto callback : on_iso_traffic_active_callbacks_list_) {
        callback(true);
      }
    }
  }

  void create_cig(uint8_t cig_id,
                  struct iso_manager::cig_create_params cig_params) {
    LOG_ASSERT(!IsCigKnown(cig_id))
        << "Invalid cig - already exists: " << +cig_id;

    btsnd_hcic_set_cig_params(
        cig_id, cig_params.sdu_itv_mtos, cig_params.sdu_itv_stom,
        cig_params.sca, cig_params.packing, cig_params.framing,
        cig_params.max_trans_lat_stom, cig_params.max_trans_lat_mtos,
        cig_params.cis_cfgs.size(), cig_params.cis_cfgs.data(),
        base::BindOnce(&iso_impl::on_set_cig_params, weak_factory_.GetWeakPtr(),
                       cig_id, cig_params.sdu_itv_mtos));

    BTM_LogHistory(
        kBtmLogTag, RawAddress::kEmpty, "CIG Create",
        base::StringPrintf("cig_id:0x%02x, size: %d", cig_id,
                           static_cast<int>(cig_params.cis_cfgs.size())));
  }

  void reconfigure_cig(uint8_t cig_id,
                       struct iso_manager::cig_create_params cig_params) {
    LOG_ASSERT(IsCigKnown(cig_id)) << "No such cig: " << +cig_id;

    btsnd_hcic_set_cig_params(
        cig_id, cig_params.sdu_itv_mtos, cig_params.sdu_itv_stom,
        cig_params.sca, cig_params.packing, cig_params.framing,
        cig_params.max_trans_lat_stom, cig_params.max_trans_lat_mtos,
        cig_params.cis_cfgs.size(), cig_params.cis_cfgs.data(),
        base::BindOnce(&iso_impl::on_set_cig_params, weak_factory_.GetWeakPtr(),
                       cig_id, cig_params.sdu_itv_mtos));
  }

  void on_remove_cig(uint8_t* stream, uint16_t len) {
    cig_remove_cmpl_evt evt;

    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";
    LOG_ASSERT(len == 2) << "Invalid packet length: " << +len;

    STREAM_TO_UINT8(evt.status, stream);
    STREAM_TO_UINT8(evt.cig_id, stream);

    BTM_LogHistory(
        kBtmLogTag, RawAddress::kEmpty, "CIG Remove complete",
        base::StringPrintf(
            "cig_id:0x%02x, status: %s", evt.cig_id,
            hci_status_code_text((tHCI_STATUS)(evt.status)).c_str()));

    if (evt.status == HCI_SUCCESS) {
      auto cis_it = conn_hdl_to_cis_map_.cbegin();
      while (cis_it != conn_hdl_to_cis_map_.cend()) {
        if (cis_it->second->cig_id == evt.cig_id)
          cis_it = conn_hdl_to_cis_map_.erase(cis_it);
        else
          ++cis_it;
      }
    }

    cig_callbacks_->OnCigEvent(kIsoEventCigOnRemoveCmpl, &evt);

    {
      const std::lock_guard<std::mutex> lock(
          on_iso_traffic_active_callbacks_list_mutex_);
      for (auto callback : on_iso_traffic_active_callbacks_list_) {
        callback(false);
      }
    }
  }

  void remove_cig(uint8_t cig_id, bool force) {
    if (!force) {
      LOG_ASSERT(IsCigKnown(cig_id)) << "No such cig: " << +cig_id;
    } else {
      log::warn("Forcing to remove CIG {}", cig_id);
    }

    btsnd_hcic_remove_cig(cig_id, base::BindOnce(&iso_impl::on_remove_cig,
                                                 weak_factory_.GetWeakPtr()));
    BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "CIG Remove",
                   base::StringPrintf("cig_id:0x%02x (f:%d)", cig_id, force));
  }

  void on_status_establish_cis(
      struct iso_manager::cis_establish_params conn_params, uint8_t* stream,
      uint16_t len) {
    uint8_t status;

    LOG_ASSERT(len == 2) << "Invalid packet length: " << +len;

    STREAM_TO_UINT16(status, stream);

    for (auto cis_param : conn_params.conn_pairs) {
      cis_establish_cmpl_evt evt;

      if (status != HCI_SUCCESS) {
        auto cis = GetCisIfKnown(cis_param.cis_conn_handle);
        LOG_ASSERT(cis != nullptr)
            << "No such cis: " << +cis_param.cis_conn_handle;

        evt.status = status;
        evt.cis_conn_hdl = cis_param.cis_conn_handle;
        evt.cig_id = 0xFF;
        cis->state_flags &= ~kStateFlagIsConnecting;
        cig_callbacks_->OnCisEvent(kIsoEventCisEstablishCmpl, &evt);

        BTM_LogHistory(
            kBtmLogTag, cis_hdl_to_addr[evt.cis_conn_hdl],
            "Establish CIS failed ",
            base::StringPrintf(
                "handle:0x%04x, status: %s", evt.cis_conn_hdl,
                hci_status_code_text((tHCI_STATUS)(status)).c_str()));
        cis_hdl_to_addr.erase(evt.cis_conn_hdl);
      }
    }
  }

  void establish_cis(struct iso_manager::cis_establish_params conn_params) {
    for (auto& el : conn_params.conn_pairs) {
      auto cis = GetCisIfKnown(el.cis_conn_handle);
      LOG_ASSERT(cis) << "No such cis: " << +el.cis_conn_handle;
      LOG_ASSERT(!(cis->state_flags &
                   (kStateFlagIsConnected | kStateFlagIsConnecting)))
          << "cis: " << +el.cis_conn_handle
          << " is already connected or connecting flags: " << +cis->state_flags
          << ", num of cis params: " << +conn_params.conn_pairs.size();

      cis->state_flags |= kStateFlagIsConnecting;

      tBTM_SEC_DEV_REC* p_rec = btm_find_dev_by_handle(el.acl_conn_handle);
      if (p_rec) {
        cis_hdl_to_addr[el.cis_conn_handle] = p_rec->ble.pseudo_addr;
        BTM_LogHistory(kBtmLogTag, p_rec->ble.pseudo_addr, "Establish CIS",
                       base::StringPrintf("handle:0x%04x", el.acl_conn_handle));
      }
    }
    btsnd_hcic_create_cis(
        conn_params.conn_pairs.size(), conn_params.conn_pairs.data(),
        base::BindOnce(&iso_impl::on_status_establish_cis,
                       weak_factory_.GetWeakPtr(), conn_params));
  }

  void disconnect_cis(uint16_t cis_handle, uint8_t reason) {
    auto cis = GetCisIfKnown(cis_handle);
    LOG_ASSERT(cis) << "No such cis: " << +cis_handle;
    LOG_ASSERT(cis->state_flags & kStateFlagIsConnected ||
               cis->state_flags & kStateFlagIsConnecting)
        << "Not connected";
    bluetooth::legacy::hci::GetInterface().Disconnect(
        cis_handle, static_cast<tHCI_STATUS>(reason));

    BTM_LogHistory(kBtmLogTag, cis_hdl_to_addr[cis_handle], "Disconnect CIS ",
                   base::StringPrintf(
                       "handle:0x%04x, reason:%s", cis_handle,
                       hci_reason_code_text((tHCI_REASON)(reason)).c_str()));
  }

  void on_setup_iso_data_path(uint8_t* stream, uint16_t len) {
    uint8_t status;
    uint16_t conn_handle;

    STREAM_TO_UINT8(status, stream);
    STREAM_TO_UINT16(conn_handle, stream);

    iso_base* iso = GetIsoIfKnown(conn_handle);
    if (iso == nullptr) {
      /* That can happen when ACL has been disconnected while ISO patch was
       * creating */
      log::warn("Invalid connection handle: {}", +conn_handle);
      return;
    }

    BTM_LogHistory(kBtmLogTag, cis_hdl_to_addr[conn_handle],
                   "Setup data path complete",
                   base::StringPrintf(
                       "handle:0x%04x, status:%s", conn_handle,
                       hci_status_code_text((tHCI_STATUS)(status)).c_str()));

    if (status == HCI_SUCCESS) iso->state_flags |= kStateFlagHasDataPathSet;
    if (iso->state_flags & kStateFlagIsBroadcast) {
      LOG_ASSERT(big_callbacks_ != nullptr) << "Invalid BIG callbacks";
      big_callbacks_->OnSetupIsoDataPath(status, conn_handle, iso->big_handle);
    } else {
      LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";
      cig_callbacks_->OnSetupIsoDataPath(status, conn_handle, iso->cig_id);
    }
  }

  void setup_iso_data_path(
      uint16_t conn_handle,
      struct iso_manager::iso_data_path_params path_params) {
    iso_base* iso = GetIsoIfKnown(conn_handle);
    LOG_ASSERT(iso != nullptr) << "No such iso connection: " << +conn_handle;

    if (!(iso->state_flags & kStateFlagIsBroadcast)) {
      LOG_ASSERT(iso->state_flags & kStateFlagIsConnected)
          << "CIS not established";
    }

    btsnd_hcic_setup_iso_data_path(
        conn_handle, path_params.data_path_dir, path_params.data_path_id,
        path_params.codec_id_format, path_params.codec_id_company,
        path_params.codec_id_vendor, path_params.controller_delay,
        std::move(path_params.codec_conf),
        base::BindOnce(&iso_impl::on_setup_iso_data_path,
                       weak_factory_.GetWeakPtr()));
    BTM_LogHistory(
        kBtmLogTag, cis_hdl_to_addr[conn_handle], "Setup data path",
        base::StringPrintf(
            "handle:0x%04x, dir:0x%02x, path_id:0x%02x, codec_id:0x%02x",
            conn_handle, path_params.data_path_dir, path_params.data_path_id,
            path_params.codec_id_format));
  }

  void on_remove_iso_data_path(uint8_t* stream, uint16_t len) {
    uint8_t status;
    uint16_t conn_handle;

    if (len < 3) {
      log::warn("Malformatted packet received");
      return;
    }
    STREAM_TO_UINT8(status, stream);
    STREAM_TO_UINT16(conn_handle, stream);

    iso_base* iso = GetIsoIfKnown(conn_handle);
    if (iso == nullptr) {
      /* That could happen when ACL has been disconnected while removing data
       * path */
      log::warn("Invalid connection handle: {}", +conn_handle);
      return;
    }

    BTM_LogHistory(kBtmLogTag, cis_hdl_to_addr[conn_handle],
                   "Remove data path complete",
                   base::StringPrintf(
                       "handle:0x%04x, status:%s", conn_handle,
                       hci_status_code_text((tHCI_STATUS)(status)).c_str()));

    if (status == HCI_SUCCESS) iso->state_flags &= ~kStateFlagHasDataPathSet;

    if (iso->state_flags & kStateFlagIsBroadcast) {
      LOG_ASSERT(big_callbacks_ != nullptr) << "Invalid BIG callbacks";
      big_callbacks_->OnRemoveIsoDataPath(status, conn_handle, iso->big_handle);
    } else {
      LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";
      cig_callbacks_->OnRemoveIsoDataPath(status, conn_handle, iso->cig_id);
    }
  }

  void remove_iso_data_path(uint16_t iso_handle, uint8_t data_path_dir) {
    iso_base* iso = GetIsoIfKnown(iso_handle);
    LOG_ASSERT(iso != nullptr) << "No such iso connection: " << loghex(iso_handle);
    LOG_ASSERT((iso->state_flags & kStateFlagHasDataPathSet) ==
               kStateFlagHasDataPathSet)
        << "Data path not set";

    btsnd_hcic_remove_iso_data_path(
        iso_handle, data_path_dir,
        base::BindOnce(&iso_impl::on_remove_iso_data_path,
                       weak_factory_.GetWeakPtr()));

    BTM_LogHistory(kBtmLogTag, cis_hdl_to_addr[iso_handle], "Remove data path",
                   base::StringPrintf("handle:0x%04x, dir:0x%02x", iso_handle,
                                      data_path_dir));
  }

  void on_iso_link_quality_read(uint8_t* stream, uint16_t len) {
    uint8_t status;
    uint16_t conn_handle;
    uint32_t txUnackedPackets;
    uint32_t txFlushedPackets;
    uint32_t txLastSubeventPackets;
    uint32_t retransmittedPackets;
    uint32_t crcErrorPackets;
    uint32_t rxUnreceivedPackets;
    uint32_t duplicatePackets;

    // 1 + 2 + 4 * 7
#define ISO_LINK_QUALITY_SIZE 31
    if (len < ISO_LINK_QUALITY_SIZE) {
      log::error("Malformated link quality format, len={}", len);
      return;
    }

    STREAM_TO_UINT8(status, stream);
    if (status != HCI_SUCCESS) {
      log::error("Failed to Read ISO Link Quality, status: {}", loghex(status));
      return;
    }

    STREAM_TO_UINT16(conn_handle, stream);

    iso_base* iso = GetIsoIfKnown(conn_handle);
    if (iso == nullptr) {
      /* That could happen when ACL has been disconnected while waiting on the
       * read respose */
      log::warn("Invalid connection handle: {}", +conn_handle);
      return;
    }

    STREAM_TO_UINT32(txUnackedPackets, stream);
    STREAM_TO_UINT32(txFlushedPackets, stream);
    STREAM_TO_UINT32(txLastSubeventPackets, stream);
    STREAM_TO_UINT32(retransmittedPackets, stream);
    STREAM_TO_UINT32(crcErrorPackets, stream);
    STREAM_TO_UINT32(rxUnreceivedPackets, stream);
    STREAM_TO_UINT32(duplicatePackets, stream);

    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";
    cig_callbacks_->OnIsoLinkQualityRead(
        conn_handle, iso->cig_id, txUnackedPackets, txFlushedPackets,
        txLastSubeventPackets, retransmittedPackets, crcErrorPackets,
        rxUnreceivedPackets, duplicatePackets);
  }

  void read_iso_link_quality(uint16_t iso_handle) {
    iso_base* iso = GetIsoIfKnown(iso_handle);
    if (iso == nullptr) {
      log::error("No such iso connection: {}", loghex(iso_handle));
      return;
    }

    btsnd_hcic_read_iso_link_quality(
        iso_handle, base::BindOnce(&iso_impl::on_iso_link_quality_read,
                                   weak_factory_.GetWeakPtr()));
  }

  BT_HDR* prepare_hci_packet(uint16_t iso_handle, uint16_t seq_nb,
                             uint16_t data_len) {
    /* Add 2 for packet seq., 2 for length */
    uint16_t iso_data_load_len = data_len + 4;

    /* Add 2 for handle, 2 for length */
    uint16_t iso_full_len = iso_data_load_len + 4;
    BT_HDR* packet = (BT_HDR*)osi_malloc(iso_full_len + sizeof(BT_HDR));
    packet->len = iso_full_len;
    packet->offset = 0;
    packet->event = MSG_STACK_TO_HC_HCI_ISO;
    packet->layer_specific = 0;

    uint8_t* packet_data = packet->data;
    UINT16_TO_STREAM(packet_data, iso_handle);
    UINT16_TO_STREAM(packet_data, iso_data_load_len);

    UINT16_TO_STREAM(packet_data, seq_nb);
    UINT16_TO_STREAM(packet_data, data_len);

    return packet;
  }

  void send_iso_data(uint16_t iso_handle, const uint8_t* data,
                     uint16_t data_len) {
    iso_base* iso = GetIsoIfKnown(iso_handle);
    LOG_ASSERT(iso != nullptr)
        << "No such iso connection handle: " << loghex(iso_handle);

    if (!(iso->state_flags & kStateFlagIsBroadcast)) {
      if (!(iso->state_flags & kStateFlagIsConnected)) {
        log::warn("Cis handle: {} not established", loghex(iso_handle));
        return;
      }
    }

    if (!(iso->state_flags & kStateFlagHasDataPathSet)) {
      log::warn("Data path not set for handle: 0x{:04x}", iso_handle);
      return;
    }

    /* Calculate sequence number for the ISO data packet.
     * It should be incremented by 1 every SDU Interval.
     */
    uint16_t seq_nb = iso->sync_info.seq_nb;
    iso->sync_info.seq_nb = (seq_nb + 1) & 0xffff;

    if (iso_credits_ == 0 || data_len > iso_buffer_size_) {
      iso->cr_stats.credits_underflow_bytes += data_len;
      iso->cr_stats.credits_underflow_count++;
      iso->cr_stats.credits_last_underflow_us =
          bluetooth::common::time_get_os_boottime_us();

      log::warn(
          ", dropping ISO packet, len: {}, iso credits: {}, iso handle: {}",
          static_cast<int>(data_len), static_cast<int>(iso_credits_),
          loghex(iso_handle));
      return;
    }

    iso_credits_--;
    iso->used_credits++;

    BT_HDR* packet = prepare_hci_packet(iso_handle, seq_nb, data_len);
    memcpy(packet->data + kIsoHeaderWithoutTsLen, data, data_len);
    auto hci = bluetooth::shim::hci_layer_get_interface();
    packet->event = MSG_STACK_TO_HC_HCI_ISO | 0x0001;
    hci->transmit_downward(packet, iso_buffer_size_);
  }

  void process_cis_est_pkt(uint8_t len, uint8_t* data) {
    cis_establish_cmpl_evt evt;

    LOG_ASSERT(len == 28) << "Invalid packet length: " << +len;
    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";

    STREAM_TO_UINT8(evt.status, data);
    STREAM_TO_UINT16(evt.cis_conn_hdl, data);

    auto cis = GetCisIfKnown(evt.cis_conn_hdl);
    LOG_ASSERT(cis != nullptr) << "No such cis: " << +evt.cis_conn_hdl;

    BTM_LogHistory(kBtmLogTag, cis_hdl_to_addr[evt.cis_conn_hdl],
                   "CIS established event",
                   base::StringPrintf(
                       "cis_handle:0x%04x status:%s", evt.cis_conn_hdl,
                       hci_error_code_text((tHCI_STATUS)(evt.status)).c_str()));

    STREAM_TO_UINT24(evt.cig_sync_delay, data);
    STREAM_TO_UINT24(evt.cis_sync_delay, data);
    STREAM_TO_UINT24(evt.trans_lat_mtos, data);
    STREAM_TO_UINT24(evt.trans_lat_stom, data);
    STREAM_TO_UINT8(evt.phy_mtos, data);
    STREAM_TO_UINT8(evt.phy_stom, data);
    STREAM_TO_UINT8(evt.nse, data);
    STREAM_TO_UINT8(evt.bn_mtos, data);
    STREAM_TO_UINT8(evt.bn_stom, data);
    STREAM_TO_UINT8(evt.ft_mtos, data);
    STREAM_TO_UINT8(evt.ft_stom, data);
    STREAM_TO_UINT16(evt.max_pdu_mtos, data);
    STREAM_TO_UINT16(evt.max_pdu_stom, data);
    STREAM_TO_UINT16(evt.iso_itv, data);

    if (evt.status == HCI_SUCCESS) {
      cis->state_flags |= kStateFlagIsConnected;
    } else {
      cis_hdl_to_addr.erase(evt.cis_conn_hdl);
    }

    cis->state_flags &= ~kStateFlagIsConnecting;

    evt.cig_id = cis->cig_id;
    cig_callbacks_->OnCisEvent(kIsoEventCisEstablishCmpl, &evt);
  }

  void disconnection_complete(uint16_t handle, uint8_t reason) {
    /* Check if this is an ISO handle */
    auto cis = GetCisIfKnown(handle);
    if (cis == nullptr) return;

    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";

    log::info("flags: {}", +cis->state_flags);

    BTM_LogHistory(
        kBtmLogTag, cis_hdl_to_addr[handle], "CIS disconnected",
        base::StringPrintf("cis_handle:0x%04x, reason:%s", handle,
                           hci_error_code_text((tHCI_REASON)(reason)).c_str()));
    cis_hdl_to_addr.erase(handle);

    if (cis->state_flags & kStateFlagIsConnected) {
      cis_disconnected_evt evt = {
          .reason = reason,
          .cig_id = cis->cig_id,
          .cis_conn_hdl = handle,
      };

      cig_callbacks_->OnCisEvent(kIsoEventCisDisconnected, &evt);
      cis->state_flags &= ~kStateFlagIsConnected;

      /* return used credits */
      iso_credits_ += cis->used_credits;
      cis->used_credits = 0;

      /* Data path is considered still valid, but can be reconfigured only once
       * CIS is reestablished.
       */
    }
  }

  void handle_num_completed_pkts(uint8_t* p, uint8_t evt_len) {
    uint8_t num_handles;

    STREAM_TO_UINT8(num_handles, p);

    LOG_ASSERT(evt_len == num_handles * 4 + 1);

    for (int i = 0; i < num_handles; i++) {
      uint16_t handle, num_sent;

      STREAM_TO_UINT16(handle, p);
      STREAM_TO_UINT16(num_sent, p);

      auto iter = conn_hdl_to_cis_map_.find(handle);
      if (iter != conn_hdl_to_cis_map_.end()) {
        iter->second->used_credits -= num_sent;
        iso_credits_ += num_sent;
        continue;
      }

      iter = conn_hdl_to_bis_map_.find(handle);
      if (iter != conn_hdl_to_bis_map_.end()) {
        iter->second->used_credits -= num_sent;
        iso_credits_ += num_sent;
        continue;
      }
    }
  }

  void handle_gd_num_completed_pkts(uint16_t handle, uint16_t credits) {
    auto iter = conn_hdl_to_cis_map_.find(handle);
    if (iter != conn_hdl_to_cis_map_.end()) {
      iter->second->used_credits -= credits;
      iso_credits_ += credits;
      return;
    }

    iter = conn_hdl_to_bis_map_.find(handle);
    if (iter != conn_hdl_to_bis_map_.end()) {
      iter->second->used_credits -= credits;
      iso_credits_ += credits;
    }
  }

  void process_create_big_cmpl_pkt(uint8_t len, uint8_t* data) {
    struct big_create_cmpl_evt evt;

    LOG_ASSERT(len >= 18) << "Invalid packet length: " << +len;
    LOG_ASSERT(big_callbacks_ != nullptr) << "Invalid BIG callbacks";

    STREAM_TO_UINT8(evt.status, data);
    STREAM_TO_UINT8(evt.big_id, data);
    STREAM_TO_UINT24(evt.big_sync_delay, data);
    STREAM_TO_UINT24(evt.transport_latency_big, data);
    STREAM_TO_UINT8(evt.phy, data);
    STREAM_TO_UINT8(evt.nse, data);
    STREAM_TO_UINT8(evt.bn, data);
    STREAM_TO_UINT8(evt.pto, data);
    STREAM_TO_UINT8(evt.irc, data);
    STREAM_TO_UINT16(evt.max_pdu, data);
    STREAM_TO_UINT16(evt.iso_interval, data);

    uint8_t num_bis;
    STREAM_TO_UINT8(num_bis, data);

    LOG_ASSERT(num_bis != 0) << "Bis count is 0";
    LOG_ASSERT(len == (18 + num_bis * sizeof(uint16_t)))
        << "Invalid packet length: " << len << ". Number of bis: " << +num_bis;

    for (auto i = 0; i < num_bis; ++i) {
      uint16_t conn_handle;
      STREAM_TO_UINT16(conn_handle, data);
      evt.conn_handles.push_back(conn_handle);
      log::info(" received BIS conn_hdl {}", +conn_handle);

      if (evt.status == HCI_SUCCESS) {
        auto bis = std::unique_ptr<iso_bis>(new iso_bis());
        bis->big_handle = evt.big_id;
        bis->sdu_itv = last_big_create_req_sdu_itv_;
        bis->sync_info = {.seq_nb = 0};
        bis->used_credits = 0;
        bis->state_flags = kStateFlagIsBroadcast;
        conn_hdl_to_bis_map_[conn_handle] = std::move(bis);
      }
    }

    big_callbacks_->OnBigEvent(kIsoEventBigOnCreateCmpl, &evt);

    {
      const std::lock_guard<std::mutex> lock(
          on_iso_traffic_active_callbacks_list_mutex_);
      for (auto callbacks : on_iso_traffic_active_callbacks_list_) {
        callbacks(true);
      }
    }
  }

  void process_terminate_big_cmpl_pkt(uint8_t len, uint8_t* data) {
    struct big_terminate_cmpl_evt evt;

    LOG_ASSERT(len == 2) << "Invalid packet length: " << +len;
    LOG_ASSERT(big_callbacks_ != nullptr) << "Invalid BIG callbacks";

    STREAM_TO_UINT8(evt.big_id, data);
    STREAM_TO_UINT8(evt.reason, data);

    bool is_known_handle = false;
    auto bis_it = conn_hdl_to_bis_map_.cbegin();
    while (bis_it != conn_hdl_to_bis_map_.cend()) {
      if (bis_it->second->big_handle == evt.big_id) {
        bis_it = conn_hdl_to_bis_map_.erase(bis_it);
        is_known_handle = true;
      } else {
        ++bis_it;
      }
    }

    LOG_ASSERT(is_known_handle) << "No such big: " << +evt.big_id;
    big_callbacks_->OnBigEvent(kIsoEventBigOnTerminateCmpl, &evt);

    {
      const std::lock_guard<std::mutex> lock(
          on_iso_traffic_active_callbacks_list_mutex_);
      for (auto callbacks : on_iso_traffic_active_callbacks_list_) {
        callbacks(false);
      }
    }
  }

  void create_big(uint8_t big_id, struct big_create_params big_params) {
    LOG_ASSERT(!IsBigKnown(big_id))
        << "Invalid big - already exists: " << +big_id;

    if (stack_config_get_interface()->get_pts_unencrypt_broadcast()) {
      log::info("Force create broadcst without encryption for PTS test");
      big_params.enc = 0;
      big_params.enc_code = {0};
    }

    last_big_create_req_sdu_itv_ = big_params.sdu_itv;
    btsnd_hcic_create_big(
        big_id, big_params.adv_handle, big_params.num_bis, big_params.sdu_itv,
        big_params.max_sdu_size, big_params.max_transport_latency,
        big_params.rtn, big_params.phy, big_params.packing, big_params.framing,
        big_params.enc, big_params.enc_code);
  }

  void terminate_big(uint8_t big_id, uint8_t reason) {
    LOG_ASSERT(IsBigKnown(big_id)) << "No such big: " << +big_id;

    btsnd_hcic_term_big(big_id, reason);
  }

  void on_iso_event(uint8_t code, uint8_t* packet, uint16_t packet_len) {
    switch (code) {
      case HCI_BLE_CIS_EST_EVT:
        process_cis_est_pkt(packet_len, packet);
        break;
      case HCI_BLE_CREATE_BIG_CPL_EVT:
        process_create_big_cmpl_pkt(packet_len, packet);
        break;
      case HCI_BLE_TERM_BIG_CPL_EVT:
        process_terminate_big_cmpl_pkt(packet_len, packet);
        break;
      case HCI_BLE_CIS_REQ_EVT:
        /* Not supported */
        break;
      case HCI_BLE_BIG_SYNC_EST_EVT:
        /* Not supported */
        break;
      case HCI_BLE_BIG_SYNC_LOST_EVT:
        /* Not supported */
        break;
      default:
        log::error("Unhandled event code {}", +code);
    }
  }

  void handle_iso_data(BT_HDR* p_msg) {
    const uint8_t* stream = p_msg->data;
    cis_data_evt evt;
    uint16_t handle, seq_nb;

    if (p_msg->len <= ((p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS)
                           ? kIsoHeaderWithTsLen
                           : kIsoHeaderWithoutTsLen))
      return;

    LOG_ASSERT(cig_callbacks_ != nullptr) << "Invalid CIG callbacks";

    STREAM_TO_UINT16(handle, stream);
    evt.cis_conn_hdl = HCID_GET_HANDLE(handle);

    iso_base* iso = GetCisIfKnown(evt.cis_conn_hdl);
    if (iso == nullptr) {
      log::error(", received data for the non-registered CIS!");
      return;
    }

    STREAM_SKIP_UINT16(stream);
    if (p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS) {
      STREAM_TO_UINT32(evt.ts, stream);
    } else {
      evt.ts = 0;
    }

    STREAM_TO_UINT16(seq_nb, stream);

    uint16_t expected_seq_nb = iso->sync_info.seq_nb;
    iso->sync_info.seq_nb = (seq_nb + 1) & 0xffff;

    evt.evt_lost = ((1 << 16) + seq_nb - expected_seq_nb) & 0xffff;
    if (evt.evt_lost > 0) {
      iso->evt_stats.evt_lost_count += evt.evt_lost;
      iso->evt_stats.evt_last_lost_us =
          bluetooth::common::time_get_os_boottime_us();

      log::warn("{} packets lost.", evt.evt_lost);
      iso->evt_stats.seq_nb_mismatch_count++;
    }

    evt.p_msg = p_msg;
    evt.cig_id = iso->cig_id;
    evt.seq_nb = seq_nb;
    cig_callbacks_->OnCisEvent(kIsoEventCisDataAvailable, &evt);
  }

  iso_cis* GetCisIfKnown(uint16_t cis_conn_handle) {
    auto cis_it = conn_hdl_to_cis_map_.find(cis_conn_handle);
    return (cis_it != conn_hdl_to_cis_map_.end()) ? cis_it->second.get()
                                                  : nullptr;
  }

  iso_bis* GetBisIfKnown(uint16_t bis_conn_handle) {
    auto bis_it = conn_hdl_to_bis_map_.find(bis_conn_handle);
    return (bis_it != conn_hdl_to_bis_map_.end()) ? bis_it->second.get()
                                                  : nullptr;
  }

  iso_base* GetIsoIfKnown(uint16_t iso_handle) {
    struct iso_base* iso = GetCisIfKnown(iso_handle);
    return (iso != nullptr) ? iso : GetBisIfKnown(iso_handle);
  }

  bool IsCigKnown(uint8_t cig_id) const {
    auto const cis_it =
        std::find_if(conn_hdl_to_cis_map_.cbegin(), conn_hdl_to_cis_map_.cend(),
                     [&cig_id](auto& kv_pair) {
                       return (kv_pair.second->cig_id == cig_id);
                     });
    return (cis_it != conn_hdl_to_cis_map_.cend());
  }

  bool IsBigKnown(uint8_t big_id) const {
    auto bis_it =
        std::find_if(conn_hdl_to_bis_map_.cbegin(), conn_hdl_to_bis_map_.cend(),
                     [&big_id](auto& kv_pair) {
                       return (kv_pair.second->big_handle == big_id);
                     });
    return (bis_it != conn_hdl_to_bis_map_.cend());
  }

  static void dump_credits_stats(int fd, const iso_base::credits_stats& stats) {
    uint64_t now_us = bluetooth::common::time_get_os_boottime_us();

    dprintf(fd, "        Credits Stats:\n");
    dprintf(fd, "          Credits underflow (count): %zu\n",
            stats.credits_underflow_count);
    dprintf(fd, "          Credits underflow (bytes): %zu\n",
            stats.credits_underflow_bytes);
    dprintf(
        fd, "          Last underflow time ago (ms): %llu\n",
        (stats.credits_last_underflow_us > 0
             ? (unsigned long long)(now_us - stats.credits_last_underflow_us) /
                   1000
             : 0llu));
  }

  static void dump_event_stats(int fd, const iso_base::event_stats& stats) {
    uint64_t now_us = bluetooth::common::time_get_os_boottime_us();

    dprintf(fd, "        Event Stats:\n");
    dprintf(fd, "          Sequence number mismatch (count): %zu\n",
            stats.seq_nb_mismatch_count);
    dprintf(fd, "          Event lost (count): %zu\n", stats.evt_lost_count);
    dprintf(fd, "          Last event lost time ago (ms): %llu\n",
            (stats.evt_last_lost_us > 0
                 ? (unsigned long long)(now_us - stats.evt_last_lost_us) / 1000
                 : 0llu));
  }

  void dump(int fd) const {
    dprintf(fd, "  ----------------\n ");
    dprintf(fd, "  ISO Manager:\n");
    dprintf(fd, "    Available credits: %d\n", iso_credits_.load());
    dprintf(fd, "    Controller buffer size: %d\n", iso_buffer_size_);
    dprintf(fd, "    Num of ISO traffic callbacks: %lu\n",
            static_cast<unsigned long>(
                on_iso_traffic_active_callbacks_list_.size()));
    dprintf(fd, "    CISes:\n");
    for (auto const& cis_pair : conn_hdl_to_cis_map_) {
      dprintf(fd, "      CIS Connection handle: %d\n", cis_pair.first);
      dprintf(fd, "        CIG ID: %d\n", cis_pair.second->cig_id);
      dprintf(fd, "        Used Credits: %d\n",
              cis_pair.second->used_credits.load());
      dprintf(fd, "        SDU Interval: %d\n", cis_pair.second->sdu_itv);
      dprintf(fd, "        State Flags: 0x%02hx\n",
              cis_pair.second->state_flags.load());
      dump_credits_stats(fd, cis_pair.second->cr_stats);
      dump_event_stats(fd, cis_pair.second->evt_stats);
    }
    dprintf(fd, "    BISes:\n");
    for (auto const& cis_pair : conn_hdl_to_bis_map_) {
      dprintf(fd, "      BIS Connection handle: %d\n", cis_pair.first);
      dprintf(fd, "        BIG Handle: %d\n", cis_pair.second->big_handle);
      dprintf(fd, "        Used Credits: %d\n",
              cis_pair.second->used_credits.load());
      dprintf(fd, "        SDU Interval: %d\n", cis_pair.second->sdu_itv);
      dprintf(fd, "        State Flags: 0x%02hx\n",
              cis_pair.second->state_flags.load());
      dump_credits_stats(fd, cis_pair.second->cr_stats);
      dump_event_stats(fd, cis_pair.second->evt_stats);
    }
    dprintf(fd, "  ----------------\n ");
  }

  std::map<uint16_t, std::unique_ptr<iso_cis>> conn_hdl_to_cis_map_;
  std::map<uint16_t, std::unique_ptr<iso_bis>> conn_hdl_to_bis_map_;
  std::map<uint16_t, RawAddress> cis_hdl_to_addr;

  std::atomic_uint16_t iso_credits_;
  uint16_t iso_buffer_size_;
  uint32_t last_big_create_req_sdu_itv_;

  CigCallbacks* cig_callbacks_ = nullptr;
  BigCallbacks* big_callbacks_ = nullptr;
  std::mutex on_iso_traffic_active_callbacks_list_mutex_;
  std::list<void (*)(bool)> on_iso_traffic_active_callbacks_list_;
  base::WeakPtrFactory<iso_impl> weak_factory_{this};
};

}  // namespace iso_manager
}  // namespace hci
}  // namespace bluetooth
