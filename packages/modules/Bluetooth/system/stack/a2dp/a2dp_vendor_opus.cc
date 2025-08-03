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

/******************************************************************************
 *
 *  Utility functions to help build and parse the Opus Codec Information
 *  Element and Media Payload.
 *
 ******************************************************************************/

#define LOG_TAG "a2dp_vendor_opus"

#include "a2dp_vendor_opus.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <string.h>

#include "a2dp_vendor_opus_decoder.h"
#include "a2dp_vendor_opus_encoder.h"
#include "include/check.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/osi.h"

using namespace bluetooth;

// data type for the Opus Codec Information Element */
// NOTE: bits_per_sample and frameSize for Opus encoder initialization.
typedef struct {
  uint32_t vendorId;
  uint16_t codecId;    /* Codec ID for Opus */
  uint8_t sampleRate;  /* Sampling Frequency */
  uint8_t channelMode; /* STEREO/DUAL/MONO */
  btav_a2dp_codec_bits_per_sample_t bits_per_sample;
  uint8_t future1; /* codec_specific_1 framesize */
  uint8_t future2; /* codec_specific_2 */
  uint8_t future3; /* codec_specific_3 */
  uint8_t future4; /* codec_specific_4 */
} tA2DP_OPUS_CIE;

/* Opus Source codec capabilities */
static const tA2DP_OPUS_CIE a2dp_opus_source_caps = {
    A2DP_OPUS_VENDOR_ID,  // vendorId
    A2DP_OPUS_CODEC_ID,   // codecId
    // sampleRate
    (A2DP_OPUS_SAMPLING_FREQ_48000),
    // channelMode
    (A2DP_OPUS_CHANNEL_MODE_STEREO),
    // bits_per_sample
    (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16),
    // future 1 frameSize
    (A2DP_OPUS_20MS_FRAMESIZE),
    // future 2
    0x00,
    // future 3
    0x00,
    // future 4
    0x00};

/* Opus Sink codec capabilities */
static const tA2DP_OPUS_CIE a2dp_opus_sink_caps = {
    A2DP_OPUS_VENDOR_ID,  // vendorId
    A2DP_OPUS_CODEC_ID,   // codecId
    // sampleRate
    (A2DP_OPUS_SAMPLING_FREQ_48000),
    // channelMode
    (A2DP_OPUS_CHANNEL_MODE_STEREO),
    // bits_per_sample
    (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16),
    // future 1 frameSize
    (A2DP_OPUS_20MS_FRAMESIZE),
    // future 2
    0x00,
    // future 3
    0x00,
    // future 4
    0x00};

/* Default Opus codec configuration */
static const tA2DP_OPUS_CIE a2dp_opus_default_config = {
    A2DP_OPUS_VENDOR_ID,                 // vendorId
    A2DP_OPUS_CODEC_ID,                  // codecId
    A2DP_OPUS_SAMPLING_FREQ_48000,       // sampleRate
    A2DP_OPUS_CHANNEL_MODE_STEREO,       // channelMode
    BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16,  // bits_per_sample
    A2DP_OPUS_20MS_FRAMESIZE,            // frameSize
    0x00,                                // future 2
    0x00,                                // future 3
    0x00                                 // future 4
};

static const tA2DP_ENCODER_INTERFACE a2dp_encoder_interface_opus = {
    a2dp_vendor_opus_encoder_init,
    a2dp_vendor_opus_encoder_cleanup,
    a2dp_vendor_opus_feeding_reset,
    a2dp_vendor_opus_feeding_flush,
    a2dp_vendor_opus_get_encoder_interval_ms,
    a2dp_vendor_opus_get_effective_frame_size,
    a2dp_vendor_opus_send_frames,
    a2dp_vendor_opus_set_transmit_queue_length};

static const tA2DP_DECODER_INTERFACE a2dp_decoder_interface_opus = {
    a2dp_vendor_opus_decoder_init,          a2dp_vendor_opus_decoder_cleanup,
    a2dp_vendor_opus_decoder_decode_packet, a2dp_vendor_opus_decoder_start,
    a2dp_vendor_opus_decoder_suspend,       a2dp_vendor_opus_decoder_configure,
};

UNUSED_ATTR static tA2DP_STATUS A2DP_CodecInfoMatchesCapabilityOpus(
    const tA2DP_OPUS_CIE* p_cap, const uint8_t* p_codec_info,
    bool is_peer_codec_info);

// Builds the Opus Media Codec Capabilities byte sequence beginning from the
// LOSC octet. |media_type| is the media type |AVDT_MEDIA_TYPE_*|.
// |p_ie| is a pointer to the Opus Codec Information Element information.
// The result is stored in |p_result|. Returns A2DP_SUCCESS on success,
// otherwise the corresponding A2DP error status code.
static tA2DP_STATUS A2DP_BuildInfoOpus(uint8_t media_type,
                                       const tA2DP_OPUS_CIE* p_ie,
                                       uint8_t* p_result) {
  if (p_ie == NULL || p_result == NULL) {
    log::error("invalid information element");
    return A2DP_INVALID_PARAMS;
  }

  *p_result++ = A2DP_OPUS_CODEC_LEN;
  *p_result++ = (media_type << 4);
  *p_result++ = A2DP_MEDIA_CT_NON_A2DP;

  // Vendor ID and Codec ID
  *p_result++ = (uint8_t)(p_ie->vendorId & 0x000000FF);
  *p_result++ = (uint8_t)((p_ie->vendorId & 0x0000FF00) >> 8);
  *p_result++ = (uint8_t)((p_ie->vendorId & 0x00FF0000) >> 16);
  *p_result++ = (uint8_t)((p_ie->vendorId & 0xFF000000) >> 24);
  *p_result++ = (uint8_t)(p_ie->codecId & 0x00FF);
  *p_result++ = (uint8_t)((p_ie->codecId & 0xFF00) >> 8);

  *p_result = 0;
  *p_result |= (uint8_t)(p_ie->channelMode) & A2DP_OPUS_CHANNEL_MODE_MASK;
  if ((*p_result & A2DP_OPUS_CHANNEL_MODE_MASK) == 0) {
    log::error("channelmode 0x{:X} setting failed", (p_ie->channelMode));
    return A2DP_INVALID_PARAMS;
  }

  *p_result |= ((uint8_t)(p_ie->future1) & A2DP_OPUS_FRAMESIZE_MASK);
  if ((*p_result & A2DP_OPUS_FRAMESIZE_MASK) == 0) {
    log::error("frameSize 0x{:X} setting failed", (p_ie->future1));
    return A2DP_INVALID_PARAMS;
  }

  *p_result |= ((uint8_t)(p_ie->sampleRate) & A2DP_OPUS_SAMPLING_FREQ_MASK);
  if ((*p_result & A2DP_OPUS_SAMPLING_FREQ_MASK) == 0) {
    log::error("samplerate 0x{:X} setting failed", (p_ie->sampleRate));
    return A2DP_INVALID_PARAMS;
  }

  p_result++;

  return A2DP_SUCCESS;
}

