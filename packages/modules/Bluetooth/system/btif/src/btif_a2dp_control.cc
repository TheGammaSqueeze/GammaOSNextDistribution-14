/******************************************************************************
 *
 *  Copyright 2016 The Android Open Source Project
 *  Copyright 2009-2012 Broadcom Corporation
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

#define LOG_TAG "bt_btif_a2dp_control"

#include "btif_a2dp_control.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <stdbool.h>
#include <stdint.h>

#include "audio_a2dp_hw/include/audio_a2dp_hw.h"
#include "btif_a2dp_sink.h"
#include "btif_a2dp_source.h"
#include "btif_av.h"
#include "btif_av_co.h"
#include "btif_hf.h"
#include "types/raw_address.h"
#include "udrv/include/uipc.h"

#define A2DP_DATA_READ_POLL_MS 10

using namespace bluetooth;

namespace fmt {
template <>
struct formatter<tA2DP_CTRL_CMD> : enum_formatter<tA2DP_CTRL_CMD> {};
template <>
struct formatter<tA2DP_CTRL_ACK> : enum_formatter<tA2DP_CTRL_ACK> {};
template <>
struct formatter<tUIPC_EVENT> : enum_formatter<tUIPC_EVENT> {};
}  // namespace fmt

struct {
  uint64_t total_bytes_read = 0;
  uint16_t audio_delay = 0;
  struct timespec timestamp = {};
} delay_report_stats;

static void btif_a2dp_data_cb(tUIPC_CH_ID ch_id, tUIPC_EVENT event);
static void btif_a2dp_ctrl_cb(tUIPC_CH_ID ch_id, tUIPC_EVENT event);

/* We can have max one command pending */
static tA2DP_CTRL_CMD a2dp_cmd_pending = A2DP_CTRL_CMD_NONE;
std::unique_ptr<tUIPC_STATE> a2dp_uipc = nullptr;

void btif_a2dp_control_init(void) {
  a2dp_uipc = UIPC_Init();
  UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, btif_a2dp_ctrl_cb, A2DP_CTRL_PATH);
}

void btif_a2dp_control_cleanup(void) {
  /* This calls blocks until UIPC is fully closed */
  if (a2dp_uipc != nullptr) {
    UIPC_Close(*a2dp_uipc, UIPC_CH_ID_ALL);
  }
}

static tA2DP_CTRL_ACK btif_a2dp_control_on_check_ready() {
  if (btif_a2dp_source_media_task_is_shutting_down()) {
    log::warn("A2DP command check ready while media task shutting down");
    return A2DP_CTRL_ACK_FAILURE;
  }

  /* check whether AV is ready to setup A2DP datapath */
  if (btif_av_stream_ready() || btif_av_stream_started_ready()) {
    return A2DP_CTRL_ACK_SUCCESS;
  } else {
    log::warn("A2DP command check ready while AV stream is not ready");
    return A2DP_CTRL_ACK_FAILURE;
  }
}

static tA2DP_CTRL_ACK btif_a2dp_control_on_start() {
  /*
   * Don't send START request to stack while we are in a call.
   * Some headsets such as "Sony MW600", don't allow AVDTP START
   * while in a call, and respond with BAD_STATE.
   */
  if (!bluetooth::headset::IsCallIdle()) {
    log::warn("A2DP command start while call state is busy");
    return A2DP_CTRL_ACK_INCALL_FAILURE;
  }

  if (btif_av_stream_ready()) {
    /* Setup audio data channel listener */
    UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb,
              A2DP_DATA_PATH);

    /*
     * Post start event and wait for audio path to open.
     * If we are the source, the ACK will be sent after the start
     * procedure is completed, othewise send it now.
     */
    btif_av_stream_start();
    if (btif_av_get_peer_sep() == AVDT_TSEP_SRC) return A2DP_CTRL_ACK_SUCCESS;
  }

  if (btif_av_stream_started_ready()) {
    /*
     * Already started, setup audio data channel listener and ACK
     * back immediately.
     */
    UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb,
              A2DP_DATA_PATH);
    return A2DP_CTRL_ACK_SUCCESS;
  }
  log::warn("A2DP command start while AV stream is not ready");
  return A2DP_CTRL_ACK_FAILURE;
}

