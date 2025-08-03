/*
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2022-2023 NXP
 **
 */
#pragma once

#include <fstream>

#define ESE_CONNECTIVITY_PACKET 0x96
#define ESE_DPD_EVENT 0x70

enum class LogEventType { kLogSMBEvent = 0, kLogDPDEvent };

// Store NTF/Event to filesystem under /data
// Currently being used to store SMB debug ntf and eSE DPD
// monitor events

class PhNxpEventLogger {
 public:
  // Mark copy constructor deleted
  PhNxpEventLogger(const PhNxpEventLogger&) = delete;

  // Get singleton instance of EventLogger.
  static PhNxpEventLogger& GetInstance();

  // Open  output file(s) for logging.
  void Initialize();

  // Write ntf/event to respective logfile.
  //   Event Type SMB: write SMB ntf to SMB logfile
  //   Event Type DPD: write DPD events DPD logfile
  void Log(uint8_t* p_ntf, uint16_t p_len, LogEventType event);

  // Close opened file(s).
  void Finalize();

 private:
  PhNxpEventLogger();
  bool logging_enabled_;
  std::ofstream smb_logFile_;
  std::ofstream dpd_logFile_;
  const char* kSMBLogFilePath = "/data/vendor/nfc/NxpNfcSmbLogDump.txt";
  const char* kDPDEventFilePath = "/data/vendor/nfc/debug/DPD_debug.txt";
};
