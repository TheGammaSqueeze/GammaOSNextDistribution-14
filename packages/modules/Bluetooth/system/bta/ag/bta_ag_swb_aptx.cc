/*
 * Copyright 2023 The Android Open Source Project
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

#include "bta_ag_swb_aptx.h"

#include <android_bluetooth_flags.h>
#include <android_bluetooth_sysprop.h>
#include <bluetooth/log.h>
#include <string.h>
#include <unistd.h>

#include "bta/ag/bta_ag_int.h"
#include "common/strings.h"
#include "stack/include/btm_api_types.h"

using namespace bluetooth;

bool is_hfp_aptx_voice_enabled() {
  return IS_FLAG_ENABLED(hfp_codec_aptx_voice) &&
         GET_SYSPROP(Hfp, codec_aptx_voice, false);
}

void bta_ag_swb_handle_vs_at_events(tBTA_AG_SCB* p_scb, uint16_t cmd,
                                    int16_t int_arg, tBTA_AG_VAL* val) {
  switch (cmd) {
    case BTA_AG_AT_QAC_EVT:
      log::verbose("BTA_AG_AT_QAC_EVT");
      p_scb->codec_updated = true;
      if (p_scb->peer_codecs & BTA_AG_SCO_APTX_SWB_SETTINGS_Q0_MASK) {
        p_scb->sco_codec = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0;
      } else if (p_scb->peer_codecs & BTM_SCO_CODEC_MSBC) {
        p_scb->sco_codec = UUID_CODEC_MSBC;
      }
      bta_ag_send_qac(p_scb, NULL);
      log::verbose("Received AT+QAC, updating sco codec to SWB: {}",
                   p_scb->sco_codec);
      val->num = p_scb->peer_codecs;
      break;
    case BTA_AG_AT_QCS_EVT: {
      tBTA_AG_PEER_CODEC codec_type, codec_sent;
      alarm_cancel(p_scb->codec_negotiation_timer);

      log::verbose("BTA_AG_AT_QCS_EVT int_arg={}", int_arg);
      switch (int_arg) {
        case BTA_AG_SCO_APTX_SWB_SETTINGS_Q0:
          codec_type = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0;
          break;
        case BTA_AG_SCO_APTX_SWB_SETTINGS_Q1:
          codec_type = BTA_AG_SCO_APTX_SWB_SETTINGS_Q1;
          break;
        case BTA_AG_SCO_APTX_SWB_SETTINGS_Q2:
          codec_type = BTA_AG_SCO_APTX_SWB_SETTINGS_Q2;
          break;
        case BTA_AG_SCO_APTX_SWB_SETTINGS_Q3:
          codec_type = BTA_AG_SCO_APTX_SWB_SETTINGS_Q3;
          break;
        default:
          log::error("Unknown codec_uuid {}", int_arg);
          p_scb->is_aptx_swb_codec = false;
          codec_type = BTM_SCO_CODEC_MSBC;
          p_scb->codec_fallback = true;
          p_scb->sco_codec = BTM_SCO_CODEC_MSBC;
          break;
      }

      if (p_scb->codec_fallback) {
        codec_sent = BTM_SCO_CODEC_MSBC;
      } else {
        codec_sent = p_scb->sco_codec;
      }

      bta_ag_sco_codec_nego(p_scb, codec_type == codec_sent);

      /* send final codec info to callback */
      val->num = codec_sent;
      break;
    }
  }
}

tBTA_AG_PEER_CODEC bta_ag_parse_qac(char* p_s) {
  tBTA_AG_PEER_CODEC retval = BTM_SCO_CODEC_NONE;
  tBTA_AG_SCO_APTX_SWB_SETTINGS codec_mode =
      BTA_AG_SCO_APTX_SWB_SETTINGS_UNKNOWN;

  auto codec_modes =
      bluetooth::common::StringSplit(std::string(p_s), ",", SWB_CODECS_NUMBER);
  for (auto& codec_mode_str : codec_modes) {
    if (!std::isdigit(*codec_mode_str.c_str())) continue;
    codec_mode = static_cast<tBTA_AG_SCO_APTX_SWB_SETTINGS>(
        std::atoi(codec_mode_str.c_str()));
    switch (codec_mode) {
      case BTA_AG_SCO_APTX_SWB_SETTINGS_Q0:
        retval |= BTA_AG_SCO_APTX_SWB_SETTINGS_Q0_MASK;
        break;
      case BTA_AG_SCO_APTX_SWB_SETTINGS_Q1:
        retval |= BTA_AG_SCO_APTX_SWB_SETTINGS_Q1_MASK;
        break;
      case BTA_AG_SCO_APTX_SWB_SETTINGS_Q2:
        retval |= BTA_AG_SCO_APTX_SWB_SETTINGS_Q2_MASK;
        break;
      case BTA_AG_SCO_APTX_SWB_SETTINGS_Q3:
        retval |= BTA_AG_SCO_APTX_SWB_SETTINGS_Q3_MASK;
        break;
      default:
        log::verbose("Unknown Codec UUID({}) received", codec_mode);
        break;
    }
  }
  return (retval);
}
