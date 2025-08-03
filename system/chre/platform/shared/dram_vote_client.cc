/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "chre/platform/shared/dram_vote_client.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/platform/system_time.h"

namespace chre {

void DramVoteClient::voteDramAccess(bool enabled) {
  LockGuard<Mutex> lock(mMutex);
  mLastDramRequest = enabled;

  bool needDram = (enabled || mDramVoteCount > 0);
  if (needDram != mLastDramVote) {
    issueDramVote(needDram);
    mLastDramVote = needDram;
  }
}

void DramVoteClient::incrementDramVoteCount() {
  LockGuard<Mutex> lock(mMutex);

  if (mDramVoteCount++ == 0) {
    mVoteCountStart = Milliseconds(SystemTime::getMonotonicTime());
    // TODO(b/181172259): Change back to LOGW once buffered logging path is
    // refactored.
    // LOGW("DRAM vote count begins");
    printf("CHRE: DRAM vote count begins\n");

    if (!mLastDramVote) {
      // Do not call voteDramAccess() directly as it will override
      // mLastDramRequest.
      issueDramVote(true /* enabled */);
      mLastDramVote = true;
    }
  } else {
    checkDramDuration();
  }
}

void DramVoteClient::decrementDramVoteCount() {
  LockGuard<Mutex> lock(mMutex);
  CHRE_ASSERT_LOG(mDramVoteCount > 0,
                  "Tried to decrement DRAM vote count when it's 0");

  if (--mDramVoteCount == 0) {
    // TODO(b/181172259): Change back to LOGW once buffered logging path is
    // refactored.
    // LOGW("DRAM vote count ends: %" PRIu64 " ms", checkDramDuration());
    printf("CHRE: DRAM vote count ends: %" PRIu64 " ms\n",
           checkDramDuration().getMilliseconds());

    // There's no DRAM activity now, remove CHRE's DRAM access vote.
    if (!mLastDramRequest) {
      issueDramVote(false /* enabled */);
      mLastDramVote = false;
    }
  }
}

Milliseconds DramVoteClient::checkDramDuration() {
  Milliseconds duration(0);
  if (mDramVoteCount > 0) {
    duration = Milliseconds(SystemTime::getMonotonicTime()) - mVoteCountStart;
  }

  // DRAM memory fallback only intends to handle a surge of DRAM memory
  // requests. If there's a prolonged period of memory fallback, this might
  // indicate a memory leak or inadequate SRAM heap size.
  if (duration > kMaxDramDuration) {
    if (EventLoopManagerSingleton::isInitialized() &&
        !EventLoopManagerSingleton::get()
             ->getEventLoop()
             .getPowerControlManager()
             .hostIsAwake()) {
      // AP is asleep
      FATAL_ERROR("Forced into DRAM for %" PRIu64 " msec",
                  duration.getMilliseconds());
    } else {
      // AP is awake, don't report error, just reset the starting time
      mVoteCountStart = Milliseconds(SystemTime::getMonotonicTime());
    }
  }
  return duration;
}

//! Explicitly instantiate the DramVoteClient singleton to reduce code size.
template class Singleton<DramVoteClient>;

}  // namespace chre
