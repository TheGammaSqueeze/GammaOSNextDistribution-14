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

#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

constexpr size_t kDevClassLength = 3;
typedef std::array<uint8_t, kDevClassLength> DEV_CLASS; /* Device class */

inline constexpr DEV_CLASS kDevClassEmpty = {};

/* 0x00 is used as unclassified for all minor device classes */
#define BTM_COD_MINOR_UNCLASSIFIED 0x00
#define BTM_COD_MINOR_WEARABLE_HEADSET 0x04
#define BTM_COD_MINOR_CONFM_HANDSFREE 0x08
#define BTM_COD_MINOR_CAR_AUDIO 0x20
#define BTM_COD_MINOR_SET_TOP_BOX 0x24

/* minor device class field for Peripheral Major Class */
/* Bits 6-7 independently specify mouse, keyboard, or combo mouse/keyboard */
#define BTM_COD_MINOR_KEYBOARD 0x40
#define BTM_COD_MINOR_POINTING 0x80
/* Bits 2-5 OR'd with selection from bits 6-7 */
/* #define BTM_COD_MINOR_UNCLASSIFIED       0x00    */
#define BTM_COD_MINOR_JOYSTICK 0x04
#define BTM_COD_MINOR_GAMEPAD 0x08
#define BTM_COD_MINOR_REMOTE_CONTROL 0x0C
#define BTM_COD_MINOR_DIGITIZING_TABLET 0x14
#define BTM_COD_MINOR_CARD_READER 0x18 /* e.g. SIM card reader */
#define BTM_COD_MINOR_DIGITAL_PAN 0x1C

/* minor device class field for Imaging Major Class */
/* Bits 5-7 independently specify display, camera, scanner, or printer */
#define BTM_COD_MINOR_DISPLAY 0x10
/* Bits 2-3 Reserved */
/* #define BTM_COD_MINOR_UNCLASSIFIED       0x00    */

/* minor device class field for Wearable Major Class */
/* Bits 2-7 meaningful    */
#define BTM_COD_MINOR_WRIST_WATCH 0x04
#define BTM_COD_MINOR_GLASSES 0x14

/* minor device class field for Health Major Class */
/* Bits 2-7 meaningful    */
#define BTM_COD_MINOR_BLOOD_MONITOR 0x04
#define BTM_COD_MINOR_THERMOMETER 0x08
#define BTM_COD_MINOR_WEIGHING_SCALE 0x0C
#define BTM_COD_MINOR_GLUCOSE_METER 0x10
#define BTM_COD_MINOR_PULSE_OXIMETER 0x14
#define BTM_COD_MINOR_HEART_PULSE_MONITOR 0x18
#define BTM_COD_MINOR_STEP_COUNTER 0x20

/***************************
 * major device class field
 ***************************/
#define BTM_COD_MAJOR_COMPUTER 0x01
#define BTM_COD_MAJOR_PHONE 0x02
#define BTM_COD_MAJOR_AUDIO 0x04
#define BTM_COD_MAJOR_PERIPHERAL 0x05
#define BTM_COD_MAJOR_IMAGING 0x06
#define BTM_COD_MAJOR_WEARABLE 0x07
#define BTM_COD_MAJOR_HEALTH 0x09
#define BTM_COD_MAJOR_UNCLASSIFIED 0x1F

/***************************
 * service class fields
 ***************************/
#define BTM_COD_SERVICE_LMTD_DISCOVER 0x0020
#define BTM_COD_SERVICE_LE_AUDIO 0x0040
#define BTM_COD_SERVICE_POSITIONING 0x0100
#define BTM_COD_SERVICE_NETWORKING 0x0200
#define BTM_COD_SERVICE_RENDERING 0x0400
#define BTM_COD_SERVICE_CAPTURING 0x0800
#define BTM_COD_SERVICE_OBJ_TRANSFER 0x1000
#define BTM_COD_SERVICE_AUDIO 0x2000
#define BTM_COD_SERVICE_TELEPHONY 0x4000
#define BTM_COD_SERVICE_INFORMATION 0x8000

/* the COD masks */
#define BTM_COD_MINOR_CLASS_MASK 0xFC
#define BTM_COD_MAJOR_CLASS_MASK 0x1F
#define BTM_COD_SERVICE_CLASS_LO_B 0x00E0
#define BTM_COD_SERVICE_CLASS_MASK 0xFFE0

inline constexpr DEV_CLASS kDevClassUnclassified = {
    0x00, BTM_COD_MAJOR_UNCLASSIFIED, BTM_COD_MINOR_UNCLASSIFIED};

/* class of device field macros */
#define BTM_COD_MINOR_CLASS(u8, pd) \
  { (u8) = (pd)[2] & BTM_COD_MINOR_CLASS_MASK; }
#define BTM_COD_MAJOR_CLASS(u8, pd) \
  { (u8) = (pd)[1] & BTM_COD_MAJOR_CLASS_MASK; }
#define BTM_COD_SERVICE_CLASS(u16, pd) \
  {                                    \
    (u16) = (pd)[0];                   \
    (u16) <<= 8;                       \
    (u16) += (pd)[1] & 0xE0;           \
  }

/* to set the fields (assumes that format type is always 0) */
#define FIELDS_TO_COD(pd, mn, mj, sv)                   \
  {                                                     \
    (pd)[2] = mn;                                       \
    (pd)[1] = (mj) + ((sv)&BTM_COD_SERVICE_CLASS_LO_B); \
    (pd)[0] = (sv) >> 8;                                \
  }

#include <sstream>
inline std::string dev_class_text(const DEV_CLASS& dev_class) {
  std::ostringstream oss;
  uint8_t mj, mn;
  uint16_t sv;
  BTM_COD_MINOR_CLASS(mn, dev_class);
  BTM_COD_MAJOR_CLASS(mj, dev_class);
  BTM_COD_SERVICE_CLASS(sv, dev_class);
  oss << std::to_string(mj) << "-" << std::to_string(mn) << "-"
      << std::to_string(sv);
  return oss.str();
}

#define DEVCLASS_TO_STREAM(p, a)                \
  {                                             \
    size_t ijk;                                 \
    for (ijk = 0; ijk < kDevClassLength; ijk++) \
      *(p)++ = (a)[kDevClassLength - 1 - ijk];  \
  }

#define STREAM_TO_DEVCLASS(a, p)                                 \
  {                                                              \
    size_t ijk;                                                  \
    uint8_t* _pa = a.data() + kDevClassLength - 1;               \
    for (ijk = 0; ijk < kDevClassLength; ijk++) *_pa-- = *(p)++; \
  }