static tA2DP_CTRL_ACK btif_a2dp_control_on_stop() {
  btif_av_stream_stop(RawAddress::kEmpty);
  return A2DP_CTRL_ACK_SUCCESS;
}

static void btif_a2dp_control_on_suspend() {
  /* Local suspend */
  if (btif_av_stream_started_ready()) {
    btif_av_stream_suspend();
    return;
  }
  /* If we are not in started state, just ack back ok and let
   * audioflinger close the channel. This can happen if we are
   * remotely suspended, clear REMOTE SUSPEND flag.
   */
  btif_av_clear_remote_suspend_flag();
  btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
}

static void btif_a2dp_control_on_get_input_audio_config() {
  tA2DP_SAMPLE_RATE sample_rate = btif_a2dp_sink_get_sample_rate();
  tA2DP_CHANNEL_COUNT channel_count = btif_a2dp_sink_get_channel_count();

  btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<uint8_t*>(&sample_rate),
            sizeof(tA2DP_SAMPLE_RATE));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0, &channel_count,
            sizeof(tA2DP_CHANNEL_COUNT));
}

static void btif_a2dp_control_on_get_output_audio_config() {
  btav_a2dp_codec_config_t codec_config;
  btav_a2dp_codec_config_t codec_capability;
  codec_config.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
  codec_config.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
  codec_config.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;
  codec_capability.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
  codec_capability.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
  codec_capability.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;

  A2dpCodecConfig* current_codec = bta_av_get_a2dp_current_codec();
  if (current_codec != nullptr) {
    codec_config = current_codec->getCodecConfig();
    codec_capability = current_codec->getCodecCapability();
  }

  btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
  // Send the current codec config
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_config.sample_rate),
            sizeof(btav_a2dp_codec_sample_rate_t));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_config.bits_per_sample),
            sizeof(btav_a2dp_codec_bits_per_sample_t));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_config.channel_mode),
            sizeof(btav_a2dp_codec_channel_mode_t));
  // Send the current codec capability
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_capability.sample_rate),
            sizeof(btav_a2dp_codec_sample_rate_t));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_capability.bits_per_sample),
            sizeof(btav_a2dp_codec_bits_per_sample_t));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            reinterpret_cast<const uint8_t*>(&codec_capability.channel_mode),
            sizeof(btav_a2dp_codec_channel_mode_t));
}

static void btif_a2dp_control_on_set_output_audio_config() {
  btav_a2dp_codec_config_t codec_config;
  codec_config.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
  codec_config.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
  codec_config.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;

  btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
  // Send the current codec config
  if (UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_CTRL,
                reinterpret_cast<uint8_t*>(&codec_config.sample_rate),
                sizeof(btav_a2dp_codec_sample_rate_t)) !=
      sizeof(btav_a2dp_codec_sample_rate_t)) {
    log::error("Error reading sample rate from audio HAL");
    return;
  }
  if (UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_CTRL,
                reinterpret_cast<uint8_t*>(&codec_config.bits_per_sample),
                sizeof(btav_a2dp_codec_bits_per_sample_t)) !=
      sizeof(btav_a2dp_codec_bits_per_sample_t)) {
    log::error("Error reading bits per sample from audio HAL");
    return;
  }
  if (UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_CTRL,
                reinterpret_cast<uint8_t*>(&codec_config.channel_mode),
                sizeof(btav_a2dp_codec_channel_mode_t)) !=
      sizeof(btav_a2dp_codec_channel_mode_t)) {
    log::error("Error reading channel mode from audio HAL");
    return;
  }
  log::verbose(
      "A2DP_CTRL_SET_OUTPUT_AUDIO_CONFIG: sample_rate=0x{:x} "
      "bits_per_sample=0x{:x} channel_mode=0x{:x}",
      codec_config.sample_rate, codec_config.bits_per_sample,
      codec_config.channel_mode);
  btif_a2dp_source_feeding_update_req(codec_config);
}

