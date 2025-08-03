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

#include "a2dp_encoding_host.h"

#include <grp.h>
#include <sys/stat.h>

#include <memory>
#include <vector>

#include "a2dp_encoding.h"
#include "btif/include/btif_a2dp_source.h"
#include "btif/include/btif_av.h"
#include "btif/include/btif_hf.h"
#include "os/log.h"
#include "stack/include/avdt_api.h"
#include "types/raw_address.h"
#include "udrv/include/uipc.h"

#define A2DP_DATA_READ_POLL_MS 10
#define A2DP_HOST_DATA_PATH "/var/run/bluetooth/audio/.a2dp_data"
// TODO(b/198260375): Make A2DP data owner group configurable.
#define A2DP_HOST_DATA_GROUP "bluetooth-audio"

namespace {

std::unique_ptr<tUIPC_STATE> a2dp_uipc = nullptr;

static void btif_a2dp_data_cb([[maybe_unused]] tUIPC_CH_ID ch_id,
                              tUIPC_EVENT event) {
  LOG_WARN("%s: BTIF MEDIA (A2DP-DATA) EVENT %s", __func__,
           dump_uipc_event(event));

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

      // Will start audio on btif_a2dp_on_started

      /* ACK back when media task is fully started */
      break;

    case UIPC_CLOSE_EVT:
      /* Post stop event and wait for audio path to stop */
      btif_av_stream_stop(RawAddress::kEmpty);
      break;

    default:
      LOG_ERROR("%s: ### A2DP-DATA EVENT %d NOT HANDLED ###", __func__, event);
      break;
  }
}

// If A2DP_HOST_DATA_GROUP exists we expect audio server and BT both are
// in this group therefore have access to A2DP socket. Otherwise audio
// server should be in the same group that BT stack runs with to access
// A2DP socket.
static void a2dp_data_path_open() {
  UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb,
            A2DP_HOST_DATA_PATH);
  struct group* grp = getgrnam(A2DP_HOST_DATA_GROUP);
  chmod(A2DP_HOST_DATA_PATH, 0770);
  if (grp) {
    int res = chown(A2DP_HOST_DATA_PATH, -1, grp->gr_gid);
    if (res == -1) {
      LOG_ERROR("%s failed: %s", __func__, strerror(errno));
    }
  }
}

tA2DP_CTRL_CMD a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
uint64_t total_bytes_read_;
timespec data_position_;
uint16_t remote_delay_report_;

}  // namespace

