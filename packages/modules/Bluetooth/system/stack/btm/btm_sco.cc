/******************************************************************************
 *
 *  Copyright 2000-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  This file contains functions that handle SCO connections. This includes
 *  operations such as connect, disconnect, change supported packet types.
 *
 ******************************************************************************/

#define LOG_TAG "btm_sco"

#include "stack/btm/btm_sco.h"

#include <base/logging.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "common/bidi_queue.h"
#include "device/include/controller.h"
#include "device/include/device_iot_config.h"
#include "hci/class_of_device.h"
#include "hci/controller_interface.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "hci/include/hci_layer.h"
#include "internal_include/bt_target.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "osi/include/properties.h"
#include "osi/include/stack_power_telemetry.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sco_hfp_hal.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcimsgs.h"
#include "stack/include/main_thread.h"
#include "stack/include/sdpdefs.h"
#include "stack/include/stack_metrics_logging.h"
#include "types/raw_address.h"

extern tBTM_CB btm_cb;

/* Default to allow enhanced connections where supported. */
constexpr bool kDefaultDisableEnhancedConnection = false;

/* Sysprops for SCO connection. */
static const char kPropertyDisableEnhancedConnection[] =
    "bluetooth.sco.disable_enhanced_connection";

namespace {

/* Structure passed with SCO change command and events.
 * Used by both Sync and Enhanced sync messaging
 */
typedef struct {
  uint16_t max_latency_ms;
  uint16_t packet_types;
  uint8_t retransmission_effort;
} tBTM_CHG_ESCO_PARAMS;

constexpr char kBtmLogTag[] = "SCO";

};  // namespace

using namespace bluetooth;
using bluetooth::legacy::hci::GetInterface;

// forward declaration for dequeueing packets
static void btm_route_sco_data(bluetooth::hci::ScoView valid_packet);
void btm_sco_conn_req(const RawAddress& bda, const DEV_CLASS& dev_class,
                      uint8_t link_type);
void btm_sco_on_disconnected(uint16_t hci_handle, tHCI_REASON reason);
bool btm_sco_removed(uint16_t hci_handle, tHCI_REASON reason);

namespace cpp {
bluetooth::common::BidiQueueEnd<bluetooth::hci::ScoBuilder,
                                bluetooth::hci::ScoView>* hci_sco_queue_end =
    nullptr;
static bluetooth::os::EnqueueBuffer<bluetooth::hci::ScoBuilder>*
    pending_sco_data = nullptr;

static void sco_data_callback() {
  if (hci_sco_queue_end == nullptr) {
    return;
  }
  auto packet = hci_sco_queue_end->TryDequeue();
  ASSERT(packet != nullptr);
  if (!packet->IsValid()) {
    log::info("Dropping invalid packet of size {}", packet->size());
    return;
  }
  if (do_in_main_thread(FROM_HERE, base::Bind(&btm_route_sco_data, *packet)) !=
      BT_STATUS_SUCCESS) {
    log::error("do_in_main_thread failed from sco_data_callback");
  }
}
static void register_for_sco() {
  hci_sco_queue_end = bluetooth::shim::GetHciLayer()->GetScoQueueEnd();
  hci_sco_queue_end->RegisterDequeue(
      bluetooth::shim::GetGdShimHandler(),
      bluetooth::common::Bind(sco_data_callback));
  pending_sco_data =
      new bluetooth::os::EnqueueBuffer<bluetooth::hci::ScoBuilder>(
          hci_sco_queue_end);

  // Register SCO for connection requests
  bluetooth::shim::GetHciLayer()->RegisterForScoConnectionRequests(
      get_main_thread()->Bind(
          [](bluetooth::hci::Address peer, bluetooth::hci::ClassOfDevice cod,
             bluetooth::hci::ConnectionRequestLinkType link_type) {
            auto peer_raw_address = bluetooth::ToRawAddress(peer);
            DEV_CLASS dev_class{cod.cod[0], cod.cod[1], cod.cod[2]};
            if (link_type == bluetooth::hci::ConnectionRequestLinkType::ESCO) {
              btm_sco_conn_req(peer_raw_address, dev_class,
                               android::bluetooth::LINK_TYPE_ESCO);
            } else {
              btm_sco_conn_req(peer_raw_address, dev_class,
                               android::bluetooth::LINK_TYPE_SCO);
            }
          }));
  // Register SCO for disconnect notifications
  bluetooth::shim::GetHciLayer()->RegisterForDisconnects(
      get_main_thread()->Bind(
          [](uint16_t handle, bluetooth::hci::ErrorCode error_code) {
            auto reason = static_cast<tHCI_REASON>(error_code);
            btm_sco_on_disconnected(handle, reason);
            btm_sco_removed(handle, reason);
          }));
}

static void shut_down_sco() {
  if (pending_sco_data != nullptr) {
    pending_sco_data->Clear();
    delete pending_sco_data;
    pending_sco_data = nullptr;
  }
  if (hci_sco_queue_end != nullptr) {
    hci_sco_queue_end->UnregisterDequeue();
    hci_sco_queue_end = nullptr;
  }
}
};  // namespace cpp

void tSCO_CB::Init() {
  hfp_hal_interface::init();
  def_esco_parms = esco_parameters_for_codec(
      ESCO_CODEC_CVSD_S3, hfp_hal_interface::get_offload_enabled());
  cpp::register_for_sco();
}

void tSCO_CB::Free() {
  cpp::shut_down_sco();
  bluetooth::audio::sco::cleanup();
}
/******************************************************************************/
/*               L O C A L    D A T A    D E F I N I T I O N S                */
/******************************************************************************/

/* MACROs to convert from SCO packet types mask to ESCO and back */
#define BTM_SCO_PKT_TYPE_MASK \
  (HCI_PKT_TYPES_MASK_HV1 | HCI_PKT_TYPES_MASK_HV2 | HCI_PKT_TYPES_MASK_HV3)

/* Mask defining only the SCO types of an esco packet type */
#define BTM_ESCO_PKT_TYPE_MASK \
  (ESCO_PKT_TYPES_MASK_HV1 | ESCO_PKT_TYPES_MASK_HV2 | ESCO_PKT_TYPES_MASK_HV3)

#define BTM_ESCO_2_SCO(escotype) \
  ((uint16_t)(((escotype)&BTM_ESCO_PKT_TYPE_MASK) << 5))

/* Define masks for supported and exception 2.0 SCO packet types */
#define BTM_SCO_SUPPORTED_PKTS_MASK                    \
  (ESCO_PKT_TYPES_MASK_HV1 | ESCO_PKT_TYPES_MASK_HV2 | \
   ESCO_PKT_TYPES_MASK_HV3 | ESCO_PKT_TYPES_MASK_EV3 | \
   ESCO_PKT_TYPES_MASK_EV4 | ESCO_PKT_TYPES_MASK_EV5)

#define BTM_SCO_EXCEPTION_PKTS_MASK                              \
  (ESCO_PKT_TYPES_MASK_NO_2_EV3 | ESCO_PKT_TYPES_MASK_NO_3_EV3 | \
   ESCO_PKT_TYPES_MASK_NO_2_EV5 | ESCO_PKT_TYPES_MASK_NO_3_EV5)

/* Buffer used for reading PCM data from audio server that will be encoded into
 * mSBC packet. The BTM_SCO_DATA_SIZE_MAX should be set to a number divisible by
 * BTM_MSBC_CODE_SIZE(240) */
static int16_t btm_pcm_buf[BTM_SCO_DATA_SIZE_MAX] = {0};

/* The read and write offset for btm_pcm_buf.
 * They are only used for WBS and the unit is byte. */
static size_t btm_pcm_buf_read_offset = 0;
static size_t btm_pcm_buf_write_offset = 0;
/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static tBTM_STATUS BTM_ChangeEScoLinkParms(uint16_t sco_inx,
                                           tBTM_CHG_ESCO_PARAMS* p_parms);

static uint16_t btm_sco_voice_settings_to_legacy(enh_esco_params_t* p_parms);

