/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

/**
 * @hide
 * TODO(b/211025367): Remove this class and use direct API values.
 */
@Backing(type="int")
enum RangingChangeReason {
  /**
   * Unknown reason
   */
  UNKNOWN,

  /**
   * A local API call triggered the change, such as a call to
   * IUwbAdapter.closeRanging.
   */
  LOCAL_API,

  /**
   * The maximum number of sessions has been reached. This may be generated for
   * an active session if a higher priority session begins.
   */
  MAX_SESSIONS_REACHED,

  /**
   * The system state has changed by user or system action resulting in the
   * session changing (e.g. the user disables UWB).
   */
  SYSTEM_POLICY,

  /**
   * The remote device has requested to change the session
   */
  REMOTE_REQUEST,

  /**
   * The session changed for a protocol specific reason
   */
  PROTOCOL_SPECIFIC,

  /**
   * The provided parameters were invalid
   */
  BAD_PARAMETERS,

  /**
   * Max ranging round retries reached.
   */
  MAX_RR_RETRY_REACHED,

  /**
   * Insufficient slot per rr.
   */
  INSUFFICIENT_SLOTS_PER_RR,

  /**
   * The system state has changed resulting in the session changing (eg: the
   * user's locale changes and an active channel is no longer permitted to be
   * used).
   */
  SYSTEM_REGULATION,

  /**
   * When ranging is suspended due to Suspend Ranging bit is set to 1 in the received RCM at
   * responder, UWBS will report this reason code with Session State set to SESSION_STATE_ACTIVE.
   */
  SESSION_SUSPENDED,

  /**
   * When ranging is resumed due to Suspend Ranging bit is set to 0 in the received RCM at
   * responder, UWBS will report this reason code with Session State set to SESSION_STATE_ACTIVE.
   */
  SESSION_RESUMED,

  /**
  *  Session was stopped due to inband signal.
  */
  INBAND_SESSION_STOP,
}
