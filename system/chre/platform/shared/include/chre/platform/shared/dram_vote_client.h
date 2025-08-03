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

#ifndef CHRE_PLATFORM_SHARED_VOTE_CLIENT_H_
#define CHRE_PLATFORM_SHARED_VOTE_CLIENT_H_

#include "chre/platform/mutex.h"
#include "chre/util/lock_guard.h"
#include "chre/util/singleton.h"
#include "chre/util/time.h"

namespace chre {

/**
 * Class used to manage voting for access to DRAM for platforms that require
 * voting for DRAM memory to be accessible.
 */
class DramVoteClient : public NonCopyable {
 public:
  /**
   * @return true if CHRE currently has a DRAM vote in place.
   */
  bool isDramVoteActive() {
    LockGuard<Mutex> lock(mMutex);
    return mLastDramVote;
  }

  /**
   * Makes a DRAM access request. An actual vote to the memory manager may not
   * be cast depending on the current mode and mDramVoteCount.
   *
   * @param enabled Whether to request DRAM access.
   */
  void voteDramAccess(bool enabled);

  /**
   * Increment the DRAM vote count when a client needs to perform some DRAM
   * activity. A DRAM vote is issued when the count increments from 0.
   */
  void incrementDramVoteCount();

  /**
   * Decrement the DRAM vote count when a client finishes some activity that has
   * to be performed in DRAM. A DRAM vote may be removed when the count
   * decrements to 0, depending on if explicit DRAM votes have been issued from
   * voteDramAccess.
   */
  void decrementDramVoteCount();

 private:
  //! The maximum allowed duration to be voted into DRAM by
  //! incrementDramVoteCount before a FATAL_ERROR is triggered.
  static constexpr Seconds kMaxDramDuration = Seconds(300);

  //! Last DRAM request made through voteDramAccess().
  bool mLastDramRequest = false;

  //! Last DRAM vote cast to the system.
  bool mLastDramVote = false;

  //! The system time mDramVoteCount increments from 0.
  Milliseconds mVoteCountStart = Milliseconds(0);

  //! The count of DRAM activities.
  uint32_t mDramVoteCount = 0;

  //! Used to protect access to member variables from other threads.
  Mutex mMutex;

  /**
   * Issue a vote to the underlying system. This must be implemented by each
   * platform to communicate with the right system.
   *
   * @param enabled Whether DRAM should be accessible.
   */
  void issueDramVote(bool enabled);

  /**
   * Check how long the system has been voted into DRAM due to
   * incrementDramVoteCount. If longer than kMaxDramDuration, trigger a crash.
   *
   * @return the duration in milliseconds since the system has been voted into
   *         big image due to incrementDramVoteCount.
   */
  Milliseconds checkDramDuration();
};

//! Provides an alias to the DramVoteClient singleton
typedef Singleton<DramVoteClient> DramVoteClientSingleton;

extern template class Singleton<DramVoteClient>;

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_VOTE_CLIENT_H_