/*******************************************************************************
 *
 * Function         btm_esco_conn_rsp
 *
 * Description      This function is called upon receipt of an (e)SCO connection
 *                  request event (BTM_ESCO_CONN_REQ_EVT) to accept or reject
 *                  the request. Parameters used to negotiate eSCO links.
 *                  If p_parms is NULL, then default values are used.
 *                  If the link type of the incoming request is SCO, then only
 *                  the tx_bw, max_latency, content format, and packet_types are
 *                  valid.  The hci_status parameter should be
 *                  ([0x0] to accept, [0x0d..0x0f] to reject)
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_esco_conn_rsp(uint16_t sco_inx, uint8_t hci_status,
                              const RawAddress& bda,
                              enh_esco_params_t* p_parms) {
  tSCO_CONN* p_sco = NULL;

  if (BTM_MAX_SCO_LINKS == 0) return;

  if (sco_inx < BTM_MAX_SCO_LINKS) p_sco = &btm_cb.sco_cb.sco_db[sco_inx];

  /* Reject the connect request if refused by caller or wrong state */
  if (hci_status != HCI_SUCCESS || p_sco == NULL) {
    if (p_sco) {
      p_sco->state = (p_sco->state == SCO_ST_W4_CONN_RSP) ? SCO_ST_LISTENING
                                                          : SCO_ST_UNUSED;
    }
    if (!btm_cb.sco_cb.esco_supported) {
      btsnd_hcic_reject_conn(bda, hci_status);
    } else {
      btsnd_hcic_reject_esco_conn(bda, hci_status);
    }
  } else {
    /* Connection is being accepted */
    p_sco->state = SCO_ST_CONNECTING;
    enh_esco_params_t* p_setup = &p_sco->esco.setup;
    /* If parameters not specified use the default */
    if (p_parms) {
      *p_setup = *p_parms;
    } else {
      /* Use the last setup passed thru BTM_SetEscoMode (or defaults) */
      *p_setup = btm_cb.sco_cb.def_esco_parms;
    }
    /* Use Enhanced Synchronous commands if supported */
    if (controller_get_interface()
            ->supports_enhanced_setup_synchronous_connection() &&
        !osi_property_get_bool(kPropertyDisableEnhancedConnection,
                               kDefaultDisableEnhancedConnection)) {
      log::verbose(
          "txbw 0x{:x}, rxbw 0x{:x}, lat 0x{:x}, retrans 0x{:02x}, pkt "
          "0x{:04x}, path {}",
          p_setup->transmit_bandwidth, p_setup->receive_bandwidth,
          p_setup->max_latency_ms, p_setup->retransmission_effort,
          p_setup->packet_types, p_setup->input_data_path);

      btsnd_hcic_enhanced_accept_synchronous_connection(bda, p_setup);

    } else {
      /* Use legacy command if enhanced SCO setup is not supported */
      uint16_t voice_content_format = btm_sco_voice_settings_to_legacy(p_setup);
      btsnd_hcic_accept_esco_conn(
          bda, p_setup->transmit_bandwidth, p_setup->receive_bandwidth,
          p_setup->max_latency_ms, voice_content_format,
          p_setup->retransmission_effort, p_setup->packet_types);
    }
  }
}

/* Return the active (first connected) SCO connection block */
static tSCO_CONN* btm_get_active_sco() {
  for (auto& link : btm_cb.sco_cb.sco_db) {
    if (link.state == SCO_ST_CONNECTED) {
      return &link;
    }
  }
  return nullptr;
}

/*******************************************************************************
 *
 * Function         btm_route_sco_data
 *
 * Description      Route received SCO data.
 *                  This function is triggered when we receive a packet of SCO
 *                  data. It regards the received SCO packet as a clock tick to
 *                  start the write and read to and from the audio server. It
 *                  also tries to balance the write/read data rate between the
 *                  Bluetooth and Audio stack by sending and receiving the same
 *                  amount of PCM data to and from the audio server.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_route_sco_data(bluetooth::hci::ScoView valid_packet) {
  uint16_t handle = valid_packet.GetHandle();
  if (handle > HCI_HANDLE_MAX) {
    log::error("Dropping SCO data with invalid handle: 0x{:X} > 0x{:X}, ",
               handle, HCI_HANDLE_MAX);
    return;
  }

  tSCO_CONN* active_sco = btm_get_active_sco();
  if (active_sco == nullptr) {
    log::error("Received SCO data when there is no active SCO connection");
    return;
  }
  if (active_sco->hci_handle != handle) {
    log::error("Dropping packet with handle(0x{:X}) != active handle(0x{:X})",
               handle, active_sco->hci_handle);
    return;
  }

  const auto codec_type = active_sco->get_codec_type();
  const std::string codec = sco_codec_type_text(codec_type);

  auto data = valid_packet.GetData();
  auto rx_data = data.data();
  const uint8_t* decoded = nullptr;
  size_t written = 0, rc = 0;
  if (codec_type == BTM_SCO_CODEC_MSBC || codec_type == BTM_SCO_CODEC_LC3) {
    auto status = valid_packet.GetPacketStatusFlag();

    if (status != bluetooth::hci::PacketStatusFlag::CORRECTLY_RECEIVED) {
      log::debug("{} packet corrupted with status({})", codec.c_str(),
                 PacketStatusFlagText(status).c_str());
    }
    auto enqueue_packet = codec_type == BTM_SCO_CODEC_LC3
                              ? &bluetooth::audio::sco::swb::enqueue_packet
                              : &bluetooth::audio::sco::wbs::enqueue_packet;
    rc = enqueue_packet(
        data, status != bluetooth::hci::PacketStatusFlag::CORRECTLY_RECEIVED);
    if (!rc) log::debug("Failed to enqueue {} packet", codec.c_str());

    while (rc) {
      auto decode = codec_type == BTM_SCO_CODEC_LC3
                        ? &bluetooth::audio::sco::swb::decode
                        : &bluetooth::audio::sco::wbs::decode;
      rc = decode(&decoded);
      if (rc == 0) break;

      written += bluetooth::audio::sco::write(decoded, rc);
    }
  } else {
    written = bluetooth::audio::sco::write(rx_data, data.size());
  }

  /* For Chrome OS, we send the outgoing data after receiving an incoming one.
   * server, so that we can keep the data read/write rate balanced */
  size_t read = 0, avail = 0;
  const uint8_t* encoded = nullptr;
  if (codec_type == BTM_SCO_CODEC_MSBC || codec_type == BTM_SCO_CODEC_LC3) {
    while (written) {
      avail = BTM_SCO_DATA_SIZE_MAX - btm_pcm_buf_write_offset;
      if (avail) {
        size_t to_read = written < avail ? written : avail;
        read = bluetooth::audio::sco::read(
            (uint8_t*)btm_pcm_buf + btm_pcm_buf_write_offset, to_read);
        if (read != to_read) {
          ASSERT_LOG(btm_pcm_buf_write_offset + read <= BTM_SCO_DATA_SIZE_MAX,
                     "Read more %s data (%lu) than available buffer (%lu) "
                     "guarded by read",
                     codec.c_str(), (unsigned long)read,
                     (unsigned long)(BTM_SCO_DATA_SIZE_MAX -
                                     btm_pcm_buf_write_offset));

          log::info(
              "Requested to read {} bytes of {} data but got {} bytes of PCM "
              "data from audio server: WriteOffset:{} ReadOffset:{}",
              (unsigned long)to_read, codec.c_str(), (unsigned long)read,
              (unsigned long)btm_pcm_buf_write_offset,
              (unsigned long)btm_pcm_buf_read_offset);
          if (read == 0) break;
        }

        written -= read;
      } else {
        /* We don't break here so that we can still decode the data in the
         * buffer to spare the buffer space when the buffer is full */
        log::warn(
            "Buffer is full when we try to read {} packet from audio server",
            codec.c_str());
        ASSERT_LOG(btm_pcm_buf_write_offset - btm_pcm_buf_read_offset >=
                       (codec_type == BTM_SCO_CODEC_MSBC ? BTM_MSBC_CODE_SIZE
                                                         : BTM_LC3_CODE_SIZE),
                   "PCM buffer is full but fails to encode an %s packet. "
                   "This is abnormal and can cause busy loop: "
                   "WriteOffset:%lu, ReadOffset:%lu, BufferSize:%lu",
                   codec.c_str(), (unsigned long)btm_pcm_buf_write_offset,
                   (unsigned long)btm_pcm_buf_read_offset,
                   (unsigned long)sizeof(btm_pcm_buf));
      }

      btm_pcm_buf_write_offset += read;

      auto encode = codec_type == BTM_SCO_CODEC_LC3
                        ? &bluetooth::audio::sco::swb::encode
                        : &bluetooth::audio::sco::wbs::encode;
      rc = encode(&btm_pcm_buf[btm_pcm_buf_read_offset / sizeof(*btm_pcm_buf)],
                  btm_pcm_buf_write_offset - btm_pcm_buf_read_offset);

      if (!rc)
        log::debug(
            "Failed to encode {} data starting at ReadOffset:{} to "
            "WriteOffset:{}",
            codec.c_str(), (unsigned long)btm_pcm_buf_read_offset,
            (unsigned long)btm_pcm_buf_write_offset);

      /* The offsets should reset some time as the buffer length should always
       * divisible by 480 and `encode` only returns 480(LC3), 240(MSBC), or 0.
       */
      btm_pcm_buf_read_offset += rc;
      if (btm_pcm_buf_write_offset == btm_pcm_buf_read_offset) {
        btm_pcm_buf_write_offset = 0;
        btm_pcm_buf_read_offset = 0;
      }

      /* Send all of the available SCO packets buffered in the queue */
      while (1) {
        auto dequeue_packet = codec_type == BTM_SCO_CODEC_LC3
                                  ? &bluetooth::audio::sco::swb::dequeue_packet
                                  : &bluetooth::audio::sco::wbs::dequeue_packet;
        rc = dequeue_packet(&encoded);
        if (!rc) break;

        auto data = std::vector<uint8_t>(encoded, encoded + rc);
        btm_send_sco_packet(std::move(data));
      }
    }
  } else {
    while (written) {
      read = bluetooth::audio::sco::read(
          (uint8_t*)btm_pcm_buf,
          written < BTM_SCO_DATA_SIZE_MAX ? written : BTM_SCO_DATA_SIZE_MAX);
      if (read == 0) {
        log::info("Failed to read {} bytes of PCM data from audio server",
                  (unsigned long)(written < BTM_SCO_DATA_SIZE_MAX
                                      ? written
                                      : BTM_SCO_DATA_SIZE_MAX));
        break;
      }
      written -= read;

      /* In narrow-band, the CVSD encode is offloaded to controller so we can
       * send PCM data directly to SCO.
       * We don't maintain buffer read/write offset for NB as we send all data
       * that we read from the audio server. */
      auto data = std::vector<uint8_t>((uint8_t*)btm_pcm_buf,
                                       (uint8_t*)btm_pcm_buf + read);
      btm_send_sco_packet(std::move(data));
    }
  }
}

