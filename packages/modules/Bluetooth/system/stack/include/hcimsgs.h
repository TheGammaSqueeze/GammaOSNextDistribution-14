/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#ifndef HCIMSGS_H
#define HCIMSGS_H

#include <base/functional/callback_forward.h>

#include <cstdint>
#include <vector>

#include "device/include/esco_parameters.h"
#include "stack/include/bt_lap.h"
#include "stack/include/bt_name.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_api_types.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/* Message by message.... */

enum hci_data_direction_t {
  HOST_TO_CONTROLLER = 0,
  CONTROLLER_TO_HOST = 1,
};

/* Disconnect */
namespace bluetooth::legacy::hci {
class Interface {
 public:
  virtual void Disconnect(uint16_t handle, uint8_t reason) const = 0;
  virtual void ChangeConnectionPacketType(uint16_t handle,
                                          uint16_t packet_types) const = 0;
  virtual void StartRoleSwitch(const RawAddress& bd_addr,
                               uint8_t role) const = 0;
  virtual void ConfigureDataPath(hci_data_direction_t data_path_direction,
                                 uint8_t data_path_id,
                                 std::vector<uint8_t> vendor_config) const = 0;
  virtual ~Interface() = default;
};

const Interface& GetInterface();
}  // namespace bluetooth::legacy::hci

/* Add SCO Connection */
void btsnd_hcic_add_SCO_conn(uint16_t handle, uint16_t packet_types);

/* Add SCO Connection */

/* Create Connection Cancel */
void btsnd_hcic_create_conn_cancel(const RawAddress& dest);

/* Create Connection Cancel */

/* Accept Connection Request */
void btsnd_hcic_accept_conn(const RawAddress& bd_addr, uint8_t role);

/* Accept Connection Request */

/* Reject Connection Request */
void btsnd_hcic_reject_conn(const RawAddress& bd_addr, uint8_t reason);

/* Reject Connection Request */

/* Link Key Request Reply */
void btsnd_hcic_link_key_req_reply(const RawAddress& bd_addr,
                                   const LinkKey& link_key);

/* Link Key Request Reply  */

/* Link Key Request Neg Reply */
void btsnd_hcic_link_key_neg_reply(const RawAddress& bd_addr);

/* Link Key Request Neg Reply  */

/* PIN Code Request Reply */
void btsnd_hcic_pin_code_req_reply(const RawAddress& bd_addr,
                                   uint8_t pin_code_len, PIN_CODE pin_code);

/* PIN Code Request Reply  */

/* Link Key Request Neg Reply */
void btsnd_hcic_pin_code_neg_reply(const RawAddress& bd_addr);

/* Link Key Request Neg Reply  */

/* Change Connection Type */
void btsnd_hcic_change_conn_type(uint16_t handle, uint16_t packet_types);

/* Change Connection Type */

void btsnd_hcic_auth_request(uint16_t handle); /* Authentication Request */

/* Set Connection Encryption */
void btsnd_hcic_set_conn_encrypt(uint16_t handle, bool enable);
/* Set Connection Encryption */

/* Remote Name Request */
void btsnd_hcic_rmt_name_req(const RawAddress& bd_addr,
                             uint8_t page_scan_rep_mode, uint8_t page_scan_mode,
                             uint16_t clock_offset);
/* Remote Name Request */

/* Remote Name Request Cancel */
void btsnd_hcic_rmt_name_req_cancel(const RawAddress& bd_addr);
/* Remote Name Request Cancel */

/* Remote Extended Features */
void btsnd_hcic_rmt_ext_features(uint16_t handle, uint8_t page_num);
/* Remote Extended Features */

void btsnd_hcic_rmt_ver_req(uint16_t handle); /* Remote Version Info Request */
void btsnd_hcic_read_rmt_clk_offset(uint16_t handle); /* Remote Clock Offset */
void btsnd_hcic_setup_esco_conn(uint16_t handle, uint32_t transmit_bandwidth,
                                uint32_t receive_bandwidth,
                                uint16_t max_latency, uint16_t voice,
                                uint8_t retrans_effort, uint16_t packet_types);