static void btif_a2dp_control_on_get_presentation_position() {
  btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);

  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            (uint8_t*)&(delay_report_stats.total_bytes_read), sizeof(uint64_t));
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0,
            (uint8_t*)&(delay_report_stats.audio_delay), sizeof(uint16_t));

  uint32_t seconds = delay_report_stats.timestamp.tv_sec;
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0, (uint8_t*)&seconds,
            sizeof(seconds));

  uint32_t nsec = delay_report_stats.timestamp.tv_nsec;
  UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0, (uint8_t*)&nsec, sizeof(nsec));
}

static void btif_a2dp_recv_ctrl_data(void) {
  tA2DP_CTRL_CMD cmd = A2DP_CTRL_CMD_NONE;
  int n;

  uint8_t read_cmd = 0; /* The read command size is one octet */
  n = UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, &read_cmd, 1);
  cmd = static_cast<tA2DP_CTRL_CMD>(read_cmd);

  /* detach on ctrl channel means audioflinger process was terminated */
  if (n == 0) {
    log::warn("CTRL CH DETACHED");
    UIPC_Close(*a2dp_uipc, UIPC_CH_ID_AV_CTRL);
    return;
  }

  // Don't log A2DP_CTRL_GET_PRESENTATION_POSITION by default, because it
  // could be very chatty when audio is streaming.
  if (cmd == A2DP_CTRL_GET_PRESENTATION_POSITION) {
    log::verbose("a2dp-ctrl-cmd : {}", audio_a2dp_hw_dump_ctrl_event(cmd));
  } else {
    log::warn("a2dp-ctrl-cmd : {}", audio_a2dp_hw_dump_ctrl_event(cmd));
  }

  a2dp_cmd_pending = cmd;
  switch (cmd) {
    case A2DP_CTRL_CMD_CHECK_READY:
      btif_a2dp_command_ack(btif_a2dp_control_on_check_ready());
      break;

    case A2DP_CTRL_CMD_START:
      btif_a2dp_command_ack(btif_a2dp_control_on_start());
      break;

    case A2DP_CTRL_CMD_STOP:
      btif_a2dp_command_ack(btif_a2dp_control_on_stop());
      break;

    case A2DP_CTRL_CMD_SUSPEND:
      btif_a2dp_control_on_suspend();
      break;

    case A2DP_CTRL_GET_INPUT_AUDIO_CONFIG:
      btif_a2dp_control_on_get_input_audio_config();
      break;

    case A2DP_CTRL_GET_OUTPUT_AUDIO_CONFIG:
      btif_a2dp_control_on_get_output_audio_config();
      break;

    case A2DP_CTRL_SET_OUTPUT_AUDIO_CONFIG:
      btif_a2dp_control_on_set_output_audio_config();
      break;

    case A2DP_CTRL_GET_PRESENTATION_POSITION:
      btif_a2dp_control_on_get_presentation_position();
      break;

    default:
      log::error("UNSUPPORTED CMD ({})", cmd);
      btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
      break;
  }

  // Don't log A2DP_CTRL_GET_PRESENTATION_POSITION by default, because it
  // could be very chatty when audio is streaming.
  if (cmd == A2DP_CTRL_GET_PRESENTATION_POSITION) {
    log::verbose("a2dp-ctrl-cmd : {} DONE", audio_a2dp_hw_dump_ctrl_event(cmd));
  } else {
    log::warn("a2dp-ctrl-cmd : {} DONE", audio_a2dp_hw_dump_ctrl_event(cmd));
  }
}