void btm_send_sco_packet(std::vector<uint8_t> data) {
  auto* active_sco = btm_get_active_sco();
  if (active_sco == nullptr || data.empty()) {
    return;
  }
  ASSERT_LOG(data.size() <= BTM_SCO_DATA_SIZE_MAX, "Invalid SCO data size: %lu",
             (unsigned long)data.size());

  uint16_t handle_with_flags = active_sco->hci_handle;
  uint16_t handle = HCID_GET_HANDLE(handle_with_flags);
  ASSERT_LOG(handle <= HCI_HANDLE_MAX, "Require handle <= 0x%X, but is 0x%X",
             HCI_HANDLE_MAX, handle);

  auto sco_packet = bluetooth::hci::ScoBuilder::Create(
      handle, bluetooth::hci::PacketStatusFlag::CORRECTLY_RECEIVED,
      std::move(data));

  cpp::pending_sco_data->Enqueue(std::move(sco_packet),
                                 bluetooth::shim::GetGdShimHandler());
}

/*******************************************************************************
 *
 * Function         btm_send_connect_request
 *
 * Description      This function is called to respond to SCO connect
 *                  indications
 *
 * Returns          void
 *
 ******************************************************************************/
static tBTM_STATUS btm_send_connect_request(uint16_t acl_handle,
                                            enh_esco_params_t* p_setup) {
  /* Send connect request depending on version of spec */
  if (!btm_cb.sco_cb.esco_supported) {
    log::info("sending non-eSCO request for handle={}", unsigned(acl_handle));
    btsnd_hcic_add_SCO_conn(acl_handle, BTM_ESCO_2_SCO(p_setup->packet_types));
  } else {
    /* Save the previous values in case command fails */
    uint16_t saved_packet_types = p_setup->packet_types;
    uint8_t saved_retransmission_effort = p_setup->retransmission_effort;
    uint16_t saved_max_latency_ms = p_setup->max_latency_ms;

    uint16_t temp_packet_types =
        (p_setup->packet_types &
         static_cast<uint16_t>(BTM_SCO_SUPPORTED_PKTS_MASK) &
         btm_cb.btm_sco_pkt_types_supported);

    /* OR in any exception packet types */
    temp_packet_types |=
        ((p_setup->packet_types & BTM_SCO_EXCEPTION_PKTS_MASK) |
         (btm_cb.btm_sco_pkt_types_supported & BTM_SCO_EXCEPTION_PKTS_MASK));

    /* Finally, remove EDR eSCO if the remote device doesn't support it */
    /* UPF25:  Only SCO was brought up in this case */
    const RawAddress bd_addr = acl_address_from_handle(acl_handle);
    if (bd_addr != RawAddress::kEmpty) {
      if (!btm_peer_supports_esco_2m_phy(bd_addr)) {
        log::verbose("BTM Remote does not support 2-EDR eSCO");
        temp_packet_types |=
            (ESCO_PKT_TYPES_MASK_NO_2_EV3 | ESCO_PKT_TYPES_MASK_NO_2_EV5);
      }
      if (!btm_peer_supports_esco_3m_phy(bd_addr)) {
        log::verbose("BTM Remote does not support 3-EDR eSCO");
        temp_packet_types |=
            (ESCO_PKT_TYPES_MASK_NO_3_EV3 | ESCO_PKT_TYPES_MASK_NO_3_EV5);
      }
      if (!btm_peer_supports_esco_ev3(bd_addr)) {
        log::verbose("BTM Remote does not support EV3 eSCO");
        // If EV3 is not supported, EV4 and EV% are not supported, either.
        temp_packet_types &= ~BTM_ESCO_LINK_ONLY_MASK;
        p_setup->retransmission_effort = ESCO_RETRANSMISSION_OFF;
        p_setup->max_latency_ms = 10;
      }

      /* Check to see if BR/EDR Secure Connections is being used
      ** If so, we cannot use SCO-only packet types (HFP 1.7)
      */
      const bool local_supports_sc =
          bluetooth::shim::GetController()->SupportsSecureConnections();
      const bool remote_supports_sc =
          BTM_PeerSupportsSecureConnections(bd_addr);

      if (local_supports_sc && remote_supports_sc) {
        temp_packet_types &= ~(BTM_SCO_PKT_TYPE_MASK);
        if (temp_packet_types == 0) {
          log::error(
              "SCO connection cannot support any packet types for "
              "acl_handle:0x{:04x}",
              acl_handle);
          return BTM_WRONG_MODE;
        }
        log::debug(
            "Both local and remote controllers support SCO secure connections "
            "handle:0x{:04x} pkt_types:0x{:04x}",
            acl_handle, temp_packet_types);

      } else if (!local_supports_sc && !remote_supports_sc) {
        log::debug(
            "Both local and remote controllers do not support secure "
            "connections for handle:0x{:04x}",
            acl_handle);
      } else if (remote_supports_sc) {
        log::debug(
            "Only remote controller supports secure connections for "
            "handle:0x{:04x}",
            acl_handle);
      } else {
        log::debug(
            "Only local controller supports secure connections for "
            "handle:0x{:04x}",
            acl_handle);
      }
    } else {
      log::error("Received SCO connect from unknown peer:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }

    p_setup->packet_types = temp_packet_types;

    /* Use Enhanced Synchronous commands if supported */
    if (controller_get_interface()
            ->supports_enhanced_setup_synchronous_connection() &&
        !osi_property_get_bool(kPropertyDisableEnhancedConnection,
                               kDefaultDisableEnhancedConnection)) {
      log::info("Sending enhanced SCO connect request over handle:0x{:04x}",
                acl_handle);
      log::info(
          "enhanced parameter list txbw=0x{:x}, rxbw=0x{}, latency_ms=0x{}, "
          "retransmit_effort=0x{}, pkt_type=0x{}, path=0x{}",
          unsigned(p_setup->transmit_bandwidth),
          unsigned(p_setup->receive_bandwidth),
          unsigned(p_setup->max_latency_ms),
          unsigned(p_setup->retransmission_effort),
          unsigned(p_setup->packet_types), unsigned(p_setup->input_data_path));
      btsnd_hcic_enhanced_set_up_synchronous_connection(acl_handle, p_setup);
      p_setup->packet_types = saved_packet_types;
      p_setup->retransmission_effort = saved_retransmission_effort;
      p_setup->max_latency_ms = saved_max_latency_ms;
    } else { /* Use older command */
      log::info("Sending eSCO connect request over handle:0x{:04x}",
                acl_handle);
      uint16_t voice_content_format = btm_sco_voice_settings_to_legacy(p_setup);
      log::info(
          "legacy parameter list txbw=0x{:x}, rxbw=0x{}, latency_ms=0x{}, "
          "retransmit_effort=0x{}, voice_content_format=0x{}, pkt_type=0x{}",
          unsigned(p_setup->transmit_bandwidth),
          unsigned(p_setup->receive_bandwidth),
          unsigned(p_setup->max_latency_ms),
          unsigned(p_setup->retransmission_effort),
          unsigned(voice_content_format), unsigned(p_setup->packet_types));
      btsnd_hcic_setup_esco_conn(
          acl_handle, p_setup->transmit_bandwidth, p_setup->receive_bandwidth,
          p_setup->max_latency_ms, voice_content_format,
          p_setup->retransmission_effort, p_setup->packet_types);
    }
  }

  return (BTM_CMD_STARTED);
}

/*******************************************************************************
 *
 * Function         BTM_CreateSco
 *
 * Description      This function is called to create an SCO connection. If the
 *                  "is_orig" flag is true, the connection will be originated,
 *                  otherwise BTM will wait for the other side to connect.
 *
 *                  NOTE:  If BTM_IGNORE_SCO_PKT_TYPE is passed in the pkt_types
 *                      parameter the default packet types is used.
 *
 * Returns          BTM_UNKNOWN_ADDR if the ACL connection is not up
 *                  BTM_BUSY         if another SCO being set up to
 *                                   the same BD address
 *                  BTM_NO_RESOURCES if the max SCO limit has been reached
 *                  BTM_CMD_STARTED  if the connection establishment is started.
 *                                   In this case, "*p_sco_inx" is filled in
 *                                   with the sco index used for the connection.
 *
 ******************************************************************************/
tBTM_STATUS BTM_CreateSco(const RawAddress* remote_bda, bool is_orig,
                          uint16_t pkt_types, uint16_t* p_sco_inx,
                          tBTM_SCO_CB* p_conn_cb, tBTM_SCO_CB* p_disc_cb) {
  enh_esco_params_t* p_setup;
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;
  uint16_t acl_handle = HCI_INVALID_HANDLE;
  *p_sco_inx = BTM_INVALID_SCO_INDEX;

  if (BTM_MAX_SCO_LINKS == 0) {
    return BTM_NO_RESOURCES;
  }

  /* If originating, ensure that there is an ACL connection to the BD Address */

  if (is_orig) {
    if (!remote_bda) {
      log::error("remote_bda is null");
      return BTM_ILLEGAL_VALUE;
    }
    acl_handle = BTM_GetHCIConnHandle(*remote_bda, BT_TRANSPORT_BR_EDR);
    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("cannot find ACL handle for remote device {}",
                 ADDRESS_TO_LOGGABLE_STR(*remote_bda));
      return BTM_UNKNOWN_ADDR;
    }
  }

  if (remote_bda) {
    /* If any SCO is being established to the remote BD address, refuse this */
    for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
      if (((p->state == SCO_ST_CONNECTING) || (p->state == SCO_ST_LISTENING) ||
           (p->state == SCO_ST_PEND_UNPARK)) &&
          (p->esco.data.bd_addr == *remote_bda)) {
        log::error("a sco connection is already going on for {}, at state {}",
                   ADDRESS_TO_LOGGABLE_STR(*remote_bda), unsigned(p->state));
        return BTM_BUSY;
      }
    }
  } else {
    /* Support only 1 wildcard BD address at a time */
    for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
      if ((p->state == SCO_ST_LISTENING) && (!p->rem_bd_known)) {
        log::error(
            "remote_bda is null and not known and we are still listening");
        return BTM_BUSY;
      }
    }
  }

  /* Try to find an unused control block, and kick off the SCO establishment */
  for (xx = 0, p = &btm_cb.sco_cb.sco_db[0]; xx < BTM_MAX_SCO_LINKS;
       xx++, p++) {
    if (p->state == SCO_ST_UNUSED) {
      if (remote_bda) {
        if (is_orig) {
          // can not create SCO link if in park mode
          tBTM_PM_STATE state;
          if (BTM_ReadPowerMode(*remote_bda, &state)) {
            if (state == BTM_PM_ST_SNIFF || state == BTM_PM_ST_PARK ||
                state == BTM_PM_ST_PENDING) {
              log::info("{} in sniff, park or pending mode {}",
                        ADDRESS_TO_LOGGABLE_STR(*remote_bda), unsigned(state));
              if (!BTM_SetLinkPolicyActiveMode(*remote_bda)) {
                log::warn("Unable to set link policy active");
              }
              p->state = SCO_ST_PEND_UNPARK;
            }
          } else {
            log::error("failed to read power mode for {}",
                       ADDRESS_TO_LOGGABLE_STR(*remote_bda));
          }
        }
        p->esco.data.bd_addr = *remote_bda;
        p->rem_bd_known = true;
      } else
        p->rem_bd_known = false;

      p_setup = &p->esco.setup;
      *p_setup = btm_cb.sco_cb.def_esco_parms;

      /* Determine the packet types */
      p_setup->packet_types = pkt_types & BTM_SCO_SUPPORTED_PKTS_MASK &
                              btm_cb.btm_sco_pkt_types_supported;
      /* OR in any exception packet types */
      if (controller_get_interface()->get_bt_version()->hci_version >=
          HCI_PROTO_VERSION_2_0) {
        p_setup->packet_types |=
            (pkt_types & BTM_SCO_EXCEPTION_PKTS_MASK) |
            (btm_cb.btm_sco_pkt_types_supported & BTM_SCO_EXCEPTION_PKTS_MASK);
      }

      p->p_conn_cb = p_conn_cb;
      p->p_disc_cb = p_disc_cb;
      p->hci_handle = HCI_INVALID_HANDLE;
      p->is_orig = is_orig;

      if (p->state != SCO_ST_PEND_UNPARK) {
        if (is_orig) {
          /* If role change is in progress, do not proceed with SCO setup
           * Wait till role change is complete */
          if (!acl_is_switch_role_idle(*remote_bda, BT_TRANSPORT_BR_EDR)) {
            log::verbose("Role Change is in progress for ACL handle 0x{:04x}",
                         acl_handle);
            p->state = SCO_ST_PEND_ROLECHANGE;
          }
        }
      }

      if (p->state != SCO_ST_PEND_UNPARK &&
          p->state != SCO_ST_PEND_ROLECHANGE) {
        if (is_orig) {
          log::debug("Initiating (e)SCO link for ACL handle:0x{:04x}",
                     acl_handle);

          if ((btm_send_connect_request(acl_handle, p_setup)) !=
              BTM_CMD_STARTED) {
            log::error("failed to send connect request for {}",
                       ADDRESS_TO_LOGGABLE_STR(*remote_bda));
            return (BTM_NO_RESOURCES);
          }

          p->state = SCO_ST_CONNECTING;
        } else {
          log::debug("Listening for (e)SCO on ACL handle:0x{:04x}", acl_handle);
          p->state = SCO_ST_LISTENING;
        }
      }

      *p_sco_inx = xx;
      log::debug("SCO connection successfully requested");
      if (p->state == SCO_ST_CONNECTING) {
        BTM_LogHistory(
            kBtmLogTag, *remote_bda, "Connecting",
            base::StringPrintf("local initiated acl:0x%04x", acl_handle));
      }
      return BTM_CMD_STARTED;
    }
  }

  /* If here, all SCO blocks in use */
  log::error("all SCO control blocks are in use");
  return BTM_NO_RESOURCES;
}

