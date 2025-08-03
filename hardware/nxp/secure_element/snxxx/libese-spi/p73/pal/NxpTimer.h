/*
 * Copyright 2022 NXP
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
#include <string>

class NxpTimer {
 public:
  /*
  ** Constructor
  */
  NxpTimer(std::string tag);

  /*
  ** Function         startTimer
  **
  ** Description      captures starting timestamp
  **
  ** Return           void
  */
  void startTimer();

  /*
  ** Function         stopTimer
  **
  ** Description       Captures end timestamp
  **
  ** Return           void
  */
  void stopTimer();

  /*
  ** Function         resetTimer
  **
  ** Description       Invalidates the timer state
  **
  ** Return           void
  */
  void resetTimer();

  /*
  ** Function         stopTimer
  **
  ** Description       Captures end timestamp
  **
  ** Return           void
  */
  unsigned long totalDuration();

  /*
  ** Function         isRunning
  **
  ** Description       Returns true if the timer is running else false
  **
  ** Return           bool
  */
  bool isRunning();

  /*
  ** Destructor
  */
  ~NxpTimer();

 private:
  unsigned long long start_ts, end_ts;
  std::string logtag;
  bool is_running;
};