static void btif_a2dp_ctrl_cb(tUIPC_CH_ID /* ch_id */, tUIPC_EVENT event) {
  // Don't log UIPC_RX_DATA_READY_EVT by default, because it
  // could be very chatty when audio is streaming.
  if (event == UIPC_RX_DATA_READY_EVT) {
    log::verbose("A2DP-CTRL-CHANNEL EVENT {}", dump_uipc_event(event));
  } else {
    log::warn("A2DP-CTRL-CHANNEL EVENT {}", dump_uipc_event(event));
  }

  switch (event) {
    case UIPC_OPEN_EVT:
      break;

    case UIPC_CLOSE_EVT:
      /* restart ctrl server unless we are shutting down */
      if (btif_a2dp_source_media_task_is_running())
        UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, btif_a2dp_ctrl_cb,
                  A2DP_CTRL_PATH);
      break;

    case UIPC_RX_DATA_READY_EVT:
      btif_a2dp_recv_ctrl_data();
      break;

    default:
      log::error("### A2DP-CTRL-CHANNEL EVENT {} NOT HANDLED ###", event);
      break;
  }
}

static void btif_a2dp_data_cb(tUIPC_CH_ID /* ch_id */, tUIPC_EVENT event) {
  log::warn("BTIF MEDIA (A2DP-DATA) EVENT {}", dump_uipc_event(event));

  switch (event) {
    case UIPC_OPEN_EVT:
      /*
       * Read directly from media task from here on (keep callback for
       * connection events.
       */
      UIPC_Ioctl(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO,
                 UIPC_REG_REMOVE_ACTIVE_READSET, NULL);
      UIPC_Ioctl(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_SET_READ_POLL_TMO,
                 reinterpret_cast<void*>(A2DP_DATA_READ_POLL_MS));

      if (btif_av_get_peer_sep() == AVDT_TSEP_SNK) {
        /* Start the media task to encode the audio */
        btif_a2dp_source_start_audio_req();
      }

      /* ACK back when media task is fully started */
      break;

    case UIPC_CLOSE_EVT:
      log::verbose("## AUDIO PATH DETACHED ##");
      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      /* Post stop event and wait for audio path to stop */
      btif_av_stream_stop(RawAddress::kEmpty);
      break;

    default:
      log::error("### A2DP-DATA EVENT {} NOT HANDLED ###", event);
      break;
  }
}

void btif_a2dp_command_ack(tA2DP_CTRL_ACK status) {
  uint8_t ack = status;

  // Don't log A2DP_CTRL_GET_PRESENTATION_POSITION by default, because it
  // could be very chatty when audio is streaming.
  if (a2dp_cmd_pending == A2DP_CTRL_GET_PRESENTATION_POSITION) {
    log::verbose("## a2dp ack : {}, status {} ##",
                 audio_a2dp_hw_dump_ctrl_event(a2dp_cmd_pending), status);
  } else {
    log::warn("## a2dp ack : {}, status {} ##",
              audio_a2dp_hw_dump_ctrl_event(a2dp_cmd_pending), status);
  }

  /* Sanity check */
  if (a2dp_cmd_pending == A2DP_CTRL_CMD_NONE) {
    log::error("warning : no command pending, ignore ack");
    return;
  }

  /* Clear pending */
  a2dp_cmd_pending = A2DP_CTRL_CMD_NONE;

  /* Acknowledge start request */
  if (a2dp_uipc != nullptr) {
    UIPC_Send(*a2dp_uipc, UIPC_CH_ID_AV_CTRL, 0, &ack, sizeof(ack));
  }
}

void btif_a2dp_control_log_bytes_read(uint32_t bytes_read) {
  delay_report_stats.total_bytes_read += bytes_read;
  clock_gettime(CLOCK_MONOTONIC, &delay_report_stats.timestamp);
}

void btif_a2dp_control_set_audio_delay(uint16_t delay) {
  log::verbose("DELAY: {:.1f} ms", (float)delay / 10);
  delay_report_stats.audio_delay = delay;
}

void btif_a2dp_control_reset_audio_delay(void) {
  log::verbose("");
  delay_report_stats.audio_delay = 0;
  delay_report_stats.total_bytes_read = 0;
  delay_report_stats.timestamp = {};
}