void btsnd_hcic_accept_esco_conn(const RawAddress& bd_addr,
                                 uint32_t transmit_bandwidth,
                                 uint32_t receive_bandwidth,
                                 uint16_t max_latency, uint16_t content_fmt,
                                 uint8_t retrans_effort, uint16_t packet_types);

void btsnd_hcic_reject_esco_conn(const RawAddress& bd_addr, uint8_t reason);
/* Hold Mode */
void btsnd_hcic_hold_mode(uint16_t handle, uint16_t max_hold_period,
                          uint16_t min_hold_period);

/* Hold Mode */

/* Sniff Mode */
void btsnd_hcic_sniff_mode(uint16_t handle, uint16_t max_sniff_period,
                           uint16_t min_sniff_period, uint16_t sniff_attempt,
                           uint16_t sniff_timeout);
/* Sniff Mode */

void btsnd_hcic_exit_sniff_mode(uint16_t handle); /* Exit Sniff Mode */

/* Park Mode */
void btsnd_hcic_park_mode(uint16_t handle, uint16_t beacon_max_interval,
                          uint16_t beacon_min_interval);
/* Park Mode */

void btsnd_hcic_exit_park_mode(uint16_t handle); /* Exit Park Mode */

/* Write Policy Settings */
void btsnd_hcic_write_policy_set(uint16_t handle, uint16_t settings);
/* Write Policy Settings */

/* Write Default Policy Settings */
void btsnd_hcic_write_def_policy_set(uint16_t settings);
/* Write Default Policy Settings */

/******************************************
 *    Lisbon Features
 ******************************************/
/* Sniff Subrating */
void btsnd_hcic_sniff_sub_rate(uint16_t handle, uint16_t max_lat,
                               uint16_t min_remote_lat, uint16_t min_local_lat);
/* Sniff Subrating */

/* Extended Inquiry Response */
void btsnd_hcic_write_ext_inquiry_response(void* buffer, uint8_t fec_req);
/* IO Capabilities Response */
void btsnd_hcic_io_cap_req_reply(const RawAddress& bd_addr, uint8_t capability,
                                 uint8_t oob_present, uint8_t auth_req);
/* IO Capabilities Req Neg Reply */
void btsnd_hcic_io_cap_req_neg_reply(const RawAddress& bd_addr,
                                     uint8_t err_code);
/* Read Local OOB Data */
void btsnd_hcic_read_local_oob_data(void);

void btsnd_hcic_user_conf_reply(const RawAddress& bd_addr, bool is_yes);

void btsnd_hcic_user_passkey_reply(const RawAddress& bd_addr, uint32_t value);

void btsnd_hcic_user_passkey_neg_reply(const RawAddress& bd_addr);

/* Remote OOB Data Request Reply */
void btsnd_hcic_rem_oob_reply(const RawAddress& bd_addr, const Octet16& c,
                              const Octet16& r);

/* Remote OOB Data Request Negative Reply */
void btsnd_hcic_rem_oob_neg_reply(const RawAddress& bd_addr);

/* Read Default Erroneous Data Reporting */
void btsnd_hcic_read_default_erroneous_data_rpt(void);

void btsnd_hcic_enhanced_flush(uint16_t handle, uint8_t packet_type);

/**** end of Simple Pairing Commands ****/

extern void btsnd_hcic_set_event_filter(uint8_t filt_type,
                                        uint8_t filt_cond_type,
                                        uint8_t* filt_cond,
                                        uint8_t filt_cond_len);
/* Set Event Filter */

/* Delete Stored Key */
void btsnd_hcic_delete_stored_key(const RawAddress& bd_addr,
                                  bool delete_all_flag);
/* Delete Stored Key */

/* Change Local Name */
void btsnd_hcic_change_name(BD_NAME name);

#define HCIC_PARAM_SIZE_READ_CMD 0