namespace bluetooth {
namespace audio {
namespace a2dp {

// Invoked by audio server to set audio config (PCM for now)
bool SetAudioConfig(AudioConfig config) {
  btav_a2dp_codec_config_t codec_config;
  codec_config.sample_rate = config.sample_rate;
  codec_config.bits_per_sample = config.bits_per_sample;
  codec_config.channel_mode = config.channel_mode;
  btif_a2dp_source_feeding_update_req(codec_config);
  return true;
}

// Invoked by audio server when it has audio data to stream.
bool StartRequest() {
  // Reset total read bytes and timestamp to avoid confusing audio
  // server at delay calculation.
  total_bytes_read_ = 0;
  data_position_ = {0, 0};

  // Check if a previous request is not finished
  if (a2dp_pending_cmd_ == A2DP_CTRL_CMD_START) {
    LOG_INFO("%s: A2DP_CTRL_CMD_START in progress", __func__);
    return false;
  } else if (a2dp_pending_cmd_ != A2DP_CTRL_CMD_NONE) {
    LOG_WARN("%s: busy in pending_cmd=%u", __func__, a2dp_pending_cmd_);
    return false;
  }

  // Don't send START request to stack while we are in a call
  if (!bluetooth::headset::IsCallIdle()) {
    LOG_ERROR("%s: call state is busy", __func__);
    return false;
  }

  if (btif_av_stream_started_ready()) {
    // Already started, ACK back immediately.
    a2dp_data_path_open();
    return true;
  }
  if (btif_av_stream_ready()) {
    a2dp_data_path_open();
    /*
     * Post start event and wait for audio path to open.
     * If we are the source, the ACK will be sent after the start
     * procedure is completed.
     */
    a2dp_pending_cmd_ = A2DP_CTRL_CMD_START;
    btif_av_stream_start();
    if (btif_av_get_peer_sep() != AVDT_TSEP_SRC) {
      LOG_INFO("%s: accepted", __func__);
      return true;  // NOTE: The request is placed, but could still fail.
    }
    a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
    return true;
  }
  LOG_ERROR("%s: AV stream is not ready to start", __func__);
  return false;
}

// Invoked by audio server when audio streaming is done.
bool StopRequest() {
  if (btif_av_get_peer_sep() == AVDT_TSEP_SNK &&
      !btif_av_stream_started_ready()) {
    btif_av_clear_remote_suspend_flag();
    return true;
  }
  LOG_INFO("%s: handling", __func__);
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_STOP;
  btif_av_stream_stop(RawAddress::kEmpty);
  return true;
}

bool SuspendRequest() {
  if (a2dp_pending_cmd_ != A2DP_CTRL_CMD_NONE) {
    LOG_WARN("%s: busy in pending_cmd=%u", __func__, a2dp_pending_cmd_);
    return false;
  }
  if (!btif_av_stream_started_ready()) {
    LOG_WARN("%s: AV stream is not started", __func__);
    return false;
  }
  LOG_INFO("%s: handling", __func__);
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_SUSPEND;
  btif_av_stream_suspend();
  return true;
}

// Invoked by audio server to check audio presentation position periodically.
PresentationPosition GetPresentationPosition() {
  PresentationPosition presentation_position{
      .remote_delay_report_ns = remote_delay_report_ * 100000u,
      .total_bytes_read = total_bytes_read_,
      .data_position = data_position_,
  };
  return presentation_position;
}

// delay reports from AVDTP is based on 1/10 ms (100us)
void set_remote_delay(uint16_t delay_report) {
  remote_delay_report_ = delay_report;
}

// Inform audio server about offloading codec; not used for now
bool update_codec_offloading_capabilities(
    const std::vector<btav_a2dp_codec_config_t>& framework_preference,
    bool supports_a2dp_hw_offload_v2) {
  return false;
}

// Checking if new bluetooth_audio is enabled
bool is_hal_enabled() { return true; }

// Check if new bluetooth_audio is running with offloading encoders
bool is_hal_offloading() { return false; }

// Initialize BluetoothAudio HAL: openProvider
bool init(bluetooth::common::MessageLoopThread* message_loop) {
  a2dp_uipc = UIPC_Init();
  total_bytes_read_ = 0;
  data_position_ = {};
  remote_delay_report_ = 0;

  return true;
}

// Clean up BluetoothAudio HAL
void cleanup() {
  end_session();

  if (a2dp_uipc != nullptr) {
    UIPC_Close(*a2dp_uipc, UIPC_CH_ID_ALL);
    a2dp_uipc = nullptr;
  }
}

// Set up the codec into BluetoothAudio HAL
bool setup_codec() {
  // TODO: setup codec
  return true;
}

void start_session() {
  // TODO: Notify server; or do we handle it during connected?
}

void end_session() {
  // TODO: Notify server; or do we handle it during disconnected?

  // Reset remote delay. New value will be set when new session starts.
  remote_delay_report_ = 0;

  a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
}

void set_audio_low_latency_mode_allowed(bool allowed){
}


void ack_stream_started(const tA2DP_CTRL_ACK& ack) {
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
  // TODO: Notify server
}

void ack_stream_suspended(const tA2DP_CTRL_ACK& ack) {
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
  // TODO: Notify server
}

// Read from the FMQ of BluetoothAudio HAL
size_t read(uint8_t* p_buf, uint32_t len) {
  uint32_t bytes_read = 0;
  if (a2dp_uipc == nullptr) {
    return 0;
  }
  bytes_read = UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, p_buf, len);
  total_bytes_read_ += bytes_read;
  // MONOTONIC_RAW isn't affected by NTP, audio stack rely on this
  // to get precise delay calculation.
  clock_gettime(CLOCK_MONOTONIC_RAW, &data_position_);
  return bytes_read;
}

// Check if OPUS codec is supported
bool is_opus_supported() { return true; }

namespace provider {

// Lookup the codec info in the list of supported offloaded sink codecs.
std::optional<btav_a2dp_codec_index_t> sink_codec_index(
    const uint8_t* p_codec_info) {
  return std::nullopt;
}

// Lookup the codec info in the list of supported offloaded source codecs.
std::optional<btav_a2dp_codec_index_t> source_codec_index(
    const uint8_t* p_codec_info) {
  return std::nullopt;
}

// Return the name of the codec which is assigned to the input index.
// The codec index must be in the ranges
// BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN..BTAV_A2DP_CODEC_INDEX_SINK_EXT_MAX or
// BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN..BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MAX.
// Returns nullopt if the codec_index is not assigned or codec extensibility
// is not supported or enabled.
std::optional<const char*> codec_index_str(
    btav_a2dp_codec_index_t codec_index) {
  return std::nullopt;
}

// Return true if the codec is supported for the session type
// A2DP_HARDWARE_ENCODING_DATAPATH or A2DP_HARDWARE_DECODING_DATAPATH.
bool supports_codec(btav_a2dp_codec_index_t codec_index) { return false; }

// Return the A2DP capabilities for the selected codec.
bool codec_info(btav_a2dp_codec_index_t codec_index, uint64_t* codec_id,
                uint8_t* codec_info, btav_a2dp_codec_config_t* codec_config) {
  return false;
}

// Query the codec selection fromt the audio HAL.
// The HAL is expected to pick the best audio configuration based on the
// discovered remote SEPs.
std::optional<a2dp_configuration> get_a2dp_configuration(
    RawAddress peer_address,
    std::vector<a2dp_remote_capabilities> const& remote_seps,
    btav_a2dp_codec_config_t const& user_preferences) {
  return std::nullopt;
}

// Query the codec parameters from the audio HAL.
// The HAL performs a two part validation:
//  - check if the configuration is valid
//  - check if the configuration is supported by the audio provider
// In case any of these checks fails, the corresponding A2DP
// status is returned. If the configuration is valid and supported,
// A2DP_OK is returned.
tA2DP_STATUS parse_a2dp_configuration(
    btav_a2dp_codec_index_t codec_index, const uint8_t* codec_info,
    btav_a2dp_codec_config_t* codec_parameters,
    std::vector<uint8_t>* vendor_specific_parameters) {
  return A2DP_FAIL;
}

}  // namespace provider

}  // namespace a2dp
}  // namespace audio
}  // namespace bluetooth