/*******************************************************************************
 *
 * Function         btm_sco_chk_pend_unpark
 *
 * Description      This function is called by BTIF when there is a mode change
 *                  event to see if there are SCO commands waiting for the
 *                  unpark.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_chk_pend_unpark(tHCI_STATUS hci_status, uint16_t hci_handle) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  for (uint16_t xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    uint16_t acl_handle =
        BTM_GetHCIConnHandle(p->esco.data.bd_addr, BT_TRANSPORT_BR_EDR);
    if ((p->state == SCO_ST_PEND_UNPARK) && (acl_handle == hci_handle)) {
      log::info(
          "{} unparked, sending connection request, acl_handle={}, "
          "hci_status={}",
          ADDRESS_TO_LOGGABLE_STR(p->esco.data.bd_addr), unsigned(acl_handle),
          unsigned(hci_status));
      if (btm_send_connect_request(acl_handle, &p->esco.setup) ==
          BTM_CMD_STARTED) {
        p->state = SCO_ST_CONNECTING;
      } else {
        log::error("failed to send connection request for {}",
                   ADDRESS_TO_LOGGABLE_STR(p->esco.data.bd_addr));
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sco_chk_pend_rolechange
 *
 * Description      This function is called by BTIF when there is a role change
 *                  event to see if there are SCO commands waiting for the role
 *                  change.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_chk_pend_rolechange(uint16_t hci_handle) {
  uint16_t xx;
  uint16_t acl_handle;
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if ((p->state == SCO_ST_PEND_ROLECHANGE) &&
        ((acl_handle = BTM_GetHCIConnHandle(
              p->esco.data.bd_addr, BT_TRANSPORT_BR_EDR)) == hci_handle))

    {
      log::verbose(
          "btm_sco_chk_pend_rolechange -> (e)SCO Link for ACL handle 0x{:04x}",
          acl_handle);

      if ((btm_send_connect_request(acl_handle, &p->esco.setup)) ==
          BTM_CMD_STARTED)
        p->state = SCO_ST_CONNECTING;
    }
  }
}

/*******************************************************************************
 *
 * Function        btm_sco_disc_chk_pend_for_modechange
 *
 * Description     This function is called by btm when there is a mode change
 *                 event to see if there are SCO  disconnect commands waiting
 *                 for the mode change.
 *
 * Returns         void
 *
 ******************************************************************************/