// Parses the Opus Media Codec Capabilities byte sequence beginning from the
// LOSC octet. The result is stored in |p_ie|. The byte sequence to parse is
// |p_codec_info|. If |is_capability| is true, the byte sequence is
// codec capabilities, otherwise is codec configuration.
// Returns A2DP_SUCCESS on success, otherwise the corresponding A2DP error
// status code.
static tA2DP_STATUS A2DP_ParseInfoOpus(tA2DP_OPUS_CIE* p_ie,
                                       const uint8_t* p_codec_info,
                                       bool is_capability) {
  uint8_t losc;
  uint8_t media_type;
  tA2DP_CODEC_TYPE codec_type;

  if (p_ie == NULL || p_codec_info == NULL) {
    log::error("unable to parse information element");
    return A2DP_INVALID_PARAMS;
  }

  // Check the codec capability length
  losc = *p_codec_info++;
  if (losc != A2DP_OPUS_CODEC_LEN) {
    log::error("invalid codec ie length {}", losc);
    return A2DP_WRONG_CODEC;
  }

  media_type = (*p_codec_info++) >> 4;
  codec_type = *p_codec_info++;
  /* Check the Media Type and Media Codec Type */
  if (media_type != AVDT_MEDIA_TYPE_AUDIO ||
      codec_type != A2DP_MEDIA_CT_NON_A2DP) {
    log::error("invalid codec");
    return A2DP_WRONG_CODEC;
  }

  // Check the Vendor ID and Codec ID */
  p_ie->vendorId = (*p_codec_info & 0x000000FF) |
                   (*(p_codec_info + 1) << 8 & 0x0000FF00) |
                   (*(p_codec_info + 2) << 16 & 0x00FF0000) |
                   (*(p_codec_info + 3) << 24 & 0xFF000000);
  p_codec_info += 4;
  p_ie->codecId =
      (*p_codec_info & 0x00FF) | (*(p_codec_info + 1) << 8 & 0xFF00);
  p_codec_info += 2;
  if (p_ie->vendorId != A2DP_OPUS_VENDOR_ID ||
      p_ie->codecId != A2DP_OPUS_CODEC_ID) {
    log::error("wrong vendor or codec id");
    return A2DP_WRONG_CODEC;
  }

  p_ie->channelMode = *p_codec_info & A2DP_OPUS_CHANNEL_MODE_MASK;
  p_ie->future1 = *p_codec_info & A2DP_OPUS_FRAMESIZE_MASK;
  p_ie->sampleRate = *p_codec_info & A2DP_OPUS_SAMPLING_FREQ_MASK;
  p_ie->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;

  if (is_capability) {
    // NOTE: The checks here are very liberal. We should be using more
    // pedantic checks specific to the SRC or SNK as specified in the spec.
    if (A2DP_BitsSet(p_ie->sampleRate) == A2DP_SET_ZERO_BIT) {
      log::error("invalid sample rate 0x{:X}", p_ie->sampleRate);
      return A2DP_BAD_SAMP_FREQ;
    }
    if (A2DP_BitsSet(p_ie->channelMode) == A2DP_SET_ZERO_BIT) {
      log::error("invalid channel mode");
      return A2DP_BAD_CH_MODE;
    }

    return A2DP_SUCCESS;
  }

  if (A2DP_BitsSet(p_ie->sampleRate) != A2DP_SET_ONE_BIT) {
    log::error("invalid sampling frequency 0x{:X}", p_ie->sampleRate);
    return A2DP_BAD_SAMP_FREQ;
  }
  if (A2DP_BitsSet(p_ie->channelMode) != A2DP_SET_ONE_BIT) {
    log::error("invalid channel mode.");
    return A2DP_BAD_CH_MODE;
  }

  return A2DP_SUCCESS;
}

// Build the Opus Media Payload Header.
// |p_dst| points to the location where the header should be written to.
// If |frag| is true, the media payload frame is fragmented.
// |start| is true for the first packet of a fragmented frame.
// |last| is true for the last packet of a fragmented frame.
// If |frag| is false, |num| is the number of number of frames in the packet,
// otherwise is the number of remaining fragments (including this one).
static void A2DP_BuildMediaPayloadHeaderOpus(uint8_t* p_dst, bool frag,
                                             bool start, bool last,
                                             uint8_t num) {
  if (p_dst == NULL) return;

  *p_dst = 0;
  if (frag) *p_dst |= A2DP_OPUS_HDR_F_MSK;
  if (start) *p_dst |= A2DP_OPUS_HDR_S_MSK;
  if (last) *p_dst |= A2DP_OPUS_HDR_L_MSK;
  *p_dst |= (A2DP_OPUS_HDR_NUM_MSK & num);
}

bool A2DP_IsVendorSourceCodecValidOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE cfg_cie;

  /* Use a liberal check when parsing the codec info */
  return (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, false) == A2DP_SUCCESS) ||
         (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, true) == A2DP_SUCCESS);
}

bool A2DP_IsVendorSinkCodecValidOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE cfg_cie;

  /* Use a liberal check when parsing the codec info */
  return (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, false) == A2DP_SUCCESS) ||
         (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, true) == A2DP_SUCCESS);
}

bool A2DP_IsVendorPeerSourceCodecValidOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE cfg_cie;

  /* Use a liberal check when parsing the codec info */
  return (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, false) == A2DP_SUCCESS) ||
         (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, true) == A2DP_SUCCESS);
}

bool A2DP_IsVendorPeerSinkCodecValidOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE cfg_cie;

  /* Use a liberal check when parsing the codec info */
  return (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, false) == A2DP_SUCCESS) ||
         (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, true) == A2DP_SUCCESS);
}

bool A2DP_IsVendorSinkCodecSupportedOpus(const uint8_t* p_codec_info) {
  return A2DP_CodecInfoMatchesCapabilityOpus(&a2dp_opus_sink_caps, p_codec_info,
                                             false) == A2DP_SUCCESS;
}
bool A2DP_IsPeerSourceCodecSupportedOpus(const uint8_t* p_codec_info) {
  return A2DP_CodecInfoMatchesCapabilityOpus(&a2dp_opus_sink_caps, p_codec_info,
                                             true) == A2DP_SUCCESS;
}

// Checks whether A2DP Opus codec configuration matches with a device's codec
// capabilities. |p_cap| is the Opus codec configuration. |p_codec_info| is
// the device's codec capabilities.
// If |is_capability| is true, the byte sequence is codec capabilities,
// otherwise is codec configuration.
// |p_codec_info| contains the codec capabilities for a peer device that
// is acting as an A2DP source.
// Returns A2DP_SUCCESS if the codec configuration matches with capabilities,
// otherwise the corresponding A2DP error status code.
static tA2DP_STATUS A2DP_CodecInfoMatchesCapabilityOpus(
    const tA2DP_OPUS_CIE* p_cap, const uint8_t* p_codec_info,
    bool is_capability) {
  tA2DP_STATUS status;
  tA2DP_OPUS_CIE cfg_cie;

  /* parse configuration */
  status = A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, is_capability);
  if (status != A2DP_SUCCESS) {
    log::error("parsing failed {}", status);
    return status;
  }

  /* verify that each parameter is in range */

  log::verbose("SAMPLING FREQ peer: 0x{:x}, capability 0x{:x}",
               cfg_cie.sampleRate, p_cap->sampleRate);
  log::verbose("CH_MODE peer: 0x{:x}, capability 0x{:x}", cfg_cie.channelMode,
               p_cap->channelMode);
  log::verbose("FRAMESIZE peer: 0x{:x}, capability 0x{:x}", cfg_cie.future1,
               p_cap->future1);

  /* sampling frequency */
  if ((cfg_cie.sampleRate & p_cap->sampleRate) == 0) return A2DP_NS_SAMP_FREQ;

  /* channel mode */
  if ((cfg_cie.channelMode & p_cap->channelMode) == 0) return A2DP_NS_CH_MODE;

  /* frameSize */
  if ((cfg_cie.future1 & p_cap->future1) == 0) return A2DP_NS_FRAMESIZE;

  return A2DP_SUCCESS;
}

bool A2DP_VendorUsesRtpHeaderOpus(UNUSED_ATTR bool content_protection_enabled,
                                  UNUSED_ATTR const uint8_t* p_codec_info) {
  return true;
}

const char* A2DP_VendorCodecNameOpus(UNUSED_ATTR const uint8_t* p_codec_info) {
  return "Opus";
}

bool A2DP_VendorCodecTypeEqualsOpus(const uint8_t* p_codec_info_a,
                                    const uint8_t* p_codec_info_b) {
  tA2DP_OPUS_CIE Opus_cie_a;
  tA2DP_OPUS_CIE Opus_cie_b;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status =
      A2DP_ParseInfoOpus(&Opus_cie_a, p_codec_info_a, true);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return false;
  }
  a2dp_status = A2DP_ParseInfoOpus(&Opus_cie_b, p_codec_info_b, true);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return false;
  }

  return true;
}

bool A2DP_VendorCodecEqualsOpus(const uint8_t* p_codec_info_a,
                                const uint8_t* p_codec_info_b) {
  tA2DP_OPUS_CIE Opus_cie_a;
  tA2DP_OPUS_CIE Opus_cie_b;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status =
      A2DP_ParseInfoOpus(&Opus_cie_a, p_codec_info_a, true);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return false;
  }
  a2dp_status = A2DP_ParseInfoOpus(&Opus_cie_b, p_codec_info_b, true);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return false;
  }

  return (Opus_cie_a.sampleRate == Opus_cie_b.sampleRate) &&
         (Opus_cie_a.channelMode == Opus_cie_b.channelMode) &&
         (Opus_cie_a.future1 == Opus_cie_b.future1);
}

int A2DP_VendorGetBitRateOpus(const uint8_t* p_codec_info) {
  int channel_count = A2DP_VendorGetTrackChannelCountOpus(p_codec_info);
  int framesize = A2DP_VendorGetFrameSizeOpus(p_codec_info);
  int samplerate = A2DP_VendorGetTrackSampleRateOpus(p_codec_info);

  // in milliseconds
  switch ((framesize * 1000) / samplerate) {
    case 20:
      if (channel_count == 2) {
        return 256000;
      } else if (channel_count == 1) {
        return 128000;
      } else
        return -1;
    default:
      return -1;
  }
}

int A2DP_VendorGetTrackSampleRateOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }

  switch (Opus_cie.sampleRate) {
    case A2DP_OPUS_SAMPLING_FREQ_48000:
      return 48000;
  }

  return -1;
}

int A2DP_VendorGetTrackBitsPerSampleOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }

  switch (Opus_cie.bits_per_sample) {
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16:
      return 16;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24:
      return 24;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32:
      return 32;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE:
    default:
      log::error("Invalid bit depth setting");
      return -1;
  }
}

int A2DP_VendorGetTrackChannelCountOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }

  switch (Opus_cie.channelMode) {
    case A2DP_OPUS_CHANNEL_MODE_MONO:
      return 1;
    case A2DP_OPUS_CHANNEL_MODE_STEREO:
    case A2DP_OPUS_CHANNEL_MODE_DUAL_MONO:
      return 2;
    default:
      log::error("Invalid channel setting");
  }

  return -1;
}

int A2DP_VendorGetSinkTrackChannelTypeOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }

  switch (Opus_cie.channelMode) {
    case A2DP_OPUS_CHANNEL_MODE_MONO:
      return 1;
    case A2DP_OPUS_CHANNEL_MODE_STEREO:
      return 2;
  }

  return -1;
}

int A2DP_VendorGetChannelModeCodeOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }

  switch (Opus_cie.channelMode) {
    case A2DP_OPUS_CHANNEL_MODE_MONO:
    case A2DP_OPUS_CHANNEL_MODE_STEREO:
      return Opus_cie.channelMode;
    default:
      break;
  }

  return -1;
}

int A2DP_VendorGetFrameSizeOpus(const uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE Opus_cie;

  // Check whether the codec info contains valid data
  tA2DP_STATUS a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, false);
  if (a2dp_status != A2DP_SUCCESS) {
    log::error("cannot decode codec information: {}", a2dp_status);
    return -1;
  }
  int samplerate = A2DP_VendorGetTrackSampleRateOpus(p_codec_info);

  switch (Opus_cie.future1) {
    case A2DP_OPUS_20MS_FRAMESIZE:
      if (samplerate == 48000) {
        return 960;
      }
  }

  return -1;
}

bool A2DP_VendorGetPacketTimestampOpus(UNUSED_ATTR const uint8_t* p_codec_info,
                                       const uint8_t* p_data,
                                       uint32_t* p_timestamp) {
  *p_timestamp = *(const uint32_t*)p_data;
  return true;
}

bool A2DP_VendorBuildCodecHeaderOpus(UNUSED_ATTR const uint8_t* p_codec_info,
                                     BT_HDR* p_buf,
                                     uint16_t frames_per_packet) {
  uint8_t* p;

  if (p_buf->offset < 4 + A2DP_OPUS_MPL_HDR_LEN) {
    return false;
  }

  p_buf->offset -= A2DP_OPUS_MPL_HDR_LEN;
  p = (uint8_t*)(p_buf + 1) + p_buf->offset;
  p_buf->len += A2DP_OPUS_MPL_HDR_LEN;

  A2DP_BuildMediaPayloadHeaderOpus(p, false, false, false,
                                   (uint8_t)frames_per_packet);

  return true;
}

std::string A2DP_VendorCodecInfoStringOpus(const uint8_t* p_codec_info) {
  std::stringstream res;
  std::string field;
  tA2DP_STATUS a2dp_status;
  tA2DP_OPUS_CIE Opus_cie;

  a2dp_status = A2DP_ParseInfoOpus(&Opus_cie, p_codec_info, true);
  if (a2dp_status != A2DP_SUCCESS) {
    res << "A2DP_ParseInfoOpus fail: " << loghex(a2dp_status);
    return res.str();
  }

  res << "\tname: Opus\n";

  // Sample frequency
  field.clear();
  AppendField(&field, (Opus_cie.sampleRate == 0), "NONE");
  AppendField(&field, (Opus_cie.sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000),
              "48000");
  res << "\tsamp_freq: " << field << " (" << loghex(Opus_cie.sampleRate)
      << ")\n";

  // Channel mode
  field.clear();
  AppendField(&field, (Opus_cie.channelMode == 0), "NONE");
  AppendField(&field, (Opus_cie.channelMode & A2DP_OPUS_CHANNEL_MODE_MONO),
              "Mono");
  AppendField(&field, (Opus_cie.channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO),
              "Stereo");
  res << "\tch_mode: " << field << " (" << loghex(Opus_cie.channelMode)
      << ")\n";

  // Framesize
  field.clear();
  AppendField(&field, (Opus_cie.future1 == 0), "NONE");
  AppendField(&field, (Opus_cie.future1 & A2DP_OPUS_20MS_FRAMESIZE), "20ms");
  AppendField(&field, (Opus_cie.future1 & A2DP_OPUS_10MS_FRAMESIZE), "10ms");
  res << "\tframesize: " << field << " (" << loghex(Opus_cie.future1) << ")\n";

  return res.str();
}

const tA2DP_ENCODER_INTERFACE* A2DP_VendorGetEncoderInterfaceOpus(
    const uint8_t* p_codec_info) {
  if (!A2DP_IsVendorSourceCodecValidOpus(p_codec_info)) return NULL;

  return &a2dp_encoder_interface_opus;
}

const tA2DP_DECODER_INTERFACE* A2DP_VendorGetDecoderInterfaceOpus(
    const uint8_t* p_codec_info) {
  if (!A2DP_IsVendorSinkCodecValidOpus(p_codec_info)) return NULL;

  return &a2dp_decoder_interface_opus;
}

