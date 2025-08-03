/*
 * Copyright 2022-2023 NXP
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

#include "NxpTimer.h"

#include <android-base/logging.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

NxpTimer::NxpTimer(std::string tag) {
  logtag = std::move(tag);
  is_running = false;
  start_ts = end_ts = 0;
}
NxpTimer::~NxpTimer() {}
void NxpTimer::startTimer() {
  is_running = true;

  struct timespec tm;
  clock_gettime(CLOCK_MONOTONIC, &tm);
  start_ts = tm.tv_nsec * 1e-3 + tm.tv_sec * 1e+6;

  LOG(INFO) << logtag << " Timer started";
}
void NxpTimer::stopTimer() {
  is_running = false;

  struct timespec tm;
  clock_gettime(CLOCK_MONOTONIC, &tm);
  end_ts = tm.tv_nsec * 1e-3 + tm.tv_sec * 1e+6;

  LOG(INFO) << logtag << " Timer stopped";
}
unsigned long NxpTimer::totalDuration() {
  unsigned long duration = end_ts - start_ts;

  resetTimer();

  return duration;
}
void NxpTimer::resetTimer() {
  is_running = false;
  start_ts = end_ts = 0;
}
bool NxpTimer::isRunning() { return is_running; }