void btm_sco_disc_chk_pend_for_modechange(uint16_t hci_handle) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];

  log::debug(
      "Checking for SCO pending mode change events hci_handle:0x{:04x} "
      "p->state:{}",
      hci_handle, sco_state_text(p->state).c_str());

  for (uint16_t xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if ((p->state == SCO_ST_PEND_MODECHANGE) &&
        (BTM_GetHCIConnHandle(p->esco.data.bd_addr, BT_TRANSPORT_BR_EDR)) ==
            hci_handle)

    {
      log::debug("Removing SCO Link handle 0x{:04x}", p->hci_handle);
      BTM_RemoveSco(xx);
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sco_conn_req
 *
 * Description      This function is called by BTU HCIF when an SCO connection
 *                  request is received from a remote.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_conn_req(const RawAddress& bda, const DEV_CLASS& dev_class,
                      uint8_t link_type) {
  tSCO_CB* p_sco = &btm_cb.sco_cb;
  tSCO_CONN* p = &p_sco->sco_db[0];
  tBTM_ESCO_CONN_REQ_EVT_DATA evt_data = {};

  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(bda, IOT_CONF_KEY_HFP_SCO_CONN_COUNT);

  for (uint16_t sco_index = 0; sco_index < BTM_MAX_SCO_LINKS;
       sco_index++, p++) {
    /*
     * If the sco state is in the SCO_ST_CONNECTING state, we still need
     * to return accept sco to avoid race conditon for sco creation
     */
    bool rem_bd_matches = p->rem_bd_known && p->esco.data.bd_addr == bda;
    if (((p->state == SCO_ST_CONNECTING) && rem_bd_matches) ||
        ((p->state == SCO_ST_LISTENING) &&
         (rem_bd_matches || !p->rem_bd_known))) {
      /* If this was a wildcard, it is not one any more */
      p->rem_bd_known = true;
      p->esco.data.link_type = link_type;
      p->state = SCO_ST_W4_CONN_RSP;
      p->esco.data.bd_addr = bda;

      /* If no callback, auto-accept the connection if packet types match */
      if (!p->esco.p_esco_cback) {
        /* If requesting eSCO reject if default parameters are SCO only */
        if ((link_type == BTM_LINK_TYPE_ESCO &&
             !(p_sco->def_esco_parms.packet_types & BTM_ESCO_LINK_ONLY_MASK) &&
             ((p_sco->def_esco_parms.packet_types &
               BTM_SCO_EXCEPTION_PKTS_MASK) == BTM_SCO_EXCEPTION_PKTS_MASK))

            /* Reject request if SCO is desired but no SCO packets delected */
            ||
            (link_type == BTM_LINK_TYPE_SCO &&
             !(p_sco->def_esco_parms.packet_types & BTM_SCO_LINK_ONLY_MASK))) {
          btm_esco_conn_rsp(sco_index, HCI_ERR_HOST_REJECT_RESOURCES, bda,
                            nullptr);
        } else {
          /* Accept the request */
          btm_esco_conn_rsp(sco_index, HCI_SUCCESS, bda, nullptr);
        }
      } else {
        /* Notify upper layer of connect indication */
        evt_data.bd_addr = bda;
        evt_data.dev_class = dev_class;
        evt_data.link_type = link_type;
        evt_data.sco_inx = sco_index;
        tBTM_ESCO_EVT_DATA btm_esco_evt_data = {};
        btm_esco_evt_data.conn_evt = evt_data;
        p->esco.p_esco_cback(BTM_ESCO_CONN_REQ_EVT, &btm_esco_evt_data);
      }

      return;
    }
  }

  /* If here, no one wants the SCO connection. Reject it */
  log::warn("rejecting SCO for {}", ADDRESS_TO_LOGGABLE_CSTR(bda));
  btm_esco_conn_rsp(BTM_MAX_SCO_LINKS, HCI_ERR_HOST_REJECT_RESOURCES, bda,
                    nullptr);
}

/*******************************************************************************
 *
 * Function         btm_sco_connected
 *
 * Description      This function is called by BTIF when an (e)SCO connection
 *                  is connected.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_connected(const RawAddress& bda, uint16_t hci_handle,
                       tBTM_ESCO_DATA* p_esco_data) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;
  bool spt = false;
  tBTM_CHG_ESCO_PARAMS parms = {};
  int codec;

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (((p->state == SCO_ST_CONNECTING) || (p->state == SCO_ST_LISTENING) ||
         (p->state == SCO_ST_W4_CONN_RSP)) &&
        (p->rem_bd_known) && (p->esco.data.bd_addr == bda)) {
      BTM_LogHistory(
          kBtmLogTag, bda, "Connection created",
          base::StringPrintf("sco_idx:%hu handle:0x%04x ", xx, hci_handle));
      power_telemetry::GetInstance().LogLinkDetails(hci_handle, bda, true,
                                                    false);

      if (p->state == SCO_ST_LISTENING) spt = true;

      p->state = SCO_ST_CONNECTED;
      p->hci_handle = hci_handle;

      BTM_LogHistory(kBtmLogTag, bda, "Connection success",
                     base::StringPrintf("handle:0x%04x %s", hci_handle,
                                        (spt) ? "listener" : "initiator"));
      log::debug("Connected SCO link handle:0x{:04x} peer:{}", hci_handle,
                 ADDRESS_TO_LOGGABLE_CSTR(bda));

      if (!btm_cb.sco_cb.esco_supported) {
        p->esco.data.link_type = BTM_LINK_TYPE_SCO;
        if (spt) {
          parms.packet_types = p->esco.setup.packet_types;
          /* Keep the other parameters the same for SCO */
          parms.max_latency_ms = p->esco.setup.max_latency_ms;
          parms.retransmission_effort = p->esco.setup.retransmission_effort;

          BTM_ChangeEScoLinkParms(xx, &parms);
        }
      } else {
        if (p_esco_data) p->esco.data = *p_esco_data;
      }

      (*p->p_conn_cb)(xx);

      codec = hfp_hal_interface::esco_coding_to_codec(
          p->esco.setup.transmit_coding_format.coding_format);
      hfp_hal_interface::notify_sco_connection_change(
          bda, /*is_connected=*/true, codec);

      /* In-band (non-offload) data path */
      if (p->is_inband()) {
        const auto codec_type = p->get_codec_type();
        if (codec_type == BTM_SCO_CODEC_MSBC ||
            codec_type == BTM_SCO_CODEC_LC3) {
          btm_pcm_buf_read_offset = 0;
          btm_pcm_buf_write_offset = 0;
          auto init = codec_type == BTM_SCO_CODEC_LC3
                          ? &bluetooth::audio::sco::swb::init
                          : &bluetooth::audio::sco::wbs::init;
          init(hfp_hal_interface::get_packet_size(codec));
        }

        std::fill(std::begin(btm_pcm_buf), std::end(btm_pcm_buf), 0);
        bluetooth::audio::sco::open();
      }
      return;
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sco_connection_failed
 *
 * Description      This function is called by BTIF when an (e)SCO connection
 *                  setup is failed.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_connection_failed(tHCI_STATUS hci_status, const RawAddress& bda,
                               uint16_t hci_handle,
                               tBTM_ESCO_DATA* p_esco_data) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (((p->state == SCO_ST_CONNECTING) || (p->state == SCO_ST_LISTENING) ||
         (p->state == SCO_ST_W4_CONN_RSP)) &&
        (p->rem_bd_known) &&
        (p->esco.data.bd_addr == bda || bda == RawAddress::kEmpty)) {
      /* Report the error if originator, otherwise remain in Listen mode */
      if (p->is_orig) {
        log::debug("SCO initiating connection failed handle:0x{:04x} reason:{}",
                   hci_handle, hci_error_code_text(hci_status).c_str());
        switch (hci_status) {
          case HCI_ERR_ROLE_SWITCH_PENDING:
            /* If role switch is pending, we need try again after role switch
             * is complete */
            p->state = SCO_ST_PEND_ROLECHANGE;
            break;
          case HCI_ERR_LMP_ERR_TRANS_COLLISION:
            /* Avoid calling disconnect callback because of sco creation race
             */
            break;
          default: /* Notify client about SCO failure */
            p->state = SCO_ST_UNUSED;
            (*p->p_disc_cb)(xx);
        }
        BTM_LogHistory(
            kBtmLogTag, bda, "Connection failed",
            base::StringPrintf(
                "locally_initiated reason:%s",
                hci_reason_code_text(static_cast<tHCI_REASON>(hci_status))
                    .c_str()));
      } else {
        log::debug(
            "SCO terminating connection failed handle:0x{:04x} reason:{}",
            hci_handle, hci_error_code_text(hci_status).c_str());
        if (p->state == SCO_ST_CONNECTING) {
          p->state = SCO_ST_UNUSED;
          (*p->p_disc_cb)(xx);
        } else {
          p->state = SCO_ST_LISTENING;
          if (bda != RawAddress::kEmpty)
            DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(
                bda, IOT_CONF_KEY_HFP_SCO_CONN_FAIL_COUNT);
        }
        BTM_LogHistory(
            kBtmLogTag, bda, "Connection failed",
            base::StringPrintf(
                "remote_initiated reason:%s",
                hci_reason_code_text(static_cast<tHCI_REASON>(hci_status))
                    .c_str()));
      }
      return;
    }
  }
}

