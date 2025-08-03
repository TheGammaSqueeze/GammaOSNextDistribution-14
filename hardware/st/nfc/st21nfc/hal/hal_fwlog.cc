/******************************************************************************
 *
 *  Copyright (C) 2023 ST Microelectronics S.A.
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
 *
 ******************************************************************************/
#define LOG_TAG "NfcHalFwLog"

#include "hal_fwlog.h"
#include <cutils/properties.h>
#include <dlfcn.h>
#include <errno.h>
#include <hardware/nfc.h>
#include <string.h>
#include "android_logmsg.h"
#include "halcore.h"

extern void DispHal(const char* title, const void* data, size_t length);

uint8_t handlePollingLoopData(uint8_t format, uint8_t* tlvBuffer,
                              uint16_t data_len, uint8_t** NewTlv) {
  uint8_t value_len = 0;
  uint8_t flag= 0;

  uint32_t timestamp = (tlvBuffer[data_len - 4] << 24) |
                       (tlvBuffer[data_len - 3] << 16) |
                       (tlvBuffer[data_len - 2] << 8) | tlvBuffer[data_len - 1];

  uint32_t ts = 0;

  if ((format & 0x30) == 0x30) {
    // ST54L: 3.95us unit
    ts = (uint32_t)(((timestamp * 1024) / 259) + 0.5);
  } else {
    // ST54J/K: 4.57us unit
    ts = (uint32_t)(((timestamp * 128) / 28) + 0.5);
  }

  int t = tlvBuffer[0];

  switch (t) {
    case T_fieldOn:
    case T_fieldOff:
      STLOG_HAL_D("%s - FieldOn/Off", __func__);
      *NewTlv = (uint8_t*)malloc(8 * sizeof(uint8_t));
      value_len = 0x06;
      (*NewTlv)[0] = TYPE_REMOTE_FIELD;
      (*NewTlv)[1] = flag;
      (*NewTlv)[2] = value_len;
      (*NewTlv)[3] = (ts >> 24) & 0xFF;
      (*NewTlv)[4] = (ts >> 16) & 0xFF;
      (*NewTlv)[5] = (ts >> 8) & 0xFF;
      (*NewTlv)[6] = ts & 0xFF;
      (*NewTlv)[7] = 0xFF;
      (*NewTlv)[8] = (t == T_fieldOn) ? 0x1 : 0x0;
      break;
    case T_CERxError:
    case T_CERx: {
      STLOG_HAL_D("%s - T_CERx", __func__);
      int tlv_size = tlvBuffer[1]-2;
      if ((tlv_size < 9) || (tlvBuffer[5] != 0)) {
      tlv_size = 8;
      }
      value_len = tlv_size- 3;
      *NewTlv = (uint8_t*)malloc(tlv_size * sizeof(uint8_t));
      uint8_t gain;
      uint8_t type;
      int length_value = tlv_size - 8;
      gain = (tlvBuffer[3] & 0xF0) >> 4;

      switch (tlvBuffer[2] & 0xF) {
        case 0x1:
           flag |= 0x01;
           type = TYPE_A;
           break;
        case 0x2:
        case 0x3:
        case 0x4:
        case 0x5:
        case 0x6:
        case 0xB:
        case 0xD:
            type = TYPE_A;
          break;
        case 0x7:
        case 0xC:
            type = TYPE_B;
          break;
        case 0x8:
        case 0x9:
          type = TYPE_F;
          break;
        case 0xA:
          type = TYPE_V;
          break;
        default:
          type = TYPE_UNKNOWN;
          break;
      }
      (*NewTlv)[0] = type;
      (*NewTlv)[1] = flag;
      (*NewTlv)[2] = value_len;
      (*NewTlv)[3] = (ts >> 24) & 0xFF;
      (*NewTlv)[4] = (ts >> 16) & 0xFF;
      (*NewTlv)[5] = (ts >> 8) & 0xFF;
      (*NewTlv)[6] = ts & 0xFF;
      (*NewTlv)[7] = gain;
      if (tlv_size > 8) {
      memcpy(*NewTlv + 8, tlvBuffer + 8, length_value);
    }
    } break;
    default:
      break;
  }
  if (value_len)
    return value_len + 3;
  else
    return 0;
}

int notifyPollingLoopFrames(uint8_t* p_data, uint16_t data_len,
                            uint8_t* bufferToSend) {
  int current_tlv_length = 0;
  int tlv_len = 0;
  int ntf_len = 4;
  uint8_t* tlvFormatted = NULL;
  uint8_t* ObserverNtf = NULL;
  uint8_t* PreviousObserverNtf;
  uint8_t NCI_ANDROID_PASSIVE_OBSERVER_HEADER[4] = {0x6f, 0xc, 0x01, 0x3};

  for (int current_tlv_pos = 6;
       current_tlv_pos + p_data[current_tlv_pos + 1] + 2 <= data_len;
       current_tlv_pos += current_tlv_length) {
    current_tlv_length = p_data[current_tlv_pos + 1] + 2;
    uint8_t* tlvBuffer = p_data + current_tlv_pos;

    tlv_len = handlePollingLoopData(p_data[3], tlvBuffer, current_tlv_length,
                                    &tlvFormatted);

    if (tlvFormatted != NULL) {

      if (ObserverNtf == NULL) {
        ObserverNtf = (uint8_t*)malloc(4 * sizeof(uint8_t));
        memcpy(ObserverNtf, NCI_ANDROID_PASSIVE_OBSERVER_HEADER, 4);
      }

      PreviousObserverNtf = ObserverNtf;

      ObserverNtf = (uint8_t*)malloc((ntf_len + tlv_len) * sizeof(uint8_t));
      memcpy(ObserverNtf, PreviousObserverNtf, ntf_len);
      memcpy(ObserverNtf + ntf_len, tlvFormatted, tlv_len);
      ObserverNtf[2] = ntf_len + tlv_len - 3;
      ntf_len += tlv_len;
      free(tlvFormatted);
      tlvFormatted = NULL;
      free(PreviousObserverNtf);
    }
  }
  if (ObserverNtf != nullptr) {
    if (ntf_len <= 258) {
      memcpy(bufferToSend, ObserverNtf, ntf_len);
    }
    free(ObserverNtf);
    return ntf_len;
  } else {
    return 0;
  }
}
