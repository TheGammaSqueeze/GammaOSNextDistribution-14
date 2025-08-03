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
 ** Copyright 2023 NXP
 **
 */
#pragma once

#include <fstream>
#include <mutex>

using namespace std;

class phNxpTempMgr {
 public:
  // mark copy constructor deleted
  phNxpTempMgr(const phNxpTempMgr&) = delete;

  /**
   * Get singleton instance of phNxpTempMgr.
   */
  static phNxpTempMgr& GetInstance();

  /**
   * Record current temperature status.
   */
  void UpdateICTempStatus(uint8_t* p_ntf, uint16_t p_len);

  /**
   * Apply delay if temp of any of IC module is NOK.
   */
  void Wait();

  /**
   * Reset the state to default.
   */
  void Reset(bool reset_timer = true);

  /**
   * Return current temp status.
   */
  inline bool IsICTempOk(void) {
    std::lock_guard<std::mutex> lock(ic_temp_mutex_);
    return is_ic_temp_ok_;
  }

 private:
  // constructor
  phNxpTempMgr();

  /**
   * Update IC temperature status with mutex locked.
   */
  void UpdateTempStatusLocked(bool temp_status);

  std::mutex ic_temp_mutex_;  // Mutex for protecting shared resources

  // tracks IC temp status
  bool is_ic_temp_ok_;

  // delay(in ms) before sending the next nci cmd to NFCC
  uint32_t total_delay_ms_;
  uint32_t timeout_timer_id_;  // ID of the tempNTF timeout callback timer
};
