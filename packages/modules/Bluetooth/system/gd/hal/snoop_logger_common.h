/******************************************************************************
 *
 *  Copyright (C) 2022 Google, Inc.
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

namespace bluetooth {
namespace hal {

constexpr uint32_t kBytesToTest = 0x12345678;
constexpr uint8_t kFirstByte = (const uint8_t&)kBytesToTest;
constexpr bool isLittleEndian = kFirstByte == 0x78;
constexpr bool isBigEndian = kFirstByte == 0x12;
static_assert((isLittleEndian || isBigEndian) && (isLittleEndian != isBigEndian));

constexpr uint32_t BTSNOOP_VERSION_NUMBER = isLittleEndian ? 0x01000000 : 1;
constexpr uint32_t BTSNOOP_DATALINK_TYPE =
    isLittleEndian ? 0xea030000 : 0x03ea;  // Datalink Type code for HCI UART (H4) is 1002

class SnoopLoggerCommon {
 public:
  struct FileHeaderType {
    uint8_t identification_pattern[8];
    uint32_t version_number;
    uint32_t datalink_type;
  } __attribute__((__packed__));

  static constexpr FileHeaderType kBtSnoopFileHeader = {
      .identification_pattern = {'b', 't', 's', 'n', 'o', 'o', 'p', 0x00},
      .version_number = BTSNOOP_VERSION_NUMBER,
      .datalink_type = BTSNOOP_DATALINK_TYPE};
};

}  // namespace hal
}  // namespace bluetooth
