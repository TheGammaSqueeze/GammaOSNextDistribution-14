/*
 * Copyright 2021 The Android Open Source Project
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
/*
 * Generated mock file from original source file
 *   Functions generated:69
 *
 *  mockcify.pl ver 0.3.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_hcic_hciblecmds.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_hcic_hciblecmds {

// Function state capture and return values, if needed
struct btsnd_hci_ble_add_device_to_periodic_advertiser_list
    btsnd_hci_ble_add_device_to_periodic_advertiser_list;
struct btsnd_hci_ble_clear_periodic_advertiser_list
    btsnd_hci_ble_clear_periodic_advertiser_list;
struct btsnd_hci_ble_remove_device_from_periodic_advertiser_list
    btsnd_hci_ble_remove_device_from_periodic_advertiser_list;
struct btsnd_hcic_accept_cis_req btsnd_hcic_accept_cis_req;
struct btsnd_hcic_ble_enh_rx_test btsnd_hcic_ble_enh_rx_test;
struct btsnd_hcic_ble_enh_tx_test btsnd_hcic_ble_enh_tx_test;
struct btsnd_hcic_ble_ltk_req_neg_reply btsnd_hcic_ble_ltk_req_neg_reply;
struct btsnd_hcic_ble_ltk_req_reply btsnd_hcic_ble_ltk_req_reply;
struct btsnd_hcic_ble_periodic_advertising_create_sync
    btsnd_hcic_ble_periodic_advertising_create_sync;
struct btsnd_hcic_ble_periodic_advertising_create_sync_cancel
    btsnd_hcic_ble_periodic_advertising_create_sync_cancel;
struct btsnd_hcic_ble_periodic_advertising_set_info_transfer
    btsnd_hcic_ble_periodic_advertising_set_info_transfer;
struct btsnd_hcic_ble_periodic_advertising_sync_transfer
    btsnd_hcic_ble_periodic_advertising_sync_transfer;
struct btsnd_hcic_ble_periodic_advertising_terminate_sync
    btsnd_hcic_ble_periodic_advertising_terminate_sync;
struct btsnd_hcic_ble_rand btsnd_hcic_ble_rand;
struct btsnd_hcic_ble_rc_param_req_neg_reply
    btsnd_hcic_ble_rc_param_req_neg_reply;
struct btsnd_hcic_ble_rc_param_req_reply btsnd_hcic_ble_rc_param_req_reply;
struct btsnd_hcic_ble_read_adv_chnl_tx_power
    btsnd_hcic_ble_read_adv_chnl_tx_power;
struct btsnd_hcic_ble_read_remote_feat btsnd_hcic_ble_read_remote_feat;
struct btsnd_hcic_ble_read_resolvable_addr_peer
    btsnd_hcic_ble_read_resolvable_addr_peer;
struct btsnd_hcic_ble_receiver_test btsnd_hcic_ble_receiver_test;
struct btsnd_hcic_ble_set_adv_data btsnd_hcic_ble_set_adv_data;
struct btsnd_hcic_ble_set_adv_enable btsnd_hcic_ble_set_adv_enable;
struct btsnd_hcic_ble_set_data_length btsnd_hcic_ble_set_data_length;
struct btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params
    btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params;
struct btsnd_hcic_ble_set_extended_scan_enable
    btsnd_hcic_ble_set_extended_scan_enable;
struct btsnd_hcic_ble_set_extended_scan_params
    btsnd_hcic_ble_set_extended_scan_params;
struct btsnd_hcic_ble_set_periodic_advertising_receive_enable
    btsnd_hcic_ble_set_periodic_advertising_receive_enable;
struct btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params
    btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params;
struct btsnd_hcic_ble_set_privacy_mode btsnd_hcic_ble_set_privacy_mode;
struct btsnd_hcic_ble_set_rand_priv_addr_timeout
    btsnd_hcic_ble_set_rand_priv_addr_timeout;
struct btsnd_hcic_ble_set_scan_enable btsnd_hcic_ble_set_scan_enable;
struct btsnd_hcic_ble_set_scan_params btsnd_hcic_ble_set_scan_params;
struct btsnd_hcic_ble_start_enc btsnd_hcic_ble_start_enc;
struct btsnd_hcic_ble_test_end btsnd_hcic_ble_test_end;
struct btsnd_hcic_ble_transmitter_test btsnd_hcic_ble_transmitter_test;
struct btsnd_hcic_ble_upd_ll_conn_params btsnd_hcic_ble_upd_ll_conn_params;
struct btsnd_hcic_ble_write_adv_params btsnd_hcic_ble_write_adv_params;
struct btsnd_hcic_create_big btsnd_hcic_create_big;
struct btsnd_hcic_create_cis btsnd_hcic_create_cis;
struct btsnd_hcic_read_iso_link_quality btsnd_hcic_read_iso_link_quality;
struct btsnd_hcic_rej_cis_req btsnd_hcic_rej_cis_req;
struct btsnd_hcic_remove_cig btsnd_hcic_remove_cig;
struct btsnd_hcic_remove_iso_data_path btsnd_hcic_remove_iso_data_path;
struct btsnd_hcic_req_peer_sca btsnd_hcic_req_peer_sca;
struct btsnd_hcic_set_cig_params btsnd_hcic_set_cig_params;
struct btsnd_hcic_setup_iso_data_path btsnd_hcic_setup_iso_data_path;
struct btsnd_hcic_term_big btsnd_hcic_term_big;

}  // namespace stack_hcic_hciblecmds
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_hcic_hciblecmds {}  // namespace stack_hcic_hciblecmds
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btsnd_hci_ble_add_device_to_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hci_ble_add_device_to_periodic_advertiser_list(
          adv_addr_type, adv_addr, adv_sid, std::move(cb));
}
void btsnd_hci_ble_clear_periodic_advertiser_list(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hci_ble_clear_periodic_advertiser_list(std::move(cb));
}
void btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
          adv_addr_type, adv_addr, adv_sid, std::move(cb));
}
void btsnd_hcic_accept_cis_req(uint16_t conn_handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_accept_cis_req(conn_handle);
}
void btsnd_hcic_ble_enh_rx_test(uint8_t rx_chan, uint8_t phy,
                                uint8_t mod_index) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_enh_rx_test(rx_chan, phy,
                                                                mod_index);
}
void btsnd_hcic_ble_enh_tx_test(uint8_t tx_chan, uint8_t data_len,
                                uint8_t payload, uint8_t phy) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_enh_tx_test(
      tx_chan, data_len, payload, phy);
}
void btsnd_hcic_ble_ltk_req_neg_reply(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_ltk_req_neg_reply(handle);
}
void btsnd_hcic_ble_ltk_req_reply(uint16_t handle, const Octet16& ltk) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_ltk_req_reply(handle, ltk);
}
void btsnd_hcic_ble_periodic_advertising_create_sync(
    uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
    const RawAddress& adv_addr, uint16_t skip_num, uint16_t sync_timeout,
    uint8_t sync_cte_type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_periodic_advertising_create_sync(
          options, adv_sid, adv_addr_type, adv_addr, skip_num, sync_timeout,
          sync_cte_type);
}
void btsnd_hcic_ble_periodic_advertising_create_sync_cancel(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_periodic_advertising_create_sync_cancel(std::move(cb));
}
void btsnd_hcic_ble_periodic_advertising_set_info_transfer(
    uint16_t conn_handle, uint16_t service_data, uint8_t adv_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_periodic_advertising_set_info_transfer(
          conn_handle, service_data, adv_handle, std::move(cb));
}
void btsnd_hcic_ble_periodic_advertising_sync_transfer(
    uint16_t conn_handle, uint16_t service_data, uint16_t sync_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_periodic_advertising_sync_transfer(
          conn_handle, service_data, sync_handle, std::move(cb));
}
void btsnd_hcic_ble_periodic_advertising_terminate_sync(
    uint16_t sync_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_periodic_advertising_terminate_sync(sync_handle,
                                                         std::move(cb));
}
void btsnd_hcic_ble_rand(base::Callback<void(BT_OCTET8)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_rand(std::move(cb));
}
void btsnd_hcic_ble_rc_param_req_neg_reply(uint16_t handle, uint8_t reason) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_rc_param_req_neg_reply(
      handle, reason);
}
void btsnd_hcic_ble_rc_param_req_reply(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout,
                                       uint16_t min_ce_len,
                                       uint16_t max_ce_len) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_rc_param_req_reply(
      handle, conn_int_min, conn_int_max, conn_latency, conn_timeout,
      min_ce_len, max_ce_len);
}
void btsnd_hcic_ble_read_adv_chnl_tx_power(void) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_read_adv_chnl_tx_power();
}
void btsnd_hcic_ble_read_remote_feat(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_read_remote_feat(handle);
}
void btsnd_hcic_ble_read_resolvable_addr_peer(uint8_t addr_type_peer,
                                              const RawAddress& bda_peer) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_read_resolvable_addr_peer(
      addr_type_peer, bda_peer);
}
void btsnd_hcic_ble_receiver_test(uint8_t rx_freq) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_receiver_test(rx_freq);
}
void btsnd_hcic_ble_set_adv_data(uint8_t data_len, uint8_t* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_adv_data(data_len,
                                                                 p_data);
}
void btsnd_hcic_ble_set_adv_enable(uint8_t adv_enable) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_adv_enable(adv_enable);
}
void btsnd_hcic_ble_set_data_length(uint16_t conn_handle, uint16_t tx_octets,
                                    uint16_t tx_time) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_data_length(
      conn_handle, tx_octets, tx_time);
}
void btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
          conn_handle, mode, skip, sync_timeout, cte_type, std::move(cb));
}
void btsnd_hcic_ble_set_extended_scan_enable(uint8_t enable,
                                             uint8_t filter_duplicates,
                                             uint16_t duration,
                                             uint16_t period) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_extended_scan_enable(
      enable, filter_duplicates, duration, period);
}
void btsnd_hcic_ble_set_extended_scan_params(uint8_t own_address_type,
                                             uint8_t scanning_filter_policy,
                                             uint8_t scanning_phys,
                                             scanning_phy_cfg* phy_cfg) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_extended_scan_params(
      own_address_type, scanning_filter_policy, scanning_phys, phy_cfg);
}
void btsnd_hcic_ble_set_periodic_advertising_receive_enable(
    uint16_t sync_handle, bool enable,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_set_periodic_advertising_receive_enable(
          sync_handle, enable, std::move(cb));
}
void btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::
      btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
          conn_handle, mode, skip, sync_timeout, cte_type, std::move(cb));
}
void btsnd_hcic_ble_set_privacy_mode(uint8_t addr_type_peer,
                                     const RawAddress& bda_peer,
                                     uint8_t privacy_type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_privacy_mode(
      addr_type_peer, bda_peer, privacy_type);
}
void btsnd_hcic_ble_set_rand_priv_addr_timeout(uint16_t rpa_timout) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_rand_priv_addr_timeout(
      rpa_timout);
}
void btsnd_hcic_ble_set_scan_enable(uint8_t scan_enable, uint8_t duplicate) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_scan_enable(scan_enable,
                                                                    duplicate);
}
void btsnd_hcic_ble_set_scan_params(uint8_t scan_type, uint16_t scan_int,
                                    uint16_t scan_win, uint8_t addr_type_own,
                                    uint8_t scan_filter_policy) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_set_scan_params(
      scan_type, scan_int, scan_win, addr_type_own, scan_filter_policy);
}
void btsnd_hcic_ble_start_enc(uint16_t handle,
                              uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
                              uint16_t ediv, const Octet16& ltk) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_start_enc(handle, rand,
                                                              ediv, ltk);
}
void btsnd_hcic_ble_test_end(void) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_test_end();
}
void btsnd_hcic_ble_transmitter_test(uint8_t tx_freq, uint8_t test_data_len,
                                     uint8_t payload) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_transmitter_test(
      tx_freq, test_data_len, payload);
}
void btsnd_hcic_ble_upd_ll_conn_params(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout,
                                       uint16_t min_ce_len,
                                       uint16_t max_ce_len) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_upd_ll_conn_params(
      handle, conn_int_min, conn_int_max, conn_latency, conn_timeout,
      min_ce_len, max_ce_len);
}
void btsnd_hcic_ble_write_adv_params(uint16_t adv_int_min, uint16_t adv_int_max,
                                     uint8_t adv_type,
                                     tBLE_ADDR_TYPE addr_type_own,
                                     tBLE_ADDR_TYPE addr_type_dir,
                                     const RawAddress& direct_bda,
                                     uint8_t channel_map,
                                     uint8_t adv_filter_policy) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_ble_write_adv_params(
      adv_int_min, adv_int_max, adv_type, addr_type_own, addr_type_dir,
      direct_bda, channel_map, adv_filter_policy);
}
void btsnd_hcic_create_big(uint8_t big_handle, uint8_t adv_handle,
                           uint8_t num_bis, uint32_t sdu_itv,
                           uint16_t max_sdu_size, uint16_t transport_latency,
                           uint8_t rtn, uint8_t phy, uint8_t packing,
                           uint8_t framing, uint8_t enc,
                           std::array<uint8_t, 16> bcst_code) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_create_big(
      big_handle, adv_handle, num_bis, sdu_itv, max_sdu_size, transport_latency,
      rtn, phy, packing, framing, enc, bcst_code);
}
void btsnd_hcic_create_cis(uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_create_cis(num_cis, cis_cfg,
                                                           std::move(cb));
}
void btsnd_hcic_read_iso_link_quality(
    uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_read_iso_link_quality(
      iso_handle, std::move(cb));
}
void btsnd_hcic_rej_cis_req(uint16_t conn_handle, uint8_t reason,
                            base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_rej_cis_req(conn_handle, reason,
                                                            std::move(cb));
}
void btsnd_hcic_remove_cig(uint8_t cig_id,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_remove_cig(cig_id,
                                                           std::move(cb));
}
void btsnd_hcic_remove_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_remove_iso_data_path(
      iso_handle, data_path_dir, std::move(cb));
}
void btsnd_hcic_req_peer_sca(uint16_t conn_handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_req_peer_sca(conn_handle);
}
void btsnd_hcic_set_cig_params(
    uint8_t cig_id, uint32_t sdu_itv_mtos, uint32_t sdu_itv_stom, uint8_t sca,
    uint8_t packing, uint8_t framing, uint16_t max_trans_lat_stom,
    uint16_t max_trans_lat_mtos, uint8_t cis_cnt, const EXT_CIS_CFG* cis_cfg,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_set_cig_params(
      cig_id, sdu_itv_mtos, sdu_itv_stom, sca, packing, framing,
      max_trans_lat_stom, max_trans_lat_mtos, cis_cnt, cis_cfg, std::move(cb));
}
void btsnd_hcic_setup_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
    uint8_t codec_id_format, uint16_t codec_id_company,
    uint16_t codec_id_vendor, uint32_t controller_delay,
    std::vector<uint8_t> codec_conf,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_setup_iso_data_path(
      iso_handle, data_path_dir, data_path_id, codec_id_format,
      codec_id_company, codec_id_vendor, controller_delay, codec_conf,
      std::move(cb));
}
void btsnd_hcic_term_big(uint8_t big_handle, uint8_t reason) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hciblecmds::btsnd_hcic_term_big(big_handle, reason);
}
// Mocked functions complete
// END mockcify generation
