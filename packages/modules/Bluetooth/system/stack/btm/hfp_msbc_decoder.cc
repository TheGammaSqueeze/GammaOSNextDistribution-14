/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "hfp_msbc_decoder"

#include "hfp_msbc_decoder.h"

#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstring>

#include "embdrv/sbc/decoder/include/oi_codec_sbc.h"
#include "embdrv/sbc/decoder/include/oi_status.h"
#include "os/log.h"

#define HFP_MSBC_PKT_LEN 60
#define HFP_MSBC_PCM_BYTES 240

using namespace bluetooth;

namespace fmt {
template <>
struct formatter<OI_STATUS> : enum_formatter<OI_STATUS> {};
}  // namespace fmt

typedef struct {
  OI_CODEC_SBC_DECODER_CONTEXT decoder_context;
  uint32_t context_data[CODEC_DATA_WORDS(2, SBC_CODEC_FAST_FILTER_BUFFERS)];
} tHFP_MSBC_DECODER;

static tHFP_MSBC_DECODER hfp_msbc_decoder;

bool hfp_msbc_decoder_init() {
  OI_STATUS status = OI_CODEC_SBC_DecoderReset(
      &hfp_msbc_decoder.decoder_context, hfp_msbc_decoder.context_data,
      sizeof(hfp_msbc_decoder.context_data), 1, 1, false);
  if (!OI_SUCCESS(status)) {
    log::error("OI_CODEC_SBC_DecoderReset failed with error code {}", status);
    return false;
  }

  status = OI_CODEC_SBC_DecoderConfigureMSbc(&hfp_msbc_decoder.decoder_context);
  if (!OI_SUCCESS(status)) {
    log::error("OI_CODEC_SBC_DecoderConfigureMSbc failed with error code {}",
               status);
    return false;
  }

  return true;
}

void hfp_msbc_decoder_cleanup(void) {
  memset(&hfp_msbc_decoder, 0, sizeof(hfp_msbc_decoder));
}

// Get the HFP MSBC encoded maximum frame size
bool hfp_msbc_decoder_decode_packet(const uint8_t* i_buf, int16_t* o_buf,
                                    size_t out_len) {
  if (out_len < HFP_MSBC_PCM_BYTES) {
    log::error(
        "Output buffer's size {} is less than one complete mSBC frame {}",
        (unsigned long)out_len, HFP_MSBC_PCM_BYTES);
    return false;
  }

  const OI_BYTE* oi_data;
  uint32_t oi_size, out_avail;

  oi_data = i_buf;
  oi_size = HFP_MSBC_PKT_LEN;
  out_avail = out_len;

  OI_STATUS status = OI_CODEC_SBC_DecodeFrame(
      &hfp_msbc_decoder.decoder_context, &oi_data, &oi_size, o_buf, &out_avail);
  if (!OI_SUCCESS(status) || out_avail != HFP_MSBC_PCM_BYTES || oi_size != 0) {
    log::error("Decoding failure: {}, {}, {}", status, (unsigned long)out_avail,
               (unsigned long)oi_size);
    return false;
  }

  return true;
}