/*******************************************************************************
 *
 * Function         BTM_RemoveSco
 *
 * Description      This function is called to remove a specific SCO connection.
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_RemoveSco(uint16_t sco_inx) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[sco_inx];
  tBTM_PM_STATE state = BTM_PM_ST_INVALID;

  log::verbose("");

  if (BTM_MAX_SCO_LINKS == 0) {
    return BTM_NO_RESOURCES;
  }

  /* Validity check */
  if ((sco_inx >= BTM_MAX_SCO_LINKS) || (p->state == SCO_ST_UNUSED))
    return (BTM_UNKNOWN_ADDR);

  /* If no HCI handle, simply drop the connection and return */
  if (p->hci_handle == HCI_INVALID_HANDLE || p->state == SCO_ST_PEND_UNPARK) {
    p->hci_handle = HCI_INVALID_HANDLE;
    p->state = SCO_ST_UNUSED;
    p->esco.p_esco_cback = NULL; /* Deregister the eSCO event callback */
    return (BTM_SUCCESS);
  }

  if (BTM_ReadPowerMode(p->esco.data.bd_addr, &state) &&
      (state == BTM_PM_ST_PENDING)) {
    log::verbose("BTM_PM_ST_PENDING for ACL mapped with SCO Link 0x{:04x}",
                 p->hci_handle);
    p->state = SCO_ST_PEND_MODECHANGE;
    return (BTM_CMD_STARTED);
  }

  tSCO_STATE old_state = p->state;
  p->state = SCO_ST_DISCONNECTING;

  GetInterface().Disconnect(p->Handle(), HCI_ERR_PEER_USER);

  log::debug("Disconnecting link sco_handle:0x{:04x} peer:{}", p->Handle(),
             ADDRESS_TO_LOGGABLE_CSTR(p->esco.data.bd_addr));
  BTM_LogHistory(
      kBtmLogTag, p->esco.data.bd_addr, "Disconnecting",
      base::StringPrintf("local initiated handle:0x%04x previous_state:%s",
                         p->Handle(), sco_state_text(old_state).c_str()));
  return (BTM_CMD_STARTED);
}

void BTM_RemoveSco(const RawAddress& bda) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (p->rem_bd_known && p->esco.data.bd_addr == bda) {
      BTM_RemoveSco(xx);
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sco_removed
 *
 * Description      This function is called by lower layers when an
 *                  disconnect is received.
 *
 * Returns          true if the link is known about, else false
 *
 ******************************************************************************/
bool btm_sco_removed(uint16_t hci_handle, tHCI_REASON reason) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;

  p = &btm_cb.sco_cb.sco_db[0];
  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if ((p->state != SCO_ST_UNUSED) && (p->state != SCO_ST_LISTENING) &&
        (p->hci_handle == hci_handle)) {
      power_telemetry::GetInstance().LogLinkDetails(
          hci_handle, RawAddress::kEmpty, false, false);
      RawAddress bda(p->esco.data.bd_addr);
      p->state = SCO_ST_UNUSED;
      p->hci_handle = HCI_INVALID_HANDLE;
      p->rem_bd_known = false;
      p->esco.p_esco_cback = NULL; /* Deregister eSCO callback */
      (*p->p_disc_cb)(xx);

      hfp_hal_interface::notify_sco_connection_change(
          bda, /*is_connected=*/false,
          hfp_hal_interface::esco_coding_to_codec(
              p->esco.setup.transmit_coding_format.coding_format));

      log::debug("Disconnected SCO link handle:{} reason:{}", hci_handle,
                 hci_reason_code_text(reason).c_str());
      return true;
    }
  }
  return false;
}

void btm_sco_on_disconnected(uint16_t hci_handle, tHCI_REASON reason) {
  tSCO_CONN* p_sco = btm_cb.sco_cb.get_sco_connection_from_handle(hci_handle);
  if (p_sco == nullptr) {
    log::debug("Unable to find sco connection");
    return;
  }

  if (!p_sco->is_active()) {
    log::info("Connection is not active handle:0x{:04x} reason:{}", hci_handle,
              hci_reason_code_text(reason).c_str());
    return;
  }

  if (p_sco->state == SCO_ST_LISTENING) {
    log::info("Connection is in listening state handle:0x{:04x} reason:{}",
              hci_handle, hci_reason_code_text(reason).c_str());
    return;
  }

  const RawAddress bd_addr(p_sco->esco.data.bd_addr);

  p_sco->state = SCO_ST_UNUSED;
  p_sco->hci_handle = HCI_INVALID_HANDLE;
  p_sco->rem_bd_known = false;
  p_sco->esco.p_esco_cback = NULL; /* Deregister eSCO callback */
  (*p_sco->p_disc_cb)(btm_cb.sco_cb.get_index(p_sco));
  log::debug("Disconnected SCO link handle:{} reason:{}", hci_handle,
             hci_reason_code_text(reason).c_str());
  BTM_LogHistory(kBtmLogTag, bd_addr, "Disconnected",
                 base::StringPrintf("handle:0x%04x reason:%s", hci_handle,
                                    hci_reason_code_text(reason).c_str()));

  hfp_hal_interface::notify_sco_connection_change(
      bd_addr, /*is_connected=*/false,
      hfp_hal_interface::esco_coding_to_codec(
          p_sco->esco.setup.transmit_coding_format.coding_format));

  if (p_sco->is_inband()) {
    const auto codec_type = p_sco->get_codec_type();
    if (codec_type == BTM_SCO_CODEC_MSBC || codec_type == BTM_SCO_CODEC_LC3) {
      auto fill_plc_stats = codec_type == BTM_SCO_CODEC_LC3
                                ? bluetooth::audio::sco::swb::fill_plc_stats
                                : bluetooth::audio::sco::wbs::fill_plc_stats;

      int num_decoded_frames;
      double packet_loss_ratio;
      if (fill_plc_stats(&num_decoded_frames, &packet_loss_ratio)) {
        const int16_t codec_id = sco_codec_type_to_id(codec_type);
        const std::string codec = sco_codec_type_text(codec_type);
        log_hfp_audio_packet_loss_stats(bd_addr, num_decoded_frames,
                                        packet_loss_ratio, codec_id);
        log::debug(
            "Stopped SCO codec:{}, num_decoded_frames:{}, "
            "packet_loss_ratio:{:f}",
            codec.c_str(), num_decoded_frames, packet_loss_ratio);
      } else {
        log::warn("Failed to get the packet loss stats");
      }

      auto cleanup = codec_type == BTM_SCO_CODEC_LC3
                         ? bluetooth::audio::sco::swb::cleanup
                         : bluetooth::audio::sco::wbs::cleanup;

      cleanup();
    }

    bluetooth::audio::sco::cleanup();
  }
}

/*******************************************************************************
 *
 * Function         btm_sco_acl_removed
 *
 * Description      This function is called when an ACL connection is
 *                  removed. If the BD address is NULL, it is assumed that
 *                  the local device is down, and all SCO links are removed.
 *                  If a specific BD address is passed, only SCO connections
 *                  to that BD address are removed.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sco_acl_removed(const RawAddress* bda) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (p->state != SCO_ST_UNUSED) {
      if ((!bda) || (p->esco.data.bd_addr == *bda && p->rem_bd_known)) {
        p->state = SCO_ST_UNUSED;
        p->esco.p_esco_cback = NULL; /* Deregister eSCO callback */
        (*p->p_disc_cb)(xx);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         BTM_ReadScoBdAddr
 *
 * Description      This function is read the remote BD Address for a specific
 *                  SCO connection,
 *
 * Returns          pointer to BD address or NULL if not known
 *
 ******************************************************************************/
const RawAddress* BTM_ReadScoBdAddr(uint16_t sco_inx) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[sco_inx];

  /* Validity check */
  if ((sco_inx < BTM_MAX_SCO_LINKS) && (p->rem_bd_known))
    return &(p->esco.data.bd_addr);
  else
    return (NULL);
}

/*******************************************************************************
 *
 * Function         BTM_SetEScoMode
 *
 * Description      This function sets up the negotiated parameters for SCO or
 *                  eSCO, and sets as the default mode used for outgoing calls
 *                  to BTM_CreateSco.  It does not change any currently active
 *                  (e)SCO links.
 *                  Note:  Incoming (e)SCO connections will always use packet
 *                      types supported by the controller.  If eSCO is not
 *                      desired the feature should be disabled in the
 *                      controller's feature mask.
 *
 * Returns          BTM_SUCCESS if the successful.
 *                  BTM_BUSY if there are one or more active (e)SCO links.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetEScoMode(enh_esco_params_t* p_parms) {
  ASSERT_LOG(p_parms != nullptr, "eSCO parameters must have a value");
  enh_esco_params_t* p_def = &btm_cb.sco_cb.def_esco_parms;

  if (btm_cb.sco_cb.esco_supported) {
    *p_def = *p_parms;
    log::debug(
        "Setting eSCO mode parameters txbw:0x{:08x} rxbw:0x{:08x} "
        "max_lat:0x{:04x} pkt:0x{:04x} rtx_effort:0x{:02x}",
        p_def->transmit_bandwidth, p_def->receive_bandwidth,
        p_def->max_latency_ms, p_def->packet_types,
        p_def->retransmission_effort);
  } else {
    /* Load defaults for SCO only */
    *p_def = esco_parameters_for_codec(
        SCO_CODEC_CVSD_D1, hfp_hal_interface::get_offload_enabled());
    log::warn("eSCO not supported so setting SCO parameters instead");
    log::debug(
        "Setting SCO mode parameters txbw:0x{:08x} rxbw:0x{:08x} "
        "max_lat:0x{:04x} pkt:0x{:04x} rtx_effort:0x{:02x}",
        p_def->transmit_bandwidth, p_def->receive_bandwidth,
        p_def->max_latency_ms, p_def->packet_types,
        p_def->retransmission_effort);
  }
  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTM_RegForEScoEvts
 *
 * Description      This function registers a SCO event callback with the
 *                  specified instance.  It should be used to received
 *                  connection indication events and change of link parameter
 *                  events.
 *
 * Returns          BTM_SUCCESS if the successful.
 *                  BTM_ILLEGAL_VALUE if there is an illegal sco_inx
 *                  BTM_MODE_UNSUPPORTED if controller version is not BT1.2 or
 *                          later or does not support eSCO.
 *
 ******************************************************************************/