bool A2DP_VendorAdjustCodecOpus(uint8_t* p_codec_info) {
  tA2DP_OPUS_CIE cfg_cie;

  // Nothing to do: just verify the codec info is valid
  if (A2DP_ParseInfoOpus(&cfg_cie, p_codec_info, true) != A2DP_SUCCESS)
    return false;

  return true;
}

btav_a2dp_codec_index_t A2DP_VendorSourceCodecIndexOpus(
    UNUSED_ATTR const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS;
}

btav_a2dp_codec_index_t A2DP_VendorSinkCodecIndexOpus(
    UNUSED_ATTR const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_SINK_OPUS;
}

const char* A2DP_VendorCodecIndexStrOpus(void) { return "Opus"; }

const char* A2DP_VendorCodecIndexStrOpusSink(void) { return "Opus SINK"; }

bool A2DP_VendorInitCodecConfigOpus(AvdtpSepConfig* p_cfg) {
  if (A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &a2dp_opus_source_caps,
                         p_cfg->codec_info) != A2DP_SUCCESS) {
    return false;
  }

  return true;
}

bool A2DP_VendorInitCodecConfigOpusSink(AvdtpSepConfig* p_cfg) {
  return A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &a2dp_opus_sink_caps,
                            p_cfg->codec_info) == A2DP_SUCCESS;
}

UNUSED_ATTR static void build_codec_config(const tA2DP_OPUS_CIE& config_cie,
                                           btav_a2dp_codec_config_t* result) {
  if (config_cie.sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000)
    result->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_48000;

  result->bits_per_sample = config_cie.bits_per_sample;

  if (config_cie.channelMode & A2DP_OPUS_CHANNEL_MODE_MONO)
    result->channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
  if (config_cie.channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
    result->channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
  }

  if (config_cie.future1 & A2DP_OPUS_20MS_FRAMESIZE)
    result->codec_specific_1 |= BTAV_A2DP_CODEC_FRAME_SIZE_20MS;
  if (config_cie.future1 & A2DP_OPUS_10MS_FRAMESIZE)
    result->codec_specific_1 |= BTAV_A2DP_CODEC_FRAME_SIZE_10MS;
}

A2dpCodecConfigOpusSource::A2dpCodecConfigOpusSource(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfigOpusBase(BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS,
                              A2DP_VendorCodecIndexStrOpus(), codec_priority,
                              true) {
  // Compute the local capability
  if (a2dp_opus_source_caps.sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
    codec_local_capability_.sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
  }
  codec_local_capability_.bits_per_sample =
      a2dp_opus_source_caps.bits_per_sample;
  if (a2dp_opus_source_caps.channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
    codec_local_capability_.channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
  }
  if (a2dp_opus_source_caps.channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
    codec_local_capability_.channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
  }
}

A2dpCodecConfigOpusSource::~A2dpCodecConfigOpusSource() {}

bool A2dpCodecConfigOpusSource::init() {
  if (!isValid()) return false;

  return true;
}

bool A2dpCodecConfigOpusSource::useRtpHeaderMarkerBit() const { return false; }

//
// Selects the best sample rate from |sampleRate|.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_best_sample_rate(uint8_t sampleRate,
                                    tA2DP_OPUS_CIE* p_result,
                                    btav_a2dp_codec_config_t* p_codec_config) {
  if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
    p_result->sampleRate = A2DP_OPUS_SAMPLING_FREQ_48000;
    p_codec_config->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
    return true;
  }
  return false;
}

//
// Selects the audio sample rate from |p_codec_audio_config|.
// |sampleRate| contains the capability.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_audio_sample_rate(
    const btav_a2dp_codec_config_t* p_codec_audio_config, uint8_t sampleRate,
    tA2DP_OPUS_CIE* p_result, btav_a2dp_codec_config_t* p_codec_config) {
  switch (p_codec_audio_config->sample_rate) {
    case BTAV_A2DP_CODEC_SAMPLE_RATE_48000:
      if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
        p_result->sampleRate = A2DP_OPUS_SAMPLING_FREQ_48000;
        p_codec_config->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_16000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_24000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_44100:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_88200:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_96000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_176400:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_192000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_NONE:
      break;
  }

  return false;
}

//
// Selects the best bits per sample from |bits_per_sample|.
// |bits_per_sample| contains the capability.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_best_bits_per_sample(
    btav_a2dp_codec_bits_per_sample_t bits_per_sample, tA2DP_OPUS_CIE* p_result,
    btav_a2dp_codec_config_t* p_codec_config) {
  if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32) {
    p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
    p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
    return true;
  }
  if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24) {
    p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
    p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
    return true;
  }
  if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16) {
    p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
    p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
    return true;
  }
  return false;
}

//
// Selects the audio bits per sample from |p_codec_audio_config|.
// |bits_per_sample| contains the capability.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_audio_bits_per_sample(
    const btav_a2dp_codec_config_t* p_codec_audio_config,
    btav_a2dp_codec_bits_per_sample_t bits_per_sample, tA2DP_OPUS_CIE* p_result,
    btav_a2dp_codec_config_t* p_codec_config) {
  switch (p_codec_audio_config->bits_per_sample) {
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16) {
        p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
        p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24) {
        p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
        p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32) {
        p_codec_config->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
        p_result->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE:
      break;
  }
  return false;
}

//
// Selects the best channel mode from |channelMode|.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_best_channel_mode(uint8_t channelMode,
                                     tA2DP_OPUS_CIE* p_result,
                                     btav_a2dp_codec_config_t* p_codec_config) {
  if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
    p_result->channelMode = A2DP_OPUS_CHANNEL_MODE_STEREO;
    p_codec_config->channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
    return true;
  }
  if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
    p_result->channelMode = A2DP_OPUS_CHANNEL_MODE_MONO;
    p_codec_config->channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
    return true;
  }
  return false;
}

