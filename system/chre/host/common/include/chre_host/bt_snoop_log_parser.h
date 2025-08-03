/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_BT_SNOOP_LOG_PARSER_H_
#define CHRE_BT_SNOOP_LOG_PARSER_H_

#include <cinttypes>
#include <fstream>
#include <memory>

#include "chre/platform/shared/bt_snoop_log.h"

namespace android {
namespace chre {

class BtSnoopLogParser {
 public:
  /**
   * Add a BT event to the snoop log file.
   *
   * @param buffer Pointer to the buffer that contains the BT snoop log.
   */
  size_t log(const char *buffer);

 private:
  enum class PacketType : uint8_t {
    CMD = 1,
    ACL = 2,
    SCO = 3,
    EVT = 4,
    ISO = 5,
  };

  //! See host_messages.fbs for the definition of this struct.
  struct BtSnoopLog {
    uint8_t direction;
    uint8_t packetSize;
    uint8_t packet[];
  } __attribute__((packed));

  //! Header type used for the snoop log file.
  struct PacketHeaderType {
    uint32_t length_original;
    uint32_t length_captured;
    uint32_t flags;
    uint32_t dropped_packets;
    uint64_t timestamp;
    PacketType type;
  } __attribute__((packed));

  bool ensureSnoopLogFileIsOpen();

  void closeSnoopLogFile();

  bool openNextSnoopLogFile();

  /**
   * Write BT event to the snoop log file.
   *
   * @param packet The BT event packet.
   * @param packetSize Size of the packet.
   * @param direction Direction of the packet.
   */
  void capture(const uint8_t *packet, size_t packetSize,
               BtSnoopDirection direction);

  //! File stream used to write the log file.
  std::ofstream mBtSnoopOstream;

  //! Number of BT packtets in the log file.
  uint32_t mPacketCounter = 0;
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_BT_SNOOP_LOG_PARSER_H_
