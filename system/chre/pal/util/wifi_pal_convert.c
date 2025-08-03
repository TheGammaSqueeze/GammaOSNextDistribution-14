/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "chre/pal/util/wifi_pal_convert.h"

// Constants defining the number of bits per LCI IE field.
#define LCI_IE_UNCERTAINTY_BITS 6
#define LCI_IE_LAT_LONG_BITS 34
#define LCI_IE_ALT_TYPE_BITS 4
#define LCI_IE_ALT_BITS 30

// The LCI subelement ID.
#define LCI_SUBELEMENT_ID 0

/************************************************
 *  Private functions
 ***********************************************/

/**
 * Reverses the bit positions in a byte.
 *
 * @param input The input byte.
 *
 * @return The output byte with reversed bits.
 */
static uint8_t reverseBits(uint8_t input) {
  uint8_t output = 0;
  for (size_t i = 0; i < 8; i++) {
    output <<= 1;
    uint8_t tmp = (uint8_t)(input & 1);
    output |= tmp;
    input >>= 1;
  }

  return output;
}

/**
 * @param buf A non-null pointer to a buffer.
 * @param bufferBitOffset The bit offset with respect to the buffer pointer.
 *
 * @return The bit value of the desired bit offset.
 */
static uint64_t getBitAtBitOffsetInByteArray(const uint8_t *buf,
                                             size_t bufferBitOffset) {
  size_t index = bufferBitOffset / 8;
  size_t offsetInByte = bufferBitOffset % 8;
  return ((buf[index] & 0x80 >> offsetInByte) != 0);
}

/**
 * Returns the field value of the LCI IE buffer.
 *
 * The user must invoke this method in order of the IE data fields, providing
 * the number of bits the field is encoded as in numBits, and updating
 * bufferBitPos sequentially.
 *
 * @param buf A non-null pointer to a buffer.
 * @param numBits The number of bits the value is encoded as.
 * @param bufferBitPos The current bit position. This value will be updated as a
 * result of this function invocation, and will be incremented by numBits.
 *
 * @return The field value.
 */
static uint64_t getField(const uint8_t *buf, size_t numBits,
                         size_t *bufferBitPos) {
  uint64_t field = 0;
  for (size_t i = 0; i < numBits; i++) {
    // Per specs, we need to store the bits in MSB first per field,
    // so we store the bits in reverse order (since we have reverse the bits
    // per byte earlier).
    field |= getBitAtBitOffsetInByteArray(buf, *bufferBitPos + i) << i;
  }

  *bufferBitPos += numBits;
  return field;
}

static int64_t convert34BitTwosComplementToInt64(uint64_t input) {
  // This is 34 bits, so we need to sign extend
  if ((input & 0x200000000) != 0) {
    input |= 0xFFFFFFFC00000000;
  }

  return (int64_t)input;
}

static int32_t convert30BitTwosComplementToInt32(uint32_t input) {
  // This is 30 bits, so we need to sign extend
  if ((input & 0x20000000) != 0) {
    input |= 0xC0000000;
  }

  return (int32_t)input;
}

static void decodeLciSubelement(const uint8_t *lciSubelement,
                                struct chreWifiLci *out) {
  uint8_t lciDataTmp[CHRE_LCI_SUBELEMENT_DATA_LEN_BYTES];
  size_t bufferBitPos = 0;
  uint64_t x;

  // First, reverse the bits to get the LSB first per specs.
  for (size_t i = 0; i < CHRE_LCI_SUBELEMENT_DATA_LEN_BYTES; i++) {
    lciDataTmp[i] = reverseBits(lciSubelement[i]);
  }

  out->latitudeUncertainty =
      (uint8_t)getField(lciDataTmp, LCI_IE_UNCERTAINTY_BITS, &bufferBitPos);

  x = getField(lciDataTmp, LCI_IE_LAT_LONG_BITS, &bufferBitPos);
  out->latitude = convert34BitTwosComplementToInt64(x);

  out->longitudeUncertainty =
      (uint8_t)getField(lciDataTmp, LCI_IE_UNCERTAINTY_BITS, &bufferBitPos);

  x = getField(lciDataTmp, LCI_IE_LAT_LONG_BITS, &bufferBitPos);
  out->longitude = convert34BitTwosComplementToInt64(x);

  out->altitudeType =
      (uint8_t)getField(lciDataTmp, LCI_IE_ALT_TYPE_BITS, &bufferBitPos);
  out->altitudeUncertainty =
      (uint8_t)getField(lciDataTmp, LCI_IE_UNCERTAINTY_BITS, &bufferBitPos);

  x = getField(lciDataTmp, LCI_IE_ALT_BITS, &bufferBitPos);
  out->altitude = convert30BitTwosComplementToInt32((uint32_t)x);
}

/************************************************
 *  Public functions
 ***********************************************/
bool chreWifiLciFromIe(const uint8_t *ieData, size_t len,
                       struct chreWifiRangingResult *outResult) {
  bool success = false;
  const size_t kHeaderLen =
      CHRE_LCI_IE_HEADER_LEN_BYTES + CHRE_LCI_SUBELEMENT_HEADER_LEN_BYTES;
  if (len >= kHeaderLen) {
    size_t pos = CHRE_LCI_IE_HEADER_LEN_BYTES;

    uint8_t subelementId = ieData[pos++];
    uint8_t subelementLength = ieData[pos++];
    if ((subelementId == LCI_SUBELEMENT_ID) &&
        (len >= kHeaderLen + subelementLength)) {
      success = true;
      if (subelementLength < CHRE_LCI_SUBELEMENT_DATA_LEN_BYTES) {
        outResult->flags = 0;
      } else {
        outResult->flags = CHRE_WIFI_RTT_RESULT_HAS_LCI;
        decodeLciSubelement(&ieData[pos], &outResult->lci);
      }
    }
  }

  return success;
}