tBTM_STATUS BTM_RegForEScoEvts(uint16_t sco_inx,
                               tBTM_ESCO_CBACK* p_esco_cback) {
  if (BTM_MAX_SCO_LINKS == 0) {
    return BTM_MODE_UNSUPPORTED;
  }

  if (!btm_cb.sco_cb.esco_supported) {
    btm_cb.sco_cb.sco_db[sco_inx].esco.p_esco_cback = NULL;
    return (BTM_MODE_UNSUPPORTED);
  }

  if (sco_inx < BTM_MAX_SCO_LINKS &&
      btm_cb.sco_cb.sco_db[sco_inx].state != SCO_ST_UNUSED) {
    btm_cb.sco_cb.sco_db[sco_inx].esco.p_esco_cback = p_esco_cback;
    return (BTM_SUCCESS);
  }
  return (BTM_ILLEGAL_VALUE);
}

/*******************************************************************************
 *
 * Function         BTM_ChangeEScoLinkParms
 *
 * Description      This function requests renegotiation of the parameters on
 *                  the current eSCO Link.  If any of the changes are accepted
 *                  by the controllers, the BTM_ESCO_CHG_EVT event is sent in
 *                  the tBTM_ESCO_CBACK function with the current settings of
 *                  the link. The callback is registered through the call to
 *                  BTM_SetEScoMode.
 *
 *                  Note: If called over a SCO link (including 1.1 controller),
 *                        a change packet type request is sent out instead.
 *
 * Returns          BTM_CMD_STARTED if command is successfully initiated.
 *                  BTM_NO_RESOURCES - not enough resources to initiate command.
 *                  BTM_WRONG_MODE if no connection with a peer device or bad
 *                                 sco_inx.
 *
 ******************************************************************************/
static tBTM_STATUS BTM_ChangeEScoLinkParms(uint16_t sco_inx,
                                           tBTM_CHG_ESCO_PARAMS* p_parms) {
  /* Make sure sco handle is valid and on an active link */
  if (sco_inx >= BTM_MAX_SCO_LINKS ||
      btm_cb.sco_cb.sco_db[sco_inx].state != SCO_ST_CONNECTED)
    return (BTM_WRONG_MODE);

  tSCO_CONN* p_sco = &btm_cb.sco_cb.sco_db[sco_inx];
  enh_esco_params_t* p_setup = &p_sco->esco.setup;

  /* Save the previous types in case command fails */
  uint16_t saved_packet_types = p_setup->packet_types;

  /* If SCO connection OR eSCO not supported just send change packet types */
  if (p_sco->esco.data.link_type == BTM_LINK_TYPE_SCO ||
      !btm_cb.sco_cb.esco_supported) {
    p_setup->packet_types =
        p_parms->packet_types &
        (btm_cb.btm_sco_pkt_types_supported & BTM_SCO_LINK_ONLY_MASK);

    log::verbose("SCO Link for handle 0x{:04x}, pkt 0x{:04x}",
                 p_sco->hci_handle, p_setup->packet_types);

    log::verbose("SCO Link for handle 0x{:04x}, pkt 0x{:04x}",
                 p_sco->hci_handle, p_setup->packet_types);

    GetInterface().ChangeConnectionPacketType(
        p_sco->hci_handle, BTM_ESCO_2_SCO(p_setup->packet_types));
  } else /* eSCO is supported and the link type is eSCO */
  {
    uint16_t temp_packet_types =
        (p_parms->packet_types & BTM_SCO_SUPPORTED_PKTS_MASK &
         btm_cb.btm_sco_pkt_types_supported);

    /* OR in any exception packet types */
    temp_packet_types |=
        ((p_parms->packet_types & BTM_SCO_EXCEPTION_PKTS_MASK) |
         (btm_cb.btm_sco_pkt_types_supported & BTM_SCO_EXCEPTION_PKTS_MASK));
    p_setup->packet_types = temp_packet_types;

    log::verbose("-> eSCO Link for handle 0x{:04x}", p_sco->hci_handle);
    log::verbose(
        "   txbw 0x{:x}, rxbw 0x{:x}, lat 0x{:x}, retrans 0x{:02x}, pkt "
        "0x{:04x}",
        p_setup->transmit_bandwidth, p_setup->receive_bandwidth,
        p_parms->max_latency_ms, p_parms->retransmission_effort,
        temp_packet_types);

    /* Use Enhanced Synchronous commands if supported */
    if (controller_get_interface()
            ->supports_enhanced_setup_synchronous_connection() &&
        !osi_property_get_bool(kPropertyDisableEnhancedConnection,
                               kDefaultDisableEnhancedConnection)) {
      btsnd_hcic_enhanced_set_up_synchronous_connection(p_sco->hci_handle,
                                                        p_setup);
      p_setup->packet_types = saved_packet_types;
    } else { /* Use older command */
      uint16_t voice_content_format = btm_sco_voice_settings_to_legacy(p_setup);
      /* When changing an existing link, only change latency, retrans, and
       * pkts */
      btsnd_hcic_setup_esco_conn(p_sco->hci_handle, p_setup->transmit_bandwidth,
                                 p_setup->receive_bandwidth,
                                 p_parms->max_latency_ms, voice_content_format,
                                 p_parms->retransmission_effort,
                                 p_setup->packet_types);
    }

    log::verbose(
        "txbw 0x{:x}, rxbw 0x{:x}, lat 0x{:x}, retrans 0x{:02x}, pkt 0x{:04x}",
        p_setup->transmit_bandwidth, p_setup->receive_bandwidth,
        p_parms->max_latency_ms, p_parms->retransmission_effort,
        temp_packet_types);
  }

  return (BTM_CMD_STARTED);
}

/*******************************************************************************
 *
 * Function         BTM_EScoConnRsp
 *
 * Description      This function is called upon receipt of an (e)SCO connection
 *                  request event (BTM_ESCO_CONN_REQ_EVT) to accept or reject
 *                  the request. Parameters used to negotiate eSCO links.
 *                  If p_parms is NULL, then values set through BTM_SetEScoMode
 *                  are used.
 *                  If the link type of the incoming request is SCO, then only
 *                  the tx_bw, max_latency, content format, and packet_types are
 *                  valid.  The hci_status parameter should be
 *                  ([0x0] to accept, [0x0d..0x0f] to reject)
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_EScoConnRsp(uint16_t sco_inx, uint8_t hci_status,
                     enh_esco_params_t* p_parms) {
  if (sco_inx < BTM_MAX_SCO_LINKS &&
      btm_cb.sco_cb.sco_db[sco_inx].state == SCO_ST_W4_CONN_RSP) {
    btm_esco_conn_rsp(sco_inx, hci_status,
                      btm_cb.sco_cb.sco_db[sco_inx].esco.data.bd_addr, p_parms);
  }
}

/*******************************************************************************
 *
 * Function         btm_is_sco_active
 *
 * Description      This function is called to see if a SCO handle is already in
 *                  use.
 *
 * Returns          bool
 *
 ******************************************************************************/
bool btm_is_sco_active(uint16_t handle) {
  uint16_t xx;
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (handle == p->hci_handle && p->state == SCO_ST_CONNECTED) return (true);
  }
  return (false);
}

/*******************************************************************************
 *
 * Function         BTM_GetNumScoLinks
 *
 * Description      This function returns the number of active sco links.
 *
 * Returns          uint8_t
 *
 ******************************************************************************/
uint8_t BTM_GetNumScoLinks(void) {
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];
  uint16_t xx;
  uint8_t num_scos = 0;

  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    switch (p->state) {
      case SCO_ST_W4_CONN_RSP:
      case SCO_ST_CONNECTING:
      case SCO_ST_CONNECTED:
      case SCO_ST_DISCONNECTING:
      case SCO_ST_PEND_UNPARK:
        num_scos++;
        break;
      default:
        break;
    }
  }
  return (num_scos);
}

/*******************************************************************************
 *
 * Function         BTM_IsScoActiveByBdaddr
 *
 * Description      This function is called to see if a SCO connection is active
 *                  for a bd address.
 *
 * Returns          bool
 *
 ******************************************************************************/