//
// Selects the audio channel mode from |p_codec_audio_config|.
// |channelMode| contains the capability.
// The result is stored in |p_result| and |p_codec_config|.
// Returns true if a selection was made, otherwise false.
//
static bool select_audio_channel_mode(
    const btav_a2dp_codec_config_t* p_codec_audio_config, uint8_t channelMode,
    tA2DP_OPUS_CIE* p_result, btav_a2dp_codec_config_t* p_codec_config) {
  switch (p_codec_audio_config->channel_mode) {
    case BTAV_A2DP_CODEC_CHANNEL_MODE_MONO:
      if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
        p_result->channelMode = A2DP_OPUS_CHANNEL_MODE_MONO;
        p_codec_config->channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO:
      if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
        p_result->channelMode = A2DP_OPUS_CHANNEL_MODE_STEREO;
        p_codec_config->channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
        return true;
      }
      break;
    case BTAV_A2DP_CODEC_CHANNEL_MODE_NONE:
      break;
  }

  return false;
}

bool A2dpCodecConfigOpusBase::setCodecConfig(const uint8_t* p_peer_codec_info,
                                             bool is_capability,
                                             uint8_t* p_result_codec_config) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  tA2DP_OPUS_CIE peer_info_cie;
  tA2DP_OPUS_CIE result_config_cie;
  uint8_t channelMode;
  uint8_t sampleRate;
  uint8_t frameSize;
  btav_a2dp_codec_bits_per_sample_t bits_per_sample;
  const tA2DP_OPUS_CIE* p_a2dp_opus_caps =
      (is_source_) ? &a2dp_opus_source_caps : &a2dp_opus_sink_caps;

  btav_a2dp_codec_config_t device_codec_config_ = getCodecConfig();

  log::info(
      "AudioManager stream config {} sample rate {} bit depth {} channel mode",
      device_codec_config_.sample_rate, device_codec_config_.bits_per_sample,
      device_codec_config_.channel_mode);

  // Save the internal state
  btav_a2dp_codec_config_t saved_codec_config = codec_config_;
  btav_a2dp_codec_config_t saved_codec_capability = codec_capability_;
  btav_a2dp_codec_config_t saved_codec_selectable_capability =
      codec_selectable_capability_;
  btav_a2dp_codec_config_t saved_codec_user_config = codec_user_config_;
  btav_a2dp_codec_config_t saved_codec_audio_config = codec_audio_config_;
  uint8_t saved_ota_codec_config[AVDT_CODEC_SIZE];
  uint8_t saved_ota_codec_peer_capability[AVDT_CODEC_SIZE];
  uint8_t saved_ota_codec_peer_config[AVDT_CODEC_SIZE];
  memcpy(saved_ota_codec_config, ota_codec_config_, sizeof(ota_codec_config_));
  memcpy(saved_ota_codec_peer_capability, ota_codec_peer_capability_,
         sizeof(ota_codec_peer_capability_));
  memcpy(saved_ota_codec_peer_config, ota_codec_peer_config_,
         sizeof(ota_codec_peer_config_));

  tA2DP_STATUS status =
      A2DP_ParseInfoOpus(&peer_info_cie, p_peer_codec_info, is_capability);
  if (status != A2DP_SUCCESS) {
    log::error("can't parse peer's capabilities: error = {}", status);
    goto fail;
  }

  //
  // Build the preferred configuration
  //
  memset(&result_config_cie, 0, sizeof(result_config_cie));
  result_config_cie.vendorId = p_a2dp_opus_caps->vendorId;
  result_config_cie.codecId = p_a2dp_opus_caps->codecId;

  //
  // Select the sample frequency
  //
  sampleRate = p_a2dp_opus_caps->sampleRate & peer_info_cie.sampleRate;
  codec_config_.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;

  switch (codec_user_config_.sample_rate) {
    case BTAV_A2DP_CODEC_SAMPLE_RATE_48000:
      if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
        result_config_cie.sampleRate = A2DP_OPUS_SAMPLING_FREQ_48000;
        codec_capability_.sample_rate = codec_user_config_.sample_rate;
        codec_config_.sample_rate = codec_user_config_.sample_rate;
      }
      break;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_44100:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_88200:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_96000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_176400:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_192000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_16000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_24000:
    case BTAV_A2DP_CODEC_SAMPLE_RATE_NONE:
      codec_capability_.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
      codec_config_.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
      break;
  }

  // Select the sample frequency if there is no user preference
  do {
    // Compute the selectable capability
    if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
      codec_selectable_capability_.sample_rate |=
          BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
    }

    if (codec_config_.sample_rate != BTAV_A2DP_CODEC_SAMPLE_RATE_NONE) break;

    // Compute the common capability
    if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000)
      codec_capability_.sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_48000;

    // No user preference - try the codec audio config
    if (select_audio_sample_rate(&codec_audio_config_, sampleRate,
                                 &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - try the default config
    if (select_best_sample_rate(
            a2dp_opus_default_config.sampleRate & peer_info_cie.sampleRate,
            &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - use the best match
    if (select_best_sample_rate(sampleRate, &result_config_cie,
                                &codec_config_)) {
      break;
    }
  } while (false);
  if (codec_config_.sample_rate == BTAV_A2DP_CODEC_SAMPLE_RATE_NONE) {
    log::error(
        "cannot match sample frequency: local caps = 0x{:x} peer info = 0x{:x}",
        p_a2dp_opus_caps->sampleRate, peer_info_cie.sampleRate);
    goto fail;
  }

  //
  // Select the bits per sample
  //
  // NOTE: this information is NOT included in the Opus A2DP codec description
  // that is sent OTA.
  bits_per_sample = p_a2dp_opus_caps->bits_per_sample;
  codec_config_.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
  switch (codec_user_config_.bits_per_sample) {
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16) {
        result_config_cie.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_capability_.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_config_.bits_per_sample = codec_user_config_.bits_per_sample;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24) {
        result_config_cie.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_capability_.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_config_.bits_per_sample = codec_user_config_.bits_per_sample;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32:
      if (bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32) {
        result_config_cie.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_capability_.bits_per_sample = codec_user_config_.bits_per_sample;
        codec_config_.bits_per_sample = codec_user_config_.bits_per_sample;
      }
      break;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE:
      result_config_cie.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      codec_capability_.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      codec_config_.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      break;
  }

  // Select the bits per sample if there is no user preference
  do {
    // Compute the selectable capability
    codec_selectable_capability_.bits_per_sample =
        p_a2dp_opus_caps->bits_per_sample;

    if (codec_config_.bits_per_sample != BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE)
      break;

    // Compute the common capability
    codec_capability_.bits_per_sample = bits_per_sample;

    // No user preference - try yhe codec audio config
    if (select_audio_bits_per_sample(&codec_audio_config_,
                                     p_a2dp_opus_caps->bits_per_sample,
                                     &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - try the default config
    if (select_best_bits_per_sample(a2dp_opus_default_config.bits_per_sample,
                                    &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - use the best match
    if (select_best_bits_per_sample(p_a2dp_opus_caps->bits_per_sample,
                                    &result_config_cie, &codec_config_)) {
      break;
    }
  } while (false);
  if (codec_config_.bits_per_sample == BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE) {
    log::error(
        "cannot match bits per sample: default = 0x{:x} user preference = "
        "0x{:x}",
        a2dp_opus_default_config.bits_per_sample,
        codec_user_config_.bits_per_sample);
    goto fail;
  }

  //
  // Select the channel mode
  //
  channelMode = p_a2dp_opus_caps->channelMode & peer_info_cie.channelMode;
  codec_config_.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;
  switch (codec_user_config_.channel_mode) {
    case BTAV_A2DP_CODEC_CHANNEL_MODE_MONO:
      if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
        result_config_cie.channelMode = A2DP_OPUS_CHANNEL_MODE_MONO;
        codec_capability_.channel_mode = codec_user_config_.channel_mode;
        codec_config_.channel_mode = codec_user_config_.channel_mode;
      }
      break;
    case BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO:
      if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
        result_config_cie.channelMode = A2DP_OPUS_CHANNEL_MODE_STEREO;
        codec_capability_.channel_mode = codec_user_config_.channel_mode;
        codec_config_.channel_mode = codec_user_config_.channel_mode;
      }
      break;
    case BTAV_A2DP_CODEC_CHANNEL_MODE_NONE:
      codec_capability_.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;
      codec_config_.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;
      break;
  }

  // Select the channel mode if there is no user preference
  do {
    // Compute the selectable capability
    if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
      codec_selectable_capability_.channel_mode |=
          BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
    }
    if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
      codec_selectable_capability_.channel_mode |=
          BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
    }

    if (codec_config_.channel_mode != BTAV_A2DP_CODEC_CHANNEL_MODE_NONE) break;

    // Compute the common capability
    if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO)
      codec_capability_.channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
    if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
      codec_capability_.channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
    }

    // No user preference - try the codec audio config
    if (select_audio_channel_mode(&codec_audio_config_, channelMode,
                                  &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - try the default config
    if (select_best_channel_mode(
            a2dp_opus_default_config.channelMode & peer_info_cie.channelMode,
            &result_config_cie, &codec_config_)) {
      break;
    }

    // No user preference - use the best match
    if (select_best_channel_mode(channelMode, &result_config_cie,
                                 &codec_config_)) {
      break;
    }
  } while (false);
  if (codec_config_.channel_mode == BTAV_A2DP_CODEC_CHANNEL_MODE_NONE) {
    log::error(
        "cannot match channel mode: local caps = 0x{:x} peer info = 0x{:x}",
        p_a2dp_opus_caps->channelMode, peer_info_cie.channelMode);
    goto fail;
  }

  //
  // Select the frame size
  //
  frameSize = p_a2dp_opus_caps->future1 & peer_info_cie.future1;
  codec_config_.codec_specific_1 = BTAV_A2DP_CODEC_FRAME_SIZE_NONE;
  switch (codec_user_config_.codec_specific_1) {
    case BTAV_A2DP_CODEC_FRAME_SIZE_20MS:
      if (frameSize & A2DP_OPUS_20MS_FRAMESIZE) {
        result_config_cie.future1 = A2DP_OPUS_20MS_FRAMESIZE;
        codec_capability_.codec_specific_1 =
            codec_user_config_.codec_specific_1;
        codec_config_.codec_specific_1 = codec_user_config_.codec_specific_1;
      }
      break;
    case BTAV_A2DP_CODEC_FRAME_SIZE_10MS:
      if (frameSize & A2DP_OPUS_10MS_FRAMESIZE) {
        result_config_cie.future1 = A2DP_OPUS_10MS_FRAMESIZE;
        codec_capability_.codec_specific_1 =
            codec_user_config_.codec_specific_1;
        codec_config_.codec_specific_1 = codec_user_config_.codec_specific_1;
      }
      break;
    case BTAV_A2DP_CODEC_FRAME_SIZE_NONE:
      codec_capability_.codec_specific_1 = BTAV_A2DP_CODEC_FRAME_SIZE_NONE;
      codec_config_.codec_specific_1 = BTAV_A2DP_CODEC_FRAME_SIZE_NONE;
      break;
  }

  // No user preference - set default value
  codec_config_.codec_specific_1 = BTAV_A2DP_CODEC_FRAME_SIZE_20MS;
  result_config_cie.future1 = A2DP_OPUS_20MS_FRAMESIZE;
  result_config_cie.future3 = 0x00;

  if (codec_config_.codec_specific_1 == BTAV_A2DP_CODEC_FRAME_SIZE_NONE) {
    log::error(
        "cannot match frame size: local caps = 0x{:x} peer info = 0x{:x}",
        p_a2dp_opus_caps->future1, peer_info_cie.future1);
    goto fail;
  }

  if (A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &result_config_cie,
                         p_result_codec_config) != A2DP_SUCCESS) {
    log::error("failed to BuildInfoOpus for result_config_cie");
    goto fail;
  }

  //
  // Copy the codec-specific fields if they are not zero
  //
  if (codec_user_config_.codec_specific_1 != 0)
    codec_config_.codec_specific_1 = codec_user_config_.codec_specific_1;
  if (codec_user_config_.codec_specific_2 != 0)
    codec_config_.codec_specific_2 = codec_user_config_.codec_specific_2;
  if (codec_user_config_.codec_specific_3 != 0)
    codec_config_.codec_specific_3 = codec_user_config_.codec_specific_3;
  if (codec_user_config_.codec_specific_4 != 0)
    codec_config_.codec_specific_4 = codec_user_config_.codec_specific_4;

  // Create a local copy of the peer codec capability, and the
  // result codec config.
  if (is_capability) {
    status = A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &peer_info_cie,
                                ota_codec_peer_capability_);
  } else {
    status = A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &peer_info_cie,
                                ota_codec_peer_config_);
  }
  CHECK(status == A2DP_SUCCESS);

  status = A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &result_config_cie,
                              ota_codec_config_);
  CHECK(status == A2DP_SUCCESS);
  return true;

