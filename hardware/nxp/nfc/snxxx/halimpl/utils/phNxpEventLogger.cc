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
#include "phNxpEventLogger.h"

#include <errno.h>
#include <phNxpConfig.h>
#include <phNxpLog.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

#include <fstream>
#include <iomanip>

#define TIMESTAMP_BUFFER_SIZE 64

PhNxpEventLogger::PhNxpEventLogger() { logging_enabled_ = false; }
PhNxpEventLogger& PhNxpEventLogger::GetInstance() {
  static PhNxpEventLogger nxp_event_logger_;
  return nxp_event_logger_;
}

// Helper function to get current timestamp of the
// device in [MM:DD HH:MIN:SEC:MSEC] format

static void GetCurrentTimestamp(char* timestamp) {
  struct timespec tv;
  clock_gettime(CLOCK_REALTIME, &tv);
  time_t rawtime = tv.tv_sec;
  struct tm* timeinfo;
  char buffer[TIMESTAMP_BUFFER_SIZE];

  timeinfo = localtime(&rawtime);
  // Need to calculate millisec separately as timeinfo doesn't
  // have milliseconds field
  int milliseconds = tv.tv_nsec / 1000000;

  strftime(buffer, sizeof(buffer), "%m-%d %H:%M:%S", timeinfo);
  sprintf(timestamp, "[%s.%03d]:", buffer, milliseconds);
}

void PhNxpEventLogger::Initialize() {
  NXPLOG_NCIHAL_D("EventLogger: init");
  dpd_logFile_.open(kDPDEventFilePath, std::ofstream::out |
                                           std::ofstream::binary |
                                           std::ofstream::app);
  if (dpd_logFile_.fail()) {
    NXPLOG_NCIHAL_D("EventLogger: Log file %s couldn't be opened! %d",
                    kDPDEventFilePath, errno);
  } else {
    mode_t permissions = 0744;  // rwxr--r--
    if (chmod(kDPDEventFilePath, permissions) == -1) {
      NXPLOG_NCIHAL_D("EventLogger: chmod failed on %s errno: %d",
                      kDPDEventFilePath, errno);
    }
  }

  unsigned long value = 0;
  if (GetNxpNumValue(NAME_NXP_SMBLOG_ENABLED, &value, sizeof(value))) {
    logging_enabled_ = (value == 1) ? true : false;
  }
  if (!logging_enabled_) return;

  smb_logFile_.open(kSMBLogFilePath, std::ofstream::out |
                                         std::ofstream::binary |
                                         std::ofstream::app);
  if (smb_logFile_.fail()) {
    NXPLOG_NCIHAL_D("EventLogger: Log file %s couldn't be opened! errno: %d",
                    kSMBLogFilePath, errno);
  }
}
void PhNxpEventLogger::Log(uint8_t* p_ntf, uint16_t p_len, LogEventType event) {
  NXPLOG_NCIHAL_D("EventLogger: event is %d", event);
  switch (event) {
    case LogEventType::kLogSMBEvent:
      if (!logging_enabled_) return;
      if (smb_logFile_.is_open()) {
        while (p_len) {
          smb_logFile_ << std::setfill('0') << std::hex << std::uppercase
                       << std::setw(2) << (0xFF & *p_ntf);
          ++p_ntf;
          --p_len;
        }
        smb_logFile_ << std::endl;
      } else {
        NXPLOG_NCIHAL_D("EventLogger: Log file %s is not opened",
                        kSMBLogFilePath);
      }
      break;
    case LogEventType::kLogDPDEvent:
      if (dpd_logFile_.is_open()) {
        char timestamp[TIMESTAMP_BUFFER_SIZE];
        memset(timestamp, 0, TIMESTAMP_BUFFER_SIZE);
        GetCurrentTimestamp(timestamp);
        dpd_logFile_ << timestamp;
        while (p_len) {
          dpd_logFile_ << std::setfill('0') << std::hex << std::uppercase
                       << std::setw(2) << (0xFF & *p_ntf);
          ++p_ntf;
          --p_len;
        }
        dpd_logFile_ << std::endl;
      } else {
        NXPLOG_NCIHAL_D("EventLogger: Log file %s is not opened",
                        kDPDEventFilePath);
      }
      break;
    default:
      NXPLOG_NCIHAL_D("EventLogger: Invalid destination");
  }
}

void PhNxpEventLogger::Finalize() {
  NXPLOG_NCIHAL_D("EventLogger: closing the Log file");
  if (dpd_logFile_.is_open()) dpd_logFile_.close();
  if (smb_logFile_.is_open()) smb_logFile_.close();
}