#define HCIC_PARAM_SIZE_WRITE_PARAM1 1

#define HCIC_PARAM_SIZE_WRITE_PARAM3 3

void btsnd_hcic_write_pin_type(uint8_t type);      /* Write PIN Type */
void btsnd_hcic_write_auto_accept(uint8_t flag);   /* Write Auto Accept */
void btsnd_hcic_read_name(void);                   /* Read Local Name */
void btsnd_hcic_write_page_tout(uint16_t timeout); /* Write Page Timout */
void btsnd_hcic_write_scan_enable(uint8_t flag);   /* Write Scan Enable */
void btsnd_hcic_write_pagescan_cfg(
    uint16_t interval, uint16_t window); /* Write Page Scan Activity */
/* Write Page Scan Activity */

/* Write Inquiry Scan Activity */
void btsnd_hcic_write_inqscan_cfg(uint16_t interval, uint16_t window);
/* Write Inquiry Scan Activity */

void btsnd_hcic_write_auth_enable(
    uint8_t flag); /* Write Authentication Enable */
void btsnd_hcic_write_dev_class(DEV_CLASS dev); /* Write Class of Device */
void btsnd_hcic_write_voice_settings(uint16_t flags); /* Write Voice Settings */

void btsnd_hcic_write_auto_flush_tout(
    uint16_t handle, uint16_t timeout); /* Write Retransmit Timout */

void btsnd_hcic_read_tx_power(uint16_t handle,
                              uint8_t type); /* Read Tx Power */

/* Write Link Supervision Timeout */
void btsnd_hcic_write_link_super_tout(uint16_t handle, uint16_t timeout);
/* Write Link Supervision Timeout */

void btsnd_hcic_write_cur_iac_lap(
    uint8_t num_cur_iac, LAP* const iac_lap); /* Write Current IAC LAP */
/* Write Current IAC LAP */

void btsnd_hcic_read_rssi(uint16_t handle); /* Read RSSI */
using ReadEncKeySizeCb = base::OnceCallback<void(uint8_t, uint16_t, uint8_t)>;
void btsnd_hcic_read_encryption_key_size(uint16_t handle, ReadEncKeySizeCb cb);
void btsnd_hcic_read_failed_contact_counter(uint16_t handle);
void btsnd_hcic_enable_test_mode(void); /* Enable Device Under Test Mode */
void btsnd_hcic_write_pagescan_type(uint8_t type); /* Write Page Scan Type */
void btsnd_hcic_write_inqscan_type(uint8_t type);  /* Write Inquiry Scan Type */
void btsnd_hcic_write_inquiry_mode(uint8_t type);  /* Write Inquiry Mode */

/* Enhanced setup SCO connection (CSA2) */
void btsnd_hcic_enhanced_set_up_synchronous_connection(
    uint16_t conn_handle, enh_esco_params_t* p_parms);

/* Enhanced accept SCO connection request (CSA2) */
void btsnd_hcic_enhanced_accept_synchronous_connection(
    const RawAddress& bd_addr, enh_esco_params_t* p_parms);

#define HCI_DATA_HANDLE_MASK 0x0FFF
#define HCI_DATA_PKT_STATUS_MASK 0x3000

#define HCID_GET_HANDLE_EVENT(p)                     \
  (uint16_t)((*((uint8_t*)((p) + 1) + (p)->offset) + \
              (*((uint8_t*)((p) + 1) + (p)->offset + 1) << 8)))

#define HCID_GET_HANDLE(u16) (uint16_t)((u16)&HCI_DATA_HANDLE_MASK)
#define HCID_GET_PKT_STATUS(u16) \
  (uint16_t)(((u16)&HCI_DATA_PKT_STATUS_MASK) >> 12)

#define HCI_DATA_EVENT_MASK 3
#define HCI_DATA_EVENT_OFFSET 12
#define HCID_GET_EVENT(u16) \
  (uint8_t)(((u16) >> HCI_DATA_EVENT_OFFSET) & HCI_DATA_EVENT_MASK)