bool BTM_IsScoActiveByBdaddr(const RawAddress& remote_bda) {
  uint8_t xx;
  tSCO_CONN* p = &btm_cb.sco_cb.sco_db[0];

  /* If any SCO is being established to the remote BD address, refuse this */
  for (xx = 0; xx < BTM_MAX_SCO_LINKS; xx++, p++) {
    if (p->esco.data.bd_addr == remote_bda && p->state == SCO_ST_CONNECTED) {
      return (true);
    }
  }
  return (false);
}

/*******************************************************************************
 *
 * Function         btm_sco_voice_settings_2_legacy
 *
 * Description      This function is called to convert the Enhanced eSCO
 *                  parameters into voice setting parameter mask used
 *                  for legacy setup synchronous connection HCI commands
 *
 * Returns          UINT16 - 16-bit mask for voice settings
 *
 *          HCI_INP_CODING_LINEAR           0x0000 (0000000000)
 *          HCI_INP_CODING_U_LAW            0x0100 (0100000000)
 *          HCI_INP_CODING_A_LAW            0x0200 (1000000000)
 *          HCI_INP_CODING_MASK             0x0300 (1100000000)
 *
 *          HCI_INP_DATA_FMT_1S_COMPLEMENT  0x0000 (0000000000)
 *          HCI_INP_DATA_FMT_2S_COMPLEMENT  0x0040 (0001000000)
 *          HCI_INP_DATA_FMT_SIGN_MAGNITUDE 0x0080 (0010000000)
 *          HCI_INP_DATA_FMT_UNSIGNED       0x00c0 (0011000000)
 *          HCI_INP_DATA_FMT_MASK           0x00c0 (0011000000)
 *
 *          HCI_INP_SAMPLE_SIZE_8BIT        0x0000 (0000000000)
 *          HCI_INP_SAMPLE_SIZE_16BIT       0x0020 (0000100000)
 *          HCI_INP_SAMPLE_SIZE_MASK        0x0020 (0000100000)
 *
 *          HCI_INP_LINEAR_PCM_BIT_POS_MASK 0x001c (0000011100)
 *          HCI_INP_LINEAR_PCM_BIT_POS_OFFS 2
 *
 *          HCI_AIR_CODING_FORMAT_CVSD      0x0000 (0000000000)
 *          HCI_AIR_CODING_FORMAT_U_LAW     0x0001 (0000000001)
 *          HCI_AIR_CODING_FORMAT_A_LAW     0x0002 (0000000010)
 *          HCI_AIR_CODING_FORMAT_TRANSPNT  0x0003 (0000000011)
 *          HCI_AIR_CODING_FORMAT_MASK      0x0003 (0000000011)
 *
 *          default (0001100000)
 *          HCI_DEFAULT_VOICE_SETTINGS    (HCI_INP_CODING_LINEAR \
 *                                   | HCI_INP_DATA_FMT_2S_COMPLEMENT \
 *                                   | HCI_INP_SAMPLE_SIZE_16BIT \
 *                                   | HCI_AIR_CODING_FORMAT_CVSD)
 *
 ******************************************************************************/
static uint16_t btm_sco_voice_settings_to_legacy(enh_esco_params_t* p_params) {
  uint16_t voice_settings = 0;

  /* Convert Input Coding Format: If no uLaw or aLAW then Linear will be used
   * (0) */
  if (p_params->input_coding_format.coding_format == ESCO_CODING_FORMAT_ULAW)
    voice_settings |= HCI_INP_CODING_U_LAW;
  else if (p_params->input_coding_format.coding_format ==
           ESCO_CODING_FORMAT_ALAW)
    voice_settings |= HCI_INP_CODING_A_LAW;
  /* else default value of '0 is good 'Linear' */

  /* Convert Input Data Format. Use 2's Compliment as the default */
  switch (p_params->input_pcm_data_format) {
    case ESCO_PCM_DATA_FORMAT_1_COMP:
      /* voice_settings |= HCI_INP_DATA_FMT_1S_COMPLEMENT;     value is '0'
       * already */
      break;

    case ESCO_PCM_DATA_FORMAT_SIGN:
      voice_settings |= HCI_INP_DATA_FMT_SIGN_MAGNITUDE;
      break;

    case ESCO_PCM_DATA_FORMAT_UNSIGN:
      voice_settings |= HCI_INP_DATA_FMT_UNSIGNED;
      break;

    default: /* 2's Compliment */
      voice_settings |= HCI_INP_DATA_FMT_2S_COMPLEMENT;
      break;
  }

  /* Convert Over the Air Coding. Use CVSD as the default */
  switch (p_params->transmit_coding_format.coding_format) {
    case ESCO_CODING_FORMAT_ULAW:
      voice_settings |= HCI_AIR_CODING_FORMAT_U_LAW;
      break;

    case ESCO_CODING_FORMAT_ALAW:
      voice_settings |= HCI_AIR_CODING_FORMAT_A_LAW;
      break;

    case ESCO_CODING_FORMAT_TRANSPNT:
    case ESCO_CODING_FORMAT_MSBC:
    case ESCO_CODING_FORMAT_LC3:
      voice_settings |= HCI_AIR_CODING_FORMAT_TRANSPNT;
      break;

    default: /* CVSD (0) */
      break;
  }

  /* Convert PCM payload MSB position (0000011100) */
  voice_settings |= (uint16_t)(((p_params->input_pcm_payload_msb_position & 0x7)
                                << HCI_INP_LINEAR_PCM_BIT_POS_OFFS));

  /* Convert Input Sample Size (0000011100) */
  if (p_params->input_coded_data_size == 16)
    voice_settings |= HCI_INP_SAMPLE_SIZE_16BIT;
  else /* Use 8 bit for all others */
    voice_settings |= HCI_INP_SAMPLE_SIZE_8BIT;

  log::verbose("voice setting for legacy 0x{:03x}", voice_settings);

  return (voice_settings);
}
/*******************************************************************************
 *
 * Function         BTM_GetScoDebugDump
 *
 * Description      Get the status of SCO. This function is only used for
 *                  testing and debugging purposes.
 *
 * Returns          Data with SCO related debug dump.
 *
 ******************************************************************************/
tBTM_SCO_DEBUG_DUMP BTM_GetScoDebugDump() {
  tSCO_CONN* active_sco = btm_get_active_sco();
  tBTM_SCO_DEBUG_DUMP debug_dump = {};

  debug_dump.is_active = active_sco != nullptr;
  if (!debug_dump.is_active) return debug_dump;

  tBTM_SCO_CODEC_TYPE codec_type = active_sco->get_codec_type();
  debug_dump.codec_id = sco_codec_type_to_id(codec_type);
  if (debug_dump.codec_id != UUID_CODEC_MSBC &&
      debug_dump.codec_id != UUID_CODEC_LC3)
    return debug_dump;

  auto fill_plc_stats = debug_dump.codec_id == UUID_CODEC_LC3
                            ? &bluetooth::audio::sco::swb::fill_plc_stats
                            : &bluetooth::audio::sco::wbs::fill_plc_stats;

  if (!fill_plc_stats(&debug_dump.total_num_decoded_frames,
                      &debug_dump.pkt_loss_ratio))
    return debug_dump;

  auto get_pkt_status = debug_dump.codec_id == UUID_CODEC_LC3
                            ? &bluetooth::audio::sco::swb::get_pkt_status
                            : &bluetooth::audio::sco::wbs::get_pkt_status;

  tBTM_SCO_PKT_STATUS* pkt_status = get_pkt_status();
  if (pkt_status == nullptr) return debug_dump;

  tBTM_SCO_PKT_STATUS_DATA* data = &debug_dump.latest_data;
  data->begin_ts_raw_us = pkt_status->begin_ts_raw_us();
  data->end_ts_raw_us = pkt_status->end_ts_raw_us();
  data->status_in_hex = pkt_status->data_to_hex_string();
  data->status_in_binary = pkt_status->data_to_binary_string();
  return debug_dump;
}

bool btm_peer_supports_esco_2m_phy(RawAddress remote_bda) {
  uint8_t* features = BTM_ReadRemoteFeatures(remote_bda);
  if (features == nullptr) {
    log::warn("Checking remote features but remote feature read is incomplete");
    return false;
  }
  return HCI_EDR_ESCO_2MPS_SUPPORTED(features);
}

bool btm_peer_supports_esco_3m_phy(RawAddress remote_bda) {
  uint8_t* features = BTM_ReadRemoteFeatures(remote_bda);
  if (features == nullptr) {
    log::warn("Checking remote features but remote feature read is incomplete");
    return false;
  }
  return HCI_EDR_ESCO_3MPS_SUPPORTED(features);
}

bool btm_peer_supports_esco_ev3(RawAddress remote_bda) {
  uint8_t* features = BTM_ReadRemoteFeatures(remote_bda);
  if (features == nullptr) {
    log::warn("Checking remote features but remote feature read is incomplete");
    return false;
  }
  return HCI_ESCO_EV3_SUPPORTED(features);
}