fail:
  // Restore the internal state
  codec_config_ = saved_codec_config;
  codec_capability_ = saved_codec_capability;
  codec_selectable_capability_ = saved_codec_selectable_capability;
  codec_user_config_ = saved_codec_user_config;
  codec_audio_config_ = saved_codec_audio_config;
  memcpy(ota_codec_config_, saved_ota_codec_config, sizeof(ota_codec_config_));
  memcpy(ota_codec_peer_capability_, saved_ota_codec_peer_capability,
         sizeof(ota_codec_peer_capability_));
  memcpy(ota_codec_peer_config_, saved_ota_codec_peer_config,
         sizeof(ota_codec_peer_config_));
  return false;
}

bool A2dpCodecConfigOpusBase::setPeerCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  tA2DP_OPUS_CIE peer_info_cie;
  uint8_t channelMode;
  uint8_t sampleRate;
  const tA2DP_OPUS_CIE* p_a2dp_opus_caps =
      (is_source_) ? &a2dp_opus_source_caps : &a2dp_opus_sink_caps;

  // Save the internal state
  btav_a2dp_codec_config_t saved_codec_selectable_capability =
      codec_selectable_capability_;
  uint8_t saved_ota_codec_peer_capability[AVDT_CODEC_SIZE];
  memcpy(saved_ota_codec_peer_capability, ota_codec_peer_capability_,
         sizeof(ota_codec_peer_capability_));

  tA2DP_STATUS status =
      A2DP_ParseInfoOpus(&peer_info_cie, p_peer_codec_capabilities, true);
  if (status != A2DP_SUCCESS) {
    log::error("can't parse peer's capabilities: error = {}", status);
    goto fail;
  }

  // Compute the selectable capability - sample rate
  sampleRate = p_a2dp_opus_caps->sampleRate & peer_info_cie.sampleRate;
  if (sampleRate & A2DP_OPUS_SAMPLING_FREQ_48000) {
    codec_selectable_capability_.sample_rate |=
        BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
  }

  // Compute the selectable capability - bits per sample
  codec_selectable_capability_.bits_per_sample =
      p_a2dp_opus_caps->bits_per_sample;

  // Compute the selectable capability - channel mode
  channelMode = p_a2dp_opus_caps->channelMode & peer_info_cie.channelMode;
  if (channelMode & A2DP_OPUS_CHANNEL_MODE_MONO) {
    codec_selectable_capability_.channel_mode |=
        BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
  }
  if (channelMode & A2DP_OPUS_CHANNEL_MODE_STEREO) {
    codec_selectable_capability_.channel_mode |=
        BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
  }

  log::info("BuildInfoOpus for peer info cie for ota caps");
  status = A2DP_BuildInfoOpus(AVDT_MEDIA_TYPE_AUDIO, &peer_info_cie,
                              ota_codec_peer_capability_);
  CHECK(status == A2DP_SUCCESS);
  return true;

fail:
  // Restore the internal state
  codec_selectable_capability_ = saved_codec_selectable_capability;
  memcpy(ota_codec_peer_capability_, saved_ota_codec_peer_capability,
         sizeof(ota_codec_peer_capability_));
  return false;
}

A2dpCodecConfigOpusSink::A2dpCodecConfigOpusSink(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfigOpusBase(BTAV_A2DP_CODEC_INDEX_SINK_OPUS,
                              A2DP_VendorCodecIndexStrOpusSink(),
                              codec_priority, false) {}

A2dpCodecConfigOpusSink::~A2dpCodecConfigOpusSink() {}

bool A2dpCodecConfigOpusSink::init() {
  if (!isValid()) return false;

  return true;
}

bool A2dpCodecConfigOpusSink::useRtpHeaderMarkerBit() const { return false; }

bool A2dpCodecConfigOpusSink::updateEncoderUserConfig(
    UNUSED_ATTR const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    UNUSED_ATTR bool* p_restart_input, UNUSED_ATTR bool* p_restart_output,
    UNUSED_ATTR bool* p_config_updated) {
  return false;
}