void btsnd_hcic_vendor_spec_cmd(uint16_t opcode, uint8_t len, uint8_t* p_data,
                                tBTM_VSC_CMPL_CB* p_cmd_cplt_cback);

/*******************************************************************************
 * BLE Commands
 *      Note: "local_controller_id" is for transport, not counted in HCI
 *             message size
 ******************************************************************************/
#define HCIC_BLE_RAND_DI_SIZE 8

#define HCIC_BLE_CHNL_MAP_SIZE 5
#define HCIC_PARAM_SIZE_BLE_READ_PHY 2
#define HCIC_PARAM_SIZE_BLE_SET_PHY 7

/* ULP HCI command */
void btsnd_hcic_ble_set_local_used_feat(uint8_t feat_set[8]);

void btsnd_hcic_ble_write_adv_params(uint16_t adv_int_min, uint16_t adv_int_max,
                                     uint8_t adv_type,
                                     tBLE_ADDR_TYPE addr_type_own,
                                     tBLE_ADDR_TYPE addr_type_dir,
                                     const RawAddress& direct_bda,
                                     uint8_t channel_map,
                                     uint8_t adv_filter_policy);

void btsnd_hcic_ble_read_adv_chnl_tx_power(void);

void btsnd_hcic_ble_set_adv_data(uint8_t data_len, uint8_t* p_data);

void btsnd_hcic_ble_set_adv_enable(uint8_t adv_enable);

void btsnd_hcic_ble_set_scan_params(uint8_t scan_type, uint16_t scan_int,
                                    uint16_t scan_win, uint8_t addr_type,
                                    uint8_t scan_filter_policy);

void btsnd_hcic_ble_set_scan_enable(uint8_t scan_enable, uint8_t duplicate);

void btsnd_hcic_ble_read_acceptlist_size(void);

void btsnd_hcic_ble_upd_ll_conn_params(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout, uint16_t min_len,
                                       uint16_t max_len);

void btsnd_hcic_ble_read_remote_feat(uint16_t handle);

void btsnd_hcic_ble_rand(base::Callback<void(BT_OCTET8)> cb);

void btsnd_hcic_ble_start_enc(uint16_t handle,
                              uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
                              uint16_t ediv, const Octet16& ltk);

void btsnd_hcic_ble_ltk_req_reply(uint16_t handle, const Octet16& ltk);

void btsnd_hcic_ble_ltk_req_neg_reply(uint16_t handle);

void btsnd_hcic_ble_read_supported_states(void);

void btsnd_hcic_ble_receiver_test(uint8_t rx_freq);

void btsnd_hcic_ble_transmitter_test(uint8_t tx_freq, uint8_t test_data_len,
                                     uint8_t payload);
void btsnd_hcic_ble_test_end(void);

void btsnd_hcic_ble_rc_param_req_reply(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout,
                                       uint16_t min_ce_len,
                                       uint16_t max_ce_len);

void btsnd_hcic_ble_rc_param_req_neg_reply(uint16_t handle, uint8_t reason);

void btsnd_hcic_ble_set_data_length(uint16_t conn_handle, uint16_t tx_octets,
                                    uint16_t tx_time);

struct scanning_phy_cfg {
  uint8_t scan_type;
  uint16_t scan_int;
  uint16_t scan_win;
};

void btsnd_hcic_ble_set_extended_scan_params(uint8_t own_address_type,
                                             uint8_t scanning_filter_policy,
                                             uint8_t scanning_phys,
                                             scanning_phy_cfg* phy_cfg);

void btsnd_hcic_ble_set_extended_scan_enable(uint8_t enable,
                                             uint8_t filter_duplicates,
                                             uint16_t duration,
                                             uint16_t period);

struct EXT_CONN_PHY_CFG {
  uint16_t scan_int;
  uint16_t scan_win;
  uint16_t conn_int_min;
  uint16_t conn_int_max;
  uint16_t conn_latency;
  uint16_t sup_timeout;
  uint16_t min_ce_len;
  uint16_t max_ce_len;
};

