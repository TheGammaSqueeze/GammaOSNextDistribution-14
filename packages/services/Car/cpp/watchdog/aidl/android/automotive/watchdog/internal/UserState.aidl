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

package android.automotive.watchdog.internal;

/**
 * Used by ICarWatchdog to describe whether user is started or stopped.
 */
@Backing(type="int")
enum UserState {
  /**
   * The user is started.
   */
  USER_STATE_STARTED,

  /**
   * The user is stopped.
   */
  USER_STATE_STOPPED,

  /**
   * The user is removed.
   */
  USER_STATE_REMOVED,

  /**
   * Number of available user states.
   *
   * @deprecated Value of enum no longer reflects the true amount of user states.
   *             Enum should not be used.
   */
  NUM_USER_STATES,

  /**
   * The user is switching.
   */
  USER_STATE_SWITCHING,

  /**
   * The user is unlocking.
   */
  USER_STATE_UNLOCKING,

  /**
   * The user is unlocked.
   */
  USER_STATE_UNLOCKED,

  /**
   * The user has been unlocked and system is in idle state.
   */
  USER_STATE_POST_UNLOCKED,
}
