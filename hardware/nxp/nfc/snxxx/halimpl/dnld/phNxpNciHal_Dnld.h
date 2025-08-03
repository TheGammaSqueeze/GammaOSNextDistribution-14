/*
 * Copyright (C) 2012-2023 NXP
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
#ifndef _PHNXPNCIHAL_DNLD_H_
#define _PHNXPNCIHAL_DNLD_H_

#include <phNfcStatus.h>
#include <phNfcTypes.h>

#define PHLIBNFC_DNLD_CHECKINTEGRITY_OFFSET (0x07)

NFCSTATUS phNxpNciHal_fw_dnld_switch_normal_mode();
NFCSTATUS phNxpNciHal_fw_download_seq(uint8_t bClkSrcVal, uint8_t bClkFreqVal,
                                      uint8_t seq_handler_offset = 0,
                                      bool bMinimalFw = false,
                                      bool degradedFwDnld = false);
#define STREAM_TO_UINT32(u32, p)                                      \
  {                                                                   \
    (u32) = (((uint32_t)(*(p))) + ((((uint32_t)(*((p) + 1)))) << 8) + \
             ((((uint32_t)(*((p) + 2)))) << 16) +                     \
             ((((uint32_t)(*((p) + 3)))) << 24));                     \
    (p) += 4;                                                         \
  }

#endif /* _PHNXPNCIHAL_DNLD_H_ */