void btsnd_hcic_ble_read_resolvable_addr_peer(uint8_t addr_type_peer,
                                              const RawAddress& bda_peer);

void btsnd_hcic_ble_set_rand_priv_addr_timeout(uint16_t rpa_timout);

void btsnd_hcic_read_authenticated_payload_tout(uint16_t handle);

void btsnd_hcic_write_authenticated_payload_tout(uint16_t handle,
                                                 uint16_t timeout);

struct EXT_CIS_CFG {
  uint8_t cis_id;
  uint16_t max_sdu_size_mtos;
  uint16_t max_sdu_size_stom;
  uint8_t phy_mtos;
  uint8_t phy_stom;
  uint8_t rtn_mtos;
  uint8_t rtn_stom;
};

void btsnd_hcic_set_cig_params(uint8_t cig_id, uint32_t sdu_itv_mtos,
                               uint32_t sdu_itv_stom, uint8_t sca,
                               uint8_t packing, uint8_t framing,
                               uint16_t max_trans_lat_stom,
                               uint16_t max_trans_lat_mtos, uint8_t cis_cnt,
                               const EXT_CIS_CFG* cis_cfg,
                               base::OnceCallback<void(uint8_t*, uint16_t)> cb);

struct EXT_CIS_TEST_CFG {
  uint8_t cis_id;
  uint8_t nse;
  uint16_t max_sdu_size_mtos;
  uint16_t max_sdu_size_stom;
  uint8_t max_pdu_mtos;
  uint8_t max_pdu_stom;
  uint8_t phy_mtos;
  uint8_t phy_stom;
  uint8_t bn_mtos;
  uint8_t bn_stom;
};

struct EXT_CIS_CREATE_CFG {
  uint16_t cis_conn_handle;
  uint16_t acl_conn_handle;
};

void btsnd_hcic_create_cis(uint8_t num_cis,
                           const EXT_CIS_CREATE_CFG* cis_create_cfg,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_remove_cig(uint8_t cig_id,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_accept_cis_req(uint16_t conn_handle);

void btsnd_hcic_rej_cis_req(uint16_t conn_handle, uint8_t reason,
                            base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_req_peer_sca(uint16_t conn_handle);

void btsnd_hcic_create_big(uint8_t big_handle, uint8_t adv_handle,
                           uint8_t num_bis, uint32_t sdu_itv,
                           uint16_t max_sdu_size, uint16_t max_trans_lat,
                           uint8_t rtn, uint8_t phy, uint8_t packing,
                           uint8_t framing, uint8_t enc,
                           std::array<uint8_t, 16> bcst_code);

void btsnd_hcic_term_big(uint8_t big_handle, uint8_t reason);

void btsnd_hcic_setup_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
    uint8_t codec_id_format, uint16_t codec_id_company,
    uint16_t codec_id_vendor, uint32_t controller_delay,
    std::vector<uint8_t> codec_conf,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_remove_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_read_iso_link_quality(
    uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_periodic_advertising_create_sync(
    uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
    const RawAddress& adv_addr, uint16_t skip_num, uint16_t sync_timeout,
    uint8_t sync_cte_type);

void btsnd_hcic_ble_periodic_advertising_create_sync_cancel(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_periodic_advertising_terminate_sync(
    uint16_t sync_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hci_ble_add_device_to_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hci_ble_clear_periodic_advertiser_list(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_set_periodic_advertising_receive_enable(
    uint16_t sync_handle, bool enable,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_periodic_advertising_sync_transfer(
    uint16_t conn_handle, uint16_t service_data, uint16_t sync_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_periodic_advertising_set_info_transfer(
    uint16_t conn_handle, uint16_t service_data, uint8_t adv_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb);

void btsnd_hcic_configure_data_path(hci_data_direction_t data_path_direction,
                                    uint8_t data_path_id,
                                    std::vector<uint8_t> vendor_config);

#endif
