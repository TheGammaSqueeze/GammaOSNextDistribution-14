/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quicksearchbox

/**
 * Interface for search settings.
 *
 * NOTE: Currently, this is not used very widely, in most instances implementers of this interface
 * are passed around by class name. Should this be deprecated ?
 */
interface SearchSettings {
  fun upgradeSettingsIfNeeded()

  /** Informs our listeners about the updated settings data. */
  fun broadcastSettingsChanged()
  fun getNextVoiceSearchHintIndex(size: Int): Int
  fun resetVoiceSearchHintFirstSeenTime()
  fun haveVoiceSearchHintsExpired(currentVoiceSearchVersion: Int): Boolean

  /**
   * Determines whether google.com should be used as the base path for all searches (as opposed to
   * using its country specific variants).
   */
  fun shouldUseGoogleCom(): Boolean
  fun setUseGoogleCom(useGoogleCom: Boolean)
  val searchBaseDomainApplyTime: Long
  var searchBaseDomain: String?
}
